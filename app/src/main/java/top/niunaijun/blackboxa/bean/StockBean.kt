package top.niunaijun.blackboxa.bean

import com.google.gson.annotations.SerializedName

data class StockBean(
    val symbol: String = "",
    val shortName: String = "",
    val longName: String = "",
    @SerializedName("regularMarketPrice") val price: Double = 0.0,
    @SerializedName("regularMarketChangePercent") val changePercent: Double = 0.0,
    @SerializedName("regularMarketChange") val change: Double = 0.0,
    @SerializedName("regularMarketVolume") val volume: Long = 0L,
    @SerializedName("regularMarketDayHigh") val dayHigh: Double = 0.0,
    @SerializedName("regularMarketDayLow") val dayLow: Double = 0.0,
    @SerializedName("fiftyTwoWeekHigh") val weekHigh52: Double = 0.0,
    @SerializedName("fiftyTwoWeekLow") val weekLow52: Double = 0.0,
    @SerializedName("marketCap") val marketCap: Long = 0L,
    val currency: String = "USD",
    val exchange: String = ""
) {
    val displayName: String get() = if (shortName.isNotEmpty()) shortName else longName.ifEmpty { symbol }
}

data class StockScreenerResponse(
    val finance: FinanceWrapper? = null
)

data class FinanceWrapper(
    val result: List<ScreenerResult>? = null,
    val error: Any? = null
)

data class ScreenerResult(
    val quotes: List<StockBean>? = null,
    val total: Int = 0,
    val count: Int = 0
)
