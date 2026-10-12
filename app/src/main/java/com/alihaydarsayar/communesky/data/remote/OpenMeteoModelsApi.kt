package com.alihaydarsayar.communesky.data.remote

import com.alihaydarsayar.communesky.model.CandidateSeries
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.NationalDay
import com.alihaydarsayar.communesky.model.NationalHour
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo'dan birden çok modelin tahmini, birden çok yer için tek istekte: modeller ve koordinatlar virgülle
 * ayrılır. Open-Meteo her değişkenin adına modelin adını ekler ("temperature_2m_ecmwf_ifs"); alan adları isteğe
 * göre değiştiği için cevap elle okunur. Tek yer istenince cevap bir nesne, birden çok yer istenince aynı sırada
 * bir listedir (bkz. [OpenMeteoModels.places]).
 */
interface OpenMeteoModelsApi {
    @GET("v1/forecast")
    suspend fun models(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String,
        @Query("models") models: String,
        @Query("hourly") hourly: String = "temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weather_code",
        @Query("forecast_days") forecastDays: Int = 8,
        @Query("timezone") timezone: String = "auto",
    ): JsonElement
}

/** Open-Meteo'daki model adları. Adlar Open-Meteo belgelerinden; yanlış ad sessizce boş veri döndürür (testle sabit). */
object OpenMeteoModels {
    val names: Map<ForecastSource, String> = mapOf(
        ForecastSource.EcmwfIfs to "ecmwf_ifs",
        ForecastSource.EcmwfAifs to "ecmwf_aifs025_single",
        ForecastSource.OpenMeteo to "best_match",
        ForecastSource.MeteoFrance to "meteofrance_seamless",
        ForecastSource.UkMetOffice to "ukmo_seamless",
    )

    fun query(sources: List<ForecastSource>): String = sources.mapNotNull { names[it] }.joinToString(",")

    /** Cevabın yer başına parçaları: tek yer için nesne, birden çok yer için liste gelir. Saf; test edilir. */
    fun places(response: JsonElement): List<JsonObject> = when (response) {
        is JsonObject -> listOf(response)
        is JsonArray -> response.filterIsInstance<JsonObject>()
        else -> emptyList()
    }

    /** Cevaptaki her modelin serisi. Hiç sıcaklığı olmayan (o noktayı kapsamayan) model atlanır. Saf; test edilir. */
    fun parse(response: JsonObject, sources: List<ForecastSource>): List<CandidateSeries> {
        val hourly = response["hourly"] as? JsonObject
        val daily = response["daily"] as? JsonObject
        val hourTimes = hourly.strings("time")
        val dayTimes = daily.strings("time")
        return sources.mapNotNull { source ->
            val suffix = "_" + (names[source] ?: return@mapNotNull null)
            val temperature = hourly.numbers("temperature_2m$suffix")
            if (temperature.all { it == null }) return@mapNotNull null
            val code = hourly.numbers("weather_code$suffix")
            val humidity = hourly.numbers("relative_humidity_2m$suffix")
            val wind = hourly.numbers("wind_speed_10m$suffix")
            val direction = hourly.numbers("wind_direction_10m$suffix")
            val gust = hourly.numbers("wind_gusts_10m$suffix")
            val max = daily.numbers("temperature_2m_max$suffix")
            val min = daily.numbers("temperature_2m_min$suffix")
            val dayCode = daily.numbers("weather_code$suffix")
            CandidateSeries(
                source = source,
                hours = hourTimes.indices.mapNotNull { i ->
                    NationalHour(
                        time = hourTimes[i],
                        weatherCode = code.getOrNull(i)?.toInt(),
                        temperature = temperature.getOrNull(i) ?: return@mapNotNull null,
                        humidity = humidity.getOrNull(i)?.toInt(),
                        windSpeed = wind.getOrNull(i),
                        windDirection = direction.getOrNull(i)?.toInt(),
                        gust = gust.getOrNull(i),
                    )
                },
                days = dayTimes.indices.mapNotNull { i ->
                    if (max.getOrNull(i) == null && min.getOrNull(i) == null) return@mapNotNull null
                    NationalDay(dayTimes[i], dayCode.getOrNull(i)?.toInt(), max.getOrNull(i), min.getOrNull(i))
                },
            )
        }
    }

    private fun JsonObject?.array(name: String): List<JsonElement> = (this?.get(name) as? JsonArray).orEmpty()
    private fun JsonObject?.strings(name: String): List<String> = array(name).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    private fun JsonObject?.numbers(name: String): List<Double?> = array(name).map { element ->
        (element as? JsonPrimitive)?.let { it.doubleOrNull ?: it.intOrNull?.toDouble() }
    }
}
