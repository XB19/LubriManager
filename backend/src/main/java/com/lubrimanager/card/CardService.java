package com.lubrimanager.card;

import com.lubrimanager.account.Account;
import com.lubrimanager.account.AccountService;
import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.card.dto.CreateCardRequest;
import com.lubrimanager.customer.Customer;
import com.lubrimanager.customer.CustomerService;
import com.lubrimanager.customer.CustomerStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Cycle de vie des cartes : création (n° attribué, unicité contrôlée),
 * activation, suspension, blocage, perte, désactivation. Les transitions
 * autorisées sont portées par {@link CardStatus}.
 */
@Service
@Transactional
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);
    private static final String PREFIX = "CARD";
    private static final int WIDTH = 6;

    private final CardRepository cards;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final ReferenceCounterService references;
    private final AuditService audit;
    private final Clock clock;

    public CardService(CardRepository cards, CustomerService customerService, AccountService accountService,
                       ReferenceCounterService references, AuditService audit, Clock clock) {
        this.cards = cards;
        this.customerService = customerService;
        this.accountService = accountService;
        this.references = references;
        this.audit = audit;
        this.clock = clock;
    }

    public Card create(CreateCardRequest req) {
        Customer customer = customerService.get(req.customerId());
        if (customer.getStatus() == CustomerStatus.ARCHIVE) {
            throw ApiException.businessRule("Client archivé : émission de carte impossible.");
        }
        Account account = accountService.getByCustomer(customer.getId());

        String externalNo = clean(req.externalNo());
        if (externalNo != null && cards.existsByExternalNoIgnoreCase(externalNo)) {
            throw ApiException.conflict("Ce numéro externe est déjà associé à une carte.");
        }

        Card card = new Card();
        card.setCardNo(references.next(PREFIX, WIDTH));
        card.setExternalNo(externalNo);
        card.setCustomer(customer);
        card.setAccount(account);
        card.setIssuedAt(clock.instant());
        card.setExpiresAt(req.expiresAt());
        card.setStatus(CardStatus.EN_ATTENTE);

        if (req.activateImmediately()) {
            applyTransition(card, CardStatus.ACTIVE, "création avec activation immédiate");
        }

        Card saved = cards.save(card);
        log.info("Carte créée : {} pour le client {} (statut {})",
                saved.getCardNo(), customer.getCustomerNo(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public Card get(Long id) {
        return cards.findById(id)
                .orElseThrow(() -> ApiException.notFound("Carte %d introuvable".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Page<Card> search(String q, CardStatus status, Long customerId, Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%"
                : "%";
        return cards.search(pattern, status, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Card> listByCustomer(Long customerId) {
        customerService.get(customerId); // 404 si le client n'existe pas
        return cards.findByCustomerIdOrderByIssuedAtDesc(customerId);
    }

    public Card changeStatus(Long id, CardStatus target, String reason, Actor actor) {
        if (!CardStatus.OPERATOR_SETTABLE.contains(target)) {
            throw ApiException.businessRule(
                    "Le statut « %s » ne peut pas être appliqué via cette opération.".formatted(target));
        }
        Card card = get(id);
        CardStatus from = card.getStatus();
        applyTransition(card, target, reason);
        auditStatusChange(card, from, reason, actor);
        return card;
    }

    public Card deactivate(Long id, String reason, Actor actor) {
        Card card = get(id);
        if (card.getStatus() == CardStatus.DESACTIVEE) {
            return card;
        }
        CardStatus from = card.getStatus();
        applyTransition(card, CardStatus.DESACTIVEE, reason);
        auditStatusChange(card, from, reason, actor);
        return card;
    }

    private void auditStatusChange(Card card, CardStatus from, String reason, Actor actor) {
        if (card.getStatus() == from) {
            return;
        }
        audit.recordChange(AuditAction.CARD_STATUS_CHANGED, "card", card.getId(), card.getCardNo(),
                "Carte %s : %s → %s%s".formatted(card.getCardNo(), from, card.getStatus(),
                        reason != null && !reason.isBlank() ? " (" + reason.trim() + ")" : ""),
                java.util.Map.of("status", from.name()), java.util.Map.of("status", card.getStatus().name()), actor);
    }

    /** Exécuté par {@link CardExpiryJob}. */
    public int expireOverdueCards() {
        int updated = cards.expireOverdue(
                CardStatus.EXPIREE,
                EnumSet.of(CardStatus.EN_ATTENTE, CardStatus.ACTIVE, CardStatus.SUSPENDUE),
                LocalDate.now(clock));
        if (updated > 0) {
            log.info("{} carte(s) passée(s) au statut EXPIREE", updated);
        }
        return updated;
    }

    // -- interne ----------------------------------------------------------

    private void applyTransition(Card card, CardStatus target, String reason) {
        CardStatus current = card.getStatus();
        if (current == target) {
            return;
        }
        if (!current.canTransitionTo(target)) {
            throw ApiException.businessRule(
                    "Transition de statut %s → %s non autorisée.".formatted(current, target));
        }
        if (target == CardStatus.ACTIVE) {
            if (card.isExpired(LocalDate.now(clock))) {
                throw ApiException.businessRule("La carte est expirée : activation impossible.");
            }
            if (card.getActivatedAt() == null) {
                card.setActivatedAt(clock.instant());
            }
        }
        log.info("Carte {} : {} → {} (motif : {})",
                card.getCardNo() != null ? card.getCardNo() : "nouvelle",
                current, target, StringUtils.hasText(reason) ? reason : "non précisé");
        card.setStatus(target);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
