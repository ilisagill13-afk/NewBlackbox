package com.visascheduler.macro;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for the macro monitor.
 *
 * Two sources:
 *  1. Environment variables (poll timing, notifications) — same pattern as
 *     the other scheduler tools in this repo.
 *  2. calibration.properties (the monitored screen region) — written by
 *     {@link Calibrator}, read here at startup.
 */
public final class MacroConfig {

    private static final Path CALIBRATION_FILE = Path.of("calibration.properties");

    private MacroConfig() {}

    // ── Region being watched (set by Calibrator) ──────────────────────────────
    public static int regionX, regionY, regionW, regionH;
    public static boolean calibrated = false;

    public static void loadCalibration() {
        if (!Files.exists(CALIBRATION_FILE)) return;
        Properties p = new Properties();
        try (var in = new FileInputStream(CALIBRATION_FILE.toFile())) {
            p.load(in);
            regionX = Integer.parseInt(p.getProperty("region.x"));
            regionY = Integer.parseInt(p.getProperty("region.y"));
            regionW = Integer.parseInt(p.getProperty("region.w"));
            regionH = Integer.parseInt(p.getProperty("region.h"));
            calibrated = true;
        } catch (Exception e) {
            throw new RuntimeException("Could not read calibration.properties: " + e.getMessage(), e);
        }
    }

    public static void saveCalibration(int x, int y, int w, int h) throws IOException {
        Properties p = new Properties();
        p.setProperty("region.x", String.valueOf(x));
        p.setProperty("region.y", String.valueOf(y));
        p.setProperty("region.w", String.valueOf(w));
        p.setProperty("region.h", String.valueOf(h));
        try (var out = new FileOutputStream(CALIBRATION_FILE.toFile())) {
            p.store(out, "Visa Macro Monitor — calibrated screen region. Delete and re-run --calibrate if your screen/window layout changes.");
        }
    }

    // ── Timing / safety (same philosophy as the Playwright scheduler) ────────

    public static final int POLL_MIN_SECONDS = Integer.parseInt(env("POLL_MIN_SECONDS", "90"));
    public static final int POLL_MAX_SECONDS = Integer.parseInt(env("POLL_MAX_SECONDS", "180"));
    public static final int MAX_DAILY_POLLS  = Integer.parseInt(env("MAX_DAILY_POLLS", "150"));
    public static final boolean BUSINESS_HOURS_ONLY =
            Boolean.parseBoolean(env("BUSINESS_HOURS_ONLY", "true"));
    public static final String CONSULATE_TIMEZONE = env("CONSULATE_TIMEZONE", "America/Toronto");

    /**
     * Sensitivity of the screenshot-diff trigger.
     * Score is average per-pixel RGB difference (0–765) over a 64x64 downscaled
     * sample of the monitored region. Typical "page redrew, nothing changed"
     * noise is under 5; an actual calendar/content change is usually 20+.
     * Lower this if you're missing real changes; raise it if you get false alerts.
     */
    public static final double DIFF_THRESHOLD =
            Double.parseDouble(env("DIFF_THRESHOLD", "18.0"));

    /** Seconds to wait after pressing F5 before taking the comparison screenshot. */
    public static final int PAGE_LOAD_MIN_SECONDS = 2;
    public static final int PAGE_LOAD_MAX_SECONDS = 5;

    /** After an alert fires, pause this long before resuming to avoid spamming. */
    public static final int POST_ALERT_COOLDOWN_SECONDS =
            Integer.parseInt(env("POST_ALERT_COOLDOWN_SECONDS", "150"));

    // ── Notifications ──────────────────────────────────────────────────────
    public static final String NOTIFY_METHOD = env("NOTIFY_METHOD", "none");
    public static final String TELEGRAM_BOT_TOKEN = env("TELEGRAM_BOT_TOKEN", "");
    public static final String TELEGRAM_CHAT_ID   = env("TELEGRAM_CHAT_ID",   "");
    public static final String SMTP_HOST       = env("SMTP_HOST",       "smtp.gmail.com");
    public static final int    SMTP_PORT       = Integer.parseInt(env("SMTP_PORT", "587"));
    public static final String SMTP_USER       = env("SMTP_USER",       "");
    public static final String SMTP_PASSWORD   = env("SMTP_PASSWORD",   "");
    public static final String NOTIFY_EMAIL_TO = env("NOTIFY_EMAIL_TO", "");

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
