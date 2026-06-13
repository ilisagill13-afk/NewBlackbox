package com.usvisa.slotbooker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.usvisa.slotbooker.core.AdaptiveIntervalController
import com.usvisa.slotbooker.core.RateLimitException
import com.usvisa.slotbooker.core.SessionExpiredException
import com.usvisa.slotbooker.core.VisaWebController
import com.usvisa.slotbooker.data.ConfigStore
import com.usvisa.slotbooker.data.VisaConfig
import com.usvisa.slotbooker.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls the visa site on an interval and auto-books the first slot found
 * inside the configured date range, then stops. Fully automatic — no confirmation step.
 */
class VisaMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var controller: VisaWebController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        startForeground(Notifications.ONGOING_ID, Notifications.ongoing(this, "Starting monitor…"))
        if (loop == null) {
            loop = scope.launch { runLoop(ConfigStore(this@VisaMonitorService).load()) }
        }
        return START_STICKY
    }

    private suspend fun runLoop(config: VisaConfig) {
        MonitorState.setRunning(true)
        if (!config.isComplete) {
            MonitorState.setStatus("Configuration incomplete")
            MonitorState.append("Stopped: configuration is incomplete.")
            stopSelf()
            return
        }

        val web = VisaWebController(applicationContext, config).also { controller = it }
        try {
            web.start()
        } catch (e: SessionExpiredException) {
            notifyRelogin()
            stopSelf()
            return
        } catch (e: Exception) {
            MonitorState.append("Failed to start browser: ${e.message}")
            stopSelf()
            return
        }

        val scheduler = AdaptiveIntervalController(
            preferredIntervalMs = config.pollIntervalMinutes
                .coerceAtLeast(VisaConfig.MIN_POLL_MINUTES) * 60_000L
        )
        while (scope.isActive) {
            var outcome = AdaptiveIntervalController.Outcome.NO_SLOT
            try {
                MonitorState.setStatus("Checking…")
                val slot = web.pollEarliestInRange()
                if (slot != null) {
                    outcome = AdaptiveIntervalController.Outcome.SLOT_FOUND
                    MonitorState.append("Slot found: ${slot.date} ${slot.time} — booking…")
                    val booked = web.book(slot)
                    if (booked) {
                        MonitorState.append("BOOKED ${slot.date} ${slot.time}")
                        MonitorState.setStatus("Booked ${slot.date} ${slot.time}")
                        Notifications.alert(
                            this,
                            "Appointment booked!",
                            "Your visa appointment was booked for ${slot.date} at ${slot.time}."
                        )
                        stopSelf()
                        return
                    } else {
                        MonitorState.append("Booking attempt failed; will retry.")
                    }
                } else {
                    MonitorState.append("No slot in range yet.")
                }
            } catch (e: SessionExpiredException) {
                notifyRelogin()
                stopSelf()
                return
            } catch (e: RateLimitException) {
                outcome = AdaptiveIntervalController.Outcome.RATE_LIMIT
                MonitorState.append("Rate-limit signal from the site.")
            } catch (e: Exception) {
                outcome = AdaptiveIntervalController.Outcome.ERROR
                MonitorState.append("Error: ${e.message}")
            }

            scheduler.record(outcome)
            val delayMs = scheduler.nextDelayMs()
            val reason = scheduler.reason()
            MonitorState.setStatus("Next check: $reason")
            MonitorState.append("AI scheduler: $reason")
            updateOngoing(reason)
            delay(delayMs)
        }
    }

    private fun updateOngoing(text: String) {
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(Notifications.ONGOING_ID, Notifications.ongoing(this, text))
    }

    private fun notifyRelogin() {
        MonitorState.setStatus("Session expired — log in again")
        MonitorState.append("Session expired. Open the app and log in again.")
        Notifications.alert(
            this,
            "Login required",
            "Your visa session expired. Open the app and log in again to resume monitoring."
        )
    }

    override fun onDestroy() {
        MonitorState.setRunning(false)
        loop?.cancel()
        controller?.destroy()
        controller = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, VisaMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VisaMonitorService::class.java))
        }
    }
}
