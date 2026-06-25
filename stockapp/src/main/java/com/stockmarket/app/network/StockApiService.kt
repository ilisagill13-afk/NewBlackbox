package com.stockmarket.app.network

import com.stockmarket.app.model.ChartResponse
import com.stockmarket.app.model.StockResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface StockApiService {

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/105.0",
        "Accept: application/json, */*",
        "Accept-Language: en-US,en;q=0.9"
    )
    @GET("v1/finance/screener/predefined/saved")
    suspend fun getScreener(
        @Query("formatted") formatted: Boolean = false,
        @Query("scrIds") screenId: String,
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 30,
        @Query("corsDomain") domain: String = "finance.yahoo.com"
    ): StockResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/105.0",
        "Accept: application/json, */*",
        "Accept-Language: en-US,en;q=0.9"
    )
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "3mo",
        @Query("includePrePost") prePost: Boolean = false
    ): ChartResponse
}
