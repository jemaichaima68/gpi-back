package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.AppUser;
import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.AppUserRepository;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftParserService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final CamtParserService camtParserService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final BankDirectoryRepository bankDirectoryRepository;
    private final XmlValidationService xmlValidationService;

    // ==================== CONSTANTES ====================
    private static final String MSG_TYPE_PACS008 = "PACS008";
    private static final String MSG_TYPE_PACS009 = "PACS009";
    private static final String MSG_TYPE_PACS002 = "PACS002";
    private static final String MSG_TYPE_CAMT056 = "CAMT056";
    private static final String MSG_TYPE_CAMT029 = "CAMT029";
    private static final String STATUS_PENDING = "EN_ATTENTE";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String TAG_BICFI = "BICFI";
    private static final String TAG_FIN_INSTN_ID = "FinInstnId";
    private static final String DIRECTION_IN = "IN";
    private static final String XML_TAG_MSG_ID = "MsgId";
    private static final String XML_TAG_CRE_DT_TM = "CreDtTm";
    private static final String XML_TAG_INSTD_AMT = "InstdAmt";
    private static final String XML_TAG_INTR_BK_STTLM_AMT = "IntrBkSttlmAmt";
    private static final String XML_TAG_CDTR_TRF_TX_INF = "CdtTrfTxInf";
    private static final String XML_TAG_PMT_ID = "PmtId";
    private static final String XML_TAG_INSTR_ID = "InstrId";
    private static final String XML_TAG_END_TO_END_ID = "EndToEndId";
    private static final String XML_TAG_UETR = "UETR";
    private static final String XML_TAG_CHRG_BR = "ChrgBr";
    private static final String XML_TAG_DBTR = "Dbtr";
    private static final String XML_TAG_NM = "Nm";
    private static final String XML_TAG_PSTL_ADR = "PstlAdr";
    private static final String XML_TAG_CTRY = "Ctry";
    private static final String XML_TAG_CDTR = "Cdtr";
    private static final String XML_TAG_DBTR_ACCT = "DbtrAcct";
    private static final String XML_TAG_CDTR_ACCT = "CdtrAcct";
    private static final String XML_TAG_ID = "Id";
    private static final String XML_TAG_IBAN = "IBAN";
    private static final String XML_TAG_DBTR_AGT = "DbtrAgt";
    private static final String XML_TAG_CDTR_AGT = "CdtrAgt";
    private static final String XML_TAG_INSTG_AGT = "InstgAgt";
    private static final String XML_TAG_INSTD_AGT = "InstdAgt";
    private static final String XML_TAG_RMT_INF = "RmtInf";
    private static final String XML_TAG_USTRD = "Ustrd";
    private static final String XML_TAG_GRP_HDR = "GrpHdr";
    private static final String XML_TAG_TX_INF_AND_STS = "TxInfAndSts";
    private static final String XML_TAG_ORGNL_GRP_INF = "OrgnlGrpInf";
    private static final String XML_TAG_ORGNL_MSG_ID = "OrgnlMsgId";
    private static final String XML_TAG_ORGNL_UETR = "OrgnlUETR";
    private static final String XML_TAG_ORGNL_END_TO_END_ID = "OrgnlEndToEndId";
    private static final String XML_TAG_TX_STS = "TxSts";
    private static final String XML_TAG_STS_RSN_INF = "StsRsnInf";
    private static final String XML_TAG_RSN = "Rsn";
    private static final String XML_TAG_CD = "Cd";
    private static final String XML_TAG_ADDTL_INF = "AddtlInf";

    public SwiftMessage parse(File xmlFile) {
        String type = detectMessageType(xmlFile);
        log.info("Type détecté : {} pour {}", type, xmlFile.getName());

        return switch (type) {
            case MSG_TYPE_PACS008 -> parsePacs008(xmlFile);
            case MSG_TYPE_PACS009 -> parsePacs009(xmlFile);
            case MSG_TYPE_PACS002 -> parsePacs002(xmlFile);
            case MSG_TYPE_CAMT056 -> camtParserService.parseCamt056(xmlFile);
            case MSG_TYPE_CAMT029 -> camtParserService.parseCamt029(xmlFile);
            default -> {
                log.warn("Type non supporté : {}", type);
                yield null;
            }
        };
    }

    private String detectMessageType(File xmlFile) {
        String name = xmlFile.getName().toLowerCase();

        if (name.contains("pacs.008") || name.contains("pacs008") || name.contains("008")) return MSG_TYPE_PACS008;
        if (name.contains("pacs.009") || name.contains("pacs009") || name.contains("009")) return MSG_TYPE_PACS009;
        if (name.contains("pacs.002") || name.contains("pacs002") || name.contains("002")) return MSG_TYPE_PACS002;
        if (name.contains("camt.056") || name.contains("camt056") || name.contains("056")) return MSG_TYPE_CAMT056;
        if (name.contains("camt.029") || name.contains("camt029") || name.contains("029")) return MSG_TYPE_CAMT029;

        return detectFromXml(xmlFile);
    }

    private String detectFromXml(File xmlFile) {
        try {
            Document doc = parseXml(xmlFile);
            String namespace = doc.getDocumentElement().getNamespaceURI();
            String content = namespace != null ? namespace : doc.getDocumentElement().getTextContent();

            if (content.contains("pacs.008")) return MSG_TYPE_PACS008;
            if (content.contains("pacs.009")) return MSG_TYPE_PACS009;
            if (content.contains("pacs.002")) return MSG_TYPE_PACS002;
            if (content.contains("camt.056")) return MSG_TYPE_CAMT056;
            if (content.contains("camt.029")) return MSG_TYPE_CAMT029;
        } catch (Exception e) {
            log.error("Détection XML impossible : {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    // ==================== MÉTHODES DE NETTOYAGE POUR XSD ====================

    /**
     * Nettoie une chaîne pour la rendre compatible avec les XSD PACS (Max35Text)
     * Caractères autorisés : a-z A-Z 0-9 / - ? : ( ) . , ' + espace
     */
    private String sanitizeForXsd(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN-" + System.currentTimeMillis();
        }

        String cleaned = value
                .replace("_", "-")
                .replace(" ", "-")
                .replaceAll("[^a-zA-Z0-9/\\-?:\\(\\)\\.,'\\+]", "-");

        if (cleaned.length() > 35) {
            cleaned = cleaned.substring(0, 35);
        }

        if (!cleaned.equals(value)) {
            log.debug("ID nettoyé: '{}' → '{}'", value, cleaned);
        }

        return cleaned;
    }

    /**
     * Valide et nettoie un UETR (UUID v4)
     */
    private String sanitizeUetr(String uetr) {
        if (uetr == null || uetr.isBlank()) {
            log.warn("UETR manquant, génération d'un nouveau UUID");
            return UUID.randomUUID().toString();
        }

        String uuidPattern = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}";
        if (!uetr.toLowerCase().matches(uuidPattern)) {
            log.warn("UETR invalide: '{}', génération d'un nouveau UUID", uetr);
            return UUID.randomUUID().toString();
        }

        return uetr.toLowerCase();
    }

    // ==================== PARSING PACS008 ====================

    private SwiftMessage parsePacs008(File xmlFile) {
        SwiftMessage message = parseCommonPayment(xmlFile, MSG_TYPE_PACS008);

        if (message != null) {
            autoRegisterBank(message.getDebtorAgentBic());
            autoRegisterBank(message.getCreditorAgentBic());
            log.info("🏦 Banques PACS008 enregistrées: D={}, C={}",
                    message.getDebtorAgentBic(), message.getCreditorAgentBic());
        }

        return message;
    }

    // ==================== PARSING PACS009 ====================

    private SwiftMessage parsePacs009(File xmlFile) {
        SwiftMessage message = parseCommonPayment(xmlFile, MSG_TYPE_PACS009);

        if (message == null) {
            log.error("❌ Échec parsing PACS009");
            return null;
        }

        log.info("📨 PACS009 reçu - UETR: {}", message.getUetr());
        log.info("🔍 BIC émetteur (InstgAgt): {}", message.getInstructingAgentBic());
        log.info("🔍 BIC récepteur (InstdAgt): {}", message.getInstructedAgentBic());

        if (message.getInstructingAgentBic() != null && !message.getInstructingAgentBic().isBlank()) {
            autoRegisterBank(message.getInstructingAgentBic());
        }
        if (message.getInstructedAgentBic() != null && !message.getInstructedAgentBic().isBlank()) {
            autoRegisterBank(message.getInstructedAgentBic());
        }

        log.info("🏦 Banques PACS009 enregistrées avec succès");

        return message;
    }

    // ==================== PARSING COMMUN PACS008/PACS009 ====================

    private SwiftMessage parseCommonPayment(File xmlFile, String messageType) {
        try {
            Document doc = parseXml(xmlFile);

            SwiftMessage message = new SwiftMessage();
            message.setMessageType(messageType);
            message.setFileName(xmlFile.getName());
            message.setStatus(STATUS_PENDING);
            message.setReceivedAt(LocalDateTime.now());

            Element grpHdr = getFirstElement(doc, XML_TAG_GRP_HDR);
            if (grpHdr != null) {
                String rawMsgId = getChildText(grpHdr, XML_TAG_MSG_ID);
                message.setMsgId(sanitizeForXsd(rawMsgId));

                String creDtTm = getChildText(grpHdr, XML_TAG_CRE_DT_TM);
                if (creDtTm != null && !creDtTm.isBlank()) {
                    try {
                        message.setCreationDateTime(LocalDateTime.parse(creDtTm, DateTimeFormatter.ISO_DATE_TIME));
                    } catch (Exception ignored) {
                        log.warn("Date non parsée : {}", creDtTm);
                    }
                }
            }

            Element txInf = getFirstElement(doc, XML_TAG_CDTR_TRF_TX_INF);
            String debtorName = null;
            String debtorIban = null;

            if (txInf != null) {
                Element pmtId = getFirstChildElement(txInf, XML_TAG_PMT_ID);
                if (pmtId != null) {
                    String rawInstrId = getChildText(pmtId, XML_TAG_INSTR_ID);
                    String rawEndToEndId = getChildText(pmtId, XML_TAG_END_TO_END_ID);
                    String rawUetr = getChildText(pmtId, XML_TAG_UETR);

                    message.setInstructionId(sanitizeForXsd(rawInstrId));
                    message.setEndToEndId(sanitizeForXsd(rawEndToEndId));
                    message.setUetr(sanitizeUetr(rawUetr));
                }

                Element amt = getFirstElement(doc, XML_TAG_INSTD_AMT);
                if (amt == null) amt = getFirstElement(doc, XML_TAG_INTR_BK_STTLM_AMT);
                if (amt != null) {
                    message.setCurrency(amt.getAttribute("Ccy"));
                    if (amt.getTextContent() != null && !amt.getTextContent().isBlank()) {
                        message.setAmount(new BigDecimal(amt.getTextContent().trim()));
                    }
                }

                message.setChargeBearer(getChildText(txInf, XML_TAG_CHRG_BR));

                Element dbtr = getFirstChildElement(txInf, XML_TAG_DBTR);
                if (dbtr != null) {
                    debtorName = getChildText(dbtr, XML_TAG_NM);
                    message.setDebtorName(debtorName);

                    Element dbtrPstlAdr = getFirstChildElement(dbtr, XML_TAG_PSTL_ADR);
                    if (dbtrPstlAdr != null) {
                        String debtorCountry = getChildText(dbtrPstlAdr, XML_TAG_CTRY);
                        if (debtorCountry != null && !debtorCountry.isBlank()) {
                            message.setDebtorCountry(debtorCountry);
                            log.info("Pays débiteur extrait: {}", debtorCountry);
                        }
                    }
                }

                Element cdtr = getFirstChildElement(txInf, XML_TAG_CDTR);
                if (cdtr != null) {
                    message.setCreditorName(getChildText(cdtr, XML_TAG_NM));

                    Element cdtrPstlAdr = getFirstChildElement(cdtr, XML_TAG_PSTL_ADR);
                    if (cdtrPstlAdr != null) {
                        String creditorCountry = getChildText(cdtrPstlAdr, XML_TAG_CTRY);
                        if (creditorCountry != null && !creditorCountry.isBlank()) {
                            message.setCreditorCountry(creditorCountry);
                            log.info("Pays créditeur extrait: {}", creditorCountry);
                        }
                    }
                }

                if (MSG_TYPE_PACS008.equals(messageType)) {
                    debtorIban = extractIban(txInf, XML_TAG_DBTR_ACCT);
                    message.setDebtorIban(debtorIban);
                    message.setCreditorIban(extractIban(txInf, XML_TAG_CDTR_ACCT));
                }

                message.setDebtorAgentBic(extractBic(txInf, XML_TAG_DBTR_AGT));
                message.setCreditorAgentBic(extractBic(txInf, XML_TAG_CDTR_AGT));
                message.setInstructingAgentBic(extractBic(txInf, XML_TAG_INSTG_AGT));
                message.setInstructedAgentBic(extractBic(txInf, XML_TAG_INSTD_AGT));

                Element rmtInf = getFirstChildElement(txInf, XML_TAG_RMT_INF);
                if (rmtInf != null) message.setRemittanceInfo(getChildText(rmtInf, XML_TAG_USTRD));
            }

            // Fallbacks
            if (message.getMsgId() == null || message.getMsgId().isBlank()) {
                message.setMsgId(messageType + "-" + System.currentTimeMillis());
            }

            if (message.getUetr() == null || message.getUetr().isBlank()) {
                message.setUetr(UUID.randomUUID().toString());
            }

            if (MSG_TYPE_PACS008.equals(messageType)) {
                assignClientAndSendEmail(message, debtorName, debtorIban);
            } else {
                log.info("📨 PACS009 reçu (interbancaire) - Pas d'association client");
            }

            log.info("Transaction parsée: UETR={}, Type={}, ClientEmail={}",
                    message.getUetr(), messageType, message.getClientEmail());

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing {} : {}", messageType, e.getMessage(), e);
            return null;
        }
    }

    private void autoRegisterBank(String bic) {
        if (bic == null || bic.isBlank()) {
            log.debug("BIC null ou vide, ignoré");
            return;
        }

        String bicUpper = bic.toUpperCase().trim();
        log.info("🏦 Enregistrement banque: {}", bicUpper);

        try {
            Optional<BankDirectory> existing = bankDirectoryRepository.findByBicIgnoreCase(bicUpper);

            if (existing.isPresent()) {
                BankDirectory bank = existing.get();
                bank.setOccurrenceCount(bank.getOccurrenceCount() + 1);
                bank.setLastSeenAt(LocalDateTime.now());
                bankDirectoryRepository.save(bank);
                log.info("✅ Banque existante, compteur incrémenté: {} -> {}", bicUpper, bank.getOccurrenceCount());
            } else {
                BankDirectory newBank = new BankDirectory();
                newBank.setBic(bicUpper);
                newBank.setBankName(bicUpper);
                newBank.setCountryCode("??");
                newBank.setFirstSeenAt(LocalDateTime.now());
                newBank.setLastSeenAt(LocalDateTime.now());
                newBank.setOccurrenceCount(1);
                bankDirectoryRepository.save(newBank);
                log.info("🏦 NOUVELLE BANQUE auto-enregistrée: {}", bicUpper);
            }
        } catch (Exception e) {
            log.error("❌ Erreur auto-enregistrement banque {}: {}", bicUpper, e.getMessage());
        }
    }

    // ==================== ASSIGNATION CLIENT ====================

    private void assignClientAndSendEmail(SwiftMessage message, String debtorName, String debtorIban) {
        try {
            String clientEmail = null;

            log.info("Recherche client pour: '{}' (IBAN: {})", debtorName, debtorIban);

            if (debtorIban != null && !debtorIban.isBlank()) {
                Optional<AppUser> userByIban = appUserRepository.findByIban(debtorIban);
                if (userByIban.isPresent()) {
                    clientEmail = userByIban.get().getEmail();
                    log.info("Client trouvé par IBAN: {} -> {}", debtorIban, clientEmail);
                }
            }

            if (clientEmail == null && debtorName != null && debtorName.contains("@")) {
                clientEmail = debtorName;
                log.info("Email extrait directement du debtorName: {}", clientEmail);
            }

            if (clientEmail == null && debtorName != null && !debtorName.isBlank()) {
                clientEmail = keycloakAdminService.getEmailByFullName(debtorName);
                if (clientEmail != null) {
                    log.info("Client trouvé par NOM: {} -> {}", debtorName, clientEmail);
                }
            }

            if (clientEmail != null) {
                message.setClientEmail(clientEmail);
                log.info("Client associé: {} -> {}", debtorName, clientEmail);

                boolean transactionExists = swiftMessageRepository.existsByUetr(message.getUetr());

                if (!transactionExists) {
                    emailService.sendTransactionReceivedEmailSync(
                            clientEmail,
                            debtorName != null ? debtorName : "Client",
                            message.getUetr(),
                            message.getAmount(),
                            message.getCurrency()
                    );
                    log.info("Email de reception envoyé à {} avec UETR: {}", clientEmail, message.getUetr());

                    notificationService.createNotification(
                            clientEmail,
                            "Nouvelle transaction reçue",
                            "Votre transaction SWIFT d'un montant de " + message.getAmount() + " " + message.getCurrency() +
                                    " (UETR: " + message.getUetr() + ") a été reçue et est en attente de validation.",
                            "info",
                            message.getUetr()
                    );
                    log.info("Notification envoyée à {}", clientEmail);
                } else {
                    log.warn("Transaction déjà existante pour UETR: {}, email non envoyé", message.getUetr());
                }
            } else {
                log.warn("Transaction non associée - IBAN: {}, Nom: {}", debtorIban, debtorName);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'association client: {}", e.getMessage(), e);
        }
    }

    // ==================== PARSING PACS002 ====================

    private SwiftMessage parsePacs002(File xmlFile) {
        if (!xmlValidationService.validateXmlFile(xmlFile, XmlValidationService.TYPE_PACS002)) {
            log.error("PACS.002 invalide selon XSD : {}", xmlFile.getName());
            return null;
        }

        try {
            Document doc = parseXml(xmlFile);

            SwiftMessage message = new SwiftMessage();
            message.setMessageType(MSG_TYPE_PACS002);
            message.setFileName(xmlFile.getName());
            message.setDirection(DIRECTION_IN);
            message.setReceivedAt(LocalDateTime.now());
            message.setStatus(STATUS_RECEIVED);

            Element grpHdr = getFirstElement(doc, XML_TAG_GRP_HDR);
            if (grpHdr != null) {
                message.setMsgId(getChildText(grpHdr, XML_TAG_MSG_ID));
            }

            Element txInfAndSts = getFirstElement(doc, XML_TAG_TX_INF_AND_STS);
            if (txInfAndSts != null) {
                Element orgnlGrpInf = getFirstChildElement(txInfAndSts, XML_TAG_ORGNL_GRP_INF);
                if (orgnlGrpInf != null) {
                    message.setOriginalMsgId(getChildText(orgnlGrpInf, XML_TAG_ORGNL_MSG_ID));
                }

                message.setOriginalUetr(getChildText(txInfAndSts, XML_TAG_ORGNL_UETR));
                message.setUetr(message.getOriginalUetr());
                message.setEndToEndId(getChildText(txInfAndSts, XML_TAG_ORGNL_END_TO_END_ID));

                String txSts = getChildText(txInfAndSts, XML_TAG_TX_STS);
                if (txSts != null) {
                    message.setGroupStatus(txSts);
                    message.setStatus(mapPacs002Status(txSts));
                }

                Element stsRsnInf = getFirstChildElement(txInfAndSts, XML_TAG_STS_RSN_INF);
                if (stsRsnInf != null) {
                    Element rsn = getFirstChildElement(stsRsnInf, XML_TAG_RSN);
                    if (rsn != null) {
                        String reasonCd = getChildText(rsn, XML_TAG_CD);
                        if (reasonCd != null) {
                            message.setRejectionReason(reasonCd);
                        }
                    }
                    String addtlInf = getChildText(stsRsnInf, XML_TAG_ADDTL_INF);
                    if (addtlInf != null && message.getRejectionReason() == null) {
                        message.setRejectionReason(addtlInf);
                    }
                }

                Element instgAgt = getFirstChildElement(txInfAndSts, XML_TAG_INSTG_AGT);
                if (instgAgt != null) {
                    Element finInstnId = getFirstChildElement(instgAgt, TAG_FIN_INSTN_ID);
                    if (finInstnId != null) {
                        message.setInstructingAgentBic(getChildText(finInstnId, TAG_BICFI));
                    }
                }

                Element instdAgt = getFirstChildElement(txInfAndSts, XML_TAG_INSTD_AGT);
                if (instdAgt != null) {
                    Element finInstnId = getFirstChildElement(instdAgt, TAG_FIN_INSTN_ID);
                    if (finInstnId != null) {
                        message.setInstructedAgentBic(getChildText(finInstnId, TAG_BICFI));
                    }
                }
            }

            if (message.getMsgId() == null || message.getMsgId().isBlank()) {
                message.setMsgId(MSG_TYPE_PACS002 + "-" + System.currentTimeMillis());
            }

            updateOriginalFromPacs002(message);

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing PACS002 : {}", e.getMessage(), e);
            return null;
        }
    }

    private void updateOriginalFromPacs002(SwiftMessage pacs002) {
        String status = pacs002.getGroupStatus();
        if (status == null) return;

        SwiftMessage original = null;

        if (pacs002.getOriginalUetr() != null) {
            original = swiftMessageRepository.findByUetr(pacs002.getOriginalUetr()).orElse(null);
        }

        if (original == null && pacs002.getOriginalMsgId() != null) {
            original = swiftMessageRepository.findByMsgId(pacs002.getOriginalMsgId()).orElse(null);
        }

        if (original != null) {
            original.setStatus(mapPacs002Status(status));
            if ("RJCT".equals(status)) {
                original.setRejectionReason(pacs002.getRejectionReason());
            }
            swiftMessageRepository.save(original);

            if (original.getClientEmail() != null && !original.getClientEmail().isBlank()) {
                if (SwiftMessage.STATUS_ACCEPTED.equals(original.getStatus())) {
                    notificationService.createNotification(
                            original.getClientEmail(),
                            "Transaction acceptee",
                            "Votre transaction a ete acceptee.",
                            "success",
                            original.getUetr()
                    );
                } else if (SwiftMessage.STATUS_REJECTED.equals(original.getStatus())) {
                    notificationService.createNotification(
                            original.getClientEmail(),
                            "Transaction rejetee",
                            "Votre transaction a ete rejetee.",
                            "error",
                            original.getUetr()
                    );
                }
            }
        }
    }

    private String mapPacs002Status(String isoStatus) {
        return switch (isoStatus) {
            case "ACCP" -> SwiftMessage.STATUS_ACCEPTED;
            case "RJCT" -> SwiftMessage.STATUS_REJECTED;
            case "PDNG" -> STATUS_PENDING;
            default -> STATUS_PENDING;
        };
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private String extractIban(Element txInf, String accountTag) {
        Element acct = getFirstChildElement(txInf, accountTag);
        if (acct == null) return null;
        Element id = getFirstChildElement(acct, XML_TAG_ID);
        return id != null ? getChildText(id, XML_TAG_IBAN) : null;
    }

    private String extractBic(Element txInf, String agentTag) {
        Element agent = getFirstChildElement(txInf, agentTag);
        if (agent == null) return null;
        Element fin = getFirstChildElement(agent, TAG_FIN_INSTN_ID);
        return fin != null ? getChildText(fin, TAG_BICFI) : null;
    }

    private Document parseXml(File xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(xmlFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private Element getFirstElement(Document doc, String tagName) {
        var nodes = doc.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private Element getFirstChildElement(Element parent, String tagName) {
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