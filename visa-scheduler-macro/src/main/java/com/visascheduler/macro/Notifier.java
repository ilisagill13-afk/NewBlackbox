package com.visascheduler.macro;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Sends a text + optional screenshot attachment via Telegram and/or email. */
public final class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    private Notifier() {}

    public static void notify(String subject, String body, Path screenshot) {
        switch (MacroConfig.NOTIFY_METHOD.toLowerCase()) {
            case "telegram" -> sendTelegram(subject, body, screenshot);
            case "email"    -> sendEmail(subject, body, screenshot);
            case "both"     -> { sendTelegram(subject, body, screenshot); sendEmail(subject, body, screenshot); }
            default -> log.info("[Notification skipped — NOTIFY_METHOD=none] {} | {}", subject, body);
        }
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    private static void sendTelegram(String subject, String body, Path screenshot) {
        if (MacroConfig.TELEGRAM_BOT_TOKEN.isBlank() || MacroConfig.TELEGRAM_CHAT_ID.isBlank()) {
            log.warn("Telegram credentials not configured — skipping.");
            return;
        }
        try {
            if (screenshot != null && Files.exists(screenshot)) {
                sendTelegramPhoto(subject + "\n\n" + body, screenshot);
            } else {
                sendTelegramText(subject + "\n\n" + body);
            }
        } catch (Exception e) {
            log.error("Telegram send failed: {}", e.getMessage());
        }
    }

    private static void sendTelegramText(String text) throws Exception {
        String url = "https://api.telegram.org/bot" + MacroConfig.TELEGRAM_BOT_TOKEN + "/sendMessage";
        String json = String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\"}",
                MacroConfig.TELEGRAM_CHAT_ID,
                text.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) log.info("Telegram text alert sent.");
        else log.error("Telegram text send failed — HTTP {}: {}", resp.statusCode(), resp.body());
    }

    private static void sendTelegramPhoto(String caption, Path imagePath) throws Exception {
        String boundary = "----VisaMacroBoundary" + System.currentTimeMillis();
        byte[] imageBytes = Files.readAllBytes(imagePath);
        String CRLF = "\r\n";

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + CRLF).getBytes());
        body.write(("Content-Disposition: form-data; name=\"chat_id\"" + CRLF + CRLF).getBytes());
        body.write((MacroConfig.TELEGRAM_CHAT_ID + CRLF).getBytes());

        body.write(("--" + boundary + CRLF).getBytes());
        body.write(("Content-Disposition: form-data; name=\"caption\"" + CRLF + CRLF).getBytes());
        body.write((caption + CRLF).getBytes());

        body.write(("--" + boundary + CRLF).getBytes());
        body.write(("Content-Disposition: form-data; name=\"photo\"; filename=\""
                + imagePath.getFileName() + "\"" + CRLF).getBytes());
        body.write(("Content-Type: image/png" + CRLF + CRLF).getBytes());
        body.write(imageBytes);
        body.write((CRLF + "--" + boundary + "--" + CRLF).getBytes());

        String url = "https://api.telegram.org/bot" + MacroConfig.TELEGRAM_BOT_TOKEN + "/sendPhoto";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) log.info("Telegram screenshot alert sent.");
        else log.error("Telegram photo send failed — HTTP {}: {}", resp.statusCode(), resp.body());
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private static void sendEmail(String subject, String body, Path screenshot) {
        if (MacroConfig.SMTP_USER.isBlank() || MacroConfig.NOTIFY_EMAIL_TO.isBlank()) {
            log.warn("Email credentials not configured — skipping.");
            return;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth",            "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host",            MacroConfig.SMTP_HOST);
            props.put("mail.smtp.port",            String.valueOf(MacroConfig.SMTP_PORT));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(MacroConfig.SMTP_USER, MacroConfig.SMTP_PASSWORD);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(MacroConfig.SMTP_USER));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(MacroConfig.NOTIFY_EMAIL_TO));
            msg.setSubject(subject);

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(body);

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);

            if (screenshot != null && Files.exists(screenshot)) {
                MimeBodyPart imagePart = new MimeBodyPart();
                byte[] data = Files.readAllBytes(screenshot);
                imagePart.setDataHandler(new jakarta.activation.DataHandler(
                        new ByteArrayDataSource(data, "image/png")));
                imagePart.setFileName(screenshot.getFileName().toString());
                multipart.addBodyPart(imagePart);
            }

            msg.setContent(multipart);
            Transport.send(msg);
            log.info("Email alert sent to {}.", MacroConfig.NOTIFY_EMAIL_TO);
        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
        }
    }
}
