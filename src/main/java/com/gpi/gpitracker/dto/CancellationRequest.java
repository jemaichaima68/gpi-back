package com.gpi.gpitracker.dto;

import lombok.Data;

@Data
public class CancellationRequest {
    private String reasonCode;   // CUST, DUPL, FRAD, CURR, AM09, TECH, UPAY, AGNT, COVR
    private String reasonText;   // Texte libre du motif
}
