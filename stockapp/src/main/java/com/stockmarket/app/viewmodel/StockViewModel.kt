package com.stockmarket.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.model.Stock
import com.stockmarket.app.repository.StockRepository
import kotlinx.coroutines.launch

class StockViewModel(private val repo: StockRepository) : ViewModel() {

    private val _stocks = MutableLiveData<List<Stock>>()
    val stocks: LiveData<List<Stock>> = _stocks

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _activeTab = MutableLiveData(Tab.DAY_GAINERS)
    val activeTab: LiveData<Tab> = _activeTab

    enum class Tab(val label: String) {
        DAY_GAINERS("Day Gainers"),
        MOST_ACTIVE("Most Active"),
        GROWTH_TECH("Growth Tech")
    }

    fun load(tab: Tab) {
        _activeTab.value = tab
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = when (tab) {
                Tab.DAY_GAINERS -> repo.getDayGainers()
                Tab.MOST_ACTIVE -> repo.getMostActive()
                Tab.GROWTH_TECH -> repo.getGrowthTech()
            }
            result.onSuccess { list ->
                _stocks.value = list
                if (list.isEmpty()) _error.value = "No trending stocks found.\nMarket may be closed or no data available."
            }.onFailure {
                _error.value = "Connection failed.\n${it.message}\n\nPull down to retry."
                _stocks.value = emptyList()
            }
            _loading.value = false
        }
    }

    fun refresh() = _activeTab.value?.let { load(it) }

    class Factory(private val repo: StockRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = StockViewModel(repo) as T
    }
}
