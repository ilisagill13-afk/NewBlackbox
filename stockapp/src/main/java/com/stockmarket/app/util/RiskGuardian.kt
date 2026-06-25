package com.stockmarket.app.util

import com.stockmarket.app.model.TechnicalSignal

/**
 * Pre-buy risk gate.
 * Evaluates every signal before allowing purchase.
 * Returns a RiskReport that the UI shows to the user.
 */
object RiskGuardian {

    data class RiskReport(
        val approved: Boolean,
        val riskLevel: RiskLevel,
        val warnings: List<String>,
        val positives: List<String>,
        val maxSuggestedPct: Int   // max % of portfolio to put in this stock
    )

    enum class RiskLevel(val label: String, val color: String) {
        LOW("LOW RISK ✅", "#00C853"),
        MEDIUM("MEDIUM RISK ⚠", "#FFD600"),
        HIGH("HIGH RISK 🔴", "#FF6F00"),
        BLOCKED("DO NOT BUY ❌", "#FF1744")
    }

    fun assess(sig: TechnicalSignal, portfolioValue: Double, cashBalance: Double): RiskReport {
        val warnings = mutableListOf<String>()
        val positives = mutableListOf<String>()
        var riskPoints = 0

        // --- Positive signals ---
        if (sig.rsi in 50.0..70.0) positives.add("RSI in buy zone (${"%.1f".format(sig.rsi)})")
        if (sig.macdBullish) positives.add("MACD bullish crossover")
        if (sig.emaCrossoverBullish) positives.add("EMA9 > EMA21 uptrend")
        if (sig.aboveMa50) positives.add("Price above 50-day average")
        if (sig.aboveMa200) positives.add("Price above 200-day average (long-term bull)")
        if (sig.volumeSurge) positives.add("Volume surge (${"%.1f".format(sig.volumeRatio)}× avg) — strong confirmation")
        if (sig.consecutiveGainDays >= 2) positives.add("${sig.consecutiveGainDays} consecutive up days — momentum")

        // --- Risk warnings ---
        if (sig.rsi > 70) {
            warnings.add("RSI ${"%.1f".format(sig.rsi)} — OVERBOUGHT. High reversal risk.")
            riskPoints += 30
        }
        if (sig.rsi > 80) {
            warnings.add("RSI extremely high — very likely to pull back soon.")
            riskPoints += 20
        }
        if (!sig.macdBullish) {
            warnings.add("MACD bearish — trend may be weakening.")
            riskPoints += 15
        }
        if (!sig.emaCrossoverBullish) {
            warnings.add("EMA short-term below medium-term — no uptrend confirmed.")
            riskPoints += 10
        }
        if (!sig.aboveMa50) {
            warnings.add("Price below 50-day average — short-term bearish.")
            riskPoints += 15
        }
        if (!sig.aboveMa200) {
            warnings.add("Price below 200-day average — long-term bearish.")
            riskPoints += 20
        }
        if (sig.volumeRatio < 0.8) {
            warnings.add("Volume very low — price move may not be reliable.")
            riskPoints += 10
        }
        if (sig.signalScore < 65) {
            warnings.add("Signal score too low (${sig.signalScore}/100) — weak setup.")
            riskPoints += 30
        }

        // --- Risk Level ---
        val riskLevel = when {
            riskPoints >= 50 -> RiskLevel.BLOCKED
            riskPoints >= 30 -> RiskLevel.HIGH
            riskPoints >= 15 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val approved = riskLevel != RiskLevel.BLOCKED

        // --- Position sizing (max % of total portfolio) ---
        val maxPct = when (riskLevel) {
            RiskLevel.LOW -> 20       // max 20% in one stock
            RiskLevel.MEDIUM -> 10   // max 10%
            RiskLevel.HIGH -> 5      // max 5%
            RiskLevel.BLOCKED -> 0
        }

        return RiskReport(approved, riskLevel, warnings, positives, maxPct)
    }
}
