package com.visascheduler;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

/** Sends notifications via Telegram and/or email. */
public final class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    private Notifier() {}

    /** Send a notification using the configured method(s). */
    public static void notify(String subject, String body) {
        switch (Config.NOTIFY_METHOD.toLowerCase()) {
            case "telegram" -> sendTelegram(subject, body);
            case "email"    -> sendEmail(subject, body);
            case "both"     -> { sendTelegram(subject, body); sendEmail(subject, body); }
            default -> log.info("[Notification skipped — NOTIFY_METHOD={}] {} | {}",
                    Config.NOTIFY_METHOD, subject, body);
        }
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    private static void sendTelegram(String subject, String body) {
        if (Config.TELEGRAM_BOT_TOKEN.isBlank() || Config.TELEGRAM_CHAT_ID.isBlank()) {
            log.warn("Telegram credentials not configured — skipping.");
            return;
        }
        try {
            String text = "<b>" + escapeHtml(subject) + "</b>\n\n" + escapeHtml(body);
            String json = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\"}",
                    Config.TELEGRAM_CHAT_ID,
                    text.replace("\"", "\\\"").replace("\n", "\\n")
            );
            String url = "https://api.telegram.org/bot" + Config.TELEGRAM_BOT_TOKEN + "/sendMessage";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Telegram notification sent.");
            } else {
                log.error("Telegram send failed — HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Telegram send exception: {}", e.getMessage());
        }
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private static void sendEmail(String subject, String body) {
        if (Config.SMTP_USER.isBlank() || Config.NOTIFY_EMAIL_TO.isBlank()) {
            log.warn("Email credentials not configured — skipping.");
            return;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth",            "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host",            Config.SMTP_HOST);
            props.put("mail.smtp.port",            String.valueOf(Config.SMTP_PORT));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(Config.SMTP_USER, Config.SMTP_PASSWORD);
                }
            });

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(Config.SMTP_USER));
            msg.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(Config.NOTIFY_EMAIL_TO));
            msg.setSubject(subject);
            msg.setText(body);
            Transport.send(msg);
            log.info("Email notification sent to {}.", Config.NOTIFY_EMAIL_TO);
        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
