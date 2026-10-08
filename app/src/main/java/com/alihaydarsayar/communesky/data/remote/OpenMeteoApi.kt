package com.alihaydarsayar.communesky.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API. Retrofit bu arayüzden HTTP isteği yapan kodu otomatik üretir.
 * Tek bir istekle anlık, saatlik ve günlük verilerin hepsi gelir; pil ve hız için iyi.
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        // Saatlik veri şu anki saatten başlar. 36 saat alıyoruz ki önbellek birkaç saat eskise de
        // önümüzdeki 24 saati gösterebilelim.
        @Query("forecast_hours") forecastHours: Int = 36,
        @Query("forecast_days") forecastDays: Int = 7,
        // 15 dakikalık yağış: "yağmur X dk içinde başlıyor" uyarısı için. Orta Avrupa ve Kuzey
        // Amerika'da gerçek 15 dakikalık modellerden, diğer yerlerde saatlik verinin ara
        // değerlerinden gelir; o yüzden uyarıda süreyi 5 dakikaya yuvarlıyoruz. 3 saat yeterli.
        @Query("minutely_15") minutely15: String = "precipitation",
        @Query("forecast_minutely_15") forecastMinutely15: Int = 12,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponseDto

    /**
     * Birden çok yer için tek istek: koordinatlar virgülle ayrılır ("41.0,40.8"), cevap aynı
     * sırada bir liste olarak gelir. Arka plan güncellemesinde her yer için ayrı istek atmak
     * yerine telsizi bir kez uyandırır; pil dostu.
     */
    @GET("v1/forecast")
    suspend fun getForecasts(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("forecast_hours") forecastHours: Int = 36,
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("minutely_15") minutely15: String = "precipitation",
        @Query("forecast_minutely_15") forecastMinutely15: Int = 12,
        @Query("timezone") timezone: String = "auto",
    ): List<ForecastResponseDto>

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
                "wind_speed_10m,wind_direction_10m,weather_code,is_day,pressure_msl," +
                "uv_index,visibility,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
                "precipitation,rain,showers,snowfall"

        private const val HOURLY_FIELDS =
            "temperature_2m,weather_code,precipitation_probability,is_day," +
                "cloud_cover_low,cloud_cover_mid,cloud_cover_high,sunshine_duration," +
                "precipitation,showers,snowfall"

        private const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                "sunrise,sunset,uv_index_max,sunshine_duration,daylight_duration,precipitation_sum"
    }
}
