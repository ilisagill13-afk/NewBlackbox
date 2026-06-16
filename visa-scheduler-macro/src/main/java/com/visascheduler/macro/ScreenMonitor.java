package com.visascheduler.macro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Watches a screen region (your real, manually logged-in browser window) for
 * visual changes by pressing F5 and diffing screenshots.
 *
 * No WebDriver, no Chrome DevTools Protocol, no automation flag of any kind —
 * to AIS's server this looks exactly like a person who keeps refreshing the
 * page and reading it. The only thing this code controls is the OS-level
 * mouse/keyboard, exactly the way a human would.
 *
 * IMPORTANT: keep the target browser window visible and focused while this
 * runs. If you switch focus to another window, F5 will refresh THAT window
 * instead — pause the monitor (Ctrl+C) before using your computer for
 * anything else, or run it on a dedicated machine/VM.
 */
public class ScreenMonitor {

    private static final Logger log = LoggerFactory.getLogger(ScreenMonitor.class);
    private static final LocalTime BUSINESS_START = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_END   = LocalTime.of(17, 0);

    private final Robot robot;
    private final Rectangle region;
    private final ZoneId zone;

    private BufferedImage baseline;
    private int dailyCount = 0;
    private LocalDate lastCountDate = LocalDate.MIN;

    public ScreenMonitor() throws AWTException {
        if (!MacroConfig.calibrated) {
            throw new IllegalStateException(
                    "No calibration found. Run with --calibrate first.");
        }
        this.robot  = new Robot();
        this.robot.setAutoWaitForIdle(true);
        this.region = new Rectangle(MacroConfig.regionX, MacroConfig.regionY,
                MacroConfig.regionW, MacroConfig.regionH);
        this.zone   = ZoneId.of(MacroConfig.CONSULATE_TIMEZONE);

        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        if (!screen.contains(region)) {
            log.warn("Calibrated region {} extends outside current screen size {} — "
                    + "did your resolution change since calibration?", region, screen);
        }
    }

    public void run() throws IOException {
        log.info("Capturing baseline screenshot of region {} …", region);
        baseline = capture();
        saveImage(baseline, "baseline.png");
        log.info("Baseline saved to baseline.png. Monitoring started.");
        log.info("Poll interval: {}-{}s | daily cap: {} | business hours only: {} ({})",
                MacroConfig.POLL_MIN_SECONDS, MacroConfig.POLL_MAX_SECONDS,
                MacroConfig.MAX_DAILY_POLLS, MacroConfig.BUSINESS_HOURS_ONLY, zone);

        while (true) {
            if (MacroConfig.BUSINESS_HOURS_ONLY && !isBusinessHours()) {
                long secs = secondsUntilBusinessHours();
                log.info("Outside business hours — sleeping ~{} min.", secs / 60);
                sleepSeconds(secs);
                continue;
            }

            resetDailyCountIfNewDay();
            if (dailyCount >= MacroConfig.MAX_DAILY_POLLS) {
                long secs = secondsUntilMidnight();
                log.info("Daily check cap ({}) reached — sleeping until midnight.",
                        MacroConfig.MAX_DAILY_POLLS);
                sleepSeconds(secs);
                continue;
            }

            try {
                dailyCount++;
                log.info("Check #{} today …", dailyCount);
                checkOnce();
            } catch (Exception e) {
                log.error("Check failed: {}", e.getMessage(), e);
            }

            HumanDelay.pollInterval();
        }
    }

    private void checkOnce() throws IOException {
        pressRefresh();
        HumanDelay.afterRefresh();

        BufferedImage current = capture();
        double score = ImageDiff.score(baseline, current);
        log.info("Diff score vs baseline: {} (threshold {})", score, MacroConfig.DIFF_THRESHOLD);

        if (score > MacroConfig.DIFF_THRESHOLD) {
            String fileName = "alert_" + System.currentTimeMillis() + ".png";
            Path path = saveImage(current, fileName);
            log.info("Change detected! Screenshot saved to {}", path);

            Notifier.notify(
                    "Possible Visa Slot Change Detected!",
                    "The monitored area on your AIS page changed (diff score "
                            + String.format("%.1f", score) + "). "
                            + "Go check your browser RIGHT NOW and book manually if a slot is available — "
                            + "slots can disappear within minutes.",
                    path);

            HumanDelay.postAlertCooldown();

            // After the cooldown, re-baseline against current state so we don't
            // keep re-alerting on the same (possibly already-gone) change.
            baseline = capture();
        }
    }

    /** Sends F5 to whatever window currently has focus — keep your browser focused. */
    private void pressRefresh() {
        // Tiny random pre-press pause — avoids perfectly periodic key timing
        sleepMs(ThreadLocalRandom.current().nextInt(150, 600));
        robot.keyPress(KeyEvent.VK_F5);
        robot.keyRelease(KeyEvent.VK_F5);
    }

    private BufferedImage capture() {
        return robot.createScreenCapture(region);
    }

    private Path saveImage(BufferedImage img, String fileName) throws IOException {
        File f = new File(fileName);
        ImageIO.write(img, "png", f);
        return f.toPath();
    }

    // ── Time helpers ──────────────────────────────────────────────────────────

    private boolean isBusinessHours() {
        LocalTime now = ZonedDateTime.now(zone).toLocalTime();
        return !now.isBefore(BUSINESS_START) && now.isBefore(BUSINESS_END);
    }

    private long secondsUntilBusinessHours() {
        ZonedDateTime now  = ZonedDateTime.now(zone);
        ZonedDateTime open = now.toLocalDate().atTime(BUSINESS_START).atZone(zone);
        if (now.isAfter(open)) open = open.plusDays(1);
        return java.time.Duration.between(now, open).getSeconds() + 60;
    }

    private long secondsUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return java.time.Duration.between(now, midnight).getSeconds() + 60;
    }

    private void resetDailyCountIfNewDay() {
        LocalDate today = ZonedDateTime.now(zone).toLocalDate();
        if (!today.equals(lastCountDate)) {
            dailyCount = 0;
            lastCountDate = today;
        }
    }

    private void sleepSeconds(long secs) { sleepMs(secs * 1000L); }

    private void sleepMs(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
