# US Visa Slot Scheduler — Canada

Automatically monitors the [AIS appointment portal](https://ais.usvisa-info.com/en-ca/niv)
for US visa interview slots at Canadian consulates and books the earliest available
slot that is sooner than your current appointment.

---

## Features

- Polls AIS every N seconds for new appointment slots
- Filters dates by your acceptable date range
- Auto-books the earliest available slot
- Notifications via Telegram and/or email
- Graceful retry with exponential back-off
- Full activity log (`visa_scheduler.log`)

---

## Quick Start

### 1. Install dependencies

```bash
cd visa-scheduler
pip install -r requirements.txt
```

### 2. Configure

Edit `config.py` **or** export environment variables:

| Variable | Description | Default |
|---|---|---|
| `AIS_EMAIL` | Your AIS account email | — |
| `AIS_PASSWORD` | Your AIS account password | — |
| `CONSULATE` | Consulate city (see list below) | `toronto` |
| `CURRENT_APPOINTMENT_DATE` | Your existing appointment (YYYY-MM-DD) | `2026-12-31` |
| `EARLIEST_DATE` | Earliest date you can attend (YYYY-MM-DD) | `2026-06-17` |
| `POLL_INTERVAL_SECONDS` | Seconds between polls | `60` |
| `NOTIFY_METHOD` | `telegram`, `email`, `both`, or `none` | `none` |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | — |
| `TELEGRAM_CHAT_ID` | Telegram chat/user ID | — |
| `SMTP_USER` | Gmail address (for email notifications) | — |
| `SMTP_PASSWORD` | Gmail app password | — |
| `NOTIFY_EMAIL_TO` | Recipient email address | — |

#### Available consulates

| City | Value |
|---|---|
| Calgary | `calgary` |
| Halifax | `halifax` |
| Montreal | `montreal` |
| Ottawa | `ottawa` |
| Quebec City | `quebec_city` |
| Toronto | `toronto` |
| Vancouver | `vancouver` |
| Victoria | `victoria` |

### 3. Run

```bash
python main.py
```

With environment variables:

```bash
AIS_EMAIL=you@example.com \
AIS_PASSWORD=secret \
CONSULATE=toronto \
EARLIEST_DATE=2026-07-01 \
CURRENT_APPOINTMENT_DATE=2026-12-01 \
NOTIFY_METHOD=telegram \
TELEGRAM_BOT_TOKEN=<your-bot-token> \
TELEGRAM_CHAT_ID=<your-chat-id> \
python main.py
```

---

## How it Works

1. **Login** — authenticates with your AIS credentials using a persistent HTTP session.
2. **Poll** — every `POLL_INTERVAL_SECONDS` it calls the AIS JSON endpoints:
   - `/appointment/days/<facility_id>.json` — list of available dates
   - `/appointment/times/<facility_id>.json?date=<date>` — time slots for a date
3. **Filter** — keeps only dates between `EARLIEST_DATE` and `CURRENT_APPOINTMENT_DATE`.
4. **Book** — submits the booking form for the earliest qualifying slot.
5. **Notify** — sends a Telegram/email notification on success or failure.

---

## Telegram Setup

1. Message [@BotFather](https://t.me/BotFather) → `/newbot` → get `TELEGRAM_BOT_TOKEN`.
2. Message [@userinfobot](https://t.me/userinfobot) → get your `TELEGRAM_CHAT_ID`.

---

## Notes

- Keep `POLL_INTERVAL_SECONDS` ≥ 30 to avoid rate-limiting by the AIS server.
- The scheduler re-logs in automatically when it detects an authentication error.
- All activity is written to `visa_scheduler.log` in the same directory.
- This tool interacts with your real AIS account — verify the booking in your AIS dashboard after it runs.

---

## Disclaimer

This tool automates interactions with the official AIS portal using your own credentials.
Use it responsibly and in accordance with the AIS terms of service.
