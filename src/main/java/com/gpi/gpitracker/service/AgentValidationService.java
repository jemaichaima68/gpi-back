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

        log.info(" ACCEPTATION: Transaction {} acceptée par {}", message.getMsgId(), validatedBy);

        // Récupération email si null
        if (message.getClientEmail() == null || message.getClientEmail().isBlank()) {
            String debtorName = message.getDebtorName();
            if (debtorName != null && !debtorName.isBlank()) {
                String email = keycloakAdminService.getEmailByFullName(debtorName);
                if (email != null) {
                    message.setClientEmail(email);
                    log.info("Email récupéré: {}", email);
                }
            }
        }

        // ⭐ SIMPLIFICATION: Statut passe directement à ACCEPTE
        message.setStatus("ACCEPTE");
        message.setAgentValidated(true);
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(validatedBy);
        swiftMessageRepository.save(message);

        // Notification au client
        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            try {
                emailService.sendTransactionAcceptedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        message.getAmount(),
                        message.getCurrency()
                );
                log.info(" Email acceptation envoyé");
            } catch (Exception e) {
                log.error(" Erreur email: {}", e.getMessage());
            }

            notificationService.createNotification(
                    message.getClientEmail(),
                    " Transaction acceptée",
                    "Votre transaction a été acceptée avec succès.",
                    "success",
                    message.getUetr()
            );
        }

        activityLogService.log(
                "ACCEPTE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " acceptée par " + validatedBy
        );

        return message;
    }

    @Transactional
    public SwiftMessage rejectTransaction(Long id, String motif, String rejectedBy) {
        SwiftMessage message = swiftMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée: " + id));

        log.info(" REJET: Transaction {} rejetée par {}", message.getMsgId(), rejectedBy);
        log.info("   Motif: {}", motif);

        // Récupération email si null
        if (message.getClientEmail() == null || message.getClientEmail().isBlank()) {
            String debtorName = message.getDebtorName();
            if (debtorName != null && !debtorName.isBlank()) {
                String email = keycloakAdminService.getEmailByFullName(debtorName);
                if (email != null) {
                    message.setClientEmail(email);
                    log.info(" Email récupéré: {}", email);
                }
            }
        }

        // ⭐ SIMPLIFICATION: Statut passe directement à REJETE
        message.setStatus("REJETE");
        message.setRejectionReason(motif);
        message.setAgentValidated(true);
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(rejectedBy);
        swiftMessageRepository.save(message);

        // Notification au client avec le motif
        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            try {
                emailService.sendTransactionRejectedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        motif
                );
                log.info("Email rejet envoyé");
            } catch (Exception e) {
                log.error(" Erreur email: {}", e.getMessage());
            }

            notificationService.createNotification(
                    message.getClientEmail(),
                    " Transaction rejetée",
                    "Votre transaction a été rejetée. Motif: " + motif,
                    "error",
                    message.getUetr()
            );
        }

        activityLogService.log(
                "REJETE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " rejetée par " + rejectedBy + " - Motif: " + motif
        );

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