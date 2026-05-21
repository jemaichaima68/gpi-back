package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftMessageSender {

    @Value("${swift.generate.path}")
    private String generatePath;

    @Value("${swift.emitted.path}")
    private String emittedPath;

    private final SwiftMessageRepository swiftMessageRepository;
    private final XmlValidationService xmlValidationService;  // NOUVEAU

    public void sendPacs008(SwiftMessage message) {
        String xml = buildPacs008Xml(message);
        writeOutgoingPayment(message, xml, "pacs.008");
    }

    public void sendPacs009(SwiftMessage message) {
        String xml = buildPacs009Xml(message);
        writeOutgoingPayment(message, xml, "pacs.009");
    }

    public Pacs002Result generatePacs002(SwiftMessage original, String decision, String reason) {
        if (!"ACCP".equals(decision) && !"RJCT".equals(decision)) {
            throw new IllegalArgumentException("Le PACS002 accepte seulement ACCP ou RJCT.");
        }

        String msgId = "PACS002-" + System.currentTimeMillis();
        String fileName = "pacs.002_" + original.getMsgId() + "_" + timestamp() + ".xml";
        String xml = buildPacs002Xml(original, msgId, decision, reason);

        // Validation XSD avant écriture
        if (!xmlValidationService.validateXmlString(xml, XmlValidationService.TYPE_PACS002)) {
            throw new RuntimeException("XML PACS002 généré invalide selon XSD");
        }

        writeGeneratedFile(fileName, xml);

        SwiftMessage emitted = new SwiftMessage();
        emitted.setMessageType("PACS002");
        emitted.setMsgId(msgId);
        emitted.setDirection("OUT");
        emitted.setStatus("ENVOYE");
        emitted.setOriginalMsgId(original.getMsgId());
        emitted.setOriginalUetr(original.getUetr());
        emitted.setUetr(original.getUetr());
        emitted.setAmount(original.getAmount());
        emitted.setCurrency(original.getCurrency());
        emitted.setReceivedAt(LocalDateTime.now());
        emitted.setFileName(fileName);
        emitted.setGroupStatus(decision);
        emitted.setRejectionReason(reason);
        swiftMessageRepository.save(emitted);

        return new Pacs002Result(xml, fileName);
    }

    public Camt056Result generateCamt056(SwiftMessage original, String reasonCode, String reasonText, String username) {
        if (original.getUetr() == null || original.getUetr().isBlank()) {
            throw new IllegalArgumentException("Impossible de générer CAMT.056 : UETR manquant.");
        }

        String msgId = "CAMT056_" + System.currentTimeMillis();
        String fileName = "camt.056_" + original.getMsgId() + "_" + timestamp() + ".xml";
        String xml = buildCamt056Xml(original, msgId, reasonCode, reasonText);

        // Validation XSD avant écriture
        if (!xmlValidationService.validateXmlString(xml, XmlValidationService.TYPE_CAMT056)) {
            throw new RuntimeException("XML CAMT.056 généré invalide selon XSD");
        }

        writeGeneratedFile(fileName, xml);

        SwiftMessage camt056 = new SwiftMessage();
        camt056.setMessageType("CAMT056");
        camt056.setMsgId(msgId);
        camt056.setDirection("OUT");
        camt056.setStatus("ENVOYE");
        camt056.setOriginalMsgId(original.getMsgId());
        camt056.setOriginalUetr(original.getUetr());
        camt056.setUetr(original.getUetr());
        camt056.setAmount(original.getAmount());
        camt056.setCurrency(original.getCurrency());
        camt056.setReceivedAt(LocalDateTime.now());
        camt056.setFileName(fileName);
        camt056.setCancellationReason(reasonCode);
        camt056.setCancellationReasonText(reasonText);
        camt056.setValidatedBy(username);
        swiftMessageRepository.save(camt056);

        original.setCancellationStatus("REQUESTED");
        original.setStatus("ANNULATION_EN_ATTENTE");
        swiftMessageRepository.save(original);

        return new Camt056Result(xml, fileName);
    }

    public Camt029Result generateCamt029(SwiftMessage original, String responseStatus, String reasonText, String username) {
        if (!"CNCL".equals(responseStatus) && !"RJCR".equals(responseStatus) && !"PDCR".equals(responseStatus)) {
            throw new IllegalArgumentException("CAMT.029 accepte seulement CNCL, RJCR ou PDCR.");
        }

        String msgId = "CAMT029_" + System.currentTimeMillis();
        String fileName = "camt.029_" + original.getMsgId() + "_" + timestamp() + ".xml";
        String xml = buildCamt029Xml(original, msgId, responseStatus, reasonText);

        // Validation XSD avant écriture
        if (!xmlValidationService.validateXmlString(xml, XmlValidationService.TYPE_CAMT029)) {
            throw new RuntimeException("XML CAMT.029 généré invalide selon XSD");
        }

        writeGeneratedFile(fileName, xml);

        SwiftMessage camt029 = new SwiftMessage();
        camt029.setMessageType("CAMT029");
        camt029.setMsgId(msgId);
        camt029.setDirection("OUT");
        camt029.setStatus("ENVOYE");
        camt029.setOriginalMsgId(original.getMsgId());
        camt029.setOriginalUetr(original.getUetr());
        camt029.setUetr(original.getUetr());
        camt029.setAmount(original.getAmount());
        camt029.setCurrency(original.getCurrency());
        camt029.setReceivedAt(LocalDateTime.now());
        camt029.setFileName(fileName);
        camt029.setGroupStatus(responseStatus);
        camt029.setCancellationReasonText(reasonText);
        camt029.setValidatedBy(username);
        swiftMessageRepository.save(camt029);

        if ("CNCL".equals(responseStatus)) {
            original.setStatus("ANNULEE");
            original.setCancellationStatus("ACCEPT");
        } else if ("RJCR".equals(responseStatus)) {
            original.setStatus("ACCEPTE");
            original.setCancellationStatus("REJECT");
        } else {
            original.setStatus("ANNULATION_EN_ATTENTE");
            original.setCancellationStatus("PEND");
        }
        swiftMessageRepository.save(original);

        return new Camt029Result(xml, fileName);
    }

    private void writeOutgoingPayment(SwiftMessage message, String xml, String prefix) {
        try {
            String fileName = prefix + "_" + message.getMsgId() + "_" + timestamp() + ".xml";
            Path target = Paths.get(emittedPath, fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, xml, StandardCharsets.UTF_8);

            message.setDirection("OUT");
            message.setStatus("ENVOYE");
            message.setFileName(fileName);
            message.setReceivedAt(LocalDateTime.now());
            swiftMessageRepository.save(message);
        } catch (Exception e) {
            throw new RuntimeException("Impossible d'émettre le message " + prefix, e);
        }
    }

    private void writeGeneratedFile(String fileName, String xml) {
        try {
            Path path = Paths.get(generatePath, fileName);
            Files.createDirectories(path.getParent());
            Files.writeString(path, xml, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Impossible d'écrire le fichier " + fileName, e);
        }
    }

    private String buildPacs008Xml(SwiftMessage m) {
        ensurePaymentDefaults(m, "PACS008");
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf><SttlmMtd>INDA</SttlmMtd></SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>%s</InstrId>
                        <EndToEndId>%s</EndToEndId>
                        <UETR>%s</UETR>
                      </PmtId>
                      <Amt><InstdAmt Ccy="%s">%s</InstdAmt></Amt>
                      <Dbtr><Nm>%s</Nm></Dbtr>
                      <DbtrAcct><Id><IBAN>%s</IBAN></Id></DbtrAcct>
                      <DbtrAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></DbtrAgt>
                      <CdtrAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></CdtrAgt>
                      <Cdtr><Nm>%s</Nm></Cdtr>
                      <CdtrAcct><Id><IBAN>%s</IBAN></Id></CdtrAcct>
                      <RmtInf><Ustrd>%s</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """,
                safe(m.getMsgId()), now, safe(m.getInstructionId()), safe(m.getEndToEndId()), safe(m.getUetr()),
                safe(m.getCurrency()), m.getAmount(),
                safe(m.getDebtorName()), safe(m.getDebtorIban()), bic(m.getDebtorAgentBic(), "BNPAFRPP"),
                bic(m.getCreditorAgentBic(), "SGSNSNSX"), safe(m.getCreditorName()), safe(m.getCreditorIban()),
                safe(m.getRemittanceInfo()));
    }

    private String buildPacs009Xml(SwiftMessage m) {
        ensurePaymentDefaults(m, "PACS009");
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
                  <FICdtTrf>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf><SttlmMtd>INDA</SttlmMtd></SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>%s</InstrId>
                        <EndToEndId>%s</EndToEndId>
                        <UETR>%s</UETR>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                      <InstgAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></InstgAgt>
                      <InstdAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></InstdAgt>
                      <RmtInf><Ustrd>%s</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FICdtTrf>
                </Document>
                """,
                safe(m.getMsgId()), now, safe(m.getInstructionId()), safe(m.getEndToEndId()), safe(m.getUetr()),
                safe(m.getCurrency()), m.getAmount(),
                bic(m.getInstructingAgentBic(), "BNPAFRPP"),
                bic(m.getInstructedAgentBic(), "SGSNSNSX"),
                safe(m.getRemittanceInfo()));
    }

    private String buildPacs002Xml(SwiftMessage original, String msgId, String decision, String reason) {
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String originalType = "PACS009".equals(original.getMessageType()) ? "pacs.009.001.08" : "pacs.008.001.08";

        // ⭐⭐⭐ NETTOYAGE CRITIQUE pour le MsgId original ⭐⭐⭐
        String originalMsgId = original.getMsgId();
        if (originalMsgId != null) {
            // Remplacer les caractères non autorisés par des tirets
            originalMsgId = originalMsgId.replace("_", "-")
                    .replace(" ", "-")
                    .replaceAll("[^a-zA-Z0-9/\\-?:\\(\\)\\.,'\\+]", "-");
            // Limiter à 35 caractères
            if (originalMsgId.length() > 35) {
                originalMsgId = originalMsgId.substring(0, 35);
            }
        } else {
            originalMsgId = "UNKNOWN-" + System.currentTimeMillis();
        }

        String reasonBlock = "";
        if ("RJCT".equals(decision)) {
            reasonBlock = String.format("""
                <StsRsnInf>
                  <Rsn><Cd>NARR</Cd></Rsn>
                  <AddtlInf>%s</AddtlInf>
                </StsRsnInf>
                """, escape(reason));
        }

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10">
              <FIToFIPmtStsRpt>
                <GrpHdr>
                  <MsgId>%s</MsgId>
                  <CreDtTm>%s</CreDtTm>
                </GrpHdr>
                <TxInfAndSts>
                  <OrgnlGrpInf>
                    <OrgnlMsgId>%s</OrgnlMsgId>
                    <OrgnlMsgNmId>%s</OrgnlMsgNmId>
                  </OrgnlGrpInf>
                  <OrgnlEndToEndId>%s</OrgnlEndToEndId>
                  <OrgnlUETR>%s</OrgnlUETR>
                  <TxSts>%s</TxSts>
                  %s
                  <InstgAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></InstgAgt>
                  <InstdAgt><FinInstnId><BICFI>%s</BICFI></FinInstnId></InstdAgt>
                </TxInfAndSts>
              </FIToFIPmtStsRpt>
            </Document>
            """,
                msgId, now,
                originalMsgId, originalType,  // ← utiliser originalMsgId nettoyé
                safe(original.getEndToEndId()), safe(original.getUetr()),
                decision, reasonBlock,
                bic(original.getInstructingAgentBic(), "BNPAFRPP"),
                bic(original.getInstructedAgentBic(), "SGSNSNSX"));
    }

    private String buildCamt056Xml(SwiftMessage original, String msgId, String reasonCode, String reasonText) {
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String originalType = "PACS009".equals(original.getMessageType()) ? "pacs.009.001.08" : "pacs.008.001.08";

        // BIC
        String assignerBic = bic(original.getInstructingAgentBic(), bic(original.getDebtorAgentBic(), "BNPAFRPP"));
        String assigneeBic = bic(original.getInstructedAgentBic(), bic(original.getCreditorAgentBic(), "SGSNSNSX"));

        // ⚠️ CRITIQUE : S'assurer que les valeurs ne sont jamais vides
        String orgnlMsgId = safe(original.getMsgId());
        if (orgnlMsgId.isBlank()) orgnlMsgId = "UNKNOWN_MSG_ID";

        String orgnlInstrId = safe(original.getInstructionId());
        if (orgnlInstrId.isBlank()) orgnlInstrId = orgnlMsgId;

        String orgnlEndToEndId = safe(original.getEndToEndId());
        if (orgnlEndToEndId.isBlank()) orgnlEndToEndId = orgnlMsgId;

        String orgnlUETR = original.getUetr();
        if (orgnlUETR == null || orgnlUETR.isBlank() || !orgnlUETR.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) {
            orgnlUETR = "123e4567-e89b-4cd3-a456-426614174000";
            log.warn("UETR invalide, utilisation d'un UUID par défaut: {}", orgnlUETR);
        }
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.056.001.08">
              <FIToFIPmtCxlReq>
                <Assgnmt>
                  <Id>%s</Id>
                  <Assgnr>
                    <Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt>
                  </Assgnr>
                  <Assgne>
                    <Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt>
                  </Assgne>
                  <CreDtTm>%s</CreDtTm>
                </Assgnmt>
                <Undrlyg>
                  <TxInf>
                    <CxlId>CXL_%s</CxlId>
                    <OrgnlGrpInf>
                      <OrgnlMsgId>%s</OrgnlMsgId>
                      <OrgnlMsgNmId>%s</OrgnlMsgNmId>
                    </OrgnlGrpInf>
                    <OrgnlInstrId>%s</OrgnlInstrId>
                    <OrgnlEndToEndId>%s</OrgnlEndToEndId>
                    <OrgnlUETR>%s</OrgnlUETR>
                    <OrgnlIntrBkSttlmAmt Ccy="%s">%s</OrgnlIntrBkSttlmAmt>
                    <CxlRsnInf>
                      <Rsn><Cd>%s</Cd></Rsn>
                      <AddtlInf>%s</AddtlInf>
                    </CxlRsnInf>
                  </TxInf>
                </Undrlyg>
              </FIToFIPmtCxlReq>
            </Document>
            """,
                msgId, assignerBic, assigneeBic, now,
                safe(original.getMsgId()),
                orgnlMsgId, originalType,      // ← utiliser orgnlMsgId
                orgnlInstrId,                   // ← utiliser orgnlInstrId (pas vide)
                orgnlEndToEndId,                // ← utiliser orgnlEndToEndId (pas vide)
                orgnlUETR,                      // ← utiliser orgnlUETR (pas vide)
                safe(original.getCurrency()), original.getAmount(),
                safe(reasonCode, "CUST"), escape(safe(reasonText, "Annulation demandée")));
    }

    private String buildCamt029Xml(SwiftMessage original, String msgId, String responseStatus, String reasonText) {
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String originalType = "PACS009".equals(original.getMessageType()) ? "pacs.009.001.08" : "pacs.008.001.08";

        // Pour CAMT.029, Assigner = banque qui répond (nous), Assignee = banque qui a demandé
        String assignerBic = bic(original.getInstructedAgentBic(), bic(original.getCreditorAgentBic(), "SGSNSNSX"));
        String assigneeBic = bic(original.getInstructingAgentBic(), bic(original.getDebtorAgentBic(), "BNPAFRPP"));

        String reasonBlock = "";
        if (reasonText != null && !reasonText.isBlank()) {
            reasonBlock = String.format("""
                    <CxlStsRsnInf>
                      <Rsn><Cd>NARR</Cd></Rsn>
                      <AddtlInf>%s</AddtlInf>
                    </CxlStsRsnInf>
                    """, escape(reasonText));
        }

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.029.001.09">
                  <RsltnOfInvstgtn>
                    <Assgnmt>
                      <Id>%s</Id>
                      <Assgnr><Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt></Assgnr>
                      <Assgne><Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt></Assgne>
                      <CreDtTm>%s</CreDtTm>
                    </Assgnmt>
                    <Sts>
                      <Conf>%s</Conf>
                    </Sts>
                    <CxlDtls>
                      <TxInfAndSts>
                        <OrgnlGrpInf>
                          <OrgnlMsgId>%s</OrgnlMsgId>
                          <OrgnlMsgNmId>%s</OrgnlMsgNmId>
                        </OrgnlGrpInf>
                        <OrgnlInstrId>%s</OrgnlInstrId>
                        <OrgnlEndToEndId>%s</OrgnlEndToEndId>
                        <OrgnlUETR>%s</OrgnlUETR>
                        <TxCxlSts>%s</TxCxlSts>
                        %s
                      </TxInfAndSts>
                    </CxlDtls>
                  </RsltnOfInvstgtn>
                </Document>
                """,
                msgId, assignerBic, assigneeBic, now, responseStatus,
                safe(original.getMsgId()), originalType,
                safe(original.getInstructionId()), safe(original.getEndToEndId()), safe(original.getUetr()),
                responseStatus, reasonBlock);
    }

    private void ensurePaymentDefaults(SwiftMessage m, String type) {
        if (m.getMsgId() == null || m.getMsgId().isBlank()) m.setMsgId(type + "_" + System.currentTimeMillis());
        if (m.getUetr() == null || m.getUetr().isBlank()) m.setUetr(java.util.UUID.randomUUID().toString());
        if (m.getInstructionId() == null || m.getInstructionId().isBlank()) m.setInstructionId(m.getMsgId());
        if (m.getEndToEndId() == null || m.getEndToEndId().isBlank()) m.setEndToEndId(m.getMsgId());
        if (m.getCurrency() == null || m.getCurrency().isBlank()) m.setCurrency("EUR");
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String safe(String value) {
        return value == null ? "" : escape(value);
    }

    private String safe(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : escape(value);
    }

    private String bic(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
    /**
     * Génère un CAMT.029 en réponse à un CAMT.056 reçu
     */
    public Camt029Result generateCamt029ForResponse(SwiftMessage camt056,
                                                    SwiftMessage originalTransaction,
                                                    String responseStatus,
                                                    String reasonText,
                                                    String username) {
        if (!"CNCL".equals(responseStatus) && !"RJCR".equals(responseStatus) && !"PDCR".equals(responseStatus)) {
            throw new IllegalArgumentException("CAMT.029 accepte seulement CNCL, RJCR ou PDCR.");
        }

        if ("RJCR".equals(responseStatus) && (reasonText == null || reasonText.isBlank())) {
            throw new IllegalArgumentException("Le motif de rejet est obligatoire pour RJCR.");
        }

        String msgId = "CAMT029_" + System.currentTimeMillis();
        String fileName = "camt.029_response_" + originalTransaction.getMsgId() + "_" + timestamp() + ".xml";
        String xml = buildCamt029ResponseXml(camt056, originalTransaction, msgId, responseStatus, reasonText);

        // Validation XSD
        if (!xmlValidationService.validateXmlString(xml, XmlValidationService.TYPE_CAMT029)) {
            throw new RuntimeException("XML CAMT.029 généré invalide selon XSD");
        }

        writeGeneratedFile(fileName, xml);

        // Sauvegarder le CAMT.029 émis
        SwiftMessage camt029 = new SwiftMessage();
        camt029.setMessageType("CAMT029");
        camt029.setMsgId(msgId);
        camt029.setDirection("OUT");
        camt029.setStatus("ENVOYE");
        camt029.setOriginalMsgId(camt056.getMsgId());
        camt029.setOriginalUetr(camt056.getUetr());
        camt029.setUetr(originalTransaction.getUetr());
        camt029.setAmount(originalTransaction.getAmount());
        camt029.setCurrency(originalTransaction.getCurrency());
        camt029.setReceivedAt(LocalDateTime.now());
        camt029.setFileName(fileName);
        camt029.setGroupStatus(responseStatus);
        camt029.setCancellationReasonText(reasonText);
        camt029.setValidatedBy(username);
        camt029.setInstructingAgentBic(camt056.getInstructedAgentBic());  // Assigner = nous
        camt029.setInstructedAgentBic(camt056.getInstructingAgentBic());   // Assignee = l'autre banque

        swiftMessageRepository.save(camt029);

        // Mettre à jour la transaction originale selon la réponse
        if ("CNCL".equals(responseStatus)) {
            originalTransaction.setStatus("ANNULEE");
            originalTransaction.setCancellationStatus("ACCEPT");
        } else if ("RJCR".equals(responseStatus)) {
            originalTransaction.setStatus("ACCEPTE");
            originalTransaction.setCancellationStatus("REJECT");
        } else {
            originalTransaction.setStatus("ANNULATION_EN_ATTENTE");
            originalTransaction.setCancellationStatus("PEND");
        }
        swiftMessageRepository.save(originalTransaction);

        log.info("CAMT.029 généré en réponse à CAMT.056: msgId={}, responseStatus={}", msgId, responseStatus);

        return new Camt029Result(xml, fileName);
    }

    /**
     * Construit le XML CAMT.029 de réponse à un CAMT.056
     */
    /**
     * Construit le XML CAMT.029 de réponse à un CAMT.056
     */
    private String buildCamt029ResponseXml(SwiftMessage camt056,
                                           SwiftMessage original,
                                           String msgId,
                                           String responseStatus,
                                           String reasonText) {
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String originalType = "PACS009".equals(original.getMessageType()) ? "pacs.009.001.08" : "pacs.008.001.08";

        String assignerBic = bic(camt056.getInstructedAgentBic(), "BNPAFRPP");
        String assigneeBic = bic(camt056.getInstructingAgentBic(), "SGSNSNSX");

        String orgnlMsgId = safe(original.getMsgId());
        if (orgnlMsgId.isBlank()) orgnlMsgId = "UNKNOWN_MSG_ID";
        String orgnlInstrId = safe(original.getInstructionId());
        if (orgnlInstrId.isBlank()) orgnlInstrId = orgnlMsgId;
        String orgnlEndToEndId = safe(original.getEndToEndId());
        if (orgnlEndToEndId.isBlank()) orgnlEndToEndId = orgnlMsgId;
        String orgnlUETR = safe(original.getUetr());
        if (orgnlUETR.isBlank()) orgnlUETR = "123e4567-e89b-4cd3-a456-426614174000";

        String statusBlock;
        String reasonBlock = "";
        String txCxlStsValue;  // ✅ Attention : l minuscule

        switch (responseStatus) {
            case "CNCL":
                statusBlock = "<Conf>ACCR</Conf>";
                txCxlStsValue = "ACCR";
                break;
            case "RJCR":
                statusBlock = "<Conf>RJCR</Conf>";
                txCxlStsValue = "RJCR";
                reasonBlock = String.format("""
                <CxlStsRsnInf>
                  <Rsn><Cd>NARR</Cd></Rsn>
                  <AddtlInf>%s</AddtlInf>
                </CxlStsRsnInf>
                """, escape(safe(reasonText, "Rejet de l'annulation")));
                break;
            case "PDCR":
                statusBlock = "<Conf>PDCR</Conf>";
                txCxlStsValue = "PDCR";
                if (reasonText != null && !reasonText.isBlank()) {
                    reasonBlock = String.format("""
                <CxlStsRsnInf>
                  <Rsn><Cd>NARR</Cd></Rsn>
                  <AddtlInf>%s</AddtlInf>
                </CxlStsRsnInf>
                """, escape(reasonText));
                }
                break;
            default:
                statusBlock = "<Conf>PDCR</Conf>";
                txCxlStsValue = "PDCR";
                break;
        }

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.029.001.09">
              <RsltnOfInvstgtn>
                <Assgnmt>
                  <Id>%s</Id>
                  <Assgnr>
                    <Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt>
                  </Assgnr>
                  <Assgne>
                    <Agt><FinInstnId><BICFI>%s</BICFI></FinInstnId></Agt>
                  </Assgne>
                  <CreDtTm>%s</CreDtTm>
                </Assgnmt>
                <Sts>
                  %s
                </Sts>
                <CxlDtls>
                  <TxInfAndSts>
                    <OrgnlGrpInf>
                      <OrgnlMsgId>%s</OrgnlMsgId>
                      <OrgnlMsgNmId>%s</OrgnlMsgNmId>
                    </OrgnlGrpInf>
                    <OrgnlInstrId>%s</OrgnlInstrId>
                    <OrgnlEndToEndId>%s</OrgnlEndToEndId>
                    <OrgnlUETR>%s</OrgnlUETR>
                    <TxCxlSts>%s</TxCxlSts>
                    %s
                  </TxInfAndSts>
                </CxlDtls>
              </RsltnOfInvstgtn>
            </Document>
            """,
                msgId, assignerBic, assigneeBic, now,
                statusBlock,
                orgnlMsgId, originalType,
                orgnlInstrId, orgnlEndToEndId, orgnlUETR,
                txCxlStsValue,  // ✅ Attention : l minuscule
                reasonBlock);
    }

    @Data
    @AllArgsConstructor
    public static class Pacs002Result {
        private String xml;
        private String fileName;
    }

    @Data
    @AllArgsConstructor
    public static class Camt056Result {
        private String xml;
        private String fileName;
    }

    @Data
    @AllArgsConstructor
    public static class Camt029Result {
        private String xml;
        private String fileName;
    }
}