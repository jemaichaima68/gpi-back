package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.*;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.service.BankJourneyService;
import com.gpi.gpitracker.service.ClientService;
import com.gpi.gpitracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final NotificationService notificationService;
    private final BankJourneyService bankJourneyService;  // ✅ نضيف هذا

    // ========== MÉTHODES POUR RÉCUPÉRER L'UTILISATEUR CONNECTÉ ==========

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) return email;
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) return preferredUsername;
        }
        return "unknown";
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) return username;
        }
        return "unknown";
    }

    // ==================== DASHBOARD ====================
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientDashboardDto> getDashboard() {
        return ResponseEntity.ok(clientService.getClientDashboard(getCurrentUserEmail()));
    }

    // ==================== TRANSFERTS ====================
    @GetMapping("/transfers/{uetr}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TransferResponseDto> getTransferByUetr(@PathVariable String uetr) {
        String clientEmail = getCurrentUserEmail();

        TransferResponseDto transfer = clientService.getTransferByUetr(uetr, clientEmail).orElse(null);
        if (transfer == null) {
            return ResponseEntity.notFound().build();
        }

        // ✅ جلب المسار البنكي الخاص بهذا التحويل فقط
        List<BankJourneyDto> bankJourney = bankJourneyService.getBankJourneyByUetr(uetr, clientEmail);
        transfer.setBankJourney(bankJourney);

        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TransferResponseDto> getTransactionById(@PathVariable Long id) {
        String clientEmail = getCurrentUserEmail();

        TransferResponseDto transfer = clientService.getTransactionById(id, clientEmail).orElse(null);
        if (transfer == null) {
            return ResponseEntity.notFound().build();
        }


        List<BankJourneyDto> bankJourney = bankJourneyService.getBankJourneyByTransactionId(id, clientEmail);
        transfer.setBankJourney(bankJourney);

        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/transactions/{id}/timeline")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<TransactionTimelineDto>> getTransactionTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getTransactionTimeline(id, getCurrentUserEmail()));
    }

    @GetMapping("/transactions/{id}/rejection-reason")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, String>> getRejectionReason(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getRejectionReason(id, getCurrentUserEmail()));
    }

    // ==================== HISTORIQUE ====================
    @GetMapping("/history")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ConsultationHistoryDto>> getHistory() {
        return ResponseEntity.ok(clientService.getConsultationHistory(getCurrentUserEmail()));
    }

    @DeleteMapping("/history/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> deleteConsultationHistory(@PathVariable Long id) {
        String clientEmail = getCurrentUserEmail();
        boolean deleted = clientService.deleteConsultationHistory(id, clientEmail);

        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Consultation supprimée", "id", id));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Consultation non trouvée"));
        }
    }

    @DeleteMapping("/history")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> deleteAllConsultationHistory() {
        String clientEmail = getCurrentUserEmail();
        int deletedCount = clientService.deleteAllConsultationHistory(clientEmail);
        return ResponseEntity.ok(Map.of("success", true, "message", deletedCount + " consultation(s) supprimée(s)", "deletedCount", deletedCount));
    }

    // ==================== NOTIFICATIONS ====================
    @GetMapping("/notifications")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ClientNotificationDto>> getNotifications() {
        return ResponseEntity.ok(notificationService.getCurrentClientNotifications(getCurrentUserEmail()));
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Long> getUnreadCount() {
        long count = notificationService.getUnreadCount(getCurrentUserEmail());
        return ResponseEntity.ok(count);
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(@PathVariable Long id) {
        notificationService.markNotificationAsRead(id, getCurrentUserEmail());
        return ResponseEntity.ok(Map.of("id", id, "read", true, "message", "Notification marquée comme lue"));
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> markAllNotificationsAsRead() {
        notificationService.markAllNotificationsAsRead(getCurrentUserEmail());
        return ResponseEntity.ok(Map.of("message", "Toutes les notifications sont marquées comme lues"));
    }

    // ==================== PROFIL ====================
    @GetMapping("/profile")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", getCurrentUserEmail());
        profile.put("username", getCurrentUsername());
        profile.put("fullName", getCurrentUsername());
        profile.put("role", "CLIENT");
        profile.put("memberSince", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        return ResponseEntity.ok(profile);
    }

    // ==================== FILTRAGE ====================
    @GetMapping("/transactions/filter")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<SwiftMessage>> filterTransactions(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String creditorCountry) {

        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate, dtf) : null;
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate, dtf) : null;
        return ResponseEntity.ok(clientService.filterClientTransactions(getCurrentUserEmail(), start, end, minAmount, maxAmount, status, creditorCountry));
    }

    // ==================== EXPORTS ====================
    @GetMapping("/transactions/export/pdf")
    @PreAuthorize("hasRole('CLIENT')")
    public void exportTransactionsPdf(HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=transactions.pdf");
        clientService.exportTransactionsPdf(response);
    }

    @GetMapping("/transactions/export/excel")
    @PreAuthorize("hasRole('CLIENT')")
    public void exportTransactionsExcel(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=transactions.xlsx");
        clientService.exportTransactionsExcel(response);
    }
}