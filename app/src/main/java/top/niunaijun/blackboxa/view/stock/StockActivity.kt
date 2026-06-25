package top.niunaijun.blackboxa.view.stock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.data.StockRepository

class StockActivity : AppCompatActivity() {

    private lateinit var viewModel: StockViewModel
    private lateinit var adapter: StockAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvMarketStatus: TextView
    private lateinit var tabLayout: TabLayout

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StockActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)

        val toolbar = findViewById<Toolbar>(R.id.stock_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Top Stocks"

        swipeRefresh = findViewById(R.id.swipe_refresh)
        recyclerView = findViewById(R.id.rv_stocks)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        tvMarketStatus = findViewById(R.id.tv_market_status)
        tabLayout = findViewById(R.id.tab_layout)

        adapter = StockAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val factory = StockFactory(StockRepository())
        viewModel = ViewModelProvider(this, factory)[StockViewModel::class.java]

        observeViewModel()
        setupTabs()

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        swipeRefresh.setColorSchemeResources(android.R.color.holo_green_dark)

        viewModel.loadStocks(StockViewModel.Tab.DAY_GAINERS)
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Day Gainers"))
        tabLayout.addTab(tabLayout.newTab().setText("Most Active"))
        tabLayout.addTab(tabLayout.newTab().setText("Weekly"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val stockTab = when (tab?.position) {
                    0 -> StockViewModel.Tab.DAY_GAINERS
                    1 -> StockViewModel.Tab.MOST_ACTIVE
                    2 -> StockViewModel.Tab.WEEKLY
                    else -> StockViewModel.Tab.DAY_GAINERS
                }
                viewModel.loadStocks(stockTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { viewModel.refresh() }
        })
    }

    private fun observeViewModel() {
        viewModel.stocks.observe(this) { stocks ->
            adapter.submitList(stocks)
            if (stocks.isNotEmpty()) {
                tvError.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                tvMarketStatus.text = "${stocks.size} stocks found • Swipe down to refresh"
            } else {
                recyclerView.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            swipeRefresh.isRefreshing = false
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                tvError.visibility = View.VISIBLE
                tvError.text = error
                tvMarketStatus.text = "Unable to fetch data"
            } else {
                tvError.visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
