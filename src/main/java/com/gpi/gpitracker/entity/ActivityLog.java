package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACTIVITY_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    @Column(name = "ENTITY_TYPE", length = 50)
    private String entityType;

    @Column(name = "ENTITY_ID", length = 36)
    private String entityId;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "PERFORMED_BY", length = 100)
    private String performedBy;

    @Column(name = "DATE_ACTION")
    private LocalDateTime dateAction;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (dateAction == null) {
            dateAction = LocalDateTime.now();
        }
    }
}