package com.visascheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Core polling loop with account-safety measures:
 *
 *  1. Random poll interval (POLL_MIN_SECONDS – POLL_MAX_SECONDS) — no fixed pattern.
 *  2. Business-hours-only mode — no off-hours requests.
 *  3. Daily poll cap (MAX_DAILY_POLLS) — limits total daily requests.
 *  4. Exponential back-off on errors / rate-limits.
 *  5. Re-authentication only when actually needed (auth error detected).
 */
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Business hours: 09:00 – 17:00 local consulate time
    private static final LocalTime BUSINESS_START = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_END   = LocalTime.of(17, 0);

    private final AisClient client;
    private final LocalDate currentAppointment;
    private final LocalDate earliestDate;
    private final ZoneId    consulateZone;

    private int consecutiveErrors = 0;
    private int dailyPollCount    = 0;
    private LocalDate lastPollDate = LocalDate.MIN;

    public Scheduler() {
        this.client             = new AisClient();
        this.currentAppointment = LocalDate.parse(Config.CURRENT_APPOINTMENT_DATE, DATE_FMT);
        this.earliestDate       = LocalDate.parse(Config.EARLIEST_DATE, DATE_FMT);
        this.consulateZone      = ZoneId.of(Config.CONSULATE_TIMEZONE);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void run() {
        log.info("Starting US Visa Slot Scheduler — consulate: {} (ID {})",
                Config.CONSULATE, Config.FACILITY_ID);
        log.info("Want slot earlier than {}, no sooner than {}",
                currentAppointment, earliestDate);
        log.info("Poll range: {}–{}s | daily cap: {} | business hours only: {}",
                Config.POLL_MIN_SECONDS, Config.POLL_MAX_SECONDS,
                Config.MAX_DAILY_POLLS, Config.BUSINESS_HOURS_ONLY);

        loginWithRetry();

        while (true) {
            // ── Business hours gate ───────────────────────────────────────────
            if (Config.BUSINESS_HOURS_ONLY && !isBusinessHours()) {
                long waitMs = secondsUntilBusinessHours() * 1000L;
                log.info("Outside business hours — sleeping until 09:00 {} (~{}min)",
                        Config.CONSULATE_TIMEZONE, waitMs / 60_000);
                sleep(waitMs);
                continue;
            }

            // ── Daily cap gate ────────────────────────────────────────────────
            resetDailyCounterIfNewDay();
            if (dailyPollCount >= Config.MAX_DAILY_POLLS) {
                log.info("Daily poll cap ({}) reached — sleeping until midnight.",
                        Config.MAX_DAILY_POLLS);
                sleep(secondsUntilMidnight() * 1000L);
                continue;
            }

            // ── Poll ──────────────────────────────────────────────────────────
            try {
                dailyPollCount++;
                log.info("Poll #{} (today) …", dailyPollCount);
                boolean booked = checkAndBook();
                consecutiveErrors = 0;
                if (booked) break;

            } catch (Exception e) {
                consecutiveErrors++;
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.error("Poll error ({}/{}): {}", consecutiveErrors, Config.MAX_RETRIES, msg);

                if (consecutiveErrors >= Config.MAX_RETRIES) {
                    String alert = "Scheduler stopped after " + Config.MAX_RETRIES
                            + " consecutive errors. Last: " + msg;
                    log.error(alert);
                    Notifier.notify("Visa Scheduler — STOPPED", alert);
                    break;
                }

                // Re-login only on auth errors
                String lower = msg.toLowerCase();
                if (lower.contains("401") || lower.contains("403") || lower.contains("sign_in")
                        || lower.contains("login failed")) {
                    log.info("Auth error detected — re-logging in …");
                    loginWithRetry();
                }

                // Exponential back-off on rate-limit responses
                if (lower.contains("429") || lower.contains("too many")) {
                    log.warn("Rate-limit hit — applying back-off …");
                    HumanDelay.backoff(consecutiveErrors);
                    continue;
                }
            }

            // ── Random interval before next poll ──────────────────────────────
            HumanDelay.pollInterval(Config.POLL_MIN_SECONDS, Config.POLL_MAX_SECONDS);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void loginWithRetry() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                client.login();
                return;
            } catch (Exception e) {
                log.error("Login attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < 3) HumanDelay.backoff(attempt);
            }
        }
        throw new RuntimeException("Could not log in after 3 attempts.");
    }

    private boolean checkAndBook() throws IOException {
        log.info("Fetching available dates …");
        List<String> dates = client.getAvailableDates();

        if (dates.isEmpty()) {
            log.info("No slots available.");
            return false;
        }
        log.info("Dates returned: {}", dates.subList(0, Math.min(10, dates.size())));

        Optional<String> best = pickBestDate(dates);
        if (best.isEmpty()) {
            log.info("No date qualifies (need {} ≤ date < {}).", earliestDate, currentAppointment);
            return false;
        }

        String date = best.get();
        log.info("Best qualifying date: {} — fetching times …", date);

        // betweenRequests delay is inside getAvailableTimes
        List<String> times = client.getAvailableTimes(date);
        if (times.isEmpty()) {
            log.info("No time slots for {}.", date);
            return false;
        }

        String time = times.get(0);
        log.info("Will attempt to book {} at {} …", date, time);

        Notifier.notify("Visa Slot Found!",
                "Booking " + date + " at " + time + " — " + titleCase(Config.CONSULATE) + " consulate.");

        // beforeBooking delay is inside bookAppointment
        boolean success = client.bookAppointment(date, time);

        if (success) {
            String msg = "US visa appointment booked!\nDate: " + date
                    + "\nTime: " + time + "\nConsulate: " + titleCase(Config.CONSULATE) + ", Canada";
            log.info(msg);
            Notifier.notify("Appointment Booked!", msg);
            return true;
        }

        log.warn("Booking attempt for {} {} failed — will retry next poll.", date, time);
        Notifier.notify("Booking Failed", "Could not book " + date + " at " + time + ". Retrying.");
        return false;
    }

    private Optional<String> pickBestDate(List<String> available) {
        return available.stream()
                .map(s -> { try { return LocalDate.parse(s, DATE_FMT); } catch (Exception e) { return null; } })
                .filter(d -> d != null && !d.isBefore(earliestDate) && d.isBefore(currentAppointment))
                .min(LocalDate::compareTo)
                .map(d -> d.format(DATE_FMT));
    }

    // ── Time / scheduling helpers ─────────────────────────────────────────────

    private boolean isBusinessHours() {
        LocalTime now = ZonedDateTime.now(consulateZone).toLocalTime();
        return !now.isBefore(BUSINESS_START) && now.isBefore(BUSINESS_END);
    }

    private long secondsUntilBusinessHours() {
        ZonedDateTime now  = ZonedDateTime.now(consulateZone);
        ZonedDateTime open = now.toLocalDate().atTime(BUSINESS_START).atZone(consulateZone);
        if (now.isAfter(open)) open = open.plusDays(1);           // tomorrow's opening
        return java.time.Duration.between(now, open).getSeconds() + 60; // +1 min buffer
    }

    private long secondsUntilMidnight() {
        ZonedDateTime now      = ZonedDateTime.now(consulateZone);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1)
                .atStartOfDay(consulateZone);
        return java.time.Duration.between(now, midnight).getSeconds() + 60;
    }

    private void resetDailyCounterIfNewDay() {
        LocalDate today = ZonedDateTime.now(consulateZone).toLocalDate();
        if (!today.equals(lastPollDate)) {
            dailyPollCount = 0;
            lastPollDate   = today;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }
}
