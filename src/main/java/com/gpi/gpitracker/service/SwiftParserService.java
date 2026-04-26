// Déclaration du package
package com.gpi.gpitracker.service;

// Imports nécessaires
import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.Optional;

/**
 * SERVICE DE PARSING DES MESSAGES SWIFT
 *
 * Rôle : Transformer un fichier XML brut en objet Java SwiftMessage
 *
 * Exemple : fichier XML → objet SwiftMessage utilisable par l'application
 */
@Service  // Indique à Spring que c'est un service (géré automatiquement)
@RequiredArgsConstructor  // Lombok : génère automatiquement le constructeur avec tous les champs final
public class SwiftParserService {

    // Logger pour tracer l'exécution
    private static final Logger log = LoggerFactory.getLogger(SwiftParserService.class);

    // Repository pour accéder à la base de données (nécessaire pour les messages PACS002)
    private final SwiftMessageRepository swiftMessageRepository;

    // Repository pour l'annuaire des banques (auto-enregistrement)
    private final BankDirectoryRepository bankDirectoryRepository;

    /**
     * MÉTHODE PRINCIPALE - Point d'entrée pour parser un fichier
     *
     * @param xmlFile Le fichier XML à analyser
     * @return Un objet SwiftMessage rempli, ou null si erreur
     */
    public SwiftMessage parse(File xmlFile) {
        // ÉTAPE 1 : Détecter le type de message (PACS008, PACS009, PACS002)
        String messageType = detectMessageType(xmlFile);
        log.info("Type détecté : {} pour le fichier {}", messageType, xmlFile.getName());

        // ÉTAPE 2 : Appeler la méthode de parsing spécifique selon le type
        return switch (messageType) {
            case "PACS008" -> parsePacs008(xmlFile);  // Paiement client
            case "PACS009" -> parsePacs009(xmlFile);  // Transfert interbancaire
            case "PACS002" -> parsePacs002(xmlFile);  // Message de réponse/statut
            default -> {
                // Type non supporté
                log.warn("Type de message non supporté : {}", messageType);
                yield null;
            }
        };
    }

    /**
     * DÉTECTION DU TYPE DE MESSAGE
     *
     * Deux méthodes : par le nom du fichier ou par le contenu XML
     *
     * @param xmlFile Le fichier à analyser
     * @return Le type détecté (PACS008, PACS009, PACS002, UNKNOWN)
     */
    private String detectMessageType(File xmlFile) {
        // MÉTHODE 1 : Par le nom du fichier (plus rapide)
        String name = xmlFile.getName().toLowerCase();  // Convertir en minuscules pour comparaison
        if (name.contains("pacs.008") || name.contains("pacs008")) return "PACS008";
        if (name.contains("pacs.009") || name.contains("pacs009")) return "PACS009";
        if (name.contains("pacs.002") || name.contains("pacs002")) return "PACS002";

        // MÉTHODE 2 : Par la racine XML (plus fiable, utilisé si le nom ne suffit pas)
        return detectFromXmlRoot(xmlFile);
    }

    /**
     * DÉTECTION PAR LA RACINE XML
     *
     * Lit la première balise du fichier XML pour identifier le type
     *
     * @param xmlFile Le fichier XML
     * @return Type détecté par la racine XML
     */
    private String detectFromXmlRoot(File xmlFile) {
        try {
            // Créer une fabrique de parseurs XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);  // Ignorer les namespaces XML (simplifie le parsing)

            // Créer le parseur et analyser le fichier
            Document doc = factory.newDocumentBuilder().parse(xmlFile);

            // Récupérer le nom de la balise racine (la première balise du fichier)
            String rootName = doc.getDocumentElement().getTagName();

            // Vérifier quel type de message contient la racine
            if (rootName.contains("pacs.008")) return "PACS008";
            if (rootName.contains("pacs.009")) return "PACS009";
            if (rootName.contains("pacs.002")) return "PACS002";

        } catch (Exception e) {
            // Si erreur de lecture, logguer et continuer
            log.error("Impossible de lire la racine XML de {} : {}", xmlFile.getName(), e.getMessage());
        }
        return "UNKNOWN";  // Type non reconnu
    }

    // ==================== PARSING PACS008 (PAIEMENT CLIENT) ====================

