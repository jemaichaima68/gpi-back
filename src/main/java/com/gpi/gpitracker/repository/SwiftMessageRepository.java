package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.SwiftMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SwiftMessageRepository extends JpaRepository<SwiftMessage, Long> {

    boolean existsByMsgId(String msgId);

    Optional<SwiftMessage> findByMsgId(String msgId);

    List<SwiftMessage> findByMessageType(String messageType);

    // Méthode 1 : convention de nommage
    List<SwiftMessage> findAllByOrderByReceivedAtDesc();

    // Méthode 2 : avec @Query (si la méthode 1 ne fonctionne pas)
    @Query("SELECT m FROM SwiftMessage m ORDER BY m.receivedAt DESC")
    List<SwiftMessage> findAllOrderByReceivedAtDesc();
    List<SwiftMessage> findByStatus(String status);
    List<SwiftMessage> findByStatusIn(List<String> statuses);
    long countByStatus(String status);
}