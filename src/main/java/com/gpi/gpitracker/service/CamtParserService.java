package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CamtParserService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final NotificationService notificationService;
    private final XmlValidationService xmlValidationService;

    public SwiftMessage parseCamt056(File xmlFile) {
        if (!xmlValidationService.validateXmlFile(xmlFile, XmlValidationService.TYPE_CAMT056)) {
            log.error("CAMT.056 invalide selon XSD : {}", xmlFile.getName());
            return null;
        }

        try {
            Document doc = parseXml(xmlFile);
            SwiftMessage message = new SwiftMessage();
            message.setMessageType("CAMT056");
            message.setFileName(xmlFile.getName());
            message.setDirection("IN");
            message.setReceivedAt(LocalDateTime.now());
            message.setStatus("RECEIVED");

            Element assgnmt = getFirstElement(doc, "Assgnmt");
            if (assgnmt != null) {
                message.setMsgId(getChildText(assgnmt, "Id"));
                Element assigner = getFirstChildElement(assgnmt, "Assgnr");
                if (assigner != null) {
                    message.setInstructingAgentBic(extractBicFromParty(assigner));
                }
                Element assignee = getFirstChildElement(assgnmt, "Assgne");
                if (assignee != null) {
                    message.setInstructedAgentBic(extractBicFromParty(assignee));
                }
            }

            Element underlying = getFirstElement(doc, "Undrlyg");
            if (underlying != null) {
                Element txInf = getFirstChildElement(underlying, "TxInf");
                if (txInf != null) {
                    message.setOriginalUetr(getChildText(txInf, "OrgnlUETR"));
                    message.setUetr(message.getOriginalUetr());
                    message.setOriginalMsgId(getChildText(txInf, "OrgnlInstrId"));
                    message.setEndToEndId(getChildText(txInf, "OrgnlEndToEndId"));

                    Element originalAmt = getFirstChildElement(txInf, "OrgnlIntrBkSttlmAmt");
                    if (originalAmt != null) {
                        message.setCurrency(originalAmt.getAttribute("Ccy"));
                        String amtText = originalAmt.getTextContent();
                        if (amtText != null && !amtText.isBlank()) {
                            message.setAmount(new BigDecimal(amtText.trim()));
                        }
                    }

                    Element cxlRsnInf = getFirstChildElement(txInf, "CxlRsnInf");
                    if (cxlRsnInf != null) {
                        Element rsn = getFirstChildElement(cxlRsnInf, "Rsn");
                        if (rsn != null) {
                            Element cd = getFirstChildElement(rsn, "Cd");
                            if (cd != null) {
                                message.setCancellationReason(cd.getTextContent());
                            }
                        }
                        message.setCancellationReasonText(getChildText(cxlRsnInf, "AddtlInf"));
                    }
                }
            }

            if (message.getMsgId() == null || message.getMsgId().isBlank()) {
                message.setMsgId("CAMT056_" + System.currentTimeMillis());
            }

            swiftMessageRepository.save(message);
            updateOriginalTransactionForCamt056(message);
            log.info("CAMT.056 sauvegardé : MsgId={}", message.getMsgId());
            return message;

        } catch (Exception e) {
            log.error("Erreur parsing CAMT.056 : {}", e.getMessage(), e);
            return null;
        }
    }

    public SwiftMessage parseCamt029(File xmlFile) {
        if (!xmlValidationService.validateXmlFile(xmlFile, XmlValidationService.TYPE_CAMT029)) {
            log.error("CAMT.029 invalide selon XSD : {}", xmlFile.getName());
            return null;
        }

        try {
            Document doc = parseXml(xmlFile);
            SwiftMessage message = new SwiftMessage();
            message.setMessageType("CAMT029");
            message.setFileName(xmlFile.getName());
            message.setDirection("IN");
            message.setReceivedAt(LocalDateTime.now());
            message.setStatus("RECEIVED");

            Element assgnmt = getFirstElement(doc, "Assgnmt");
            if (assgnmt != null) {
                message.setMsgId(getChildText(assgnmt, "Id"));
                Element assigner = getFirstChildElement(assgnmt, "Assgnr");
                if (assigner != null) {
                    message.setInstructingAgentBic(extractBicFromParty(assigner));
                }
                Element assignee = getFirstChildElement(assgnmt, "Assgne");
                if (assignee != null) {
                    message.setInstructedAgentBic(extractBicFromParty(assignee));
                }
            }

            String resolutionStatus = null;
            Element sts = getFirstElement(doc, "Sts");
            if (sts != null) {
                Element conf = getFirstChildElement(sts, "Conf");
                if (conf != null) {
                    resolutionStatus = conf.getTextContent().trim();
                    message.setGroupStatus(resolutionStatus);
                }
            }

            Element cxlDtls = getFirstElement(doc, "CxlDtls");
            if (cxlDtls != null) {
                Element txInfAndSts = getFirstChildElement(cxlDtls, "TxInfAndSts");
                if (txInfAndSts != null) {
                    message.setOriginalUetr(getChildText(txInfAndSts, "OrgnlUETR"));
                    message.setUetr(message.getOriginalUetr());

                    String txCxlSts = getChildText(txInfAndSts, "TxCxlSts");
                    if (txCxlSts != null && !txCxlSts.isBlank()) {
                        resolutionStatus = txCxlSts;
                        message.setGroupStatus(txCxlSts);
                    }
                }
            }

            if (message.getMsgId() == null || message.getMsgId().isBlank()) {
                message.setMsgId("CAMT029_" + System.currentTimeMillis());
            }

            swiftMessageRepository.save(message);
            updateOriginalTransactionForCamt029(message, resolutionStatus);
            log.info("CAMT.029 sauvegardé : MsgId={}", message.getMsgId());
            return message;

        } catch (Exception e) {
            log.error("Erreur parsing CAMT.029 : {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractBicFromParty(Element partyElement) {
        if (partyElement == null) return null;
        Element agt = getFirstChildElement(partyElement, "Agt");
        if (agt != null) {
            Element finInstnId = getFirstChildElement(agt, "FinInstnId");
            if (finInstnId != null) {
                return getChildText(finInstnId, "BICFI");
            }
        }
        return null;
    }

    private void updateOriginalTransactionForCamt056(SwiftMessage camt056) {
        if (camt056.getOriginalUetr() == null || camt056.getOriginalUetr().isBlank()) {
            log.warn("CAMT.056 sans OriginalUETR");
            return;
        }
        swiftMessageRepository.findByUetr(camt056.getOriginalUetr()).ifPresent(original -> {
            original.setCancellationStatus("REQUESTED");
            original.setStatus("ANNULATION_EN_ATTENTE");
            swiftMessageRepository.save(original);

            // ⭐⭐ NOTIFICATION : Demande d'annulation envoyée ⭐⭐
            if (original.getClientEmail() != null && !original.getClientEmail().isBlank()) {
                notificationService.createNotification(
                        original.getClientEmail(),
                        "Demande d'annulation envoyée",
                        "Votre demande d'annulation a été envoyée à la banque destinataire. En attente de confirmation.",
                        "warn",
                        original.getUetr()
                );
            }
        });
    }

    private void updateOriginalTransactionForCamt029(SwiftMessage camt029, String resolutionStatus) {
        if (camt029.getOriginalUetr() == null || camt029.getOriginalUetr().isBlank()) {
            log.warn("CAMT.029 sans OriginalUETR");
            return;
        }
        swiftMessageRepository.findByUetr(camt029.getOriginalUetr()).ifPresent(original -> {
            String notificationTitle = "";
            String notificationMessage = "";
            String notificationType = "";

            if ("CNCL".equals(resolutionStatus)) {
                original.setStatus("ANNULEE");
                original.setCancellationStatus("ACCEPT");
                notificationTitle = "✅ Annulation acceptée";
                notificationMessage = "Votre demande d'annulation a été acceptée. La transaction est définitivement annulée.";
                notificationType = "success";

            } else if ("RJCR".equals(resolutionStatus)) {
                original.setStatus("ACCEPTE");
                original.setCancellationStatus("REJECT");
                notificationTitle = "❌ Annulation rejetée";
                notificationMessage = "Votre demande d'annulation a été rejetée. La transaction reste acceptée.";
                notificationType = "error";

            } else if ("PDCR".equals(resolutionStatus)) {
                original.setStatus("ANNULATION_EN_ATTENTE");
                original.setCancellationStatus("PEND");
                notificationTitle = "⏳ Annulation en attente";
                notificationMessage = "Votre demande d'annulation est encore en cours de traitement.";
                notificationType = "warn";
            }

            // Sauvegarder les informations d'annulation dans la transaction originale
            if (camt029.getCancellationReason() != null) {
                original.setCancellationReason(camt029.getCancellationReason());
            }
            if (camt029.getCancellationReasonText() != null) {
                original.setCancellationReasonText(camt029.getCancellationReasonText());
            }

            swiftMessageRepository.save(original);

            // ⭐⭐ NOTIFICATION : Résultat de la demande d'annulation ⭐⭐
            if (original.getClientEmail() != null && !original.getClientEmail().isBlank()) {
                notificationService.createNotification(
                        original.getClientEmail(),
                        notificationTitle,
                        notificationMessage,
                        notificationType,
                        original.getUetr()
                );
            }
        });
    }

    private Document parseXml(File xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private Element getFirstElement(Document doc, String tagName) {
        var nodes = doc.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private Element getFirstChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        var nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String getChildText(Element parent, String tagName) {
        if (parent == null) return null;
        var nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }
}