package top.niunaijun.blackboxa.view.stock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.bean.StockBean
import top.niunaijun.blackboxa.data.StockRepository

class StockViewModel(private val repository: StockRepository) : ViewModel() {

    private val _stocks = MutableLiveData<List<StockBean>>()
    val stocks: LiveData<List<StockBean>> = _stocks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentTab = Tab.DAY_GAINERS

    enum class Tab { DAY_GAINERS, MOST_ACTIVE, WEEKLY }

    fun loadStocks(tab: Tab = currentTab) {
        currentTab = tab
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = when (tab) {
                Tab.DAY_GAINERS -> repository.getDayGainers()
                Tab.MOST_ACTIVE -> repository.getMostActive()
                Tab.WEEKLY -> repository.getWeeklyGainers()
            }

            result.onSuccess { list ->
                _stocks.value = list
                if (list.isEmpty()) {
                    _error.value = "No stocks found. Market may be closed."
                }
            }.onFailure { error ->
                _error.value = "Failed to load: ${error.message}"
                _stocks.value = emptyList()
            }

            _isLoading.value = false
        }
    }

    fun refresh() = loadStocks(currentTab)
}
