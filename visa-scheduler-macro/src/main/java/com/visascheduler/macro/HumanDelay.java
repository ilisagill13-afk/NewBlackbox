package com.visascheduler.macro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/** Randomized, human-shaped pauses — avoids a robotic fixed-interval pattern. */
public final class HumanDelay {

    private static final Logger log = LoggerFactory.getLogger(HumanDelay.class);

    private HumanDelay() {}

    public static void afterRefresh() {
        sleep(rangeMs(MacroConfig.PAGE_LOAD_MIN_SECONDS, MacroConfig.PAGE_LOAD_MAX_SECONDS));
    }

    public static void pollInterval() {
        long ms = rangeMs(MacroConfig.POLL_MIN_SECONDS, MacroConfig.POLL_MAX_SECONDS);
        log.info("Next check in {}s …", ms / 1000);
        sleep(ms);
    }

    public static void postAlertCooldown() {
        long ms = MacroConfig.POST_ALERT_COOLDOWN_SECONDS * 1000L;
        log.info("Cooling down {}s after alert before resuming checks …", ms / 1000);
        sleep(ms);
    }

    private static long rangeMs(int minSec, int maxSec) {
        return ThreadLocalRandom.current().nextLong((long) minSec * 1000, (long) maxSec * 1000 + 1);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
