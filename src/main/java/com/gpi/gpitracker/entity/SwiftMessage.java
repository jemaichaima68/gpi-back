package com.gpi.gpitracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "SWIFT_MESSAGE")
public class SwiftMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "swift_msg_seq")
    @SequenceGenerator(name = "swift_msg_seq", sequenceName = "SWIFT_MSG_SEQ", allocationSize = 1)
    private Long id;

    // ===== Type de message (PACS008, PACS009, PACS002...) =====
    @Column(name = "MESSAGE_TYPE", length = 20, nullable = false)
    private String messageType;

    // ===== GrpHdr =====
    @Column(name = "MSG_ID", unique = true, nullable = false, length = 35)
    private String msgId;

    @Column(name = "CREATION_DATE_TIME")
    private LocalDateTime creationDateTime;

    @Column(name = "NB_OF_TRANSACTIONS")
    private int nbOfTransactions;

    @Column(name = "SETTLEMENT_DATE", length = 10)
    private String settlementDate;

    @Column(name = "INSTRUCTING_AGENT_BIC", length = 11)
    private String instructingAgentBic;

    @Column(name = "INSTRUCTED_AGENT_BIC", length = 11)
    private String instructedAgentBic;

    // ===== PmtId =====
    @Column(name = "INSTRUCTION_ID", length = 35)
    private String instructionId;

    @Column(name = "END_TO_END_ID", length = 35)
    private String endToEndId;

    @Column(name = "TRANSACTION_ID", length = 35)
    private String transactionId;

    @Column(name = "UETR", length = 36)
    private String uetr;

    // ===== Montant =====
    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", length = 3)
    private String currency;

    @Column(name = "CHARGE_BEARER", length = 4)
    private String chargeBearer;

    // ===== Débiteur =====
    @Column(name = "DEBTOR_NAME", length = 140)
    private String debtorName;

    @Column(name = "DEBTOR_IBAN", length = 34)
    private String debtorIban;

    @Column(name = "DEBTOR_AGENT_BIC", length = 11)
    private String debtorAgentBic;

    @Column(name = "DEBTOR_COUNTRY", length = 2)
    private String debtorCountry;

    @Column(name = "DEBTOR_ADDRESS", length = 140)
    private String debtorAddress;

    // ===== Créditeur =====
    @Column(name = "CREDITOR_NAME", length = 140)
    private String creditorName;

    @Column(name = "CREDITOR_IBAN", length = 34)
    private String creditorIban;

    @Column(name = "CREDITOR_AGENT_BIC", length = 11)
    private String creditorAgentBic;

    @Column(name = "CREDITOR_COUNTRY", length = 2)
    private String creditorCountry;

    @Column(name = "CREDITOR_ADDRESS", length = 140)
    private String creditorAddress;

    // ===== Motif =====
    @Column(name = "REMITTANCE_INFO", length = 140)
    private String remittanceInfo;

    // ===== Métadonnées système =====
    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Column(name = "STATUS", length = 20)   // RECEIVED, ARCHIVED
    private String status;

    @Column(name = "RECEIVED_AT")
    private LocalDateTime receivedAt;

    @Column(name = "ARCHIVED_AT")
    private LocalDateTime archivedAt;

    private Boolean transaction;
}