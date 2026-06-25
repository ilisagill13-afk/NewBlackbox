package com.stockmarket.app.repository

import android.util.Log
import com.stockmarket.app.algorithm.TechnicalAnalysis
import com.stockmarket.app.model.OHLCV
import com.stockmarket.app.model.Stock
import com.stockmarket.app.model.TechnicalSignal
import com.stockmarket.app.network.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class StockRepository {

    private val tag = "StockRepository"

    // Min score to display a stock (only strong buy setups shown)
    private val MIN_SCORE = 65

    suspend fun getDayGainers(): Result<List<TechnicalSignal>> =
        fetchAndAnalyze("day_gainers")

    suspend fun getMostActive(): Result<List<TechnicalSignal>> =
        fetchAndAnalyze("most_actives")

    suspend fun getGrowthTech(): Result<List<TechnicalSignal>> =
        fetchAndAnalyze("growth_technology_stocks")

    private suspend fun fetchAndAnalyze(screenId: String): Result<List<TechnicalSignal>> =
        runCatching {
            // 1. Fetch screener — try primary, fallback to secondary
            val stocks = fetchScreener(screenId)

            // 2. Quick pre-filter to avoid wasting chart API calls
            val candidates = stocks
                .filter { it.changePercent > 0 && it.price > 0 }
                .sortedByDescending { TechnicalAnalysis.quickScore(it) }
                .take(20)  // analyze top 20 candidates

            // 3. Fetch historical candles for each candidate in parallel
            val signals = coroutineScope {
                candidates.map { stock ->
                    async { analyzeStock(stock) }
                }.awaitAll()
            }

            // 4. Keep only strong buy / buy signals, sorted by score
            signals
                .filterNotNull()
                .filter { it.signalScore >= MIN_SCORE }
                .sortedByDescending { it.signalScore }
        }.also {
            if (it.isFailure) Log.e(tag, "fetchAndAnalyze($screenId): ${it.exceptionOrNull()?.message}")
        }

    private suspend fun fetchScreener(screenId: String): List<Stock> {
        return try {
            val r = ApiClient.service.getScreener(screenId = screenId)
            r.finance?.result?.firstOrNull()?.quotes ?: emptyList()
        } catch (e: Exception) {
            Log.w(tag, "Primary screener failed, trying fallback: ${e.message}")
            try {
                val r = ApiClient.fallback.getScreener(screenId = screenId)
                r.finance?.result?.firstOrNull()?.quotes ?: emptyList()
            } catch (e2: Exception) {
                Log.e(tag, "Fallback screener also failed: ${e2.message}")
                emptyList()
            }
        }
    }

    private suspend fun analyzeStock(stock: Stock): TechnicalSignal? {
        return try {
            val candles = fetchCandles(stock.symbol) ?: return TechnicalSignal(
                stock = stock,
                signalScore = TechnicalAnalysis.quickScore(stock)
            )
            TechnicalAnalysis.analyze(stock, candles)
        } catch (e: Exception) {
            Log.w(tag, "analyzeStock(${stock.symbol}): ${e.message}")
            null
        }
    }

    private suspend fun fetchCandles(symbol: String): List<OHLCV>? {
        return try {
            val resp = ApiClient.service.getChart(symbol)
            val result = resp.chart?.result?.firstOrNull() ?: return null
            val timestamps = result.timestamp ?: return null
            val quote = result.indicators?.quote?.firstOrNull() ?: return null

            timestamps.indices.mapNotNull { i ->
                val close = quote.close?.getOrNull(i) ?: return@mapNotNull null
                val open = quote.open?.getOrNull(i) ?: close
                val high = quote.high?.getOrNull(i) ?: close
                val low = quote.low?.getOrNull(i) ?: close
                val vol = quote.volume?.getOrNull(i) ?: 0L
                OHLCV(timestamps[i], open, high, low, close, vol)
            }
        } catch (e: Exception) {
            Log.w(tag, "fetchCandles($symbol): ${e.message}")
            null
        }
    }
}
