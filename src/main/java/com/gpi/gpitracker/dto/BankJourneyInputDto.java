package com.gpi.gpitracker.dto;

import java.math.BigDecimal;

public class BankJourneyInputDto {
    private String bankName;
    private String bankBic;
    private String role;
    private BigDecimal fees;
    private String feesCurrency;
    private String status;

    // Constructeur par défaut
    public BankJourneyInputDto() {}

    // Constructeur avec paramètres
    public BankJourneyInputDto(String bankName, String bankBic, String role, BigDecimal fees, String feesCurrency, String status) {
        this.bankName = bankName;
        this.bankBic = bankBic;
        this.role = role;
        this.fees = fees;
        this.feesCurrency = feesCurrency;
        this.status = status;
    }

    // Getters
    public String getBankName() { return bankName; }
    public String getBankBic() { return bankBic; }
    public String getRole() { return role; }
    public BigDecimal getFees() { return fees; }
    public String getFeesCurrency() { return feesCurrency; }
    public String getStatus() { return status; }

    // Setters
    public void setBankName(String bankName) { this.bankName = bankName; }
    public void setBankBic(String bankBic) { this.bankBic = bankBic; }
    public void setRole(String role) { this.role = role; }
    public void setFees(BigDecimal fees) { this.fees = fees; }
    public void setFeesCurrency(String feesCurrency) { this.feesCurrency = feesCurrency; }
    public void setStatus(String status) { this.status = status; }
}