package com.gpi.gpitracker.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String adminRealm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.target-realm}")
    private String targetRealm;

    private Keycloak getKeycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(clientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    /**
     * Trouve un rôle dans Keycloak de façon insensible à la casse
     */
    private RoleRepresentation findRoleIgnoreCase(RealmResource realm, String roleName) {
        List<RoleRepresentation> allRoles = realm.roles().list();
        return allRoles.stream()
                .filter(r -> r.getName().equalsIgnoreCase(roleName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rôle introuvable dans Keycloak : " + roleName));
    }

    /**
     * Create a new user in Keycloak
     */
    public String createUser(String username, String email,
                             String firstName, String lastName,
                             String password, String role) {
        return createUser(username, email, firstName, lastName, password, role, null);
    }

    /**
     * Create a new user in Keycloak with phone number
     */
    public String createUser(String username, String email,
                             String firstName, String lastName,
                             String password, String role,
                             String phone) {
        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            UsersResource users = realm.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);

            // Add phone as attribute if provided
            if (phone != null && !phone.isEmpty()) {
                Map<String, List<String>> attributes = new HashMap<>();
                attributes.put("phone", Collections.singletonList(phone));
                user.setAttributes(attributes);
            }

            // Set temporary password
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(true);
            user.setCredentials(Collections.singletonList(credential));

            try (Response response = users.create(user)) {
                if (response.getStatus() == 409) {
                    throw new IllegalArgumentException(
                            "Ce nom d'utilisateur ou email existe déjà dans Keycloak"
                    );
                }

                if (response.getStatus() != 201) {
                    throw new IllegalStateException(
                            "Erreur création Keycloak: " + response.getStatus()
                    );
                }

                String path = response.getLocation().getPath();
                String keycloakId = path.substring(path.lastIndexOf('/') + 1);

                // Assign role — insensible à la casse
                RoleRepresentation roleRep = findRoleIgnoreCase(realm, role);
                users.get(keycloakId).roles().realmLevel()
                        .add(Collections.singletonList(roleRep));

                return keycloakId;
            }
        } finally {
            keycloak.close();
        }
    }

    /**
     * Update user in Keycloak
     */
    public void updateUser(String keycloakId, String username,
                           String email, String firstName,
                           String lastName) {
        updateUser(keycloakId, username, email, firstName, lastName, null);
    }

    /**
     * Update user in Keycloak with phone number
     */
    public void updateUser(String keycloakId, String username,
                           String email, String firstName,
                           String lastName, String phone) {
        Keycloak keycloak = getKeycloak();
        try {
            UserRepresentation user = keycloak.realm(targetRealm)
                    .users().get(keycloakId).toRepresentation();

            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);

            // Update phone attribute if provided
            if (phone != null) {
                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }
                attributes.put("phone", Collections.singletonList(phone));
                user.setAttributes(attributes);
            }

            keycloak.realm(targetRealm).users()
                    .get(keycloakId).update(user);
        } finally {
            keycloak.close();
        }
    }

    /**
     * Delete user from Keycloak
     */
    public void deleteUser(String keycloakId) {
        Keycloak keycloak = getKeycloak();
        try {
            keycloak.realm(targetRealm).users()
                    .get(keycloakId).remove();
        } finally {
            keycloak.close();
        }
    }

    /**
     * Update user status (enable/disable)
     */
    public void updateUserStatus(String keycloakId, boolean enabled) {
        Keycloak keycloak = getKeycloak();
        try {
            UserRepresentation user = keycloak.realm(targetRealm)
                    .users().get(keycloakId).toRepresentation();
            user.setEnabled(enabled);
            keycloak.realm(targetRealm).users()
                    .get(keycloakId).update(user);
        } finally {
            keycloak.close();
        }
    }

    /**
     * Reset user password
     */
    public void resetPassword(String keycloakId, String newPassword, boolean temporary) {
        Keycloak keycloak = getKeycloak();
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(temporary);

            keycloak.realm(targetRealm).users()
                    .get(keycloakId).resetPassword(credential);
        } finally {
            keycloak.close();
        }
    }

    /**
     * Get user by username
     */
    public Optional<UserRepresentation> getUserByUsername(String username) {
        Keycloak keycloak = getKeycloak();
        try {
            List<UserRepresentation> users = keycloak.realm(targetRealm)
                    .users().search(username, true);
            return users.stream().findFirst();
        } finally {
            keycloak.close();
        }
    }

    /**
     * Get user by email
     */
    public Optional<UserRepresentation> getUserByEmail(String email) {
        Keycloak keycloak = getKeycloak();
        try {
            List<UserRepresentation> users = keycloak.realm(targetRealm)
                    .users().search(null, null, null, email, 0, 1);
            return users.stream().findFirst();
        } finally {
            keycloak.close();
        }
    }

    /**
     * Assign role to user — insensible à la casse
     */
    public void assignRole(String keycloakId, String roleName) {
        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            RoleRepresentation role = findRoleIgnoreCase(realm, roleName);
            realm.users().get(keycloakId).roles().realmLevel()
                    .add(Collections.singletonList(role));
        } finally {
            keycloak.close();
        }
    }

    /**
     * Remove role from user — insensible à la casse
     */
    public void removeRole(String keycloakId, String roleName) {
        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            RoleRepresentation role = findRoleIgnoreCase(realm, roleName);
            realm.users().get(keycloakId).roles().realmLevel()
                    .remove(Collections.singletonList(role));
        } finally {
            keycloak.close();
        }
    }

    /**
     * Get user roles
     */
    public List<String> getUserRoles(String keycloakId) {
        Keycloak keycloak = getKeycloak();
        try {
            List<RoleRepresentation> roles = keycloak.realm(targetRealm)
                    .users().get(keycloakId).roles().realmLevel().listEffective();
            return roles.stream()
                    .map(RoleRepresentation::getName)
                    .toList();
        } finally {
            keycloak.close();
        }
    }

    // ==================== MÉTHODE POUR CHERCHER EMAIL PAR NOM/USERNAME ====================

    /**
     * Cherche l'email d'un utilisateur Keycloak par son username ou nom complet
     * @param fullName Le username ou nom complet (ex: "foufa" ou "Haifa Jerbi")
     * @return L'email de l'utilisateur, ou null si non trouvé
     */
    public String getEmailByFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }

        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            UsersResource users = realm.users();

            log.info("🔍 Recherche email pour: '{}'", fullName);

            // 1. Chercher par USERNAME (le plus simple et unique)
            List<UserRepresentation> byUsername = users.search(fullName, true);
            if (!byUsername.isEmpty()) {
                String email = byUsername.get(0).getEmail();
                if (email != null && !email.isBlank()) {
                    log.info("✅ Email trouvé par username: {} -> {}", fullName, email);
                    return email;
                }
            }

            // 2. Fallback: chercher par nom complet (first name + last name)
            List<UserRepresentation> results = users.search(fullName, 0, 10);
            if (!results.isEmpty()) {
                String email = results.get(0).getEmail();
                if (email != null && !email.isBlank()) {
                    log.info(" Email trouvé par recherche: {} -> {}", fullName, email);
                    return email;
                }
            }

            // 3. Fallback: chercher par prénom + nom séparés
            String[] parts = fullName.trim().split(" ");
            if (parts.length >= 2) {
                String firstName = parts[0];
                String lastName = parts[1];

                List<UserRepresentation> byFirstName = users.search(firstName, 0, 10);
                for (UserRepresentation user : byFirstName) {
                    if (firstName.equalsIgnoreCase(user.getFirstName()) &&
                            lastName.equalsIgnoreCase(user.getLastName())) {
                        String email = user.getEmail();
                        if (email != null && !email.isBlank()) {
                            log.info(" Email trouvé par prénom+nom: {} -> {}", fullName, email);
                            return email;
                        }
                    }
                }
            }

            log.warn(" Aucun email trouvé pour: {}", fullName);
            return null;
        } finally {
            keycloak.close();
        }
    }
}