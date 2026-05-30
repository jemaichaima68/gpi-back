package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.DashboardStats;
import com.gpi.gpitracker.dto.UserCreateRequest;
import com.gpi.gpitracker.entity.AppUser;
import com.gpi.gpitracker.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    // ==================== CONSTANTES ====================
    private static final String USER_NOT_FOUND = "Utilisateur non trouvé : ";
    private static final String USER_ENTITY_TYPE = "USER";
    private static final String ERROR_SAVE_DB = "Erreur base de données: ";

    // Random réutilisable pour la génération de mots de passe
    private final Random random = new Random();

    // ==================== MÉTHODES DE LECTURE ====================

    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    public List<AppUser> getUsersByRole(String role) {
        return userRepository.findByRole(role);
    }

    public Optional<AppUser> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<AppUser> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    public Optional<AppUser> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // Recherche par IBAN
    public Optional<AppUser> getUserByIban(String iban) {
        if (iban == null || iban.isBlank()) return Optional.empty();
        return userRepository.findByIban(iban);
    }

    // ==================== GÉNÉRATION MOT DE PASSE ====================

    /**
     * Génère un mot de passe temporaire sécurisé
     */
    private String generateTemporaryPassword() {
        String uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lowercase = "abcdefghijkmnpqrstuvwxyz";
        String numbers = "23456789";
        String special = "!@#$%&*";
        String allChars = uppercase + lowercase + numbers + special;

        StringBuilder password = new StringBuilder();

        // Au moins 1 caractère de chaque catégorie
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(numbers.charAt(random.nextInt(numbers.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Compléter jusqu'à 12 caractères
        for (int i = password.length(); i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Mélanger le mot de passe
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    // ==================== CRÉATION UTILISATEUR ====================

    @Transactional
    public AppUser createUser(UserCreateRequest request) {
        // Vérifications existantes
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email déjà utilisé : " + request.getEmail());
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Nom d'utilisateur déjà utilisé : " + request.getUsername());
        }

        // Vérifier si l'IBAN existe déjà (pour les clients)
        if (request.getIban() != null && !request.getIban().isBlank()) {
            if (userRepository.findByIban(request.getIban()).isPresent()) {
                throw new IllegalArgumentException("IBAN déjà utilisé par un autre client");
            }
        }

        // Générer un mot de passe temporaire automatiquement
        String temporaryPassword = generateTemporaryPassword();
        log.info("=== Mot de passe temporaire généré pour: {}", request.getEmail());

        // Utiliser le mot de passe généré pour Keycloak
        String keycloakId = keycloakAdminService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                temporaryPassword,
                request.getRole(),
                request.getPhone()
        );

        log.info("=== KEYCLOAK OK, keycloakId: {}", keycloakId);

        AppUser user = new AppUser();
        user.setKeycloakId(keycloakId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        user.setActif(1);
        user.setPhone(request.getPhone());
        user.setBirthDate(request.getBirthDate());
        user.setAddress(request.getAddress());
        user.setCity(request.getCity());
        user.setPostalCode(request.getPostalCode());
        user.setCountry(request.getCountry());
        user.setIban(request.getIban());

        log.debug("=== AVANT SAVE: {}", user.getUsername());

        AppUser saved;
        try {
            saved = userRepository.save(user);
            log.info("=== SAVE OK, id: {}", saved.getId());
        } catch (Exception e) {
            log.error("=== ERREUR SAVE DB: {}", e.getMessage(), e);
            // Rollback Keycloak en cas d'erreur DB
            try {
                keycloakAdminService.deleteUser(keycloakId);
            } catch (Exception ex) {
                log.warn("Erreur lors du rollback Keycloak: {}", ex.getMessage());
            }
            throw new IllegalStateException(ERROR_SAVE_DB + e.getMessage(), e);
        }

        // Envoyer l'email avec le mot de passe généré
        try {
            String firstName = (request.getFirstName() != null && !request.getFirstName().isBlank())
                    ? request.getFirstName()
                    : request.getUsername();

            emailService.sendWelcomeEmail(
                    request.getEmail(),
                    firstName,
                    request.getUsername(),
                    temporaryPassword
            );
            log.info("=== EMAIL OK avec mot de passe généré");
        } catch (Exception e) {
            log.error("=== ERREUR EMAIL (non bloquant): {}", e.getMessage());
        }

        activityLogService.log(
                "CREATE", USER_ENTITY_TYPE, saved.getId(),
                "Utilisateur " + saved.getUsername() +
                        " créé avec le rôle " + saved.getRole()
        );

        return saved;
    }

    // ==================== MISE À JOUR UTILISATEUR ====================

    @Transactional
    public AppUser updateUser(String id, AppUser userDetails) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        String oldUsername = user.getUsername();
        String oldEmail = user.getEmail();

        user.setUsername(userDetails.getUsername());
        user.setEmail(userDetails.getEmail());
        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setRole(userDetails.getRole());

        if (userDetails.getPhone() != null) user.setPhone(userDetails.getPhone());
        if (userDetails.getBirthDate() != null) user.setBirthDate(userDetails.getBirthDate());
        if (userDetails.getAddress() != null) user.setAddress(userDetails.getAddress());
        if (userDetails.getCity() != null) user.setCity(userDetails.getCity());
        if (userDetails.getPostalCode() != null) user.setPostalCode(userDetails.getPostalCode());
        if (userDetails.getCountry() != null) user.setCountry(userDetails.getCountry());

        // Mise à jour du statut
        if (userDetails.getActif() != null) user.setActif(userDetails.getActif());

        // Mise à jour dans Keycloak (sans changer le mot de passe)
        try {
            keycloakAdminService.updateUser(
                    user.getKeycloakId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    userDetails.getFirstName(),
                    userDetails.getLastName(),
                    userDetails.getPhone()
            );
            log.info("Utilisateur Keycloak mis à jour: {}", user.getKeycloakId());
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour Keycloak: {}", e.getMessage());
            // On continue car l'utilisateur local peut être mis à jour quand même
        }

        AppUser updated = userRepository.save(user);

        activityLogService.log(
                "UPDATE", USER_ENTITY_TYPE, id,
                "Utilisateur " + oldUsername + " → " + updated.getUsername() + " modifié"
        );

        return updated;
    }

    // ==================== CHANGEMENT DE STATUT ====================

    @Transactional
    public AppUser toggleUserStatus(String id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));
        int newStatus = user.getActif() == 1 ? 0 : 1;
        user.setActif(newStatus);

        try {
            keycloakAdminService.updateUserStatus(
                    user.getKeycloakId(), newStatus == 1
            );
            log.info("Statut Keycloak mis à jour: {} -> {}", user.getUsername(), newStatus == 1 ? "actif" : "inactif");
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour du statut Keycloak: {}", e.getMessage());
            // On continue car l'utilisateur local peut être mis à jour quand même
        }

        AppUser updated = userRepository.save(user);

        activityLogService.log(
                newStatus == 1 ? "ACTIVATE" : "DEACTIVATE",
                USER_ENTITY_TYPE, id,
                "Utilisateur " + updated.getUsername() +
                        (newStatus == 1 ? " activé" : " désactivé")
        );

        return updated;
    }

    // ==================== SUPPRESSION UTILISATEUR (CORRIGÉE) ====================

    @Transactional
    public void deleteUser(String id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        String username = user.getUsername();
        String keycloakId = user.getKeycloakId();

        log.info("=== SUPPRESSION UTILISATEUR: {} (keycloakId: {})", username, keycloakId);

        // 1. Supprimer d'abord de la base de données locale
        try {
            userRepository.deleteById(id);
            log.info("✅ Utilisateur supprimé de la base locale: {}", username);
        } catch (Exception e) {
            log.error("❌ Erreur lors de la suppression locale: {}", e.getMessage());
            throw new IllegalStateException("Impossible de supprimer l'utilisateur de la base: " + e.getMessage(), e);
        }

        // 2. Essayer de supprimer de Keycloak (non bloquant)
        if (keycloakId != null && !keycloakId.isBlank()) {
            try {
                keycloakAdminService.deleteUser(keycloakId);
                log.info("✅ Utilisateur Keycloak supprimé: {}", keycloakId);
            } catch (Exception e) {
                // L'utilisateur est déjà supprimé ou n'existe pas - ce n'est pas bloquant
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("404") ||
                        errorMsg.contains("Not Found") ||
                        errorMsg.contains("not found") ||
                        errorMsg.contains("does not exist"))) {
                    log.warn("⚠️ L'utilisateur Keycloak n'existait pas déjà: {}", keycloakId);
                } else {
                    log.error("⚠️ Erreur non bloquante lors de la suppression Keycloak: {}", e.getMessage());
                }
                // On ne relance pas l'exception car l'utilisateur local est déjà supprimé
            }
        } else {
            log.warn("⚠️ Aucun keycloakId pour l'utilisateur: {}", username);
        }

        // 3. Logger l'action
        activityLogService.log(
                "DELETE", USER_ENTITY_TYPE, id,
                "Utilisateur " + username + " supprimé" +
                        (keycloakId != null ? " (Keycloak: " + keycloakId + ")" : "")
        );

        log.info("=== SUPPRESSION TERMINÉE: {}", username);
    }

    // ==================== STATISTIQUES DASHBOARD ====================

    public DashboardStats getDashboardStats() {
        List<AppUser> allUsers = userRepository.findAll();
        LocalDateTime firstDayOfMonth = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        DashboardStats stats = new DashboardStats();
        stats.setTotalUsers(allUsers.size());
        stats.setActiveUsers(allUsers.stream().filter(u -> u.getActif() == 1).count());
        stats.setInactiveUsers(allUsers.stream().filter(u -> u.getActif() == 0).count());
        stats.setAddedThisMonth(allUsers.stream()
                .filter(u -> u.getDateCreation() != null && u.getDateCreation().isAfter(firstDayOfMonth))
                .count());
        stats.setByRole(allUsers.stream().collect(Collectors.groupingBy(AppUser::getRole, Collectors.counting())));
        stats.setRecentUsers(allUsers.stream()
                .filter(u -> u.getDateCreation() != null)
                .sorted((a, b) -> b.getDateCreation().compareTo(a.getDateCreation()))
                .limit(5)
                .toList());

        Map<String, Long> registrationsByMonth = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = LocalDateTime.now().minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);
            String monthLabel = monthStart.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            long count = allUsers.stream()
                    .filter(u -> u.getDateCreation() != null && u.getDateCreation().isAfter(monthStart) && u.getDateCreation().isBefore(monthEnd))
                    .count();
            registrationsByMonth.put(monthLabel, count);
        }
        stats.setRegistrationsByMonth(registrationsByMonth);

        return stats;
    }
}