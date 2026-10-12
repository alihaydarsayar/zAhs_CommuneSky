package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.data.extras.ExtrasFreshness
import com.alihaydarsayar.communesky.data.remote.OpenMeteoModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Ana tahminin (Open-Meteo) ulusal servisin ve öteki modellerin tahminiyle birleştirilmesi. */
class ForecastMergeTest {

    private val day = LocalDate.of(2026, 10, 12)
    private val now = day.atTime(9, 20)

    /** Open-Meteo: 12 Ekim 09:00'dan itibaren 36 saat, hep 10°, parçalı bulutlu (kod 2), yağış ihtimali %10. */
    private fun base(probability: Int = 10) = Forecast(
        current = CurrentWeather(
            time = now, temperature = 10.0, apparentTemperature = 9.0, humidity = 60, dewPoint = 3.0,
            windSpeed = 10.0, windDirection = 200, weatherCode = 2, isDay = true, pressure = 1012.0, uvIndex = 2.0, visibility = 10_000.0,
        ),
        hourly = (0 until 36).map { HourlyForecast(day.atTime(9, 0).plusHours(it.toLong()), 10.0, 2, probability, isDay = true) },
        daily = (0 until 7).map { DailyForecast(day.plusDays(it.toLong()), 2, 5.0, 12.0, probability, null, null, null) },
        utcOffsetSeconds = 10_800,
    )

    /** MGM: 3 saatte bir; 09:00 20°, 12:00 26°, 15:00 23°… ve 5 gün. */
    private fun mgm(code: Int? = 3) = CandidateSeries(
        ForecastSource.Mgm,
        hours = listOf(9 to 20.0, 12 to 26.0, 15 to 23.0, 18 to 20.0, 21 to 17.0, 24 to 14.0, 27 to 12.0, 30 to 13.0).map { (hour, temperature) ->
            NationalHour(day.atStartOfDay().plusHours(hour.toLong()).toString(), weatherCode = code, temperature = temperature)
        },
        days = (0 until 5).map { NationalDay(day.plusDays(it.toLong()).toString(), weatherCode = 61, maxTemperature = 27.0 - it, minTemperature = if (it == 4) null else 15.0) },
    )

    private fun ecmwf() = CandidateSeries(
        ForecastSource.EcmwfIfs,
        hours = (0 until 48).map { NationalHour(day.atTime(9, 0).plusHours(it.toLong()).toString(), weatherCode = 1, temperature = 30.0) },
        days = (0 until 8).map { NationalDay(day.plusDays(it.toLong()).toString(), weatherCode = 1, maxTemperature = 31.0, minTemperature = 21.0) },
    )

    private val turkey = PlaceExtras(centerId = 93414)

    @Test
    fun `with a full MGM forecast, temperatures and icons come from MGM`() {
        val result = ForecastMerge.merge(base(), turkey.copy(national = mgm(), models = listOf(ecmwf())), now)
        assertEquals(ForecastSource.Mgm, result.sources.forecast)
        assertFalse(result.sources.nationalUnavailable)
        val hours = result.forecast.hourly.associateBy { it.time.hour to it.time.dayOfMonth }
        // Tam saatler MGM değeri; aradakiler komşu değerlerden doğrusal: 09:00 20°, 12:00 26° → 10:00 22°, 11:00 24°.
        assertEquals(20.0, hours[9 to 12]!!.temperature, 0.001)
        assertEquals(22.0, hours[10 to 12]!!.temperature, 0.001)
        assertEquals(24.0, hours[11 to 12]!!.temperature, 0.001)
        assertEquals(26.0, hours[12 to 12]!!.temperature, 0.001)
        // İkon en yakın MGM saatinden; yağış ihtimali Open-Meteo'dan.
        assertEquals(3, hours[10 to 12]!!.weatherCode)
        assertEquals(10, hours[10 to 12]!!.precipitationProbability)
        // MGM'nin bittiği saatten sonrası (13 Ekim 06:00'dan sonra) Open-Meteo'da kalır.
        assertEquals(10.0, result.forecast.hourly.last().temperature, 0.001)
        // Günlük: ilk 5 gün MGM; 5. günün gece en düşüğü MGM'de yok, Open-Meteo'dan; 6. günden sonrası ECMWF.
        val daily = result.forecast.daily
        assertEquals(27.0, daily[0].maxTemperature, 0.001)
        assertEquals(15.0, daily[0].minTemperature, 0.001)
        assertEquals(61, daily[0].weatherCode)
        assertEquals(23.0, daily[4].maxTemperature, 0.001)
        assertEquals(5.0, daily[4].minTemperature, 0.001)
        assertEquals(31.0, daily[5].maxTemperature, 0.001)
        assertEquals(21.0, daily[6].minTemperature, 0.001)
    }

