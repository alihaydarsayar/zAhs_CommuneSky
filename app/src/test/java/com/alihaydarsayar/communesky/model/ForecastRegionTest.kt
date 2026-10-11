package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.data.extras.ExtrasFreshness
import com.alihaydarsayar.communesky.data.remote.OpenMeteoModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Ülkeye göre yerel model: bölge sınırları, istenen modeller, varsayılan kaynak ve hakemin seçimi. */
class ForecastRegionTest {

    private fun region(lat: Double, lon: Double) = ForecastRegion.of(lat, lon)

    @Test
    fun `places in France`() {
        // Paris, Marsilya, Lille, Brest, Strazburg, Bordeaux, Nice, Ajaccio.
        for ((lat, lon) in listOf(48.86 to 2.35, 43.30 to 5.37, 50.63 to 3.06, 48.39 to -4.49, 48.58 to 7.75, 44.84 to -0.58, 43.70 to 7.27, 41.92 to 8.74)) {
            assertEquals("$lat,$lon", ForecastRegion.France, region(lat, lon))
        }
    }

    @Test
    fun `places in the United Kingdom and Ireland`() {
        // Liverpool, Londra, Edinburgh, Cardiff, Belfast, Aberdeen.
        for ((lat, lon) in listOf(53.41 to -2.99, 51.51 to -0.13, 55.95 to -3.19, 51.48 to -3.18, 54.60 to -5.93, 57.15 to -2.09)) {
            assertEquals("$lat,$lon", ForecastRegion.UnitedKingdom, region(lat, lon))
        }
        // Limerick, Dublin, Cork, Galway, Letterkenny (Donegal).
        for ((lat, lon) in listOf(52.66 to -8.63, 53.35 to -6.26, 51.90 to -8.47, 53.27 to -9.05, 54.95 to -7.73)) {
            assertEquals("$lat,$lon", ForecastRegion.Ireland, region(lat, lon))
        }
    }

    @Test
    fun `neighbours and the rest of the world`() {
        assertEquals(ForecastRegion.Turkey, region(41.04, 29.01))
        // Brüksel, Cenevre, Barselona, Torino, Frankfurt, Madrid, New York, Tokyo.
        for ((lat, lon) in listOf(50.85 to 4.35, 46.20 to 6.14, 41.39 to 2.17, 45.07 to 7.69, 50.11 to 8.68, 40.42 to -3.70, 40.71 to -74.01, 35.68 to 139.69)) {
            assertEquals("$lat,$lon", ForecastRegion.Other, region(lat, lon))
        }
    }

    @Test
    fun `the local model is requested by its Open-Meteo name`() {
        val paris = PlaceExtras(queryLatitude = 48.86, queryLongitude = 2.35)
        val liverpool = PlaceExtras(queryLatitude = 53.41, queryLongitude = -2.99)
        val limerick = PlaceExtras(queryLatitude = 52.66, queryLongitude = -8.63)
        val newYork = PlaceExtras(queryLatitude = 40.71, queryLongitude = -74.01)
        assertEquals("meteofrance_seamless,ecmwf_ifs,ecmwf_aifs025_single,best_match", OpenMeteoModels.query(ExtrasFreshness.modelsFor(paris)))
        assertEquals("ukmo_seamless,ecmwf_ifs,ecmwf_aifs025_single,best_match", OpenMeteoModels.query(ExtrasFreshness.modelsFor(liverpool)))
        assertEquals("ukmo_seamless,ecmwf_ifs,ecmwf_aifs025_single,best_match", OpenMeteoModels.query(ExtrasFreshness.modelsFor(limerick)))
        assertEquals("ecmwf_ifs,ecmwf_aifs025_single,best_match", OpenMeteoModels.query(ExtrasFreshness.modelsFor(newYork)))
    }

    @Test
    fun `defaults - local model in France and the UK, the referee decides in Ireland`() {
        assertEquals(ForecastSource.MeteoFrance, ForecastMerge.defaultSource(PlaceExtras(queryLatitude = 48.86, queryLongitude = 2.35)))
        assertEquals(ForecastSource.UkMetOffice, ForecastMerge.defaultSource(PlaceExtras(queryLatitude = 53.41, queryLongitude = -2.99)))
        assertEquals(ForecastSource.OpenMeteo, ForecastMerge.defaultSource(PlaceExtras(queryLatitude = 52.66, queryLongitude = -8.63)))
        assertEquals(ForecastSource.OpenMeteo, ForecastMerge.defaultSource(PlaceExtras(queryLatitude = 40.71, queryLongitude = -74.01)))
        assertEquals(ForecastSource.Mgm, ForecastMerge.defaultSource(PlaceExtras(queryLatitude = 41.04, queryLongitude = 29.01, centerId = 1)))
        // Sıra: hakemin seçtiği, varsayılan, sonra Open-Meteo.
        val liverpool = PlaceExtras(queryLatitude = 53.41, queryLongitude = -2.99)
        assertEquals(listOf(ForecastSource.UkMetOffice, ForecastSource.OpenMeteo), ForecastMerge.order(liverpool))
        assertEquals(listOf(ForecastSource.OpenMeteo, ForecastSource.UkMetOffice), ForecastMerge.order(liverpool.copy(chosen = ForecastSource.OpenMeteo)))
        val limerick = PlaceExtras(queryLatitude = 52.66, queryLongitude = -8.63)
        assertEquals(listOf(ForecastSource.UkMetOffice, ForecastSource.OpenMeteo), ForecastMerge.order(limerick.copy(chosen = ForecastSource.UkMetOffice)))
    }

