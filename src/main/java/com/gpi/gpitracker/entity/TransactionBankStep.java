package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTION_BANK_STEP")
public class TransactionBankStep {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bank_step")
    @SequenceGenerator(name = "seq_bank_step", sequenceName = "SEQ_TRANSACTION_BANK_STEP", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @Column(name = "UETR")
    private String uetr;

    @Column(name = "CLIENT_EMAIL")
    private String clientEmail;

    @Column(name = "STEP_ORDER")
    private Integer stepOrder;

    @Column(name = "BANK_NAME")
    private String bankName;

    @Column(name = "BANK_BIC")
    private String bankBic;

    @Column(name = "ROLE")
    private String role;

    @Column(name = "FEES", columnDefinition = "NUMBER(15,2)")
    private BigDecimal fees;

    @Column(name = "FEES_CURRENCY")
    private String feesCurrency;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "STEP_TIMESTAMP")
    private LocalDateTime stepTimestamp;

    public TransactionBankStep() {}

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getUetr() { return uetr; }
    public void setUetr(String uetr) { this.uetr = uetr; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankBic() { return bankBic; }
    public void setBankBic(String bankBic) { this.bankBic = bankBic; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public BigDecimal getFees() { return fees; }
    public void setFees(BigDecimal fees) { this.fees = fees; }

    public String getFeesCurrency() { return feesCurrency; }
    public void setFeesCurrency(String feesCurrency) { this.feesCurrency = feesCurrency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStepTimestamp() { return stepTimestamp; }
    public void setStepTimestamp(LocalDateTime stepTimestamp) { this.stepTimestamp = stepTimestamp; }
}