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

    private static final String STATUS_PENDING = SwiftMessage.STATUS_PENDING; // EN_ATTENTE
    private static final String STATUS_ACCEPTED = SwiftMessage.STATUS_ACCEPTED; // ACCEPTE
    private static final String STATUS_REJECTED = SwiftMessage.STATUS_REJECTED; // REJETE

    public String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING;
        }

        String upperStatus = status.trim().toUpperCase();

        if (upperStatus.equals("EN_ATTENTE") || upperStatus.equals("SIGNALE")) {
            return STATUS_PENDING;
        }

        if (upperStatus.equals("ACCEPTE") || upperStatus.equals("ACCP")
                || upperStatus.equals("ACTIVE") || upperStatus.equals("VALIDATED")) {
            return STATUS_ACCEPTED;
        }

        if (upperStatus.equals("REJETE") || upperStatus.equals("RJCT")
                || upperStatus.equals("REJETE_AUTO")) {
            return STATUS_REJECTED;
        }

        return STATUS_PENDING;
    }

    @Transactional
    public SwiftMessage acceptTransaction(Long id, String validatedBy) {
        SwiftMessage message = swiftMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction non trouvée: " + id));

        if (!STATUS_PENDING.equals(message.getStatus())) {
            throw new IllegalStateException("Cette transaction est déjà traitée. Statut actuel : " + message.getStatus());
        }

        log.info("✅ ACCEPTATION: Transaction {} acceptée par {}", message.getMsgId(), validatedBy);

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

        message.setStatus(STATUS_ACCEPTED);
        message.setAgentValidated(true);
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(validatedBy);

        swiftMessageRepository.save(message);

        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            try {
                emailService.sendTransactionAcceptedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        message.getAmount(),
                        message.getCurrency()
                );
            } catch (Exception e) {
                log.error("❌ Erreur email acceptation: {}", e.getMessage());
            }

            notificationService.createNotification(
                    message.getClientEmail(),
                    "✅ Transaction acceptée",
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
                .orElseThrow(() -> new IllegalArgumentException("Transaction non trouvée: " + id));

        if (!STATUS_PENDING.equals(message.getStatus())) {
            throw new IllegalStateException("Cette transaction est déjà traitée. Statut actuel : " + message.getStatus());
        }

        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif de rejet est obligatoire.");
        }

        log.info("❌ REJET: Transaction {} rejetée par {}", message.getMsgId(), rejectedBy);

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

        message.setStatus(STATUS_REJECTED);
        message.setRejectionReason(motif);
        message.setAgentValidated(false);
        message.setValidatedAt(LocalDateTime.now());
        message.setValidatedBy(rejectedBy);

        swiftMessageRepository.save(message);

        if (message.getClientEmail() != null && !message.getClientEmail().isBlank()) {
            try {
                emailService.sendTransactionRejectedEmailSync(
                        message.getClientEmail(),
                        message.getDebtorName(),
                        message.getUetr(),
                        motif
                );
            } catch (Exception e) {
                log.error("❌ Erreur email rejet: {}", e.getMessage());
            }

            notificationService.createNotification(
                    message.getClientEmail(),
                    "❌ Transaction rejetée",
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
        return STATUS_PENDING.equals(normalizeStatus(status));
    }

    public String getStatusLabel(String status) {
        String normalized = normalizeStatus(status);

        if (STATUS_PENDING.equals(normalized)) return "En attente";
        if (STATUS_ACCEPTED.equals(normalized)) return "Accepté";
        if (STATUS_REJECTED.equals(normalized)) return "Rejeté";

        return status;
    }
}