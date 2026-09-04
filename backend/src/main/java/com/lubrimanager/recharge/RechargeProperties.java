package com.lubrimanager.recharge;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paramètres de recharge (DEC-03, DEC-22). À terme, ces valeurs seront gérées
 * depuis l'administration ({@code app_setting}) ; pour l'instant elles sont dans
 * la configuration.
 *
 * @param approvalThreshold au-delà, la recharge passe EN_ATTENTE d'une validation MANAGER
 * @param maxAmount          montant maximum autorisé pour une recharge
 */
@Validated
@ConfigurationProperties("lubrimanager.recharge")
public record RechargeProperties(
        @NotNull @Positive BigDecimal approvalThreshold,
        @NotNull @Positive BigDecimal maxAmount) {
}
