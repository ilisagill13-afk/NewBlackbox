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
        log.info("║   US Visa Slot Scheduler — Canada  v1.0.0   ║");
        log.info("╚══════════════════════════════════════════════╝");
        log.info("Consulate   : {}", Config.CONSULATE);
        log.info("Facility ID : {}", Config.FACILITY_ID);
        log.info("Current appt: {}", Config.CURRENT_APPOINTMENT_DATE);
        log.info("Earliest ok : {}", Config.EARLIEST_DATE);
        log.info("Poll every  : {}s", Config.POLL_INTERVAL_SECONDS);
        log.info("Notifications: {}", Config.NOTIFY_METHOD);

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
