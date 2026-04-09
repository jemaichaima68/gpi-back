package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.SwiftMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SwiftMessageRepository extends JpaRepository<SwiftMessage, Long> {

    // Vérifie si un message avec ce MsgId existe déjà (évite les doublons)
    boolean existsByMsgId(String msgId);

    // Cherche par identifiant de message
    Optional<SwiftMessage> findByMsgId(String msgId);

    // Filtre par type de message : PACS008, PACS009, PACS002...
    List<SwiftMessage> findByMessageType(String messageType);
}