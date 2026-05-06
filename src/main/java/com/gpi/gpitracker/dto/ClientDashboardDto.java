package com.gpi.gpitracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDashboardDto {
    private Long totalTransactions;
    private Long pendingTransactions;
    private Long acceptedTransactions;
    private Long rejectedTransactions;
    private BigDecimal totalAmount;
    private Double averageProcessingTimeHours;
    private List<RecentTransactionDto> recentTransactions;
    private Map<String, Long> statusDistribution;
}