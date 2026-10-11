package com.alihaydarsayar.communesky.data.remote

import com.alihaydarsayar.communesky.model.AirHour
import com.alihaydarsayar.communesky.model.AirQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Air Quality API: Copernicus Atmosphere Monitoring Service (CAMS) verisi, CC BY 4.0. Anahtar
 * gerekmez. Üç günün saatlik değerleri tek istekte gelir; saatte bir, hava güncellemesiyle aynı çalışmada istenir.
 */
interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("forecast_days") forecastDays: Int = 3,
        @Query("timezone") timezone: String = "auto",
    ): AirQualityDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
        const val HOURLY_FIELDS = "european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide"
    }
}

@Serializable
data class AirQualityDto(val hourly: AirHourlyDto? = null) {
    fun toModel(): AirQuality {
        val h = hourly ?: return AirQuality()
        return AirQuality(
            h.time.indices.map { i ->
                AirHour(
                    time = h.time[i],
                    aqi = h.aqi.getOrNull(i)?.let { Math.round(it).toInt() },
                    pm25 = h.pm25.getOrNull(i),
                    pm10 = h.pm10.getOrNull(i),
                    ozone = h.ozone.getOrNull(i),
                    no2 = h.no2.getOrNull(i),
                    so2 = h.so2.getOrNull(i),
                    co = h.co.getOrNull(i),
                )
            },
        )
    }
}

@Serializable
data class AirHourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("european_aqi") val aqi: List<Double?> = emptyList(),
    @SerialName("pm2_5") val pm25: List<Double?> = emptyList(),
    val pm10: List<Double?> = emptyList(),
    val ozone: List<Double?> = emptyList(),
    @SerialName("nitrogen_dioxide") val no2: List<Double?> = emptyList(),
    @SerialName("sulphur_dioxide") val so2: List<Double?> = emptyList(),
    @SerialName("carbon_monoxide") val co: List<Double?> = emptyList(),
)
