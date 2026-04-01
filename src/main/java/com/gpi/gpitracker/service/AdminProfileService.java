package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.AdminProfileDto;
import com.gpi.gpitracker.entity.AdminProfile;
import com.gpi.gpitracker.repository.AdminProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminProfileService {

    private final AdminProfileRepository repo;

    public AdminProfileService(AdminProfileRepository repo) {
        this.repo = repo;
    }

    public AdminProfileDto getProfile(String keycloakId) {
        Optional<AdminProfile> opt = repo.findByKeycloakId(keycloakId);
        if (opt.isPresent()) {
            return toDto(opt.get());
        }
        return defaultDto(keycloakId);
    }

    public AdminProfileDto saveProfile(String keycloakId, AdminProfileDto dto) {
        Optional<AdminProfile> opt = repo.findByKeycloakId(keycloakId);

        AdminProfile entity;
        if (opt.isPresent()) {
            entity = opt.get();
        } else {
            entity = new AdminProfile();
        }

        entity.setKeycloakId(keycloakId);
        entity.setLanguage(dto.getLanguage());
        entity.setTimezone(dto.getTimezone());
        entity.setTheme(dto.getTheme());
        entity.setDateFormat(dto.getDateFormat());
        entity.setNotifUserAdd(dto.isNotifUserAdd());
        entity.setNotifUserDelete(dto.isNotifUserDelete());
        entity.setNotifStatusChange(dto.isNotifStatusChange());
        entity.setNotifSystemAlert(dto.isNotifSystemAlert());

        AdminProfile saved = repo.save(entity);
        return toDto(saved);
    }

    private AdminProfileDto toDto(AdminProfile e) {
        AdminProfileDto d = new AdminProfileDto();
        d.setKeycloakId(e.getKeycloakId());
        d.setLanguage(e.getLanguage());
        d.setTimezone(e.getTimezone());
        d.setTheme(e.getTheme());
        d.setDateFormat(e.getDateFormat());
        d.setNotifUserAdd(e.isNotifUserAdd());
        d.setNotifUserDelete(e.isNotifUserDelete());
        d.setNotifStatusChange(e.isNotifStatusChange());
        d.setNotifSystemAlert(e.isNotifSystemAlert());
        return d;
    }

    private AdminProfileDto defaultDto(String keycloakId) {
        AdminProfileDto d = new AdminProfileDto();
        d.setKeycloakId(keycloakId);
        d.setLanguage("fr");
        d.setTimezone("Africa/Tunis");
        d.setTheme("light");
        d.setDateFormat("dd/MM/yyyy");
        d.setNotifUserAdd(true);
        d.setNotifUserDelete(true);
        d.setNotifStatusChange(true);
        d.setNotifSystemAlert(true);
        return d;
    }
}