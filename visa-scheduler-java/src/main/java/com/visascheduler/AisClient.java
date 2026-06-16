package com.visascheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client for the AIS portal.
 *
 * Anti-detection measures applied here:
 *  - Random User-Agent rotation (picked per request from Config.USER_AGENTS)
 *  - Full browser-like Accept / Accept-Language / Sec-Fetch-* headers
 *  - Human-like micro-pauses injected between sequential requests
 *  - 429 / 503 responses trigger exponential back-off, not instant retry
 */
public class AisClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AisClient.class);
    private static final Pattern SCHEDULE_ID_PATTERN = Pattern.compile("/schedule/(\\d+)");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CloseableHttpClient http;
    private String scheduleId;

    public AisClient() {
        this.http = HttpClients.custom()
                .setDefaultRequestConfig(
                        org.apache.hc.client5.http.config.RequestConfig.custom()
                                .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(30))
                                .setConnectionRequestTimeout(
                                        org.apache.hc.core5.util.Timeout.ofSeconds(30))
                                .setCookieSpec(StandardCookieSpec.RELAXED)
                                .build())
                // Follow redirects automatically so we land on the right page
                .disableRedirectHandling()
                .build();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public void login() throws IOException {
        log.info("Logging in as {} …", Config.AIS_EMAIL);

        String csrf = fetchCsrf(Config.SIGN_IN_URL);
        HumanDelay.betweenRequests();                    // pause after page load

        List<NameValuePair> form = List.of(
                new BasicNameValuePair("utf8",               "✓"),
                new BasicNameValuePair("authenticity_token", csrf),
                new BasicNameValuePair("user[email]",        Config.AIS_EMAIL),
                new BasicNameValuePair("user[password]",     Config.AIS_PASSWORD),
                new BasicNameValuePair("policy_confirmed",   "1"),
                new BasicNameValuePair("commit",             "Sign In")
        );

        HttpPost post = buildPost(Config.SIGN_IN_URL, form);
        post.setHeader("Referer",          Config.SIGN_IN_URL);
        post.setHeader("Origin",           Config.BASE_URL);
        post.setHeader("Sec-Fetch-Site",   "same-origin");
        post.setHeader("Sec-Fetch-Mode",   "navigate");
        post.setHeader("Sec-Fetch-User",   "?1");
        post.setHeader("Sec-Fetch-Dest",   "document");

        try (CloseableHttpResponse resp = http.execute(post)) {
            checkForRateLimit(resp, "login");
            String body     = readBody(resp);
            String finalUrl = headerOrFallback(resp, "Location", Config.SIGN_IN_URL);

            // Follow redirect manually since we disabled auto-redirect
            if (resp.getCode() / 100 == 3 && finalUrl.contains("schedule")) {
                scheduleId = extractScheduleId(finalUrl, "");
            } else {
                if (finalUrl.contains("sign_in") || body.contains("Invalid email or password")) {
                    throw new RuntimeException(
                            "Login failed — check AIS_EMAIL/AIS_PASSWORD.");
                }
                scheduleId = extractScheduleId(finalUrl, body);
            }
            log.info("Logged in. Schedule ID: {}", scheduleId);
        }
    }

    // ── Slot Queries ──────────────────────────────────────────────────────────

    public List<String> getAvailableDates() throws IOException {
        String url  = Config.availableDatesUrl(scheduleId);
        String json = getJson(url, Config.appointmentsUrl(scheduleId));

        List<String> dates = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("date")) dates.add(node.get("date").asText());
            }
        }
        return dates;
    }

    public List<String> getAvailableTimes(String date) throws IOException {
        HumanDelay.betweenRequests();                   // human pause before next call
        String url  = Config.availableTimesUrl(scheduleId, date);
        String json = getJson(url, Config.appointmentsUrl(scheduleId));

        List<String> times = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        JsonNode available = root.path("available_times");
        if (available.isArray()) {
            for (JsonNode t : available) times.add(t.asText());
        }
        return times;
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    public boolean bookAppointment(String date, String time) throws IOException {
        String apptUrl = Config.appointmentsUrl(scheduleId);

        // Re-load the appointment page to get a fresh CSRF
        String csrf;
        try {
            csrf = fetchCsrf(apptUrl);
        } catch (Exception e) {
            log.error("CSRF fetch before booking failed: {}", e.getMessage());
            return false;
        }

        // Simulate the human pausing to review the slot before clicking "Book"
        HumanDelay.beforeBooking();

        List<NameValuePair> form = List.of(
                new BasicNameValuePair("utf8",                                       "✓"),
                new BasicNameValuePair("authenticity_token",                         csrf),
                new BasicNameValuePair("confirmed_limit_message",                    "1"),
                new BasicNameValuePair("use_consulate_appointment_capacity",         "true"),
                new BasicNameValuePair("appointments[consulate_appointment][facility_id]",
                        String.valueOf(Config.FACILITY_ID)),
                new BasicNameValuePair("appointments[consulate_appointment][date]",  date),
                new BasicNameValuePair("appointments[consulate_appointment][time]",  time)
        );

        HttpPost post = buildPost(apptUrl, form);
        post.setHeader("Referer",        apptUrl);
        post.setHeader("Origin",         Config.BASE_URL);
        post.setHeader("Sec-Fetch-Site", "same-origin");
        post.setHeader("Sec-Fetch-Mode", "navigate");
        post.setHeader("Sec-Fetch-User", "?1");
        post.setHeader("Sec-Fetch-Dest", "document");

        try (CloseableHttpResponse resp = http.execute(post)) {
            checkForRateLimit(resp, "book");
            String body     = readBody(resp);
            String finalUrl = headerOrFallback(resp, "Location", apptUrl);
            int    status   = resp.getCode();

            if (status == 200 && body.contains("Appointment Confirmed")) {
                log.info("Appointment confirmed: {} at {}", date, time);
                return true;
            }
            if (status / 100 == 3 && !finalUrl.contains("new")
                    && finalUrl.contains("appointment")) {
                log.info("Booking redirect to: {} — treating as success", finalUrl);
                return true;
            }
            log.warn("Booking HTTP {} — URL: {}", status, finalUrl);
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchCsrf(String url) throws IOException {
        HttpGet get = buildGet(url, Config.BASE_URL);
        get.setHeader("Sec-Fetch-Mode", "navigate");
        get.setHeader("Sec-Fetch-Dest", "document");
        try (CloseableHttpResponse resp = http.execute(get)) {
            checkForRateLimit(resp, "csrf-fetch");
            String html = readBody(resp);
            Document doc  = Jsoup.parse(html);
            Element  meta = doc.selectFirst("meta[name=csrf-token]");
            if (meta == null) throw new RuntimeException("No CSRF token on: " + url);
            return meta.attr("content");
        }
    }

    private String getJson(String url, String referer) throws IOException {
        HttpGet get = buildGet(url, referer);
        get.setHeader("Accept",           "application/json, text/javascript, */*; q=0.01");
        get.setHeader("X-Requested-With", "XMLHttpRequest");
        get.setHeader("Sec-Fetch-Mode",   "cors");
        get.setHeader("Sec-Fetch-Dest",   "empty");
        try (CloseableHttpResponse resp = http.execute(get)) {
            checkForRateLimit(resp, "json");
            return readBody(resp);
        }
    }

    /**
     * Detects HTTP 429 (Too Many Requests) and 503 (rate-limit / maintenance)
     * and throws a descriptive exception so the caller's back-off logic fires.
     */
    private void checkForRateLimit(CloseableHttpResponse resp, String context) {
        int code = resp.getCode();
        if (code == 429) {
            // Honour Retry-After header if present
            var retryAfter = resp.getFirstHeader("Retry-After");
            String msg = "HTTP 429 Too Many Requests during [" + context + "]";
            if (retryAfter != null) msg += " — Retry-After: " + retryAfter.getValue() + "s";
            throw new RuntimeException(msg);
        }
        if (code == 503) {
            throw new RuntimeException("HTTP 503 during [" + context + "] — server busy");
        }
    }

    private String readBody(CloseableHttpResponse resp) throws IOException {
        var entity = resp.getEntity();
        if (entity == null) return "";
        return new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String headerOrFallback(CloseableHttpResponse resp, String name, String fallback) {
        var h = resp.getFirstHeader(name);
        return (h != null) ? h.getValue() : fallback;
    }

    private String extractScheduleId(String url, String html) {
        Matcher m = SCHEDULE_ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);

        Document doc = Jsoup.parse(html);
        for (Element a : doc.select("a[href]")) {
            Matcher am = SCHEDULE_ID_PATTERN.matcher(a.attr("href"));
            if (am.find()) return am.group(1);
        }
        throw new RuntimeException("Could not determine schedule ID.");
    }

    private String randomUserAgent() {
        List<String> agents = Config.USER_AGENTS;
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

    private HttpGet buildGet(String url, String referer) {
        HttpGet get = new HttpGet(url);
        get.setHeader("User-Agent",      randomUserAgent());
        get.setHeader("Accept",          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        get.setHeader("Accept-Language", "en-US,en;q=0.9");
        get.setHeader("Accept-Encoding", "gzip, deflate, br");
        get.setHeader("Referer",         referer);
        get.setHeader("Sec-Fetch-Site",  "same-origin");
        return get;
    }

    private HttpPost buildPost(String url, List<NameValuePair> form) {
        HttpPost post = new HttpPost(url);
        post.setHeader("User-Agent",      randomUserAgent());
        post.setHeader("Accept",          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        post.setHeader("Accept-Language", "en-US,en;q=0.9");
        post.setHeader("Accept-Encoding", "gzip, deflate, br");
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        return post;
    }

    @Override
    public void close() throws IOException {
        http.close();
    }
}
