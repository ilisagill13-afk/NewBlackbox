package com.stockmarket.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stockmarket.app.network.ApiClient
import com.stockmarket.app.portfolio.PortfolioManager
import com.stockmarket.app.util.NotificationHelper
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 minutes.
 * - Fetches current prices for all held positions
 * - Updates trailing stops
 * - Auto-sells if stop-loss or take-profit is triggered
 * - Sends push notifications for alerts
 */
class PriceMonitorWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PriceMonitorWorker"
        private const val WORK_NAME = "stockpulse_price_monitor"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceMonitorWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Price monitor scheduled every 15 minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun isMarketHours(): Boolean {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekday = day in Calendar.MONDAY..Calendar.FRIDAY
            val timeMinutes = hour * 60 + min
            // NYSE: 9:30 AM – 4:00 PM ET
            val openMinutes = 9 * 60 + 30
            val closeMinutes = 16 * 60
            return isWeekday && timeMinutes in openMinutes..closeMinutes
        }
    }

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("stockpulse_portfolio", Context.MODE_PRIVATE)
        val portfolio = PortfolioManager.get(prefs)

        if (portfolio.positions.isEmpty()) return Result.success()

        if (!isMarketHours()) {
            Log.d(TAG, "Market closed — skipping price check")
            return Result.success()
        }

        Log.d(TAG, "Checking prices for ${portfolio.positions.size} positions")

        val symbols = portfolio.positions.map { it.symbol }
        val prices = fetchCurrentPrices(symbols)

        if (prices.isEmpty()) return Result.retry()

        // Update trailing stops
        val updatedPortfolio = PortfolioManager.updatePrices(prefs, prices)

        // Check each position for stop-loss / take-profit / near-stop
        val toSell = mutableListOf<Triple<String, Double, String>>() // symbol, price, reason

        for (pos in updatedPortfolio.positions) {
            val current = pos.currentPrice

            when {
                pos.isStopHit -> {
                    Log.w(TAG, "STOP-LOSS HIT: ${pos.symbol} at $current (stop: ${pos.stopLossPrice})")
                    toSell.add(Triple(pos.symbol, current, "stop"))
                }
                pos.isTakeProfitHit -> {
                    Log.i(TAG, "TAKE-PROFIT HIT: ${pos.symbol} at $current (target: ${pos.takeProfitPrice})")
                    toSell.add(Triple(pos.symbol, current, "profit"))
                }
                pos.isNearStop && !pos.alertSent -> {
                    Log.w(TAG, "NEAR STOP: ${pos.symbol} — ${pos.distanceToStopPct}% from stop")
                    NotificationHelper.notifyNearStop(
                        context, pos.symbol, current, pos.stopLossPrice, pos.distanceToStopPct
                    )
                    // Mark alert sent so we don't spam
                    val idx = updatedPortfolio.positions.indexOf(pos)
                    if (idx >= 0) updatedPortfolio.positions[idx] = pos.copy(alertSent = true)
                }
            }

            // Notify if trailing stop moved significantly
            val prevStop = pos.stopLossPrice
            val newStop = pos.updateTrailingStop().stopLossPrice
            if (newStop > prevStop + 0.01 && pos.trailingStopPct > 0) {
                NotificationHelper.notifyTrailingStopMoved(context, pos.symbol, newStop, current)
            }
        }

        PortfolioManager.save(prefs, updatedPortfolio)

        // Execute sells
        for ((symbol, price, reason) in toSell) {
            val result = PortfolioManager.sellStock(prefs, symbol, price)
            result.onSuccess { (_, soldPos) ->
                when (reason) {
                    "stop" -> NotificationHelper.notifyStopLossTriggered(
                        context, symbol, price,
                        soldPos.shares * price, soldPos.profitLoss
                    )
                    "profit" -> NotificationHelper.notifyTakeProfitTriggered(
                        context, symbol, price, soldPos.profitLoss
                    )
                }
                Log.i(TAG, "Auto-sold $symbol at $price (reason: $reason)")
            }
        }

        return Result.success()
    }

    private suspend fun fetchCurrentPrices(symbols: List<String>): Map<String, Double> {
        val prices = mutableMapOf<String, Double>()
        for (symbol in symbols) {
            try {
                val resp = ApiClient.service.getChart(symbol, interval = "1d", range = "1d")
                val price = resp.chart?.result?.firstOrNull()?.meta?.regularMarketPrice
                if (price != null && price > 0) prices[symbol] = price
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch price for $symbol: ${e.message}")
            }
        }
        return prices
    }
}
