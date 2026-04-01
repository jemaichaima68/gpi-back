package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.DashboardStats;
import com.gpi.gpitracker.dto.UserCreateRequest;
import com.gpi.gpitracker.entity.AppUser;
import com.gpi.gpitracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ===== DASHBOARD STATS =====
    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(userService.getDashboardStats());
    }

    // ===== LISTER TOUS LES UTILISATEURS =====
    @GetMapping("/users")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<AppUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ===== FILTRER PAR RÔLE =====
    @GetMapping("/users/role/{role}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<AppUser>> getUsersByRole(
            @PathVariable String role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    // ===== TROUVER PAR ID =====
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<AppUser> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== CRÉER UN UTILISATEUR =====
    @PostMapping("/users")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> createUser(
            @RequestBody UserCreateRequest request) {
        try {
            AppUser created = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== MODIFIER UN UTILISATEUR =====
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> updateUser(
            @PathVariable String id,
            @RequestBody AppUser userDetails) {
        try {
            AppUser updated = userService.updateUser(id, userDetails);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== ACTIVER / DÉSACTIVER =====
    @PatchMapping("/users/{id}/toggle")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> toggleStatus(@PathVariable String id) {
        try {
            AppUser updated = userService.toggleUserStatus(id);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== SUPPRIMER =====
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}