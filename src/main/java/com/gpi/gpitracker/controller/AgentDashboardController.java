package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.RecentTransactionDto;
import com.gpi.gpitracker.service.AgentDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentDashboardController {

    private final AgentDashboardService service;

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(service.getDashboardStatsV2());
    }

    @GetMapping("/dashboard/stats-v2")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getStatsV2() {
        return ResponseEntity.ok(service.getDashboardStatsV2());
    }

    @GetMapping("/dashboard/today-stats")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getTodayStats() {
        return ResponseEntity.ok(service.getTodayActivityStats());
    }

    @GetMapping("/dashboard/activity")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<Map<String, Object>> getActivityData(
            @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(service.getActivityData(period));
    }

    @GetMapping("/messages/pending")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<RecentTransactionDto>> getPendingMessages(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getPendingMessages(limit));
    }

    @GetMapping("/messages/recent-processed")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<RecentTransactionDto>> getRecentProcessedMessages(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(service.getRecentProcessedMessages(limit));
    }

    @GetMapping("/messages/recent")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<RecentTransactionDto>> getRecentMessages(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(service.getRecentProcessedMessages(limit));
    }
}