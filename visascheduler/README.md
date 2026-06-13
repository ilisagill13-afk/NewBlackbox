# Visa Slot Booker (`:visascheduler`)

A standalone Android app that watches the US visa appointment site
(`ais.usvisa-info.com`) and **automatically books the earliest open slot** within a date
range you choose, for *your own* existing appointment.

> ⚠️ This reschedules an appointment you already have (you must already own a paid,
> scheduled appointment). It cannot create a new application. Automating the site is against
> its Terms of Service and aggressive use can get your account banned — keep the poll interval
> sensible (the app enforces a 2-minute floor) and use it only for your own booking.

## How it works
1. You log in once through a real, visible WebView (`LoginActivity`) — you solve any
   Cloudflare/CAPTCHA yourself. The session cookie is then reused.
2. A foreground service (`VisaMonitorService`) loads the appointment page **once**, then keeps an
   off-screen WebView parked on the site's origin and polls **only** the available-days/-times
   JSON endpoints via same-origin `fetch()` (`assets/visa_automation.js`). It **never reloads the
   page** during polling — each check is a tiny XHR that carries your cookie + CSRF token like the
   site's own calls, so we don't reset our place in line or trigger the heavier page-load limiter.
3. When a date inside your range appears, it immediately books the earliest time (reusing the
   session CSRF token, still no reload) and posts a "Booked!" notification, then stops.

## Rate limiting — why the interval matters
`ais.usvisa-info.com` actively throttles slot checking:
- Roughly **~48 checks** in a short window start returning empty arrays → a **soft ban (~5 hours)**.
- Bot-like activity (checking every few seconds, repeated page refreshes) → an **"Access
  Limitation" lockout up to 72 hours**.

So instead of a fixed refresh, an **adaptive scheduler decides each next-check time**
(`core/AdaptiveIntervalController.kt`). It:
- enforces a **safe hourly budget** (~40 checks/hour, a margin under the soft-ban threshold) and
  waits out the window if the budget is spent;
- **checks as fast as the budget allows** while everything is healthy (to catch the earliest slot);
- **cools down** (30 min, doubling on repeats) when the site returns a hard rate-limit signal
  (HTTP 429/403), and **backs off** exponentially on transient errors;
- adds **random jitter** so the cadence isn't robotic.

Your configured interval is treated as the *preferred* healthy cadence (min 2 minutes); the
scheduler only ever slows down from there for safety, never below the budget. The current
decision and its reason are shown live in the app and the ongoing notification.

## Configuration (Config screen)
| Field | Where to find it |
|-------|------------------|
| Region/locale | The path segment in the site URL, e.g. `en-in` (India), `en-ca` (Canada). |
| Email / Password | Your `usvisa-info.com` login. Stored encrypted (EncryptedSharedPreferences). |
| Schedule ID | The number in the URL of your existing appointment: `.../schedule/<ID>/...`. |
| Consulate facility ID | The `value` of your consulate in the appointment page's location `<select>`. |
| ASC facility ID | Biometrics centre id (optional; leave blank if not required). |
| Earliest / Latest date | `yyyy-MM-dd` range you'll accept. |
| Poll interval | Minutes between checks (minimum 2). |

To find the facility IDs and schedule ID: log in on a desktop browser, open the appointment
page, and inspect the location dropdown `<option value="...">` and the URL.

## Build
```bash
./gradlew :visascheduler:assembleDebug
```
Requires the Android SDK (compileSdk 35) and JDK 17+. Produces a standalone APK with its own
launcher icon — it does **not** depend on the BlackBox virtualization modules.
