package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, String> {
    Optional<AdminProfile> findByKeycloakId(String keycloakId);
}