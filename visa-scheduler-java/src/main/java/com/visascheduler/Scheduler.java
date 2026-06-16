package com.visascheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Core polling loop.
 *
 * <ol>
 *   <li>Logs in to AIS with retry.</li>
 *   <li>Polls for available dates every {@link Config#POLL_INTERVAL_SECONDS} seconds.</li>
 *   <li>Filters dates to those earlier than the current appointment and no sooner than
 *       the user's earliest acceptable date.</li>
 *   <li>Books the earliest qualifying slot.</li>
 *   <li>Sends notifications on success or terminal failure.</li>
 * </ol>
 */
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AisClient client;
    private final LocalDate currentAppointment;
    private final LocalDate earliestDate;
    private int consecutiveErrors = 0;

    public Scheduler() {
        this.client             = new AisClient();
        this.currentAppointment = LocalDate.parse(Config.CURRENT_APPOINTMENT_DATE, DATE_FMT);
        this.earliestDate       = LocalDate.parse(Config.EARLIEST_DATE, DATE_FMT);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void run() {
        log.info("Starting US Visa Slot Scheduler for Canada — consulate: {} (ID {})",
                Config.CONSULATE, Config.FACILITY_ID);
        log.info("Target: slot earlier than {}, no sooner than {}",
                currentAppointment, earliestDate);

        loginWithRetry();

        while (true) {
            try {
                boolean booked = checkAndBook();
                consecutiveErrors = 0;
                if (booked) break;
            } catch (Exception e) {
                consecutiveErrors++;
                log.error("Poll error ({}/{}): {}", consecutiveErrors, Config.MAX_RETRIES,
                        e.getMessage());

                if (consecutiveErrors >= Config.MAX_RETRIES) {
                    String msg = "Scheduler stopped after " + Config.MAX_RETRIES
                            + " consecutive errors. Last: " + e.getMessage();
                    log.error(msg);
                    Notifier.notify("Visa Scheduler — STOPPED", msg);
                    break;
                }

                // Re-authenticate on auth errors
                String err = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (err.contains("401") || err.contains("403") || err.contains("sign_in")) {
                    log.info("Auth error detected — re-logging in …");
                    loginWithRetry();
                }
            }

            sleep(Config.POLL_INTERVAL_SECONDS);
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
                if (attempt < 3) sleep(5 * attempt);
            }
        }
        throw new RuntimeException("Could not log in after 3 attempts.");
    }

    private boolean checkAndBook() throws IOException {
        log.info("Checking available dates …");
        List<String> dates = client.getAvailableDates();

        if (dates.isEmpty()) {
            log.info("No slots available right now.");
            return false;
        }

        log.info("Available dates returned: {}", dates.subList(0, Math.min(10, dates.size())));

        Optional<String> best = pickBestDate(dates);
        if (best.isEmpty()) {
            log.info("No dates meet the criteria (earlier than {}, at or after {}).",
                    currentAppointment, earliestDate);
            return false;
        }

        String date = best.get();
        log.info("Best available date: {} — fetching time slots …", date);

        List<String> times = client.getAvailableTimes(date);
        if (times.isEmpty()) {
            log.info("No time slots available for {}.", date);
            return false;
        }

        String time = times.get(0);
        log.info("Attempting to book {} at {} …", date, time);

        Notifier.notify("Visa Slot Found!",
                "Attempting to book " + date + " at " + time
                        + " at " + titleCase(Config.CONSULATE) + " consulate.");

        boolean success = client.bookAppointment(date, time);

        if (success) {
            String msg = "Successfully booked US visa appointment!\n"
                    + "Date: " + date + "\n"
                    + "Time: " + time + "\n"
                    + "Consulate: " + titleCase(Config.CONSULATE) + ", Canada";
            log.info(msg);
            Notifier.notify("Visa Appointment Booked!", msg);
            return true;
        }

        log.warn("Booking failed for {} {} — will retry next poll.", date, time);
        Notifier.notify("Booking Attempt Failed",
                "Could not book " + date + " at " + time + ". Will try again.");
        return false;
    }

    private Optional<String> pickBestDate(List<String> available) {
        return available.stream()
                .map(s -> {
                    try { return LocalDate.parse(s, DATE_FMT); }
                    catch (Exception e) { return null; }
                })
                .filter(d -> d != null
                        && !d.isBefore(earliestDate)
                        && d.isBefore(currentAppointment))
                .min(LocalDate::compareTo)
                .map(d -> d.format(DATE_FMT));
    }

    private void sleep(int seconds) {
        log.info("Sleeping {}s before next poll …", seconds);
        try { Thread.sleep(seconds * 1000L); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }
}
