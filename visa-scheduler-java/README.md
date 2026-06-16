# US Visa Slot Scheduler — Canada (Java)

Automatically monitors [ais.usvisa-info.com](https://ais.usvisa-info.com/en-ca/niv)
for US visa interview slots at Canadian consulates and books the earliest available
slot that is sooner than your current appointment.

Built with **Java 17**, Apache HttpClient 5, Jsoup, Jackson, and Logback.

---

## Build

```bash
cd visa-scheduler-java
mvn clean package -q
# Output: target/visa-scheduler-1.0.0.jar  (fat JAR, no extra deps needed)
```

## Run

```bash
java -jar target/visa-scheduler-1.0.0.jar
```

## Configuration

All settings can be provided via **environment variables** (recommended) or edited directly in `Config.java`.

| Variable | Description | Default |
|---|---|---|
| `AIS_EMAIL` | AIS account email | — |
| `AIS_PASSWORD` | AIS account password | — |
| `CONSULATE` | Consulate city (see table below) | `toronto` |
| `CURRENT_APPOINTMENT_DATE` | Existing appointment date (YYYY-MM-DD) | `2026-12-31` |
| `EARLIEST_DATE` | Earliest date you can attend (YYYY-MM-DD) | `2026-06-17` |
| `POLL_INTERVAL_SECONDS` | Seconds between polls (≥ 30) | `60` |
| `MAX_RETRIES` | Max consecutive errors before stopping | `10` |
| `NOTIFY_METHOD` | `telegram` / `email` / `both` / `none` | `none` |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | — |
| `TELEGRAM_CHAT_ID` | Telegram chat / user ID | — |
| `SMTP_HOST` | SMTP server | `smtp.gmail.com` |
| `SMTP_PORT` | SMTP port | `587` |
| `SMTP_USER` | Gmail address | — |
| `SMTP_PASSWORD` | Gmail app password | — |
| `NOTIFY_EMAIL_TO` | Recipient email | — |

### Consulates

| City | `CONSULATE` value |
|---|---|
| Calgary | `calgary` |
| Halifax | `halifax` |
| Montreal | `montreal` |
| Ottawa | `ottawa` |
| Quebec City | `quebec_city` |
| Toronto | `toronto` |
| Vancouver | `vancouver` |
| Victoria | `victoria` |

## Full Example

```bash
AIS_EMAIL=you@example.com \
AIS_PASSWORD=secret \
CONSULATE=toronto \
EARLIEST_DATE=2026-07-01 \
CURRENT_APPOINTMENT_DATE=2026-12-01 \
NOTIFY_METHOD=telegram \
TELEGRAM_BOT_TOKEN=<your-bot-token> \
TELEGRAM_CHAT_ID=<your-chat-id> \
java -jar target/visa-scheduler-1.0.0.jar
```

## Project Structure

```
visa-scheduler-java/
├── pom.xml
└── src/main/
    ├── java/com/visascheduler/
    │   ├── Main.java          ← entry point, startup banner
    │   ├── Config.java        ← all settings (env var overrides)
    │   ├── AisClient.java     ← HTTP session: login, query slots, book
    │   ├── Scheduler.java     ← polling loop, date filtering, retry
    │   └── Notifier.java      ← Telegram + SMTP email notifications
    └── resources/
        └── logback.xml        ← logging config (console + rolling file)
```

## Logs

All activity is written to **`visa_scheduler.log`** (rolling, max 10 MB × 7 days)
and to stdout simultaneously.

## Disclaimer

This tool uses your real AIS credentials against the live portal.
Verify every booking in your AIS dashboard. Use responsibly and in line with
the AIS terms of service.
