package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import org.w3c.dom.Document;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiftFileWatcherService {

    private final SwiftParserService parserService;
    private final FileArchiveService archiveService;
    private final SwiftMessageRepository swiftMessageRepository;
    private final SwiftValidationService validationService;
    private final XmlValidationService xmlValidationService;

    // ==================== CONSTANTES ====================
    private static final String MSG_TYPE_PACS008 = "PACS008";
    private static final String MSG_TYPE_PACS009 = "PACS009";
    private static final String MSG_TYPE_PACS002 = "PACS002";
    private static final String MSG_TYPE_CAMT056 = "CAMT056";
    private static final String MSG_TYPE_CAMT029 = "CAMT029";
    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";
    private static final String STATUS_PENDING = "PDNG";
    private static final String STATUS_SENT = "ENVOYE";

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
            String detectedType = detectMessageTypeQuick(xmlFile);

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

            message.setDirection(DIRECTION_IN);
            if (MSG_TYPE_PACS008.equals(message.getMessageType()) || MSG_TYPE_PACS009.equals(message.getMessageType())) {
                message.setStatus(STATUS_PENDING);

                SwiftValidationService.EvaluationResult eval = validationService.evaluerTransaction(message);
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
            String detectedType = detectMessageTypeQuick(xmlFile);

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

            message.setDirection(DIRECTION_OUT);
            message.setStatus(STATUS_SENT);
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
            return MSG_TYPE_PACS008;
        }
        if (fileName.contains("pacs.009") || fileName.contains("pacs009") || fileName.contains("009")) {
            return MSG_TYPE_PACS009;
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

    private boolean has(Document doc, String tag, String fileName) {
        boolean ok = doc.getElementsByTagName(tag).getLength() > 0;
        if (!ok) log.error("{} sans {}.", fileName, tag);
        return ok;
    }
}