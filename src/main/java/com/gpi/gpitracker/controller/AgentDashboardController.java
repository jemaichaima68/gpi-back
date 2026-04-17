package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.AgentDashboardStatsDto;
import com.gpi.gpitracker.dto.RecentTransactionDto;
import com.gpi.gpitracker.service.AgentDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;  // ← AJOUTER CET IMPORT

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentDashboardController {

    private final AgentDashboardService service;

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<AgentDashboardStatsDto> getStats() {
        return ResponseEntity.ok(service.getDashboardStats());
    }


}