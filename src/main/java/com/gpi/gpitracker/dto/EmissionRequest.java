package com.gpi.gpitracker.dto;

import lombok.Data;

@Data
public class EmissionRequest {
    private String amount;
    private String currency;
    private String debtorName;
    private String creditorName;
    private String creditorCountry;
    private String remittanceInfo;
}