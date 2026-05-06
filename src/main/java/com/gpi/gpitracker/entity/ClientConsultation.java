package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CLIENT_CONSULTATIONS")
public class ClientConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clientEmail;

    @Column(nullable = false)
    private String uetr;

    @Column(nullable = false)
    private LocalDateTime consultedAt;

    public ClientConsultation() {}

    public ClientConsultation(String clientEmail, String uetr, LocalDateTime consultedAt) {
        this.clientEmail = clientEmail;
        this.uetr = uetr;
        this.consultedAt = consultedAt;
    }

    @PrePersist
    public void onCreate() {
        if (consultedAt == null) {
            consultedAt = LocalDateTime.now();
        }
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }
    public String getUetr() { return uetr; }
    public void setUetr(String uetr) { this.uetr = uetr; }
    public LocalDateTime getConsultedAt() { return consultedAt; }
    public void setConsultedAt(LocalDateTime consultedAt) { this.consultedAt = consultedAt; }
}