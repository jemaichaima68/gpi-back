// AgentLogController.java - NOUVEAU FICHIER
package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.entity.ActivityLog;
import com.gpi.gpitracker.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/logs")
@RequiredArgsConstructor
public class AgentLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<ActivityLog>> getTransactionLogs() {
        // Retourne uniquement les logs concernant les transactions
        List<ActivityLog> transactionLogs = activityLogService.getLogsByEntityType("TRANSACTION");
        return ResponseEntity.ok(transactionLogs);
    }
}