package com.gpi.gpitracker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TransferResponseDto {

    private Long id;
    private String uetr;
    private BigDecimal amount;
    private String currency;
    private String beneficiaryName;
    private String beneficiaryAccount;
    private String beneficiaryBank;
    private String senderName;
    private String debtorName;
    private String creditorName;
    private String creditorAgentBic;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rejectionReason;
    private String alerte;
    private String motifAlerte;
    private String debtorCountry;
    private String creditorCountry;

    // ⭐⭐ NOUVEAUX CHAMPS POUR L'ANNULATION ⭐⭐
    private String cancellationReason;
    private String cancellationReasonText;
    private String cancellationStatus;

    // Champs pour le parcours bancaire
    private List<BankJourneyDto> bankJourney;
    private Double totalFees;
    private Double netAmount;

    // Constructeur par défaut
    public TransferResponseDto() {}

    // ==================== GETTERS ====================
    public Long getId() { return id; }
    public String getUetr() { return uetr; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getBeneficiaryAccount() { return beneficiaryAccount; }
    public String getBeneficiaryBank() { return beneficiaryBank; }
    public String getSenderName() { return senderName; }
    public String getDebtorName() { return debtorName; }
    public String getCreditorName() { return creditorName; }
    public String getCreditorAgentBic() { return creditorAgentBic; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getAlerte() { return alerte; }
    public String getMotifAlerte() { return motifAlerte; }
    public String getDebtorCountry() { return debtorCountry; }
    public String getCreditorCountry() { return creditorCountry; }

    // ⭐⭐ NOUVEAUX GETTERS ⭐⭐
    public String getCancellationReason() { return cancellationReason; }
    public String getCancellationReasonText() { return cancellationReasonText; }
    public String getCancellationStatus() { return cancellationStatus; }

    public List<BankJourneyDto> getBankJourney() { return bankJourney; }
    public Double getTotalFees() { return totalFees; }
    public Double getNetAmount() { return netAmount; }

    // ==================== SETTERS ====================
    public void setId(Long id) { this.id = id; }
    public void setUetr(String uetr) { this.uetr = uetr; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public void setBeneficiaryAccount(String beneficiaryAccount) { this.beneficiaryAccount = beneficiaryAccount; }
    public void setBeneficiaryBank(String beneficiaryBank) { this.beneficiaryBank = beneficiaryBank; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setDebtorName(String debtorName) { this.debtorName = debtorName; }
    public void setCreditorName(String creditorName) { this.creditorName = creditorName; }
    public void setCreditorAgentBic(String creditorAgentBic) { this.creditorAgentBic = creditorAgentBic; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setAlerte(String alerte) { this.alerte = alerte; }
    public void setMotifAlerte(String motifAlerte) { this.motifAlerte = motifAlerte; }
    public void setDebtorCountry(String debtorCountry) { this.debtorCountry = debtorCountry; }
    public void setCreditorCountry(String creditorCountry) { this.creditorCountry = creditorCountry; }

    // ⭐⭐ NOUVEAUX SETTERS ⭐⭐
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setCancellationReasonText(String cancellationReasonText) { this.cancellationReasonText = cancellationReasonText; }
    public void setCancellationStatus(String cancellationStatus) { this.cancellationStatus = cancellationStatus; }

    public void setBankJourney(List<BankJourneyDto> bankJourney) { this.bankJourney = bankJourney; }
    public void setTotalFees(Double totalFees) { this.totalFees = totalFees; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }
}