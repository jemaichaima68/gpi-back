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

    // ==================== CONSTANTES ====================
    private static final String ALERTE_GRAVE = "GRAVE";
    private static final String ALERTE_ATTENTION = "ATTENTION";
    private static final String ALERTE_OK = "OK";
    private static final String STATUS_AUTO_REJECT = "REJETE_AUTO";
    private static final String STATUS_SIGNALE = "SIGNALE";
    private static final String MONTANT_PREFIX = "Montant (";
    private static final String MSG_TYPE_PACS009 = "PACS009";

    // ==================== ÉVALUATION PRINCIPALE ====================

    public EvaluationResult evaluerTransaction(SwiftMessage message) {
        AppSettings settings = settingsService.getRawSettings();

        if (message.getAmount() == null) {
            return new EvaluationResult(ALERTE_GRAVE, "Montant manquant dans le fichier XML");
        }

        if (MSG_TYPE_PACS009.equals(message.getMessageType())) {
            return evaluerTransactionInterbancaire(message, settings);
        }

        return evaluerTransactionClient(message, settings);
    }

    // ==================== VALIDATION CLIENT (PACS008) ====================

    private EvaluationResult evaluerTransactionClient(SwiftMessage message, AppSettings settings) {
        double amount = message.getAmount().doubleValue();

        if (amount > settings.getMontantMax()) {
            return new EvaluationResult(ALERTE_GRAVE,
                    MONTANT_PREFIX + message.getAmount() + " " + message.getCurrency() +
                            ") dépasse le plafond maximum de " + settings.getMontantMax() + " " + message.getCurrency());
        }

        if (amount < settings.getMontantMin()) {
            return new EvaluationResult(ALERTE_GRAVE,
                    MONTANT_PREFIX + message.getAmount() + " " + message.getCurrency() +
                            ") inférieur au minimum requis de " + settings.getMontantMin() + " " + message.getCurrency());
        }

        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (message.getCurrency() == null || !devisesOk.contains(message.getCurrency())) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "Devise " + message.getCurrency() + " non autorisée. Devises acceptées : " + settings.getDevisesAutorisees());
        }

        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (message.getCreditorCountry() != null && paysBloques.contains(message.getCreditorCountry())) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "Pays bénéficiaire " + message.getCreditorCountry() + " est dans la liste des pays sanctionnés");
        }

        return new EvaluationResult(ALERTE_OK, null);
    }

    // ==================== VALIDATION INTERBANCAIRE (PACS009) ====================

    private EvaluationResult evaluerTransactionInterbancaire(SwiftMessage message, AppSettings settings) {
        double amount = message.getAmount().doubleValue();
        double plafondInterbancaire = settings.getMontantMax() * 10;

        if (amount > plafondInterbancaire) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "Montant interbancaire (" + message.getAmount() + " " + message.getCurrency() +
                            ") dépasse le plafond maximum de " + plafondInterbancaire + " " + message.getCurrency());
        }

        if (amount < settings.getMontantMin()) {
            return new EvaluationResult(ALERTE_GRAVE,
                    MONTANT_PREFIX + message.getAmount() + " " + message.getCurrency() +
                            ") inférieur au minimum requis de " + settings.getMontantMin() + " " + message.getCurrency());
        }

        if (message.getInstructingAgentBic() == null || message.getInstructingAgentBic().isBlank()) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "BIC de la banque donneuse d'ordre (Instructing Agent) manquant");
        }

        if (message.getInstructedAgentBic() == null || message.getInstructedAgentBic().isBlank()) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "BIC de la banque bénéficiaire (Instructed Agent) manquant");
        }

        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (message.getCurrency() == null || !devisesOk.contains(message.getCurrency())) {
            return new EvaluationResult(ALERTE_GRAVE,
                    "Devise " + message.getCurrency() + " non autorisée pour transfert interbancaire");
        }

        if (message.getCreditorCountry() != null && !message.getCreditorCountry().isBlank()) {
            List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
            if (paysBloques.contains(message.getCreditorCountry())) {
                return new EvaluationResult(ALERTE_GRAVE,
                        "Pays bénéficiaire " + message.getCreditorCountry() + " est dans la liste des pays sanctionnés");
            }
        }

        if ((message.getDebtorIban() == null || message.getDebtorIban().isBlank()) &&
                (message.getCreditorIban() == null || message.getCreditorIban().isBlank())) {
            return new EvaluationResult(ALERTE_ATTENTION,
                    "Aucun IBAN renseigné (transaction via compte nostro/vostro) - vérifier la conformité");
        }

        return new EvaluationResult(ALERTE_OK, null);
    }

    // ==================== CLASS INTERNE POUR LE RÉSULTAT ====================

    public static class EvaluationResult {
        private final String alerte;
        private final String motif;

        public EvaluationResult(String alerte, String motif) {
            this.alerte = alerte;
            this.motif = motif;
        }

        public String getAlerte() { return alerte; }
        public String getMotif() { return motif; }
    }

    // ==================== MÉTHODES DÉPRÉCIÉES ====================

    /**
     * @deprecated Utiliser {@link #evaluerTransaction(SwiftMessage)} à la place
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public String validateTransaction(SwiftMessage message) {
        AppSettings settings = settingsService.getRawSettings();

        if (message.getAmount() == null) {
            return STATUS_AUTO_REJECT;
        }

        if (message.getAmount().doubleValue() < settings.getMontantMin()) {
            return STATUS_AUTO_REJECT;
        }
        if (message.getAmount().doubleValue() > settings.getMontantMax()) {
            return STATUS_SIGNALE;
        }
        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (message.getCurrency() == null || !devisesOk.contains(message.getCurrency())) {
            return STATUS_AUTO_REJECT;
        }
        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (message.getCreditorCountry() != null && paysBloques.contains(message.getCreditorCountry())) {
            return STATUS_AUTO_REJECT;
        }
        return "ACCEPTE";
    }

    @Transactional
    public void validateAllPendingTransactions() {
        List<SwiftMessage> pendingMessages = messageRepository.findByStatus("RECEIVED");

        for (SwiftMessage message : pendingMessages) {
            if (message.getAmount() == null) {
                log.warn("Transaction {} sans montant, ignorée", message.getMsgId());
                continue;
            }

            String oldStatus = message.getStatus();
            String newStatus = validateTransaction(message);
            message.setStatus(newStatus);

            String description = "Transaction " + message.getMsgId() +
                    " : validation automatique (" + oldStatus + " → " + newStatus + ")";

            if (STATUS_AUTO_REJECT.equals(newStatus)) {
                String reason = getRejectionReason(message);
                message.setRejectionReason(reason);
                description += " - Motif: " + reason;
            } else if (STATUS_SIGNALE.equals(newStatus)) {
                message.setAgentValidated(false);
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

        if (message.getAmount() == null) {
            return "Montant manquant dans la transaction";
        }

        if (message.getAmount().doubleValue() < settings.getMontantMin()) {
            return "Montant minimum requis : " + settings.getMontantMin() + " " + message.getCurrency();
        }

        List<String> devisesOk = Arrays.asList(settings.getDevisesAutorisees().split(","));
        if (message.getCurrency() == null || !devisesOk.contains(message.getCurrency())) {
            return "Devise non autorisée. Devises acceptées : " + settings.getDevisesAutorisees();
        }

        List<String> paysBloques = Arrays.asList(settings.getPaysSanctionnes().split(","));
        if (message.getCreditorCountry() != null && paysBloques.contains(message.getCreditorCountry())) {
            return "Pays bénéficiaire sanctionné : " + message.getCreditorCountry();
        }

        return "Règle métier non respectée";
    }
}