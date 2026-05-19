package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.*;
import com.gpi.gpitracker.entity.BankDirectory;
import com.gpi.gpitracker.entity.ClientConsultation;
import com.gpi.gpitracker.entity.SwiftMessage;
import com.gpi.gpitracker.repository.BankDirectoryRepository;
import com.gpi.gpitracker.repository.ClientConsultationRepository;
import com.gpi.gpitracker.repository.SwiftMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final SwiftMessageRepository swiftMessageRepository;
    private final ClientConsultationRepository clientConsultationRepository;
    private final BankDirectoryRepository bankDirectoryRepository;
    private final BankJourneyService bankJourneyService;

    // ==================== STATUTS MÉTIER CLIENT ====================
    private static final String STATUS_PENDING = "PDNG";
    private static final String STATUS_ACCEPTED = "ACCEPTE";
    private static final String STATUS_REJECTED = "REJETE";
    private static final String STATUS_CANCEL_PENDING = "ANNULATION_EN_ATTENTE";
    private static final String STATUS_CANCELLED = "ANNULEE";

    // ==================== TRANSFERTS ====================

    public Optional<TransferResponseDto> getTransferByUetr(String uetr, String clientEmail) {
        Optional<SwiftMessage> message = findClientOriginalTransactionByUetr(uetr, clientEmail);

        message.ifPresent(swiftMessage -> {
            clientConsultationRepository.save(
                    new ClientConsultation(clientEmail, swiftMessage.getUetr(), LocalDateTime.now())
            );
        });

        return message.map(this::toTransferResponseDto);
    }

    public Optional<TransferResponseDto> getTransactionById(Long id, String clientEmail) {
        Optional<SwiftMessage> message = swiftMessageRepository.findById(id);

        if (message.isEmpty()) {
            return Optional.empty();
        }

        SwiftMessage tx = message.get();

        if (clientEmail != null && tx.getClientEmail() != null && !clientEmail.equals(tx.getClientEmail())) {
            return Optional.empty();
        }

        if (!isClientVisibleTransaction(tx)) {
            return Optional.empty();
        }

        return Optional.of(toTransferResponseDto(tx));
    }

    /**
     * Important :
     * Le client recherche par UETR, mais plusieurs messages peuvent porter le même UETR :
     * PACS008, CAMT056, CAMT029...
     * Pour l'espace client, on affiche toujours la transaction métier originale, donc PACS008.
     */
    private Optional<SwiftMessage> findClientOriginalTransactionByUetr(String uetr, String clientEmail) {
        if (uetr == null || uetr.isBlank()) {
            return Optional.empty();
        }

        return swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> uetr.equals(m.getUetr()))
                .filter(this::isClientVisibleTransaction)
                .filter(m -> clientEmail == null || m.getClientEmail() == null || clientEmail.equals(m.getClientEmail()))
                .findFirst();
    }

    private boolean isClientVisibleTransaction(SwiftMessage message) {
        return "PACS008".equals(message.getMessageType());
    }

