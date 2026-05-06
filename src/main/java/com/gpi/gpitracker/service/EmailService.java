package com.gpi.gpitracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.name}")
    private String appName;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ==================== EMAIL BIENVENUE ====================
    public void sendWelcomeEmail(String toEmail, String firstName,
                                 String username, String password) {
        log.info(">>> Envoi email de bienvenue à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur " + appName + " - Vos identifiants de connexion");
            helper.setText(buildWelcomeEmailHtml(firstName, username, password), true);
            mailSender.send(message);
            log.info("✅ Email de bienvenue envoyé avec succès à {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Erreur envoi email de bienvenue à {}: {}", toEmail, e.getMessage());
        }
    }

    // ==================== EMAIL TRANSACTION REÇUE ====================
    @Async
    public CompletableFuture<Void> sendTransactionReceivedEmail(
            String toEmail, String clientName, String uetr, BigDecimal amount, String currency) {

        log.info(">>> Envoi email transaction reçue à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Confirmation de reception de votre transaction SWIFT");
            helper.setText(buildTransactionReceivedHtml(clientName, uetr, amount, currency), true);
            mailSender.send(message);
            log.info("✅ Email transaction reçue envoyé avec succès à {}", toEmail);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("❌ Erreur envoi email transaction reçue à {}: {}", toEmail, e.getMessage());
            CompletableFuture<Void> failure = new CompletableFuture<>();
            failure.completeExceptionally(e);
            return failure;
        }
    }

    // ==================== EMAIL TRANSACTION ACCEPTÉE (VERSION SYNCHRONE) ====================
    public void sendTransactionAcceptedEmailSync(
            String toEmail, String clientName, String uetr, BigDecimal amount, String currency) {

        log.info(">>> Envoi email acceptation (SYNC) à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Decision : Transaction acceptee");
            helper.setText(buildTransactionAcceptedHtml(clientName, uetr, amount, currency), true);
            mailSender.send(message);
            log.info("✅ Email acceptation envoyé avec succès à {}", toEmail);
        } catch (Exception e) {
            log.error("❌ ERREUR CRITIQUE - Échec envoi email acceptation à {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email d'acceptation: " + e.getMessage(), e);
        }
    }

    // ==================== EMAIL TRANSACTION REJETÉE (VERSION SYNCHRONE) ====================
    public void sendTransactionRejectedEmailSync(
            String toEmail, String clientName, String uetr, String rejectionReason) {

        log.info(">>> Envoi email rejet (SYNC) à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Decision : Transaction non validee");
            helper.setText(buildTransactionRejectedHtml(clientName, uetr, rejectionReason), true);
            mailSender.send(message);
            log.info("✅ Email rejet envoyé avec succès à {}", toEmail);
        } catch (Exception e) {
            log.error("❌ ERREUR CRITIQUE - Échec envoi email rejet à {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de rejet: " + e.getMessage(), e);
        }
    }

    // ==================== EMAIL TRANSACTION ACCEPTÉE (ASYNC - gardée pour compatibilité) ====================
    @Async
    public CompletableFuture<Void> sendTransactionAcceptedEmail(
            String toEmail, String clientName, String uetr, BigDecimal amount, String currency) {

        log.info(">>> Envoi email acceptation (ASYNC) à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Decision : Transaction acceptee");
            helper.setText(buildTransactionAcceptedHtml(clientName, uetr, amount, currency), true);
            mailSender.send(message);
            log.info("✅ Email acceptation envoyé avec succès à {}", toEmail);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("❌ Erreur envoi email acceptation à {}: {}", toEmail, e.getMessage());
            CompletableFuture<Void> failure = new CompletableFuture<>();
            failure.completeExceptionally(e);
            return failure;
        }
    }

    // ==================== EMAIL TRANSACTION REJETÉE (ASYNC - gardée pour compatibilité) ====================
    @Async
    public CompletableFuture<Void> sendTransactionRejectedEmail(
            String toEmail, String clientName, String uetr, String rejectionReason) {

        log.info(">>> Envoi email rejet (ASYNC) à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Decision : Transaction non validee");
            helper.setText(buildTransactionRejectedHtml(clientName, uetr, rejectionReason), true);
            mailSender.send(message);
            log.info("✅ Email rejet envoyé avec succès à {}", toEmail);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("❌ Erreur envoi email rejet à {}: {}", toEmail, e.getMessage());
            CompletableFuture<Void> failure = new CompletableFuture<>();
            failure.completeExceptionally(e);
            return failure;
        }
    }

    // ==================== MÉTHODES HTML ====================

    private String buildWelcomeEmailHtml(String firstName, String username, String password) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><meta charset='UTF-8'><title>Bienvenue sur GPI Tracker</title>\n" +
                "<style>body{font-family:'Segoe UI',Arial,sans-serif;background-color:#f4f6f9;margin:0;padding:0;}" +
                ".container{max-width:600px;margin:0 auto;background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.05);}" +
                ".header{background-color:#1a4d8c;padding:24px;text-align:center;}" +
                ".header h1{color:#ffffff;margin:0;font-size:24px;font-weight:500;}" +
                ".content{padding:32px;}" +
                ".info-box{background-color:#f8f9fc;border-left:4px solid #1a4d8c;padding:16px;margin:20px 0;}" +
                ".button{display:inline-block;background-color:#1a4d8c;color:#ffffff;padding:12px 28px;text-decoration:none;border-radius:4px;font-weight:500;margin-top:16px;}" +
                ".footer{background-color:#f4f6f9;padding:16px;text-align:center;font-size:12px;color:#888888;border-top:1px solid #e0e4e8;}" +
                ".warning{background-color:#fff8e1;padding:12px;border-radius:4px;font-size:13px;color:#856404;margin:16px 0;}</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class='container'>\n" +
                "<div class='header'><h1>GPI Tracker</h1></div>\n" +
                "<div class='content'>\n" +
                "<p>Bonjour <strong>" + firstName  + "</strong>,</p>\n" +
                "<p>Nous vous confirmons la creation de votre compte sur la plateforme GPI Tracker.</p>\n" +
                "<div class='info-box'>\n" +
                "<p><strong>Nom d'utilisateur :</strong> " + username + "</p>\n" +
                "<p><strong>Mot de passe temporaire :</strong> " + password + "</p>\n" +
                "</div>\n" +
                "<div style='text-align:center;'><a href='" + frontendUrl + "' class='button'>Acceder a la plateforme</a></div>\n" +
                "<div class='warning'>Pour des raisons de securite, nous vous recommandons de modifier votre mot de passe lors de votre premiere connexion.</div>\n" +
                "</div>\n" +
                "<div class='footer'>\n" +
                "<p>© 2026 GPI Tracker - Tous droits reserves</p>\n" +
                "<p>Cet email est genere automatiquement, merci de ne pas y repondre.</p>\n" +
                "</div>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String buildTransactionReceivedHtml(String clientName, String uetr, BigDecimal amount, String currency) {
        String amountStr = amount != null ? amount.toPlainString() : "0";
        String currencyStr = currency != null ? currency : "EUR";
        String uetrStr = uetr != null ? uetr : "En cours de generation";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><meta charset='UTF-8'><title>Confirmation de reception de transaction</title>\n" +
                "<style>body{font-family:'Segoe UI',Arial,sans-serif;background-color:#f4f6f9;margin:0;padding:0;}" +
                ".container{max-width:600px;margin:0 auto;background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.05);}" +
                ".header{background-color:#1a4d8c;padding:24px;text-align:center;}" +
                ".header h1{color:#ffffff;margin:0;font-size:24px;font-weight:500;}" +
                ".content{padding:32px;}" +
                ".info-box{background-color:#f8f9fc;border-left:4px solid #1a4d8c;padding:20px;margin:20px 0;}" +
                ".info-row{margin:12px 0;}" +
                ".status{display:inline-block;background-color:#fef3c7;color:#856404;padding:4px 12px;border-radius:4px;font-size:13px;font-weight:500;margin-top:8px;}" +
                ".footer{background-color:#f4f6f9;padding:16px;text-align:center;font-size:12px;color:#888888;border-top:1px solid #e0e4e8;}</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class='container'>\n" +
                "<div class='header'><h1>GPI Tracker</h1></div>\n" +
                "<div class='content'>\n" +
                "<p>Bonjour <strong>" + clientName + "</strong>,</p>\n" +
                "<p>Nous vous confirmons la reception de votre transaction SWIFT sur notre plateforme.</p>\n" +
                "<div class='info-box'>\n" +
                "<div class='info-row'><strong>UETR :</strong> " + uetrStr + "</div>\n" +
                "<div class='info-row'><strong>Montant :</strong> " + amountStr + " " + currencyStr + "</div>\n" +
                "</div>\n" +
                "<div><span class='status'>Statut : En attente de validation</span></div>\n" +
                "<p style='margin-top:24px;'>Notre equipe procede a la validation de votre transaction. Vous serez informe des qu'une decision sera prise.</p>\n" +
                "</div>\n" +
                "<div class='footer'>\n" +
                "<p>© 2026 GPI Tracker - Tous droits reserves</p>\n" +
                "<p>Cet email est genere automatiquement, merci de ne pas y repondre.</p>\n" +
                "</div>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String buildTransactionAcceptedHtml(String clientName, String uetr, BigDecimal amount, String currency) {
        String amountStr = amount != null ? amount.toPlainString() : "0";
        String currencyStr = currency != null ? currency : "EUR";
        String uetrStr = uetr != null ? uetr : "";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><meta charset='UTF-8'><title>Decision sur votre transaction</title>\n" +
                "<style>\n" +
                "body{font-family:'Segoe UI',Arial,sans-serif;background-color:#f4f6f9;margin:0;padding:0;line-height:1.5;}\n" +
                ".container{max-width:600px;margin:20px auto;background-color:#ffffff;border-radius:4px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);}\n" +
                ".header{background-color:#2c3e50;padding:28px 24px;border-bottom:1px solid #eaeef2;}\n" +
                ".header h1{color:#ffffff;margin:0;font-size:22px;font-weight:500;}\n" +
                ".content{padding:32px 28px;}\n" +
                ".greeting{font-size:16px;color:#2c3e50;margin-bottom:24px;}\n" +
                ".info-box{background-color:#f8f9fa;padding:20px;margin:24px 0;border-left:3px solid #7f8c8d;}\n" +
                ".info-row{margin:12px 0;font-size:14px;}\n" +
                ".info-row strong{min-width:140px;display:inline-block;color:#2c3e50;}\n" +
                ".footer{background-color:#f8f9fa;padding:20px;text-align:center;font-size:12px;color:#95a5a6;border-top:1px solid #eaeef2;}\n" +
                ".signature{margin-top:32px;padding-top:16px;border-top:1px solid #eaeef2;font-size:13px;color:#555;}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class='container'>\n" +
                "<div class='header'>\n" +
                "<h1>" + appName + "</h1>\n" +
                "</div>\n" +
                "<div class='content'>\n" +
                "<div class='greeting'>Bonjour <strong>" + clientName + "</strong>,</div>\n" +
                "<p>Nous vous informons que votre transaction SWIFT a ete <strong>acceptee</strong> par notre service apres verification des elements transmis.</p>\n" +
                "<div class='info-box'>\n" +
                "<div class='info-row'><strong>Reference UETR :</strong> " + uetrStr + "</div>\n" +
                "<div class='info-row'><strong>Montant :</strong> " + amountStr + " " + currencyStr + "</div>\n" +
                "</div>\n" +
                "<p>Le transfert a ete transmis au reseau SWIFT pour execution. Le beneficiaire sera credite conformement aux delais standards applicables aux virements internationaux.</p>\n" +
                "<p>Pour suivre l'avancement de votre transaction, vous pouvez utiliser la reference UETR ci-dessus sur notre plateforme de suivi.</p>\n" +
                "<div class='signature'>\n" +
                "<p>Cordialement,<br><strong>Service des Operations Bancaires</strong></p>\n" +
                "</div>\n" +
                "</div>\n" +
                "<div class='footer'>\n" +
                "<p>Cet email est une notification automatique. Merci de ne pas y repondre.</p>\n" +
                "<p>© 2026 " + appName + " - Tous droits reserves</p>\n" +
                "</div>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String buildTransactionRejectedHtml(String clientName, String uetr, String rejectionReason) {
        String uetrStr = uetr != null ? uetr : "";
        String reasonStr = rejectionReason != null ? rejectionReason : "Non specifie";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><meta charset='UTF-8'><title>Decision sur votre transaction</title>\n" +
                "<style>\n" +
                "body{font-family:'Segoe UI',Arial,sans-serif;background-color:#f4f6f9;margin:0;padding:0;line-height:1.5;}\n" +
                ".container{max-width:600px;margin:20px auto;background-color:#ffffff;border-radius:4px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);}\n" +
                ".header{background-color:#2c3e50;padding:28px 24px;border-bottom:1px solid #eaeef2;}\n" +
                ".header h1{color:#ffffff;margin:0;font-size:22px;font-weight:500;}\n" +
                ".content{padding:32px 28px;}\n" +
                ".greeting{font-size:16px;color:#2c3e50;margin-bottom:24px;}\n" +
                ".info-box{background-color:#f8f9fa;padding:20px;margin:24px 0;border-left:3px solid #7f8c8d;}\n" +
                ".info-row{margin:12px 0;font-size:14px;}\n" +
                ".info-row strong{min-width:140px;display:inline-block;color:#2c3e50;}\n" +
                ".reason-box{background-color:#f8f9fa;border:1px solid #e0e4e8;padding:16px;margin:16px 0;font-size:14px;}\n" +
                ".footer{background-color:#f8f9fa;padding:20px;text-align:center;font-size:12px;color:#95a5a6;border-top:1px solid #eaeef2;}\n" +
                ".signature{margin-top:32px;padding-top:16px;border-top:1px solid #eaeef2;font-size:13px;color:#555;}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class='container'>\n" +
                "<div class='header'>\n" +
                "<h1>" + appName + "</h1>\n" +
                "</div>\n" +
                "<div class='content'>\n" +
                "<div class='greeting'>Bonjour <strong>" + clientName + "</strong>,</div>\n" +
                "<p>Nous faisons suite a votre demande de transfert SWIFT et vous informons que celle-ci n'a pas pu etre validee par notre service.</p>\n" +
                "<div class='info-box'>\n" +
                "<div class='info-row'><strong>Reference UETR :</strong> " + uetrStr + "</div>\n" +
                "</div>\n" +
                "<div class='reason-box'>\n" +
                "<strong>Motif :</strong><br>\n" +
                "<span style='color:#555;'>" + reasonStr + "</span>\n" +
                "</div>\n" +
                "<p>Nous vous invitons a regulieriser les informations mentionnees ci-dessus. Pour toute question relative a cette decision, vous pouvez contacter notre service client a l'adresse support@" + appName.toLowerCase().replace(" ", "") + ".com</p>\n" +
                "<div class='signature'>\n" +
                "<p>Cordialement,<br><strong>Service Conformite</strong></p>\n" +
                "</div>\n" +
                "</div>\n" +
                "<div class='footer'>\n" +
                "<p>Cet email est une notification automatique. Merci de ne pas y repondre.</p>\n" +
                "<p>© 2026 " + appName + " - Tous droits reserves</p>\n" +
                "</div>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
    }
}