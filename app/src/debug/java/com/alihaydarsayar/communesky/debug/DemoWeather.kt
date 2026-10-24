package com.alihaydarsayar.communesky.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alihaydarsayar.communesky.data.extras.ExtrasStore
import com.alihaydarsayar.communesky.data.local.PlaceEntity
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity
import com.alihaydarsayar.communesky.data.local.WeatherDatabase
import com.alihaydarsayar.communesky.data.remote.CurrentDto
import com.alihaydarsayar.communesky.data.remote.DailyDto
import com.alihaydarsayar.communesky.data.remote.ForecastResponseDto
import com.alihaydarsayar.communesky.data.remote.HourlyDto
import com.alihaydarsayar.communesky.data.remote.Minutely15Dto
import com.alihaydarsayar.communesky.model.AirHour
import com.alihaydarsayar.communesky.model.AirQuality
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.PlaceExtras
import com.alihaydarsayar.communesky.model.WeatherAlert
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Yalnız debug sürümünde (src/debug; release'de yoktur): README ve mağaza görselleri için örnek hava durumu. Seçilen
 * yerin ve havanın tahminini, telefonun şu anki saatine göre, uygulamanın kendi önbelleğine (Room) yazar; ekranlar
 * ve widget'lar bundan gerçek veriymiş gibi çizilir. Örnek yerler yalnız Beşiktaş, Londra ve Paris'tir.
 *
 *   adb shell am broadcast -a com.alihaydarsayar.communesky.debug.DEMO -n com.alihaydarsayar.communesky/.debug.DemoReceiver \
 *       --es place besiktas --es sky rain [--es role device|home|saved] [--ei air 35] [--es alert yellow]
 *
 * Her çağrı önceki örnek veriyi siler. Birden çok yer için testler [install] ile doğrudan çağırır.
 */
object DemoWeather {

    data class DemoPlace(val key: String, val name: String, val region: String?, val latitude: Double, val longitude: Double, val zone: String)

    val BESIKTAS = DemoPlace("besiktas", "Beşiktaş", "İstanbul, Türkiye", 41.0422, 29.0083, "Europe/Istanbul")
    val LONDON = DemoPlace("london", "London", "England, United Kingdom", 51.5074, -0.1278, "Europe/London")
    val PARIS = DemoPlace("paris", "Paris", "Île-de-France, France", 48.8566, 2.3522, "Europe/Paris")
    private val PLACES = listOf(BESIKTAS, LONDON, PARIS).associateBy { it.key }

    /** Bir hava durumu: sıcaklık, rüzgâr, UV ve bulut birbiriyle tutarlı. [week]: sonraki 7 günün (bugün dahil) hava kodları. */
    data class Sky(
        val key: String,
        val code: Int,
        val tMax: Double,
        val tMin: Double,
        val humidity: Int,
        val windKmh: Double,
        val windDir: Int,
        val uvMax: Double,
        val pressure: Double,
        val visibilityM: Double,
        val cloud: Int,
        val cloudLow: Int,
        val cloudMid: Int,
        val cloudHigh: Int,
        val week: List<Int>,
        val weekMax: List<Double>,
        val weekMin: List<Double>,
    )

    private fun week(vararg codes: Int) = codes.toList()
    private fun spread(base: Double, vararg d: Double) = d.map { base + it }

    val SKIES: Map<String, Sky> = listOf(
        Sky("clear", 0, 28.0, 19.0, 45, 12.0, 20, 9.0, 1018.0, 30000.0, 5, 0, 0, 5, week(0, 0, 1, 0, 2, 0, 0), spread(28.0, 0.0, 1.0, 2.0, 1.0, -2.0, 0.0, 1.0), spread(19.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0)),
        Sky("mostly_clear", 1, 26.0, 17.0, 50, 14.0, 340, 8.0, 1017.0, 28000.0, 20, 15, 5, 25, week(1, 0, 0, 1, 2, 3, 1), spread(26.0, 0.0, 1.0, 1.0, 0.0, -1.0, -3.0, 0.0), spread(17.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.0, 0.0)),
        Sky("partly_cloudy", 2, 24.0, 16.0, 58, 16.0, 300, 6.0, 1015.0, 25000.0, 55, 30, 40, 40, week(2, 2, 1, 3, 61, 2, 1), spread(24.0, 0.0, 1.0, 2.0, -1.0, -3.0, -1.0, 1.0), spread(16.0, 0.0, 0.0, 1.0, 0.0, -1.0, -1.0, 0.0)),
        Sky("overcast", 3, 19.0, 14.0, 72, 14.0, 250, 2.0, 1011.0, 18000.0, 100, 70, 90, 80, week(3, 3, 2, 61, 3, 2, 1), spread(19.0, 0.0, 0.0, 1.0, -2.0, -1.0, 1.0, 2.0), spread(14.0, 0.0, 0.0, 0.0, -1.0, -1.0, 0.0, 1.0)),
        Sky("drizzle", 53, 17.0, 13.0, 86, 12.0, 230, 1.0, 1009.0, 9000.0, 100, 90, 85, 60, week(53, 61, 3, 2, 3, 53, 2), spread(17.0, 0.0, -1.0, 0.0, 1.0, 2.0, 1.0, 2.0), spread(13.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0)),
        Sky("rain", 63, 15.0, 11.0, 90, 20.0, 220, 1.0, 1006.0, 8000.0, 100, 95, 90, 70, week(63, 61, 3, 80, 63, 2, 3), spread(15.0, 0.0, -1.0, 1.0, 0.0, -1.0, 2.0, 3.0), spread(11.0, 0.0, -1.0, 0.0, 0.0, -1.0, 1.0, 1.0)),
        Sky("heavy_rain", 65, 16.0, 12.0, 94, 30.0, 210, 0.0, 1002.0, 5000.0, 100, 100, 95, 80, week(65, 63, 61, 3, 80, 2, 2), spread(16.0, 0.0, -1.0, -1.0, 0.0, 1.0, 2.0, 3.0), spread(12.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0)),
        Sky("storm", 95, 24.0, 18.0, 80, 36.0, 200, 3.0, 1003.0, 12000.0, 90, 70, 90, 80, week(95, 80, 61, 3, 2, 1, 2), spread(24.0, 0.0, -2.0, -3.0, -2.0, 0.0, 1.0, 2.0), spread(18.0, 0.0, -1.0, -2.0, -2.0, -1.0, 0.0, 0.0)),
        Sky("snow", 73, 0.0, -4.0, 88, 16.0, 40, 0.0, 1016.0, 4000.0, 100, 90, 95, 70, week(73, 71, 3, 71, 2, 1, 3), spread(0.0, 0.0, -1.0, 1.0, 1.0, 2.0, 3.0, 2.0), spread(-4.0, 0.0, -1.0, -1.0, 0.0, -1.0, 0.0, 0.0)),
        Sky("sleet", 66, 2.0, -1.0, 92, 18.0, 60, 0.0, 1010.0, 5000.0, 100, 95, 95, 60, week(66, 71, 61, 3, 3, 2, 1), spread(2.0, 0.0, -1.0, 2.0, 3.0, 4.0, 4.0, 3.0), spread(-1.0, 0.0, -1.0, 0.0, 1.0, 2.0, 1.0, 1.0)),
        Sky("fog", 45, 14.0, 9.0, 97, 4.0, 90, 1.0, 1021.0, 600.0, 100, 100, 20, 10, week(45, 45, 3, 2, 1, 2, 3), spread(14.0, 0.0, 1.0, 2.0, 3.0, 2.0, 1.0, 0.0), spread(9.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0)),
        Sky("windy", 2, 21.0, 13.0, 55, 62.0, 310, 5.0, 1000.0, 30000.0, 45, 25, 30, 50, week(2, 1, 3, 61, 2, 1, 0), spread(21.0, 0.0, -1.0, -3.0, -2.0, 0.0, 1.0, 2.0), spread(13.0, 0.0, -1.0, -2.0, -1.0, 0.0, 0.0, 1.0)),
        Sky("hot", 0, 39.0, 27.0, 28, 10.0, 200, 11.0, 1011.0, 24000.0, 0, 0, 0, 0, week(0, 0, 0, 1, 0, 0, 1), spread(39.0, 0.0, 1.0, 2.0, 0.0, -1.0, 1.0, 2.0), spread(27.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)),
        Sky("freezing", 0, -4.0, -11.0, 62, 8.0, 20, 1.0, 1034.0, 30000.0, 5, 0, 0, 5, week(0, 0, 1, 2, 71, 3, 0), spread(-4.0, 0.0, 1.0, 2.0, 1.0, 0.0, -1.0, -2.0), spread(-11.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, -1.0)),
    ).associateBy { it.key }

    private val WET = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    private val SNOW = setOf(71, 73, 75, 77, 85, 86)

    /** Bir yerin örnek verisi: hangi yer, hangi hava, hangi rol (Bulunduğum yer, Ev ya da kayıtlı yer). */
    data class Entry(
        val place: DemoPlace,
        val sky: Sky,
        val role: Role = Role.Device,
        /** Hava kalitesi indeksi (CAMS ölçeği); null ise hava kalitesi verisi yok. */
        val aqi: Int? = 28,
        /** Sarı uyarı (örnek metinle); yalnız fırtınalı görseller için. */
        val yellowAlert: Boolean = false,
        /** "Bulunduğum yer" satırının konumu; null ise yerin kendi koordinatı. */
        val deviceAt: DemoPlace? = null,
        /** "Bulunduğum yer" satırında yazan ad; null ise ad bulunamamış sayılır ("Konumum"). */
        val deviceName: String? = place.name,
    )

    enum class Role { Device, Home, Saved }

    fun place(key: String): DemoPlace = PLACES.getValue(key)

    // --- Güneş -------------------------------------------------------------------------------------

    /** Gün doğumu ve batımı (yerel saat, dakika); kabaca ±10 dk. */
    internal fun sunMinutes(latitude: Double, longitude: Double, date: LocalDate, offsetHours: Double): Pair<Int, Int> {
        val n = date.dayOfYear
        val declination = Math.toRadians(23.44) * kotlin.math.sin(2 * PI * (284 + n) / 365.0)
        val phi = Math.toRadians(latitude)
        val cosOmega = (-tan(phi) * tan(declination)).coerceIn(-1.0, 1.0)
        val hours = Math.toDegrees(acos(cosOmega)) / 15.0
        // Zaman denklemi: yıl içinde ±16 dakika.
        val b = 2 * PI * (n - 81) / 364.0
        val eqMinutes = 9.87 * kotlin.math.sin(2 * b) - 7.53 * cos(b) - 1.5 * kotlin.math.sin(b)
        val noon = 12.0 - longitude / 15.0 + offsetHours - eqMinutes / 60.0
        return ((noon - hours) * 60).roundToInt() to ((noon + hours) * 60).roundToInt()
    }

    private fun iso(date: LocalDate, minutes: Int) = date.atStartOfDay().plusMinutes(minutes.toLong()).toString().let { if (it.length == 10) "${it}T00:00" else it }
    private fun isoTime(time: LocalDateTime) = time.withSecond(0).withNano(0).toString().let { if (it.length == 13) "$it:00" else it }

    // --- Tahmin ------------------------------------------------------------------------------------

    /** Open-Meteo'nun cevabı biçiminde örnek tahmin ([now]: yerin yerel şu anki saati). */
    fun forecast(place: DemoPlace, sky: Sky, now: LocalDateTime): ForecastResponseDto {
        val zone = ZoneId.of(place.zone)
        val offsetSeconds = zone.rules.getOffset(now.atZone(zone).toInstant()).totalSeconds
        val offsetHours = offsetSeconds / 3600.0
        val today = now.toLocalDate()
        val days = (0 until 7).map { today.plusDays(it.toLong()) }
        val sun = days.map { sunMinutes(place.latitude, place.longitude, it, offsetHours) }

        fun dayIndex(time: LocalDateTime) = ChronoDays.between(today, time.toLocalDate()).coerceIn(0, 6)
        fun isDay(time: LocalDateTime): Boolean {
            val (rise, set) = sun[dayIndex(time)]
            val m = time.hour * 60 + time.minute
            return m in rise until set
        }

        fun codeAt(time: LocalDateTime): Int = if (time.toLocalDate() == today) sky.code else sky.week[dayIndex(time)]
        fun isWet(code: Int) = code in WET
        fun isSnow(code: Int) = code in SNOW

        // Günün sıcaklık eğrisi: en düşük gün doğumundan hemen sonra, en yüksek 15:00 civarı.
        fun temp(time: LocalDateTime): Double {
            val i = dayIndex(time)
            val hi = sky.weekMax[i]
            val lo = sky.weekMin[i]
            val h = time.hour + time.minute / 60.0
            val wave = (cos(2 * PI * (h - 15.0) / 24.0) + 1) / 2
            return lo + (hi - lo) * wave
        }

        fun rainMm(code: Int): Double = when (code) {
            51, 53, 55, 56, 57 -> 0.3
            61, 66 -> 0.8
            63, 67, 80, 81 -> 1.8
            65, 82 -> 5.5
            95, 96, 99 -> 3.5
            71 -> 0.4
            73, 77, 85, 86 -> 1.2
            75 -> 2.5
            else -> 0.0
        }

        fun probability(code: Int): Int = when {
            isWet(code) || isSnow(code) -> 85
            code == 3 -> 20
            code == 2 -> 10
            else -> 0
        }

        fun uv(time: LocalDateTime, code: Int): Double {
            val i = dayIndex(time)
            if (!isDay(time)) return 0.0
            val (rise, set) = sun[i]
            val m = time.hour * 60 + time.minute
            val x = (m - rise).toDouble() / (set - rise)
            val bell = kotlin.math.sin(PI * x).coerceAtLeast(0.0)
            val cloudFactor = when { code == 3 || isWet(code) || isSnow(code) -> 0.25; code == 2 -> 0.7; code == 45 -> 0.3; else -> 1.0 }
            return ((sky.uvMax * bell * bell * cloudFactor) * 10).roundToInt() / 10.0
        }

        fun apparent(t: Double): Double {
            val wind = sky.windKmh
            return when {
                t >= 27 -> t + (sky.humidity - 40) / 12.0
                t <= 10 -> t - wind * 0.12
                else -> t - wind * 0.04
            }.let { (it * 10).roundToInt() / 10.0 }
        }

        fun dew(t: Double) = ((t - (100 - sky.humidity) / 5.0) * 10).roundToInt() / 10.0

        val start = today.atStartOfDay()
        val hours = (0 until 24 * 7).map { start.plusHours(it.toLong()) }
        val hourlyTime = hours.map { isoTime(it) }
        val codes = hours.map { codeAt(it) }
        val mm = codes.map { rainMm(it) }

        val hourly = HourlyDto(
            time = hourlyTime,
            temperature = hours.map { (temp(it) * 10).roundToInt() / 10.0 },
            weatherCode = codes,
            precipitationProbability = codes.map { probability(it) },
            isDay = hours.map { if (isDay(it)) 1 else 0 },
            cloudLow = hours.map { if (it.toLocalDate() == today) sky.cloudLow else 30 },
            cloudMid = hours.map { if (it.toLocalDate() == today) sky.cloudMid else 30 },
            cloudHigh = hours.map { if (it.toLocalDate() == today) sky.cloudHigh else 30 },
            sunshineDuration = hours.mapIndexed { i, t -> if (isDay(t) && codes[i] in setOf(0, 1)) 3600.0 else if (isDay(t) && codes[i] == 2) 1800.0 else 0.0 },
            precipitation = mm,
            showers = hours.map { 0.0 },
            snowfall = hours.indices.map { i -> if (isSnow(codes[i])) mm[i] else 0.0 },
            apparentTemperature = hours.map { apparent(temp(it)) },
            humidity = hours.map { sky.humidity.toDouble() },
            dewPoint = hours.map { dew(temp(it)) },
            windSpeed = hours.map { sky.windKmh },
            windGust = hours.map { sky.windKmh * 1.6 },
            windDirection = hours.map { sky.windDir.toDouble() },
            uvIndex = hours.indices.map { uv(hours[it], codes[it]) },
            visibility = hours.map { sky.visibilityM },
            pressure = hours.map { sky.pressure },
            cloudCover = hours.map { sky.cloud.toDouble() },
        )

        val daily = DailyDto(
            time = days.map { it.toString() },
            weatherCode = sky.week,
            maxTemperature = sky.weekMax,
            minTemperature = sky.weekMin,
            precipitationProbability = sky.week.map { probability(it) },
            sunrise = days.indices.map { iso(days[it], sun[it].first) },
            sunset = days.indices.map { iso(days[it], sun[it].second) },
            uvIndexMax = sky.week.map { c -> ((sky.uvMax * (if (c == 3 || isWet(c) || isSnow(c)) 0.3 else 1.0)) * 10).roundToInt() / 10.0 },
            sunshineDuration = sky.week.mapIndexed { i, c -> (sun[i].second - sun[i].first) * 60.0 * (if (c in setOf(0, 1)) 0.9 else if (c == 2) 0.5 else 0.05) },
            daylightDuration = sun.map { (it.second - it.first) * 60.0 },
            precipitationSum = sky.week.map { rainMm(it) * (if (isWet(it) || isSnow(it)) 8 else 0) },
            windSpeedMax = sky.week.map { sky.windKmh * 1.2 },
            windGustMax = sky.week.map { sky.windKmh * 1.8 },
            apparentMax = sky.weekMax.map { apparent(it) },
            apparentMin = sky.weekMin.map { apparent(it) },
            humidityMean = sky.week.map { sky.humidity.toDouble() },
            pressureMean = sky.week.map { sky.pressure },
            cloudMean = sky.week.map { sky.cloud.toDouble() },
            visibilityMean = sky.week.map { sky.visibilityM },
            windDirection = sky.week.map { sky.windDir.toDouble() },
        )

        // Şu anki saat: 15 dakikaya yuvarlanır (Open-Meteo'nun "current" zamanı).
        val current = now.withMinute(now.minute / 15 * 15).withSecond(0).withNano(0)
        val t = (temp(now) * 10).roundToInt() / 10.0
        val code = sky.code
        val wetNow = isWet(code)
        val snowNow = isSnow(code)
        val currentDto = CurrentDto(
            time = isoTime(current),
            temperature = t,
            apparentTemperature = apparent(t),
            humidity = sky.humidity,
            dewPoint = dew(t),
            windSpeed = sky.windKmh,
            windDirection = sky.windDir,
            weatherCode = code,
            isDay = if (isDay(now)) 1 else 0,
            pressure = sky.pressure,
            uvIndex = uv(now, code),
            visibility = sky.visibilityM,
            cloudCover = sky.cloud,
            cloudLow = sky.cloudLow,
            cloudMid = sky.cloudMid,
            cloudHigh = sky.cloudHigh,
            precipitation = if (wetNow || snowNow) rainMm(code) / 4 else 0.0,
            rain = if (wetNow) rainMm(code) / 4 else 0.0,
            showers = 0.0,
            snowfall = if (snowNow) rainMm(code) / 4 else 0.0,
        )

        // 15 dakikalık yağış: dünden yarına, her değer bir önceki 15 dakikanın toplamı.
        val slices = generateSequence(start) { it.plusMinutes(15) }.take(4 * 24 * 3).toList()
        val minutely = Minutely15Dto(
            time = slices.map { isoTime(it) },
            precipitation = slices.mapIndexed { i, time -> val c = codeAt(time); if (isWet(c) || isSnow(c)) ((rainMm(c) / 4) * (0.55 + 0.45 * kotlin.math.sin(i * 0.8 + 1.0))).coerceAtLeast(0.05) else 0.0 },
        )
        return ForecastResponseDto(utcOffsetSeconds = offsetSeconds, elevation = 40.0, current = currentDto, hourly = hourly, daily = daily, minutely15 = minutely)
    }

    private object ChronoDays {
        fun between(a: LocalDate, b: LocalDate): Int = (b.toEpochDay() - a.toEpochDay()).toInt()
    }

    /** CAMS ölçeğinde sade bir hava kalitesi: [aqi] düzeyinde, gün içinde hafif dalgalı. */
    private fun air(now: LocalDateTime, aqi: Int): AirQuality {
        val start = now.toLocalDate().atStartOfDay()
        val hours = (0 until 72).map { i ->
            val time = start.plusHours(i.toLong())
            val wave = (kotlin.math.sin(2 * PI * (time.hour - 6) / 24.0) * 0.18 * aqi).roundToInt()
            val value = max(5, aqi + wave)
            AirHour(
                time = isoTime(time),
                aqi = value,
                pm25 = value * 0.45,
                pm10 = value * 0.8,
                ozone = 40.0 + value * 0.9,
                no2 = value * 0.7,
                so2 = 3.0 + value * 0.05,
                co = 150.0 + value * 4.0,
            )
        }
        return AirQuality(hours)
    }


    private val responseJson = Json { encodeDefaults = true; explicitNulls = false }

    /**
     * Örnek veri açıkken Open-Meteo tahmin isteğine verilen cevap ([flag]: "yer=hava" satırları, örn. "besiktas=rain").
     * İstek başka bir adrese ya da tanınmayan bir yere aitse null (istek başarısız olur).
     */
    fun respond(url: okhttp3.HttpUrl, flag: String): String? {
        if (!url.host.endsWith("open-meteo.com") || !url.encodedPath.endsWith("/v1/forecast") || url.queryParameter("start_date") != null) return null
        val wanted = flag.lines().mapNotNull { line -> line.trim().split("=").takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val lats = url.queryParameter("latitude")?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: return null
        val lons = url.queryParameter("longitude")?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: return null
        val now = Instant.now()
        val bodies = lats.indices.map { i ->
            val place = PLACES.values.minByOrNull { max(kotlin.math.abs(it.latitude - lats[i]), kotlin.math.abs(it.longitude - lons[i])) } ?: return null
            val sky = SKIES[wanted[place.key] ?: wanted.values.firstOrNull() ?: "clear"] ?: return null
            val local = LocalDateTime.ofInstant(now, ZoneId.of(place.zone))
            responseJson.encodeToString(ForecastResponseDto.serializer(), forecast(place, sky, local))
        }
        return if (bodies.size == 1) bodies.single() else bodies.joinToString(",", "[", "]")
    }

    // --- Yazma ----------------------------------------------------------------------------------------

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DemoEntryPoint {
        fun database(): WeatherDatabase
        fun json(): Json
        fun extras(): ExtrasStore
    }

    /**
     * Önceki bütün yer ve hava verisini siler, [entries]'i yazar. Telefonun şu anki saati her yerin yerel saatine
     * çevrilir; bu yüzden önce telefonun tarihi ve saati ayarlanmalıdır.
     */
    suspend fun install(context: Context, entries: List<Entry>, instant: Instant = Instant.now()) = withContext(Dispatchers.IO) {
        val point = EntryPointAccessors.fromApplication(context.applicationContext, DemoEntryPoint::class.java)
        val db = point.database()
        val json = point.json()
        db.clearAllTables()
        var order = 0
        for (entry in entries) {
            val zone = ZoneId.of(entry.place.zone)
            val local = LocalDateTime.ofInstant(instant, zone)
            val dto = forecast(entry.place, entry.sky, local)
            val id = when (entry.role) {
                Role.Device -> WeatherCacheEntity.DEVICE_PLACE_ID
                else -> db.placeDao().insert(
                    PlaceEntity(name = entry.place.name, region = entry.place.region, latitude = entry.place.latitude, longitude = entry.place.longitude, sortOrder = order++, isHome = entry.role == Role.Home),
                )
            }
            android.util.Log.i("DemoWeather", "id=$id ${entry.place.key} ${entry.role}")
            val at = entry.deviceAt ?: entry.place
            db.weatherCacheDao().upsertAll(
                listOf(
                    WeatherCacheEntity(
                        id = id,
                        cityName = if (entry.role == Role.Device) entry.deviceName else entry.place.name,
                        latitude = if (entry.role == Role.Device) at.latitude else entry.place.latitude,
                        longitude = if (entry.role == Role.Device) at.longitude else entry.place.longitude,
                        isCurrentLocation = entry.role == Role.Device,
                        forecastJson = json.encodeToString(ForecastResponseDto.serializer(), dto),
                        fetchedAtMillis = System.currentTimeMillis(),
                        accuracyMeters = if (entry.role == Role.Device) 25f else null,
                        regionName = null,
                    ),
                ),
            )
            val alerts = if (entry.yellowAlert) {
                listOf(
                    WeatherAlert(
                        id = "demo-${entry.place.key}-1-yellow",
                        level = AlertLevel.Yellow,
                        events = listOf(AlertEvent.Thunderstorm),
                        text = "Thunderstorms are expected in the evening.",
                        begin = local.withHour(17).withMinute(0).withSecond(0).withNano(0).toString().padEnd(16, '0'),
                        end = local.withHour(23).withMinute(59).withSecond(0).withNano(0).toString().padEnd(16, '0'),
                        source = "MGM",
                    ),
                )
            } else {
                emptyList()
            }
            val aqi = entry.aqi
            if (aqi != null || alerts.isNotEmpty()) {
                point.extras().save(
                    id,
                    PlaceExtras(
                        queryLatitude = entry.place.latitude,
                        queryLongitude = entry.place.longitude,
                        alerts = alerts,
                        alertsFetchedAt = System.currentTimeMillis(),
                        airQuality = aqi?.let { air(local, it) },
                        airFetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

/** adb ile örnek veriyi yazar (bkz. [DemoWeather]). */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val place = DemoWeather.place(intent.getStringExtra("place") ?: "besiktas")
        val sky = DemoWeather.SKIES.getValue(intent.getStringExtra("sky") ?: "clear")
        val role = when (intent.getStringExtra("role")) {
            "home" -> DemoWeather.Role.Home
            "saved" -> DemoWeather.Role.Saved
            else -> DemoWeather.Role.Device
        }
        val aqi = if (intent.hasExtra("air")) intent.getIntExtra("air", 28) else 28
        val alert = intent.getStringExtra("alert") == "yellow"
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // "Bulunduğum yer" sayfası da aynı örnek veriyle dolar (ilk sayfa); görsel için kayıtlı yer sayfası açılır
                // ("open_place" ile), böylece başlıkta konumdan bulunan mahalle değil yerin kendi adı (ilçe) yazar.
                val entries = if (role == DemoWeather.Role.Device) listOf(DemoWeather.Entry(place, sky, role, aqi, alert)) else
                    listOf(DemoWeather.Entry(place, sky, DemoWeather.Role.Device, aqi, alert), DemoWeather.Entry(place, sky, role, aqi, alert))
                DemoWeather.install(context, entries)
                android.util.Log.i("DemoWeather", "yazıldı: ${place.key} ${sky.key} $role")
            } catch (error: Throwable) {
                android.util.Log.e("DemoWeather", "yazılamadı", error)
            } finally {
                pending.finish()
            }
        }
    }
}
