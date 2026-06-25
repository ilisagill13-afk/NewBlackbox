package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.StockScreenerResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface StockApiService {

    @Headers(
        "User-Agent: Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0",
        "Accept: application/json"
    )
    @GET("v1/finance/screener/predefined/saved")
    suspend fun getDayGainers(
        @Query("formatted") formatted: Boolean = false,
        @Query("scrIds") screenId: String = "day_gainers",
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 25
    ): StockScreenerResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0",
        "Accept: application/json"
    )
    @GET("v1/finance/screener/predefined/saved")
    suspend fun getMostActive(
        @Query("formatted") formatted: Boolean = false,
        @Query("scrIds") screenId: String = "most_actives",
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 25
    ): StockScreenerResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0",
        "Accept: application/json"
    )
    @GET("v1/finance/screener/predefined/saved")
    suspend fun getWeeklyGainers(
        @Query("formatted") formatted: Boolean = false,
        @Query("scrIds") screenId: String = "day_gainers_weekly",
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 25
    ): StockScreenerResponse
}
