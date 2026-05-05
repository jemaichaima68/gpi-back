package com.gpi.gpitracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class FileArchiveService {

    private static final Logger log = LoggerFactory.getLogger(FileArchiveService.class);

    // ==================== DOSSIERS MESSAGES REÇUS ====================

    @Value("${swift.received.path}")
    private String receivedPath;

    @Value("${swift.received.archive.path}")
    private String receivedArchivePath;

    // ==================== DOSSIERS MESSAGES ÉMIS ====================

    @Value("${swift.emitted.path}")
    private String emittedPath;

    @Value("${swift.emitted.archive.path}")
    private String emittedArchivePath;

    // ==================== ARCHIVAGE DES MESSAGES REÇUS ====================

    public boolean archiveReceivedFile(String fileName) {
        return archiveFile(receivedPath, receivedArchivePath, fileName, "reçu");
    }

    // ==================== ARCHIVAGE DES MESSAGES ÉMIS ====================

    public boolean archiveEmittedFile(String fileName) {
        return archiveFile(emittedPath, emittedArchivePath, fileName, "émis");
    }

    // ==================== MÉTHODE COMMUNE ====================

    private boolean archiveFile(String sourceFolder, String archiveFolder, String fileName, String type) {
        try {
            Path source = Paths.get(sourceFolder, fileName);

            if (!Files.exists(source)) {
                log.error("Fichier {} introuvable pour archivage : {}", type, source);
                return false;
            }

            Path archiveDir = Paths.get(archiveFolder);
            Files.createDirectories(archiveDir);

            String archivedFileName = buildArchivedFileName(fileName);
            Path destination = archiveDir.resolve(archivedFileName);

            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Fichier {} archivé avec succès : {} → {}", type, source, destination);
            return true;

        } catch (IOException e) {
            log.error("Erreur lors de l'archivage du fichier {} {} : {}", type, fileName, e.getMessage());
            return false;
        }
    }

    // ==================== NOM DU FICHIER ARCHIVÉ ====================

    private String buildArchivedFileName(String originalFileName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        int dotIndex = originalFileName.lastIndexOf('.');

        if (dotIndex > 0) {
            String name = originalFileName.substring(0, dotIndex);
            String ext = originalFileName.substring(dotIndex);
            return name + "_" + timestamp + ext;
        }

        return originalFileName + "_" + timestamp;
    }
}