package com.gpi.gpitracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventDto {
    private String status;
    private String label;
    private String description;
    private String details;
    private LocalDateTime timestamp;
    private String location;
}