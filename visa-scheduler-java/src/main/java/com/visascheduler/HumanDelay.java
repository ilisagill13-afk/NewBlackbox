package com.visascheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for injecting human-like, randomized pauses between actions
 * so the scheduler doesn't look like a bot to the AIS server.
 */
public final class HumanDelay {

    private static final Logger log = LoggerFactory.getLogger(HumanDelay.class);

    private HumanDelay() {}

    /**
     * Random pause between two HTTP requests within the same poll cycle
     * (e.g. after fetching the date list, before fetching time slots).
     * 2 – 6 seconds — feels like a human reading the page.
     */
    public static void betweenRequests() {
        pause("between-requests", 2_000, 6_000);
    }

    /**
     * Random pause before submitting the booking form.
     * 4 – 10 seconds — simulates a human reviewing the slot before clicking.
     */
    public static void beforeBooking() {
        pause("pre-booking", 4_000, 10_000);
    }

    /**
     * Random pause after a failed request before the next attempt.
     * Uses exponential back-off seeded with the attempt number.
     *
     * @param attempt 1-based attempt count
     */
    public static void backoff(int attempt) {
        long base = (long) Math.pow(2, attempt) * 1_000L;          // 2s, 4s, 8s …
        long jitter = ThreadLocalRandom.current().nextLong(0, base / 2);
        long total = Math.min(base + jitter, 120_000L);             // cap at 2 min
        log.info("Back-off wait: {}ms (attempt {})", total, attempt);
        sleep(total);
    }

    /**
     * Main poll interval — random value in [minSec, maxSec].
     *
     * @param minSec minimum seconds
     * @param maxSec maximum seconds
     */
    public static void pollInterval(int minSec, int maxSec) {
        long ms = ThreadLocalRandom.current().nextLong(
                (long) minSec * 1000, (long) maxSec * 1000 + 1);
        log.info("Next poll in {}s …", ms / 1000);
        sleep(ms);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void pause(String label, long minMs, long maxMs) {
        long ms = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        log.debug("Human pause [{}]: {}ms", label, ms);
        sleep(ms);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
