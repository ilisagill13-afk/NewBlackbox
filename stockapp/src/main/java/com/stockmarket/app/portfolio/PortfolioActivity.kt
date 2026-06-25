package com.stockmarket.app.portfolio

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stockmarket.app.R
import com.stockmarket.app.worker.PriceMonitorWorker
import kotlin.math.abs

class PortfolioActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var tvCash: TextView
    private lateinit var tvTotalValue: TextView
    private lateinit var tvTotalPL: TextView
    private lateinit var tvTotalPLPct: TextView
    private lateinit var tvTotalInvested: TextView
    private lateinit var tvAtRisk: TextView
    private lateinit var rvPositions: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnDeposit: Button
    private lateinit var btnRiskSettings: Button
    private lateinit var positionAdapter: PositionAdapter

    companion object {
        fun start(context: Context) {
            context.startActivity(android.content.Intent(context, PortfolioActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portfolio)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Portfolio"
        supportActionBar?.subtitle = "Auto Stop-Loss Active"

        prefs = getSharedPreferences("stockpulse_portfolio", Context.MODE_PRIVATE)

        tvCash = findViewById(R.id.tv_cash)
        tvTotalValue = findViewById(R.id.tv_total_value)
        tvTotalPL = findViewById(R.id.tv_total_pl)
        tvTotalPLPct = findViewById(R.id.tv_total_pl_pct)
        tvTotalInvested = findViewById(R.id.tv_total_invested)
        tvAtRisk = findViewById(R.id.tv_at_risk)
        rvPositions = findViewById(R.id.rv_positions)
        tvEmpty = findViewById(R.id.tv_empty_portfolio)
        btnDeposit = findViewById(R.id.btn_deposit)
        btnRiskSettings = findViewById(R.id.btn_risk_settings)

        positionAdapter = PositionAdapter(onSell = { showSellDialog(it) })
        rvPositions.layoutManager = LinearLayoutManager(this)
        rvPositions.adapter = positionAdapter

        btnDeposit.setOnClickListener { showDepositDialog() }
        btnRiskSettings.setOnClickListener { showRiskSettingsDialog() }

        // Start background price monitor
        PriceMonitorWorker.schedule(this)

        refresh()
    }

    fun refresh() {
        PortfolioManager.invalidateCache()
        val p = PortfolioManager.get(prefs)
        val green = Color.parseColor("#00C853")
        val red = Color.parseColor("#FF1744")
        val yellow = Color.parseColor("#FFD600")

        tvCash.text = fmt(p.cashBalance)
        tvTotalValue.text = fmt(p.totalCurrentValue)
        tvTotalInvested.text = fmt(p.totalInvested)

        val plColor = if (p.totalProfitLoss >= 0) green else red
        tvTotalPL.text = "${if (p.totalProfitLoss >= 0) "+" else ""}${fmt(p.totalProfitLoss)}"
        tvTotalPL.setTextColor(plColor)
        tvTotalPLPct.text = "${"%.2f".format(abs(p.totalProfitLossPct))}%"
        tvTotalPLPct.setTextColor(plColor)

        val atRisk = p.positionsAtRisk
        if (atRisk.isNotEmpty()) {
            tvAtRisk.visibility = View.VISIBLE
            tvAtRisk.text = "⚠ ${atRisk.size} position(s) near stop-loss: ${atRisk.joinToString { it.symbol }}"
            tvAtRisk.setTextColor(yellow)
        } else {
            tvAtRisk.visibility = View.GONE
        }

        if (p.positions.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvPositions.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvPositions.visibility = View.VISIBLE
            positionAdapter.setData(p.positions)
        }
    }

    private fun showDepositDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_deposit, null)
        val etAmount = view.findViewById<EditText>(R.id.et_amount)
        AlertDialog.Builder(this)
            .setTitle("💰 Add Funds")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (amount > 0) { PortfolioManager.deposit(prefs, amount); refresh() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showRiskSettingsDialog() {
        val p = PortfolioManager.get(prefs)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_risk_settings, null)
        val tvStopVal = view.findViewById<TextView>(R.id.tv_stop_value)
        val sbStop = view.findViewById<SeekBar>(R.id.sb_stop_loss)
        val tvTpVal = view.findViewById<TextView>(R.id.tv_tp_value)
        val sbTp = view.findViewById<SeekBar>(R.id.sb_take_profit)
        val swTrailing = view.findViewById<Switch>(R.id.sw_trailing_stop)

        sbStop.max = 20
        sbStop.progress = p.defaultStopLossPct.toInt()
        tvStopVal.text = "Stop-Loss: ${p.defaultStopLossPct.toInt()}%"
        sbStop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) {
                tvStopVal.text = "Stop-Loss: ${v.coerceAtLeast(1)}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        sbTp.max = 50
        sbTp.progress = p.defaultTakeProfitPct.toInt()
        tvTpVal.text = "Take-Profit: ${p.defaultTakeProfitPct.toInt()}%"
        sbTp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) {
                tvTpVal.text = "Take-Profit: ${v.coerceAtLeast(1)}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        swTrailing.isChecked = p.trailingStopEnabled

        AlertDialog.Builder(this)
            .setTitle("🛡 Risk Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val sl = sbStop.progress.coerceAtLeast(1).toDouble()
                val tp = sbTp.progress.coerceAtLeast(1).toDouble()
                PortfolioManager.updateRiskSettings(prefs, sl, tp, swTrailing.isChecked)
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSellDialog(pos: Position) {
        val sign = if (pos.isProfit) "+" else ""
        AlertDialog.Builder(this)
            .setTitle("Sell ${pos.symbol}?")
            .setMessage(
                "Current Price: ${fmt(pos.currentPrice)}\n" +
                "P/L: $sign${fmt(pos.profitLoss)} ($sign${"%.2f".format(pos.profitLossPct)}%)\n\n" +
                "Sell ${String.format("%.4f", pos.shares)} shares?\n" +
                "You will receive: ${fmt(pos.shares * pos.currentPrice)}"
            )
            .setPositiveButton("Sell") { _, _ ->
                PortfolioManager.sellStock(prefs, pos.symbol, pos.currentPrice)
                refresh()
            }
            .setNegativeButton("Keep", null).show()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return true
    }
    private fun fmt(v: Double) = "$${"%.2f".format(v)}"
}

class PositionAdapter(private val onSell: (Position) -> Unit) :
    RecyclerView.Adapter<PositionAdapter.VH>() {

    private val items = mutableListOf<Position>()
    fun setData(list: List<Position>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbol: TextView = view.findViewById(R.id.tv_pos_symbol)
        val tvName: TextView = view.findViewById(R.id.tv_pos_name)
        val tvShares: TextView = view.findViewById(R.id.tv_pos_shares)
        val tvBuyPrice: TextView = view.findViewById(R.id.tv_pos_buy_price)
        val tvCurrentPrice: TextView = view.findViewById(R.id.tv_pos_current_price)
        val tvPL: TextView = view.findViewById(R.id.tv_pos_pl)
        val tvValue: TextView = view.findViewById(R.id.tv_pos_value)
        val tvStopLoss: TextView = view.findViewById(R.id.tv_pos_stop_loss)
        val tvTakeProfit: TextView = view.findViewById(R.id.tv_pos_take_profit)
        val tvRiskStatus: TextView = view.findViewById(R.id.tv_pos_risk_status)
        val btnSell: Button = view.findViewById(R.id.btn_sell)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_position, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val p = items[pos]
        val green = Color.parseColor("#00C853")
        val red = Color.parseColor("#FF1744")
        val yellow = Color.parseColor("#FFD600")

        holder.tvSymbol.text = p.symbol
        holder.tvName.text = p.companyName
        holder.tvShares.text = "${"%.4f".format(p.shares)} shares"
        holder.tvBuyPrice.text = "Avg: $${"%.2f".format(p.buyPrice)}"
        holder.tvCurrentPrice.text = "Now: $${"%.2f".format(p.currentPrice)}"
        holder.tvValue.text = "$${"%.2f".format(p.currentValue)}"

        val plText = "${if (p.isProfit) "+" else ""}$${"%.2f".format(p.profitLoss)} " +
            "(${"%.2f".format(abs(p.profitLossPct))}%)"
        holder.tvPL.text = plText
        holder.tvPL.setTextColor(if (p.isProfit) green else red)

        holder.tvStopLoss.text = "🛑 Stop: $${"%.2f".format(p.stopLossPrice)} " +
            "(${"%.1f".format(abs(p.stopLossPct))}%)"
        holder.tvTakeProfit.text = "🎯 Target: $${"%.2f".format(p.takeProfitPrice)}"

        holder.tvRiskStatus.text = when {
            p.isStopHit -> "🚨 STOP HIT — will auto-sell"
            p.isTakeProfitHit -> "🎯 TARGET REACHED — will auto-sell"
            p.isNearStop -> "⚠ NEAR STOP — only ${"%.1f".format(p.distanceToStopPct)}% away!"
            p.trailingStopPct > 0 -> "📈 Trailing stop active (${p.trailingStopPct}%)"
            else -> "✅ Protected"
        }
        holder.tvRiskStatus.setTextColor(
            when {
                p.isStopHit || p.isTakeProfitHit -> red
                p.isNearStop -> yellow
                else -> green
            }
        )

        holder.btnSell.setOnClickListener { onSell(p) }
    }

    override fun getItemCount() = items.size
}
