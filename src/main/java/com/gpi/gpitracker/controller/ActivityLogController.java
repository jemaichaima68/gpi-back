package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.entity.ActivityLog;
import com.gpi.gpitracker.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<ActivityLog>> getAllLogs() {
        return ResponseEntity.ok(activityLogService.getAllLogs());
    }

    @GetMapping("/action/{action}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<ActivityLog>> getLogsByAction(
            @PathVariable String action) {
        return ResponseEntity.ok(
                activityLogService.getLogsByAction(action)
        );
    }
}