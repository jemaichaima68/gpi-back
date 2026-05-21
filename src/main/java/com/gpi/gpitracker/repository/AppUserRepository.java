package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, String> {

    // Trouver par email
    Optional<AppUser> findByEmail(String email);

    // Trouver par keycloak ID
    Optional<AppUser> findByKeycloakId(String keycloakId);

    // Trouver par username
    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByIban(String iban);

    // Filtrer par rôle
    List<AppUser> findByRole(String role);

    // Filtrer par statut actif
    List<AppUser> findByActif(Integer actif);

    // Filtrer par rôle et statut
    List<AppUser> findByRoleAndActif(String role, Integer actif);


}