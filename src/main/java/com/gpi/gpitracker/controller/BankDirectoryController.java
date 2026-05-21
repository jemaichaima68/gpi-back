package com.gpi.gpitracker.controller;

import com.gpi.gpitracker.dto.BankDirectorySimpleDto;
import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bank-directory")
@RequiredArgsConstructor
public class BankDirectoryController {

    private final BankDirectoryRepository bankDirectoryRepository;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<BankDirectorySimpleDto>> getAllBanks() {
        List<BankDirectory> banks = bankDirectoryRepository.findAll();

        List<BankDirectorySimpleDto> result = banks.stream().map(bank -> {
            BankDirectorySimpleDto dto = new BankDirectorySimpleDto();
            dto.setBic(bank.getBic());
            dto.setBankName(bank.getBankName());
            dto.setCountryCode(bank.getCountryCode());
            dto.setCity(bank.getCity());
            dto.setFirstSeenAt(bank.getFirstSeenAt());
            dto.setLastSeenAt(bank.getLastSeenAt());
            dto.setOccurrenceCount(bank.getOccurrenceCount());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}