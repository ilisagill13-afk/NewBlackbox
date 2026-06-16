# US Visa Slot Scheduler — Canada (Java v2)

Monitors [ais.usvisa-info.com](https://ais.usvisa-info.com/en-ca/niv) for US visa
interview slots at Canadian consulates and either **alerts you to book manually**
(default, safest) or **auto-books** the earliest qualifying slot.

---

## Read this first — no automation is 100% undetectable

Every layer below reduces detection risk, but the single biggest factor is
**where the requests come from**:

> **Run this on your home computer / home IP, never on a VPS or cloud server.**
> AIS (like most government booking portals) blocks/flags datacenter IP ranges
> (AWS, GCP, Azure, DigitalOcean, etc.) far more aggressively than residential IPs,
> regardless of how human-like the browser behaves.

The safest possible setup, in order of risk:

| Mode | Risk | How |
|---|---|---|
| **Manual checking** | None | You refresh the page yourself |
| **NOTIFY_ONLY=true** (default here) | Low | Bot only *reads* the slots page and pings you; **you** click "book" in your own browser within the alert window |
| **Cookie import + NOTIFY_ONLY** | Low-Medium | Bot reuses your real Chrome session cookies, so even the "reading" traffic looks like your normal browser |
| **AUTO_BOOK=true** | Medium-High | Bot also submits the booking form — the riskiest action, since it's a write operation tied to your account |

This tool defaults to **NOTIFY_ONLY mode**. You get a Telegram/email ping the
moment a slot opens, then book it yourself in ~1–2 minutes. This keeps the bot's
footprint to read-only polling, which is far less likely to trigger an account
flag than automating the booking form submission itself.

---

## Anti-detection layers implemented

### 1. Real browser (Playwright/Chromium) — not a raw HTTP client
A raw `HttpClient` has a different TLS/JA3 fingerprint than real Chrome, doesn't
execute JavaScript (fails Cloudflare/Akamai challenges), and sends HTTP/2 headers
in the wrong order. Playwright drives an actual Chromium browser, so all of this
matches a genuine browser byte-for-byte.

### 2. Stealth JS patches (`StealthScripts.java`)
Removes every common headless/automation tell:
- CDP artifact globals (`__cdc_*`, `__playwright`)
- `navigator.webdriver` → `undefined`
- `navigator.plugins` / `mimeTypes` → realistic non-empty lists
- `navigator.permissions.query('notifications')` → `'default'` not `'denied'`
- `window.outerWidth/Height` → matches viewport (headless reports `0`)
- `screen.colorDepth/pixelDepth` → `24`
- `navigator.deviceMemory` / `hardwareConcurrency` → realistic values
- `window.chrome.runtime` → present (missing in headless by default)
- `MediaDevices.enumerateDevices()` → returns fake devices instead of `[]`
- Canvas / AudioContext fingerprint → tiny random noise per session
- WebRTC → ICE servers stripped (prevents real IP leak via STUN)

### 3. Cookie import from your real browser (`CookieImporter.java`) — **recommended**
The strongest measure: log in to AIS in your **actual daily-use Chrome**, export
the session cookies, and let the bot reuse that exact session. It then never
touches the login form at all — to AIS it just looks like your browser is still
open.

**How to export:**
1. Install the [Cookie-Editor](https://cookie-editor.com/) extension in Chrome.
2. Log in normally at `https://ais.usvisa-info.com/en-ca/niv`.
3. Click Cookie-Editor → **Export** → **Export as JSON** (copies to clipboard).
4. Paste into a new file named `cookies.json` in the same folder as the JAR.

The bot loads this automatically on startup. When AIS session cookies expire
(~30 days), just repeat the export.

### 4. Behavioural realism
- Visits the AIS homepage and an info page before ever touching `/sign_in` (warm-up)
- Gaussian-distributed typing speed (mean 85ms/key) instead of instant fill
- Mouse moves to a randomized point near the element, pauses, then clicks
- Random scroll (60–320px) before interacting with any form
- Random viewport from 5 common laptop resolutions, locale `en-CA`, consulate timezone

### 5. Request-pattern realism
- Random poll interval (`POLL_MIN_SECONDS`–`POLL_MAX_SECONDS`, default 90–180s)
- Business-hours-only polling (default `09:00`–`17:00` consulate local time)
- Daily poll cap (`MAX_DAILY_POLLS`, default 150)
- Exponential back-off on HTTP 429 / 503, not blind instant retry

---

## Build

Requires JDK 17+ and Maven 3.8+.

```bash
cd visa-scheduler-java
mvn clean package -q
# Output: target/visa-scheduler-2.0.0.jar
```

## First run — install Chromium for Playwright (once)

```bash
java -cp target/visa-scheduler-2.0.0.jar com.microsoft.playwright.CLI install chromium
```

## Run (default: NOTIFY_ONLY mode)

```bash
AIS_EMAIL=you@example.com \
AIS_PASSWORD=secret \
CONSULATE=toronto \
EARLIEST_DATE=2026-07-01 \
CURRENT_APPOINTMENT_DATE=2026-12-01 \
NOTIFY_METHOD=telegram \
TELEGRAM_BOT_TOKEN=<token> \
TELEGRAM_CHAT_ID=<id> \
java -jar target/visa-scheduler-2.0.0.jar
```

If `cookies.json` is present in the working directory, it's used instead of
`AIS_EMAIL`/`AIS_PASSWORD`.

## Configuration

| Variable | Description | Default |
|---|---|---|
| `AIS_EMAIL` / `AIS_PASSWORD` | Used only if no `cookies.json` present | — |
| `CONSULATE` | Consulate city | `toronto` |
| `CURRENT_APPOINTMENT_DATE` | Existing appt (YYYY-MM-DD) | `2026-12-31` |
| `EARLIEST_DATE` | Earliest you can attend | `2026-06-17` |
| `NOTIFY_ONLY` | `true` = alert only (safe) / `false` = auto-book | `true` |
| `POLL_MIN_SECONDS` / `POLL_MAX_SECONDS` | Random poll interval range | `90` / `180` |
| `MAX_DAILY_POLLS` | Daily request cap | `150` |
| `BUSINESS_HOURS_ONLY` | Restrict polling to 09:00–17:00 local | `true` |
| `CONSULATE_TIMEZONE` | IANA zone for business hours | `America/Toronto` |
| `HEADLESS` | Run browser invisibly | `true` |
| `NOTIFY_METHOD` | `telegram` / `email` / `both` / `none` | `none` |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | Telegram alerts | — |
| `SMTP_USER` / `SMTP_PASSWORD` / `NOTIFY_EMAIL_TO` | Email alerts | — |

### Consulates

| City | `CONSULATE` | `CONSULATE_TIMEZONE` |
|---|---|---|
| Toronto | `toronto` | `America/Toronto` |
| Vancouver | `vancouver` | `America/Vancouver` |
| Montreal | `montreal` | `America/Toronto` |
| Ottawa | `ottawa` | `America/Toronto` |
| Calgary | `calgary` | `America/Edmonton` |
| Halifax | `halifax` | `America/Halifax` |
| Quebec City | `quebec_city` | `America/Toronto` |
| Victoria | `victoria` | `America/Vancouver` |

## Disclaimer

This is your own AIS account, used against the live portal. No combination of
stealth techniques guarantees zero detection risk — `NOTIFY_ONLY` mode plus a
home IP plus cookie import is the lowest-risk configuration available. Always
verify bookings in your AIS dashboard.
