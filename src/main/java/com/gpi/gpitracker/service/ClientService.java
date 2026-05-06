package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.*;
import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.entity.ClientConsultation;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import com.gpi.gpitracker.repository.ClientConsultationRepository;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final ClientConsultationRepository clientConsultationRepository;
    private final AgentValidationService agentValidationService;
    private final BankDirectoryRepository bankDirectoryRepository;
    private final BankJourneyService bankJourneyService;

    // ==================== NORMALISATION STATUT ====================
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "PDNG";
        switch (status.toUpperCase()) {
            case "EN_ATTENTE": case "PDNG": case "SIGNALE": return "PDNG";
            case "ACTC": return "ACTC";
            case "ACSP": return "ACSP";
            case "ACCP": case "ACCEPTE": case "ACTIVE": case "VALIDATED": case "ACSC": return "ACSC";
            case "RJCT": case "REJETE": case "REJETE_AUTO": return "RJCT";
            default: return status.toUpperCase();
        }
    }

    private String getStatusLabel(String status) {
        switch (status) {
            case "PDNG": return "En attente";
            case "ACTC": return "Validation technique";
            case "ACSP": return "En traitement";
            case "ACSC": return "Accepté";
            case "RJCT": return "Rejeté";
            default: return status;
        }
    }

    // ==================== TRANSFERTS ====================
    public Optional<TransferResponseDto> getTransferByUetr(String uetr, String clientEmail) {
        Optional<SwiftMessage> message = swiftMessageRepository.findFirstByUetrOrderByReceivedAtDesc(uetr);
        message.ifPresent(swiftMessage -> {
            if (swiftMessage.getClientEmail() == null || swiftMessage.getClientEmail().equals(clientEmail)) {
                clientConsultationRepository.save(
                        new ClientConsultation(clientEmail, swiftMessage.getUetr(), LocalDateTime.now())
                );
            }
        });
        return message.map(this::toTransferResponseDto);
    }

    public Optional<TransferResponseDto> getTransactionById(Long id, String clientEmail) {
        Optional<SwiftMessage> message = swiftMessageRepository.findById(id);
        return message.map(this::toTransferResponseDto);
    }

    public List<TransactionTimelineDto> getTransactionTimeline(Long id, String clientEmail) {
        Optional<SwiftMessage> opt = swiftMessageRepository.findById(id);
        if (opt.isEmpty()) return Collections.emptyList();

        SwiftMessage msg = opt.get();
        List<TransactionTimelineDto> timeline = new ArrayList<>();

        Map<String, String> labels = Map.of(
                "PDNG", "En attente", "ACTC", "Validation technique",
                "ACSP", "En traitement", "ACSC", "Acceptée", "RJCT", "Rejetée"
        );

        Map<String, String> icons = Map.of(
                "PDNG", "clock", "ACTC", "check-circle",
                "ACSP", "hourglass", "ACSC", "check-double", "RJCT", "times-circle"
        );

        List<String> ordered = List.of("PDNG", "ACTC", "ACSP", "ACSC", "RJCT");
        String currentStatus = msg.getStatus();

        for (String status : ordered) {
            if ("RJCT".equals(currentStatus) && !"RJCT".equals(status)) continue;
            TransactionTimelineDto dto = new TransactionTimelineDto();
            dto.setStatus(status);
            dto.setStatusLabel(labels.getOrDefault(status, status));
            dto.setDescription("Statut " + labels.getOrDefault(status, status));
            dto.setTimestamp(msg.getReceivedAt());
            dto.setIcon(icons.getOrDefault(status, "info"));
            dto.setCompleted(status.equals(currentStatus));
            timeline.add(dto);
            if (status.equals(currentStatus)) break;
        }
        return timeline;
    }

    public Map<String, String> getRejectionReason(Long id, String clientEmail) {
        Optional<SwiftMessage> opt = swiftMessageRepository.findById(id);
        if (opt.isEmpty()) return Map.of();
        return Map.of("rejectionReason", Optional.ofNullable(opt.get().getRejectionReason()).orElse(""));
    }

    // ==================== HISTORIQUE ====================
    public List<ConsultationHistoryDto> getConsultationHistory(String clientEmail) {
        return clientConsultationRepository.findByClientEmailOrderByConsultedAtDesc(clientEmail)
                .stream()
                .map(this::toConsultationHistoryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteConsultationHistory(Long id, String clientEmail) {
        Optional<ClientConsultation> consultation = clientConsultationRepository.findById(id);
        if (consultation.isPresent() && consultation.get().getClientEmail().equals(clientEmail)) {
            clientConsultationRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional
    public int deleteAllConsultationHistory(String clientEmail) {
        List<ClientConsultation> consultations = clientConsultationRepository
                .findByClientEmailOrderByConsultedAtDesc(clientEmail);
        int count = consultations.size();
        if (count > 0) {
            clientConsultationRepository.deleteAll(consultations);
        }
        return count;
    }

    // ==================== DASHBOARD ====================
    public ClientDashboardDto getClientDashboard(String clientEmail) {
        List<SwiftMessage> messages = swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> clientEmail.equals(m.getClientEmail()))
                .collect(Collectors.toList());

        ClientDashboardDto dto = new ClientDashboardDto();
        dto.setTotalTransactions((long) messages.size());
        dto.setPendingTransactions(messages.stream().filter(m -> "PDNG".equals(m.getStatus())).count());
        dto.setAcceptedTransactions(messages.stream().filter(m -> "ACSC".equals(m.getStatus()) || "ACSP".equals(m.getStatus())).count());
        dto.setRejectedTransactions(messages.stream().filter(m -> "RJCT".equals(m.getStatus())).count());
        dto.setTotalAmount(messages.stream().map(SwiftMessage::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        dto.setRecentTransactions(messages.stream().limit(5).map(m -> {
            RecentTransactionDto recentDto = new RecentTransactionDto();
            recentDto.setId(m.getId());
            recentDto.setUetr(m.getUetr());
            recentDto.setStatus(m.getStatus());
            recentDto.setAmount(m.getAmount());
            recentDto.setCurrency(m.getCurrency());
            recentDto.setCreditorName(m.getCreditorName());
            recentDto.setDebtorName(m.getDebtorName());
            recentDto.setReceivedAt(m.getReceivedAt());
            return recentDto;
        }).collect(Collectors.toList()));

        dto.setStatusDistribution(messages.stream().collect(Collectors.groupingBy(SwiftMessage::getStatus, Collectors.counting())));
        return dto;
    }

    // ==================== FILTRAGE ====================
    public List<SwiftMessage> filterClientTransactions(String clientEmail, LocalDateTime startDate, LocalDateTime endDate,
                                                       BigDecimal minAmount, BigDecimal maxAmount, String status, String creditorCountry) {
        return swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> clientEmail.equals(m.getClientEmail()))
                .filter(m -> startDate == null || m.getReceivedAt().isAfter(startDate))
                .filter(m -> endDate == null || m.getReceivedAt().isBefore(endDate))
                .filter(m -> minAmount == null || m.getAmount().compareTo(minAmount) >= 0)
                .filter(m -> maxAmount == null || m.getAmount().compareTo(maxAmount) <= 0)
                .filter(m -> status == null || status.equals(m.getStatus()))
                .filter(m -> creditorCountry == null || creditorCountry.equals(m.getCreditorCountry()))
                .collect(Collectors.toList());
    }

    // ==================== EXPORTS ====================
    public void exportTransactionsPdf(HttpServletResponse response) {
        throw new UnsupportedOperationException("Export PDF à implémenter");
    }

    public void exportTransactionsExcel(HttpServletResponse response) {
        throw new UnsupportedOperationException("Export Excel à implémenter");
    }

    // ==================== GESTION PARCOURS BANCAIRE ====================
    @Transactional
    public void saveBankJourneyFromSwiftMessage(SwiftMessage message) {
        String clientEmail = message.getClientEmail();
        if (clientEmail == null || clientEmail.isBlank()) return;

        List<BankJourneyInputDto> steps = extractBankStepsFromMessage(message);
        if (!steps.isEmpty()) {
            bankJourneyService.saveBankJourney(message.getUetr(), message.getId(), clientEmail, steps);
        }
    }

    @Transactional
    public void deleteBankJourneyByUetr(String uetr, String clientEmail) {
        bankJourneyService.deleteByUetr(uetr, clientEmail);
    }

    private List<BankJourneyInputDto> extractBankStepsFromMessage(SwiftMessage message) {
        List<BankJourneyInputDto> steps = new ArrayList<>();

        // Étape 1: Banque émettrice
        if (message.getDebtorAgentBic() != null && !message.getDebtorAgentBic().isBlank()) {
            BankJourneyInputDto step = new BankJourneyInputDto();
            step.setBankName(getBankNameFromBic(message.getDebtorAgentBic()));
            step.setBankBic(message.getDebtorAgentBic());
            step.setRole("Banque Émettrice");
            step.setFees(BigDecimal.valueOf(calculateEmitterFees(message)));
            step.setFeesCurrency(message.getCurrency());
            step.setStatus("Envoyé");
            steps.add(step);
        }

        // Dernière étape: Banque bénéficiaire
        if (message.getCreditorAgentBic() != null && !message.getCreditorAgentBic().isBlank()) {
            BankJourneyInputDto lastStep = new BankJourneyInputDto();
            lastStep.setBankName(getBankNameFromBic(message.getCreditorAgentBic()));
            lastStep.setBankBic(message.getCreditorAgentBic());
            lastStep.setRole("Banque Bénéficiaire finale");
            lastStep.setFees(null);
            lastStep.setFeesCurrency(null);
            lastStep.setStatus(getFinalStepStatus(message));
            steps.add(lastStep);
        }

        return steps;
    }

    private Double calculateEmitterFees(SwiftMessage message) {
        double amount = message.getAmount() != null ? message.getAmount().doubleValue() : 0;
        return amount * 0.005;
    }

    private String getFinalStepStatus(SwiftMessage message) {
        String status = message.getStatus();
        if ("ACSC".equals(status) || "ACCEPTE".equals(status)) return "Terminé";
        if ("RJCT".equals(status) || "REJETE".equals(status)) return "Rejeté";
        if ("ACSP".equals(status) || "ACTC".equals(status)) return "En cours";
        return "En attente";
    }

    // ==================== CONVERSIONS ====================
    private TransferResponseDto toTransferResponseDto(SwiftMessage message) {
        TransferResponseDto dto = new TransferResponseDto();
        dto.setId(message.getId());
        dto.setUetr(message.getUetr());
        dto.setAmount(message.getAmount());
        dto.setCurrency(message.getCurrency());
        dto.setBeneficiaryName(message.getCreditorName());
        dto.setBeneficiaryAccount(message.getCreditorIban());
        dto.setBeneficiaryBank(message.getCreditorAgentBic());
        dto.setSenderName(message.getDebtorName());
        dto.setDebtorName(message.getDebtorName());
        dto.setCreditorName(message.getCreditorName());
        dto.setCreditorAgentBic(message.getCreditorAgentBic());
        dto.setStatus(agentValidationService != null ? agentValidationService.normalizeStatus(message.getStatus()) : normalizeStatus(message.getStatus()));
        dto.setCreatedAt(message.getReceivedAt());
        dto.setUpdatedAt(message.getReceivedAt());
        dto.setRejectionReason(message.getRejectionReason());
        dto.setAlerte(message.getAlerte());
        dto.setMotifAlerte(message.getMotifAlerte());

        // Récupération du parcours bancaire depuis la base
        List<BankJourneyDto> journey = bankJourneyService.getBankJourneyByUetr(message.getUetr(), message.getClientEmail());
        dto.setBankJourney(journey);
        dto.setTotalFees(calculateTotalFeesFromSteps(journey));
        dto.setNetAmount(calculateNetAmountFromSteps(message, journey));

        return dto;
    }

    private Double calculateTotalFeesFromSteps(List<BankJourneyDto> journey) {
        double total = 0.0;
        for (BankJourneyDto step : journey) {
            if (step.getFees() != null && !step.getFees().isBlank()) {
                try {
                    String feesStr = step.getFees().replace(",", ".").replaceAll("[^0-9.]", "");
                    total += Double.parseDouble(feesStr);
                } catch (NumberFormatException e) { }
            }
        }
        return total;
    }

    private Double calculateNetAmountFromSteps(SwiftMessage message, List<BankJourneyDto> journey) {
        double amount = message.getAmount() != null ? message.getAmount().doubleValue() : 0;
        double totalFees = calculateTotalFeesFromSteps(journey);
        double net = amount - totalFees;
        if ("EUR".equals(message.getCurrency()) && net > 0) net = net * 1.09;
        return net > 0 ? net : 0;
    }

    private String getBankNameFromBic(String bic) {
        if (bic == null || bic.isBlank()) return bic;
        return bankDirectoryRepository.findByBicIgnoreCase(bic)
                .map(BankDirectory::getBankName)
                .orElse(bic);
    }

    private ConsultationHistoryDto toConsultationHistoryDto(ClientConsultation consultation) {
        ConsultationHistoryDto dto = new ConsultationHistoryDto();
        dto.setId(consultation.getId());
        dto.setUetr(consultation.getUetr());
        dto.setConsultedAt(consultation.getConsultedAt());

        Optional<SwiftMessage> msg = swiftMessageRepository.findFirstByUetrOrderByReceivedAtDesc(consultation.getUetr());
        if (msg.isPresent()) {
            SwiftMessage m = msg.get();
            dto.setStatus(m.getStatus());
            dto.setAmount(m.getAmount());
            dto.setCurrency(m.getCurrency());
            dto.setUpdatedAt(m.getReceivedAt());
        } else {
            dto.setStatus("PDNG");
            dto.setAmount(BigDecimal.ZERO);
            dto.setCurrency("EUR");
            dto.setUpdatedAt(consultation.getConsultedAt());
        }
        return dto;
    }
}