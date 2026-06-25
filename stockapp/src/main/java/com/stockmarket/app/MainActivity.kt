package com.stockmarket.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.stockmarket.app.databinding.ActivityMainBinding
import com.stockmarket.app.model.TechnicalSignal
import com.stockmarket.app.repository.StockRepository
import com.stockmarket.app.ui.adapter.StockAdapter
import com.stockmarket.app.ui.detail.StockDetailActivity
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

        adapter = StockAdapter { sig -> openDetail(sig) }
        binding.recyclerView.adapter = adapter

        vm = ViewModelProvider(this, StockViewModel.Factory(StockRepository()))[StockViewModel::class.java]

        setupTabs()
        observeVm()

        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }
        binding.swipeRefresh.setColorSchemeColors(
            getColor(R.color.gain_green),
            getColor(R.color.accent_gold)
        )

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
            if (hasData) {
                binding.tvStatus.text = "${signals.size} strong signals • Pull to refresh"
            }
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
