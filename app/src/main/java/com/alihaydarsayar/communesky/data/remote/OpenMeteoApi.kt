package com.alihaydarsayar.communesky.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API. Retrofit bu arayüzden HTTP isteği yapan kodu otomatik üretir.
 * Örnek istek: https://api.open-meteo.com/v1/forecast?latitude=41.01&longitude=28.98&current=...
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponseDto

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/"

        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m," +
                "wind_speed_10m,weather_code,is_day"

        // API'nin döndürdüğü ama bizim kullanmadığımız alanlar hata vermesin diye ignoreUnknownKeys.
        private val json = Json { ignoreUnknownKeys = true }

        fun create(): OpenMeteoApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
    }
}
