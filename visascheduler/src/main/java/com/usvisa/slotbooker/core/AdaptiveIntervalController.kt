package com.usvisa.slotbooker.core

import kotlin.math.min
import kotlin.random.Random

/**
 * The "AI on the interval": an adaptive scheduler that decides how long to wait before the next
 * slot check. Goals, in priority order:
 *   1. Never get banned — stay under a safe hourly request budget (the AIS soft-ban kicks in around
 *      ~48 checks/window) and cool down hard when the site signals a rate limit.
 *   2. Catch the earliest slot — when everything looks healthy, check as fast as the budget allows.
 *   3. Look human — randomized jitter so the cadence is never robotic.
 *
 * It is fed the [Outcome] of each check and returns the next delay plus a human-readable [reason]
 * so the UI can show what the scheduler is "thinking".
 */
class AdaptiveIntervalController(
    private val preferredIntervalMs: Long,
    private val minIntervalMs: Long = 60_000L,            // never faster than 60s
    private val maxIntervalMs: Long = 20 * 60_000L,       // never slower than 20m when healthy
    private val hourlyBudget: Int = 40,                   // safe margin under the ~48/window soft-ban
    private val baseCooldownMs: Long = 30 * 60_000L       // recovery pause on a rate-limit signal
) {
    enum class Outcome { NO_SLOT, SLOT_FOUND, ERROR, RATE_LIMIT }

    private val requestTimes = ArrayDeque<Long>()
    private var consecutiveErrors = 0
    private var cooldownUntil = 0L
    private var lastReason = "starting"

    fun reason(): String = lastReason

    // --- State the AI advisor uses to reason about the next interval ---
    val budget: Int get() = hourlyBudget
    fun checksThisHour(now: Long = System.currentTimeMillis()): Int { prune(now); return requestTimes.size }
    fun consecutiveErrorCount(): Int = consecutiveErrors
    fun isCoolingDown(now: Long = System.currentTimeMillis()): Boolean = now < cooldownUntil

    /** Record what happened on the check that just finished. */
    fun record(outcome: Outcome, now: Long = System.currentTimeMillis()) {
        requestTimes.addLast(now)
        prune(now)
        when (outcome) {
            Outcome.RATE_LIMIT -> {
                consecutiveErrors++
                val mult = min(1 shl (consecutiveErrors - 1), 8) // 1,2,4,8
                cooldownUntil = now + baseCooldownMs * mult
            }
            Outcome.ERROR -> consecutiveErrors++
            else -> consecutiveErrors = 0
        }
    }

    /** Decide how long to wait before the next check, using the user's preferred cadence. */
    fun nextDelayMs(now: Long = System.currentTimeMillis()): Long =
        computeDelay(preferredIntervalMs, "rule-based", now)

    /**
     * Clamp a delay the AI proposed (in ms) through the same safety logic, so the model sets the
     * healthy cadence but can never breach the rate-limit floor, the hourly budget, or an active
     * cooldown/backoff. The safety layer always wins over the AI.
     */
    fun clampAiSuggestion(aiMs: Long, now: Long = System.currentTimeMillis()): Long =
        computeDelay(aiMs, "AI", now)

    private fun computeDelay(targetBase: Long, source: String, now: Long): Long {
        prune(now)
        val floor = maxOf(minIntervalMs, budgetSpacing(), budgetWait(now))

        if (now < cooldownUntil) {
            val remaining = (cooldownUntil - now).coerceAtLeast(floor)
            lastReason = "cooling down ${fmt(remaining)} (site rate-limit signal)"
            return jitter(remaining)
        }
        if (consecutiveErrors > 0) {
            val backoff = (preferredIntervalMs * min(1 shl consecutiveErrors, 8))
                .coerceAtMost(maxIntervalMs)
            val delay = maxOf(backoff, floor)
            lastReason = "backing off after $consecutiveErrors error(s) → ${fmt(delay)}"
            return jitter(delay)
        }
        val target = targetBase.coerceIn(minIntervalMs, maxIntervalMs)
        val delay = maxOf(target, floor)
        lastReason = "$source cadence — ${requestTimes.size}/$hourlyBudget checks this hour → ${fmt(delay)}"
        return jitter(delay)
    }

    /** Minimum spacing so we never exceed the hourly budget on a steady cadence. */
    private fun budgetSpacing(): Long = 3_600_000L / hourlyBudget

    /** If the budget is already spent, wait until the oldest request leaves the 1-hour window. */
    private fun budgetWait(now: Long): Long =
        if (requestTimes.size >= hourlyBudget && requestTimes.isNotEmpty())
            (requestTimes.first() + 3_600_000L - now).coerceAtLeast(0L)
        else 0L

    private fun prune(now: Long) {
        while (requestTimes.isNotEmpty() && now - requestTimes.first() > 3_600_000L) {
            requestTimes.removeFirst()
        }
    }

    /** Add 0–20% randomness so the cadence isn't robotic. */
    private fun jitter(base: Long): Long = base + Random.nextLong(0, (base / 5).coerceAtLeast(1L))

    private fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        return if (totalSec >= 60) "${totalSec / 60}m ${totalSec % 60}s" else "${totalSec}s"
    }
}
