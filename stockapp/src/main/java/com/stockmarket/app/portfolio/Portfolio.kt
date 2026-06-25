package com.stockmarket.app.portfolio

import com.google.gson.Gson
import kotlin.math.max

data class Position(
    val symbol: String,
    val companyName: String,
    val shares: Double,
    val buyPrice: Double,
    val buyTime: Long = System.currentTimeMillis(),
    var currentPrice: Double = buyPrice,

    // Risk management
    var stopLossPrice: Double = 0.0,     // auto-sell if price drops here
    var takeProfitPrice: Double = 0.0,   // auto-sell if price reaches here
    var trailingStopPct: Double = 0.0,   // trailing stop percentage (e.g. 3.0 = 3%)
    var highWaterMark: Double = buyPrice, // highest price seen since buy
    var alertSent: Boolean = false
) {
    val invested: Double get() = shares * buyPrice
    val currentValue: Double get() = shares * currentPrice
    val profitLoss: Double get() = currentValue - invested
    val profitLossPct: Double get() = if (invested > 0) (profitLoss / invested) * 100.0 else 0.0
    val isProfit: Boolean get() = profitLoss > 0

    val stopLossPct: Double
        get() = if (buyPrice > 0 && stopLossPrice > 0) ((stopLossPrice - buyPrice) / buyPrice) * 100.0 else 0.0

    val distanceToStopPct: Double
        get() = if (currentPrice > 0 && stopLossPrice > 0) ((currentPrice - stopLossPrice) / currentPrice) * 100.0 else 100.0

    val isNearStop: Boolean get() = distanceToStopPct in 0.0..2.0   // within 2% of stop
    val isStopHit: Boolean get() = stopLossPrice > 0 && currentPrice <= stopLossPrice
    val isTakeProfitHit: Boolean get() = takeProfitPrice > 0 && currentPrice >= takeProfitPrice

    // Update trailing stop when price rises
    fun updateTrailingStop(): Position {
        if (trailingStopPct <= 0) return this
        val newHigh = max(highWaterMark, currentPrice)
        val newStop = newHigh * (1.0 - trailingStopPct / 100.0)
        return copy(
            highWaterMark = newHigh,
            stopLossPrice = max(stopLossPrice, newStop)  // stop only moves up, never down
        )
    }
}

data class Portfolio(
    val positions: MutableList<Position> = mutableListOf(),
    var cashBalance: Double = 0.0,
    var defaultStopLossPct: Double = 3.0,    // default 3% stop loss
    var defaultTakeProfitPct: Double = 10.0, // default 10% take profit
    var trailingStopEnabled: Boolean = true
) {
    val totalInvested: Double get() = positions.sumOf { it.invested }
    val totalCurrentValue: Double get() = positions.sumOf { it.currentValue } + cashBalance
    val totalProfitLoss: Double get() = positions.sumOf { it.profitLoss }
    val totalProfitLossPct: Double
        get() = if (totalInvested > 0) (totalProfitLoss / totalInvested) * 100.0 else 0.0
    val positionsAtRisk: List<Position>
        get() = positions.filter { it.isNearStop || it.isStopHit }
}

object PortfolioManager {

    private val gson = Gson()
    private var _portfolio: Portfolio? = null

    fun get(prefs: android.content.SharedPreferences): Portfolio {
        if (_portfolio != null) return _portfolio!!
        val json = prefs.getString("portfolio_v2", null)
        _portfolio = if (json != null) {
            try { gson.fromJson(json, Portfolio::class.java) } catch (e: Exception) { Portfolio() }
        } else Portfolio()
        return _portfolio!!
    }

    fun save(prefs: android.content.SharedPreferences, portfolio: Portfolio) {
        _portfolio = portfolio
        prefs.edit().putString("portfolio_v2", gson.toJson(portfolio)).apply()
    }

    fun deposit(prefs: android.content.SharedPreferences, amount: Double): Portfolio {
        val p = get(prefs)
        p.cashBalance += amount
        save(prefs, p)
        return p
    }

    fun buyStock(
        prefs: android.content.SharedPreferences,
        symbol: String,
        name: String,
        price: Double,
        amountToInvest: Double,
        stopLossPct: Double? = null,
        takeProfitPct: Double? = null
    ): Result<Portfolio> = runCatching {
        val p = get(prefs)
        require(p.cashBalance >= amountToInvest) { "Insufficient balance. Available: $${"%.2f".format(p.cashBalance)}" }
        require(amountToInvest > 0) { "Amount must be greater than 0" }

        val slPct = stopLossPct ?: p.defaultStopLossPct
        val tpPct = takeProfitPct ?: p.defaultTakeProfitPct
        val stopPrice = price * (1.0 - slPct / 100.0)
        val targetPrice = price * (1.0 + tpPct / 100.0)
        val shares = amountToInvest / price

        p.cashBalance -= amountToInvest

        val existing = p.positions.find { it.symbol == symbol }
        if (existing != null) {
            val totalShares = existing.shares + shares
            val avgPrice = (existing.invested + amountToInvest) / totalShares
            val newStop = avgPrice * (1.0 - slPct / 100.0)
            val newTarget = avgPrice * (1.0 + tpPct / 100.0)
            p.positions.remove(existing)
            p.positions.add(
                Position(
                    symbol, name, totalShares, avgPrice, existing.buyTime,
                    currentPrice = price,
                    stopLossPrice = newStop,
                    takeProfitPrice = newTarget,
                    trailingStopPct = if (p.trailingStopEnabled) slPct else 0.0,
                    highWaterMark = price
                )
            )
        } else {
            p.positions.add(
                Position(
                    symbol, name, shares, price,
                    currentPrice = price,
                    stopLossPrice = stopPrice,
                    takeProfitPrice = targetPrice,
                    trailingStopPct = if (p.trailingStopEnabled) slPct else 0.0,
                    highWaterMark = price
                )
            )
        }
        save(prefs, p)
        p
    }

    fun sellStock(
        prefs: android.content.SharedPreferences,
        symbol: String,
        currentPrice: Double
    ): Result<Pair<Portfolio, Position>> = runCatching {
        val p = get(prefs)
        val pos = p.positions.find { it.symbol == symbol }
            ?: error("Position not found: $symbol")
        val proceeds = pos.shares * currentPrice
        p.positions.remove(pos)
        p.cashBalance += proceeds
        save(prefs, p)
        Pair(p, pos.copy(currentPrice = currentPrice))
    }

    fun updatePrices(
        prefs: android.content.SharedPreferences,
        prices: Map<String, Double>
    ): Portfolio {
        val p = get(prefs)
        val updated = p.positions.map { pos ->
            val newPrice = prices[pos.symbol]
            if (newPrice != null && newPrice > 0) {
                pos.copy(currentPrice = newPrice).updateTrailingStop()
            } else pos
        }
        p.positions.clear()
        p.positions.addAll(updated)
        save(prefs, p)
        return p
    }

    fun updateRiskSettings(
        prefs: android.content.SharedPreferences,
        stopLossPct: Double,
        takeProfitPct: Double,
        trailingStop: Boolean
    ): Portfolio {
        val p = get(prefs)
        p.defaultStopLossPct = stopLossPct
        p.defaultTakeProfitPct = takeProfitPct
        p.trailingStopEnabled = trailingStop
        save(prefs, p)
        return p
    }

    fun invalidateCache() { _portfolio = null }
}
