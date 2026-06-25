package com.stockmarket.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import com.stockmarket.app.databinding.ActivityMainBinding
import com.stockmarket.app.model.Stock
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

        adapter = StockAdapter { stock -> openDetail(stock) }
        binding.recyclerView.adapter = adapter

        vm = ViewModelProvider(this, StockViewModel.Factory(StockRepository()))[StockViewModel::class.java]

        observeVm()
        setupTabs()

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
                val vmTab = StockViewModel.Tab.values().getOrElse(tab?.position ?: 0) {
                    StockViewModel.Tab.DAY_GAINERS
                }
                vm.load(vmTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { vm.refresh() }
        })
    }

    private fun observeVm() {
        vm.stocks.observe(this) { stocks ->
            adapter.submitList(stocks)
            binding.tvEmpty.visibility = if (stocks.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (stocks.isNotEmpty()) View.VISIBLE else View.GONE
            if (stocks.isNotEmpty()) {
                binding.tvStatus.text = "${stocks.size} stocks • Swipe to refresh"
            }
        }

        vm.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        vm.error.observe(this) { err ->
            if (err != null) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = err
                binding.tvStatus.text = "Unable to load data"
            }
        }
    }

    private fun openDetail(stock: Stock) {
        val intent = Intent(this, StockDetailActivity::class.java).apply {
            putExtra(StockDetailActivity.EXTRA_SYMBOL, stock.symbol)
            putExtra(StockDetailActivity.EXTRA_NAME, stock.displayName)
            putExtra(StockDetailActivity.EXTRA_PRICE, stock.price)
            putExtra(StockDetailActivity.EXTRA_CHANGE_PCT, stock.changePercent)
            putExtra(StockDetailActivity.EXTRA_CHANGE, stock.change)
            putExtra(StockDetailActivity.EXTRA_VOLUME, stock.volume)
            putExtra(StockDetailActivity.EXTRA_MARKET_CAP, stock.marketCap)
            putExtra(StockDetailActivity.EXTRA_DAY_HIGH, stock.dayHigh)
            putExtra(StockDetailActivity.EXTRA_DAY_LOW, stock.dayLow)
            putExtra(StockDetailActivity.EXTRA_52W_HIGH, stock.high52w)
            putExtra(StockDetailActivity.EXTRA_52W_LOW, stock.low52w)
            putExtra(StockDetailActivity.EXTRA_MA50, stock.ma50)
            putExtra(StockDetailActivity.EXTRA_MA200, stock.ma200)
            putExtra(StockDetailActivity.EXTRA_MOMENTUM, stock.momentumScore)
            putExtra(StockDetailActivity.EXTRA_STRENGTH, stock.strengthLabel)
        }
        startActivity(intent)
    }
}
