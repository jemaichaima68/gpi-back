package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.SwiftMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SwiftMessageRepository extends JpaRepository<SwiftMessage, Long> {

    boolean existsByMsgId(String msgId);

    boolean existsByUetr(String uetr);

    Optional<SwiftMessage> findByMsgId(String msgId);

    Optional<SwiftMessage> findFirstByUetrOrderByReceivedAtDesc(String uetr);

    Optional<SwiftMessage> findFirstByMsgIdOrderByReceivedAtDesc(String msgId);

    Optional<SwiftMessage> findByUetr(String uetr);

    List<SwiftMessage> findByMessageType(String messageType);

    List<SwiftMessage> findAllByOrderByReceivedAtDesc();

    @Query("SELECT m FROM SwiftMessage m ORDER BY m.receivedAt DESC")
    List<SwiftMessage> findAllOrderByReceivedAtDesc();

    List<SwiftMessage> findByStatus(String status);

    List<SwiftMessage> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    @Query("SELECT m FROM SwiftMessage m WHERE DATE(m.receivedAt) = :date")
    List<SwiftMessage> findByReceivedDate(@Param("date") LocalDate date);

    List<SwiftMessage> findByReceivedAtBetween(LocalDateTime start, LocalDateTime end);

    List<SwiftMessage> findByReceivedAtAfter(LocalDateTime date);

    List<SwiftMessage> findByReceivedAtBefore(LocalDateTime date);

    @Query("SELECT DATE(m.receivedAt), COUNT(m) FROM SwiftMessage m GROUP BY DATE(m.receivedAt) ORDER BY DATE(m.receivedAt) DESC")
    List<Object[]> countTransactionsByDate();

    // ==================== MÉTHODES POUR CAMT ====================

    @Query("SELECT m FROM SwiftMessage m WHERE m.originalUetr = :originalUetr")
    List<SwiftMessage> findByOriginalUetr(@Param("originalUetr") String originalUetr);

    @Query("SELECT m FROM SwiftMessage m WHERE m.clientEmail = :clientEmail" +
            " AND (:startDate IS NULL OR m.receivedAt >= :startDate)" +
            " AND (:endDate IS NULL OR m.receivedAt <= :endDate)" +
            " AND (:minAmount IS NULL OR m.amount >= :minAmount)" +
            " AND (:maxAmount IS NULL OR m.amount <= :maxAmount)" +
            " AND (:status IS NULL OR m.status = :status)" +
            " AND (:creditorCountry IS NULL OR m.creditorCountry = :creditorCountry)" +
            " ORDER BY m.receivedAt DESC")
    List<SwiftMessage> findByClientEmailAndFilters(
            @Param("clientEmail") String clientEmail,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("status") String status,
            @Param("creditorCountry") String creditorCountry
    );
}