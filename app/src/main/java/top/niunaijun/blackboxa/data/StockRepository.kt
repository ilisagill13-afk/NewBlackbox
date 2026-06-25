package top.niunaijun.blackboxa.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.niunaijun.blackboxa.bean.StockBean
import java.util.concurrent.TimeUnit

class StockRepository {

    private val api: StockApiService by lazy { createApi() }

    private fun createApi(): StockApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://query1.finance.yahoo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StockApiService::class.java)
    }

    suspend fun getDayGainers(): Result<List<StockBean>> = runCatching {
        val response = api.getDayGainers()
        response.finance?.result?.firstOrNull()?.quotes
            ?.filter { it.changePercent > 0 }
            ?.sortedByDescending { it.changePercent }
            ?: emptyList()
    }.also { if (it.isFailure) Log.e("StockRepo", "getDayGainers error", it.exceptionOrNull()) }

    suspend fun getMostActive(): Result<List<StockBean>> = runCatching {
        val response = api.getMostActive()
        response.finance?.result?.firstOrNull()?.quotes
            ?.filter { it.changePercent > 0 }
            ?.sortedByDescending { it.changePercent }
            ?: emptyList()
    }.also { if (it.isFailure) Log.e("StockRepo", "getMostActive error", it.exceptionOrNull()) }

    suspend fun getWeeklyGainers(): Result<List<StockBean>> = runCatching {
        val response = api.getWeeklyGainers()
        response.finance?.result?.firstOrNull()?.quotes
            ?.filter { it.changePercent > 0 }
            ?.sortedByDescending { it.changePercent }
            ?: emptyList()
    }.also { if (it.isFailure) Log.e("StockRepo", "getWeeklyGainers error", it.exceptionOrNull()) }
}
