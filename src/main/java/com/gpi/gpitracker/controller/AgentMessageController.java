package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.ConfirmationRequest;
import com.gpi.gpitracker.dto.EmissionRequest;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.gpi.gpitracker.service.ActivityLogService;
import com.gpi.gpitracker.service.SwiftMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/messages")
@RequiredArgsConstructor
public class AgentMessageController {

    private final SwiftMessageRepository messageRepository;
    private final ActivityLogService activityLogService;
    private final SwiftMessageSender messageSender;

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

    // ==================== ENDPOINT POUR CONFIRMER UNE DÉCISION ====================

    @PutMapping(value = "/{id}/confirmation", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> confirmTransaction(@PathVariable Long id,
                                                     @RequestBody ConfirmationRequest request) {
        SwiftMessage original = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée"));

        String oldStatus = original.getStatus();
        String newStatus = convertStatus(request.getStatus());

        original.setStatus(newStatus);
        if ("RJCT".equals(request.getStatus()) && request.getMotif() != null) {
            original.setRejectionReason(request.getMotif());
        }
        messageRepository.save(original);

        activityLogService.log(
                newStatus,
                "TRANSACTION",
                String.valueOf(original.getId()),
                "Transaction " + original.getMsgId() + " : " + oldStatus + " → " + newStatus
        );

        SwiftMessageSender.Pacs002Result result = messageSender.generatePacs002(original, request.getStatus(), request.getMotif());

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