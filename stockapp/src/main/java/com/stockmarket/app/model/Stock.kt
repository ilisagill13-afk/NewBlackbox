package com.stockmarket.app.model

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

data class Stock(
    val symbol: String = "",
    val shortName: String = "",
    val longName: String = "",
    @SerializedName("regularMarketPrice") val price: Double = 0.0,
    @SerializedName("regularMarketChangePercent") val changePercent: Double = 0.0,
    @SerializedName("regularMarketChange") val change: Double = 0.0,
    @SerializedName("regularMarketVolume") val volume: Long = 0L,
    @SerializedName("averageDailyVolume3Month") val avgVolume: Long = 0L,
    @SerializedName("regularMarketDayHigh") val dayHigh: Double = 0.0,
    @SerializedName("regularMarketDayLow") val dayLow: Double = 0.0,
    @SerializedName("fiftyTwoWeekHigh") val high52w: Double = 0.0,
    @SerializedName("fiftyTwoWeekLow") val low52w: Double = 0.0,
    @SerializedName("fiftyDayAverage") val ma50: Double = 0.0,
    @SerializedName("twoHundredDayAverage") val ma200: Double = 0.0,
    @SerializedName("marketCap") val marketCap: Long = 0L,
    val currency: String = "USD",
    val exchange: String = "",
    @SerializedName("regularMarketPreviousClose") val prevClose: Double = 0.0
) {
    val displayName: String
        get() = shortName.ifEmpty { longName.ifEmpty { symbol } }

    val isGaining: Boolean get() = changePercent > 0

    val momentumScore: Int
        get() {
            var score = 0
            if (changePercent > 0) score += 30
            if (changePercent > 2) score += 20
            if (changePercent > 5) score += 20
            if (price > ma50 && ma50 > 0) score += 15
            if (price > ma200 && ma200 > 0) score += 15
            if (volume > avgVolume && avgVolume > 0) score += 10
            if (high52w > 0 && price > (high52w * 0.9)) score += 10
            return score.coerceAtMost(100)
        }

    val strengthLabel: String
        get() = when {
            momentumScore >= 80 -> "VERY STRONG"
            momentumScore >= 60 -> "STRONG"
            momentumScore >= 40 -> "MODERATE"
            else -> "WATCH"
        }
}

data class StockResponse(
    val finance: FinanceData? = null
)

data class FinanceData(
    val result: List<ScreenerResult>? = null,
    val error: Any? = null
)

data class ScreenerResult(
    val quotes: List<Stock>? = null,
    val total: Int = 0,
    val count: Int = 0
)
