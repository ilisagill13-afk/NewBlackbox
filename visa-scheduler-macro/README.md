# US Visa Slot Macro Monitor — Canada

A pure **OS-level keyboard/screen macro** (Java `Robot` class) that watches a
**real, manually-opened, manually-logged-in Chrome window** for changes on
the AIS appointment page, and alerts you the moment something changes so you
can book it yourself.

This is the lowest-detection-risk option in this repo because **no browser
automation framework is used at all**:

| | Playwright bot (`visa-scheduler-java`) | This macro monitor |
|---|---|---|
| Drives browser via | WebDriver/CDP protocol | Nothing — it's your own browser |
| `navigator.webdriver` | Patched, but the protocol still runs | Never set — there's no automation API in play |
| TLS/JA3 fingerprint | Real Chromium, but spawned by Playwright | Identical to you opening Chrome by hand, because it IS you opening Chrome by hand |
| Login | Automated (or cookie import) | You log in yourself, normally |
| Booking | Can auto-book | **Never auto-books** — alerts only |
| What AIS sees | A Chromium instance with CDP attached | A person who refreshes the page and reads it |

The trade-off: it requires your computer to be on with the browser window
open and focused, and it doesn't click anything for you — you do the booking.

---

## How it works

1. **Calibrate once**: open your real Chrome, log in to AIS, navigate to the
   appointment page, then run `--calibrate`. A screenshot opens in a window —
   click the top-left then bottom-right corner of the calendar/appointment
   area. This is saved to `calibration.properties`.
2. **Run the monitor**: it takes a baseline screenshot of that region, then
   on a random interval (default 90–180s):
   - Presses **F5** (refresh) — exactly what an impatient human does
   - Waits 2–5s for the page to reload
   - Screenshots the same region again
   - Compares it to the baseline using a perceptual pixel-difference score
   - If the difference exceeds the threshold (default 18 out of 765), it
     means the page content changed — possibly a slot appeared — and it:
     - Saves the screenshot
     - Sends you a Telegram message **with the screenshot attached** (or email)
     - You look at the screenshot, switch to your browser, and book manually

It never reads page text, never clicks calendar cells, never submits the
booking form. The only actions it performs are F5 and screenshots.

---

## Setup

Requires JDK 17+ and a real graphical desktop (this needs `java.awt.Robot`,
which requires an actual display — it will not run in a headless server
environment).

```bash
cd visa-scheduler-macro
mvn clean package -q
```

### Step 1 — Calibrate

1. Open Chrome, log in to `https://ais.usvisa-info.com/en-ca/niv` yourself.
2. Navigate to the appointment/reschedule page.
3. Run:
   ```bash
   java -jar target/visa-scheduler-macro-1.0.0.jar --calibrate
   ```
4. A window pops up showing your full screen. Click the **top-left** corner
   of the calendar/appointment area, then the **bottom-right** corner.
   The window closes automatically.

### Step 2 — Run

```bash
NOTIFY_METHOD=telegram \
TELEGRAM_BOT_TOKEN=<your-bot-token> \
TELEGRAM_CHAT_ID=<your-chat-id> \
java -jar target/visa-scheduler-macro-1.0.0.jar
```

Keep the Chrome window **visible and focused** the whole time — this tool
sends F5 to whatever window currently has focus on your desktop. If you need
to use your computer for something else, stop the monitor first (`Ctrl+C`).

---

## Configuration

| Variable | Description | Default |
|---|---|---|
| `POLL_MIN_SECONDS` / `POLL_MAX_SECONDS` | Random interval between checks | `90` / `180` |
| `MAX_DAILY_POLLS` | Max checks per day | `150` |
| `BUSINESS_HOURS_ONLY` | Only check 09:00–17:00 local time | `true` |
| `CONSULATE_TIMEZONE` | IANA timezone for business hours | `America/Toronto` |
| `DIFF_THRESHOLD` | Sensitivity of change detection (0–765) | `18.0` |
| `POST_ALERT_COOLDOWN_SECONDS` | Pause after an alert before resuming checks | `150` |
| `NOTIFY_METHOD` | `telegram` / `email` / `both` / `none` | `none` |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | Telegram alerts (with screenshot) | — |
| `SMTP_USER` / `SMTP_PASSWORD` / `NOTIFY_EMAIL_TO` | Email alerts (with screenshot attached) | — |

### Tuning `DIFF_THRESHOLD`

- Too low → false alerts from page re-rendering, ads, clock widgets, etc.
- Too high → misses real slot changes.
- Check `baseline.png` and any `alert_*.png` files saved in the working
  directory after a run to see what triggered (or didn't).

---

## Re-calibrating

If you resize the browser window, change screen resolution, move the window,
or change zoom level, delete `calibration.properties` and run `--calibrate`
again.

---

## Disclaimer

This automates input on your own computer using your own already-logged-in
browser session — it does not interact with AIS through any API or
automation protocol. It still relies on your account being used reasonably:
keep the poll interval sane and don't run multiple instances against the
same account. Always verify and complete the actual booking yourself.
