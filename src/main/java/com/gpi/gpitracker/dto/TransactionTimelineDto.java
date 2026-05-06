package com.gpi.gpitracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTimelineDto {
    private String status;
    private String statusLabel;
    private String description;
    private LocalDateTime timestamp;
    private boolean completed;
    private String icon;
}