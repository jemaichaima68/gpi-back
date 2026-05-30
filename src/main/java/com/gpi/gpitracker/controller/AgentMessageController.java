package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.CancellationRequest;
import com.gpi.gpitracker.dto.ConfirmationRequest;
import com.gpi.gpitracker.dto.EmissionRequest;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.gpi.gpitracker.service.ActivityLogService;
import com.gpi.gpitracker.service.EmailService;
import com.gpi.gpitracker.service.NotificationService;
import com.gpi.gpitracker.service.SwiftMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent/messages")
@RequiredArgsConstructor
public class AgentMessageController {

    private final SwiftMessageRepository messageRepository;
    private final ActivityLogService activityLogService;
    private final SwiftMessageSender messageSender;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // ==================== CONSTANTES ====================
    private static final String STATUS_ACCEPTED = "ACCEPTE";
    private static final String STATUS_REJECTED = "REJETE";
    private static final String STATUS_PENDING = "EN_ATTENTE";
    private static final String ENTITY_TYPE_TRANSACTION = "TRANSACTION";
    private static final String MESSAGE_TYPE_PACS008 = "PACS008";
    private static final String MESSAGE_TYPE_PACS009 = "PACS009";
    private static final String DECISION_ACCEPT = "ACCP";
    private static final String DECISION_REJECT = "RJCT";

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }

        return "unknown";
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getAllMessages() {
        return ResponseEntity.ok(messageRepository.findAllByOrderByReceivedAtDesc());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> getMessageById(@PathVariable Long id) {
        return messageRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/emit/pacs008")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Void> emitPacs008(@RequestBody EmissionRequest request) {

        SwiftMessage message = new SwiftMessage();

        message.setMessageType(MESSAGE_TYPE_PACS008);
        message.setMsgId("MSG_" + System.currentTimeMillis());
        message.setInstructionId(message.getMsgId());
        message.setEndToEndId(message.getMsgId());
        message.setUetr(UUID.randomUUID().toString());

        message.setAmount(new BigDecimal(request.getAmount()));
        message.setCurrency(request.getCurrency());
        message.setDebtorName(request.getDebtorName());
        message.setCreditorName(request.getCreditorName());
        message.setCreditorCountry(request.getCreditorCountry());
        message.setRemittanceInfo(request.getRemittanceInfo());

        messageSender.sendPacs008(message);

        activityLogService.log(
                "EMISSION",
                ENTITY_TYPE_TRANSACTION,
                String.valueOf(message.getId()),
                "Émission PACS008 : " + message.getMsgId()
        );

        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{id}/confirmation", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> confirmTransaction(@PathVariable Long id,
                                                     @RequestBody ConfirmationRequest request) {

        SwiftMessage original = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction non trouvée : " + id));

        String username = getCurrentUsername();

        if (!MESSAGE_TYPE_PACS008.equals(original.getMessageType()) &&
                !MESSAGE_TYPE_PACS009.equals(original.getMessageType())) {
            throw new IllegalArgumentException("Seuls les messages PACS008/PACS009 peuvent être traités.");
        }

        if (!"EN_ATTENTE".equals(original.getStatus()) &&
                !"ENVOYE".equals(original.getStatus()) &&
                !"PDNG".equals(original.getStatus())) {
            throw new IllegalStateException("Cette transaction est déjà traitée. Statut actuel : " + original.getStatus());
        }

        SwiftMessageSender.Pacs002Result result;

        if (DECISION_ACCEPT.equals(request.getStatus())) {

            original.setStatus(STATUS_ACCEPTED);
            original.setAgentValidated(true);
            original.setValidatedAt(LocalDateTime.now());
            original.setValidatedBy(username);

            messageRepository.save(original);

            result = messageSender.generatePacs002(original, DECISION_ACCEPT, null);

            notifyClient(original, STATUS_ACCEPTED);

            activityLogService.log(
                    "ACCEPTE",
                    ENTITY_TYPE_TRANSACTION,
                    String.valueOf(original.getId()),
                    "Transaction " + original.getMsgId() + " acceptée par " + username
            );

        } else if (DECISION_REJECT.equals(request.getStatus())) {

            if (request.getMotif() == null || request.getMotif().isBlank()) {
                throw new IllegalArgumentException("Le motif de rejet est obligatoire.");
            }

            original.setStatus(STATUS_REJECTED);
            original.setRejectionReason(request.getMotif());
            original.setAgentValidated(false);
            original.setValidatedAt(LocalDateTime.now());
            original.setValidatedBy(username);

            messageRepository.save(original);

            result = messageSender.generatePacs002(original, DECISION_REJECT, request.getMotif());

            notifyClient(original, STATUS_REJECTED);

            activityLogService.log(
                    "REJETE",
                    ENTITY_TYPE_TRANSACTION,
                    String.valueOf(original.getId()),
                    "Transaction " + original.getMsgId() + " rejetée par " + username
            );

        } else {
            throw new IllegalArgumentException("Statut invalide. Utilisez ACCP ou RJCT.");
        }

        return xmlResponse(result.getXml(), result.getFileName());
    }

    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> requestCancellation(@PathVariable Long id,
                                                      @RequestBody CancellationRequest request) {

        SwiftMessage original = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction non trouvée : " + id));

        if (!STATUS_ACCEPTED.equals(original.getStatus())) {
            throw new IllegalStateException("L'annulation est possible seulement pour une transaction acceptée.");
        }

        SwiftMessageSender.Camt056Result result = messageSender.generateCamt056(
                original,
                request.getReasonCode(),
                request.getReasonText(),
                getCurrentUsername()
        );

        notifyClient(original, "ANNULATION");

        activityLogService.log(
                "CAMT056",
                ENTITY_TYPE_TRANSACTION,
                String.valueOf(original.getId()),
                "Demande d'annulation CAMT.056 pour " + original.getMsgId()
        );

        return xmlResponse(result.getXml(), result.getFileName());
    }

    private void notifyClient(SwiftMessage message, String type) {

        if (message.getClientEmail() == null || message.getClientEmail().isBlank()) {
            return;
        }

        try {
            if (STATUS_ACCEPTED.equals(type)) {

                emailService.sendTransactionAcceptedByAgentEmail(
                        message.getClientEmail(),
                        message.getCreditorName(),
                        message.getUetr(),
                        message.getAmount(),
                        message.getCurrency()
                );

                notificationService.createNotification(
                        message.getClientEmail(),
                        "Transaction acceptée",
                        "Votre transaction a été acceptée.",
                        "success",
                        message.getUetr()
                );

            } else if (STATUS_REJECTED.equals(type)) {

                emailService.sendTransactionRejectedEmailSync(
                        message.getClientEmail(),
                        message.getCreditorName(),
                        message.getUetr(),
                        message.getRejectionReason()
                );

                notificationService.createNotification(
                        message.getClientEmail(),
                        "Transaction rejetée",
                        "Votre transaction a été rejetée.",
                        "error",
                        message.getUetr()
                );

            } else if ("ANNULATION".equals(type)) {

                notificationService.createNotification(
                        message.getClientEmail(),
                        "Demande d'annulation",
                        "Une demande d'annulation CAMT.056 a été envoyée.",
                        "warn",
                        message.getUetr()
                );
            }

        } catch (Exception e) {
            log.warn("Erreur notification client : {}", e.getMessage());
        }
    }

    private ResponseEntity<byte[]> xmlResponse(String xml, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Répondre à une demande d'annulation CAMT.056 reçue
     * Génère un CAMT.029 en réponse
     */
    @PostMapping(value = "/cancellation/{camt056Id}/respond", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> respondToCancellationRequest(
            @PathVariable Long camt056Id,
            @RequestBody CancellationResponseRequest request) {

        // 1. Récupérer le CAMT.056 reçu
        SwiftMessage camt056 = messageRepository.findById(camt056Id)
                .orElseThrow(() -> new IllegalArgumentException("CAMT.056 non trouvé : " + camt056Id));

        // 2. Vérifier que c'est bien un CAMT.056 entrant
        if (!"CAMT056".equals(camt056.getMessageType())) {
            throw new IllegalArgumentException("Le message n'est pas un CAMT.056");
        }
        if (!"IN".equals(camt056.getDirection())) {
            throw new IllegalArgumentException("Ce message n'est pas un CAMT.056 entrant");
        }

        // 3. Récupérer la transaction originale (celle à annuler)
        SwiftMessage originalTransaction = null;
        if (camt056.getOriginalUetr() != null && !camt056.getOriginalUetr().isBlank()) {
            originalTransaction = messageRepository.findByUetr(camt056.getOriginalUetr()).orElse(null);
        }

        if (originalTransaction == null) {
            throw new IllegalArgumentException("Transaction originale non trouvée pour UETR: " + camt056.getOriginalUetr());
        }

        String username = getCurrentUsername();

        // 4. Générer le CAMT.029 en réponse
        SwiftMessageSender.Camt029Result result = messageSender.generateCamt029ForResponse(
                camt056,
                originalTransaction,
                request.getResponseStatus(),
                request.getReasonText(),
                username
        );

        // 5. Marquer le CAMT.056 comme traité
        camt056.setStatus("TRAITE");
        camt056.setGroupStatus(request.getResponseStatus());
        camt056.setValidatedBy(username);
        camt056.setValidatedAt(LocalDateTime.now());
        messageRepository.save(camt056);

        activityLogService.log(
                "CAMT029_RESPONSE",
                "CANCELLATION",
                String.valueOf(camt056.getId()),
                "Réponse CAMT.029 à CAMT.056 " + camt056.getMsgId() + " : " + request.getResponseStatus()
        );

        return xmlResponse(result.getXml(), result.getFileName());
    }

    // DTO pour la requête (à mettre comme classe interne)
    public static class CancellationResponseRequest {
        private String responseStatus;  // CNCL, RJCR, PDCR
        private String reasonText;

        public String getResponseStatus() { return responseStatus; }
        public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }
        public String getReasonText() { return reasonText; }
        public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    }
}