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
2. A foreground service (`VisaMonitorService`) keeps an off-screen WebView on the site's origin
   and polls the available-days/-times JSON endpoints via same-origin `fetch()`
   (`assets/visa_automation.js`), so requests carry your cookie + CSRF token like the site's own
   calls.
3. When a date inside your range appears, it immediately books the earliest time and posts a
   "Booked!" notification, then stops.

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
