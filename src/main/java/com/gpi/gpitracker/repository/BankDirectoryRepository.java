package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.BankDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BankDirectoryRepository extends JpaRepository<BankDirectory, String> {
    Optional<BankDirectory> findByBicIgnoreCase(String bic);
}