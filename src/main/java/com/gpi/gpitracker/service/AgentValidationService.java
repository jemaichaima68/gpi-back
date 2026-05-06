package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentValidationService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final KeycloakAdminService keycloakAdminService;
    private final NotificationService notificationService;

    public String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "PDNG";
        switch (status.toUpperCase()) {
            case "EN_ATTENTE": case "PDNG": case "SIGNALE": return "PDNG";
            case "ACTC": return "ACTC";
            case "ACSP": return "ACSP";
            case "ACCP": case "ACCEPTE": case "ACTIVE": case "VALIDATED": case "ACSC": return "ACSC";
            case "RJCT": case "REJETE": case "REJETE_AUTO": return "RJCT";
            default: return status.toUpperCase();
        }
    }

    @Transactional
    public SwiftMessage acceptTransaction(Long id, String validatedBy) {
        SwiftMessage message = swiftMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée: " + id));

        String oldStatus = message.getStatus();

        // ============================================================
        // 🔥 DEBUG - AFFICHAGE FORCÉ
        // ============================================================
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║         ACCEPT TRANSACTION - DEBUG MODE ACTIVÉ              ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("🔍 ID: {}", id);
        log.info("🔍 ClientEmail avant: '{}'", message.getClientEmail());
        log.info("🔍 DebtorName: '{}'", message.getDebtorName());
        log.info("🔍 UETR: '{}'", message.getUetr());
        log.info("🔍 Status actuel: '{}'", oldStatus);
        log.info("================================================================");

        // Récupération email si null
        if (message.getClientEmail() == null || message.getClientEmail().isBlank()) {
            log.info("📧 ClientEmail est null, recherche dans Keycloak...");
            String debtorName = message.getDebtorName();
            if (debtorName != null && !debtorName.isBlank()) {
                String email = keycloakAdminService.getEmailByFullName(debtorName);
                if (email != null) {
                    message.setClientEmail(email);
                    log.info("✅ Email récupéré depuis Keycloak pour {} : {}", debtorName, email);
                    swiftMessageRepository.save(message);
                } else {
                    log.warn("⚠️ Aucun email trouvé dans Keycloak pour le nom: {}", debtorName);
                }
            }
        }

        // Mise à jour status
        message.setStatus("ACCEPTE");
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(validatedBy);
        swiftMessageRepository.save(message);

        // Log activity
        activityLogService.log(
                "ACCEPTE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " acceptée par " + validatedBy
        );

        // ============================================================
        // 🔥 ENVOI EMAIL - LOGS DÉTAILLÉS
        // ============================================================
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│              TENTATIVE D'ENVOI EMAIL                        │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ ClientEmail: '{}'", message.getClientEmail());
        log.info("│ ClientEmail is null: {}", message.getClientEmail() == null);
        log.info("│ ClientEmail is blank: {}", message.getClientEmail() == null || message.getClientEmail().isBlank());
        log.info("│ DebtorName: '{}'", message.getDebtorName());
        log.info("│ UETR: '{}'", message.getUetr());
        log.info("└─────────────────────────────────────────────────────────────┘");

        boolean emailSent = false;
        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            log.info("📧 [ACTION] Appel de sendTransactionAcceptedEmailSync à: {}", message.getClientEmail());
            try {
                emailService.sendTransactionAcceptedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        message.getAmount(),
                        message.getCurrency()
                );
                emailSent = true;
                log.info("✅ [SUCCÈS] Email acceptation envoyé avec succès à {}", message.getClientEmail());
            } catch (Exception e) {
                log.error("❌ [ERREUR] Échec envoi email: {}", e.getMessage(), e);
            }
        } else {
            log.error("❌ [BLOQUÉ] Email non envoyé - clientEmail est null ou vide");
        }

        // Notification interface
        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            try {
                notificationService.notifyStatusChange(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        "ACSC",
                        null
                );
                log.info("🔔 Notification acceptation envoyée à: {}", message.getClientEmail());
            } catch (Exception e) {
                log.error("❌ Erreur notification: {}", e.getMessage());
            }
        }

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ RÉSUMÉ - Email envoyé: {}                                      ║", emailSent);
        log.info("║ Transaction {} acceptée ({} → {})", message.getMsgId(), oldStatus, message.getStatus());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        return message;
    }

    @Transactional
    public SwiftMessage rejectTransaction(Long id, String motif, String rejectedBy) {
        SwiftMessage message = swiftMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée: " + id));

        String oldStatus = message.getStatus();

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║              REJECT TRANSACTION - DEBUG MODE                 ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("🔍 ID: {}", id);
        log.info("🔍 ClientEmail avant: '{}'", message.getClientEmail());
        log.info("🔍 DebtorName: '{}'", message.getDebtorName());
        log.info("🔍 UETR: '{}'", message.getUetr());
        log.info("🔍 Motif: '{}'", motif);

        if (message.getClientEmail() == null || message.getClientEmail().isBlank()) {
            String debtorName = message.getDebtorName();
            if (debtorName != null && !debtorName.isBlank()) {
                String email = keycloakAdminService.getEmailByFullName(debtorName);
                if (email != null) {
                    message.setClientEmail(email);
                    log.info("✅ Email récupéré depuis Keycloak: {}", email);
                    swiftMessageRepository.save(message);
                }
            }
        }

        message.setStatus("REJETE");
        message.setRejectionReason(motif);
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(rejectedBy);
        swiftMessageRepository.save(message);

        activityLogService.log(
                "REJETE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " rejetée par " + rejectedBy
        );

        boolean emailSent = false;
        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            log.info("📧 Envoi email rejet à: {}", message.getClientEmail());
            try {
                emailService.sendTransactionRejectedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        motif
                );
                emailSent = true;
                log.info("✅ Email rejet envoyé");
            } catch (Exception e) {
                log.error("❌ Erreur: {}", e.getMessage());
            }
        }

        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            notificationService.notifyStatusChange(
                    message.getClientEmail(),
                    message.getDebtorName(),
                    message.getUetr(),
                    "RJCT",
                    motif
            );
        }

        log.info("Transaction {} rejetée - Email: {}", message.getMsgId(), emailSent);
        return message;
    }

    public boolean needsAgentAction(String status) {
        String normalized = normalizeStatus(status);
        return "PDNG".equals(normalized);
    }

    public String getStatusLabel(String status) {
        switch (normalizeStatus(status)) {
            case "PDNG": return "En attente";
            case "ACTC": return "Validation technique";
            case "ACSP": return "En traitement";
            case "ACSC": return "Finalisé";
            case "RJCT": return "Rejeté";
            default: return status;
        }
    }
}