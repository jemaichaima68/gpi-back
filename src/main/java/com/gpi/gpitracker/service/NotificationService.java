package com.gpi.gpitracker.service;

import com.gpi.gpitracker.dto.ClientNotificationDto;
import com.gpi.gpitracker.entity.ClientNotification;
import com.gpi.gpitracker.repository.ClientNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ClientNotificationRepository clientNotificationRepository;

    public List<ClientNotificationDto> getCurrentClientNotifications(String clientEmail) {
        return clientNotificationRepository.findByClientEmailOrderByCreatedAtDesc(clientEmail)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markNotificationAsRead(Long id, String clientEmail) {
        clientNotificationRepository.findByIdAndClientEmail(id, clientEmail)
                .ifPresent(notification -> notification.setRead(true));
    }

    @Transactional
    public void markAllNotificationsAsRead(String clientEmail) {
        clientNotificationRepository.findByClientEmailAndReadFalseOrderByCreatedAtDesc(clientEmail)
                .forEach(notification -> notification.setRead(true));
    }

    public void createNotification(String clientEmail, String title, String message, String type, String uetr) {
        if (clientEmail == null || clientEmail.isBlank()) return;

        ClientNotification notification = new ClientNotification();
        notification.setClientEmail(clientEmail);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notification.setUetr(uetr);
        clientNotificationRepository.save(notification);
    }

    public void notifyStatusChange(String clientEmail, String debtorName, String uetr, String normalizedStatus, String details) {
        String title = switch (normalizedStatus) {
            case "ACTC" -> "Transfert validé";
            case "ACSP" -> "Transfert en cours";
            case "ACSC" -> "Transfert finalisé";
            case "RJCT" -> "Transfert rejeté";
            default -> "Mise à jour du transfert";
        };

        String message = switch (normalizedStatus) {
            case "ACTC" -> "Votre transfert a été validé. Votre UETR est maintenant actif.";
            case "ACSP" -> "Votre transfert est en cours de règlement.";
            case "ACSC" -> "Votre transfert a été finalisé avec succès.";
            case "RJCT" -> "Votre transfert a été rejeté." + (details != null && !details.isBlank() ? " Motif: " + details : "");
            default -> "Le statut de votre transfert a été mis à jour.";
        };

        createNotification(clientEmail, title, message, mapType(normalizedStatus), uetr);
    }

    private String mapType(String normalizedStatus) {
        return switch (normalizedStatus) {
            case "ACTC", "ACSP", "ACSC" -> "success";
            case "RJCT" -> "error";
            case "PDNG" -> "warning";
            default -> "info";
        };
    }

    public long getUnreadCount(String clientEmail) {
        return clientNotificationRepository.countByClientEmailAndReadFalse(clientEmail);
    }

    private ClientNotificationDto toDto(ClientNotification notification) {
        return new ClientNotificationDto(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getType(),
                notification.getRead(),
                notification.getUetr()
        );
    }
}