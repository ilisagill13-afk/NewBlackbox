package top.niunaijun.blackboxa.view.stock

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.StockBean
import kotlin.math.abs

class StockAdapter : ListAdapter<StockBean, StockAdapter.StockViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StockBean>() {
            override fun areItemsTheSame(old: StockBean, new: StockBean) = old.symbol == new.symbol
            override fun areContentsTheSame(old: StockBean, new: StockBean) = old == new
        }

        private val COLOR_GAIN_TEXT = Color.parseColor("#2E7D32")
        private val COLOR_LOSS_TEXT = Color.parseColor("#C62828")

        private fun formatPrice(value: Double): String =
            if (value == 0.0) "--" else "$${"%.2f".format(value)}"

        private fun formatPercent(value: Double): String {
            val sign = if (value >= 0) "▲ +" else "▼ "
            return "$sign${"%.2f".format(abs(value))}%"
        }

        private fun formatVolume(volume: Long): String = when {
            volume >= 1_000_000_000 -> "${"%.1f".format(volume / 1_000_000_000.0)}B"
            volume >= 1_000_000 -> "${"%.1f".format(volume / 1_000_000.0)}M"
            volume >= 1_000 -> "${"%.1f".format(volume / 1_000.0)}K"
            else -> volume.toString()
        }

        private fun formatMarketCap(cap: Long): String = when {
            cap >= 1_000_000_000_000 -> "${"%.2f".format(cap / 1_000_000_000_000.0)}T"
            cap >= 1_000_000_000 -> "${"%.2f".format(cap / 1_000_000_000.0)}B"
            cap >= 1_000_000 -> "${"%.2f".format(cap / 1_000_000.0)}M"
            else -> "--"
        }
    }

    inner class StockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rank: TextView = view.findViewById(R.id.tv_rank)
        val symbol: TextView = view.findViewById(R.id.tv_symbol)
        val name: TextView = view.findViewById(R.id.tv_name)
        val price: TextView = view.findViewById(R.id.tv_price)
        val change: TextView = view.findViewById(R.id.tv_change)
        val volume: TextView = view.findViewById(R.id.tv_volume)
        val marketCap: TextView = view.findViewById(R.id.tv_market_cap)
        val dayRange: TextView = view.findViewById(R.id.tv_day_range)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false)
        return StockViewHolder(view)
    }

    override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
        val stock = getItem(position)

        holder.rank.text = "#${position + 1}"
        holder.symbol.text = stock.symbol
        holder.name.text = stock.displayName
        holder.price.text = formatPrice(stock.price)
        holder.change.text = formatPercent(stock.changePercent)
        holder.volume.text = "Vol: ${formatVolume(stock.volume)}"
        holder.marketCap.text = "Cap: ${formatMarketCap(stock.marketCap)}"

        val rangeStr = if (stock.dayHigh > 0 && stock.dayLow > 0)
            "H: ${formatPrice(stock.dayHigh)}  L: ${formatPrice(stock.dayLow)}"
        else "--"
        holder.dayRange.text = rangeStr

        val isPositive = stock.changePercent >= 0
        val changeColor = if (isPositive) COLOR_GAIN_TEXT else COLOR_LOSS_TEXT
        holder.change.setTextColor(changeColor)
    }
}
