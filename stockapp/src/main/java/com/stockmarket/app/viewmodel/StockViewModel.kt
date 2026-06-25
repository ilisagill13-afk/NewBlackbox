package com.stockmarket.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.model.TechnicalSignal
import com.stockmarket.app.repository.StockRepository
import kotlinx.coroutines.launch

class StockViewModel(private val repo: StockRepository) : ViewModel() {

    private val _signals = MutableLiveData<List<TechnicalSignal>>()
    val signals: LiveData<List<TechnicalSignal>> = _signals

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _loadingMsg = MutableLiveData("Loading…")
    val loadingMsg: LiveData<String> = _loadingMsg

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var currentTab = Tab.DAY_GAINERS

    enum class Tab(val label: String) {
        DAY_GAINERS("Day Gainers"),
        MOST_ACTIVE("Most Active"),
        GROWTH_TECH("Growth Tech")
    }

    fun load(tab: Tab) {
        currentTab = tab
        _loading.value = true
        _error.value = null
        _signals.value = emptyList()
        _loadingMsg.value = "Scanning market…"

        viewModelScope.launch {
            _loadingMsg.value = "Fetching top stocks…"
            val result = when (tab) {
                Tab.DAY_GAINERS -> repo.getDayGainers()
                Tab.MOST_ACTIVE -> repo.getMostActive()
                Tab.GROWTH_TECH -> repo.getGrowthTech()
            }

            result.onSuccess { list ->
                _signals.value = list
                if (list.isEmpty()) {
                    _error.value = "No strong buy signals found.\nMarket may be closed or data unavailable.\n\nPull down to retry."
                }
            }.onFailure {
                _error.value = "Connection failed.\n${it.message}\n\nPull down to retry."
                _signals.value = emptyList()
            }

            _loading.value = false
        }
    }

    fun refresh() = load(currentTab)

    class Factory(private val repo: StockRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = StockViewModel(repo) as T
    }
}