    /**
     * PARSE UN MESSAGE PACS008
     *
     * PACS008 = Paiement client (ex: un particulier paie un commerçant)
     *
     * Extrait les informations principales :
     * - Identifiants (MsgId, UETR)
     * - Montant et devise
     * - Nom du débiteur (client qui paie)
     * - Nom du créditeur (bénéficiaire)
     *
     * @param xmlFile Le fichier XML à parser
     * @return SwiftMessage rempli avec les données du fichier
     */
    private SwiftMessage parsePacs008(File xmlFile) {
        try {
            // CRÉATION DU PARSEUR XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);  // Ignorer les namespaces (simplifie)
            DocumentBuilder builder = factory.newDocumentBuilder();

            // ANALYSE DU FICHIER
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();  // Normalise la structure XML

            // CRÉATION DE L'OBJET À REMPLIR
            SwiftMessage message = new SwiftMessage();
            message.setMessageType("PACS008");  // Définir le type

            // 1. EXTRAIRE LE GROUP HEADER (En-tête du groupe)
            // Cherche la balise <GrpHdr> (Group Header)
            Element grpHdr = getFirstElement(doc, "GrpHdr");

            // Extraire <MsgId> (Message ID) - Identifiant unique du message
            message.setMsgId(getChildText(grpHdr, "MsgId"));

            // Extraire <CreDtTm> (Creation Date Time) - Date de création du message
            String creDtTm = getChildText(grpHdr, "CreDtTm");
            if (creDtTm != null && !creDtTm.isEmpty()) {
                // Convertir la chaîne de date en objet LocalDateTime Java
                message.setCreationDateTime(
                        LocalDateTime.parse(creDtTm, DateTimeFormatter.ISO_DATE_TIME)
                );
            }

            // 2. EXTRAIRE LES INFOS DE TRANSACTION
            // Cherche la balise <CdtTrfTxInf> (Credit Transfer Transaction Info)
            Element cdtTrf = getFirstElement(doc, "CdtTrfTxInf");

            // 2.1 Payment ID (Identifiants du paiement)
            Element pmtId = getFirstChildElement(cdtTrf, "PmtId");
            if (pmtId != null) {
                // UETR = Unique End-to-end Transaction Reference (référence unique)
                message.setUetr(getChildText(pmtId, "UETR"));
            }

            // 2.2 MONTANT ET DEVISE
            // Cherche la balise <InstdAmt> (Instructed Amount)
            NodeList amtNodes = doc.getElementsByTagName("InstdAmt");
            if (amtNodes.getLength() > 0) {
                Element amtEl = (Element) amtNodes.item(0);
                String amtText = amtEl.getTextContent().trim();
                if (!amtText.isEmpty()) {
                    // Convertir le texte en nombre BigDecimal (précision pour argent)
                    message.setAmount(new BigDecimal(amtText));
                }
                // Récupérer l'attribut "Ccy" (Currency) comme "EUR", "USD", etc.
                message.setCurrency(amtEl.getAttribute("Ccy"));
            }

            // 2.3 DÉBITEUR (celui qui paie)
            Element dbtr = getFirstChildElement(cdtTrf, "Dbtr");
            if (dbtr != null) {
                // Nom du débiteur
                message.setDebtorName(getChildText(dbtr, "Nm"));

                // Adresse du débiteur (pour extraire le pays)
                Element adr = getFirstChildElement(dbtr, "PstlAdr");
                if (adr != null) {
                    message.setDebtorCountry(getChildText(adr, "Ctry"));
                }
            }

            // 2.4 CRÉDITEUR (bénéficiaire)
            Element cdtr = getFirstChildElement(cdtTrf, "Cdtr");
            if (cdtr != null) {
                // Nom du créditeur
                message.setCreditorName(getChildText(cdtr, "Nm"));

                // Adresse du créditeur (pour extraire le pays)
                Element adr = getFirstChildElement(cdtr, "PstlAdr");
                if (adr != null) {
                    message.setCreditorCountry(getChildText(adr, "Ctry"));
                }
            }

            // 3. MÉTADONNÉES SYSTÈME
            message.setFileName(xmlFile.getName());          // Nom du fichier source
            message.setStatus("EN_ATTENTE");                 // Statut initial
            message.setReceivedAt(LocalDateTime.now());      // Date de réception

            // 4. AUTO-ENREGISTREMENT DES BANQUES (PACS008)
            autoRegisterBank(message.getDebtorAgentBic());
            autoRegisterBank(message.getCreditorAgentBic());

            // LOG DE SUCCÈS
            log.info("Parsing PACS008 OK — MsgId: {} | UETR: {} | {} {} | {} → {}",
                    message.getMsgId(), message.getUetr(),
                    message.getAmount(), message.getCurrency(),
                    message.getDebtorName(), message.getCreditorName());

            return message;

        } catch (Exception e) {
            // EN CAS D'ERREUR : logguer et retourner null
            log.error("Erreur parsing pacs.008 [{}] : {}", xmlFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ==================== PARSING PACS009 (TRANSFERT INTERBANCAIRE) ====================

    /**
     * PARSE UN MESSAGE PACS009
     *
     * PACS009 = Transfert interbancaire (ex: une banque transfère à une autre banque)
     *
     * Différences avec PACS008 :
     * - Pas de client final, ce sont des banques qui s'échangent de l'argent
     * - Présence de codes BIC (identifiants des banques)
     * - Peut utiliser des comptes nostro/vostro (comptes entre banques)
     *
     * @param xmlFile Le fichier XML à parser
     * @return SwiftMessage rempli avec les données du fichier
     */
    private SwiftMessage parsePacs009(File xmlFile) {
        try {
            // PRÉPARATION DU PARSEUR XML (identique à PACS008)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // CRÉATION DE L'OBJET
            SwiftMessage message = new SwiftMessage();
            message.setMessageType("PACS009");

            // ===== 1. GROUP HEADER (En-tête) =====
            Element grpHdr = getFirstElement(doc, "GrpHdr");

            // Identifiant unique du message
            message.setMsgId(getChildText(grpHdr, "MsgId"));

            // Date de création
            String creDtTm = getChildText(grpHdr, "CreDtTm");
            if (creDtTm != null && !creDtTm.isEmpty()) {
                message.setCreationDateTime(
                        LocalDateTime.parse(creDtTm, DateTimeFormatter.ISO_DATE_TIME)
                );
            }

            // Nombre de transactions dans le lot
            String nbOfTxs = getChildText(grpHdr, "NbOfTxs");
            if (nbOfTxs != null) {
                try {
                    message.setNbOfTransactions(Integer.parseInt(nbOfTxs));
                } catch (NumberFormatException e) {
                    log.warn("NbOfTxs invalide : {}", nbOfTxs);
                }
            }

            // Date de règlement (settlement)
            Element sttlmInf = getFirstChildElement(grpHdr, "SttlmInf");
            if (sttlmInf != null) {
                message.setSettlementDate(getChildText(sttlmInf, "SttlmDt"));
            }

            // ===== 2. DÉTAILS DU TRANSFERT =====
            Element cdtTrfTxInf = getFirstElement(doc, "CdtTrfTxInf");

            // 2.1 IDENTIFIANTS
            Element pmtId = getFirstChildElement(cdtTrfTxInf, "PmtId");
            if (pmtId != null) {
                message.setEndToEndId(getChildText(pmtId, "EndToEndId"));  // Référence de bout en bout
                message.setUetr(getChildText(pmtId, "UETR"));               // Référence unique
            }

            // 2.2 MONTANT
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

            // 2.3 QUI SUPPORTE LES FRAIS ?
            // ChrgBr = Charge Bearer (SLEV = Service Level, DEBT = Débiteur, CRED = Créditeur, SHAR = Partagé)
            message.setChargeBearer(getChildText(cdtTrfTxInf, "ChrgBr"));

            // 2.4 BANQUE DONNEUSE D'ORDRE (Instructing Agent)
            // C'est la banque qui INITIE le transfert
            Element instgAgt = getFirstChildElement(cdtTrfTxInf, "InstgAgt");
            if (instgAgt != null) {
                Element finInstnId = getFirstChildElement(instgAgt, "FinInstnId");
                if (finInstnId != null) {
                    // BICFI = Bank Identifier Code (Financial Institution)
                    message.setInstructingAgentBic(getChildText(finInstnId, "BICFI"));
                }
            }

            // 2.5 BANQUE BÉNÉFICIAIRE (Instructed Agent)
            // C'est la banque qui REÇOIT le transfert
            Element instdAgt = getFirstChildElement(cdtTrfTxInf, "InstdAgt");
            if (instdAgt != null) {
                Element finInstnId = getFirstChildElement(instdAgt, "FinInstnId");
                if (finInstnId != null) {
                    message.setInstructedAgentBic(getChildText(finInstnId, "BICFI"));
                }
            }

            // 2.6 COMPTE DÉBITEUR (compte de la banque donneuse d'ordre)
            Element dbtrAcct = getFirstChildElement(cdtTrfTxInf, "DbtrAcct");
            if (dbtrAcct != null) {
                Element id = getFirstChildElement(dbtrAcct, "Id");
                if (id != null) {
                    // Essayer d'extraire l'IBAN
                    String iban = getChildText(id, "IBAN");
                    if (iban != null) {
                        message.setDebtorIban(iban);
                    } else {
                        // Si pas d'IBAN, prendre autre identifiant
                        String othr = getChildText(id, "Othr");
                        if (othr != null) message.setDebtorIban(othr);
                    }
                }
            }

            // 2.7 COMPTE CRÉDITEUR (compte de la banque bénéficiaire)
            Element cdtrAcct = getFirstChildElement(cdtTrfTxInf, "CdtrAcct");
            if (cdtrAcct != null) {
                Element id = getFirstChildElement(cdtrAcct, "Id");
                if (id != null) {
                    String iban = getChildText(id, "IBAN");
                    if (iban != null) {
                        message.setCreditorIban(iban);
                    } else {
                        String othr = getChildText(id, "Othr");
                        if (othr != null) message.setCreditorIban(othr);
                    }
                }
            }

            // 2.8 INFORMATIONS SUPPLÉMENTAIRES (motif du paiement)
            message.setRemittanceInfo(getChildText(cdtTrfTxInf, "RmtInf"));

            // ===== 3. MÉTADONNÉES SYSTÈME =====
            message.setFileName(xmlFile.getName());
            message.setStatus("EN_ATTENTE");
            message.setReceivedAt(LocalDateTime.now());

            // 4. AUTO-ENREGISTREMENT DES BANQUES (PACS009)
            autoRegisterBank(message.getInstructingAgentBic());
            autoRegisterBank(message.getInstructedAgentBic());

            // LOG DE SUCCÈS
            log.info("Parsing PACS009 OK — MsgId: {} | UETR: {} | {} {} | Instg: {} → Instd: {}",
                    message.getMsgId(), message.getUetr(),
                    message.getAmount(), message.getCurrency(),
                    message.getInstructingAgentBic(), message.getInstructedAgentBic());

            return message;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.009 [{}] : {}", xmlFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ==================== PARSING PACS002 (MESSAGE DE RÉPONSE) ====================

    /**
     * PARSE UN MESSAGE PACS002
     *
     * PACS002 = Message de statut/réponse
     *
     * Ce message est envoyé EN RÉPONSE à un PACS008 ou PACS009
     * Il indique si la transaction a été acceptée, rejetée, ou est en attente
     *
     * IMPORTANT : Ce parser ne crée pas une nouvelle transaction, mais
     * MET À JOUR le statut de la transaction originale !
     *
     * @param xmlFile Le fichier XML à parser
     * @return SwiftMessage contenant les infos du PACS002 (pour traçabilité)
     */
    private SwiftMessage parsePacs002(File xmlFile) {
        try {
            // PRÉPARATION DU PARSEUR (avec namespace awareness cette fois)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);  // Important pour PACS002
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // 1. EXTRAIRE LES INFOS DE L'EN-TÊTE
            Element grpHdr = getFirstElement(doc, "GrpHdr");
            String msgId = getChildText(grpHdr, "MsgId");  // ID de ce message PACS002

            // 2. EXTRAIRE LES INFOS SUR LE MESSAGE ORIGINAL
            Element orgnlGrp = getFirstElement(doc, "OrgnlGrpInfAndSts");
            String originalMsgId = getChildText(orgnlGrp, "OrgnlMsgId");  // ID du message original
            String groupStatus = getChildText(orgnlGrp, "GrpSts");        // Statut : ACCP, RJCT, PDNG...

            // 3. EXTRAIRE LE MOTIF DE REJET (si présent)
            String reason = null;
            Element rsnInf = getFirstChildElement(orgnlGrp, "StsRsnInf");
            if (rsnInf != null) {
                Element rsn = getFirstChildElement(rsnInf, "Rsn");
                if (rsn != null) {
                    reason = getChildText(rsn, "Prtry");  // Motif personnalisé
                }
            }

            // 4. METTRE À JOUR LA TRANSACTION ORIGINALE DANS LA BASE DE DONNÉES
            Optional<SwiftMessage> optOriginal = swiftMessageRepository.findByMsgId(originalMsgId);
            if (optOriginal.isPresent()) {
                SwiftMessage original = optOriginal.get();

                // Convertir le statut SWIFT en statut métier
                switch (groupStatus) {
                    case "ACCP" -> original.setStatus("ACCEPTE");   // Accepté
                    case "ACTC" -> original.setStatus("ACTC");      // Accepté techniquement
                    case "ACSP" -> original.setStatus("ACSP");      // En cours de règlement
                    case "RJCT" -> original.setStatus("REJETE");    // Rejeté
                    case "PDNG" -> original.setStatus("EN_ATTENTE"); // En attente
                    default -> original.setStatus("EN_ATTENTE");
                }

                // Si rejeté, enregistrer le motif
                if (reason != null && "RJCT".equals(groupStatus)) {
                    original.setRejectionReason(reason);
                }

                // Sauvegarder la mise à jour
                swiftMessageRepository.save(original);
                log.info("Transaction {} mise à jour : statut = {} (via pacs.002 reçu)",
                        originalMsgId, original.getStatus());
            } else {
                log.warn("Pacs.002 reçu pour une transaction inconnue : {}", originalMsgId);
            }

            // 5. CRÉER UN ENREGISTREMENT DU PACS002 REÇU (pour traçabilité)
            SwiftMessage received = new SwiftMessage();
            received.setMessageType("PACS002");
            received.setMsgId(msgId);
            received.setGroupStatus(groupStatus);
            received.setStatus("RECEIVED");
            received.setReceivedAt(LocalDateTime.now());
            received.setFileName(xmlFile.getName());
            if (originalMsgId != null) {
                received.setOriginalMsgId(originalMsgId);  // Lien vers le message original
            }

            return received;

        } catch (Exception e) {
            log.error("Erreur parsing pacs.002 : {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== MÉTHODES UTILITAIRES POUR LE PARSING XML ====================

    /**
     * RÉCUPÈRE LE PREMIER ÉLÉMENT D'UN CERTAIN NOM DANS LE DOCUMENT
     *
     * @param doc Le document XML
     * @param tagName Le nom de la balise à chercher
     * @return Le premier élément trouvé, ou null si aucun
     */
    private Element getFirstElement(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    /**
     * RÉCUPÈRE LE PREMIER ÉLÉMENT ENFANT D'UN ÉLÉMENT PARENT
     *
     * @param parent L'élément parent
     * @param tagName Le nom de la balise enfant à chercher
     * @return Le premier élément enfant trouvé, ou null
     */
    private Element getFirstChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    /**
     * RÉCUPÈRE LE TEXTE D'UN ÉLÉMENT ENFANT
     *
     * Utile pour extraire des valeurs comme <MsgId>VALEUR</MsgId>
     *
     * @param parent L'élément parent
     * @param tagName Le nom de la balise contenant le texte
     * @return Le texte contenu dans la balise, ou null
     */
    private String getChildText(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? list.item(0).getTextContent().trim() : null;
    }

    // ==================== AUTO-ENREGISTREMENT DES BANQUES ====================

    /**
     * Auto-enregistrement silencieux d'une banque
     * Si le BIC existe déjà, on met à jour la date et le compteur
     * Si le BIC n'existe pas, on l'ajoute
     *
     * @param bic Le BIC de la banque à enregistrer
     */
    private void autoRegisterBank(String bic) {
        if (bic == null || bic.isBlank()) return;

        try {
            Optional<BankDirectory> existing = bankDirectoryRepository.findByBicIgnoreCase(bic);

            if (existing.isPresent()) {
                // Mise à jour : incrémenter le compteur et la date de dernière vue
                BankDirectory bank = existing.get();
                bank.setOccurrenceCount(bank.getOccurrenceCount() + 1);
                bank.setLastSeenAt(LocalDateTime.now());
                bankDirectoryRepository.save(bank);
                log.debug("Banque déjà existante, compteur incrémenté: {}", bic);
            } else {
                // Nouvelle banque - ajout automatique avec informations minimales
                BankDirectory newBank = new BankDirectory();
                newBank.setBic(bic.toUpperCase());
                newBank.setBankName(bic.toUpperCase()); // Nom temporaire = BIC
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