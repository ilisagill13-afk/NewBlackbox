# US Visa Slot Scheduler — Canada (Java v2)

Automatically monitors [ais.usvisa-info.com](https://ais.usvisa-info.com/en-ca/niv)
for US visa interview slots at Canadian consulates and books the earliest available
slot sooner than your current appointment.

**v2 uses Microsoft Playwright (real Chromium browser)** instead of a raw HTTP client,
making it undetectable by TLS/JA3 fingerprinting, JavaScript challenges, and
behavioural analysis systems used in 2026.

---

## Why Playwright instead of HttpClient?

| Detection vector | Raw HttpClient | Playwright (Chromium) |
|---|---|---|
| TLS / JA3 fingerprint | ❌ Java's TLS — easily flagged | ✅ Real Chrome TLS stack |
| JavaScript challenges (Cloudflare, Akamai) | ❌ No JS engine | ✅ Full V8 execution |
| navigator.webdriver flag | ❌ N/A | ✅ Patched to `undefined` |
| HTTP/2 HPACK header order | ❌ Wrong order | ✅ Identical to Chrome |
| Canvas / WebGL fingerprint | ❌ None | ✅ Real GPU rendering |
| Cookie / session behaviour | ❌ Manual handling | ✅ Automatic browser storage |
| Mouse movement pattern | ❌ None | ✅ Gaussian random movement |
| Typing speed distribution | ❌ Instant fill | ✅ Normal-distributed keystrokes |

---

## Build

Requires **JDK 17+** and **Maven 3.8+**.  
Playwright downloads its own Chromium on first run — no separate install needed.

```bash
cd visa-scheduler-java
mvn clean package -q
# Output: target/visa-scheduler-2.0.0.jar
```

## First Run (Playwright browser install)

```bash
# One-time: installs Chromium (~130MB)
java -cp target/visa-scheduler-2.0.0.jar com.microsoft.playwright.CLI install chromium

# Then run the scheduler
java -jar target/visa-scheduler-2.0.0.jar
```

## Configuration

All settings via **environment variables** (recommended) or `Config.java`.

| Variable | Description | Default |
|---|---|---|
| `AIS_EMAIL` | AIS account email | — |
| `AIS_PASSWORD` | AIS account password | — |
| `CONSULATE` | Consulate city (see table) | `toronto` |
| `CURRENT_APPOINTMENT_DATE` | Your existing appt (YYYY-MM-DD) | `2026-12-31` |
| `EARLIEST_DATE` | Earliest you can attend (YYYY-MM-DD) | `2026-06-17` |
| `POLL_MIN_SECONDS` | Minimum wait between polls | `90` |
| `POLL_MAX_SECONDS` | Maximum wait between polls | `180` |
| `MAX_DAILY_POLLS` | Max polls per day | `150` |
| `BUSINESS_HOURS_ONLY` | Only poll 09:00–17:00 consulate time | `true` |
| `CONSULATE_TIMEZONE` | IANA timezone for business hours | `America/Toronto` |
| `HEADLESS` | Run browser invisibly | `true` |
| `NOTIFY_METHOD` | `telegram` / `email` / `both` / `none` | `none` |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | — |
| `TELEGRAM_CHAT_ID` | Telegram chat / user ID | — |
| `SMTP_USER` | Gmail address | — |
| `SMTP_PASSWORD` | Gmail app password | — |
| `NOTIFY_EMAIL_TO` | Recipient email | — |

### Consulates

| City | `CONSULATE` | Timezone |
|---|---|---|
| Toronto | `toronto` | `America/Toronto` |
| Vancouver | `vancouver` | `America/Vancouver` |
| Montreal | `montreal` | `America/Toronto` |
| Ottawa | `ottawa` | `America/Toronto` |
| Calgary | `calgary` | `America/Edmonton` |
| Halifax | `halifax` | `America/Halifax` |
| Quebec City | `quebec_city` | `America/Toronto` |
| Victoria | `victoria` | `America/Vancouver` |

## Full Example

```bash
AIS_EMAIL=you@example.com \
AIS_PASSWORD=secret \
CONSULATE=toronto \
EARLIEST_DATE=2026-07-01 \
CURRENT_APPOINTMENT_DATE=2026-12-01 \
HEADLESS=true \
NOTIFY_METHOD=telegram \
TELEGRAM_BOT_TOKEN=<your-token> \
TELEGRAM_CHAT_ID=<your-id> \
java -jar target/visa-scheduler-2.0.0.jar
```

Set `HEADLESS=false` to watch the browser interact with the site (useful for debugging).

## Anti-detection features

- **Real Chromium** — identical TLS fingerprint to desktop Chrome
- **navigator.webdriver = undefined** — removes the most common bot flag
- **Random viewport** — one of 5 common laptop resolutions per session
- **Locale & timezone match consulate** — `en-CA` locale, correct timezone
- **Gaussian typing speed** — mean 80ms/key, stddev 30ms — normal distribution
- **Gaussian mouse movement** — cursor offset + pause before every click
- **Random scroll** before every form interaction
- **Random poll interval** — 90–180s (configurable) — no fixed pattern
- **Business hours only** — no suspicious 3am requests
- **Daily cap** (150 polls) — limits total daily load
- **Exponential back-off** on 429 / 503 responses
- **Persistent browser state** — cookies saved to `browser_state.json`, reused next session
- **User-Agent rotation** — 5 different UA strings across sessions

## Disclaimer

This tool uses your own AIS account credentials on the live portal.
Use it responsibly. Verify every booking in your AIS dashboard.
