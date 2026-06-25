package com.stockmarket.app.portfolio

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Position(
    val symbol: String,
    val companyName: String,
    val shares: Double,
    val buyPrice: Double,
    val buyTime: Long = System.currentTimeMillis(),
    var currentPrice: Double = buyPrice
) {
    val invested: Double get() = shares * buyPrice
    val currentValue: Double get() = shares * currentPrice
    val profitLoss: Double get() = currentValue - invested
    val profitLossPct: Double get() = if (invested > 0) (profitLoss / invested) * 100.0 else 0.0
    val isProfit: Boolean get() = profitLoss > 0
}

data class Portfolio(
    val positions: MutableList<Position> = mutableListOf(),
    var cashBalance: Double = 0.0
) {
    val totalInvested: Double get() = positions.sumOf { it.invested }
    val totalCurrentValue: Double get() = positions.sumOf { it.currentValue } + cashBalance
    val totalProfitLoss: Double get() = positions.sumOf { it.profitLoss }
    val totalProfitLossPct: Double
        get() = if (totalInvested > 0) (totalProfitLoss / totalInvested) * 100.0 else 0.0
}

object PortfolioManager {

    private val gson = Gson()
    private var _portfolio: Portfolio? = null

    fun get(prefs: android.content.SharedPreferences): Portfolio {
        if (_portfolio != null) return _portfolio!!
        val json = prefs.getString("portfolio", null)
        _portfolio = if (json != null) {
            try { gson.fromJson(json, Portfolio::class.java) } catch (e: Exception) { Portfolio() }
        } else Portfolio()
        return _portfolio!!
    }

    fun save(prefs: android.content.SharedPreferences, portfolio: Portfolio) {
        _portfolio = portfolio
        prefs.edit().putString("portfolio", gson.toJson(portfolio)).apply()
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
        amountToInvest: Double
    ): Result<Portfolio> = runCatching {
        val p = get(prefs)
        if (p.cashBalance < amountToInvest) error("Insufficient balance")
        val shares = amountToInvest / price
        p.cashBalance -= amountToInvest
        val existing = p.positions.find { it.symbol == symbol }
        if (existing != null) {
            // Average down/up — merge position
            val totalShares = existing.shares + shares
            val avgPrice = (existing.invested + amountToInvest) / totalShares
            p.positions.remove(existing)
            p.positions.add(Position(symbol, name, totalShares, avgPrice, existing.buyTime, price))
        } else {
            p.positions.add(Position(symbol, name, shares, price, System.currentTimeMillis(), price))
        }
        save(prefs, p)
        p
    }

    fun sellStock(
        prefs: android.content.SharedPreferences,
        symbol: String,
        currentPrice: Double,
        sharesToSell: Double? = null
    ): Result<Portfolio> = runCatching {
        val p = get(prefs)
        val pos = p.positions.find { it.symbol == symbol } ?: error("Position not found")
        val sell = sharesToSell ?: pos.shares
        val proceeds = sell * currentPrice
        if (sell >= pos.shares) {
            p.positions.remove(pos)
        } else {
            pos.currentPrice = currentPrice
        }
        p.cashBalance += proceeds
        save(prefs, p)
        p
    }

    fun updatePrices(prefs: android.content.SharedPreferences, prices: Map<String, Double>): Portfolio {
        val p = get(prefs)
        p.positions.forEach { pos ->
            prices[pos.symbol]?.let { pos.currentPrice = it }
        }
        save(prefs, p)
        return p
    }
}