    @Test
    fun `missing MGM values fall back piece by piece`() {
        // Saatlik istasyonu olmayan kırsal yer: saatlik yok, günlük var. Saatlik ECMWF'ye düşer; günlük MGM'den.
        val onlyDaily = mgm().copy(hours = emptyList())
        val result = ForecastMerge.merge(base(), turkey.copy(national = onlyDaily, models = listOf(ecmwf())), now)
        assertEquals(ForecastSource.EcmwfIfs, result.sources.forecast)
        assertTrue(result.sources.nationalUnavailable)
        assertEquals(30.0, result.forecast.hourly.first().temperature, 0.001)
        // Tek bir saatin sıcaklığı eksikse (‑9999) o saat komşularından hesaplanır.
        val gap = mgm().let { series -> series.copy(hours = series.hours.map { if (it.at.hour == 12) it.copy(temperature = null) else it }) }
        val filled = ForecastMerge.merge(base(), turkey.copy(national = gap), now)
        assertEquals(ForecastSource.Mgm, filled.sources.forecast)
        assertEquals(21.5, filled.forecast.hourly.first { it.time.hour == 12 }.temperature, 0.001)
    }

    @Test
    fun `when MGM fails, ECMWF is used and then Open-Meteo`() {
        val failed = turkey.copy(nationalFailed = true, models = listOf(ecmwf()))
        val result = ForecastMerge.merge(base(), failed, now)
        assertEquals(ForecastSource.EcmwfIfs, result.sources.forecast)
        assertTrue(result.sources.nationalUnavailable)
        assertEquals(31.0, result.forecast.daily[0].maxTemperature, 0.001)
        // ECMWF de yoksa ana tahmin olduğu gibi kalır; ekran asla boş kalmaz.
        val nothing = ForecastMerge.merge(base(), turkey.copy(nationalFailed = true), now)
        assertEquals(ForecastSource.OpenMeteo, nothing.sources.forecast)
        assertTrue(nothing.sources.nationalUnavailable)
        assertEquals(base().hourly, nothing.forecast.hourly)
    }

    @Test
    fun `when MGM answers again the forecast returns to MGM`() {
        val failed = turkey.copy(nationalFailed = true, models = listOf(ecmwf()))
        assertEquals(ForecastSource.EcmwfIfs, ForecastMerge.merge(base(), failed, now).sources.forecast)
        // Sonraki istekte MGM cevap verdi: işaret kalkar, tahmin yeniden MGM'den gelir.
        val back = ForecastMerge.merge(base(), failed.copy(national = mgm(), nationalFailed = false), now)
        assertEquals(ForecastSource.Mgm, back.sources.forecast)
        assertFalse(back.sources.nationalUnavailable)
        // Başarısız bir isteğin zamanı kaydedilmez: 30 dakika beklenmeden yeniden denenir.
        assertTrue(ExtrasFreshness.isStale(0, 10_000_000L, ExtrasFreshness.FORECAST, force = false))
    }

    @Test
    fun `outside Türkiye the forecast stays as it is`() {
        val abroad = PlaceExtras(models = listOf(ecmwf()))
        val result = ForecastMerge.merge(base(), abroad, now)
        assertEquals(ForecastSource.OpenMeteo, result.sources.forecast)
        assertFalse(result.sources.nationalUnavailable)
        assertEquals(base().hourly, result.forecast.hourly)
        assertEquals(base().daily, result.forecast.daily)
        // Ek veri hiç yoksa da aynı.
        assertEquals(ForecastSource.OpenMeteo, ForecastMerge.merge(base(), null, now).sources.forecast)
    }

    @Test
    fun `the referee's choice comes first`() {
        val extras = turkey.copy(national = mgm(), models = listOf(ecmwf()), chosen = ForecastSource.EcmwfIfs)
        val result = ForecastMerge.merge(base(), extras, now)
        assertEquals(ForecastSource.EcmwfIfs, result.sources.forecast)
        assertFalse(result.sources.nationalUnavailable)
        assertEquals(30.0, result.forecast.hourly.first().temperature, 0.001)
    }

    @Test
    fun `icon and rain chance never contradict each other`() {
        // MGM yağmur diyor (kod 61), Open-Meteo %10: yüzde gösterilmez, "Yağmur bekleniyor (MGM)".
        val rainy = ForecastMerge.merge(base(probability = 10), turkey.copy(national = mgm(code = 61)), now)
        assertEquals(RainNote.Expected(ForecastSource.Mgm), rainy.sources.rainNote)
        assertFalse(rainy.forecast.hourly.first().showProbability)
        // MGM yağmur demiyor, Open-Meteo %70: ikon MGM'den, yanında "Yağmur ihtimali %70".
        val dry = ForecastMerge.merge(base(probability = 70), turkey.copy(national = mgm(code = 3)), now)
        assertEquals(RainNote.Chance(70), dry.sources.rainNote)
        assertEquals(3, dry.forecast.hourly.first().weatherCode)
        assertTrue(dry.forecast.hourly.first().showProbability)
        // İkisi de aynı şeyi söylüyorsa not yok.
        assertNull(ForecastMerge.merge(base(probability = 70), turkey.copy(national = mgm(code = 61)), now).sources.rainNote)
        assertNull(ForecastMerge.merge(base(probability = 10), turkey.copy(national = mgm(code = 3)), now).sources.rainNote)
    }

