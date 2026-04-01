package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.AdminProfileDto;
import com.gpi.gpitracker.service.AdminProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/admin/profile")
public class AdminProfileController {

    private final AdminProfileService service;

    public AdminProfileController(AdminProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AdminProfileDto> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return ResponseEntity.ok(service.getProfile(keycloakId));
    }

    @PutMapping
    public ResponseEntity<AdminProfileDto> saveProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminProfileDto dto) {
        String keycloakId = jwt.getSubject();
        return ResponseEntity.ok(service.saveProfile(keycloakId, dto));
    }
}