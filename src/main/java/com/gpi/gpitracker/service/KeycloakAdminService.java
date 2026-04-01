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
import java.util.*;

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

            Response response = users.create(user);

            if (response.getStatus() == 409) {
                throw new RuntimeException(
                        "Ce nom d'utilisateur ou email existe déjà dans Keycloak"
                );
            }

            if (response.getStatus() != 201) {
                throw new RuntimeException(
                        "Erreur création Keycloak: " + response.getStatus()
                );
            }

            String path = response.getLocation().getPath();
            String keycloakId = path.substring(path.lastIndexOf('/') + 1);

            // Assign role
            RoleRepresentation roleRep = realm.roles()
                    .get(role).toRepresentation();
            users.get(keycloakId).roles().realmLevel()
                    .add(Collections.singletonList(roleRep));

            return keycloakId;
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
     * Assign role to user
     */
    public void assignRole(String keycloakId, String roleName) {
        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
            realm.users().get(keycloakId).roles().realmLevel()
                    .add(Collections.singletonList(role));
        } finally {
            keycloak.close();
        }
    }

    /**
     * Remove role from user
     */
    public void removeRole(String keycloakId, String roleName) {
        Keycloak keycloak = getKeycloak();
        try {
            RealmResource realm = keycloak.realm(targetRealm);
            RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
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
}