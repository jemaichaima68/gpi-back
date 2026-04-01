package com.gpi.gpitracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

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

    public void sendWelcomeEmail(String toEmail, String firstName,
                                 String username, String password) {
        System.out.println(">>> Envoi email à : " + toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8"
            );
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur " + appName +
                    " — Vos identifiants de connexion");
            helper.setText(buildEmailHtml(
                    firstName, username, password
            ), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur envoi email : " + e.getMessage()
            );
        }
    }

    private String buildEmailHtml(String firstName,
                                  String username,
                                  String password) {
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
                        Pour votre sécurité, changez votre mot de passe 
                        lors de votre première connexion.
                    </div>
                </div>
                <div class="footer">© 2026 GPI Tracker</div>
            </div>
            </body>
            </html>
            """.formatted(firstName, username, password, frontendUrl);
    }
}