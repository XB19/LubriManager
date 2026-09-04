package com.lubrimanager.account;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.customer.Customer;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ouverture et consultation des comptes. Le mouvement du solde (recharge, vente)
 * relèvera de services dédiés, en transactions atomiques (Phase 5+).
 */
@Service
@Transactional
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Crée le compte unique d'un client. Appelé dans la transaction de création du client. */
    public Account openFor(Customer customer) {
        if (accounts.existsByCustomerId(customer.getId())) {
            throw ApiException.conflict("Ce client possède déjà un compte.");
        }
        Account account = new Account();
        account.setCustomer(customer);
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("XOF");
        account.setStatus(AccountStatus.ACTIF);
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound("Compte %d introuvable".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Account getByCustomer(Long customerId) {
        return accounts.findByCustomerId(customerId)
                .orElseThrow(() -> ApiException.notFound(
                        "Le client %d n'a pas de compte".formatted(customerId)));
    }
}
