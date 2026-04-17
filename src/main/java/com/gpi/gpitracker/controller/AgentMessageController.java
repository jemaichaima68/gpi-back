// AgentMessageController.java
package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.gpi.gpitracker.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/messages")
@RequiredArgsConstructor
public class AgentMessageController {

    private final SwiftMessageRepository messageRepository;
    private final ActivityLogService activityLogService;

    @GetMapping("/en-attente")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getPendingMessages() {
        List<SwiftMessage> pending = messageRepository.findByStatus("EN_ATTENTE");
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> getTransactionById(@PathVariable Long id) {
        return messageRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<?> getStats() {
        long total = messageRepository.count();
        long enAttente = messageRepository.countByStatus("RECEIVED");
        long signalees = messageRepository.countByStatus("SIGNALE");
        long acceptees = messageRepository.countByStatus("ACCEPTE");
        long rejetees = messageRepository.countByStatus("REJETE_AUTO") +
                messageRepository.countByStatus("REJETE");

        return ResponseEntity.ok(Map.of(
                "totalTransactions", total,
                "enAttente", enAttente,
                "signalees", signalees,
                "acceptees", acceptees,
                "rejetees", rejetees
        ));
    }

    // ✅ Accepter une transaction signalée
    @PutMapping("/{id}/accepter")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> acceptTransaction(@PathVariable Long id) {
        SwiftMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée"));

        String oldStatus = message.getStatus();
        message.setStatus("ACCEPTE");
        message.setNeedsAgentApproval(false);
        message = messageRepository.save(message);

        // ✅ LOG D'ACTIVITÉ
        activityLogService.log(
                "ACCEPTE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " acceptée par l'agent (statut: " + oldStatus + " → ACCEPTE)"
        );

        log.info("Transaction {} acceptée par l'agent", message.getMsgId());
        return ResponseEntity.ok(message);
    }

    // ✅ Rejeter une transaction signalée
    @PutMapping("/{id}/rejeter")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> rejectTransaction(
            @PathVariable Long id,
            @RequestBody(required = false) String motif) {
        SwiftMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée"));

        String oldStatus = message.getStatus();
        message.setStatus("REJETE");
        message.setNeedsAgentApproval(false);
        if (motif != null && !motif.isBlank()) {
            message.setRejectionReason(motif);
        }
        message = messageRepository.save(message);

        // ✅ LOG D'ACTIVITÉ
        String description = "Transaction " + message.getMsgId() + " rejetée par l'agent (statut: " + oldStatus + " → REJETE)";
        if (motif != null && !motif.isBlank()) {
            description += " - Motif: " + motif;
        }
        activityLogService.log(
                "REJETE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                description
        );

        log.info("Transaction {} rejetée par l'agent", message.getMsgId());
        return ResponseEntity.ok(message);
    }
}