package com.gpi.gpitracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientNotificationDto {
    private Long id;
    private String title;
    private String message;
    private LocalDateTime date;
    private String type;
    private boolean read;
    private String uetr;
}