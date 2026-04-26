package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankDirectoryService {

    private final BankDirectoryRepository bankDirectoryRepository;

    /**
     * Auto-enregistrement silencieux d'une banque
     * Si le BIC existe déjà, on incrémente juste le compteur
     * Si le BIC n'existe pas, on l'ajoute
     */
    @Transactional
    public void autoRegisterIfNotExists(String bic) {
        if (bic == null || bic.isBlank()) {
            return;
        }

        String bicUpper = bic.toUpperCase().trim();

        try {
            Optional<BankDirectory> existing = bankDirectoryRepository.findByBicIgnoreCase(bicUpper);

            if (existing.isPresent()) {
                // Mise à jour : incrémenter le compteur
                BankDirectory bank = existing.get();
                bank.setOccurrenceCount(bank.getOccurrenceCount() + 1);
                bank.setLastSeenAt(LocalDateTime.now());
                bankDirectoryRepository.save(bank);
                log.debug("Banque déjà existante, compteur incrémenté: {}", bicUpper);
            } else {
                // Nouvelle banque - ajout automatique
                BankDirectory newBank = new BankDirectory();
                newBank.setBic(bicUpper);
                newBank.setBankName(bicUpper);  // Nom temporaire = BIC
                newBank.setCountryCode("??");
                newBank.setFirstSeenAt(LocalDateTime.now());
                newBank.setLastSeenAt(LocalDateTime.now());
                newBank.setOccurrenceCount(1);

                bankDirectoryRepository.save(newBank);
                log.info("🏦 Nouvelle banque auto-enregistrée: {}", bicUpper);
            }
        } catch (Exception e) {
            log.error("Erreur auto-enregistrement banque {}: {}", bicUpper, e.getMessage());
        }
    }
}