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

    // Ces valeurs sont définies dans application.properties
    @Value("${swift.received.path}")
    private String receivedPath;

    @Value("${swift.received.archive.path}")
    private String archivePath;

    /**
     * Déplace un fichier du dossier "messages_recus" vers "messages_recus/archive"
     *
     * Exemple :
     *   AVANT  : swift/messages_recus/pacs.008.xml
     *   APRÈS  : swift/messages_recus/archive/pacs.008_20240405_143022.xml
     *
     * On ajoute un timestamp au nom du fichier pour éviter les écrasements.
     */
    public boolean archiveReceivedFile(String fileName) {
        try {
            // Chemin du fichier source (dans messages_recus)
            Path source = Paths.get(receivedPath, fileName);

            // Créer le dossier archive s'il n'existe pas encore
            Path archiveDir = Paths.get(archivePath);
            Files.createDirectories(archiveDir);

            // Nouveau nom avec timestamp : pacs.008_20240405_143022.xml
            String archivedFileName = buildArchivedFileName(fileName);

            // Chemin de destination (dans archive)
            Path destination = archiveDir.resolve(archivedFileName);

            // LE MOVE : déplace le fichier (équivalent à couper/coller)
            // REPLACE_EXISTING = si le fichier existe déjà dans archive, on le remplace
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Fichier archivé avec succès : {} → {}", source, destination);
            return true;

        } catch (IOException e) {
            log.error("Erreur lors de l'archivage du fichier {} : {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * Construit le nouveau nom du fichier avec un timestamp
     * Exemple : pacs.008.xml → pacs.008_20240405_143022.xml
     */
    private String buildArchivedFileName(String originalFileName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Sépare le nom de l'extension
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = originalFileName.substring(0, dotIndex);
            String ext  = originalFileName.substring(dotIndex);
            return name + "_" + timestamp + ext;
        }
        return originalFileName + "_" + timestamp;
    }
}