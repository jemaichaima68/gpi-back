package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import com.prowidesoftware.swift.model.mx.BusinessAppHdrV04;
import com.prowidesoftware.swift.model.mx.MxPacs00200110;
import com.prowidesoftware.swift.model.mx.dic.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftMessageSender {

    @Value("${swift.generate.path}")
    private String generatePath;

    private final SwiftMessageRepository swiftMessageRepository;

    // ==================== PACS002 ====================

    public Pacs002Result generatePacs002(SwiftMessage original, String decision, String reason) {

        String fileName = "pacs002_" + original.getMsgId() + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xml";

        try {

            // ===================== 1. BUILD DOCUMENT =====================
            MxPacs00200110 document = new MxPacs00200110();

            FIToFIPaymentStatusReportV10 report = new FIToFIPaymentStatusReportV10();
            document.setFIToFIPmtStsRpt(report);

            // ===== Group Header =====
            GroupHeader91 grpHdr = new GroupHeader91();
            grpHdr.setMsgId("MSG" + System.currentTimeMillis());
            grpHdr.setCreDtTm(OffsetDateTime.now());
            report.setGrpHdr(grpHdr);

            // ===== Original Group =====
            OriginalGroupHeader17 org = new OriginalGroupHeader17();
            org.setOrgnlMsgId(original.getMsgId());

            String type = "pacs.008.001.08";
            if ("PACS009".equals(original.getMessageType())) {
                type = "pacs.009.001.08";
            }

            org.setOrgnlMsgNmId(type);
            org.setGrpSts(decision);

            report.getOrgnlGrpInfAndSts().add(org);

            // ===== Transaction =====
            PaymentTransaction110 tx = new PaymentTransaction110();
            tx.setOrgnlInstrId(original.getMsgId());
            tx.setTxSts(decision);

            report.getTxInfAndSts().add(tx);

            // ===================== 2. GENERATE XML =====================
            String xml = document.message();
            log.info("📄 PACS002 generated");

            // ===================== 3. VALIDATION =====================
            validateXml(xml);

            // ===================== 4. APP HEADER =====================
            BusinessAppHdrV04 appHdr = new BusinessAppHdrV04();
            appHdr.setBizMsgIdr("BIZ" + System.currentTimeMillis());
            appHdr.setMsgDefIdr("pacs.002.001.10");
            appHdr.setCreDt(OffsetDateTime.now());

            document.setAppHdr(appHdr);

            String finalXml = document.message();

            // ===================== 5. SAVE FILE =====================
            Path path = Paths.get(generatePath, fileName);
            Files.createDirectories(path.getParent());
            Files.writeString(path, finalXml);

            log.info("💾 PACS002 saved: {}", path);

            // ===================== 6. SAVE DB =====================
            SwiftMessage emitted = new SwiftMessage();
            emitted.setMessageType("PACS002");
            emitted.setMsgId(grpHdr.getMsgId());
            emitted.setDirection("OUT");
            emitted.setStatus("ENVOYE");
            emitted.setOriginalMsgId(original.getMsgId());
            emitted.setUetr(original.getUetr());
            emitted.setAmount(original.getAmount());
            emitted.setCurrency(original.getCurrency());
            emitted.setReceivedAt(LocalDateTime.now());
            emitted.setFileName(fileName);
            emitted.setGroupStatus(decision);

            if ("RJCT".equals(decision)) {
                emitted.setRejectionReason(reason);
            }

            swiftMessageRepository.save(emitted);

            log.info("🚀 PACS002 READY & VALID");

            return new Pacs002Result(finalXml, fileName);

        } catch (Exception e) {
            log.error("❌ ERROR generating PACS002", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== VALIDATION ====================

    private void validateXml(String xml) {
        try {
            log.info("🔍 Validating XML against XSD...");

            var factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");

            var resource = getClass().getClassLoader()
                    .getResource("xsd/pacs.002.001.10.xsd");

            if (resource == null) {
                throw new RuntimeException("❌ XSD not found in resources/xsd");
            }

            var schema = factory.newSchema(new java.io.File(resource.toURI()));
            var validator = schema.newValidator();

            validator.validate(new StreamSource(new StringReader(xml)));

            log.info("✅ XML VALID (XSD OK)");

        } catch (Exception e) {
            log.error("❌ XML INVALID");
            log.error("Reason: {}", e.getMessage());
            throw new RuntimeException("XSD validation failed", e);
        }
    }

    // ==================== RESULT ====================

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