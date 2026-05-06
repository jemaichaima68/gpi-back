package com.gpi.gpitracker.dto;

public class BankJourneyDto {
    private Integer step;
    private String bankName;
    private String bankBic;
    private String role;
    private String fees;
    private String status;
    private String timestamp;

    public BankJourneyDto() {}

    public BankJourneyDto(Integer step, String bankName, String bankBic, String role, String fees, String status, String timestamp) {
        this.step = step;
        this.bankName = bankName;
        this.bankBic = bankBic;
        this.role = role;
        this.fees = fees;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters
    public Integer getStep() { return step; }
    public String getBankName() { return bankName; }
    public String getBankBic() { return bankBic; }
    public String getRole() { return role; }
    public String getFees() { return fees; }
    public String getStatus() { return status; }
    public String getTimestamp() { return timestamp; }

    // Setters
    public void setStep(Integer step) { this.step = step; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public void setBankBic(String bankBic) { this.bankBic = bankBic; }
    public void setRole(String role) { this.role = role; }
    public void setFees(String fees) { this.fees = fees; }
    public void setStatus(String status) { this.status = status; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}