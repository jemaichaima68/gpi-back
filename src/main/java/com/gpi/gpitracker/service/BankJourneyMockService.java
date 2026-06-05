package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.BankJourneyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankJourneyMockService {

    /**
     * Génère un parcours bancaire mocké pour les transactions sans parcours réel
     */
    public List<BankJourneyDto> generateMockJourney(String debtorCountry, String creditorCountry, BigDecimal amount) {
        List<BankJourneyDto> journey = new ArrayList<>();

        if (debtorCountry == null || creditorCountry == null) {
            log.warn("Pays manquants, parcours bancaire par défaut");
            return getDefaultJourney(amount);
        }

        int step = 1;

        // 1. Banque émettrice (pays du donneur d'ordre)
        journey.add(createBankStep(step++, getBankName(debtorCountry), getBankBic(debtorCountry), "Banque émettrice", 2.50, amount));

        // 2. Banque intermédiaire (si pays différents)
        if (!debtorCountry.equals(creditorCountry)) {
            journey.add(createBankStep(step++, "SWIFT International", "SWIFTINTL", "Banque correspondante", 3.50, amount));
        }

        // 3. Banque bénéficiaire (pays du créditeur)
        journey.add(createBankStep(step++, getBankName(creditorCountry), getBankBic(creditorCountry), "Banque bénéficiaire", 5.00, amount));

        return journey;
    }

    private BankJourneyDto createBankStep(int step, String bankName, String bankBic, String role, double fixedFees, BigDecimal amount) {
        BankJourneyDto dto = new BankJourneyDto();
        dto.setStep(step);
        dto.setBankName(bankName);
        dto.setBankBic(bankBic);
        dto.setRole(role);
        dto.setFees(String.format("%.2f EUR", fixedFees));
        dto.setStatus("Terminé");
        dto.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        return dto;
    }

    private List<BankJourneyDto> getDefaultJourney(BigDecimal amount) {
        List<BankJourneyDto> journey = new ArrayList<>();
        journey.add(createBankStep(1, "Banque Émettrice", "BANKFRPP", "Banque émettrice", 2.50, amount));
        journey.add(createBankStep(2, "Banque Bénéficiaire", "BANKTNTT", "Banque bénéficiaire", 5.00, amount));
        return journey;
    }

    private String getBankName(String countryCode) {
        return switch (countryCode) {
            case "FR" -> "BNP Paribas";
            case "TN" -> "Banque Internationale Arabe de Tunisie";
            case "DE" -> "Deutsche Bank";
            case "BE" -> "KBC Bank";
            case "CH" -> "UBS";
            case "MA" -> "Attijariwafa Bank";
            case "DZ" -> "Banque d'Algérie";
            case "SN" -> "Ecobank Sénégal";
            case "CI" -> "NSIA Banque";
            case "US" -> "JPMorgan Chase";
            case "GB" -> "Barclays";
            case "IT" -> "UniCredit";
            case "ES" -> "Santander";
            default -> "SWIFT International Bank";
        };
    }

    private String getBankBic(String countryCode) {
        return switch (countryCode) {
            case "FR" -> "BNPAFRPP";
            case "TN" -> "BIATTTN";
            case "DE" -> "DEUTDEFF";
            case "BE" -> "KREDBEBB";
            case "CH" -> "UBSWCHZH";
            case "MA" -> "ATWAMAMC";
            case "DZ" -> "ALGIDZAL";
            case "SN" -> "ECOCSN";
            case "CI" -> "NSIACIAB";
            case "US" -> "CHASUS33";
            case "GB" -> "BARCGB22";
            case "IT" -> "UNCRITMM";
            case "ES" -> "BSCHESMM";
            default -> "SWIFTINTL";
        };
    }
}