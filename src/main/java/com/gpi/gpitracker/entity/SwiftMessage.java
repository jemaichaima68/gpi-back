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

    // ==================== CONSTANTES DE STATUT ====================
    public static final String STATUS_PENDING = "EN_ATTENTE";
    public static final String STATUS_ACCEPTED = "ACCEPTE";
    public static final String STATUS_REJECTED = "REJETE";

    // ==================== CONSTANTES D'ALERTE ====================
    public static final String ALERTE_OK = "OK";
    public static final String ALERTE_ATTENTION = "ATTENTION";
    public static final String ALERTE_GRAVE = "GRAVE";

    // ==================== CONSTANTES DE DIRECTION ====================
    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

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
    private String direction;

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
     * Statut de la transaction (simplifié)
     * - EN_ATTENTE : En attente de validation par l'agent
     * - ACCEPTE : Acceptée par l'agent
     * - REJETE : Rejetée par l'agent
     */
    @Column(name = "STATUS", length = 30)
    private String status;

    /**
     * Niveau d'alerte calculé par les règles métier
     * - OK : Aucune alerte
     * - ATTENTION : À surveiller
     * - GRAVE : Critique
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
     * Motif de rejet (renseigné quand status = REJETE)
     */
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    /**
     * Pour les messages de type PACS002 : référence vers le message original
     */
    @Column(name = "ORIGINAL_MSG_ID", length = 35)
    private String originalMsgId;

    /**
     * Statut du groupe pour les réponses
     */
    @Column(name = "GROUP_STATUS", length = 10)
    private String groupStatus;

    // ==================== CHAMPS POUR LE CLIENT ====================

    /**
     * Email du client associé à la transaction
     */
    @Column(name = "CLIENT_EMAIL", length = 100)
    private String clientEmail;

    /**
     * Date de validation par l'agent
     */
    @Column(name = "VALIDATED_AT")
    private LocalDateTime validatedAt;

    /**
     * Nom de l'agent qui a validé
     */
    @Column(name = "VALIDATED_BY", length = 100)
    private String validatedBy;

    /**
     * Flag indiquant si l'agent a validé (accepté)
     */
    @Column(name = "AGENT_VALIDATED")
    private Boolean agentValidated = false;

    // ==================== CHAMPS POUR L'ANNULATION ====================

    /**
     * Code raison d'annulation (CUST, DUPL, FRAD, etc.)
     */
    @Column(name = "CANCELLATION_REASON", length = 35)
    private String cancellationReason;

    /**
     * Texte libre du motif d'annulation
     */
    @Column(name = "CANCELLATION_REASON_TEXT", length = 500)
    private String cancellationReasonText;

    /**
     * Statut de l'annulation (PEND, ACCEPT, REJECT)
     */
    @Column(name = "CANCELLATION_STATUS", length = 10)
    private String cancellationStatus;

    /**
     * UETR de la transaction originale (pour les réponses)
     */
    @Column(name = "ORIGINAL_UETR", length = 36)
    private String originalUetr;

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Vérifie si la transaction est en attente de validation agent
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status) && !Boolean.TRUE.equals(agentValidated);
    }

    /**
     * Vérifie si la transaction a été acceptée
     */
    public boolean isAccepted() {
        return STATUS_ACCEPTED.equals(status);
    }

    /**
     * Vérifie si la transaction a été rejetée
     */
    public boolean isRejected() {
        return STATUS_REJECTED.equals(status);
    }

    /**
     * Vérifie si la transaction a une alerte critique
     */
    public boolean isCriticalAlert() {
        return ALERTE_GRAVE.equals(alerte);
    }

    /**
     * Vérifie si la transaction a une alerte d'attention
     */
    public boolean isWarningAlert() {
        return ALERTE_ATTENTION.equals(alerte);
    }

    /**
     * Retourne le libellé du statut en français
     */
    public String getStatusLabel() {
        if (isAccepted()) return "Acceptée";
        if (isRejected()) return "Rejetée";
        return "En attente";
    }

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = STATUS_PENDING;
        }
        if (agentValidated == null) {
            agentValidated = false;
        }
    }
}