    @Test
    fun `the local model fills the hours it covers and the rest stays`() {
        val day = LocalDate.of(2026, 10, 12)
        val now = day.atTime(9, 20)
        val base = Forecast(
            current = CurrentWeather(now, 10.0, 9.0, 60, 3.0, 10.0, 200, 2, true, 1012.0, 2.0, 10_000.0),
            hourly = (0 until 48).map { HourlyForecast(day.atTime(9, 0).plusHours(it.toLong()), 10.0, 2, 10, isDay = true) },
            daily = (0 until 7).map { DailyForecast(day.plusDays(it.toLong()), 2, 5.0, 12.0, 10, null, null, null) },
            utcOffsetSeconds = 3600,
        )
        // Met Office modeli: ilk 30 saat 14°, dört gün.
        val local = CandidateSeries(
            ForecastSource.UkMetOffice,
            hours = (0 until 30).map { NationalHour(day.atTime(9, 0).plusHours(it.toLong()).toString(), weatherCode = 3, temperature = 14.0, humidity = 80, windSpeed = 20.0) },
            days = (0 until 4).map { NationalDay(day.plusDays(it.toLong()).toString(), weatherCode = 3, maxTemperature = 15.0, minTemperature = 8.0) },
        )
        val liverpool = PlaceExtras(queryLatitude = 53.41, queryLongitude = -2.99, models = listOf(local))
        val result = ForecastMerge.merge(base, liverpool, now)
        assertEquals(ForecastSource.UkMetOffice, result.sources.forecast)
        assertEquals(14.0, result.forecast.hourly[5].temperature, 1e-9)
        assertEquals(80, result.forecast.hourly[5].humidity)
        // Modelin kapsamadığı saatler ve günler ana tahminde kalır.
        assertEquals(10.0, result.forecast.hourly[40].temperature, 1e-9)
        assertEquals(15.0, result.forecast.daily[0].maxTemperature, 1e-9)
        assertEquals(12.0, result.forecast.daily[6].maxTemperature, 1e-9)
        // Model gelmediyse (boş) Open-Meteo kullanılır.
        assertEquals(ForecastSource.OpenMeteo, ForecastMerge.merge(base, liverpool.copy(models = emptyList()), now).sources.forecast)
        // Hakem Open-Meteo'yu seçtiyse yerel model kullanılmaz.
        val chosen = ForecastMerge.merge(base, liverpool.copy(chosen = ForecastSource.OpenMeteo), now)
        assertEquals(ForecastSource.OpenMeteo, chosen.sources.forecast)
        assertEquals(10.0, chosen.forecast.hourly[5].temperature, 1e-9)
    }

    @Test
    fun `the referee compares the local model with Open-Meteo's own choice`() {
        fun accuracy(source: ForecastSource, error: Double) = SourceAccuracy(source, shortTermError = error, dayAheadError = error, bias = 0.0, dayBias = 0.0, nightBias = 0.0, nextDayMaxError = error, days = 12)
        // Birleşik Krallık: varsayılan Met Office; Open-Meteo %15'ten fazla daha iyiyse ona geçilir.
        val uk = ForecastSource.UkMetOffice
        assertEquals(ForecastSource.OpenMeteo, ForecastReferee.choose(uk, listOf(accuracy(uk, 2.0), accuracy(ForecastSource.OpenMeteo, 1.5))))
        assertEquals(null, ForecastReferee.choose(uk, listOf(accuracy(uk, 2.0), accuracy(ForecastSource.OpenMeteo, 1.8))))
        // İrlanda: varsayılan Open-Meteo; Met Office belirgin biçimde iyiyse o seçilir.
        val open = ForecastSource.OpenMeteo
        assertEquals(uk, ForecastReferee.choose(open, listOf(accuracy(open, 2.0), accuracy(uk, 1.6))))
        assertTrue(ForecastRegion.Ireland.localModel == uk)
    }
}