// ==================== TIMELINE CLIENT SIMPLIFIÉE ====================

    public List<TransactionTimelineDto> getTransactionTimeline(Long id, String clientEmail) {
        Optional<SwiftMessage> opt = swiftMessageRepository.findById(id);
        if (opt.isEmpty()) return Collections.emptyList();

        SwiftMessage msg = opt.get();

        // Vérifier que le client a accès à cette transaction
        if (clientEmail != null && msg.getClientEmail() != null && !clientEmail.equals(msg.getClientEmail())) {
            return Collections.emptyList();
        }

        List<TransactionTimelineDto> timeline = new ArrayList<>();

        // Étape 1 : Réception (toujours présente)
        TransactionTimelineDto step1 = new TransactionTimelineDto();
        step1.setStatus("RECEIVED");
        step1.setStatusLabel("Reçue");
        step1.setDescription("Votre transfert a été reçu par notre système.");
        step1.setTimestamp(msg.getReceivedAt());
        step1.setCompleted(true);
        timeline.add(step1);

        // Étape 2 : Décision (Acceptée ou Rejetée)
        TransactionTimelineDto step2 = new TransactionTimelineDto();

        if ("REJETE".equals(msg.getStatus())) {
            // Transaction rejetée
            step2.setStatus("REJECTED");
            step2.setStatusLabel("Rejetée");
            String reason = msg.getRejectionReason() != null ? msg.getRejectionReason() : "Non spécifié";
            step2.setDescription("Votre transfert a été rejeté. Motif : " + reason);
            step2.setTimestamp(msg.getValidatedAt() != null ? msg.getValidatedAt() : msg.getReceivedAt());
            step2.setCompleted(true);
            timeline.add(step2);

        } else if ("ANNULEE".equals(msg.getStatus())) {
            // Transaction annulée (après acceptation)
            step2.setStatus("ACCEPTED");
            step2.setStatusLabel("Acceptée");
            step2.setDescription("Votre transfert a été accepté par notre équipe.");
            step2.setTimestamp(msg.getValidatedAt() != null ? msg.getValidatedAt() : msg.getReceivedAt());
            step2.setCompleted(true);
            timeline.add(step2);

            // Étape 3 : Annulation
            TransactionTimelineDto step3 = new TransactionTimelineDto();
            step3.setStatus("CANCELLED");
            step3.setStatusLabel("Annulée");
            step3.setDescription("Votre transfert a été annulé suite à votre demande.");
            step3.setTimestamp(msg.getArchivedAt() != null ? msg.getArchivedAt() : msg.getReceivedAt());
            step3.setCompleted(true);
            timeline.add(step3);

        } else if ("ACCEPTE".equals(msg.getStatus())) {
            // Transaction acceptée (finale)
            step2.setStatus("ACCEPTED");
            step2.setStatusLabel("Acceptée");
            step2.setDescription("Votre transfert a été accepté et sera traité par notre réseau bancaire.");
            step2.setTimestamp(msg.getValidatedAt() != null ? msg.getValidatedAt() : msg.getReceivedAt());
            step2.setCompleted(true);
            timeline.add(step2);

        } else if ("ANNULATION_EN_ATTENTE".equals(msg.getStatus())) {
            // Annulation en attente
            step2.setStatus("ACCEPTED");
            step2.setStatusLabel("Acceptée");
            step2.setDescription("Votre transfert a été accepté par notre équipe.");
            step2.setTimestamp(msg.getValidatedAt() != null ? msg.getValidatedAt() : msg.getReceivedAt());
            step2.setCompleted(true);
            timeline.add(step2);

            // Étape 3 : Annulation en attente
            TransactionTimelineDto step3 = new TransactionTimelineDto();
            step3.setStatus("CANCELLATION_PENDING");
            step3.setStatusLabel("Annulation en cours");
            step3.setDescription("Votre demande d'annulation a été envoyée. En attente de confirmation.");
            step3.setTimestamp(msg.getValidatedAt() != null ? msg.getValidatedAt() : msg.getReceivedAt());
            step3.setCompleted(false);
            timeline.add(step3);

        } else {
            // En attente de décision
            step2.setStatus("PENDING");
            step2.setStatusLabel("En attente");
            step2.setDescription("Votre transfert est en cours d'analyse par notre équipe.");
            step2.setTimestamp(null);
            step2.setCompleted(false);
            timeline.add(step2);
        }

        return timeline;
    }

    public Map<String, String> getRejectionReason(Long id, String clientEmail) {
        Optional<SwiftMessage> opt = swiftMessageRepository.findById(id);
        if (opt.isEmpty()) {
            return Map.of();
        }

        SwiftMessage msg = opt.get();

        if (msg.getClientEmail() != null && !msg.getClientEmail().equals(clientEmail)) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        result.put("rejectionReason", msg.getRejectionReason() != null ? msg.getRejectionReason() : "");
        result.put("rejectedAt", msg.getValidatedAt() != null ? msg.getValidatedAt().toString() : "");
        result.put("rejectedBy", msg.getValidatedBy() != null ? msg.getValidatedBy() : "");
        return result;
    }

    // ==================== DASHBOARD ====================

    public ClientDashboardDto getClientDashboard(String clientEmail) {
        List<SwiftMessage> messages = swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> clientEmail != null && clientEmail.equals(m.getClientEmail()))
                .filter(this::isClientVisibleTransaction)
                .collect(Collectors.toList());

        log.info("Dashboard client {} : {} transaction(s)", clientEmail, messages.size());

        ClientDashboardDto dto = new ClientDashboardDto();
        dto.setTotalTransactions((long) messages.size());

        dto.setPendingTransactions(messages.stream()
                .filter(m -> STATUS_PENDING.equals(m.getStatus()))
                .count());

        dto.setAcceptedTransactions(messages.stream()
                .filter(m -> STATUS_ACCEPTED.equals(m.getStatus())
                        || STATUS_CANCEL_PENDING.equals(m.getStatus())
                        || STATUS_CANCELLED.equals(m.getStatus()))
                .count());

        dto.setRejectedTransactions(messages.stream()
                .filter(m -> STATUS_REJECTED.equals(m.getStatus()))
                .count());

        dto.setTotalAmount(messages.stream()
                .map(SwiftMessage::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        dto.setAverageProcessingTimeHours(0.0);

        dto.setRecentTransactions(messages.stream().limit(5).map(m -> {
            RecentTransactionDto recentDto = new RecentTransactionDto();
            recentDto.setId(m.getId());
            recentDto.setMsgId(m.getMsgId());
            recentDto.setUetr(m.getUetr());
            recentDto.setStatus(m.getStatus());
            recentDto.setAmount(m.getAmount());
            recentDto.setCurrency(m.getCurrency());
            recentDto.setCreditorName(m.getCreditorName());
            recentDto.setDebtorName(m.getDebtorName());
            recentDto.setReceivedAt(m.getReceivedAt());
            recentDto.setDebtorCountry(m.getDebtorCountry());
            recentDto.setCreditorCountry(m.getCreditorCountry());
            recentDto.setAlerte(m.getAlerte());
            recentDto.setMotifAlerte(m.getMotifAlerte());
            recentDto.setMessageType(m.getMessageType());
            recentDto.setAgentValidated(m.getAgentValidated());
            recentDto.setRejectionReason(m.getRejectionReason());
            return recentDto;
        }).collect(Collectors.toList()));

        dto.setStatusDistribution(messages.stream()
                .collect(Collectors.groupingBy(SwiftMessage::getStatus, Collectors.counting())));

        return dto;
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
        dto.setDebtorCountry(message.getDebtorCountry());
        dto.setCreditorCountry(message.getCreditorCountry());
        dto.setStatus(message.getStatus());
        dto.setCreatedAt(message.getReceivedAt());
        dto.setUpdatedAt(message.getValidatedAt() != null ? message.getValidatedAt() : message.getReceivedAt());
        dto.setRejectionReason(message.getRejectionReason());
        dto.setAlerte(message.getAlerte());
        dto.setMotifAlerte(message.getMotifAlerte());
        dto.setCancellationReason(message.getCancellationReason());
        dto.setCancellationReasonText(message.getCancellationReasonText());
        dto.setCancellationStatus(message.getCancellationStatus());

        List<BankJourneyDto> journey = bankJourneyService.getBankJourneyByUetr(message.getUetr(), message.getClientEmail());
        dto.setBankJourney(journey);
        dto.setTotalFees(calculateTotalFeesFromSteps(journey));
        dto.setNetAmount(calculateNetAmountFromSteps(message, journey));

        return dto;
    }

    private Double calculateTotalFeesFromSteps(List<BankJourneyDto> journey) {
        double total = 0.0;

        if (journey == null) {
            return total;
        }

        for (BankJourneyDto step : journey) {
            if (step.getFees() != null && !step.getFees().isBlank()) {
                try {
                    String feesStr = step.getFees().replace(",", ".").replaceAll("[^0-9.]", "");
                    if (!feesStr.isBlank()) {
                        total += Double.parseDouble(feesStr);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        return total;
    }

    private Double calculateNetAmountFromSteps(SwiftMessage message, List<BankJourneyDto> journey) {
        double amount = message.getAmount() != null ? message.getAmount().doubleValue() : 0.0;
        double totalFees = calculateTotalFeesFromSteps(journey);
        double net = amount - totalFees;

        if ("EUR".equals(message.getCurrency()) && net > 0) {
            net = net * 1.09;
        }

        return Math.max(net, 0.0);
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

        Optional<SwiftMessage> msg = findClientOriginalTransactionByUetr(
                consultation.getUetr(),
                consultation.getClientEmail()
        );

        if (msg.isPresent()) {
            SwiftMessage m = msg.get();
            dto.setStatus(m.getStatus());
            dto.setAmount(m.getAmount());
            dto.setCurrency(m.getCurrency());
            dto.setUpdatedAt(m.getValidatedAt() != null ? m.getValidatedAt() : m.getReceivedAt());
        } else {
            dto.setStatus(STATUS_PENDING);
            dto.setAmount(BigDecimal.ZERO);
            dto.setCurrency("EUR");
            dto.setUpdatedAt(consultation.getConsultedAt());
        }

        return dto;
    }

    // ==================== FILTRAGE ====================

    public List<SwiftMessage> filterClientTransactions(String clientEmail,
                                                       LocalDateTime startDate,
                                                       LocalDateTime endDate,
                                                       BigDecimal minAmount,
                                                       BigDecimal maxAmount,
                                                       String status,
                                                       String creditorCountry) {
        return swiftMessageRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(m -> clientEmail != null && clientEmail.equals(m.getClientEmail()))
                .filter(this::isClientVisibleTransaction)
                .filter(m -> startDate == null || (m.getReceivedAt() != null && m.getReceivedAt().isAfter(startDate)))
                .filter(m -> endDate == null || (m.getReceivedAt() != null && m.getReceivedAt().isBefore(endDate)))
                .filter(m -> minAmount == null || (m.getAmount() != null && m.getAmount().compareTo(minAmount) >= 0))
                .filter(m -> maxAmount == null || (m.getAmount() != null && m.getAmount().compareTo(maxAmount) <= 0))
                .filter(m -> status == null || status.isBlank() || (m.getStatus() != null && m.getStatus().equals(status)))
                .filter(m -> creditorCountry == null || creditorCountry.isBlank()
                        || (m.getCreditorCountry() != null && m.getCreditorCountry().equals(creditorCountry)))
                .collect(Collectors.toList());
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
        List<ClientConsultation> consultations =
                clientConsultationRepository.findByClientEmailOrderByConsultedAtDesc(clientEmail);

        int count = consultations.size();

        if (count > 0) {
            clientConsultationRepository.deleteAll(consultations);
        }

        return count;
    }

    // ==================== EXPORTS ====================

    public void exportTransactionsPdf(HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=transactions.pdf");
            response.getWriter().write("Export PDF - À implémenter");
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("Erreur export PDF : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'export PDF", e);
        }
    }

    public void exportTransactionsExcel(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=transactions.xlsx");
            response.getWriter().write("Export Excel - À implémenter");
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("Erreur export Excel : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'export Excel", e);
        }
    }
}
