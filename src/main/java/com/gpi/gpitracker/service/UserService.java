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

    @Transactional
    public AppUser createUser(UserCreateRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException(
                    "Email déjà utilisé : " + request.getEmail()
            );
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException(
                    "Nom d'utilisateur déjà utilisé : " + request.getUsername()
            );
        }

        String keycloakId = keycloakAdminService.createUser(
                request.getUsername(), request.getEmail(),
                request.getFirstName(), request.getLastName(),
                request.getPassword(), request.getRole()
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

        System.out.println("=== AVANT SAVE: " + user.getUsername());

        AppUser saved;
        try {
            saved = userRepository.save(user);
            System.out.println("=== SAVE OK, id: " + saved.getId());
        } catch (Exception e) {
            System.err.println("=== ERREUR SAVE DB: " + e.getMessage());
            e.printStackTrace();
            keycloakAdminService.deleteUser(keycloakId); // rollback Keycloak
            throw new RuntimeException("Erreur base de données: " + e.getMessage());
        }

        try {
            emailService.sendWelcomeEmail(
                    request.getEmail(), request.getFirstName(),
                    request.getUsername(), request.getPassword()
            );
            System.out.println("=== EMAIL OK");
        } catch (Exception e) {
            System.err.println("=== ERREUR EMAIL (non bloquant): " + e.getMessage());
            // Ne pas faire échouer la création pour un email raté
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
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur non trouvé : " + id)
                );

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

        if (userDetails.getActif() != null) user.setActif(userDetails.getActif());

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
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur non trouvé : " + id)
                );
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
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur non trouvé : " + id)
                );

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
        stats.setActiveUsers(
                allUsers.stream().filter(u -> u.getActif() == 1).count()
        );
        stats.setInactiveUsers(
                allUsers.stream().filter(u -> u.getActif() == 0).count()
        );
        stats.setAddedThisMonth(
                allUsers.stream()
                        .filter(u -> u.getDateCreation() != null &&
                                u.getDateCreation().isAfter(firstDayOfMonth))
                        .count()
        );
        stats.setByRole(
                allUsers.stream().collect(
                        Collectors.groupingBy(AppUser::getRole, Collectors.counting())
                )
        );
        stats.setRecentUsers(
                allUsers.stream()
                        .filter(u -> u.getDateCreation() != null)
                        .sorted((a, b) -> b.getDateCreation()
                                .compareTo(a.getDateCreation()))
                        .limit(5)
                        .collect(Collectors.toList())
        );

        Map<String, Long> registrationsByMonth = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = LocalDateTime.now()
                    .minusMonths(i).withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);
            String monthLabel = monthStart.getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            long count = allUsers.stream()
                    .filter(u -> u.getDateCreation() != null
                            && u.getDateCreation().isAfter(monthStart)
                            && u.getDateCreation().isBefore(monthEnd))
                    .count();
            registrationsByMonth.put(monthLabel, count);
        }
        stats.setRegistrationsByMonth(registrationsByMonth);

        return stats;
    }
}