package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.AppSettingsDto;
import com.gpi.gpitracker.entity.AppSettings;
import com.gpi.gpitracker.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingsRepository repo;

    private static final String GLOBAL_ID = "GLOBAL";

    // ── GET ───────────────────────────────────────────────────────
    public AppSettingsDto getSettings() {
        AppSettings entity = repo.findById(GLOBAL_ID)
                .orElseGet(() -> createDefault());
        return toDto(entity);
    }

    // ── SAVE ──────────────────────────────────────────────────────
    public AppSettingsDto saveSettings(AppSettingsDto dto) {
        AppSettings entity = repo.findById(GLOBAL_ID)
                .orElseGet(AppSettings::new);

        entity.setId(GLOBAL_ID);
        entity.setMontantMax(dto.getMontantMax());
        entity.setMontantMin(dto.getMontantMin());
        entity.setDelaiTraitementH(dto.getDelaiTraitementH());

        // Convertir List<String> → String CSV
        if (dto.getDevisesAutorisees() != null) {
            entity.setDevisesAutorisees(
                    String.join(",", dto.getDevisesAutorisees())
            );
        }
        if (dto.getPaysSanctionnes() != null) {
            entity.setPaysSanctionnes(
                    String.join(",", dto.getPaysSanctionnes())
            );
        }

        return toDto(repo.save(entity));
    }

    // ── Utilitaire : accessible depuis ValidationService ──────────
    public AppSettings getRawSettings() {
        return repo.findById(GLOBAL_ID).orElseGet(() -> createDefault());
    }

    // ── Helpers ───────────────────────────────────────────────────
    private AppSettings createDefault() {
        AppSettings s = new AppSettings();
        s.setId(GLOBAL_ID);
        return repo.save(s);
    }

    private AppSettingsDto toDto(AppSettings e) {
        AppSettingsDto d = new AppSettingsDto();
        d.setMontantMax(e.getMontantMax());
        d.setMontantMin(e.getMontantMin());
        d.setDelaiTraitementH(e.getDelaiTraitementH());
        d.setDevisesAutorisees(splitCsv(e.getDevisesAutorisees()));
        d.setPaysSanctionnes(splitCsv(e.getPaysSanctionnes()));
        return d;
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}