package com.gpi.gpitracker.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecentTransactionDto {
    private Long id;
    private String msgId;
    private String uetr;
    private BigDecimal amount;
    private String currency;
    private String debtorName;
    private String creditorName;
    private String creditorCountry;
    private String status;
    private LocalDateTime receivedAt;
    private String alerte;           // ← AJOUTER
    private String motifAlerte;
    private String messageType;
    private String debtorCountry;
}