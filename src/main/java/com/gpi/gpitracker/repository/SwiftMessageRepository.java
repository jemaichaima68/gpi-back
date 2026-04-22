package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.SwiftMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SwiftMessageRepository extends JpaRepository<SwiftMessage, Long> {

    boolean existsByMsgId(String msgId);

    Optional<SwiftMessage> findByMsgId(String msgId);

    List<SwiftMessage> findByMessageType(String messageType);

    List<SwiftMessage> findAllByOrderByReceivedAtDesc();

    @Query("SELECT m FROM SwiftMessage m ORDER BY m.receivedAt DESC")
    List<SwiftMessage> findAllOrderByReceivedAtDesc();

    List<SwiftMessage> findByStatus(String status);

    List<SwiftMessage> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    // ==================== MÉTHODES DE FILTRAGE PAR DATE ====================

    /**
     * Filtrer les transactions par date exacte
     */
    @Query("SELECT m FROM SwiftMessage m WHERE DATE(m.receivedAt) = :date")
    List<SwiftMessage> findByReceivedDate(@Param("date") LocalDate date);

    /**
     * Filtrer les transactions par plage de dates
     */
    List<SwiftMessage> findByReceivedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Filtrer les transactions reçues après une date donnée
     */
    List<SwiftMessage> findByReceivedAtAfter(LocalDateTime date);

    /**
     * Filtrer les transactions reçues avant une date donnée
     */
    List<SwiftMessage> findByReceivedAtBefore(LocalDateTime date);

    /**
     * Compter les transactions par date (pour les graphiques)
     */
    @Query("SELECT DATE(m.receivedAt), COUNT(m) FROM SwiftMessage m GROUP BY DATE(m.receivedAt) ORDER BY DATE(m.receivedAt) DESC")
    List<Object[]> countTransactionsByDate();
}