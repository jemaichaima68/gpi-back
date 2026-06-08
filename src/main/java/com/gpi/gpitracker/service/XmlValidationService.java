package com.gpi.gpitracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class XmlValidationService {

    // Constantes pour les types de messages supportés par XSD
    public static final String TYPE_PACS002 = "PACS002";
    public static final String TYPE_CAMT056 = "CAMT056";
    public static final String TYPE_CAMT029 = "CAMT029";

    // Chemins des XSD dans resources/xsd/
    private static final String PACS002_XSD = "xsd/pacs.002.001.10.xsd";
    private static final String CAMT056_XSD = "xsd/camt.056.001.08.xsd";
    private static final String CAMT029_XSD = "xsd/camt.029.001.09.xsd";

    // Cache des schémas chargés
    private final Map<String, Schema> schemaCache = new HashMap<>();

    /**
     * Valide un fichier XML selon son type
     * @param xmlFile Fichier XML à valider
     * @param messageType Type de message (PACS002, CAMT056, CAMT029)
     * @return true si valide, false sinon
     */
    public boolean validateXmlFile(File xmlFile, String messageType) {
        if (xmlFile == null || !xmlFile.exists() || xmlFile.length() == 0) {
            log.error("Fichier invalide ou vide");
            return false;
        }

        String xsdPath = getXsdPath(messageType);
        if (xsdPath == null) {
            log.debug("Aucun XSD défini pour le type: {}, validation basique", messageType);
            return basicXmlValidation(xmlFile);
        }
        return validateXmlAgainstXsd(xmlFile, xsdPath);
    }

    /**
     * Valide un contenu XML string selon son type
     */
    public boolean validateXmlString(String xmlContent, String messageType) {
        if (xmlContent == null || xmlContent.isBlank()) {
            log.error("Contenu XML vide");
            return false;
        }

        String xsdPath = getXsdPath(messageType);
        if (xsdPath == null) {
            log.debug("Aucun XSD défini pour le type: {}, validation ignorée", messageType);
            return true;
        }
        return validateXmlStringAgainstXsd(xmlContent, xsdPath);
    }

    /**
     * Validation basique sans XSD (vérifie seulement que c'est du XML valide)
     */
    public boolean basicXmlValidation(File xmlFile) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.newDocumentBuilder().parse(xmlFile);
            log.info(" Validation basique réussie pour {}", xmlFile.getName());
            return true;
        } catch (Exception e) {
            log.error(" Validation basique échouée pour {} : {}", xmlFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Retourne le chemin XSD selon le type de message
     */
    private String getXsdPath(String messageType) {
        if (messageType == null) return null;

        return switch (messageType) {
            case TYPE_PACS002 -> PACS002_XSD;
            case TYPE_CAMT056 -> CAMT056_XSD;
            case TYPE_CAMT029 -> CAMT029_XSD;
            default -> null;
        };
    }

    /**
     * Valide un fichier XML contre un XSD spécifique
     */
    private boolean validateXmlAgainstXsd(File xmlFile, String xsdPath) {
        try {
            Schema schema = getSchema(xsdPath);
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xmlFile));
            log.info(" Validation XSD réussie: {} contre {}", xmlFile.getName(), xsdPath);
            return true;
        } catch (SAXException e) {
            log.error(" Validation XSD échouée pour {} : {}", xmlFile.getName(), e.getMessage());
            return false;
        } catch (IOException e) {
            log.error(" Erreur lecture fichier {} : {}", xmlFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Valide un contenu XML string contre un XSD
     */
    private boolean validateXmlStringAgainstXsd(String xmlContent, String xsdPath) {
        try {
            Schema schema = getSchema(xsdPath);
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
            log.info("Validation XSD string réussie contre {}", xsdPath);
            return true;
        } catch (SAXException e) {
            log.error(" Validation XSD string échouée : {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.error(" Erreur lecture string XML : {}", e.getMessage());
            return false;
        }
    }

    /**
     * Charge un schéma XSD depuis le classpath avec cache
     */
    private Schema getSchema(String xsdPath) throws SAXException {
        if (schemaCache.containsKey(xsdPath)) {
            return schemaCache.get(xsdPath);
        }

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            ClassPathResource resource = new ClassPathResource(xsdPath);

            if (!resource.exists()) {
                log.error("XSD non trouvé: {}", xsdPath);
                throw new SAXException("XSD non trouvé: " + xsdPath);
            }

            Schema schema = factory.newSchema(new StreamSource(resource.getInputStream()));
            schemaCache.put(xsdPath, schema);
            log.info(" Schéma XSD chargé: {}", xsdPath);
            return schema;
        } catch (IOException e) {
            log.error(" Impossible de charger le XSD {} : {}", xsdPath, e.getMessage());
            throw new SAXException("XSD non trouvé: " + xsdPath, e);
        }
    }

    /**
     * Vérifie si un type de message est supporté par la validation XSD
     */
    public boolean isTypeSupported(String messageType) {
        return getXsdPath(messageType) != null;
    }
}