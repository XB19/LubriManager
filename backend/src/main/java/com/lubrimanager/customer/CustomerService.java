package com.lubrimanager.customer;

import com.lubrimanager.account.AccountService;
import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.Branch;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.customer.dto.CreateCustomerRequest;
import com.lubrimanager.customer.dto.UpdateCustomerRequest;
import com.lubrimanager.customer.dto.UpdateCustomerStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Règles de gestion des clients : attribution du n° client, normalisation des
 * champs, transitions de statut. Aucune logique dans le contrôleur.
 */
@Service
@Transactional
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String CUSTOMER_PREFIX = "CLI";
    private static final int CUSTOMER_WIDTH = 6;

    private final CustomerRepository customers;
    private final BranchRepository branches;
    private final ReferenceCounterService references;
    private final AccountService accountService;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, BranchRepository branches,
                           ReferenceCounterService references, AccountService accountService, AuditService audit) {
        this.customers = customers;
        this.branches = branches;
        this.references = references;
        this.accountService = accountService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String q, CustomerType type, CustomerStatus status, Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%"
                : "%";
        return customers.search(pattern, type, status, pageable);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Client %d introuvable".formatted(id)));
    }

    public Customer create(CreateCustomerRequest req) {
        Customer customer = new Customer();
        customer.setCustomerNo(references.next(CUSTOMER_PREFIX, CUSTOMER_WIDTH));
        customer.setBranch(resolveBranch(req.branchId()));
        customer.setStatus(CustomerStatus.ACTIF);
        apply(customer, req.fullName(), req.companyName(), req.phone(), req.email(),
                req.address(), req.type(), req.notes());
        Customer saved = customers.save(customer);
        accountService.openFor(saved); // DEC-01 : compte unique ouvert avec le client
        log.info("Client créé : {} ({}) + compte", saved.getCustomerNo(), saved.getFullName());
        return saved;
    }

    public Customer update(Long id, UpdateCustomerRequest req) {
        Customer customer = get(id);
        apply(customer, req.fullName(), req.companyName(), req.phone(), req.email(),
                req.address(), req.type(), req.notes());
        return customer;
    }

    public Customer updateStatus(Long id, UpdateCustomerStatusRequest req, Actor actor) {
        Customer customer = get(id);
        CustomerStatus from = customer.getStatus();
        if (from == req.status()) {
            return customer;
        }
        customer.setStatus(req.status());
        String reason = StringUtils.hasText(req.reason()) ? req.reason().trim() : null;
        log.info("Client {} : statut {} -> {} (motif : {})",
                customer.getCustomerNo(), from, req.status(), reason != null ? reason : "non précisé");
        audit.recordChange(AuditAction.CUSTOMER_STATUS_CHANGED, "customer", customer.getId(), customer.getCustomerNo(),
                "Client %s : %s → %s%s".formatted(customer.getCustomerNo(), from, req.status(),
                        reason != null ? " (" + reason + ")" : ""),
                java.util.Map.of("status", from.name()), java.util.Map.of("status", req.status().name()), actor);
        return customer;
    }

    // -- helpers -------------------------------------------------------------

    private void apply(Customer c, String fullName, String companyName, String phone, String email,
                       String address, CustomerType type, String notes) {
        c.setFullName(fullName.trim());
        c.setCompanyName(clean(companyName));
        c.setPhone(clean(phone));
        c.setEmail(clean(email));
        c.setAddress(clean(address));
        c.setType(type);
        c.setNotes(clean(notes));
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Branch resolveBranch(Long branchId) {
        if (branchId != null) {
            return branches.findById(branchId)
                    .orElseThrow(() -> ApiException.notFound("Agence %d introuvable".formatted(branchId)));
        }
        return branches.findByCode("MAIN")
                .or(() -> branches.findAll().stream().findFirst())
                .orElseThrow(() -> ApiException.businessRule("Aucune agence n'est configurée."));
    }
}
