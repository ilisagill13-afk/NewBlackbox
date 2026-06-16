package com.visascheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.SameSiteAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Imports session cookies from a JSON file exported from the user's REAL
 * Chrome browser, so the scheduler inherits an already-authenticated session
 * that has genuine browsing history — the strongest anti-detection measure.
 *
 * HOW TO EXPORT COOKIES FROM YOUR REAL CHROME:
 *
 *  1. Install the Chrome extension "Cookie-Editor" (free, open source).
 *  2. Log in to https://ais.usvisa-info.com/en-ca/niv manually.
 *  3. Click Cookie-Editor → Export → "JSON" → copy to clipboard.
 *  4. Paste into a file and save as: cookies.json  (same directory as the JAR).
 *
 * The scheduler will load these cookies automatically on startup, bypassing
 * the login page entirely (and its bot-detection) for subsequent runs.
 *
 * When cookies expire (AIS sessions last ~30 days), delete cookies.json and
 * log in again manually to get fresh ones.
 */
public final class CookieImporter {

    private static final Logger log = LoggerFactory.getLogger(CookieImporter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private CookieImporter() {}

    /**
     * Loads cookies.json and injects all AIS-domain cookies into the browser
     * context. Returns true if at least one session cookie was injected.
     */
    public static boolean injectIfPresent(BrowserContext context) {
        File cookieFile = new File("cookies.json");
        if (!cookieFile.exists()) {
            log.info("No cookies.json found — will use normal login.");
            return false;
        }

        try {
            JsonNode root = JSON.readTree(cookieFile);
            if (!root.isArray() || root.isEmpty()) {
                log.warn("cookies.json is empty or not an array — ignoring.");
                return false;
            }

            List<com.microsoft.playwright.options.Cookie> playwrightCookies = new ArrayList<>();
            for (JsonNode c : root) {
                String domain = c.path("domain").asText("");
                if (!domain.contains("usvisa-info.com")) continue;

                var cookie = new com.microsoft.playwright.options.Cookie(
                        c.path("name").asText(""),
                        c.path("value").asText("")
                );
                cookie.setDomain(domain);
                cookie.setPath(c.path("path").asText("/"));
                cookie.setSecure(c.path("secure").asBoolean(false));
                cookie.setHttpOnly(c.path("httpOnly").asBoolean(false));

                // sameSite
                String ss = c.path("sameSite").asText("Lax");
                cookie.setSameSite(switch (ss.toLowerCase()) {
                    case "strict" -> SameSiteAttribute.STRICT;
                    case "none"   -> SameSiteAttribute.NONE;
                    default       -> SameSiteAttribute.LAX;
                });

                // expiry (Cookie-Editor exports as Unix timestamp in "expirationDate")
                if (c.has("expirationDate")) {
                    cookie.setExpires(c.get("expirationDate").asDouble());
                }

                playwrightCookies.add(cookie);
            }

            if (playwrightCookies.isEmpty()) {
                log.warn("cookies.json had no ais.usvisa-info.com cookies.");
                return false;
            }

            context.addCookies(playwrightCookies);
            log.info("Injected {} cookies from cookies.json — skipping login page.",
                    playwrightCookies.size());
            return true;

        } catch (Exception e) {
            log.error("Failed to load cookies.json: {}", e.getMessage());
            return false;
        }
    }
}
