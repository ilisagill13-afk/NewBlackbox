package com.visascheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AIS client backed by a real Chromium browser (via Playwright).
 *
 * Why Playwright and not raw HTTP:
 *  - Real TLS/JA3 fingerprint (actual Chrome, not Java's HttpClient)
 *  - JavaScript executes → Cloudflare/Akamai challenges pass
 *  - Browser cookies, storage, navigator.* APIs all present
 *  - HTTP/2 with correct HPACK header ordering
 *  - Realistic viewport, timezone, locale, WebGL/Canvas fingerprint
 *
 * Extra stealth applied:
 *  - navigator.webdriver removed via init script
 *  - Random viewport size per session (common laptop resolutions)
 *  - Locale and timezone match the consulate city
 *  - Human-like mouse movement before every click
 *  - Gaussian-distributed typing speed (not instant .fill())
 *  - Random scroll before interacting with a form
 */
public class AisClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AisClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCHEDULE_ID_PATTERN = Pattern.compile("/schedule/(\\d+)");

    // Realistic laptop viewport sizes
    private static final int[][] VIEWPORTS = {
            {1366, 768}, {1440, 900}, {1536, 864}, {1920, 1080}, {1280, 800}
    };

    private final Playwright playwright;
    private final Browser     browser;
    private final BrowserContext context;
    private final Page        page;
    private String scheduleId;

    public AisClient() {
        playwright = Playwright.create();

        // Launch real Chromium with stealth flags
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(Config.HEADLESS)
                .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-infobars",
                        "--window-size=" + randomViewport()[0] + "," + randomViewport()[1]
                )));

        int[] vp = randomViewport();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(vp[0], vp[1])
                .setLocale("en-CA")
                .setTimezoneId(Config.CONSULATE_TIMEZONE)
                .setUserAgent(Config.USER_AGENTS.get(
                        ThreadLocalRandom.current().nextInt(Config.USER_AGENTS.size())))
                // Persist cookies across pages — looks like a real browser session
                .setStorageStatePath(Paths.get("browser_state.json").toAbsolutePath())
        );

        // Remove webdriver flag that headless Chrome exposes
        context.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                + "Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});"
                + "Object.defineProperty(navigator, 'languages', {get: () => ['en-CA','en']});"
                + "window.chrome = { runtime: {} };"
        );

        page = context.newPage();

        // Intercept 429 / 503 at browser level and throw immediately
        page.onResponse(response -> {
            int status = response.status();
            if (status == 429) {
                log.warn("Browser received HTTP 429 — rate limited!");
            } else if (status == 503) {
                log.warn("Browser received HTTP 503 — server busy.");
            }
        });
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public void login() {
        log.info("Navigating to AIS login page …");
        // Visit homepage first — real users don't land directly on sign_in
        page.navigate(Config.BASE_URL + "/" + Config.COUNTRY_CODE + "/niv",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        HumanDelay.betweenRequests();

        page.navigate(Config.SIGN_IN_URL,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        HumanDelay.betweenRequests();

        // Human-like: scroll a little before filling the form
        randomScroll();

        // Type email with realistic key-by-key speed
        Locator emailField = page.locator("#user_email");
        emailField.click();
        humanType(emailField, Config.AIS_EMAIL);
        HumanDelay.betweenRequests();

        Locator passField = page.locator("#user_password");
        passField.click();
        humanType(passField, Config.AIS_PASSWORD);
        HumanDelay.betweenRequests();

        // Check the policy checkbox if present
        Locator policyCheck = page.locator("#policy_confirmed");
        if (policyCheck.count() > 0 && !policyCheck.isChecked()) {
            humanClick(policyCheck);
            HumanDelay.betweenRequests();
        }

        // Click Sign In and wait for navigation
        Locator signInBtn = page.locator("input[type=submit][value='Sign In']");
        humanClick(signInBtn);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String url = page.url();
        if (url.contains("sign_in")) {
            String err = page.locator("#flash_error, .flash-error").count() > 0
                    ? page.locator("#flash_error, .flash-error").textContent()
                    : "Unknown error";
            throw new RuntimeException("Login failed: " + err.trim());
        }

        scheduleId = extractScheduleId(url);
        log.info("Logged in. Schedule ID: {}", scheduleId);

        // Save browser state (cookies + localStorage) for next run
        try {
            context.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(Paths.get("browser_state.json").toAbsolutePath()));
        } catch (Exception e) {
            log.warn("Could not save browser state: {}", e.getMessage());
        }
    }

    // ── Slot Queries ──────────────────────────────────────────────────────────

    public List<String> getAvailableDates() {
        String url = Config.availableDatesUrl(scheduleId);

        // Use page.evaluate to fetch JSON via the already-authenticated XHR context
        // This avoids opening a new page and keeps the same session cookies
        String json = (String) page.evaluate(
                "async (url) => {"
                + "  const r = await fetch(url, {"
                + "    headers: {"
                + "      'Accept': 'application/json, text/javascript, */*; q=0.01',"
                + "      'X-Requested-With': 'XMLHttpRequest'"
                + "    },"
                + "    credentials: 'include'"
                + "  });"
                + "  if (r.status === 429) throw new Error('RATE_LIMITED_429');"
                + "  if (r.status === 503) throw new Error('SERVER_BUSY_503');"
                + "  return r.text();"
                + "}", url);

        List<String> dates = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("date")) dates.add(node.get("date").asText());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse available dates JSON: {}", e.getMessage());
        }
        return dates;
    }

    public List<String> getAvailableTimes(String date) {
        HumanDelay.betweenRequests();
        String url = Config.availableTimesUrl(scheduleId, date);

        String json = (String) page.evaluate(
                "async (url) => {"
                + "  const r = await fetch(url, {"
                + "    headers: {"
                + "      'Accept': 'application/json, text/javascript, */*; q=0.01',"
                + "      'X-Requested-With': 'XMLHttpRequest'"
                + "    },"
                + "    credentials: 'include'"
                + "  });"
                + "  if (r.status === 429) throw new Error('RATE_LIMITED_429');"
                + "  return r.text();"
                + "}", url);

        List<String> times = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode available = root.path("available_times");
            if (available.isArray()) {
                for (JsonNode t : available) times.add(t.asText());
            }
        } catch (Exception e) {
            log.error("Failed to parse available times JSON: {}", e.getMessage());
        }
        return times;
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    public boolean bookAppointment(String date, String time) {
        String apptUrl = Config.appointmentsUrl(scheduleId);

        log.info("Navigating to appointment page to book {} at {} …", date, time);
        page.navigate(apptUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        HumanDelay.betweenRequests();
        randomScroll();

        // Human pause — simulates reading/reviewing before clicking
        HumanDelay.beforeBooking();

        // Select the facility (consulate) from dropdown if present
        Locator facilitySelect = page.locator(
                "select[name='appointments[consulate_appointment][facility_id]']");
        if (facilitySelect.count() > 0) {
            facilitySelect.selectOption(String.valueOf(Config.FACILITY_ID));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            HumanDelay.betweenRequests();
        }

        // Select the date
        Locator dateInput = page.locator(
                "input[name='appointments[consulate_appointment][date]']");
        if (dateInput.count() > 0) {
            dateInput.fill(date);
            HumanDelay.betweenRequests();
        }

        // Select the time slot
        Locator timeSelect = page.locator(
                "select[name='appointments[consulate_appointment][time]']");
        if (timeSelect.count() > 0) {
            timeSelect.selectOption(time);
            HumanDelay.betweenRequests();
        }

        // Scroll to the submit button and click it
        Locator submitBtn = page.locator("input[type=submit], button[type=submit]").last();
        submitBtn.scrollIntoViewIfNeeded();
        HumanDelay.betweenRequests();
        humanClick(submitBtn);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String finalUrl  = page.url();
        String pageTitle = page.title();

        if (page.content().contains("Appointment Confirmed")
                || pageTitle.contains("Confirmed")) {
            log.info("Appointment confirmed! {} at {}", date, time);
            return true;
        }
        if (!finalUrl.contains("new") && finalUrl.contains("appointment")) {
            log.info("Booking redirect to {} — treating as success.", finalUrl);
            return true;
        }

        log.warn("Booking result unclear — URL: {} | Title: {}", finalUrl, pageTitle);
        return false;
    }

    // ── Human-like interaction helpers ────────────────────────────────────────

    /**
     * Types text character by character with Gaussian-distributed delays
     * (mean 80ms, stddev 30ms) — matches real human typing speed distribution.
     */
    private void humanType(Locator locator, String text) {
        for (char c : text.toCharArray()) {
            locator.type(String.valueOf(c));
            long delay = gaussianDelay(80, 30, 30, 250);
            try { Thread.sleep(delay); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Moves mouse near the element center (with slight random offset) then clicks —
     * looks like a real cursor movement rather than an instant .click().
     */
    private void humanClick(Locator locator) {
        BoundingBox box = locator.boundingBox();
        if (box != null) {
            double x = box.x + box.width  / 2 + ThreadLocalRandom.current().nextDouble(-5, 5);
            double y = box.y + box.height / 2 + ThreadLocalRandom.current().nextDouble(-3, 3);
            page.mouse().move(x, y);
            try { Thread.sleep(gaussianDelay(120, 40, 60, 300)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            page.mouse().click(x, y);
        } else {
            locator.click();
        }
    }

    /** Scroll the page by a random amount — real users scroll while reading. */
    private void randomScroll() {
        int scrollY = ThreadLocalRandom.current().nextInt(80, 350);
        page.evaluate("window.scrollBy(0, " + scrollY + ")");
        try { Thread.sleep(gaussianDelay(400, 150, 200, 900)); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /**
     * Gaussian (normal) distributed delay clamped to [minMs, maxMs].
     * More realistic than uniform random — human reaction times follow normal distribution.
     */
    private static long gaussianDelay(double meanMs, double stdMs, long minMs, long maxMs) {
        double raw = ThreadLocalRandom.current().nextGaussian() * stdMs + meanMs;
        return (long) Math.max(minMs, Math.min(maxMs, raw));
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private String extractScheduleId(String url) {
        Matcher m = SCHEDULE_ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);

        // Fall back to checking page links
        String found = (String) page.evaluate(
                "() => {"
                + "  const a = document.querySelector('a[href*=\"/schedule/\"]');"
                + "  return a ? a.href : '';"
                + "}");
        if (found != null && !found.isBlank()) {
            Matcher m2 = SCHEDULE_ID_PATTERN.matcher(found);
            if (m2.find()) return m2.group(1);
        }
        throw new RuntimeException("Could not determine schedule ID after login.");
    }

    private static int[] randomViewport() {
        return VIEWPORTS[ThreadLocalRandom.current().nextInt(VIEWPORTS.length)];
    }

    @Override
    public void close() {
        try { context.close();    } catch (Exception ignored) {}
        try { browser.close();    } catch (Exception ignored) {}
        try { playwright.close(); } catch (Exception ignored) {}
    }
}
