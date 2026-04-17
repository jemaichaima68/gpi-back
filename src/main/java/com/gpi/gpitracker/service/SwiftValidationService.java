package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.entity.AppSettings;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftValidationService {

    private final AppSettingsService settingsService;
    private final SwiftMessageRepository messageRepository;
    private final ActivityLogService activityLogService;

    // ==================== ÉVALUATION (sans changer le statut) ====================

    public EvaluationResult evaluerTransaction(SwiftMessage message) {
        AppSettings settings = settingsService.getRawSettings();

        // 1. Vérification montant maximum
        if (message.getAmount().doubleValue() > settings.getMontantMax()) {
            return new EvaluationResult("GRAVE", "Montant (" + message.getAmount() + " " + message.getCurrency() +
                    ") dépasse le plafond maximum de " + settings.getMontantMax() + " " + message.getCurrency());
        }

        // 2. Vérification montant minimum
        if (message.getAmount().doubleValue() < settings.getMontantMin()) {
            return new EvaluationResult("GRAVE", "Montant (" + message.getAmount() + " " + message.getCurrency() +
                    ") inférieur au minimum requis de " + settings.getMontantMin() + " " + message.getCurrency());
        }

        // 3. Vérification devise autorisée
        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (!devisesOk.contains(message.getCurrency())) {
            return new EvaluationResult("GRAVE", "Devise " + message.getCurrency() +
                    " non autorisée. Devises acceptées : " + settings.getDevisesAutorisees());
        }

        // 4. Vérification pays bénéficiaire sanctionné
        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (paysBloques.contains(message.getCreditorCountry())) {
            return new EvaluationResult("GRAVE", "Pays bénéficiaire " + message.getCreditorCountry() +
                    " est dans la liste des pays sanctionnés");
        }

        // 5. Tout est OK
        return new EvaluationResult("OK", null);
    }

    // ==================== CLASS INTERNE POUR LE RÉSULTAT ====================

    public static class EvaluationResult {
        private final String alerte;   // "OK", "ATTENTION", "GRAVE"
        private final String motif;

        public EvaluationResult(String alerte, String motif) {
            this.alerte = alerte;
            this.motif = motif;
        }

        public String getAlerte() { return alerte; }
        public String getMotif() { return motif; }
    }

    // ==================== ANCIENNES MÉTHODES (à supprimer ou conserver pour compatibilité) ====================

    /**
     * @deprecated Ancienne méthode de validation automatique. Utiliser evaluerTransaction() à la place.
     */
    @Deprecated
    public String validateTransaction(SwiftMessage message) {
        AppSettings settings = settingsService.getRawSettings();

        if (message.getAmount().doubleValue() < settings.getMontantMin()) {
            return "REJETE_AUTO";
        }
        if (message.getAmount().doubleValue() > settings.getMontantMax()) {
            return "SIGNALE";
        }
        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (!devisesOk.contains(message.getCurrency())) {
            return "REJETE_AUTO";
        }
        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (paysBloques.contains(message.getCreditorCountry())) {
            return "REJETE_AUTO";
        }
        return "ACCEPTE";
    }

    @Transactional
    public void validateAllPendingTransactions() {
        List<SwiftMessage> pendingMessages = messageRepository.findByStatus("RECEIVED");

        for (SwiftMessage message : pendingMessages) {
            String oldStatus = message.getStatus();
            String newStatus = validateTransaction(message);
            message.setStatus(newStatus);

            String description = "Transaction " + message.getMsgId() +
                    " : validation automatique (" + oldStatus + " → " + newStatus + ")";

            if ("REJETE_AUTO".equals(newStatus)) {
                String reason = getRejectionReason(message);
                message.setRejectionReason(reason);
                description += " - Motif: " + reason;
            } else if ("SIGNALE".equals(newStatus)) {
                message.setNeedsAgentApproval(true);
                description += " - En attente d'approbation agent";
            }

            activityLogService.log(
                    newStatus,
                    "TRANSACTION",
                    String.valueOf(message.getId()),
                    description
            );

            messageRepository.save(message);
        }

        log.info("Validation terminée : {} transactions traitées", pendingMessages.size());
    }

    public String getRejectionReason(SwiftMessage message) {
        AppSettings settings = settingsService.getRawSettings();

        if (message.getAmount().doubleValue() < settings.getMontantMin()) {
            return "Montant minimum requis : " + settings.getMontantMin() + " " + message.getCurrency();
        }

        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (!devisesOk.contains(message.getCurrency())) {
            return "Devise non autorisée. Devises acceptées : " + settings.getDevisesAutorisees();
        }

        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (paysBloques.contains(message.getCreditorCountry())) {
            return "Pays bénéficiaire sanctionné : " + message.getCreditorCountry();
        }

        return "Règle métier non respectée";
    }
}