package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftMessageSender {

    @Value("${swift.emitted.path}")
    private String emittedPath;

    @Value("${swift.generate.path}")
    private String generatePath;

    private final SwiftMessageRepository swiftMessageRepository;

    // ==================== PACS008 SORTANT (ÉMIS) ====================

    /**
     * Émet un PACS008 sortant (paiement client)
     */
    public void sendPacs008(SwiftMessage message) {
        try {
            String xml = buildPacs008Xml(message);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("pacs.008_%s_%s.xml", message.getMsgId(), timestamp);

            Path target = Paths.get(emittedPath, fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);

            // Sauvegarde en base
            message.setDirection("OUT");
            message.setStatus("ENVOYE");
            message.setFileName(fileName);
            message.setReceivedAt(LocalDateTime.now());
            swiftMessageRepository.save(message);

            log.info("Pacs.008 sortant émis : {}", target);
        } catch (IOException e) {
            log.error("Erreur lors de l'écriture du pacs.008 sortant", e);
            throw new RuntimeException("Impossible d'émettre le pacs.008", e);
        }
    }

    private String buildPacs008Xml(SwiftMessage message) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String uetr = message.getUetr() != null ? message.getUetr() : java.util.UUID.randomUUID().toString();

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                <FIToFICstmrCdtTrf>
                    <GrpHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                        <NbOfTxs>1</NbOfTxs>
                        <SttlmInf>
                            <SttlmMtd>INDA</SttlmMtd>
                        </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                        <PmtId>
                            <EndToEndId>%s</EndToEndId>
                            <UETR>%s</UETR>
                        </PmtId>
                        <Amt>
                            <InstdAmt Ccy="%s">%s</InstdAmt>
                        </Amt>
                        <Dbtr>
                            <Nm>%s</Nm>
                        </Dbtr>
                        <Cdtr>
                            <Nm>%s</Nm>
                        </Cdtr>
                        <RmtInf>
                            <Ustrd>%s</Ustrd>
                        </RmtInf>
                    </CdtTrfTxInf>
                </FIToFICstmrCdtTrf>
            </Document>
            """, message.getMsgId(), now, message.getEndToEndId() != null ? message.getEndToEndId() : message.getMsgId(),
                uetr, message.getCurrency(), message.getAmount(),
                message.getDebtorName(), message.getCreditorName(),
                message.getRemittanceInfo() != null ? message.getRemittanceInfo() : "");
    }

    // ==================== PACS009 SORTANT (ÉMIS) ====================

    /**
     * Émet un PACS009 sortant (transfert interbancaire)
     */
    public void sendPacs009(SwiftMessage message) {
        try {
            String xml = buildPacs009Xml(message);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("pacs.009_%s_%s.xml", message.getMsgId(), timestamp);

            Path target = Paths.get(emittedPath, fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);

            // Sauvegarde en base
            message.setDirection("OUT");
            message.setStatus("ENVOYE");
            message.setFileName(fileName);
            message.setReceivedAt(LocalDateTime.now());
            swiftMessageRepository.save(message);

            log.info("Pacs.009 sortant émis : {}", target);
        } catch (IOException e) {
            log.error("Erreur lors de l'écriture du pacs.009 sortant", e);
            throw new RuntimeException("Impossible d'émettre le pacs.009", e);
        }
    }

    private String buildPacs009Xml(SwiftMessage message) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String uetr = message.getUetr() != null ? message.getUetr() : java.util.UUID.randomUUID().toString();

        String endToEndId = message.getEndToEndId() != null
                ? message.getEndToEndId()
                : message.getMsgId();

        String chargeBearer = message.getChargeBearer() != null
                ? message.getChargeBearer()
                : "SLEV";

        String remittanceInfo = message.getRemittanceInfo() != null
                ? message.getRemittanceInfo()
                : "Transfert interbancaire";

        String debtorIban = message.getDebtorIban() != null
                ? message.getDebtorIban()
                : "";

        String creditorIban = message.getCreditorIban() != null
                ? message.getCreditorIban()
                : "";

        return String.format("""
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
            <FIToFICstmrCdtTrf>
                <GrpHdr>
                    <MsgId>%s</MsgId>
                    <CreDtTm>%s</CreDtTm>
                    <NbOfTxs>1</NbOfTxs>
                    <SttlmInf>
                        <SttlmDt>%s</SttlmDt>
                    </SttlmInf>
                </GrpHdr>

                <CdtTrfTxInf>
                    <PmtId>
                        <EndToEndId>%s</EndToEndId>
                        <UETR>%s</UETR>
                    </PmtId>

                    <Amt>
                        <InstdAmt Ccy="%s">%s</InstdAmt>
                    </Amt>

                    <ChrgBr>%s</ChrgBr>

                    <InstgAgt>
                        <FinInstnId>
                            <BICFI>%s</BICFI>
                        </FinInstnId>
                    </InstgAgt>

                    <InstdAgt>
                        <FinInstnId>
                            <BICFI>%s</BICFI>
                        </FinInstnId>
                    </InstdAgt>

                    <DbtrAcct>
                        <Id>
                            <IBAN>%s</IBAN>
                        </Id>
                    </DbtrAcct>

                    <CdtrAcct>
                        <Id>
                            <IBAN>%s</IBAN>
                        </Id>
                    </CdtrAcct>

                    <RmtInf>
                        <Ustrd>%s</Ustrd>
                    </RmtInf>
                </CdtTrfTxInf>
            </FIToFICstmrCdtTrf>
        </Document>
        """,
                message.getMsgId(),
                now,
                java.time.LocalDate.now(),
                endToEndId,
                uetr,
                message.getCurrency(),
                message.getAmount(),
                chargeBearer,
                message.getInstructingAgentBic(),
                message.getInstructedAgentBic(),
                debtorIban,
                creditorIban,
                remittanceInfo
        );
    }
    // ==================== PACS002 SORTANT (RÉPONSE) ====================

    public Pacs002Result generatePacs002(SwiftMessage original, String decision, String reason) {
        String xml = buildPacs002Xml(original, decision, reason);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("pacs.002_%s_%s.xml", original.getMsgId(), timestamp);

        // Sauvegarde sur disque
        try {
            Path target = Paths.get(generatePath, fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Pacs.002 sauvegardé dans msgGenerate : {}", target);
        } catch (IOException e) {
            log.warn("Impossible de sauvegarder le pacs.002 : {}", e.getMessage());
        }

        // Sauvegarde en base du PACS002 sortant
        SwiftMessage emitted = new SwiftMessage();
        emitted.setMessageType("PACS002");
        emitted.setMsgId("MSG" + System.currentTimeMillis());
        emitted.setDirection("OUT");
        emitted.setStatus("ENVOYE");
        emitted.setOriginalMsgId(original.getMsgId());
        emitted.setUetr(original.getUetr());
        emitted.setAmount(original.getAmount());
        emitted.setCurrency(original.getCurrency());
        emitted.setDebtorName(original.getDebtorName());
        emitted.setCreditorName(original.getCreditorName());
        emitted.setCreditorCountry(original.getCreditorCountry());
        emitted.setReceivedAt(LocalDateTime.now());
        emitted.setFileName(fileName);
        emitted.setGroupStatus(decision);
        if ("RJCT".equals(decision) && reason != null) {
            emitted.setRejectionReason(reason);
        }
        swiftMessageRepository.save(emitted);

        return new Pacs002Result(xml, fileName);
    }

    private String buildPacs002Xml(SwiftMessage tx, String decision, String reason) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String msgId = "MSG" + System.currentTimeMillis();

        String statusReason = "";
        if (reason != null && !reason.isBlank() && "RJCT".equals(decision)) {
            statusReason = "<StsRsnInf><Rsn><Prtry>" + escapeXml(reason) + "</Prtry></Rsn></StsRsnInf>";
        }

        String originalMsgNmId = "pacs.008.001.08";
        if ("PACS009".equals(tx.getMessageType())) {
            originalMsgNmId = "pacs.009.001.08";
        }

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12">
                <FIToFIPmtStsRpt>
                    <GrpHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                    </GrpHdr>
                    <OrgnlGrpInfAndSts>
                        <OrgnlMsgId>%s</OrgnlMsgId>
                        <OrgnlMsgNmId>%s</OrgnlMsgNmId>
                        <GrpSts>%s</GrpSts>
                        %s
                    </OrgnlGrpInfAndSts>
                </FIToFIPmtStsRpt>
            </Document>
            """, msgId, now, tx.getMsgId(), originalMsgNmId, decision, statusReason);
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static class Pacs002Result {
        private final String xmlContent;
        private final String fileName;
        public Pacs002Result(String xmlContent, String fileName) {
            this.xmlContent = xmlContent;
            this.fileName = fileName;
        }
        public String getXmlContent() { return xmlContent; }
        public String getFileName() { return fileName; }
    }
}