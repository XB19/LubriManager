package com.lubrimanager.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findAllByOrderBySortOrderAscLabelAsc();

    List<PaymentMethod> findByActiveTrueOrderBySortOrderAscLabelAsc();

    Optional<PaymentMethod> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
