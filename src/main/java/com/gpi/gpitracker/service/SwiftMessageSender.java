package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
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

    // ==================== PACS002 (Réponse à une transaction) ====================

    public void sendPacs002(SwiftMessage original, String decision, String reason) {
        try {
            String xml = buildPacs002Xml(original, decision, reason);
            String fileName = String.format("pacs.002_%s_%d.xml",
                    original.getMsgId(), System.currentTimeMillis());
            Path target = Paths.get(emittedPath, fileName);
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Pacs.002 émis : {}", target);
        } catch (IOException e) {
            log.error("Erreur lors de l'écriture du pacs.002", e);
            throw new RuntimeException("Impossible d'émettre le pacs.002", e);
        }
    }

    private String buildPacs002Xml(SwiftMessage tx, String decision, String reason) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String msgId = "MSG" + System.currentTimeMillis();
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12">
                <FIToFIPmtStsRpt>
                    <GrpHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                    </GrpHdr>
                    <OrgnlGrpInfAndSts>
                        <OrgnlMsgId>%s</OrgnlMsgId>
                        <OrgnlMsgNmId>pacs.008.001.08</OrgnlMsgNmId>
                        <GrpSts>%s</GrpSts>
                        %s
                    </OrgnlGrpInfAndSts>
                </FIToFIPmtStsRpt>
            </Document>
            """.formatted(
                msgId, now, tx.getMsgId(), decision,
                (reason != null && !reason.isBlank()) ?
                        "<StsRsnInf><Rsn><Prtry>" + reason + "</Prtry></Rsn></StsRsnInf>" : ""
        );
    }

    // ==================== PACS009 (Transfert interbancaire) ====================

    public void sendPacs009(SwiftMessage original, String decision, String reason) {
        try {
            String xml = buildPacs009Xml(original, decision, reason);
            String fileName = String.format("pacs.009_%s_%d.xml",
                    original.getMsgId(), System.currentTimeMillis());
            Path target = Paths.get(emittedPath, fileName);
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Pacs.009 émis : {}", target);
        } catch (IOException e) {
            log.error("Erreur lors de l'écriture du pacs.009", e);
            throw new RuntimeException("Impossible d'émettre le pacs.009", e);
        }
    }

    private String buildPacs009Xml(SwiftMessage tx, String decision, String reason) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String msgId = "MSG" + System.currentTimeMillis();
        String statusReason = (reason != null && !reason.isBlank() && "RJCT".equals(decision)) ?
                "<StsRsnInf><Rsn><Prtry>" + reason + "</Prtry></Rsn></StsRsnInf>" : "";

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
                <FIToFIPmtStsRpt>
                    <GrpHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                    </GrpHdr>
                    <OrgnlGrpInfAndSts>
                        <OrgnlMsgId>%s</OrgnlMsgId>
                        <OrgnlMsgNmId>pacs.009.001.08</OrgnlMsgNmId>
                        <GrpSts>%s</GrpSts>
                        %s
                    </OrgnlGrpInfAndSts>
                </FIToFIPmtStsRpt>
            </Document>
            """.formatted(msgId, now, tx.getMsgId(), decision, statusReason);
    }

    // ==================== PACS008 (Paiement client - pour génération manuelle) ====================

    public void sendPacs008(SwiftMessage message) {
        try {
            String xml = buildPacs008Xml(message);
            String fileName = String.format("pacs.008_%s_%d.xml",
                    message.getMsgId(), System.currentTimeMillis());
            Path target = Paths.get(emittedPath, fileName);
            Files.writeString(target, xml, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Pacs.008 émis : {}", target);
        } catch (IOException e) {
            log.error("Erreur lors de l'écriture du pacs.008", e);
            throw new RuntimeException("Impossible d'émettre le pacs.008", e);
        }
    }

    private String buildPacs008Xml(SwiftMessage message) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        return """
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
                            <InstrId>%s</InstrId>
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
                            <PstlAdr>
                                <Ctry>%s</Ctry>
                            </PstlAdr>
                        </Cdtr>
                        <RmtInf>
                            <Ustrd>%s</Ustrd>
                        </RmtInf>
                    </CdtTrfTxInf>
                </FIToFICstmrCdtTrf>
            </Document>
            """.formatted(
                message.getMsgId(), now,
                message.getInstructionId() != null ? message.getInstructionId() : message.getMsgId(),
                message.getEndToEndId() != null ? message.getEndToEndId() : message.getMsgId(),
                message.getUetr(),
                message.getCurrency(), message.getAmount(),
                message.getDebtorName(),
                message.getCreditorName(),
                message.getCreditorCountry(),
                message.getRemittanceInfo() != null ? message.getRemittanceInfo() : ""
        );
    }
}