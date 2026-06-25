package com.stockmarket.app.algorithm

import com.stockmarket.app.model.OHLCV
import com.stockmarket.app.model.Recommendation
import com.stockmarket.app.model.Stock
import com.stockmarket.app.model.TechnicalSignal
import kotlin.math.abs
import kotlin.math.max

/**
 * Technical Analysis Engine
 *
 * Indicators:
 *  - RSI(14)  : Momentum oscillator. Buy zone: 50–70 (not overbought).
 *  - EMA(9/21): Short/medium trend. Bullish when EMA9 > EMA21.
 *  - MACD     : EMA(12)−EMA(26), signal=EMA(9). Bullish when MACD > signal.
 *  - Volume   : Ratio vs 20-day avg. Surge > 1.5× confirms trend.
 *
 * Score (0–100):
 *  RSI in buy zone (50–70)      → +25
 *  MACD bullish crossover        → +20
 *  EMA9 > EMA21                  → +15
 *  Price above MA50              → +10
 *  Price above MA200             → +10
 *  Volume surge (≥ 1.5×)         → +10
 *  Day change > 2%               → +10
 *
 * Minimum score to display: 65 (strong buy setup).
 */
object TechnicalAnalysis {

    fun analyze(stock: Stock, candles: List<OHLCV>): TechnicalSignal {
        if (candles.size < 30) return TechnicalSignal(stock = stock)

        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }

        val rsi = rsi(closes, period = 14)
        val ema9 = ema(closes, period = 9)
        val ema21 = ema(closes, period = 21)
        val ema12 = ema(closes, period = 12)
        val ema26 = ema(closes, period = 26)

        val macdLine = ema12 - ema26
        val macdHistory = macdSeries(closes)
        val macdSignal = if (macdHistory.size >= 9) ema(macdHistory, 9) else macdLine
        val macdHistogram = macdLine - macdSignal
        val macdBullish = macdLine > macdSignal

        val emaBullish = ema9 > ema21

        val vol20Avg = volumes.takeLast(21).dropLast(1).average()
        val volRatio = if (vol20Avg > 0) volumes.last().toDouble() / vol20Avg else 0.0
        val volSurge = volRatio >= 1.5

        val aboveMa50 = stock.ma50 > 0 && stock.price > stock.ma50
        val aboveMa200 = stock.ma200 > 0 && stock.price > stock.ma200

        val consecutiveGains = consecutivePositiveDays(closes)

        var score = 0
        if (rsi in 50.0..70.0) score += 25
        else if (rsi in 45.0..50.0) score += 10   // near zone
        if (macdBullish) score += 20
        if (emaBullish) score += 15
        if (aboveMa50) score += 10
        if (aboveMa200) score += 10
        if (volSurge) score += 10
        if (stock.changePercent > 2.0) score += 10
        else if (stock.changePercent > 0) score += 5

        val recommendation = when {
            score >= 80 -> Recommendation.STRONG_BUY
            score >= 65 -> Recommendation.BUY
            score >= 45 -> Recommendation.WATCH
            else -> Recommendation.AVOID
        }

        return TechnicalSignal(
            stock = stock,
            rsi = rsi,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHistogram = macdHistogram,
            macdBullish = macdBullish,
            ema9 = ema9,
            ema21 = ema21,
            emaCrossoverBullish = emaBullish,
            volumeRatio = volRatio,
            volumeSurge = volSurge,
            aboveMa50 = aboveMa50,
            aboveMa200 = aboveMa200,
            consecutiveGainDays = consecutiveGains,
            signalScore = score.coerceIn(0, 100),
            recommendation = recommendation
        )
    }

    // RSI(n) — standard Wilder smoothing
    fun rsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return 50.0

        val changes = closes.zipWithNext { a, b -> b - a }
        val initial = changes.take(period)
        var avgGain = initial.filter { it > 0 }.sum() / period
        var avgLoss = initial.filter { it < 0 }.sumOf { abs(it) } / period

        for (i in period until changes.size) {
            val gain = max(changes[i], 0.0)
            val loss = max(-changes[i], 0.0)
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    // EMA — last value only
    fun ema(values: List<Double>, period: Int): Double {
        if (values.size < period) return values.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = values.take(period).average()
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1 - k)
        }
        return ema
    }

    // MACD line series (EMA12 − EMA26) for each candle
    private fun macdSeries(closes: List<Double>): List<Double> {
        if (closes.size < 26) return emptyList()
        val k12 = 2.0 / 13
        val k26 = 2.0 / 27
        var e12 = closes.take(12).average()
        var e26 = closes.take(26).average()
        val series = mutableListOf<Double>()
        for (i in 26 until closes.size) {
            e12 = closes[i] * k12 + e12 * (1 - k12)
            e26 = closes[i] * k26 + e26 * (1 - k26)
            series.add(e12 - e26)
        }
        return series
    }

    // Count consecutive days of positive close-to-close change
    private fun consecutivePositiveDays(closes: List<Double>): Int {
        var count = 0
        for (i in closes.indices.reversed().drop(1)) {
            if (closes[i + 1] > closes[i]) count++ else break
        }
        return count
    }

    // Quick bullish filter without full chart — uses only screener data
    fun quickScore(stock: Stock): Int {
        var score = 0
        if (stock.changePercent > 0) score += 10
        if (stock.changePercent > 2) score += 10
        if (stock.changePercent > 5) score += 10
        if (stock.ma50 > 0 && stock.price > stock.ma50) score += 20
        if (stock.ma200 > 0 && stock.price > stock.ma200) score += 20
        if (stock.avgVolume > 0 && stock.volume > stock.avgVolume * 1.5) score += 15
        if (stock.high52w > 0 && stock.price > stock.high52w * 0.85) score += 15
        return score.coerceAtMost(100)
    }
}
