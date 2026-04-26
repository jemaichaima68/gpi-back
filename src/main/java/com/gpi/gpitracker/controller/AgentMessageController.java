package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.gpi.gpitracker.service.ActivityLogService;
import com.gpi.gpitracker.service.SwiftMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/agent/messages")
@RequiredArgsConstructor
public class AgentMessageController {

    private final SwiftMessageRepository messageRepository;
    private final ActivityLogService activityLogService;
    private final SwiftMessageSender swiftMessageSender;

    @Value("${swift.received.path}")
    private String receivedPath;

    @Value("${swift.received.archive.path}")
    private String archivePath;

    // ==================== ENDPOINTS EXISTANTS ====================

    @GetMapping("/en-attente")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getPendingMessages() {
        List<SwiftMessage> pending = messageRepository.findByStatusIn(
                List.of("EN_ATTENTE", "PDNG", "ACTC")
        );
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getAllTransactions() {
        return ResponseEntity.ok(messageRepository.findAllByOrderByReceivedAtDesc());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> getTransactionById(@PathVariable Long id) {
        return messageRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== ENDPOINTS POUR LES FICHIERS XML ====================

    /**
     * Récupère le contenu XML d'une transaction
     * @param id L'identifiant de la transaction
     * @return Le contenu du fichier XML
     */
    @GetMapping("/{id}/xml")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<String> getTransactionXml(@PathVariable Long id) {
        log.info("Demande de récupération du fichier XML pour la transaction ID: {}", id);

        Optional<SwiftMessage> optionalMessage = messageRepository.findById(id);

        if (!optionalMessage.isPresent()) {
            log.warn("Transaction non trouvée pour l'ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        SwiftMessage message = optionalMessage.get();
        String fileName = message.getFileName();

        if (fileName == null || fileName.isBlank()) {
            log.warn("Aucun nom de fichier associé à la transaction ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        // Chercher le fichier
        File xmlFile = findXmlFile(fileName);

        if (xmlFile == null || !xmlFile.exists()) {
            log.warn("Fichier XML non trouvé pour la transaction ID: {}, fileName: {}", id, fileName);
            return ResponseEntity.notFound().build();
        }

        try {
            String xmlContent = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            log.info("Fichier XML trouvé et lu avec succès: {}", fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xmlContent);
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier XML {}: {}", fileName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Télécharge le fichier XML d'une transaction
     * @param id L'identifiant de la transaction
     * @return Le fichier XML en téléchargement
     */
    @GetMapping("/{id}/download-xml")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> downloadTransactionXml(@PathVariable Long id) {
        log.info("Demande de téléchargement du fichier XML pour la transaction ID: {}", id);

        Optional<SwiftMessage> optionalMessage = messageRepository.findById(id);

        if (!optionalMessage.isPresent()) {
            log.warn("Transaction non trouvée pour l'ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        SwiftMessage message = optionalMessage.get();
        String fileName = message.getFileName();

        if (fileName == null || fileName.isBlank()) {
            log.warn("Aucun nom de fichier associé à la transaction ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        File xmlFile = findXmlFile(fileName);

        if (xmlFile == null || !xmlFile.exists()) {
            log.warn("Fichier XML non trouvé pour la transaction ID: {}, fileName: {}", id, fileName);
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileContent = Files.readAllBytes(xmlFile.toPath());
            log.info("Fichier XML prêt pour téléchargement: {}", fileName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(fileContent.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier XML {}: {}", fileName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Recherche le fichier XML dans les dossiers d'archive et reçus
     * @param fileName Le nom du fichier à rechercher
     * @return Le fichier trouvé, ou null
     */
    private File findXmlFile(String fileName) {
        // Chercher d'abord dans le dossier d'archive
        Path archiveDir = Paths.get(archivePath);
        File archiveFile = archiveDir.resolve(fileName).toFile();
        if (archiveFile.exists()) {
            return archiveFile;
        }

        // Chercher ensuite dans le dossier des fichiers reçus
        Path receivedDir = Paths.get(receivedPath);
        File receivedFile = receivedDir.resolve(fileName).toFile();
        if (receivedFile.exists()) {
            return receivedFile;
        }

        // Chercher des fichiers avec timestamp dans le nom (ex: pacs.008_20240405_143022.xml)
        if (archiveDir.toFile().exists()) {
            String baseName = fileName.substring(0, Math.min(fileName.length() - 4, 20));
            File[] files = archiveDir.toFile().listFiles((dir, name) ->
                    name.startsWith(baseName) && name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                return files[0];
            }
        }

        // Chercher dans le dossier reçu avec pattern similaire
        if (receivedDir.toFile().exists()) {
            String baseName = fileName.substring(0, Math.min(fileName.length() - 4, 20));
            File[] files = receivedDir.toFile().listFiles((dir, name) ->
                    name.startsWith(baseName) && name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                return files[0];
            }
        }

        return null;
    }

    // ==================== ENDPOINTS DE FILTRAGE PAR DATE ====================

    /**
     * Filtrer les transactions par période prédéfinie
     * @param period - "today", "yesterday", "thisWeek", "lastWeek", "thisMonth", "lastMonth", "custom"
     * @param startDate - date de début (pour custom)
     * @param endDate - date de fin (pour custom)
     */
    @GetMapping("/filter-by-date")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getTransactionsByDateRange(
            @RequestParam String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDateTime start = null;
        LocalDateTime end = LocalDateTime.now();

        switch (period.toLowerCase()) {
            case "today":
                start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;

            case "yesterday":
                start = LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                end = LocalDateTime.now().minusDays(1).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                break;

            case "thisweek":
                start = LocalDateTime.now().minusDays(LocalDateTime.now().getDayOfWeek().getValue() - 1)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;

            case "lastweek":
                start = LocalDateTime.now().minusDays(LocalDateTime.now().getDayOfWeek().getValue() + 6)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
                end = LocalDateTime.now().minusDays(LocalDateTime.now().getDayOfWeek().getValue())
                        .withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                break;

            case "thismonth":
                start = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;

            case "lastmonth":
                start = LocalDateTime.now().minusMonths(1).withDayOfMonth(1)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
                end = LocalDateTime.now().withDayOfMonth(1)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1);
                break;

            case "custom":
                if (startDate != null && endDate != null) {
                    start = startDate.atStartOfDay();
                    end = endDate.atTime(23, 59, 59);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Les dates startDate et endDate sont requises pour la période custom"));
                }
                break;

            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Période non reconnue: " + period));
        }

        List<SwiftMessage> transactions;
        if (end != null) {
            transactions = messageRepository.findByReceivedAtBetween(start, end);
        } else {
            transactions = messageRepository.findByReceivedAtAfter(start);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("startDate", start != null ? start.toString() : null);
        response.put("endDate", end != null ? end.toString() : null);
        response.put("count", transactions.size());
        response.put("transactions", transactions);

        log.info("Filtrage par date - période: {}, {} transaction(s) trouvée(s)", period, transactions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Filtrer les transactions par date exacte
     */
    @GetMapping("/filter-by-exact-date")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getTransactionsByExactDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SwiftMessage> transactions = messageRepository.findByReceivedDate(date);

        Map<String, Object> response = new HashMap<>();
        response.put("date", date.toString());
        response.put("count", transactions.size());
        response.put("transactions", transactions);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupérer les statistiques par date (pour les graphiques)
     */
    @GetMapping("/stats-by-date")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getTransactionStatsByDate() {
        List<Object[]> results = messageRepository.countTransactionsByDate();

        Map<String, Long> stats = new LinkedHashMap<>();
        for (Object[] result : results) {
            String date = result[0] != null ? result[0].toString() : "unknown";
            Long count = ((Number) result[1]).longValue();
            stats.put(date, count);
        }

        // Statistiques supplémentaires
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0);
        long todayCount = messageRepository.findByReceivedAtAfter(todayStart).size();

        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        long weekCount = messageRepository.findByReceivedAtAfter(weekStart).size();

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        long monthCount = messageRepository.findByReceivedAtAfter(monthStart).size();

        Map<String, Object> response = new HashMap<>();
        response.put("daily", stats);
        response.put("todayCount", todayCount);
        response.put("thisWeekCount", weekCount);
        response.put("thisMonthCount", monthCount);

        return ResponseEntity.ok(response);
    }

    // ==================== ENDPOINTS DE TRAITEMENT ====================

    @PutMapping("/{id}/confirmation")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> sendConfirmation(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        log.info("Réception confirmation pour transaction {} : {}", id, payload);

        SwiftMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée : " + id));

        String status = payload.get("status");
        String motif = payload.get("motif");

        if (status == null || status.isBlank()) {
            throw new RuntimeException("Statut requis pour la confirmation");
        }

        swiftMessageSender.sendPacs002(message, status, motif);

        message.setStatus(mapStatusToFrench(status));
        message.setNeedsAgentApproval(false);

        if ("RJCT".equals(status) && motif != null && !motif.isBlank()) {
            message.setRejectionReason(motif);
        }

        message = messageRepository.save(message);

        activityLogService.log(
                status,
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " mise à jour : statut = " + status +
                        (motif != null ? " - Motif: " + motif : "")
        );

        log.info("Transaction {} mise à jour : statut = {}", message.getMsgId(), status);

        return ResponseEntity.ok(message);
    }

    private String mapStatusToFrench(String swiftStatus) {
        return switch (swiftStatus) {
            case "ACCP" -> "ACCEPTE";
            case "RJCT" -> "REJETE";
            case "PDNG" -> "EN_ATTENTE";
            case "ACTC" -> "ACTC";
            case "ACSP" -> "ACSP";
            default -> "EN_ATTENTE";
        };
    }

    @GetMapping("/client")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getClientTransactions() {
        List<SwiftMessage> messages = messageRepository.findByMessageType("PACS008");
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/{id}/accepter")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> acceptTransaction(@PathVariable Long id) {
        SwiftMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée"));

        swiftMessageSender.sendPacs002(message, "ACCP", null);
        message.setStatus("ACCEPTE");
        message.setNeedsAgentApproval(false);
        message = messageRepository.save(message);

        activityLogService.log(
                "ACCEPTE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " acceptée"
        );

        log.info("Transaction {} acceptée", message.getMsgId());
        return ResponseEntity.ok(message);
    }

    @PutMapping("/{id}/rejeter")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<SwiftMessage> rejectTransaction(
            @PathVariable Long id,
            @RequestBody(required = false) String motif) {
        SwiftMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée"));

        String rejectMotif = motif != null ? motif : "Rejeté par l'agent";
        swiftMessageSender.sendPacs002(message, "RJCT", rejectMotif);
        message.setStatus("REJETE");
        message.setNeedsAgentApproval(false);
        message.setRejectionReason(rejectMotif);
        message = messageRepository.save(message);

        activityLogService.log(
                "REJETE",
                "TRANSACTION",
                String.valueOf(message.getId()),
                "Transaction " + message.getMsgId() + " rejetée"
        );

        log.info("Transaction {} rejetée", message.getMsgId());
        return ResponseEntity.ok(message);
    }

    @GetMapping("/interbancaires")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<SwiftMessage>> getInterbancaireTransactions() {
        List<SwiftMessage> messages = messageRepository.findByMessageType("PACS009");
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Long>> getStats() {
        long total = messageRepository.count();
        long pacs008 = messageRepository.findByMessageType("PACS008").size();
        long pacs009 = messageRepository.findByMessageType("PACS009").size();
        long enAttente = messageRepository.countByStatus("EN_ATTENTE");
        long acceptees = messageRepository.countByStatus("ACCEPTE");
        long rejetees = messageRepository.countByStatus("REJETE");

        return ResponseEntity.ok(Map.of(
                "totalTransactions", total,
                "pacs008", pacs008,
                "pacs009", pacs009,
                "enAttente", enAttente,
                "acceptees", acceptees,
                "rejetees", rejetees
        ));
    }
}