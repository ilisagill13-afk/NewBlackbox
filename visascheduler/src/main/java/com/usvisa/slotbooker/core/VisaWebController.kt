package com.usvisa.slotbooker.core

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.usvisa.slotbooker.data.VisaConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns an off-screen [WebView] kept on the ais.usvisa-info.com origin so the injected automation
 * script can issue same-origin, cookie-authenticated fetch() calls. All WebView interaction happens
 * on the main thread; results flow back through [Bridge] into suspending calls.
 */
class VisaWebController(
    private val context: Context,
    private val config: VisaConfig
) {

    private val pathPrefix = "/${config.locale}/niv"
    private val appointmentUrl =
        "$BASE$pathPrefix/schedule/${config.scheduleId}/appointment"

    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val reqCounter = AtomicInteger(0)
    private var pageLoad: CompletableDeferred<Unit>? = null

    private var webView: WebView? = null
    private val automationScript: String by lazy {
        context.assets.open("visa_automation.js").bufferedReader().use { it.readText() }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onResult(reqId: String, json: String) {
            pending.remove(reqId)?.complete(json)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun start() = withContext(Dispatchers.Main) {
        if (webView != null) return@withContext
        CookieManager.getInstance().setAcceptCookie(true)
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(Bridge(), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(automationScript, null)
                    pageLoad?.complete(Unit)
                }
            }
        }
        loadAppointmentPage()
    }

    /**
     * Loads the appointment page exactly ONCE, to land on the site origin and capture the
     * session cookie + CSRF token. We deliberately never reload it during polling: a full page
     * reload is the heavy request that can reset our place in line and trip the anti-bot limiter.
     * Reused only on startup and when explicitly recovering after a re-login.
     */
    suspend fun loadAppointmentPage() = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext
        val load = CompletableDeferred<Unit>()
        pageLoad = load
        wv.loadUrl(appointmentUrl)
        withTimeout(PAGE_TIMEOUT_MS) { load.await() }
        // If the site bounced us to the login page, the session is gone.
        val loggedIn = JSONObject(call("isLoggedIn")).optBoolean("loggedIn", false)
        if (!loggedIn) throw SessionExpiredException()
    }

    /**
     * Returns the earliest available slot within [VisaConfig.minDate]..[VisaConfig.maxDate], or null.
     * Issues only the lightweight days/times JSON fetch on the already-loaded origin — no page
     * reload — so each check is a small same-origin XHR, never a reset.
     */
    suspend fun pollEarliestInRange(): Slot? {
        val days = fetchDays()
        val target = days.filter { it in config.minDate..config.maxDate }.minOrNull() ?: return null
        val times = fetchTimes(target)
        val time = times.minOrNull() ?: return null
        return Slot(target, time)
    }

    suspend fun book(slot: Slot): Boolean {
        // Uses the CSRF token captured at the initial page load. Rails per-session tokens stay
        // valid for the whole session, so booking needs no page reload either.
        val body = buildBookingBody(slot)
        val resp = JSONObject(call("book", pathPrefix, config.scheduleId, body))
        return resp.optBoolean("ok", false)
    }

    fun destroy() {
        webView?.let { wv ->
            wv.post {
                wv.stopLoading()
                wv.destroy()
            }
        }
        webView = null
    }

    private suspend fun fetchDays(): List<String> {
        val resp = JSONObject(call("getDays", pathPrefix, config.scheduleId, config.consulateFacilityId))
        if (!resp.optBoolean("ok")) handleError(resp)
        val arr = JSONArray(resp.getString("data"))
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("date")?.takeIf { it.isNotEmpty() }
        }
    }

    private suspend fun fetchTimes(date: String): List<String> {
        val resp = JSONObject(
            call("getTimes", pathPrefix, config.scheduleId, config.consulateFacilityId, date)
        )
        if (!resp.optBoolean("ok")) handleError(resp)
        val obj = JSONObject(resp.getString("data"))
        val arr = obj.optJSONArray("available_times") ?: JSONArray()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun handleError(resp: JSONObject): Nothing {
        val err = resp.optString("error")
        if (err.contains("SESSION_EXPIRED")) throw SessionExpiredException()
        throw IllegalStateException("Request failed: $err")
    }

    private fun buildBookingBody(slot: Slot): String {
        val parts = mutableListOf(
            "confirmed_limit_message=1",
            "use_consulate_appointment_capacity=true",
            "appointments[consulate_appointment][facility_id]=${enc(config.consulateFacilityId)}",
            "appointments[consulate_appointment][date]=${enc(slot.date)}",
            "appointments[consulate_appointment][time]=${enc(slot.time)}"
        )
        if (config.ascFacilityId.isNotBlank()) {
            // Biometrics facility carried through; ASC date/time mirror the consulate selection
            // when the consulate drives capacity. Adjust per-post if your post requires distinct ASC slots.
            parts += "appointments[asc_appointment][facility_id]=${enc(config.ascFacilityId)}"
        }
        return parts.joinToString("&")
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    /** Calls window.VisaBot.<func>(reqId, args...) and suspends until the JS reports back. */
    private suspend fun call(func: String, vararg args: String): String {
        val reqId = "r${reqCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String>()
        pending[reqId] = deferred
        val jsArgs = (listOf(reqId) + args).joinToString(",") { "'${jsEscape(it)}'" }
        val js = "window.VisaBot && window.VisaBot.$func($jsArgs);"
        withContext(Dispatchers.Main) { webView?.evaluateJavascript(js, null) }
        return try {
            withTimeout(CALL_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(reqId)
        }
    }

    private fun jsEscape(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

    private companion object {
        const val BASE = "https://ais.usvisa-info.com"
        const val PAGE_TIMEOUT_MS = 45_000L
        const val CALL_TIMEOUT_MS = 30_000L
    }
}