    @Test
    fun `hours are not invented across a long gap`() {
        val hours = listOf(NationalHour("2026-10-12T09:00", temperature = 10.0), NationalHour("2026-10-12T21:00", temperature = 22.0))
        assertNull(ForecastMerge.temperatureAt(hours, LocalDateTime.of(2026, 10, 12, 15, 0)))
        assertEquals(10.0, ForecastMerge.temperatureAt(hours, LocalDateTime.of(2026, 10, 12, 9, 0))!!, 0.0)
        assertNull(ForecastMerge.temperatureAt(hours, LocalDateTime.of(2026, 10, 12, 8, 0)))
    }

    @Test
    fun `Open-Meteo model names and the multi-model answer`() {
        // Adlar Open-Meteo belgelerindeki adlardır; yanlış ad sessizce boş veri döndürür.
        assertEquals("ecmwf_ifs,ecmwf_aifs025_single,best_match", OpenMeteoModels.query(listOf(ForecastSource.EcmwfIfs, ForecastSource.EcmwfAifs, ForecastSource.OpenMeteo)))
        assertEquals("meteofrance_seamless", OpenMeteoModels.names[ForecastSource.MeteoFrance])
        assertEquals("ukmo_seamless", OpenMeteoModels.names[ForecastSource.UkMetOffice])
        val response = Json.decodeFromString(
            JsonObject.serializer(),
            """{"hourly":{"time":["2026-10-12T09:00","2026-10-12T10:00"],
                 "temperature_2m_ecmwf_ifs":[18.5,19.0],"weather_code_ecmwf_ifs":[3,61],
                 "temperature_2m_ecmwf_aifs025_single":[null,null],
                 "temperature_2m_best_match":[18.0,null],"weather_code_best_match":[2,2]},
                "daily":{"time":["2026-10-12"],"temperature_2m_max_ecmwf_ifs":[24.1],"temperature_2m_min_ecmwf_ifs":[15.2],"weather_code_ecmwf_ifs":[61],
                 "temperature_2m_max_best_match":[23.0],"temperature_2m_min_best_match":[14.0]}}""",
        )
        val series = OpenMeteoModels.parse(response, listOf(ForecastSource.EcmwfIfs, ForecastSource.EcmwfAifs, ForecastSource.OpenMeteo))
        // O noktayı kapsamayan model (hepsi null) atlanır.
        assertEquals(listOf(ForecastSource.EcmwfIfs, ForecastSource.OpenMeteo), series.map { it.source })
        assertEquals(19.0, series[0].hours[1].temperature!!, 0.0)
        assertEquals(61, series[0].hours[1].weatherCode)
        assertEquals(24.1, series[0].days[0].maxTemperature!!, 0.0)
        // Sıcaklığı eksik saat atlanır.
        assertEquals(1, series[1].hours.size)
    }

    @Test
    fun `data is asked for again only when it is stale`() {
        val now = 10_000_000L
        assertFalse(ExtrasFreshness.isStale(now - 29 * 60_000L, now, ExtrasFreshness.FORECAST, force = false))
        assertTrue(ExtrasFreshness.isStale(now - 30 * 60_000L, now, ExtrasFreshness.FORECAST, force = false))
        assertTrue(ExtrasFreshness.isStale(now - 16 * 60_000L, now, ExtrasFreshness.ALERTS, force = false))
        // Hiç alınmadıysa ya da kullanıcı aşağı çekip yenilediyse eşiğe bakılmaz.
        assertTrue(ExtrasFreshness.isStale(0, now, ExtrasFreshness.FORECAST, force = false))
        assertTrue(ExtrasFreshness.isStale(now - 1_000, now, ExtrasFreshness.FORECAST, force = true))
    }

    @Test
    fun `the learned correction shifts day and night temperatures apart`() {
        val night = base().let { forecast -> forecast.copy(hourly = forecast.hourly.mapIndexed { i, hour -> hour.copy(isDay = i < 10) }) }
        val result = ForecastMerge.merge(night, PlaceExtras(dayCorrection = -1.0, nightCorrection = 1.5), now)
        // Gündüz saatleri 1° düşer, gece saatleri 1,5° artar; en yüksek gündüz, en düşük gece düzeltmesini alır.
        assertEquals(9.0, result.forecast.hourly[0].temperature, 0.001)
        assertEquals(11.5, result.forecast.hourly[12].temperature, 0.001)
        assertEquals(11.0, result.forecast.daily[0].maxTemperature, 0.001)
        assertEquals(6.5, result.forecast.daily[0].minTemperature, 0.001)
        assertEquals(-1.0, result.sources.dayCorrection, 0.0)
        // Düzeltme yoksa hiçbir şey değişmez.
        assertEquals(night.hourly, ForecastMerge.merge(night, PlaceExtras(), now).forecast.hourly)
    }
}
