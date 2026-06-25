package com.stockmarket.app.ui.detail

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.stockmarket.app.databinding.ActivityDetailBinding
import com.stockmarket.app.model.Recommendation
import kotlin.math.abs

class StockDetailActivity : AppCompatActivity() {

    companion object {
        const val KEY = "symbol"
    }

    private lateinit var b: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val i = intent
        val symbol = i.getStringExtra(KEY) ?: "—"
        val name = i.getStringExtra("name") ?: "—"
        val price = i.getDoubleExtra("price", 0.0)
        val changePct = i.getDoubleExtra("change_pct", 0.0)
        val change = i.getDoubleExtra("change", 0.0)
        val volume = i.getLongExtra("volume", 0L)
        val mktCap = i.getLongExtra("mktcap", 0L)
        val dayHigh = i.getDoubleExtra("day_high", 0.0)
        val dayLow = i.getDoubleExtra("day_low", 0.0)
        val w52High = i.getDoubleExtra("w52_high", 0.0)
        val w52Low = i.getDoubleExtra("w52_low", 0.0)
        val ma50 = i.getDoubleExtra("ma50", 0.0)
        val ma200 = i.getDoubleExtra("ma200", 0.0)
        val rsi = i.getDoubleExtra("rsi", 0.0)
        val macdLine = i.getDoubleExtra("macd_line", 0.0)
        val macdSignal = i.getDoubleExtra("macd_signal", 0.0)
        val macdHist = i.getDoubleExtra("macd_hist", 0.0)
        val macdBullish = i.getBooleanExtra("macd_bullish", false)
        val ema9 = i.getDoubleExtra("ema9", 0.0)
        val ema21 = i.getDoubleExtra("ema21", 0.0)
        val emaBullish = i.getBooleanExtra("ema_bullish", false)
        val volRatio = i.getDoubleExtra("vol_ratio", 0.0)
        val volSurge = i.getBooleanExtra("vol_surge", false)
        val score = i.getIntExtra("score", 0)
        val recName = i.getStringExtra("rec") ?: Recommendation.WATCH.name
        val rec = runCatching { Recommendation.valueOf(recName) }.getOrDefault(Recommendation.WATCH)
        val consecDays = i.getIntExtra("consec_days", 0)

        supportActionBar?.title = symbol
        supportActionBar?.subtitle = name

        val green = Color.parseColor("#00C853")
        val red = Color.parseColor("#FF1744")
        val yellow = Color.parseColor("#FFD600")
        val gray = Color.parseColor("#9E9E9E")
        val isGain = changePct >= 0
        val gainColor = if (isGain) green else red

        // --- Price Hero ---
        b.tvSymbolLarge.text = symbol
        b.tvCompanyName.text = name
        b.tvPriceLarge.text = fmt(price)
        b.tvChangeLarge.text = "${if (isGain) "▲ +" else "▼ "}${"%.2f".format(abs(changePct))}%  (${"%.2f".format(abs(change))})"
        b.tvChangeLarge.setTextColor(gainColor)

        // --- Recommendation Badge ---
        b.tvRecommendation.text = "${rec.emoji} ${rec.label}"
        b.tvRecommendation.setTextColor(
            when (rec) {
                Recommendation.STRONG_BUY -> green
                Recommendation.BUY -> Color.parseColor("#69F0AE")
                Recommendation.WATCH -> yellow
                Recommendation.AVOID -> red
            }
        )

        // --- Signal Score ---
        b.tvScoreValue.text = "$score / 100"
        b.progressScore.progress = score
        b.progressScore.progressTintList = ColorStateList.valueOf(
            when { score >= 80 -> green; score >= 65 -> Color.parseColor("#69F0AE"); else -> yellow }
        )

        // --- RSI ---
        val rsiZone = when {
            rsi > 70 -> "Overbought ⚠"
            rsi >= 55 -> "Buy Zone ✅"
            rsi >= 45 -> "Neutral"
            rsi >= 30 -> "Oversold"
            else -> "Extreme Oversold"
        }
        val rsiColor = when { rsi > 70 -> red; rsi >= 55 -> green; rsi >= 45 -> yellow; else -> gray }
        b.tvRsiValue.text = "${"%.1f".format(rsi)}  ($rsiZone)"
        b.tvRsiValue.setTextColor(rsiColor)

        // --- MACD ---
        b.tvMacdValue.text = "Line ${"%.4f".format(macdLine)}  Signal ${"%.4f".format(macdSignal)}"
        b.tvMacdSignal.text = if (macdBullish) "▲ Bullish Crossover ✅" else "▼ Bearish"
        b.tvMacdSignal.setTextColor(if (macdBullish) green else red)
        b.tvMacdHist.text = "Histogram: ${"%.4f".format(macdHist)}"
        b.tvMacdHist.setTextColor(if (macdHist > 0) green else red)

        // --- EMA ---
        b.tvEmaValues.text = "EMA9: ${fmt(ema9)}   EMA21: ${fmt(ema21)}"
        b.tvEmaSignal.text = if (emaBullish) "EMA9 > EMA21 — Bullish ✅" else "EMA9 < EMA21 — Bearish ⚠"
        b.tvEmaSignal.setTextColor(if (emaBullish) green else red)

        // --- Volume ---
        b.tvVolumeValue.text = fmtVol(volume)
        b.tvVolumeRatio.text = "×${"%.1f".format(volRatio)} of 20-day avg"
        b.tvVolumeSignal.text = if (volSurge) "Volume Surge ✅ — Strong confirmation" else "Normal volume"
        b.tvVolumeSignal.setTextColor(if (volSurge) yellow else gray)

        // --- Moving Averages ---
        b.tvMa50Signal.text = if (ma50 > 0 && price > ma50) "✅ Price above MA50 (${fmt(ma50)})" else if (ma50 > 0) "⚠ Below MA50 (${fmt(ma50)})" else "—"
        b.tvMa50Signal.setTextColor(if (ma50 > 0 && price > ma50) green else yellow)
        b.tvMa200Signal.text = if (ma200 > 0 && price > ma200) "✅ Price above MA200 (${fmt(ma200)})" else if (ma200 > 0) "⚠ Below MA200 (${fmt(ma200)})" else "—"
        b.tvMa200Signal.setTextColor(if (ma200 > 0 && price > ma200) green else yellow)

        // --- Streak ---
        b.tvStreak.text = if (consecDays > 0) "↑ $consecDays consecutive up days" else "No consecutive up streak"
        b.tvStreak.setTextColor(if (consecDays >= 2) green else gray)

        // --- Market Data ---
        b.tvDayRange.text = "H: ${fmt(dayHigh)}  —  L: ${fmt(dayLow)}"
        b.tvMarketCap.text = fmtCap(mktCap)
        b.tv52w.text = "H: ${fmt(w52High)}  —  L: ${fmt(w52Low)}"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun fmt(v: Double) = if (v == 0.0) "—" else "$${"%.2f".format(v)}"
    private fun fmtVol(v: Long) = when {
        v >= 1_000_000_000 -> "${"%.1f".format(v / 1e9)}B"
        v >= 1_000_000 -> "${"%.1f".format(v / 1e6)}M"
        v >= 1_000 -> "${"%.1f".format(v / 1e3)}K"
        else -> "$v"
    }
    private fun fmtCap(v: Long) = when {
        v >= 1_000_000_000_000 -> "${"%.2f".format(v / 1e12)}T"
        v >= 1_000_000_000 -> "${"%.2f".format(v / 1e9)}B"
        v >= 1_000_000 -> "${"%.2f".format(v / 1e6)}M"
        else -> "—"
    }
}
