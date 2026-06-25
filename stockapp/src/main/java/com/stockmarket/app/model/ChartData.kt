package com.stockmarket.app.model

import com.google.gson.annotations.SerializedName

data class ChartResponse(
    val chart: ChartWrapper? = null
)

data class ChartWrapper(
    val result: List<ChartResult>? = null,
    val error: Any? = null
)

data class ChartResult(
    val meta: ChartMeta? = null,
    val timestamp: List<Long>? = null,
    val indicators: ChartIndicators? = null
)

data class ChartMeta(
    val symbol: String = "",
    val regularMarketPrice: Double = 0.0,
    val previousClose: Double = 0.0
)

data class ChartIndicators(
    val quote: List<QuoteData>? = null
)

data class QuoteData(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null,
    @SerializedName("volume") val volume: List<Long?>? = null
)

data class OHLCV(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
