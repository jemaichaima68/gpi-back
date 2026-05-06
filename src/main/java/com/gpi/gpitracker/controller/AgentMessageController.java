package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.ConfirmationRequest;
import com.gpi.gpitracker.dto.EmissionRequest;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.gpi.gpitracker.service.ActivityLogService;
import com.gpi.gpitracker.service.AgentValidationService;
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
    private final AgentValidationService agentValidationService;  // ← AJOUTER CETTE LIGNE

    // ==================== MÉTHODE POUR RÉCUPÉRER L'UTILISATEUR CONNECTÉ ====================
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) return username;
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

    // ==================== ENDPOINT POUR ÉMETTRE UN PACS008 SORTANT ====================

    @PostMapping("/emit/pacs008")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<?> emitPacs008(@RequestBody EmissionRequest request) {
        SwiftMessage message = new SwiftMessage();
        message.setMessageType("PACS008");
        message.setMsgId("MSG_" + System.currentTimeMillis());
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
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Émission PACS008 : " + message.getMsgId() + " → " + message.getCreditorName()
        );

        return ResponseEntity.ok().build();
    }

    // ==================== ENDPOINT POUR CONFIRMER UNE DÉCISION (CORRIGÉ) ====================

    @PutMapping(value = "/{id}/confirmation", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> confirmTransaction(@PathVariable Long id,
                                                     @RequestBody ConfirmationRequest request) {

        log.info("========================================");
        log.info("=== CONFIRMATION RECUE ===");
        log.info("ID: {}", id);
        log.info("Status demandé: {}", request.getStatus());
        log.info("Motif: {}", request.getMotif());
        log.info("========================================");

        SwiftMessage original = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée: " + id));

        String oldStatus = original.getStatus();
        String username = getCurrentUsername();

        log.info("🔍 Transaction trouvée - Status actuel: {}, ClientEmail: {}", oldStatus, original.getClientEmail());

        // ✅ APPEL À AgentValidationService POUR GÉRER L'ACCEPTATION/REJET + EMAIL + NOTIFICATION
        if ("ACCP".equals(request.getStatus())) {
            log.info("🔍 Appel de AgentValidationService.acceptTransaction pour ID: {}", id);
            agentValidationService.acceptTransaction(id, username);
            log.info("✅ Retour de AgentValidationService.acceptTransaction");
        } else if ("RJCT".equals(request.getStatus())) {
            log.info("🔍 Appel de AgentValidationService.rejectTransaction pour ID: {}", id);
            agentValidationService.rejectTransaction(id, request.getMotif(), username);
            log.info("✅ Retour de AgentValidationService.rejectTransaction");
        } else {
            // Si ce n'est ni ACCP ni RJCT, on met juste à jour le status
            String newStatus = convertStatus(request.getStatus());
            original.setStatus(newStatus);
            messageRepository.save(original);

            activityLogService.log(
                    newStatus,
                    "TRANSACTION",
                    String.valueOf(original.getId()),
                    "Transaction " + original.getMsgId() + " : " + oldStatus + " → " + newStatus
            );
        }

        // ✅ Récupérer la transaction à jour après modification
        SwiftMessage updatedMessage = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée après mise à jour"));

        // ✅ Générer le PACS002 de réponse
        SwiftMessageSender.Pacs002Result result = messageSender.generatePacs002(updatedMessage, request.getStatus(), request.getMotif());

        log.info("========================================");
        log.info("=== PACS002 GÉNÉRÉ ===");
        log.info("Fichier: {}", result.getFileName());
        log.info("========================================");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(result.getXmlContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String convertStatus(String status) {
        return switch (status) {
            case "ACCP" -> "ACCEPTE";
            case "RJCT" -> "REJETE";
            case "PDNG" -> "EN_ATTENTE";
            case "ACTC" -> "ACTC";
            case "ACSP" -> "ACSP";
            default -> status;
        };
    }
}