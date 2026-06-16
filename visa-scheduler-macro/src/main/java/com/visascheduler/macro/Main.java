package com.visascheduler.macro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * US Visa Slot Macro Monitor — Canada
 *
 * Watches a real, manually-opened and manually-logged-in Chrome window for
 * changes to the appointment slots area, using only OS-level keyboard/screen
 * APIs (java.awt.Robot). No browser automation framework is involved at any
 * point, so there is no WebDriver flag, no CDP protocol traffic, and no
 * automation-specific TLS/JS fingerprint for AIS to detect.
 *
 * This tool only ALERTS you — it never books anything. You stay in full
 * control of your own logged-in session and click "book" yourself.
 *
 * Usage:
 *   java -jar visa-scheduler-macro-1.0.0.jar --calibrate   (one-time setup)
 *   java -jar visa-scheduler-macro-1.0.0.jar                (start monitoring)
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--calibrate")) {
                Calibrator.run();
                return;
            }

            MacroConfig.loadCalibration();
            if (!MacroConfig.calibrated) {
                System.out.println("No calibration.properties found.");
                System.out.println("Run calibration first:");
                System.out.println("  java -jar visa-scheduler-macro-1.0.0.jar --calibrate");
                return;
            }

            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("║   US Visa Slot Macro Monitor — Canada   v1.0.0       ║");
            log.info("╚══════════════════════════════════════════════════════╝");
            log.info("Watched region : x={} y={} w={} h={}",
                    MacroConfig.regionX, MacroConfig.regionY, MacroConfig.regionW, MacroConfig.regionH);
            log.info("Diff threshold : {}", MacroConfig.DIFF_THRESHOLD);
            log.info("Notifications  : {}", MacroConfig.NOTIFY_METHOD);
            log.info("");
            log.info("IMPORTANT: keep your AIS browser window open, visible, and FOCUSED.");
            log.info("This tool presses F5 on whatever window has focus — if you switch");
            log.info("away, pause this program first (Ctrl+C).");
            log.info("");

            if (MacroConfig.NOTIFY_METHOD.equalsIgnoreCase("none")) {
                log.warn("NOTIFY_METHOD=none — you will see alerts only in this console/log, "
                        + "not on your phone. Consider setting NOTIFY_METHOD=telegram.");
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> log.info("Monitor stopped.")));

            new ScreenMonitor().run();

        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
