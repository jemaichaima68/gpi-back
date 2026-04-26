package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "BANK_DIRECTORY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDirectory {

    @Id
    @Column(name = "BIC", length = 11, nullable = false, unique = true)
    private String bic;

    @Column(name = "BANK_NAME", length = 200)
    private String bankName;

    @Column(name = "COUNTRY_CODE", length = 2)
    private String countryCode;

    @Column(name = "FIRST_SEEN_AT")
    private LocalDateTime firstSeenAt;

    @Column(name = "LAST_SEEN_AT")
    private LocalDateTime lastSeenAt;

    @Column(name = "OCCURRENCE_COUNT")
    private Integer occurrenceCount = 1;
    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "SWIFT_BRANCH_CODE", length = 3)
    private String swiftBranchCode;

    @Column(name = "SUPPORTS_GPI")
    private Boolean supportsGpi;

    @Column(name = "ACTIVE")
    private Boolean active;

    @Column(name = "BANK_TYPE", length = 50)
    private String bankType;

    @PrePersist
    protected void onCreate() {
        firstSeenAt = LocalDateTime.now();
        lastSeenAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }
}