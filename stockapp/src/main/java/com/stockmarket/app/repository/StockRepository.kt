package com.stockmarket.app.repository

import android.util.Log
import com.stockmarket.app.model.Stock
import com.stockmarket.app.network.ApiClient

class StockRepository {

    private val tag = "StockRepository"

    suspend fun getDayGainers(): Result<List<Stock>> =
        fetchWithFallback("day_gainers")

    suspend fun getMostActive(): Result<List<Stock>> =
        fetchWithFallback("most_actives")

    suspend fun getGrowthTech(): Result<List<Stock>> =
        fetchWithFallback("growth_technology_stocks")

    private suspend fun fetchWithFallback(screenId: String): Result<List<Stock>> {
        val primary = runCatching {
            val resp = ApiClient.service.getScreener(screenId = screenId)
            parseAndFilter(resp.finance?.result?.firstOrNull()?.quotes)
        }
        if (primary.isSuccess && primary.getOrNull()?.isNotEmpty() == true) return primary

        Log.w(tag, "Primary failed for $screenId, trying fallback")
        return runCatching {
            val resp = ApiClient.fallback.getScreener(screenId = screenId)
            parseAndFilter(resp.finance?.result?.firstOrNull()?.quotes)
        }.also {
            if (it.isFailure) Log.e(tag, "Fallback also failed: ${it.exceptionOrNull()?.message}")
        }
    }

    private fun parseAndFilter(quotes: List<Stock>?): List<Stock> {
        return quotes
            ?.filter { it.changePercent > 0 && it.price > 0 }
            ?.sortedByDescending { it.momentumScore }
            ?: emptyList()
    }
}
