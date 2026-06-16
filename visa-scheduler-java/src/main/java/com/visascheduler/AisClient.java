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
import org.apache.hc.client5.http.impl.cookie.RFC6265CookieSpecFactory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client that talks to the AIS (Appointment Information System) portal.
 * Maintains cookies across requests so the session stays authenticated.
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
                                .setResponseTimeout(
                                        org.apache.hc.core5.util.Timeout.ofSeconds(30))
                                .setConnectionRequestTimeout(
                                        org.apache.hc.core5.util.Timeout.ofSeconds(30))
                                .setCookieSpec(StandardCookieSpec.RELAXED)
                                .build())
                .build();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Log in to AIS and populate {@code scheduleId}.
     *
     * @throws IOException          on network error
     * @throws RuntimeException     on login failure
     */
    public void login() throws IOException {
        log.info("Logging in as {} …", Config.AIS_EMAIL);

        String csrf = fetchCsrf(Config.SIGN_IN_URL);

        List<NameValuePair> form = List.of(
                new BasicNameValuePair("utf8",                   "✓"),
                new BasicNameValuePair("authenticity_token",     csrf),
                new BasicNameValuePair("user[email]",            Config.AIS_EMAIL),
                new BasicNameValuePair("user[password]",         Config.AIS_PASSWORD),
                new BasicNameValuePair("policy_confirmed",       "1"),
                new BasicNameValuePair("commit",                 "Sign In")
        );

        HttpPost post = buildPost(Config.SIGN_IN_URL, form);
        post.setHeader("Referer", Config.SIGN_IN_URL);

        try (CloseableHttpResponse resp = http.execute(post)) {
            String body = new String(resp.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8);
            String finalUrl = extractFinalUrl(resp, Config.SIGN_IN_URL);

            if (finalUrl.contains("sign_in")) {
                throw new RuntimeException(
                        "Login failed — check AIS_EMAIL/AIS_PASSWORD. Final URL: " + finalUrl);
            }

            scheduleId = extractScheduleId(finalUrl, body);
            log.info("Logged in successfully. Schedule ID: {}", scheduleId);
        }
    }

    private String extractFinalUrl(CloseableHttpResponse resp, String fallback) {
        var location = resp.getFirstHeader("Location");
        return (location != null) ? location.getValue() : fallback;
    }

    private String extractScheduleId(String url, String html) {
        Matcher m = SCHEDULE_ID_PATTERN.matcher(url);
        if (m.find()) return m.group(1);

        Document doc = Jsoup.parse(html);
        for (Element a : doc.select("a[href]")) {
            Matcher am = SCHEDULE_ID_PATTERN.matcher(a.attr("href"));
            if (am.find()) return am.group(1);
        }
        throw new RuntimeException("Could not determine schedule ID after login.");
    }

    // ── Slot Queries ──────────────────────────────────────────────────────────

    /** Returns list of available appointment dates (YYYY-MM-DD). */
    public List<String> getAvailableDates() throws IOException {
        String url = Config.availableDatesUrl(scheduleId);
        String json = getJson(url);

        List<String> dates = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("date")) {
                    dates.add(node.get("date").asText());
                }
            }
        }
        return dates;
    }

    /** Returns available time slots (e.g. "08:30") for a given date. */
    public List<String> getAvailableTimes(String date) throws IOException {
        String url = Config.availableTimesUrl(scheduleId, date);
        String json = getJson(url);

        List<String> times = new ArrayList<>();
        JsonNode root = JSON.readTree(json);
        JsonNode available = root.path("available_times");
        if (available.isArray()) {
            for (JsonNode t : available) {
                times.add(t.asText());
            }
        }
        return times;
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    /**
     * Submit the appointment booking form.
     *
     * @return true if the booking was confirmed
     */
    public boolean bookAppointment(String date, String time) throws IOException {
        String apptUrl = Config.appointmentsUrl(scheduleId);

        String csrf;
        try {
            csrf = fetchCsrf(apptUrl);
        } catch (Exception e) {
            log.error("Could not fetch CSRF before booking: {}", e.getMessage());
            return false;
        }

        List<NameValuePair> form = List.of(
                new BasicNameValuePair("utf8",                                          "✓"),
                new BasicNameValuePair("authenticity_token",                            csrf),
                new BasicNameValuePair("confirmed_limit_message",                       "1"),
                new BasicNameValuePair("use_consulate_appointment_capacity",            "true"),
                new BasicNameValuePair("appointments[consulate_appointment][facility_id]",
                        String.valueOf(Config.FACILITY_ID)),
                new BasicNameValuePair("appointments[consulate_appointment][date]",     date),
                new BasicNameValuePair("appointments[consulate_appointment][time]",     time)
        );

        HttpPost post = buildPost(apptUrl, form);
        post.setHeader("Referer", apptUrl);

        try (CloseableHttpResponse resp = http.execute(post)) {
            String body = new String(resp.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8);
            String finalUrl = extractFinalUrl(resp, apptUrl);

            int status = resp.getCode();
            if (status == 200 && body.contains("Appointment Confirmed")) {
                log.info("Appointment confirmed on page: {} at {}", date, time);
                return true;
            }
            if (!finalUrl.contains("new") && finalUrl.contains("appointment")) {
                log.info("Booking likely succeeded — redirected to: {}", finalUrl);
                return true;
            }

            log.warn("Booking response HTTP {} — final URL: {}", status, finalUrl);
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchCsrf(String url) throws IOException {
        HttpGet get = buildGet(url);
        try (CloseableHttpResponse resp = http.execute(get)) {
            String html = new String(resp.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);
            Element meta = doc.selectFirst("meta[name=csrf-token]");
            if (meta == null) {
                throw new RuntimeException("CSRF token not found on: " + url);
            }
            return meta.attr("content");
        }
    }

    private String getJson(String url) throws IOException {
        HttpGet get = buildGet(url);
        get.setHeader("Accept",           "application/json, text/javascript, */*; q=0.01");
        get.setHeader("X-Requested-With", "XMLHttpRequest");
        try (CloseableHttpResponse resp = http.execute(get)) {
            return new String(resp.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private HttpGet buildGet(String url) {
        HttpGet get = new HttpGet(url);
        get.setHeader("User-Agent",       Config.USER_AGENT);
        get.setHeader("Accept-Language",  "en-US,en;q=0.9");
        get.setHeader("Referer",          Config.BASE_URL);
        return get;
    }

    private HttpPost buildPost(String url, List<NameValuePair> form) {
        HttpPost post = new HttpPost(url);
        post.setHeader("User-Agent",      Config.USER_AGENT);
        post.setHeader("Accept-Language", "en-US,en;q=0.9");
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        return post;
    }

    @Override
    public void close() throws IOException {
        http.close();
    }
}
