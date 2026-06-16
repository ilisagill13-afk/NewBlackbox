"""
Configuration for US Visa Slot Scheduler (Canada)
Edit the values here or set them as environment variables.
"""

import os

# ──────────────────────────────────────────────
# AIS Credentials
# ──────────────────────────────────────────────
AIS_EMAIL = os.environ.get("AIS_EMAIL", "your_email@example.com")
AIS_PASSWORD = os.environ.get("AIS_PASSWORD", "your_password")

# ──────────────────────────────────────────────
# Appointment Details
# ──────────────────────────────────────────────
# Country code for Canada
COUNTRY_CODE = "en-ca"

# Consulate city (options: calgary, halifax, montreal, ottawa, quebec_city,
#                          toronto, vancouver, victoria)
CONSULATE = os.environ.get("CONSULATE", "toronto")

# Consulate facility IDs (mapped from city names)
FACILITY_IDS = {
    "calgary":      89,
    "halifax":      90,
    "montreal":     91,
    "ottawa":       92,
    "quebec_city":  93,
    "toronto":      94,
    "vancouver":    95,
    "victoria":     96,
}

FACILITY_ID = FACILITY_IDS.get(CONSULATE, 94)

# Your current appointment date (YYYY-MM-DD). The scheduler will only book a
# slot EARLIER than this date. Set to a far-future date to accept any slot.
CURRENT_APPOINTMENT_DATE = os.environ.get("CURRENT_APPOINTMENT_DATE", "2026-12-31")

# Earliest date you can attend (YYYY-MM-DD)
EARLIEST_DATE = os.environ.get("EARLIEST_DATE", "2026-06-17")

# ──────────────────────────────────────────────
# Scheduling Parameters
# ──────────────────────────────────────────────
# How often to poll for slots (seconds). Be respectful — don't go below 30s.
POLL_INTERVAL_SECONDS = int(os.environ.get("POLL_INTERVAL_SECONDS", "60"))

# How many consecutive failures before giving up
MAX_RETRIES = int(os.environ.get("MAX_RETRIES", "10"))

# Base URL for the AIS system
BASE_URL = "https://ais.usvisa-info.com"

# API endpoint paths
SIGN_IN_URL = f"{BASE_URL}/{COUNTRY_CODE}/niv/users/sign_in"
APPOINTMENTS_URL = f"{BASE_URL}/{COUNTRY_CODE}/niv/schedule/{{schedule_id}}/appointment"
AVAILABLE_DATES_URL = (
    f"{BASE_URL}/{COUNTRY_CODE}/niv/schedule/{{schedule_id}}/appointment/days/"
    f"{FACILITY_ID}.json?appointments[expedite]=false"
)
AVAILABLE_TIMES_URL = (
    f"{BASE_URL}/{COUNTRY_CODE}/niv/schedule/{{schedule_id}}/appointment/times/"
    f"{FACILITY_ID}.json?date={{date}}&appointments[expedite]=false"
)

# ──────────────────────────────────────────────
# Notifications
# ──────────────────────────────────────────────
# Set NOTIFY_METHOD to "telegram", "email", "both", or "none"
NOTIFY_METHOD = os.environ.get("NOTIFY_METHOD", "none")

# Telegram (BotFather token + chat ID)
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")

# Email (SMTP)
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
NOTIFY_EMAIL_TO = os.environ.get("NOTIFY_EMAIL_TO", "")

# ──────────────────────────────────────────────
# Browser / Headless
# ──────────────────────────────────────────────
HEADLESS = os.environ.get("HEADLESS", "true").lower() == "true"
# Set USER_AGENT to mimic a real browser
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/125.0.0.0 Safari/537.36"
)
