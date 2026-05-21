package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.DashboardStats;
import com.gpi.gpitracker.dto.UserCreateRequest;
import com.gpi.gpitracker.entity.AppUser;
import com.gpi.gpitracker.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

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
        Random random = new Random();

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

    @Transactional
    public AppUser createUser(UserCreateRequest request) {
        // Vérifications existantes
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email déjà utilisé : " + request.getEmail());
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Nom d'utilisateur déjà utilisé : " + request.getUsername());
        }

        // Vérifier si l'IBAN existe déjà (pour les clients)
        if (request.getIban() != null && !request.getIban().isBlank()) {
            if (userRepository.findByIban(request.getIban()).isPresent()) {
                throw new RuntimeException("IBAN déjà utilisé par un autre client");
            }
        }

        // ✅ GÉNÉRER UN MOT DE PASSE TEMPORAIRE AUTOMATIQUEMENT
        String temporaryPassword = generateTemporaryPassword();
        System.out.println("=== Mot de passe temporaire généré pour: " + request.getEmail());
        System.out.println("=== Mot de passe: " + temporaryPassword);

        // ✅ Utiliser le mot de passe généré pour Keycloak (IGNORER celui du frontend)
        String keycloakId = keycloakAdminService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                temporaryPassword,  // ← Utiliser le mot de passe généré
                request.getRole()
        );

        System.out.println("=== KEYCLOAK OK, keycloakId: " + keycloakId);

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

        System.out.println("=== AVANT SAVE: " + user.getUsername());

        AppUser saved;
        try {
            saved = userRepository.save(user);
            System.out.println("=== SAVE OK, id: " + saved.getId());
        } catch (Exception e) {
            System.err.println("=== ERREUR SAVE DB: " + e.getMessage());
            e.printStackTrace();
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("Erreur base de données: " + e.getMessage());
        }

        // ✅ Envoyer l'email avec le mot de passe GÉNÉRÉ (pas celui du frontend)
        try {
            String firstName = (request.getFirstName() != null && !request.getFirstName().isBlank())
                    ? request.getFirstName()
                    : request.getUsername();

            emailService.sendWelcomeEmail(
                    request.getEmail(),
                    firstName,
                    request.getUsername(),
                    temporaryPassword  // ← Utiliser le mot de passe généré
            );
            System.out.println("=== EMAIL OK avec mot de passe généré");
        } catch (Exception e) {
            System.err.println("=== ERREUR EMAIL (non bloquant): " + e.getMessage());
        }

        activityLogService.log(
                "CREATE", "USER", saved.getId(),
                "Utilisateur " + saved.getUsername() +
                        " créé avec le rôle " + saved.getRole()
        );

        return saved;
    }

    @Transactional
    public AppUser updateUser(String id, AppUser userDetails) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + id));

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

        // ⚠️ NE PAS MODIFIER L'IBAN EN MODIFICATION (sécurité)
        // L'IBAN ne peut être modifié que si c'est explicitement autorisé
        // Pour plus de sécurité, on ignore l'IBAN dans updateUser

        // Mise à jour du statut
        if (userDetails.getActif() != null) user.setActif(userDetails.getActif());

        // Mise à jour dans Keycloak (sans changer le mot de passe)
        keycloakAdminService.updateUser(
                user.getKeycloakId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName()
        );

        AppUser updated = userRepository.save(user);

        activityLogService.log(
                "UPDATE", "USER", id,
                "Utilisateur " + updated.getUsername() + " modifié"
        );

        return updated;
    }

    @Transactional
    public AppUser toggleUserStatus(String id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + id));
        int newStatus = user.getActif() == 1 ? 0 : 1;
        user.setActif(newStatus);
        keycloakAdminService.updateUserStatus(
                user.getKeycloakId(), newStatus == 1
        );
        AppUser updated = userRepository.save(user);

        activityLogService.log(
                newStatus == 1 ? "ACTIVATE" : "DEACTIVATE",
                "USER", id,
                "Utilisateur " + updated.getUsername() +
                        (newStatus == 1 ? " activé" : " désactivé")
        );

        return updated;
    }

    @Transactional
    public void deleteUser(String id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + id));

        activityLogService.log(
                "DELETE", "USER", id,
                "Utilisateur " + user.getUsername() + " supprimé"
        );

        keycloakAdminService.deleteUser(user.getKeycloakId());
        userRepository.deleteById(id);
    }

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
                .collect(Collectors.toList()));

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