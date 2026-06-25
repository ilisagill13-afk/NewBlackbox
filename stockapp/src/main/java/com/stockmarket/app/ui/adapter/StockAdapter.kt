package com.stockmarket.app.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockmarket.app.databinding.ItemStockBinding
import com.stockmarket.app.model.Recommendation
import com.stockmarket.app.model.RsiColor
import com.stockmarket.app.model.TechnicalSignal
import kotlin.math.abs

class StockAdapter(
    private val onClick: (TechnicalSignal) -> Unit,
    private val onBuy: (TechnicalSignal) -> Unit
) : ListAdapter<TechnicalSignal, StockAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TechnicalSignal>() {
            override fun areItemsTheSame(a: TechnicalSignal, b: TechnicalSignal) =
                a.stock.symbol == b.stock.symbol
            override fun areContentsTheSame(a: TechnicalSignal, b: TechnicalSignal) = a == b
        }

        private val COLOR_GREEN = Color.parseColor("#00C853")
        private val COLOR_RED = Color.parseColor("#FF1744")
        private val COLOR_YELLOW = Color.parseColor("#FFD600")
        private val COLOR_GRAY = Color.parseColor("#9E9E9E")
        private val COLOR_ORANGE = Color.parseColor("#FF6F00")

        fun fmtPrice(v: Double) = if (v == 0.0) "—" else "$${"%.2f".format(v)}"
        fun fmtPct(v: Double): String {
            val sign = if (v >= 0) "▲ +" else "▼ "
            return "$sign${"%.2f".format(abs(v))}%"
        }
        fun fmtVol(v: Long) = when {
            v >= 1_000_000_000 -> "${"%.1f".format(v / 1e9)}B"
            v >= 1_000_000     -> "${"%.1f".format(v / 1e6)}M"
            v >= 1_000         -> "${"%.1f".format(v / 1e3)}K"
            else               -> "$v"
        }
    }

    inner class VH(val b: ItemStockBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(sig: TechnicalSignal, pos: Int) {
            val stock = sig.stock

            b.tvRank.text = "#${pos + 1}"
            b.tvSymbol.text = stock.symbol
            b.tvName.text = stock.displayName
            b.tvPrice.text = fmtPrice(stock.price)
            b.tvChange.text = fmtPct(stock.changePercent)
            b.tvChange.setTextColor(if (stock.isGaining) COLOR_GREEN else COLOR_RED)

            // Recommendation badge
            val rec = sig.recommendation
            b.tvStrength.text = "${rec.emoji} ${rec.label}"
            b.tvStrength.setTextColor(
                when (rec) {
                    Recommendation.STRONG_BUY -> COLOR_GREEN
                    Recommendation.BUY -> Color.parseColor("#69F0AE")
                    Recommendation.WATCH -> COLOR_YELLOW
                    Recommendation.AVOID -> COLOR_RED
                }
            )

            // Score bar
            b.tvMomentum.text = "Signal Score: ${sig.signalScore}/100"
            b.progressMomentum.progress = sig.signalScore
            val barColor = when {
                sig.signalScore >= 80 -> COLOR_GREEN
                sig.signalScore >= 65 -> Color.parseColor("#69F0AE")
                else -> COLOR_YELLOW
            }
            b.progressMomentum.progressTintList = ColorStateList.valueOf(barColor)

            // RSI tag
            val rsiColor = when (sig.rsiColor) {
                RsiColor.GREEN -> COLOR_GREEN
                RsiColor.YELLOW -> COLOR_YELLOW
                RsiColor.RED -> COLOR_RED
                RsiColor.GRAY -> COLOR_GRAY
            }
            b.tvRsi.text = "RSI ${"%.1f".format(sig.rsi)}"
            b.tvRsi.setTextColor(rsiColor)

            // MACD tag
            b.tvMacd.text = if (sig.macdBullish) "MACD ▲" else "MACD ▼"
            b.tvMacd.setTextColor(if (sig.macdBullish) COLOR_GREEN else COLOR_GRAY)

            // Volume tag
            b.tvVolume.text = if (sig.volumeSurge)
                "Vol ×${"%.1f".format(sig.volumeRatio)} 🔊"
            else
                "Vol: ${fmtVol(stock.volume)}"
            b.tvVolume.setTextColor(if (sig.volumeSurge) COLOR_YELLOW else COLOR_GRAY)

            b.root.setOnClickListener { onClick(sig) }
            b.btnBuy.setOnClickListener { onBuy(sig) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), position)
}
