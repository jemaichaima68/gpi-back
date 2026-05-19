package com.gpi.gpitracker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDirectorySimpleDto {
    private String bic;
    private String bankName;
    private String countryCode;
    private String city;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Integer occurrenceCount;
}