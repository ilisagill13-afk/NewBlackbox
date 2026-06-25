package com.stockmarket.app.model

data class TechnicalSignal(
    val stock: Stock,

    // RSI
    val rsi: Double = 0.0,          // 0-100, ideal buy zone: 50-70

    // MACD
    val macdLine: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHistogram: Double = 0.0,
    val macdBullish: Boolean = false, // macdLine > signalLine

    // EMA Crossover
    val ema9: Double = 0.0,
    val ema21: Double = 0.0,
    val emaCrossoverBullish: Boolean = false, // ema9 > ema21

    // Volume
    val volumeRatio: Double = 0.0,  // current vs 20-day avg
    val volumeSurge: Boolean = false, // ratio > 1.5

    // Trend
    val aboveMa50: Boolean = false,
    val aboveMa200: Boolean = false,
    val consecutiveGainDays: Int = 0,

    // Composite score
    val signalScore: Int = 0,       // 0-100
    val recommendation: Recommendation = Recommendation.WATCH
) {
    val rsiZone: String get() = when {
        rsi > 70 -> "Overbought"
        rsi >= 55 -> "Bullish Zone"
        rsi >= 45 -> "Neutral"
        rsi >= 30 -> "Oversold"
        else -> "Extreme Oversold"
    }

    val rsiColor: RsiColor get() = when {
        rsi > 70 -> RsiColor.RED
        rsi >= 55 -> RsiColor.GREEN
        rsi >= 45 -> RsiColor.YELLOW
        else -> RsiColor.GRAY
    }
}

enum class Recommendation(val label: String, val emoji: String) {
    STRONG_BUY("STRONG BUY", "🔥"),
    BUY("BUY", "✅"),
    WATCH("WATCH", "👀"),
    AVOID("AVOID", "⚠️")
}

enum class RsiColor { GREEN, YELLOW, RED, GRAY }
