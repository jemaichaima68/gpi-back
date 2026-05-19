package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftFileWatcherService {

    private final SwiftParserService parserService;
    private final FileArchiveService archiveService;
    private final SwiftMessageRepository swiftMessageRepository;
    private final SwiftValidationService validationService;
    private final XmlValidationService xmlValidationService;  // NOUVEAU

    @Value("${swift.received.path}")
    private String receivedPath;

    @Value("${swift.emitted.path}")
    private String emittedPath;

    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanReceivedMessages() {
        scanFolder(receivedPath, true);
    }

    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanEmittedMessages() {
        scanFolder(emittedPath, false);
    }

    private void scanFolder(String path, boolean received) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            log.warn("Dossier introuvable : {}", path);
            return;
        }

        File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) return;

        for (File file : xmlFiles) {
            if (received) processReceivedFile(file);
            else processEmittedFile(file);
        }
    }

    private void processReceivedFile(File xmlFile) {
        try {
            // Détecter d'abord le type pour validation XSD
            String detectedType = detectMessageTypeQuick(xmlFile);

            // Valider selon le type détecté
            if (!validateXmlFileWithXsd(xmlFile, detectedType)) {
                log.error("Validation XSD échouée pour {}", xmlFile.getName());
                archiveService.archiveReceivedFile(xmlFile.getName());
                return;
            }

            SwiftMessage message = parserService.parse(xmlFile);
            if (message == null) return;

            if (message.getMsgId() != null && swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                archiveService.archiveReceivedFile(xmlFile.getName());
                return;
            }

            message.setDirection("IN");
            if ("PACS008".equals(message.getMessageType()) || "PACS009".equals(message.getMessageType())) {
                message.setStatus("PDNG");

                SwiftValidationService.EvaluationResult eval =
                        validationService.evaluerTransaction(message);
                message.setAlerte(eval.getAlerte());
                message.setMotifAlerte(eval.getMotif());
            }

            message.setReceivedAt(LocalDateTime.now());
            message.setFileName(xmlFile.getName());
            swiftMessageRepository.save(message);

            if (archiveService.archiveReceivedFile(xmlFile.getName())) {
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);
            }

        } catch (Exception e) {
            log.error("Erreur traitement entrant {} : {}", xmlFile.getName(), e.getMessage(), e);
        }
    }

    private void processEmittedFile(File xmlFile) {
        try {
            // Détecter d'abord le type pour validation XSD
            String detectedType = detectMessageTypeQuick(xmlFile);

            // Valider selon le type détecté
            if (!validateXmlFileWithXsd(xmlFile, detectedType)) {
                log.error("Validation XSD échouée pour {}", xmlFile.getName());
                archiveService.archiveEmittedFile(xmlFile.getName());
                return;
            }

            SwiftMessage message = parserService.parse(xmlFile);
            if (message == null) return;

            if (message.getMsgId() != null && swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                archiveService.archiveEmittedFile(xmlFile.getName());
                return;
            }

            message.setDirection("OUT");
            message.setStatus("ENVOYE");
            message.setReceivedAt(LocalDateTime.now());
            message.setFileName(xmlFile.getName());
            swiftMessageRepository.save(message);

            if (archiveService.archiveEmittedFile(xmlFile.getName())) {
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);
            }

        } catch (Exception e) {
            log.error("Erreur traitement sortant {} : {}", xmlFile.getName(), e.getMessage(), e);
        }
    }

    /**
     * Détection rapide du type de message (par nom de fichier)
     */
    private String detectMessageTypeQuick(File xmlFile) {
        String fileName = xmlFile.getName().toLowerCase();

        if (fileName.contains("pacs.002") || fileName.contains("pacs002") || fileName.contains("002")) {
            return XmlValidationService.TYPE_PACS002;
        }
        if (fileName.contains("camt.056") || fileName.contains("camt056") || fileName.contains("056")) {
            return XmlValidationService.TYPE_CAMT056;
        }
        if (fileName.contains("camt.029") || fileName.contains("camt029") || fileName.contains("029")) {
            return XmlValidationService.TYPE_CAMT029;
        }
        if (fileName.contains("pacs.008") || fileName.contains("pacs008") || fileName.contains("008")) {
            return "PACS008";
        }
        if (fileName.contains("pacs.009") || fileName.contains("pacs009") || fileName.contains("009")) {
            return "PACS009";
        }

        return "UNKNOWN";
    }

    /**
     * Valide un fichier XML avec le XSD approprié selon son type
     */
    private boolean validateXmlFileWithXsd(File xmlFile, String messageType) {
        try {
            if (!xmlFile.exists() || xmlFile.length() == 0) {
                log.error("Fichier vide ou inexistant: {}", xmlFile.getName());
                return false;
            }

            // Utiliser le service de validation XSD
            boolean isValid = xmlValidationService.validateXmlFile(xmlFile, messageType);

            if (!isValid) {
                log.warn("Fichier XML invalide: {} (type: {})", xmlFile.getName(), messageType);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Erreur validation XML {} : {}", xmlFile.getName(), e.getMessage());
            return false;
        }
    }

    // Méthode conservée pour compatibilité (utilisée par d'autres classes)
    private boolean validateXmlFile(File xmlFile) {
        String type = detectMessageTypeQuick(xmlFile);
        return validateXmlFileWithXsd(xmlFile, type);
    }

    private String detectType(String fileName, Document doc) {
        if (fileName.contains("008")) return "PACS008";
        if (fileName.contains("009")) return "PACS009";
        if (fileName.contains("002")) return "PACS002";
        if (fileName.contains("056")) return "CAMT056";
        if (fileName.contains("029")) return "CAMT029";

        if (doc != null) {
            String namespace = doc.getDocumentElement().getNamespaceURI();
            if (namespace == null) namespace = "";

            if (namespace.contains("pacs.008")) return "PACS008";
            if (namespace.contains("pacs.009")) return "PACS009";
            if (namespace.contains("pacs.002")) return "PACS002";
            if (namespace.contains("camt.056")) return "CAMT056";
            if (namespace.contains("camt.029")) return "CAMT029";
        }

        return "UNKNOWN";
    }

    private boolean validateByType(Document doc, String type, String fileName) {
        return switch (type) {
            case "PACS008", "PACS009" -> has(doc, "MsgId", fileName) && has(doc, "CreDtTm", fileName) &&
                    (doc.getElementsByTagName("InstdAmt").getLength() > 0 ||
                            doc.getElementsByTagName("IntrBkSttlmAmt").getLength() > 0);
            case "PACS002" -> has(doc, "MsgId", fileName) &&
                    (doc.getElementsByTagName("GrpSts").getLength() > 0 ||
                            doc.getElementsByTagName("TxSts").getLength() > 0);
            case "CAMT056" -> has(doc, "Assgnmt", fileName) && has(doc, "OrgnlUETR", fileName);
            case "CAMT029" -> has(doc, "Assgnmt", fileName) && has(doc, "OrgnlUETR", fileName) &&
                    (doc.getElementsByTagName("Conf").getLength() > 0 ||
                            doc.getElementsByTagName("TxCxlSts").getLength() > 0);
            default -> {
                log.error("Type non supporté : {}", type);
                yield false;
            }
        };
    }

    private boolean has(Document doc, String tag, String fileName) {
        boolean ok = doc.getElementsByTagName(tag).getLength() > 0;
        if (!ok) log.error("{} sans {}.", fileName, tag);
        return ok;
    }
}