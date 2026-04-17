package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, String> {
    // findById("GLOBAL") suffit — une seule ligne de config
}