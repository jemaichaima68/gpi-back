package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.ActivityLog;
import com.gpi.gpitracker.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository logRepository;

    /**
     * Récupère automatiquement le username depuis le token Keycloak JWT.
     * Retourne "unknown" si aucun utilisateur connecté.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "unknown";
        }
        // Token JWT Keycloak → preferred_username
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }
        // Fallback sur le nom du principal
        return auth.getName();
    }

    /**
     * Log automatique : performedBy est extrait du token Keycloak.
     */
    public void log(String action, String entityType,
                    String entityId, String description) {
        ActivityLog log = new ActivityLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setPerformedBy(getCurrentUsername()); // ← toujours le vrai utilisateur
        logRepository.save(log);
    }

    /**
     * Log avec performedBy explicite (si besoin depuis un contexte sans auth).
     */
    public void log(String action, String entityType,
                    String entityId, String description,
                    String performedBy) {
        ActivityLog log = new ActivityLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setPerformedBy(performedBy);
        logRepository.save(log);
    }

    public List<ActivityLog> getAllLogs() {
        return logRepository.findAllByOrderByDateActionDesc();
    }

    public List<ActivityLog> getLogsByAction(String action) {
        return logRepository.findByActionOrderByDateActionDesc(action);
    }
    public List<ActivityLog> getLogsByEntityType(String entityType) {
        return logRepository.findByEntityTypeOrderByDateActionDesc(entityType);
    }
}