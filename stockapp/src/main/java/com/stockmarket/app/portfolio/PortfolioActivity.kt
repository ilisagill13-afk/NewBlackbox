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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stockmarket.app.R
import kotlin.math.abs

class PortfolioActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var positionAdapter: PositionAdapter

    private lateinit var tvCash: TextView
    private lateinit var tvTotalValue: TextView
    private lateinit var tvTotalPL: TextView
    private lateinit var tvTotalPLPct: TextView
    private lateinit var tvTotalInvested: TextView
    private lateinit var rvPositions: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnDeposit: Button

    companion object {
        fun start(context: Context) {
            context.startActivity(
                android.content.Intent(context, PortfolioActivity::class.java)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portfolio)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Portfolio"

        prefs = getSharedPreferences("stockpulse_portfolio", Context.MODE_PRIVATE)

        tvCash = findViewById(R.id.tv_cash)
        tvTotalValue = findViewById(R.id.tv_total_value)
        tvTotalPL = findViewById(R.id.tv_total_pl)
        tvTotalPLPct = findViewById(R.id.tv_total_pl_pct)
        tvTotalInvested = findViewById(R.id.tv_total_invested)
        rvPositions = findViewById(R.id.rv_positions)
        tvEmpty = findViewById(R.id.tv_empty_portfolio)
        btnDeposit = findViewById(R.id.btn_deposit)

        positionAdapter = PositionAdapter(onSell = { pos ->
            showSellDialog(pos)
        })
        rvPositions.layoutManager = LinearLayoutManager(this)
        rvPositions.adapter = positionAdapter

        btnDeposit.setOnClickListener { showDepositDialog() }

        refresh()
    }

    fun refresh() {
        val p = PortfolioManager.get(prefs)
        val green = Color.parseColor("#00C853")
        val red = Color.parseColor("#FF1744")

        tvCash.text = fmt(p.cashBalance)
        tvTotalValue.text = fmt(p.totalCurrentValue)
        tvTotalInvested.text = fmt(p.totalInvested)

        val plColor = if (p.totalProfitLoss >= 0) green else red
        tvTotalPL.text = "${if (p.totalProfitLoss >= 0) "+" else ""}${fmt(p.totalProfitLoss)}"
        tvTotalPL.setTextColor(plColor)
        tvTotalPLPct.text = "${"%.2f".format(abs(p.totalProfitLossPct))}%"
        tvTotalPLPct.setTextColor(plColor)

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
                if (amount > 0) {
                    PortfolioManager.deposit(prefs, amount)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSellDialog(pos: Position) {
        AlertDialog.Builder(this)
            .setTitle("Sell ${pos.symbol}?")
            .setMessage(
                "Current P/L: ${if (pos.isProfit) "+" else ""}${fmt(pos.profitLoss)} " +
                "(${if (pos.isProfit) "+" else ""}${"%.2f".format(pos.profitLossPct)}%)\n\n" +
                "Sell all ${String.format("%.4f", pos.shares)} shares at ${fmt(pos.currentPrice)}?"
            )
            .setPositiveButton("Sell All") { _, _ ->
                PortfolioManager.sellStock(prefs, pos.symbol, pos.currentPrice)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun fmt(v: Double) = "$${",.2f".let { "%.2f".format(v) }}"
}

class PositionAdapter(
    private val onSell: (Position) -> Unit
) : RecyclerView.Adapter<PositionAdapter.VH>() {

    private val items = mutableListOf<Position>()

    fun setData(list: List<Position>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbol: TextView = view.findViewById(R.id.tv_pos_symbol)
        val tvName: TextView = view.findViewById(R.id.tv_pos_name)
        val tvShares: TextView = view.findViewById(R.id.tv_pos_shares)
        val tvBuyPrice: TextView = view.findViewById(R.id.tv_pos_buy_price)
        val tvCurrentPrice: TextView = view.findViewById(R.id.tv_pos_current_price)
        val tvPL: TextView = view.findViewById(R.id.tv_pos_pl)
        val tvValue: TextView = view.findViewById(R.id.tv_pos_value)
        val btnSell: Button = view.findViewById(R.id.btn_sell)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_position, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pos = items[position]
        val green = Color.parseColor("#00C853")
        val red = Color.parseColor("#FF1744")

        holder.tvSymbol.text = pos.symbol
        holder.tvName.text = pos.companyName
        holder.tvShares.text = "${"%.4f".format(pos.shares)} shares"
        holder.tvBuyPrice.text = "Avg buy: $${"%.2f".format(pos.buyPrice)}"
        holder.tvCurrentPrice.text = "Now: $${"%.2f".format(pos.currentPrice)}"
        holder.tvValue.text = "$${"%.2f".format(pos.currentValue)}"

        val plText = "${if (pos.isProfit) "+" else ""}$${"%.2f".format(pos.profitLoss)}" +
            " (${"%.2f".format(abs(pos.profitLossPct))}%)"
        holder.tvPL.text = plText
        holder.tvPL.setTextColor(if (pos.isProfit) green else red)

        holder.btnSell.setOnClickListener { onSell(pos) }
    }

    override fun getItemCount() = items.size
}
