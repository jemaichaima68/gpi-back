package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.BankJourneyDto;
import com.gpi.gpitracker.dto.BankJourneyInputDto;
import com.gpi.gpitracker.entity.TransactionBankStep;
import com.gpi.gpitracker.repository.TransactionBankStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankJourneyService {

    private final TransactionBankStepRepository bankStepRepository;

    public List<BankJourneyDto> getBankJourneyByUetr(String uetr, String clientEmail) {
        List<TransactionBankStep> steps = bankStepRepository.findByUetrAndClientEmailOrderByStepOrderAsc(uetr, clientEmail);
        return steps.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<BankJourneyDto> getBankJourneyByTransactionId(Long transactionId, String clientEmail) {
        List<TransactionBankStep> steps = bankStepRepository.findByTransactionIdAndClientEmailOrderByStepOrderAsc(transactionId, clientEmail);
        return steps.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveBankJourney(String uetr, Long transactionId, String clientEmail, List<BankJourneyInputDto> steps) {
        bankStepRepository.deleteByUetrAndClientEmail(uetr, clientEmail);

        for (int i = 0; i < steps.size(); i++) {
            BankJourneyInputDto step = steps.get(i);
            TransactionBankStep entity = new TransactionBankStep();
            entity.setUetr(uetr);
            entity.setTransactionId(transactionId);
            entity.setClientEmail(clientEmail);
            entity.setStepOrder(i + 1);
            entity.setBankName(step.getBankName());
            entity.setBankBic(step.getBankBic());
            entity.setRole(step.getRole());
            entity.setFees(step.getFees());
            entity.setFeesCurrency(step.getFeesCurrency());
            entity.setStatus(step.getStatus());
            entity.setStepTimestamp(LocalDateTime.now());
            bankStepRepository.save(entity);
        }
    }

    @Transactional
    public void deleteByUetr(String uetr, String clientEmail) {
        bankStepRepository.deleteByUetrAndClientEmail(uetr, clientEmail);
    }

    private BankJourneyDto convertToDto(TransactionBankStep step) {
        BankJourneyDto dto = new BankJourneyDto();
        dto.setStep(step.getStepOrder());
        dto.setBankName(step.getBankName());
        dto.setBankBic(step.getBankBic());
        dto.setRole(step.getRole());

        String feesText = "";
        if (step.getFees() != null && step.getFeesCurrency() != null) {
            feesText = String.format("%.2f %s", step.getFees(), step.getFeesCurrency());
        }
        dto.setFees(feesText);

        dto.setStatus(step.getStatus());

        String timestampText = "";
        if (step.getStepTimestamp() != null) {
            timestampText = step.getStepTimestamp().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }
        dto.setTimestamp(timestampText);

        return dto;
    }
}