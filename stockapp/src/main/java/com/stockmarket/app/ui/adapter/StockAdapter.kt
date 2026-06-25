package com.stockmarket.app.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockmarket.app.databinding.ItemStockBinding
import com.stockmarket.app.model.Stock
import kotlin.math.abs

class StockAdapter(
    private val onClick: (Stock) -> Unit
) : ListAdapter<Stock, StockAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Stock>() {
            override fun areItemsTheSame(a: Stock, b: Stock) = a.symbol == b.symbol
            override fun areContentsTheSame(a: Stock, b: Stock) = a == b
        }

        private val GREEN_DARK = Color.parseColor("#1B5E20")
        private val GREEN = Color.parseColor("#2E7D32")
        private val GREEN_BG = Color.parseColor("#E8F5E9")
        private val RED = Color.parseColor("#C62828")

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
        fun fmtCap(v: Long) = when {
            v >= 1_000_000_000_000 -> "${"%.2f".format(v / 1e12)}T"
            v >= 1_000_000_000     -> "${"%.2f".format(v / 1e9)}B"
            v >= 1_000_000         -> "${"%.2f".format(v / 1e6)}M"
            else -> "—"
        }
    }

    inner class VH(val b: ItemStockBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(stock: Stock, position: Int) {
            b.tvRank.text = "#${position + 1}"
            b.tvSymbol.text = stock.symbol
            b.tvName.text = stock.displayName
            b.tvPrice.text = fmtPrice(stock.price)
            b.tvChange.text = fmtPct(stock.changePercent)
            b.tvVolume.text = "Vol: ${fmtVol(stock.volume)}"
            b.tvMarketCap.text = "MCap: ${fmtCap(stock.marketCap)}"

            val rangeText = if (stock.dayHigh > 0 && stock.dayLow > 0)
                "H ${fmtPrice(stock.dayHigh)}  L ${fmtPrice(stock.dayLow)}"
            else "—"
            b.tvDayRange.text = rangeText

            b.tvMomentum.text = "Momentum: ${stock.momentumScore}/100"
            b.tvStrength.text = stock.strengthLabel

            val gainColor = if (stock.isGaining) GREEN else RED
            b.tvChange.setTextColor(gainColor)
            b.tvStrength.setTextColor(gainColor)

            val scoreColor = when {
                stock.momentumScore >= 80 -> GREEN_DARK
                stock.momentumScore >= 60 -> GREEN
                else -> Color.parseColor("#F57F17")
            }
            b.progressMomentum.progress = stock.momentumScore
            b.progressMomentum.progressTintList =
                android.content.res.ColorStateList.valueOf(scoreColor)

            b.root.setOnClickListener { onClick(stock) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), position)
}
