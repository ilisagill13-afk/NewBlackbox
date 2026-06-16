package com.visascheduler;

import java.util.List;
import java.util.Map;

/**
 * All configuration in one place.
 * Values can be overridden via environment variables (recommended for credentials).
 */
public final class Config {

    private Config() {}

    // ── AIS Credentials ──────────────────────────────────────────────────────
    public static final String AIS_EMAIL    = env("AIS_EMAIL",    "your_email@example.com");
    public static final String AIS_PASSWORD = env("AIS_PASSWORD", "your_password");

    // ── Appointment Details ───────────────────────────────────────────────────
    public static final String COUNTRY_CODE = "en-ca";

    /**
     * Consulate city key.
     * Options: calgary, halifax, montreal, ottawa, quebec_city, toronto, vancouver, victoria
     */
    public static final String CONSULATE = env("CONSULATE", "toronto");

    private static final Map<String, Integer> FACILITY_IDS = Map.of(
            "calgary",      89,
            "halifax",      90,
            "montreal",     91,
            "ottawa",       92,
            "quebec_city",  93,
            "toronto",      94,
            "vancouver",    95,
            "victoria",     96
    );
    public static final int FACILITY_ID = FACILITY_IDS.getOrDefault(CONSULATE, 94);

    /** The scheduler only books a slot EARLIER than this date (YYYY-MM-DD). */
    public static final String CURRENT_APPOINTMENT_DATE =
            env("CURRENT_APPOINTMENT_DATE", "2026-12-31");

    /** Earliest date you are willing to attend (YYYY-MM-DD). */
    public static final String EARLIEST_DATE = env("EARLIEST_DATE", "2026-06-17");

    // ── Scheduling / Rate-limiting ────────────────────────────────────────────

    /**
     * Minimum seconds to wait between polls.
     * Default 90 — never go below 60 to avoid triggering rate limits.
     */
    public static final int POLL_MIN_SECONDS =
            Integer.parseInt(env("POLL_MIN_SECONDS", "90"));

    /**
     * Maximum seconds to wait between polls.
     * A random value in [POLL_MIN_SECONDS, POLL_MAX_SECONDS] is chosen each cycle.
     */
    public static final int POLL_MAX_SECONDS =
            Integer.parseInt(env("POLL_MAX_SECONDS", "180"));

    /**
     * Maximum number of polling rounds per calendar day.
     * Prevents runaway polling (≤ 200 keeps daily requests reasonable).
     */
    public static final int MAX_DAILY_POLLS =
            Integer.parseInt(env("MAX_DAILY_POLLS", "150"));

    /**
     * If true, only poll during local business hours (09:00 – 17:00 in the
     * consulate's timezone). Appointment slots typically only open during office
     * hours anyway, so off-hours polling just wastes requests.
     */
    public static final boolean BUSINESS_HOURS_ONLY =
            Boolean.parseBoolean(env("BUSINESS_HOURS_ONLY", "true"));

    /**
     * Timezone used for the business-hours check.
     * Use IANA zone IDs, e.g. "America/Toronto", "America/Vancouver".
     */
    public static final String CONSULATE_TIMEZONE =
            env("CONSULATE_TIMEZONE", "America/Toronto");

    /** Max consecutive errors before giving up entirely. */
    public static final int MAX_RETRIES = Integer.parseInt(env("MAX_RETRIES", "10"));

    /**
     * NOTIFY_ONLY = true  → bot only detects slots and sends a notification;
     *                        YOU manually book in your real browser.
     *                        SAFEST option — booking automation is never triggered.
     *
     * NOTIFY_ONLY = false → bot detects AND auto-books (riskier).
     */
    public static final boolean NOTIFY_ONLY =
            Boolean.parseBoolean(env("NOTIFY_ONLY", "true"));

    // ── URLs ─────────────────────────────────────────────────────────────────
    public static final String BASE_URL = "https://ais.usvisa-info.com";

    public static final String SIGN_IN_URL =
            BASE_URL + "/" + COUNTRY_CODE + "/niv/users/sign_in";

    public static String appointmentsUrl(String scheduleId) {
        return BASE_URL + "/" + COUNTRY_CODE + "/niv/schedule/" + scheduleId + "/appointment";
    }

    public static String availableDatesUrl(String scheduleId) {
        return BASE_URL + "/" + COUNTRY_CODE + "/niv/schedule/" + scheduleId
                + "/appointment/days/" + FACILITY_ID
                + ".json?appointments[expedite]=false";
    }

    public static String availableTimesUrl(String scheduleId, String date) {
        return BASE_URL + "/" + COUNTRY_CODE + "/niv/schedule/" + scheduleId
                + "/appointment/times/" + FACILITY_ID
                + ".json?date=" + date + "&appointments[expedite]=false";
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    /** "telegram" | "email" | "both" | "none" */
    public static final String NOTIFY_METHOD = env("NOTIFY_METHOD", "none");

    public static final String TELEGRAM_BOT_TOKEN = env("TELEGRAM_BOT_TOKEN", "");
    public static final String TELEGRAM_CHAT_ID   = env("TELEGRAM_CHAT_ID",   "");

    public static final String SMTP_HOST       = env("SMTP_HOST",       "smtp.gmail.com");
    public static final int    SMTP_PORT       = Integer.parseInt(env("SMTP_PORT", "587"));
    public static final String SMTP_USER       = env("SMTP_USER",       "");
    public static final String SMTP_PASSWORD   = env("SMTP_PASSWORD",   "");
    public static final String NOTIFY_EMAIL_TO = env("NOTIFY_EMAIL_TO", "");

    // ── Browser / Playwright ──────────────────────────────────────────────────
    /**
     * Run Chromium in headless mode (true = no visible window).
     * Set to false for debugging to watch the browser interact with the site.
     */
    public static final boolean HEADLESS =
            Boolean.parseBoolean(env("HEADLESS", "true"));

    /**
     * Pool of realistic Chrome/Safari/Firefox User-Agent strings.
     * One is picked at random per browser session.
     */
    public static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4_1) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.4.1 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) "
                    + "Gecko/20100101 Firefox/126.0"
    );

    // ── Helper ────────────────────────────────────────────────────────────────
    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
