package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "APP_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "KEYCLOAK_ID", unique = true, nullable = false, length = 36)
    private String keycloakId;

    @Column(name = "USERNAME", nullable = false, length = 100)
    private String username;

    @Column(name = "EMAIL", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "FIRST_NAME", length = 100)
    private String firstName;

    @Column(name = "LAST_NAME", length = 100)
    private String lastName;

    @Column(name = "ROLE", nullable = false, length = 50)
    private String role;

    @Column(name = "ACTIF")
    private Integer actif = 1;

    // New fields
    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "BIRTH_DATE")
    private LocalDate birthDate;

    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "COUNTRY", length = 2)
    private String country;

    @Column(name = "DATE_CREATION")
    private LocalDateTime dateCreation;

    @Column(name = "DATE_MODIFICATION")
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }
}