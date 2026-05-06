package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.ClientNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientNotificationRepository extends JpaRepository<ClientNotification, Long> {

    List<ClientNotification> findByClientEmailOrderByCreatedAtDesc(String clientEmail);

    List<ClientNotification> findByClientEmailAndReadFalseOrderByCreatedAtDesc(String clientEmail);

    Optional<ClientNotification> findByIdAndClientEmail(Long id, String clientEmail);

    long countByClientEmailAndReadFalse(String clientEmail);
}