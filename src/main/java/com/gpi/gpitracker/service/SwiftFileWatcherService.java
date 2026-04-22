// Déclaration du package (organisation du code)
package com.gpi.gpitracker.service;

// Import des classes nécessaires
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

// Annotation indiquant que cette classe est un service Spring (géré par le conteneur)
@Service
public class SwiftFileWatcherService {

    // Logger pour enregistrer des messages de log (info, erreur, warning)
    private static final Logger log = LoggerFactory.getLogger(SwiftFileWatcherService.class);

    // Injection automatique du service de parsing XML
    @Autowired
    private SwiftParserService parserService;

    // Injection automatique du service d'archivage des fichiers
    @Autowired
    private FileArchiveService archiveService;

    // Injection automatique du repository pour accéder à la base de données
    @Autowired
    private SwiftMessageRepository swiftMessageRepository;

    // Injection automatique du service de validation des transactions
    @Autowired
    private SwiftValidationService validationService;

    // Injection de la valeur de configuration : chemin du dossier à surveiller
    // Défini dans application.properties (ex: swift.received.path=/chemin/vers/messages_recus)
    @Value("${swift.received.path}")
    private String receivedPath;

    /**
     * Méthode exécutée automatiquement selon une périodicité définie
     * fixedDelayString = délai entre la fin de la dernière exécution et le début de la prochaine
     * Valeur par défaut : 30000 millisecondes (30 secondes) si non définie dans properties
     */
    @Scheduled(fixedDelayString = "${swift.watcher.delay:30000}")
    public void scanReceivedMessages() {
        // Log d'information avec la date/heure actuelle du scan
        log.info("=== Scan du dossier messages_recus/ [{}] ===", LocalDateTime.now());

        // Crée un objet File représentant le dossier à surveiller
        File folder = new File(receivedPath);

        // Vérifie si le dossier existe et est bien un répertoire
        if (!folder.exists() || !folder.isDirectory()) {
            // Si le dossier n'existe pas, log d'avertissement et sortie de la méthode
            log.warn("Dossier introuvable : {}", receivedPath);
            return;
        }

        // Liste tous les fichiers du dossier dont le nom se termine par ".xml" (insensible à la casse)
        File[] xmlFiles = folder.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".xml")
        );

        // Vérifie si aucun fichier XML n'a été trouvé
        if (xmlFiles == null || xmlFiles.length == 0) {
            log.info("Aucun fichier XML à traiter.");
            return;
        }

        // Log du nombre de fichiers XML trouvés
        log.info("{} fichier(s) XML trouvé(s).", xmlFiles.length);

        // Parcourt chaque fichier XML trouvé
        for (File xmlFile : xmlFiles) {
            processFile(xmlFile);  // Traite le fichier un par un
        }
    }

    /**
     * Traite un fichier XML individuel
     * @param xmlFile Le fichier XML à traiter
     */
    private void processFile(File xmlFile) {
        // Log du début du traitement du fichier
        log.info("Traitement du fichier : {}", xmlFile.getName());

        try {
            // ÉTAPE 1 : PARSING - Transforme le fichier XML en objet SwiftMessage
            // Si le parsing échoue, message sera null
            SwiftMessage message = parserService.parse(xmlFile);
            if (message == null) {
                log.error("Parsing échoué pour : {}", xmlFile.getName());
                return;  // Arrête le traitement si parsing impossible
            }

            // ÉTAPE 2 : DÉDOUBLONNAGE - Vérifie si ce message a déjà été traité
            // Utilise le msgId (identifiant unique du message SWIFT) pour éviter les doublons
            if (swiftMessageRepository.existsByMsgId(message.getMsgId())) {
                log.warn("Message déjà traité (doublon) : {}. Archivage direct.", message.getMsgId());
                archiveService.archiveReceivedFile(xmlFile.getName());  // Archive sans sauvegarder
                return;  // Sortie de la méthode
            }

            // ÉTAPE 3 : INITIALISATION - Définit les valeurs par défaut
            message.setStatus("EN_ATTENTE");  // Statut initial : en attente de traitement
            message.setReceivedAt(LocalDateTime.now());  // Date/heure de réception

            // ÉTAPE 4 : DÉTECTION DU TYPE DE MESSAGE
            // Vérifie si c'est un message de paiement (client ou interbancaire)
            boolean isPaymentMessage = "PACS008".equals(message.getMessageType()) ||
                    "PACS009".equals(message.getMessageType());

            // ÉTAPE 5 : VALIDATION - Applique les règles métier uniquement aux messages de paiement
            if (isPaymentMessage) {
                // Appelle le service de validation qui analyse la transaction
                SwiftValidationService.EvaluationResult eval = validationService.evaluerTransaction(message);
                // Stocke le niveau d'alerte : "OK", "ATTENTION", "GRAVE"
                message.setAlerte(eval.getAlerte());
                // Stocke le motif détaillé de l'alerte (ex: "Montant dépasse le plafond")
                message.setMotifAlerte(eval.getMotif());
            }
            // Note : Les messages de type PACS002 (réponses) ne sont pas validés

            // ÉTAPE 6 : SAUVEGARDE EN BASE DE DONNÉES
            swiftMessageRepository.save(message);
            log.info("Message sauvegardé : MsgId={}, Type={}, Statut={}, Alerte={}",
                    message.getMsgId(), message.getMessageType(), message.getStatus(), message.getAlerte());

            // ÉTAPE 7 : ARCHIVAGE - Déplace le fichier XML vers le dossier d'archive
            boolean archived = archiveService.archiveReceivedFile(xmlFile.getName());

            // ÉTAPE 8 : MISE À JOUR POST-ARCHIVAGE
            if (archived) {
                // Si l'archivage a réussi, on enregistre la date d'archivage
                message.setArchivedAt(LocalDateTime.now());
                swiftMessageRepository.save(message);  // Met à jour le message en base
            }
            // Note : Si l'archivage échoue, le message reste en base mais le fichier n'est pas déplacé
            // Le fichier sera retraité au prochain scan (car toujours présent dans le dossier source)

        } catch (Exception e) {
            // ÉTAPE 9 : GESTION DES ERREURS
            // Capture toute exception (problème réseau, base de données, parsing, etc.)
            log.error("Erreur lors du traitement de {} : {}", xmlFile.getName(), e.getMessage(), e);
            // Le fichier n'est pas archivé en cas d'erreur pour pouvoir être retraité plus tard
        }
    }
}