package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.AppSettingsDto;
import com.gpi.gpitracker.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AppSettingsController {

    private final AppSettingsService service;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<AppSettingsDto> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<AppSettingsDto> saveSettings(
            @RequestBody AppSettingsDto dto) {
        return ResponseEntity.ok(service.saveSettings(dto));
    }
}