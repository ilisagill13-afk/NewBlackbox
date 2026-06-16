"""Notification helpers (Telegram + Email)."""

import logging
import smtplib
from email.mime.text import MIMEText

import requests

import config

logger = logging.getLogger(__name__)


def _send_telegram(message: str) -> None:
    if not config.TELEGRAM_BOT_TOKEN or not config.TELEGRAM_CHAT_ID:
        logger.warning("Telegram credentials not configured — skipping.")
        return
    url = f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {"chat_id": config.TELEGRAM_CHAT_ID, "text": message, "parse_mode": "HTML"}
    try:
        resp = requests.post(url, json=payload, timeout=10)
        resp.raise_for_status()
        logger.info("Telegram notification sent.")
    except Exception as exc:  # noqa: BLE001
        logger.error("Telegram send failed: %s", exc)


def _send_email(subject: str, body: str) -> None:
    if not config.SMTP_USER or not config.NOTIFY_EMAIL_TO:
        logger.warning("Email credentials not configured — skipping.")
        return
    msg = MIMEText(body, "plain")
    msg["Subject"] = subject
    msg["From"] = config.SMTP_USER
    msg["To"] = config.NOTIFY_EMAIL_TO
    try:
        with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT) as server:
            server.starttls()
            server.login(config.SMTP_USER, config.SMTP_PASSWORD)
            server.sendmail(config.SMTP_USER, [config.NOTIFY_EMAIL_TO], msg.as_string())
        logger.info("Email notification sent to %s.", config.NOTIFY_EMAIL_TO)
    except Exception as exc:  # noqa: BLE001
        logger.error("Email send failed: %s", exc)


def notify(subject: str, body: str) -> None:
    """Send a notification via the configured method(s)."""
    method = config.NOTIFY_METHOD.lower()
    if method in ("telegram", "both"):
        _send_telegram(f"<b>{subject}</b>\n\n{body}")
    if method in ("email", "both"):
        _send_email(subject, body)
    if method == "none":
        logger.info("[Notification skipped — NOTIFY_METHOD=none] %s | %s", subject, body)
