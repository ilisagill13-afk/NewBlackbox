package com.visascheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
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
 * AIS client — real Chromium via Playwright with full stealth stack:
 *
 *  Layer 1 — Browser:  Headless Chromium with CDP artifact flags removed,
 *                       automation-control flag disabled at launch.
 *
 *  Layer 2 — JS patches (StealthScripts):  navigator.webdriver, plugins,
 *                       permissions, outerWidth/Height, canvas noise,
 *                       audio noise, WebRTC block, Chrome runtime object.
 *
 *  Layer 3 — Session:  Injects real browser cookies from cookies.json when
 *                       present — the authenticated session has genuine history.
 *                       Falls back to automated login with human-like typing.
 *
 *  Layer 4 — Behaviour: Warm-up page visit before checking slots; random
 *                       scroll before every form; Gaussian typing/click delays.
 *
 * Most important thing NOT handled here: run this on your HOME IP, never
 * on a VPS/cloud server whose IP is in a datacenter block.
 */
public class AisClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AisClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCHEDULE_ID_PATTERN = Pattern.compile("/schedule/(\\d+)");

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

        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(Config.HEADLESS)
                .setArgs(List.of(
                        // Disable the flag that exposes Chrome as controlled
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-infobars",
                        "--disable-extensions",
                        "--disable-plugins-discovery",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-background-timer-throttling",
                        "--disable-renderer-backgrounding",
                        "--disable-backgrounding-occluded-windows"
                )));

        int[] vp = randomViewport();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(vp[0], vp[1])
                .setLocale("en-CA")
                .setTimezoneId(Config.CONSULATE_TIMEZONE)
                .setUserAgent(Config.USER_AGENTS.get(
                        ThreadLocalRandom.current().nextInt(Config.USER_AGENTS.size())))
                .setJavaScriptEnabled(true)
                .setIgnoreHTTPSErrors(false)
        );

        // Inject all stealth patches before ANY page script runs
        context.addInitScript(StealthScripts.ALL);

        page = context.newPage();

        page.onResponse(response -> {
            if (response.status() == 429) {
                throw new RuntimeException("HTTP 429 Too Many Requests — rate limited!");
            }
        });
    }

    // ── Session bootstrap ─────────────────────────────────────────────────────

    /**
     * Establishes an authenticated AIS session.
     * Priority:
     *  1. Inject cookies from cookies.json (user's real Chrome session)
     *  2. Fall back to automated form login
     */
    public void login() throws Exception {
        boolean cookiesLoaded = CookieImporter.injectIfPresent(context);

        if (cookiesLoaded) {
            // Verify the injected session is still valid by visiting the dashboard
            log.info("Verifying injected cookie session …");
            page.navigate(Config.BASE_URL + "/" + Config.COUNTRY_CODE + "/niv",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            HumanDelay.betweenRequests();

            String url = page.url();
            if (url.contains("sign_in")) {
                log.warn("Injected cookies have expired — falling back to form login.");
                formLogin();
            } else {
                scheduleId = extractScheduleId(url, "");
                log.info("Cookie session valid. Schedule ID: {}", scheduleId);
            }
        } else {
            warmUpBrowsing();  // visit a couple of pages before logging in
            formLogin();
        }
    }

    /** Visit public AIS pages first — real users don't arrive directly at /sign_in. */
    private void warmUpBrowsing() throws Exception {
        log.info("Warm-up: visiting AIS public pages …");
        page.navigate(Config.BASE_URL + "/" + Config.COUNTRY_CODE + "/niv",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        randomScroll();
        HumanDelay.betweenRequests();

        // Optionally visit the "Apply for a visa" page a real user might read
        page.navigate(Config.BASE_URL + "/" + Config.COUNTRY_CODE + "/niv/information/iv_services",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        randomScroll();
        HumanDelay.betweenRequests();
    }

    private void formLogin() throws Exception {
        log.info("Logging in via form as {} …", Config.AIS_EMAIL);

        page.navigate(Config.SIGN_IN_URL,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        HumanDelay.betweenRequests();
        randomScroll();

        Locator emailField = page.locator("#user_email");
        emailField.click();
        humanType(emailField, Config.AIS_EMAIL);
        HumanDelay.betweenRequests();

        Locator passField = page.locator("#user_password");
        passField.click();
        humanType(passField, Config.AIS_PASSWORD);
        HumanDelay.betweenRequests();

        Locator policyCheck = page.locator("#policy_confirmed");
        if (policyCheck.count() > 0 && !policyCheck.isChecked()) {
            humanClick(policyCheck);
            HumanDelay.betweenRequests();
        }

        Locator signInBtn = page.locator("input[type=submit][value='Sign In']");
        humanClick(signInBtn);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String url = page.url();
        if (url.contains("sign_in")) {
            String err = page.locator("#flash_error, .flash-error").count() > 0
                    ? page.locator("#flash_error, .flash-error").textContent().trim()
                    : "Unknown error";
            throw new RuntimeException("Login failed: " + err);
        }

        scheduleId = extractScheduleId(url, page.content());
        log.info("Logged in. Schedule ID: {}", scheduleId);

        // Save session state for next run
        try {
            context.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(Paths.get("browser_state.json").toAbsolutePath()));
            log.info("Session state saved to browser_state.json.");
        } catch (Exception e) {
            log.warn("Could not save session state: {}", e.getMessage());
        }
    }

    // ── Slot Queries ──────────────────────────────────────────────────────────

    public List<String> getAvailableDates() throws Exception {
        String url  = Config.availableDatesUrl(scheduleId);
        String json = xhrFetch(url);

        List<String> dates = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("date")) dates.add(node.get("date").asText());
            }
        }
        return dates;
    }

    public List<String> getAvailableTimes(String date) throws Exception {
        HumanDelay.betweenRequests();
        String url  = Config.availableTimesUrl(scheduleId, date);
        String json = xhrFetch(url);

        List<String> times = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        JsonNode available = root.path("available_times");
        if (available.isArray()) {
            for (JsonNode t : available) times.add(t.asText());
        }
        return times;
    }

    /**
     * Fetch a JSON API endpoint from inside the already-authenticated browser
     * page using the browser's own fetch() — same cookies, same TLS session,
     * same Origin header as a human clicking a button.
     */
    private String xhrFetch(String url) {
        Object result = page.evaluate("""
            async (url) => {
              const r = await fetch(url, {
                headers: {
                  'Accept': 'application/json, text/javascript, */*; q=0.01',
                  'X-Requested-With': 'XMLHttpRequest'
                },
                credentials: 'include'
              });
              if (r.status === 429) throw new Error('RATE_LIMITED_429');
              if (r.status === 503) throw new Error('SERVER_BUSY_503');
              if (!r.ok) throw new Error('HTTP_' + r.status);
              return r.text();
            }
        """, url);
        if (result == null) throw new RuntimeException("Empty response from: " + url);
        return result.toString();
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    public boolean bookAppointment(String date, String time) throws Exception {
        String apptUrl = Config.appointmentsUrl(scheduleId);

        log.info("Navigating to appointment page …");
        page.navigate(apptUrl,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        HumanDelay.betweenRequests();
        randomScroll();
        HumanDelay.beforeBooking();        // 4–10s human reading pause

        // Select facility
        Locator facilitySelect = page.locator(
                "select[name='appointments[consulate_appointment][facility_id]']");
        if (facilitySelect.count() > 0) {
            facilitySelect.selectOption(String.valueOf(Config.FACILITY_ID));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            HumanDelay.betweenRequests();
        }

        // Pick date
        Locator dateInput = page.locator(
                "input[name='appointments[consulate_appointment][date]']");
        if (dateInput.count() > 0) {
            dateInput.fill(date);
            HumanDelay.betweenRequests();
        }

        // Pick time
        Locator timeSelect = page.locator(
                "select[name='appointments[consulate_appointment][time]']");
        if (timeSelect.count() > 0) {
            timeSelect.selectOption(time);
            HumanDelay.betweenRequests();
        }

        Locator submitBtn = page.locator("input[type=submit], button[type=submit]").last();
        submitBtn.scrollIntoViewIfNeeded();
        HumanDelay.betweenRequests();
        humanClick(submitBtn);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String finalUrl  = page.url();
        String content   = page.content();
        String pageTitle = page.title();

        if (content.contains("Appointment Confirmed") || pageTitle.contains("Confirmed")) {
            return true;
        }
        if (!finalUrl.contains("new") && finalUrl.contains("appointment")) {
            log.info("Redirect to {} — treating as success.", finalUrl);
            return true;
        }
        log.warn("Booking unclear — URL: {} | Title: {}", finalUrl, pageTitle);
        return false;
    }

    // ── Human-like helpers ────────────────────────────────────────────────────

    private void humanType(Locator locator, String text) {
        for (char c : text.toCharArray()) {
            locator.type(String.valueOf(c));
            sleep(gaussianDelay(85, 35, 30, 280));
        }
    }

    private void humanClick(Locator locator) {
        BoundingBox box = locator.boundingBox();
        if (box != null) {
            double x = box.x + box.width  / 2.0 + jitter(6);
            double y = box.y + box.height / 2.0 + jitter(4);
            page.mouse().move(x, y);
            sleep(gaussianDelay(130, 45, 60, 320));
            page.mouse().click(x, y);
        } else {
            locator.click();
        }
    }

    private void randomScroll() {
        int scrollY = ThreadLocalRandom.current().nextInt(60, 320);
        page.evaluate("window.scrollBy(0, " + scrollY + ")");
        sleep(gaussianDelay(450, 150, 200, 900));
    }

    private static long gaussianDelay(double mean, double std, long min, long max) {
        double v = ThreadLocalRandom.current().nextGaussian() * std + mean;
        return (long) Math.max(min, Math.min(max, v));
    }

    private static double jitter(double range) {
        return ThreadLocalRandom.current().nextDouble(-range, range);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private String extractScheduleId(String url, String html) {
        Matcher m = SCHEDULE_ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);

        if (!html.isBlank()) {
            Matcher m2 = SCHEDULE_ID_PATTERN.matcher(html);
            if (m2.find()) return m2.group(1);
        }

        // Try from live DOM
        Object found = page.evaluate(
                "() => { const a = document.querySelector('a[href*=\"/schedule/\"]');"
                + " return a ? a.href : ''; }");
        if (found instanceof String s && !s.isBlank()) {
            Matcher m3 = SCHEDULE_ID_PATTERN.matcher(s);
            if (m3.find()) return m3.group(1);
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
