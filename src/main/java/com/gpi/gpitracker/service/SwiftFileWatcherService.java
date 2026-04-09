package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;

@Service
public class SwiftFileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(SwiftFileWatcherService.class);

    @Autowired
    private SwiftParserService parserService;           // <-- anciennement Pacs008ParserService

    @Autowired
    private FileArchiveService archiveService;

    @Autowired
    private SwiftMessageRepository swiftMessageRepository; // <-- anciennement Pacs008Repository

    @Value("${swift.received.path}")
    private String receivedPath;

    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanReceivedMessages() {
        log.info("=== Scan du dossier messages_recus/ [{}] ===", LocalDateTime.now());

        File folder = new File(receivedPath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.warn("Dossier introuvable : {}", receivedPath);
            return;
        }

        File[] xmlFiles = folder.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".xml")
        );

        if (xmlFiles == null || xmlFiles.length == 0) {
            log.info("Aucun fichier XML à traiter.");
            return;
        }

        log.info("{} fichier(s) XML trouvé(s).", xmlFiles.length);

        for (File xmlFile : xmlFiles) {
            processFile(xmlFile);
        }
    }

    private void processFile(File xmlFile) {
        log.info("Traitement du fichier : {}", xmlFile.getName());

        try {
            // ÉTAPE 1 : Parsing XML — le parser détecte automatiquement le type
            SwiftMessage message = parserService.parse(xmlFile);

            if (message == null) {
                log.error("Parsing échoué pour : {}", xmlFile.getName());
                return;
            }

            // ÉTAPE 2 : Vérifier les doublons
            if (swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                log.warn("Message déjà traité (doublon) : {}. Archivage direct.", message.getMsgId());
                archiveService.archiveReceivedFile(xmlFile.getName());
                return;
            }

            // ÉTAPE 3 : Sauvegarde en base Oracle
            swiftMessageRepository.save(message);
            log.info("Message sauvegardé [{}] : MsgId={}", message.getMessageType(), message.getMsgId());

            // ÉTAPE 4 : Move vers archive/
            boolean archived = archiveService.archiveReceivedFile(xmlFile.getName());

            if (archived) {
                message.setStatus("ARCHIVED");
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);
                log.info("Fichier archivé avec succès : {}", xmlFile.getName());
            } else {
                log.error("Archivage échoué pour : {}", xmlFile.getName());
            }

        } catch (Exception e) {
            log.error("Erreur lors du traitement de {} : {}", xmlFile.getName(), e.getMessage(), e);
        }
    }
}