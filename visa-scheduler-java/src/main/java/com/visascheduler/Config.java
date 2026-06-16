package com.visascheduler;

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
    /** Country/language segment in the URL (Canada = en-ca) */
    public static final String COUNTRY_CODE = "en-ca";

    /**
     * Consulate city key. Valid values:
     * calgary, halifax, montreal, ottawa, quebec_city, toronto, vancouver, victoria
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

    /**
     * Your current appointment date (YYYY-MM-DD).
     * The scheduler will only book a slot EARLIER than this date.
     */
    public static final String CURRENT_APPOINTMENT_DATE =
            env("CURRENT_APPOINTMENT_DATE", "2026-12-31");

    /** Earliest date you are willing to attend (YYYY-MM-DD). */
    public static final String EARLIEST_DATE = env("EARLIEST_DATE", "2026-06-17");

    // ── Scheduling ────────────────────────────────────────────────────────────
    /** Seconds to wait between polls. Keep >= 30 to respect rate limits. */
    public static final int POLL_INTERVAL_SECONDS =
            Integer.parseInt(env("POLL_INTERVAL_SECONDS", "60"));

    public static final int MAX_RETRIES = Integer.parseInt(env("MAX_RETRIES", "10"));

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

    // ── Browser / Request ─────────────────────────────────────────────────────
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/125.0.0.0 Safari/537.36";

    // ── Helper ────────────────────────────────────────────────────────────────
    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
