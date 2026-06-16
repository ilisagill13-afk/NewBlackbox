package com.visascheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * US Visa Slot Scheduler — Canada
 *
 * <p>Run with environment variables:
 * <pre>
 *   AIS_EMAIL=you@example.com \
 *   AIS_PASSWORD=secret \
 *   CONSULATE=toronto \
 *   EARLIEST_DATE=2026-07-01 \
 *   CURRENT_APPOINTMENT_DATE=2026-12-01 \
 *   NOTIFY_METHOD=telegram \
 *   TELEGRAM_BOT_TOKEN=&lt;token&gt; \
 *   TELEGRAM_CHAT_ID=&lt;id&gt; \
 *   java -jar visa-scheduler-1.0.0.jar
 * </pre>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║   US Visa Slot Scheduler — Canada  v2.0.0   ║");
        log.info("╚══════════════════════════════════════════════╝");
        log.info("Consulate        : {}", Config.CONSULATE);
        log.info("Facility ID      : {}", Config.FACILITY_ID);
        log.info("Current appt     : {}", Config.CURRENT_APPOINTMENT_DATE);
        log.info("Earliest ok      : {}", Config.EARLIEST_DATE);
        log.info("Mode             : {}", Config.NOTIFY_ONLY
                ? "NOTIFY_ONLY (safest — you book manually)"
                : "AUTO_BOOK (bot books for you — higher risk)");
        log.info("Poll interval    : {}–{}s (random)", Config.POLL_MIN_SECONDS, Config.POLL_MAX_SECONDS);
        log.info("Daily cap        : {} polls", Config.MAX_DAILY_POLLS);
        log.info("Business hrs only: {}", Config.BUSINESS_HOURS_ONLY);
        log.info("Consulate TZ     : {}", Config.CONSULATE_TIMEZONE);
        log.info("Headless browser : {}", Config.HEADLESS);
        log.info("Notifications    : {}", Config.NOTIFY_METHOD);

        if (Config.NOTIFY_ONLY && Config.NOTIFY_METHOD.equalsIgnoreCase("none")) {
            log.warn("NOTIFY_ONLY=true but NOTIFY_METHOD=none — you will not be "
                    + "alerted when a slot opens! Set NOTIFY_METHOD=telegram or email.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log.info("Scheduler stopped.")));

        try {
            new Scheduler().run();
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
