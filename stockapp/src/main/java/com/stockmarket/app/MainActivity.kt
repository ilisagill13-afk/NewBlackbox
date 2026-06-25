package com.stockmarket.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.stockmarket.app.databinding.ActivityMainBinding
import com.stockmarket.app.model.TechnicalSignal
import com.stockmarket.app.portfolio.PortfolioActivity
import com.stockmarket.app.portfolio.PortfolioManager
import com.stockmarket.app.repository.StockRepository
import com.stockmarket.app.ui.adapter.StockAdapter
import com.stockmarket.app.ui.detail.StockDetailActivity
import com.stockmarket.app.util.NotificationHelper
import com.stockmarket.app.util.RiskGuardian
import com.stockmarket.app.viewmodel.StockViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vm: StockViewModel
    private lateinit var adapter: StockAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "StockPulse"
        supportActionBar?.subtitle = "Technical Analysis Scanner"

        adapter = StockAdapter(
            onClick = { sig -> openDetail(sig) },
            onBuy = { sig -> showBuyDialog(sig) }
        )
        binding.recyclerView.adapter = adapter

        vm = ViewModelProvider(this, StockViewModel.Factory(StockRepository()))[StockViewModel::class.java]

        setupTabs()
        observeVm()

        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }
        binding.swipeRefresh.setColorSchemeColors(
            getColor(R.color.gain_green),
            getColor(R.color.accent_gold)
        )

        binding.btnPortfolio.setOnClickListener {
            PortfolioActivity.start(this)
        }

        NotificationHelper.createChannels(this)
        vm.load(StockViewModel.Tab.DAY_GAINERS)
    }

    private fun setupTabs() {
        StockViewModel.Tab.values().forEach { tab ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tab.label))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                vm.load(StockViewModel.Tab.values().getOrElse(tab?.position ?: 0) {
                    StockViewModel.Tab.DAY_GAINERS
                })
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { vm.refresh() }
        })
    }

    private fun observeVm() {
        vm.signals.observe(this) { signals ->
            adapter.submitList(signals)
            val hasData = signals.isNotEmpty()
            binding.recyclerView.visibility = if (hasData) View.VISIBLE else View.GONE
            binding.tvEmpty.visibility = if (!hasData) View.VISIBLE else View.GONE
            if (hasData) binding.tvStatus.text = "${signals.size} strong signals • Pull to refresh"
        }
        vm.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
        vm.loadingMsg.observe(this) { msg ->
            if (vm.loading.value == true) binding.tvStatus.text = msg
        }
        vm.error.observe(this) { err ->
            if (err != null) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = err
                binding.tvStatus.text = "No data"
            }
        }
    }

    private fun showBuyDialog(sig: TechnicalSignal) {
        val prefs = getSharedPreferences("stockpulse_portfolio", Context.MODE_PRIVATE)
        val portfolio = PortfolioManager.get(prefs)

        val risk = RiskGuardian.assess(sig, portfolio.totalCurrentValue, portfolio.cashBalance)

        // Block purchase if risk is too high
        if (!risk.approved) {
            val warnings = risk.warnings.joinToString("\n• ", prefix = "• ")
            AlertDialog.Builder(this)
                .setTitle("❌ Purchase Blocked")
                .setMessage(
                    "Risk assessment: ${risk.riskLevel.label}\n\n" +
                    "This stock is too risky to buy right now:\n$warnings\n\n" +
                    "Wait for a better setup with a higher signal score."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_buy, null)
        val tvInfo = view.findViewById<TextView>(R.id.tv_buy_info)
        val etAmount = view.findViewById<EditText>(R.id.et_invest_amount)
        val tvCash = view.findViewById<TextView>(R.id.tv_cash_available)

        val maxAmount = portfolio.totalCurrentValue * risk.maxSuggestedPct / 100.0
        val riskWarnings = if (risk.warnings.isEmpty()) "" else
            "\n\n⚠ ${risk.warnings.joinToString(" | ")}"
        val positives = if (risk.positives.isEmpty()) "" else
            "\n✅ ${risk.positives.take(2).joinToString(" | ")}"

        tvInfo.text = "${sig.stock.symbol} — $${"%.2f".format(sig.stock.price)}\n" +
            "${sig.recommendation.emoji} ${sig.recommendation.label} (${sig.signalScore}/100)\n" +
            "Risk: ${risk.riskLevel.label} · Max suggested: $${"%.0f".format(maxAmount)}" +
            positives + riskWarnings
        tvCash.text = "Available cash: $${"%.2f".format(portfolio.cashBalance)}"

        AlertDialog.Builder(this)
            .setTitle("💰 Buy ${sig.stock.symbol}")
            .setView(view)
            .setPositiveButton("Confirm Buy") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (amount > maxAmount * 1.5) {
                    AlertDialog.Builder(this)
                        .setTitle("⚠ Position Too Large")
                        .setMessage(
                            "Investing $${"%.0f".format(amount)} exceeds the suggested max of $${"%.0f".format(maxAmount)} " +
                            "for this risk level (${risk.riskLevel.label}).\n\nReduce the amount to protect your portfolio."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                val result = PortfolioManager.buyStock(
                    prefs, sig.stock.symbol, sig.stock.displayName,
                    sig.stock.price, amount
                )
                result.onSuccess {
                    AlertDialog.Builder(this)
                        .setTitle("✅ Bought!")
                        .setMessage(
                            "Invested $${"%.2f".format(amount)} in ${sig.stock.symbol}.\n" +
                            "Stop-loss and take-profit set automatically.\n\nView portfolio to monitor."
                        )
                        .setPositiveButton("View Portfolio") { _, _ -> PortfolioActivity.start(this) }
                        .setNegativeButton("OK", null)
                        .show()
                }.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("⚠ Failed")
                        .setMessage(it.message ?: "Purchase failed")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDetail(sig: TechnicalSignal) {
        startActivity(Intent(this, StockDetailActivity::class.java).apply {
            putExtra(StockDetailActivity.KEY, sig.stock.symbol)
            putExtra("name", sig.stock.displayName)
            putExtra("price", sig.stock.price)
            putExtra("change_pct", sig.stock.changePercent)
            putExtra("change", sig.stock.change)
            putExtra("volume", sig.stock.volume)
            putExtra("mktcap", sig.stock.marketCap)
            putExtra("day_high", sig.stock.dayHigh)
            putExtra("day_low", sig.stock.dayLow)
            putExtra("w52_high", sig.stock.high52w)
            putExtra("w52_low", sig.stock.low52w)
            putExtra("ma50", sig.stock.ma50)
            putExtra("ma200", sig.stock.ma200)
            putExtra("rsi", sig.rsi)
            putExtra("macd_line", sig.macdLine)
            putExtra("macd_signal", sig.macdSignal)
            putExtra("macd_hist", sig.macdHistogram)
            putExtra("macd_bullish", sig.macdBullish)
            putExtra("ema9", sig.ema9)
            putExtra("ema21", sig.ema21)
            putExtra("ema_bullish", sig.emaCrossoverBullish)
            putExtra("vol_ratio", sig.volumeRatio)
            putExtra("vol_surge", sig.volumeSurge)
            putExtra("score", sig.signalScore)
            putExtra("rec", sig.recommendation.name)
            putExtra("consec_days", sig.consecutiveGainDays)
        })
    }
}
