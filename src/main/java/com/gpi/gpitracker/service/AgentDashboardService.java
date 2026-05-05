package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.RecentTransactionDto;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentDashboardService {

    private final SwiftMessageRepository swiftMessageRepository;

    /**
     * Statistiques version 2 pour le nouveau dashboard
     */
    public Map<String, Object> getDashboardStatsV2() {
        List<SwiftMessage> allMessages = swiftMessageRepository.findAll();

        // Transactions en attente (EN_ATTENTE ou PDNG)
        long enAttente = allMessages.stream()
                .filter(m -> "EN_ATTENTE".equals(m.getStatus()) || "PDNG".equals(m.getStatus()))
                .count();

        // Transactions déjà traitées (acceptées ou rejetées)
        long totalTraitees = allMessages.stream()
                .filter(m -> "ACCEPTE".equals(m.getStatus()) || "REJETE".equals(m.getStatus())
                        || "ACCP".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                .count();

        // Transactions acceptées
        long acceptees = allMessages.stream()
                .filter(m -> "ACCEPTE".equals(m.getStatus()) || "ACCP".equals(m.getStatus()))
                .count();

        // Taux d'acceptation
        double tauxAcceptation = totalTraitees > 0
                ? Math.round((double) acceptees / totalTraitees * 100)
                : 0;

        // Alertes critiques
        long alertesCritiques = allMessages.stream()
                .filter(m -> "GRAVE".equals(m.getAlerte()))
                .count();

        // Alertes attention
        long alertesAttention = allMessages.stream()
                .filter(m -> "ATTENTION".equals(m.getAlerte()))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("enAttente", enAttente);
        stats.put("totalTraitees", totalTraitees);
        stats.put("tauxAcceptation", tauxAcceptation);
        stats.put("alertesCritiques", alertesCritiques);
        stats.put("alertesAttention", alertesAttention);

        return stats;
    }

    /**
     * Statistiques d'activité du jour
     */
    public Map<String, Object> getTodayActivityStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<SwiftMessage> todaysMessages = swiftMessageRepository.findByReceivedAtBetween(startOfDay, endOfDay);

        long traitees = todaysMessages.stream()
                .filter(m -> "ACCEPTE".equals(m.getStatus()) || "REJETE".equals(m.getStatus())
                        || "ACCP".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                .count();

        long acceptees = todaysMessages.stream()
                .filter(m -> "ACCEPTE".equals(m.getStatus()) || "ACCP".equals(m.getStatus()))
                .count();

        long rejetees = todaysMessages.stream()
                .filter(m -> "REJETE".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("traitees", traitees);
        stats.put("acceptees", acceptees);
        stats.put("rejetees", rejetees);

        return stats;
    }

    /**
     * Données pour le graphique d'activité (par jour ou par semaine)
     */
    public Map<String, Object> getActivityData(String period) {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        if ("week".equals(period)) {
            // 7 derniers jours
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                LocalDateTime start = date.atStartOfDay();
                LocalDateTime end = date.atTime(23, 59, 59);

                long count = swiftMessageRepository.findByReceivedAtBetween(start, end).stream()
                        .filter(m -> "ACCEPTE".equals(m.getStatus()) || "REJETE".equals(m.getStatus())
                                || "ACCP".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                        .count();

                labels.add(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH));
                values.add(count);
            }
        } else {
            // 4 dernières semaines
            for (int i = 3; i >= 0; i--) {
                LocalDate weekStart = LocalDate.now().minusWeeks(i).with(DayOfWeek.MONDAY);
                LocalDate weekEnd = weekStart.plusDays(6);
                LocalDateTime start = weekStart.atStartOfDay();
                LocalDateTime end = weekEnd.atTime(23, 59, 59);

                long count = swiftMessageRepository.findByReceivedAtBetween(start, end).stream()
                        .filter(m -> "ACCEPTE".equals(m.getStatus()) || "REJETE".equals(m.getStatus())
                                || "ACCP".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                        .count();

                labels.add("S" + (i + 1));
                values.add(count);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);

        return result;
    }

    /**
     * Transactions en attente de traitement
     */
    public List<RecentTransactionDto> getPendingMessages(int limit) {
        return swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> "EN_ATTENTE".equals(m.getStatus()) || "PDNG".equals(m.getStatus()))
                .limit(limit)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Dernières transactions traitées
     */
    public List<RecentTransactionDto> getRecentProcessedMessages(int limit) {
        return swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> "ACCEPTE".equals(m.getStatus()) || "REJETE".equals(m.getStatus())
                        || "ACCP".equals(m.getStatus()) || "RJCT".equals(m.getStatus()))
                .limit(limit)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Conversion de SwiftMessage vers RecentTransactionDto
     */
    private RecentTransactionDto convertToDto(SwiftMessage msg) {
        RecentTransactionDto dto = new RecentTransactionDto();
        dto.setId(msg.getId());
        dto.setMsgId(msg.getMsgId());
        dto.setMessageType(msg.getMessageType());
        dto.setUetr(msg.getUetr());
        dto.setAmount(msg.getAmount());
        dto.setCurrency(msg.getCurrency());
        dto.setDebtorName(msg.getDebtorName());
        dto.setCreditorName(msg.getCreditorName());
        dto.setDebtorCountry(msg.getDebtorCountry());
        dto.setCreditorCountry(msg.getCreditorCountry());
        dto.setStatus(msg.getStatus());
        dto.setAlerte(msg.getAlerte());
        dto.setMotifAlerte(msg.getMotifAlerte());
        dto.setReceivedAt(msg.getReceivedAt());
        return dto;
    }
}