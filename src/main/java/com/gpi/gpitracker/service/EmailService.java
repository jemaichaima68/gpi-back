package com.gpi.gpitracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

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

    /**
     * Envoi d'email de bienvenue lors de la création d'un compte
     */
    public void sendWelcomeEmail(String toEmail, String firstName,
                                 String username, String password) {
        log.info(">>> Envoi email de bienvenue à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur " + appName + " — Vos identifiants de connexion");
            helper.setText(buildWelcomeEmailHtml(firstName, username, password), true);
            mailSender.send(message);
            log.info("Email de bienvenue envoyé avec succès à {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur envoi email de bienvenue: {}", e.getMessage());
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    /**
     * Envoi d'email de réinitialisation de mot de passe
     */
    public void sendPasswordResetEmail(String toEmail, String firstName,
                                       String username, String newPassword) {
        log.info(">>> Envoi email de réinitialisation à : {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Réinitialisation de votre mot de passe");
            helper.setText(buildPasswordResetEmailHtml(firstName, username, newPassword), true);
            mailSender.send(message);
            log.info("Email de réinitialisation envoyé avec succès à {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur envoi email réinitialisation: {}", e.getMessage());
            throw new RuntimeException("Erreur envoi email de réinitialisation", e);
        }
    }

    /**
     * Construction du HTML pour l'email de bienvenue
     */
    private String buildWelcomeEmailHtml(String firstName, String username, String password) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8">
            <style>
                body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:20px}
                .container{max-width:600px;margin:0 auto;background:white;border-radius:12px;overflow:hidden}
                .header{background:linear-gradient(135deg,#1a237e,#4a148c);padding:30px;text-align:center}
                .header h1{color:white;margin:0;font-size:24px}
                .header p{color:rgba(255,255,255,0.8);margin:8px 0 0}
                .body{padding:30px}
                .body h2{color:#1a237e}
                .credentials{background:#f8f4ff;border-left:4px solid #4a148c;padding:20px;border-radius:8px;margin:20px 0}
                .credentials p{margin:8px 0;color:#333}
                .credentials strong{color:#4a148c}
                .btn{display:inline-block;background:#4a148c;color:white;padding:14px 30px;border-radius:8px;text-decoration:none;font-weight:bold;margin:20px 0}
                .warning{background:#fff3e0;padding:12px;border-radius:8px;color:#e65100;font-size:13px;margin-top:20px}
                .footer{background:#f5f5f5;padding:20px;text-align:center;color:#999;font-size:12px}
            </style>
            </head>
            <body>
            <div class="container">
                <div class="header">
                    <h1>GPI Tracker</h1>
                    <p>Plateforme de suivi des paiements SWIFT</p>
                </div>
                <div class="body">
                    <h2>Bienvenue, %s !</h2>
                    <p>Votre compte a été créé avec succès. Voici vos identifiants :</p>
                    <div class="credentials">
                        <p>Nom d'utilisateur : <strong>%s</strong></p>
                        <p>Mot de passe temporaire : <strong>%s</strong></p>
                    </div>
                    <a href="%s" class="btn">Accéder à l'application</a>
                    <div class="warning">
                        Pour votre sécurité, changez votre mot de passe lors de votre première connexion.
                    </div>
                </div>
                <div class="footer">© 2026 GPI Tracker</div>
            </div>
            </body>
            </html>
            """.formatted(firstName, username, password, frontendUrl);
    }

    /**
     * Construction du HTML pour l'email de réinitialisation de mot de passe
     */
    private String buildPasswordResetEmailHtml(String firstName, String username, String newPassword) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8">
            <style>
                body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:20px}
                .container{max-width:600px;margin:0 auto;background:white;border-radius:12px;overflow:hidden}
                .header{background:linear-gradient(135deg,#f59e0b,#d97706);padding:30px;text-align:center}
                .header h1{color:white;margin:0;font-size:24px}
                .body{padding:30px}
                .credentials{background:#fef3c7;border-left:4px solid #f59e0b;padding:20px;border-radius:8px;margin:20px 0}
                .credentials p{margin:8px 0;color:#333}
                .credentials strong{color:#d97706}
                .warning{background:#e0e7ff;padding:12px;border-radius:8px;color:#4f46e5;font-size:13px;margin-top:20px}
                .footer{background:#f5f5f5;padding:20px;text-align:center;color:#999;font-size:12px}
                .btn{display:inline-block;background:#f59e0b;color:white;padding:12px 24px;border-radius:8px;text-decoration:none;margin-top:20px}
            </style>
            </head>
            <body>
            <div class="container">
                <div class="header">
                    <h1>🔐 Réinitialisation du mot de passe</h1>
                </div>
                <div class="body">
                    <h2>Bonjour %s,</h2>
                    <p>Votre mot de passe a été réinitialisé par l'administrateur.</p>
                    <div class="credentials">
                        <p>Nom d'utilisateur : <strong>%s</strong></p>
                        <p>Nouveau mot de passe : <strong>%s</strong></p>
                    </div>
                    <div class="warning">
                        ⚠️ Ce mot de passe est temporaire. Veuillez le changer lors de votre prochaine connexion.
                    </div>
                    <div style="text-align:center;">
                        <a href="%s" class="btn">🔑 Se connecter</a>
                    </div>
                </div>
                <div class="footer">© 2026 GPI Tracker</div>
            </div>
            </body>
            </html>
            """.formatted(firstName, username, newPassword, frontendUrl);
    }
}