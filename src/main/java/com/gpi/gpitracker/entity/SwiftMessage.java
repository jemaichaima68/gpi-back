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

    // ===== Type de message (PACS008, PACS009, PACS002, CAMT056, CAMT029...) =====
    @Column(name = "MESSAGE_TYPE", length = 20, nullable = false)
    private String messageType;

    // ===== GrpHdr (Group Header) =====
    @Column(name = "MSG_ID", unique = true, nullable = false, length = 35)
    private String msgId;

    @Column(name = "CREATION_DATE_TIME")
    private LocalDateTime creationDateTime;

    @Column(name = "NB_OF_TRANSACTIONS")
    private Integer nbOfTransactions;

    @Column(name = "SETTLEMENT_DATE", length = 10)
    private String settlementDate;

    @Column(name = "INSTRUCTING_AGENT_BIC", length = 11)
    private String instructingAgentBic;

    @Column(name = "INSTRUCTED_AGENT_BIC", length = 11)
    private String instructedAgentBic;

    // ===== PmtId (Payment Identification) =====
    @Column(name = "INSTRUCTION_ID", length = 35)
    private String instructionId;

    @Column(name = "END_TO_END_ID", length = 35)
    private String endToEndId;

    @Column(name = "DIRECTION", length = 3)
    private String direction;  // "IN" pour reçu, "OUT" pour émis

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

    // ===== Débiteur (Debtor) =====
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

    // ===== Créditeur (Creditor) =====
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

    // ===== Motif (Remittance Info) =====
    @Column(name = "REMITTANCE_INFO", length = 140)
    private String remittanceInfo;

    // ===== Métadonnées système =====
    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    /**
     * Statut de la transaction selon les codes SWIFT ISO 20022.
     * Valeurs possibles :
     * - PDNG (Pending) : En attente de traitement
     * - ACTC (Accepted Technical Validation) : Accepté techniquement
     * - ACCP (Accepted) : Accepté par la banque
     * - ACSP (Accepted Settlement In Process) : En cours de règlement
     * - RJCT (Rejected) : Rejeté
     */
    @Column(name = "STATUS", length = 30)
    private String status;

    /**
     * Niveau d'alerte calculé par les règles métier.
     * Valeurs possibles : "OK", "ATTENTION", "GRAVE"
     */
    @Column(name = "ALERTE", length = 20)
    private String alerte;

    /**
     * Motif détaillé de l'alerte
     */
    @Column(name = "MOTIF_ALERTE", length = 500)
    private String motifAlerte;

    @Column(name = "RECEIVED_AT")
    private LocalDateTime receivedAt;

    @Column(name = "ARCHIVED_AT")
    private LocalDateTime archivedAt;

    /**
     * Motif de rejet
     */
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    /**
     * Indique si la transaction nécessite une approbation manuelle
     */
    @Column(name = "NEEDS_AGENT_APPROVAL")
    private Boolean needsAgentApproval = false;

    /**
     * Pour les messages de type PACS002 : référence vers le message original
     */
    @Column(name = "ORIGINAL_MSG_ID", length = 35)
    private String originalMsgId;

    /**
     * Statut du groupe (ACCP, RJCT, PDNG, ACTC, ACSP)
     */
    @Column(name = "GROUP_STATUS", length = 10)
    private String groupStatus;

    @Column(name = "TRANSACTION")
    private Boolean transaction;

    // ===== CHAMPS AJOUTÉS POUR LE CLIENT =====

    @Column(name = "CLIENT_EMAIL", length = 100)
    private String clientEmail;

    @Column(name = "VALIDATED_AT")
    private LocalDateTime validatedAt;

    @Column(name = "VALIDATED_BY", length = 100)
    private String validatedBy;

    // ===== GETTERS ET SETTERS DES CHAMPS AJOUTÉS =====

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public void setValidatedBy(String validatedBy) {
        this.validatedBy = validatedBy;
    }

    // ===== LOMBOK GENERATED (assurons-nous que les getters/setters existent) =====
    // Les annotations @Getter et @Setter de Lombok génèrent automatiquement
    // les getters et setters pour tous les champs, y compris les nouveaux.
    // Pas besoin de les écrire manuellement si Lombok fonctionne.

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        if (needsAgentApproval == null) {
            needsAgentApproval = false;
        }
        if (status == null) {
            status = "PDNG";
        }
    }
}