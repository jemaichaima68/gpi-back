package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SwiftParserService {

    private static final Logger log = LoggerFactory.getLogger(SwiftParserService.class);

    /**
     * Point d'entrée principal : détecte le type de pacs et délègue au bon parser.
     * Pour ajouter un nouveau type (pacs.009, pacs.002...) :
     *   → ajouter simplement un nouveau "case" dans le switch.
     */
    public SwiftMessage parse(File xmlFile) {
        String messageType = detectMessageType(xmlFile);
        log.info("Type détecté : {} pour le fichier {}", messageType, xmlFile.getName());

        return switch (messageType) {
            case "PACS008" -> parsePacs008(xmlFile);
            // case "PACS009" -> parsePacs009(xmlFile);  // à ajouter plus tard
            // case "PACS002" -> parsePacs002(xmlFile);  // à ajouter plus tard
            default -> {
                log.warn("Type de message non supporté : {}", messageType);
                yield null;
            }
        };
    }

    // ================================================================
    // Détection automatique du type via le nom de fichier ou le contenu
    // ================================================================

    private String detectMessageType(File xmlFile) {
        String name = xmlFile.getName().toLowerCase();
        if (name.contains("pacs.008") || name.contains("pacs008")) return "PACS008";
        if (name.contains("pacs.009") || name.contains("pacs009")) return "PACS009";
        if (name.contains("pacs.002") || name.contains("pacs002")) return "PACS002";
        // fallback : lire la balise racine du XML
        return detectFromXmlRoot(xmlFile);
    }

    private String detectFromXmlRoot(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().parse(xmlFile);
            String rootName = doc.getDocumentElement().getTagName();
            if (rootName.contains("pacs.008")) return "PACS008";
            if (rootName.contains("pacs.009")) return "PACS009";
            if (rootName.contains("pacs.002")) return "PACS002";
        } catch (Exception e) {
            log.error("Impossible de lire la racine XML de {} : {}", xmlFile.getName(), e.getMessage());
        }
        return "UNKNOWN";
    }

    // ================================================================
    // Parser PACS008 — FIToFICustomerCreditTransfer
    // ================================================================

    private SwiftMessage parsePacs008(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            SwiftMessage message = new SwiftMessage();
            message.setMessageType("PACS008");   // <-- type explicitement positionné

            // ============================================================
            // GrpHdr — Group Header
            // ============================================================
            Element grpHdr = getFirstElement(doc, "GrpHdr");

            message.setMsgId(getChildText(grpHdr, "MsgId"));

            String creDtTm = getChildText(grpHdr, "CreDtTm");
            if (creDtTm != null && !creDtTm.isEmpty()) {
                message.setCreationDateTime(
                        LocalDateTime.parse(creDtTm, DateTimeFormatter.ISO_DATE_TIME)
                );
            }

            message.setNbOfTransactions(parseIntSafe(getChildText(grpHdr, "NbOfTxs")));
            message.setSettlementDate(getChildText(grpHdr, "IntrBkSttlmDt"));

            Element instgAgt = getFirstElement(doc, "InstgAgt");
            if (instgAgt != null) {
                Element fi = getFirstChildElement(instgAgt, "FinInstnId");
                if (fi != null) message.setInstructingAgentBic(getChildText(fi, "BICFI"));
            }

            Element instdAgt = getFirstElement(doc, "InstdAgt");
            if (instdAgt != null) {
                Element fi = getFirstChildElement(instdAgt, "FinInstnId");
                if (fi != null) message.setInstructedAgentBic(getChildText(fi, "BICFI"));
            }

            // ============================================================
            // CdtTrfTxInf — Credit Transfer Transaction Info
            // ============================================================
            Element cdtTrf = getFirstElement(doc, "CdtTrfTxInf");

            Element pmtId = getFirstChildElement(cdtTrf, "PmtId");
            if (pmtId != null) {
                message.setInstructionId(getChildText(pmtId, "InstrId"));
                message.setEndToEndId(getChildText(pmtId, "EndToEndId"));
                message.setTransactionId(getChildText(pmtId, "TxId"));
                message.setUetr(getChildText(pmtId, "UETR"));
            }

            NodeList amtNodes = doc.getElementsByTagName("InstdAmt");
            if (amtNodes.getLength() > 0) {
                Element amtEl = (Element) amtNodes.item(0);
                String amtText = amtEl.getTextContent().trim();
                if (!amtText.isEmpty()) message.setAmount(new BigDecimal(amtText));
                message.setCurrency(amtEl.getAttribute("Ccy"));
            }

            message.setChargeBearer(getChildText(cdtTrf, "ChrgBr"));

            // ---- DÉBITEUR ----
            Element dbtr = getFirstChildElement(cdtTrf, "Dbtr");
            if (dbtr != null) {
                message.setDebtorName(getChildText(dbtr, "Nm"));
                Element adr = getFirstChildElement(dbtr, "PstlAdr");
                if (adr != null) {
                    message.setDebtorCountry(getChildText(adr, "Ctry"));
                    message.setDebtorAddress(getChildText(adr, "AdrLine"));
                }
            }

            Element dbtrAcct = getFirstChildElement(cdtTrf, "DbtrAcct");
            if (dbtrAcct != null) {
                Element id = getFirstChildElement(dbtrAcct, "Id");
                if (id != null) message.setDebtorIban(getChildText(id, "IBAN"));
            }

            Element dbtrAgt = getFirstChildElement(cdtTrf, "DbtrAgt");
            if (dbtrAgt != null) {
                Element fi = getFirstChildElement(dbtrAgt, "FinInstnId");
                if (fi != null) message.setDebtorAgentBic(getChildText(fi, "BICFI"));
            }

            // ---- CRÉDITEUR ----
            Element cdtr = getFirstChildElement(cdtTrf, "Cdtr");
            if (cdtr != null) {
                message.setCreditorName(getChildText(cdtr, "Nm"));
                Element adr = getFirstChildElement(cdtr, "PstlAdr");
                if (adr != null) {
                    message.setCreditorCountry(getChildText(adr, "Ctry"));
                    message.setCreditorAddress(getChildText(adr, "AdrLine"));
                }
            }

            Element cdtrAcct = getFirstChildElement(cdtTrf, "CdtrAcct");
            if (cdtrAcct != null) {
                Element id = getFirstChildElement(cdtrAcct, "Id");
                if (id != null) message.setCreditorIban(getChildText(id, "IBAN"));
            }

            Element cdtrAgt = getFirstChildElement(cdtTrf, "CdtrAgt");
            if (cdtrAgt != null) {
                Element fi = getFirstChildElement(cdtrAgt, "FinInstnId");
                if (fi != null) message.setCreditorAgentBic(getChildText(fi, "BICFI"));
            }

            Element rmtInf = getFirstChildElement(cdtTrf, "RmtInf");
            if (rmtInf != null) message.setRemittanceInfo(getChildText(rmtInf, "Ustrd"));

            // Métadonnées système
            message.setFileName(xmlFile.getName());
            message.setStatus("RECEIVED");
            message.setReceivedAt(LocalDateTime.now());

            log.info("Parsing OK — MsgId: {} | UETR: {} | {} {} | {} → {}",
                    message.getMsgId(), message.getUetr(),
                    message.getAmount(), message.getCurrency(),
                    message.getDebtorName(), message.getCreditorName());

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.008 [{}] : {}", xmlFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ================================================================
    // Méthodes utilitaires (partagées par tous les parsers)
    // ================================================================

    private Element getFirstElement(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private Element getFirstChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private String getChildText(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? list.item(0).getTextContent().trim() : null;
    }

    private int parseIntSafe(String value) {
        try { return value != null ? Integer.parseInt(value.trim()) : 0; }
        catch (NumberFormatException e) { return 0; }
    }
}