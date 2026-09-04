package com.lubrimanager.card;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passe chaque nuit les cartes échues au statut EXPIREE (RG-18). Le solde reste
 * acquis au compte du client ; seule la carte devient inutilisable.
 */
@Component
public class CardExpiryJob {

    private final CardService cardService;

    public CardExpiryJob(CardService cardService) {
        this.cardService = cardService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Lome")
    public void run() {
        cardService.expireOverdueCards();
    }
}
