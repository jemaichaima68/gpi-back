package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftParserService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final BankDirectoryRepository bankDirectoryRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public SwiftMessage parse(File xmlFile) {
        String messageType = detectMessageType(xmlFile);
        log.info("Type détecté : {} pour le fichier {}", messageType, xmlFile.getName());

        return switch (messageType) {
            case "PACS008" -> parsePacs008(xmlFile);
            case "PACS009" -> parsePacs009(xmlFile);
            case "PACS002" -> parsePacs002(xmlFile);
            default -> {
                log.warn("Type de message non supporté : {}", messageType);
                yield null;
            }
        };
    }

    private String detectMessageType(File xmlFile) {
        String name = xmlFile.getName().toLowerCase();
        if (name.contains("pacs.008") || name.contains("pacs008")) return "PACS008";
        if (name.contains("pacs.009") || name.contains("pacs009")) return "PACS009";
        if (name.contains("pacs.002") || name.contains("pacs002")) return "PACS002";
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

    // ==================== PARSING PACS008 ====================

    private SwiftMessage parsePacs008(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            SwiftMessage message = new SwiftMessage();
            message.setMessageType("PACS008");
            message.setFileName(xmlFile.getName());
            message.setStatus("EN_ATTENTE");
            message.setReceivedAt(LocalDateTime.now());

            // 1. GROUP HEADER
            Element grpHdr = getFirstElement(doc, "GrpHdr");
            if (grpHdr != null) {
                message.setMsgId(getChildText(grpHdr, "MsgId"));
                String creDtTm = getChildText(grpHdr, "CreDtTm");
                if (creDtTm != null && !creDtTm.isEmpty()) {
                    try {
                        message.setCreationDateTime(LocalDateTime.parse(creDtTm, DateTimeFormatter.ISO_DATE_TIME));
                    } catch (Exception e) {
                        log.warn("Erreur parsing date: {}", creDtTm);
                    }
                }
            }

            // 2. TRANSACTION INFO
            Element cdtTrf = getFirstElement(doc, "CdtTrfTxInf");
            if (cdtTrf != null) {

                Element pmtId = getFirstChildElement(cdtTrf, "PmtId");
                if (pmtId != null) {
                    String uetr = getChildText(pmtId, "UETR");
                    if (uetr != null && !uetr.isBlank()) {
                        message.setUetr(uetr);
                        log.info("UETR extrait du XML: {}", uetr);
                    }
                    message.setEndToEndId(getChildText(pmtId, "EndToEndId"));
                    message.setInstructionId(getChildText(pmtId, "InstrId"));
                }

                NodeList amtNodes = doc.getElementsByTagName("InstdAmt");
                if (amtNodes.getLength() > 0) {
                    Element amtEl = (Element) amtNodes.item(0);
                    String amtText = amtEl.getTextContent().trim();
                    if (!amtText.isEmpty()) {
                        message.setAmount(new BigDecimal(amtText));
                    }
                    message.setCurrency(amtEl.getAttribute("Ccy"));
                }

                String chargeBearer = getChildText(cdtTrf, "ChrgBr");
                if (chargeBearer != null) message.setChargeBearer(chargeBearer);

                Element dbtr = getFirstChildElement(cdtTrf, "Dbtr");
                String debtorName = null;
                if (dbtr != null) {
                    debtorName = getChildText(dbtr, "Nm");
                    message.setDebtorName(debtorName);
                    log.info("Nom du débiteur extrait: {}", debtorName);

                    Element dbtrAdr = getFirstChildElement(dbtr, "PstlAdr");
                    if (dbtrAdr != null) {
                        message.setDebtorCountry(getChildText(dbtrAdr, "Ctry"));
                        String address = getChildText(dbtrAdr, "AdrLine");
                        if (address != null) message.setDebtorAddress(address);
                    }
                }

                Element dbtrAcct = getFirstChildElement(cdtTrf, "DbtrAcct");
                if (dbtrAcct != null) {
                    Element acctId = getFirstChildElement(dbtrAcct, "Id");
                    if (acctId != null) {
                        String iban = getChildText(acctId, "IBAN");
                        if (iban != null) message.setDebtorIban(iban);
                    }
                }

                Element dbtrAgt = getFirstChildElement(cdtTrf, "DbtrAgt");
                if (dbtrAgt != null) {
                    Element finInstnId = getFirstChildElement(dbtrAgt, "FinInstnId");
                    if (finInstnId != null) {
                        message.setDebtorAgentBic(getChildText(finInstnId, "BICFI"));
                    }
                }

                Element cdtr = getFirstChildElement(cdtTrf, "Cdtr");
                if (cdtr != null) {
                    message.setCreditorName(getChildText(cdtr, "Nm"));
                    Element cdtrAdr = getFirstChildElement(cdtr, "PstlAdr");
                    if (cdtrAdr != null) {
                        message.setCreditorCountry(getChildText(cdtrAdr, "Ctry"));
                        String address = getChildText(cdtrAdr, "AdrLine");
                        if (address != null) message.setCreditorAddress(address);
                    }
                }

                Element cdtrAcct = getFirstChildElement(cdtTrf, "CdtrAcct");
                if (cdtrAcct != null) {
                    Element acctId = getFirstChildElement(cdtrAcct, "Id");
                    if (acctId != null) {
                        String iban = getChildText(acctId, "IBAN");
                        if (iban != null) message.setCreditorIban(iban);
                    }
                }

                Element cdtrAgt = getFirstChildElement(cdtTrf, "CdtrAgt");
                if (cdtrAgt != null) {
                    Element finInstnId = getFirstChildElement(cdtrAgt, "FinInstnId");
                    if (finInstnId != null) {
                        message.setCreditorAgentBic(getChildText(finInstnId, "BICFI"));
                    }
                }

                Element rmtInf = getFirstChildElement(cdtTrf, "RmtInf");
                if (rmtInf != null) {
                    String ustrd = getChildText(rmtInf, "Ustrd");
                    if (ustrd != null) message.setRemittanceInfo(ustrd);
                }

                // ✅ DÉTECTION AUTO DU CLIENT PAR SON NOM
                autoDetectAndAssignClient(message, debtorName);
            }

            autoRegisterBank(message.getDebtorAgentBic());
            autoRegisterBank(message.getCreditorAgentBic());

            log.info("=== PACS008 PARSED ===");
            log.info("MsgId: {}, UETR: {}, Montant: {} {}, ClientEmail: {}",
                    message.getMsgId(), message.getUetr(), message.getAmount(),
                    message.getCurrency(), message.getClientEmail());

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.008 [{}] : {}", xmlFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ✅ MÉTHODE PRINCIPALE : Détection auto du client + Email + Notification
    private void autoDetectAndAssignClient(SwiftMessage message, String debtorName) {
        try {
            if (debtorName == null || debtorName.isBlank()) {
                log.warn("⚠️ Aucun nom débiteur trouvé - impossible d'associer un client");
                return;
            }

            log.info("🔍 Recherche automatique du client par nom: '{}'", debtorName);

            // 1. Chercher l'email dans Keycloak par le nom
            String clientEmail = keycloakAdminService.getEmailByFullName(debtorName);

            if (clientEmail != null) {
                // 2. Associer l'email à la transaction
                message.setClientEmail(clientEmail);
                log.info("✅ Client associé automatiquement: {} -> {}", debtorName, clientEmail);

                // 3. Vérifier si la transaction existe déjà (éviter doublon)
                boolean transactionExists = swiftMessageRepository.existsByUetr(message.getUetr());

                if (!transactionExists) {
                    // 4. Envoyer EMAIL de confirmation au client
                    emailService.sendTransactionReceivedEmail(
                            clientEmail,
                            debtorName,
                            message.getUetr(),
                            message.getAmount(),
                            message.getCurrency()
                    );
                    log.info("📧 Email de réception envoyé à {}", clientEmail);

                    // 5. Envoyer NOTIFICATION dans l'interface client
                    notificationService.createNotification(
                            clientEmail,
                            "💰 Nouvelle transaction reçue",
                            "Votre transaction SWIFT d'un montant de " + message.getAmount() + " " + message.getCurrency() +
                                    " (UETR: " + message.getUetr() + ") a été reçue et est en attente de validation.",
                            "info",
                            message.getUetr()
                    );
                    log.info("🔔 Notification envoyée à {}", clientEmail);
                } else {
                    log.warn("⚠️ Transaction déjà existante pour UETR: {}", message.getUetr());
                }
            } else {
                log.warn("⚠️ Aucun client trouvé dans Keycloak pour le nom: '{}' - Transaction en attente d'affectation manuelle", debtorName);

                // Option: notification à l'admin qu'une transaction est sans client
                notificationService.createNotification(
                        "admin@gpi.com",  // Email admin
                        "⚠️ Transaction sans client",
                        "Une transaction est arrivée mais le client '{}' n'existe pas dans Keycloak",
                        "warning",
                        message.getUetr()
                );
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'association automatique du client: {}", e.getMessage());
        }
    }

    // ==================== PARSING PACS009 ====================

    private SwiftMessage parsePacs009(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            SwiftMessage message = new SwiftMessage();
            message.setMessageType("PACS009");
            message.setFileName(xmlFile.getName());
            message.setStatus("EN_ATTENTE");
            message.setReceivedAt(LocalDateTime.now());

            Element grpHdr = getFirstElement(doc, "GrpHdr");
            if (grpHdr != null) {
                message.setMsgId(getChildText(grpHdr, "MsgId"));
            }

            Element cdtTrfTxInf = getFirstElement(doc, "CdtTrfTxInf");
            if (cdtTrfTxInf != null) {
                Element pmtId = getFirstChildElement(cdtTrfTxInf, "PmtId");
                if (pmtId != null) {
                    message.setEndToEndId(getChildText(pmtId, "EndToEndId"));
                    message.setUetr(getChildText(pmtId, "UETR"));
                }

                Element amt = getFirstChildElement(cdtTrfTxInf, "Amt");
                if (amt != null) {
                    Element instdAmt = getFirstChildElement(amt, "InstdAmt");
                    if (instdAmt != null) {
                        String amtText = instdAmt.getTextContent().trim();
                        if (!amtText.isEmpty()) {
                            message.setAmount(new BigDecimal(amtText));
                        }
                        message.setCurrency(instdAmt.getAttribute("Ccy"));
                    }
                }

                Element dbtr = getFirstChildElement(cdtTrfTxInf, "Dbtr");
                String debtorName = null;
                if (dbtr != null) {
                    debtorName = getChildText(dbtr, "Nm");
                    message.setDebtorName(debtorName);
                }

                Element cdtr = getFirstChildElement(cdtTrfTxInf, "Cdtr");
                if (cdtr != null) {
                    message.setCreditorName(getChildText(cdtr, "Nm"));
                }

                // ✅ Détection auto du client pour PACS009
                autoDetectAndAssignClient(message, debtorName);
            }

            autoRegisterBank(message.getInstructingAgentBic());
            autoRegisterBank(message.getInstructedAgentBic());

            log.info("=== PACS009 PARSED ===");
            log.info("MsgId: {}, UETR: {}", message.getMsgId(), message.getUetr());

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.009: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== PARSING PACS002 ====================

    private SwiftMessage parsePacs002(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            SwiftMessage received = new SwiftMessage();
            received.setMessageType("PACS002");
            received.setFileName(xmlFile.getName());
            received.setStatus("RECEIVED");
            received.setReceivedAt(LocalDateTime.now());

            Element grpHdr = getFirstElement(doc, "GrpHdr");
            if (grpHdr != null) {
                received.setMsgId(getChildText(grpHdr, "MsgId"));
            }

            Element orgnlGrp = getFirstElement(doc, "OrgnlGrpInfAndSts");
            if (orgnlGrp != null) {
                String originalMsgId = getChildText(orgnlGrp, "OrgnlMsgId");
                String groupStatus = getChildText(orgnlGrp, "GrpSts");
                received.setOriginalMsgId(originalMsgId);
                received.setGroupStatus(groupStatus);

                Optional<SwiftMessage> optOriginal = swiftMessageRepository.findFirstByMsgIdOrderByReceivedAtDesc(originalMsgId);
                if (optOriginal.isPresent()) {
                    SwiftMessage original = optOriginal.get();
                    String oldStatus = original.getStatus();
                    String newStatus = mapGroupStatus(groupStatus);
                    original.setStatus(newStatus);
                    swiftMessageRepository.save(original);

                    log.info("Transaction {} mise à jour : {} → {}", originalMsgId, oldStatus, newStatus);

                    // ✅ Notifier le client du changement de statut
                    if (original.getClientEmail() != null && !original.getClientEmail().isBlank()) {
                        String notificationStatus = mapGroupStatusToNotification(groupStatus);
                        notificationService.notifyStatusChange(
                                original.getClientEmail(),
                                original.getDebtorName(),
                                original.getUetr(),
                                notificationStatus,
                                null
                        );
                        log.info("🔔 Notification de changement de statut envoyée à {}", original.getClientEmail());
                    }
                }
            }

            return received;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.002: {}", e.getMessage(), e);
            return null;
        }
    }

    private String mapGroupStatus(String groupStatus) {
        return switch (groupStatus) {
            case "ACCP", "ACSC" -> "ACCEPTE";
            case "RJCT" -> "REJETE";
            case "PDNG" -> "EN_ATTENTE";
            default -> "EN_ATTENTE";
        };
    }

    private String mapGroupStatusToNotification(String groupStatus) {
        return switch (groupStatus) {
            case "ACCP", "ACSC" -> "ACSC";
            case "ACTC" -> "ACTC";
            case "ACSP" -> "ACSP";
            case "RJCT" -> "RJCT";
            default -> "PDNG";
        };
    }

    // ==================== MÉTHODES UTILITAIRES ====================

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

    private void autoRegisterBank(String bic) {
        if (bic == null || bic.isBlank()) return;

        try {
            Optional<BankDirectory> existing = bankDirectoryRepository.findByBicIgnoreCase(bic);
            if (existing.isPresent()) {
                BankDirectory bank = existing.get();
                bank.setOccurrenceCount(bank.getOccurrenceCount() + 1);
                bank.setLastSeenAt(LocalDateTime.now());
                bankDirectoryRepository.save(bank);
                log.debug("Banque déjà existante, compteur incrémenté: {}", bic);
            } else {
                BankDirectory newBank = new BankDirectory();
                newBank.setBic(bic.toUpperCase());
                newBank.setBankName(bic.toUpperCase());
                newBank.setCountryCode("??");
                newBank.setFirstSeenAt(LocalDateTime.now());
                newBank.setLastSeenAt(LocalDateTime.now());
                newBank.setOccurrenceCount(1);
                bankDirectoryRepository.save(newBank);
                log.info("📝 Nouvelle banque auto-enregistrée: {}", bic);
            }
        } catch (Exception e) {
            log.error("Erreur auto-enregistrement banque {}: {}", bic, e.getMessage());
        }
    }
}