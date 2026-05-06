package com.gpi.gpitracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationHistoryDto {
    private Long id;
    private String uetr;
    private LocalDateTime consultedAt;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime updatedAt;
}