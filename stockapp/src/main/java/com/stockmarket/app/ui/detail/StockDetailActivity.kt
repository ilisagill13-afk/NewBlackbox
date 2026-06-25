package com.stockmarket.app.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.stockmarket.app.R
import com.stockmarket.app.databinding.ActivityDetailBinding
import kotlin.math.abs

class StockDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SYMBOL = "symbol"
        const val EXTRA_NAME = "name"
        const val EXTRA_PRICE = "price"
        const val EXTRA_CHANGE_PCT = "change_pct"
        const val EXTRA_CHANGE = "change"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_MARKET_CAP = "market_cap"
        const val EXTRA_DAY_HIGH = "day_high"
        const val EXTRA_DAY_LOW = "day_low"
        const val EXTRA_52W_HIGH = "high_52w"
        const val EXTRA_52W_LOW = "low_52w"
        const val EXTRA_MA50 = "ma50"
        const val EXTRA_MA200 = "ma200"
        const val EXTRA_MOMENTUM = "momentum"
        const val EXTRA_STRENGTH = "strength"

        fun fmtPrice(v: Double) = if (v == 0.0) "—" else "$${"%.2f".format(v)}"
        fun fmtVol(v: Long) = when {
            v >= 1_000_000_000 -> "${"%.1f".format(v / 1e9)}B"
            v >= 1_000_000     -> "${"%.1f".format(v / 1e6)}M"
            v >= 1_000         -> "${"%.1f".format(v / 1e3)}K"
            else               -> "$v"
        }
        fun fmtCap(v: Long) = when {
            v >= 1_000_000_000_000 -> "${"%.2f".format(v / 1e12)}T"
            v >= 1_000_000_000     -> "${"%.2f".format(v / 1e9)}B"
            v >= 1_000_000         -> "${"%.2f".format(v / 1e6)}M"
            else -> "—"
        }
    }

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val symbol = intent.getStringExtra(EXTRA_SYMBOL) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val price = intent.getDoubleExtra(EXTRA_PRICE, 0.0)
        val changePct = intent.getDoubleExtra(EXTRA_CHANGE_PCT, 0.0)
        val change = intent.getDoubleExtra(EXTRA_CHANGE, 0.0)
        val volume = intent.getLongExtra(EXTRA_VOLUME, 0L)
        val marketCap = intent.getLongExtra(EXTRA_MARKET_CAP, 0L)
        val dayHigh = intent.getDoubleExtra(EXTRA_DAY_HIGH, 0.0)
        val dayLow = intent.getDoubleExtra(EXTRA_DAY_LOW, 0.0)
        val high52w = intent.getDoubleExtra(EXTRA_52W_HIGH, 0.0)
        val low52w = intent.getDoubleExtra(EXTRA_52W_LOW, 0.0)
        val ma50 = intent.getDoubleExtra(EXTRA_MA50, 0.0)
        val ma200 = intent.getDoubleExtra(EXTRA_MA200, 0.0)
        val momentum = intent.getIntExtra(EXTRA_MOMENTUM, 0)
        val strength = intent.getStringExtra(EXTRA_STRENGTH) ?: ""

        supportActionBar?.title = symbol
        supportActionBar?.subtitle = name

        binding.tvSymbolLarge.text = symbol
        binding.tvCompanyName.text = name
        binding.tvPriceLarge.text = fmtPrice(price)

        val isGain = changePct >= 0
        val sign = if (isGain) "▲ +" else "▼ "
        val changeColor = if (isGain) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        binding.tvChangeLarge.text = "$sign${"%.2f".format(abs(changePct))}%  (${"%.2f".format(abs(change))})"
        binding.tvChangeLarge.setTextColor(changeColor)

        binding.tvDayRange.text = "H: ${fmtPrice(dayHigh)}  —  L: ${fmtPrice(dayLow)}"
        binding.tvVolume.text = fmtVol(volume)
        binding.tvMarketCap.text = fmtCap(marketCap)
        binding.tv52wRange.text = "H: ${fmtPrice(high52w)}  —  L: ${fmtPrice(low52w)}"
        binding.tvMa50.text = if (ma50 > 0) fmtPrice(ma50) else "—"
        binding.tvMa200.text = if (ma200 > 0) fmtPrice(ma200) else "—"

        binding.tvMomentumScore.text = "$momentum / 100"
        binding.progressMomentum.progress = momentum
        binding.tvStrengthLabel.text = strength
        binding.tvStrengthLabel.setTextColor(changeColor)

        val scoreColor = when {
            momentum >= 80 -> Color.parseColor("#1B5E20")
            momentum >= 60 -> Color.parseColor("#2E7D32")
            else -> Color.parseColor("#F57F17")
        }
        binding.progressMomentum.progressTintList =
            android.content.res.ColorStateList.valueOf(scoreColor)

        val ma50Signal = if (ma50 > 0 && price > ma50) "✅ Above 50-day MA" else if (ma50 > 0) "⚠ Below 50-day MA" else "—"
        val ma200Signal = if (ma200 > 0 && price > ma200) "✅ Above 200-day MA" else if (ma200 > 0) "⚠ Below 200-day MA" else "—"
        binding.tvSignal50.text = ma50Signal
        binding.tvSignal200.text = ma200Signal
        binding.tvSignal50.setTextColor(if (ma50 > 0 && price > ma50) Color.parseColor("#2E7D32") else Color.parseColor("#F57F17"))
        binding.tvSignal200.setTextColor(if (ma200 > 0 && price > ma200) Color.parseColor("#2E7D32") else Color.parseColor("#F57F17"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return true
    }
}
