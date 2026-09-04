package com.lubrimanager.payment;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.payment.dto.CreatePaymentMethodRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentMethodService {

    private final PaymentMethodRepository repository;

    public PaymentMethodService(PaymentMethodRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> list(boolean onlyActive) {
        return onlyActive
                ? repository.findByActiveTrueOrderBySortOrderAscLabelAsc()
                : repository.findAllByOrderBySortOrderAscLabelAsc();
    }

    @Transactional(readOnly = true)
    public PaymentMethod requireActive(Long id) {
        PaymentMethod method = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Moyen de paiement %d introuvable".formatted(id)));
        if (!method.isActive()) {
            throw ApiException.businessRule("Ce moyen de paiement est désactivé.");
        }
        return method;
    }

    public PaymentMethod create(CreatePaymentMethodRequest req) {
        String code = req.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Un moyen de paiement avec ce code existe déjà.");
        }
        PaymentMethod method = new PaymentMethod();
        method.setCode(code);
        method.setLabel(req.label().trim());
        method.setActive(true);
        method.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 99);
        return repository.save(method);
    }

    public PaymentMethod setActive(Long id, boolean active) {
        PaymentMethod method = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Moyen de paiement %d introuvable".formatted(id)));
        method.setActive(active);
        return method;
    }
}
