package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;

@Service
public class SwiftFileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(SwiftFileWatcherService.class);

    @Autowired
    private SwiftParserService parserService;

    @Autowired
    private FileArchiveService archiveService;

    @Autowired
    private SwiftMessageRepository swiftMessageRepository;

    @Autowired
    private SwiftValidationService validationService;

    @Value("${swift.received.path}")
    private String receivedPath;

    @Value("${swift.emitted.path}")
    private String emittedPath;

    // ==================== SCAN DES MESSAGES ENTRANTS ====================

    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanReceivedMessages() {
        log.info("=== Scan du dossier messages_recus [{}] ===", LocalDateTime.now());

        File folder = new File(receivedPath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.warn("Dossier messages_recus introuvable : {}", receivedPath);
            return;
        }

        File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

        if (xmlFiles == null || xmlFiles.length == 0) {
            log.info("Aucun fichier XML dans messages_recus.");
            return;
        }

        for (File xmlFile : xmlFiles) {
            processReceivedFile(xmlFile);
        }
    }

    private void processReceivedFile(File xmlFile) {
        log.info("Traitement du fichier entrant : {}", xmlFile.getName());

        try {
            if (!validateXmlFile(xmlFile)) {
                log.error("Validation XML échouée : {}", xmlFile.getName());
                return;
            }

            SwiftMessage message = parserService.parse(xmlFile);
            if (message == null) {
                log.error("Parsing échoué : {}", xmlFile.getName());
                return;
            }

            // CONTRÔLE DOUBLON
            if (swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                log.warn("Message entrant déjà traité : {}. Archivage direct.", message.getMsgId());

                boolean archived = archiveService.archiveReceivedFile(xmlFile.getName());
                if (!archived) {
                    log.error("Impossible d'archiver le doublon entrant : {}", xmlFile.getName());
                }
                return;
            }

            message.setDirection("IN");
            message.setStatus("EN_ATTENTE");
            message.setReceivedAt(LocalDateTime.now());
            message.setFileName(xmlFile.getName());

            boolean isPaymentMessage =
                    "PACS008".equals(message.getMessageType()) ||
                            "PACS009".equals(message.getMessageType());

            if (isPaymentMessage) {
                SwiftValidationService.EvaluationResult eval =
                        validationService.evaluerTransaction(message);

                message.setAlerte(eval.getAlerte());
                message.setMotifAlerte(eval.getMotif());
            }

            swiftMessageRepository.save(message);

            log.info("Message entrant sauvegardé : MsgId={}, Type={}, Statut={}",
                    message.getMsgId(),
                    message.getMessageType(),
                    message.getStatus());

            boolean archived = archiveService.archiveReceivedFile(xmlFile.getName());

            if (archived) {
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);
                log.info("Message entrant archivé : {}", message.getMsgId());
            } else {
                log.error("Message entrant sauvegardé mais non archivé : {}", xmlFile.getName());
            }

        } catch (Exception e) {
            log.error("Erreur lors du traitement entrant {} : {}",
                    xmlFile.getName(),
                    e.getMessage(),
                    e);
        }
    }

    // ==================== SCAN DES MESSAGES SORTANTS ====================

    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanEmittedMessages() {
        log.info("=== Scan du dossier messages_emis [{}] ===", LocalDateTime.now());

        File folder = new File(emittedPath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.warn("Dossier messages_emis introuvable : {}", emittedPath);
            return;
        }

        File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

        if (xmlFiles == null || xmlFiles.length == 0) {
            log.info("Aucun fichier XML dans messages_emis.");
            return;
        }

        for (File xmlFile : xmlFiles) {
            processEmittedFile(xmlFile);
        }
    }

    private void processEmittedFile(File xmlFile) {
        log.info("Traitement du fichier sortant : {}", xmlFile.getName());

        try {
            if (!validateXmlFile(xmlFile)) {
                log.error("Validation XML échouée : {}", xmlFile.getName());
                return;
            }

            SwiftMessage message = parserService.parse(xmlFile);
            if (message == null) {
                log.error("Parsing échoué : {}", xmlFile.getName());
                return;
            }

            // CONTRÔLE DOUBLON
            if (swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                log.warn("Message sortant déjà traité : {}. Archivage direct.", message.getMsgId());

                boolean archived = archiveService.archiveEmittedFile(xmlFile.getName());
                if (!archived) {
                    log.error("Impossible d'archiver le doublon sortant : {}", xmlFile.getName());
                }
                return;
            }

            message.setDirection("OUT");
            message.setStatus("ENVOYE");
            message.setReceivedAt(LocalDateTime.now());
            message.setFileName(xmlFile.getName());

            swiftMessageRepository.save(message);

            log.info("Message sortant sauvegardé : MsgId={}, Type={}",
                    message.getMsgId(),
                    message.getMessageType());

            boolean archived = archiveService.archiveEmittedFile(xmlFile.getName());

            if (archived) {
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);
                log.info("Message sortant archivé : {}", message.getMsgId());
            } else {
                log.error("Message sortant sauvegardé mais non archivé : {}", xmlFile.getName());
            }

        } catch (Exception e) {
            log.error("Erreur lors du traitement sortant {} : {}",
                    xmlFile.getName(),
                    e.getMessage(),
                    e);
        }
    }

    // ==================== VALIDATION XML ====================

    private boolean validateXmlFile(File xmlFile) {
        try {
            if (!xmlFile.exists()) {
                log.error("Fichier inexistant : {}", xmlFile.getName());
                return false;
            }

            if (xmlFile.length() == 0) {
                log.error("Fichier vide : {}", xmlFile.getName());
                return false;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            String fileName = xmlFile.getName().toLowerCase();
            String messageType = detectTypeByFileName(fileName);

            log.info("Type détecté pour {} : {}", xmlFile.getName(), messageType);

            return validateByType(doc, messageType, xmlFile.getName());

        } catch (Exception e) {
            log.error("XML invalide : {} - {}", xmlFile.getName(), e.getMessage());
            return false;
        }
    }

    private String detectTypeByFileName(String fileName) {
        if (fileName.contains("pacs.008") || fileName.contains("pacs008")) {
            return "PACS008";
        }

        if (fileName.contains("pacs.009") || fileName.contains("pacs009")) {
            return "PACS009";
        }

        if (fileName.contains("pacs.002") || fileName.contains("pacs002")) {
            return "PACS002";
        }

        return "UNKNOWN";
    }

    private boolean validateByType(Document doc, String type, String fileName) {
        switch (type) {
            case "PACS008":
                return validatePacs008(doc, fileName);

            case "PACS009":
                return validatePacs009(doc, fileName);

            case "PACS002":
                return validatePacs002(doc, fileName);

            default:
                log.error("Type non supporté : {}", type);
                return false;
        }
    }

    private boolean validatePacs008(Document doc, String fileName) {
        boolean hasMsgId = doc.getElementsByTagName("MsgId").getLength() > 0;
        boolean hasCreDtTm = doc.getElementsByTagName("CreDtTm").getLength() > 0;
        boolean hasInstdAmt = doc.getElementsByTagName("InstdAmt").getLength() > 0;

        if (!hasMsgId) log.error("PACS008 sans MsgId : {}", fileName);
        if (!hasCreDtTm) log.error("PACS008 sans CreDtTm : {}", fileName);
        if (!hasInstdAmt) log.error("PACS008 sans InstdAmt : {}", fileName);

        return hasMsgId && hasCreDtTm && hasInstdAmt;
    }

    private boolean validatePacs009(Document doc, String fileName) {
        boolean hasMsgId = doc.getElementsByTagName("MsgId").getLength() > 0;
        boolean hasCreDtTm = doc.getElementsByTagName("CreDtTm").getLength() > 0;
        boolean hasInstdAmt = doc.getElementsByTagName("InstdAmt").getLength() > 0;

        if (!hasMsgId) log.error("PACS009 sans MsgId : {}", fileName);
        if (!hasCreDtTm) log.error("PACS009 sans CreDtTm : {}", fileName);
        if (!hasInstdAmt) log.error("PACS009 sans InstdAmt : {}", fileName);

        return hasMsgId && hasCreDtTm && hasInstdAmt;
    }

    private boolean validatePacs002(Document doc, String fileName) {
        boolean hasMsgId = doc.getElementsByTagName("MsgId").getLength() > 0;
        boolean hasOrgnlMsgId = doc.getElementsByTagName("OrgnlMsgId").getLength() > 0;
        boolean hasGrpSts = doc.getElementsByTagName("GrpSts").getLength() > 0;

        if (!hasMsgId) log.error("PACS002 sans MsgId : {}", fileName);
        if (!hasOrgnlMsgId) log.error("PACS002 sans OrgnlMsgId : {}", fileName);
        if (!hasGrpSts) log.error("PACS002 sans GrpSts : {}", fileName);

        return hasMsgId && hasOrgnlMsgId && hasGrpSts;
    }
}