package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.AgentDashboardStatsDto;
import com.gpi.gpitracker.dto.RecentTransactionDto;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentDashboardService {

    private final SwiftMessageRepository swiftMessageRepository;

    public AgentDashboardStatsDto getDashboardStats() {
        List<SwiftMessage> allMessages = swiftMessageRepository.findAll();

        long enAttente = allMessages.stream().filter(m -> "EN_ATTENTE".equals(m.getStatus())).count();
        long acceptees = allMessages.stream().filter(m -> "ACCEPTE".equals(m.getStatus())).count();
        long rejetees = allMessages.stream().filter(m -> "REJETE".equals(m.getStatus())).count();
        long signalees = allMessages.stream().filter(m -> "SIGNALE".equals(m.getStatus())).count();

        BigDecimal montantTotal = allMessages.stream()
                .map(SwiftMessage::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double montantMoyen = allMessages.isEmpty() ? 0 : montantTotal.doubleValue() / allMessages.size();

        AgentDashboardStatsDto dto = new AgentDashboardStatsDto();
        dto.setTotalTransactions((long) allMessages.size());
        dto.setEnAttente(enAttente);
        dto.setAcceptees(acceptees);
        dto.setRejetees(rejetees);
        dto.setSignalees(signalees);
        dto.setMontantTotal(montantTotal.doubleValue());
        dto.setMontantMoyen(montantMoyen);

        return dto;
    }

    public List<RecentTransactionDto> getRecentMessages(int limit) {
        return swiftMessageRepository.findAllByOrderByReceivedAtDesc()
                .stream()
                .limit(limit)
                .map(this::toRecentDto)
                .collect(Collectors.toList());
    }

    private RecentTransactionDto toRecentDto(SwiftMessage msg) {
        RecentTransactionDto dto = new RecentTransactionDto();
        dto.setId(msg.getId());
        dto.setMsgId(msg.getMsgId());
        dto.setUetr(msg.getUetr());
        dto.setAmount(msg.getAmount());
        dto.setCurrency(msg.getCurrency());
        dto.setDebtorName(msg.getDebtorName());
        dto.setCreditorName(msg.getCreditorName());
        dto.setCreditorCountry(msg.getCreditorCountry());
        dto.setStatus(msg.getStatus());
        dto.setReceivedAt(msg.getReceivedAt());
        return dto;
    }
}