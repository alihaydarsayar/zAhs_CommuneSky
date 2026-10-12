package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bir yer için ekranda gösterdiğimiz tüm hava durumu verisi. Saatler o yerin yerel saatidir. */
data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val utcOffsetSeconds: Int,
    /** 15 dakikalık yağış dilimleri; eski önbellekte boş olabilir. */
    val minutely: List<PrecipitationSlice> = emptyList(),
    /** Modelin bu nokta için kullandığı rakım (m). */
    val elevation: Double? = null,
) {
    /** O yerde şu an saat kaç? (Telefonun saat diliminden bağımsız.) */
    fun localNow(now: Instant = Instant.now()): LocalDateTime =
        LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    /** Önbellek eskiyse geçmiş saatleri atlayıp şimdiden itibaren [count] saati döndürür. */
    fun upcomingHours(now: LocalDateTime, count: Int = 24): List<HourlyForecast> {
        val currentHour = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.filter { !it.time.isBefore(currentHour) }.take(count)
    }

    /** Bugünden başlayan günler (önbellek dünden kaldıysa dün atlanır). */
    fun upcomingDays(now: LocalDateTime): List<DailyForecast> =
        daily.filter { !it.date.isBefore(now.toLocalDate()) }

    fun today(now: LocalDateTime): DailyForecast? = daily.firstOrNull { it.date == now.toLocalDate() }

    /**
     * Önümüzdeki iki saatte yağış başlayacak mı ya da şu anki yağış dinecek mi?
     * Bir değişiklik yoksa veya veri yoksa null döner.
     */
    fun rainOutlook(now: LocalDateTime, observed: Boolean = false): RainOutlook? {
        val horizon = now.plus(OutlookHorizon)
        val upcoming = minutely.filter { it.end.isAfter(now) && it.start.isBefore(horizon) }
        if (upcoming.isEmpty()) return null
        val isSnow = current.condition == WeatherCondition.Snow || current.temperature <= 1.0
        val currentWet = current.condition.isWet || current.condition == WeatherCondition.Snow
        if (observed) {
            // Anlık durum istasyon ölçümünden geliyorsa modelin 15 dakikalık verisiyle çelişebilir.
            // Ölçüm yağış diyorsa "diniyor" demeyiz (model kuru diye yağmur dinmiş olmaz); ölçüm kuru
            // ama model şu an yağış diyorsa "birazdan başlıyor" demek de ölçümle çelişir.
            if (currentWet) return null
            val sliceNow = minutely.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
            if (sliceNow?.isWet == true) return null
        }
        return if (currentWet) {
            val dry = upcoming.firstOrNull { !it.isWet } ?: return null
            RainOutlook(RainOutlook.Kind.Stopping, minutesUntil(now, dry.start), isSnow)
        } else {
            val wet = upcoming.firstOrNull { it.isWet } ?: return null
            RainOutlook(RainOutlook.Kind.Starting, minutesUntil(now, wet.start), isSnow)
        }
    }

    private fun minutesUntil(now: LocalDateTime, time: LocalDateTime): Int {
        val minutes = Duration.between(now, time).toMinutes().coerceAtLeast(0)
        // 5 dakikaya yuvarlıyoruz; dakikası dakikasına bir kesinlik vaat etmek dürüst olmaz.
        return (((minutes + 4) / 5) * 5).toInt()
    }

    private companion object {
        val OutlookHorizon: Duration = Duration.ofHours(2)
    }
}

/** Bir 15 dakikalık dilim: [start]–[end] arasında düşen yağış. */
data class PrecipitationSlice(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val precipitationMm: Double,
    /** Ekranda yağış göstermeye değecek kadar mı? */
    val isWet: Boolean,
)

/** "Yağmur X dakika içinde başlıyor / diniyor" bilgisi. */
data class RainOutlook(val kind: Kind, val minutes: Int, val isSnow: Boolean) {
    enum class Kind { Starting, Stopping }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double?,
    val windSpeed: Double,
    val windDirection: Int?,
    val weatherCode: Int,
    val isDay: Boolean,
    val pressure: Double?,
    val uvIndex: Double?,
    /** Metre cinsinden. */
    val visibility: Double?,
    /** Bulut oranı (%): toplam ve katmanlar (alçak, orta, yüksek). */
    val cloudCover: Int? = null,
    val cloudLow: Int? = null,
    val cloudMid: Int? = null,
    val cloudHigh: Int? = null,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
    /**
     * Yağış yüzdesi gösterilsin mi? Hava durumu ulusal servisten geliyor ve yağış diyorsa, ana tahminin
     * düşük yüzdesi onunla çelişir; o zaman yüzde gösterilmez.
     */
    val showProbability: Boolean = true,
    // Saatlik ek değerler (1.5); eski önbellekte ya da kaynak vermediyse null.
    val apparentTemperature: Double? = null,
    val humidity: Int? = null,
    val dewPoint: Double? = null,
    /** km/sa. */
    val windSpeed: Double? = null,
    /** Hamle, km/sa. */
    val windGust: Double? = null,
    val windDirection: Int? = null,
    val uvIndex: Double? = null,
    /** Metre. */
    val visibility: Double? = null,
    /** hPa. */
    val pressure: Double? = null,
    /** %. */
    val cloudCover: Int? = null,
    /** Bu saat içinde düşen yağış (mm). */
    val precipitation: Double? = null,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)

    /** Ekrandaki simge yağış gösteriyor mu (yağmur, çisenti, sağanak, gök gürültüsü ya da kar)? */
    val showsPrecipitation: Boolean get() = condition.isWet || condition == WeatherCondition.Snow

    /**
     * Bu saatte yağış var sayılır mı? Simge yağış gösteriyorsa ya da ölçülebilir miktar bekleniyorsa evet.
     * Öneriler buna bakar: ekranda yağmur simgesi varken "şemsiye gerekmez" denmez.
     */
    val isPrecipitating: Boolean get() = showsPrecipitation || (precipitation ?: 0.0) >= WET_MM

    val isThunderstorm: Boolean get() = condition == WeatherCondition.Thunderstorm

    companion object {
        /** Bir saatte "yağış var" sayılan miktar (mm). */
        const val WET_MM = 0.2
    }
}

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
    /** Günlük toplam yağış (mm). */
    val precipitationSum: Double? = null,
    /** Günün en yüksek rüzgâr hızı ve hamlesi (km/sa). */
    val windSpeedMax: Double? = null,
    val windGustMax: Double? = null,
    // Günlük ortalamalar (1.5): ayrıntı sayfalarındaki 7 günlük özetler için; eski önbellekte null.
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val humidityMean: Double? = null,
    /** hPa. */
    val pressureMean: Double? = null,
    val cloudMean: Double? = null,
    /** Metre. */
    val visibilityMean: Double? = null,
    /** Günün hâkim rüzgâr yönü (derece). */
    val windDirection: Int? = null,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)

    /**
     * Yağış kutusunun cümlesi. Yüzde ve toplam miktar ayrı modellerden gelebilir: yüzde yüksekken ya da günün
     * simgesi yağış gösterirken miktar sıfır diye "yağış beklenmiyor" denmez.
     */
    val precipitationOutlook: PrecipitationOutlook
        get() = when {
            (precipitationSum ?: 0.0) >= PrecipitationOutlook.EXPECTED_MM -> PrecipitationOutlook.Expected
            precipitationProbability >= PrecipitationOutlook.POSSIBLE_CHANCE || condition.isWet || condition == WeatherCondition.Snow ->
                PrecipitationOutlook.Possible
            else -> PrecipitationOutlook.None
        }
}

/** Bugün yağış: miktar bekleniyor, yalnızca ihtimal var (miktar çok az) ya da beklenmiyor. */
enum class PrecipitationOutlook {
    Expected, Possible, None;

    companion object {
        const val EXPECTED_MM = 0.1
        const val POSSIBLE_CHANCE = 40
    }
}

/** Önbellekten okunan, ekrana hazır hava durumu: hangi yer, ne zaman alındı. */
data class WeatherSnapshot(
    val placeId: Long,
    val city: City,
    val isCurrentLocation: Boolean,
    val forecast: Forecast,
    val fetchedAt: Instant,
    /** Sadece "Bulunduğum yer" için: konumun hata payı (metre). */
    val accuracyMeters: Float? = null,
    /** İstasyon ölçümü kullanıldıysa nasıl kullanıldığı (anlık durumu belirledi ya da uyarı). */
    val observation: ObservationUse? = null,
    /** Tahminin kaynakları; eski önbellekte ya da ek veri yokken null. */
    val sources: ForecastSources? = null,
    /** Bu yer için geçerli meteorolojik uyarılar (süresi dolanlar hariç), en ağırı önce. */
    val alerts: List<WeatherAlert> = emptyList(),
    val normal: SeasonalNormal? = null,
    /** Hava kalitesi; veri yoksa ya da eskiyse null. */
    val airQuality: AirQuality? = null,
) {
    /** Şu anki hava kalitesi "kötü" ya da daha kötü mü (koşu önerisi buna bakar). */
    val airQualityPoor: Boolean get() = airQuality?.at(forecast.localNow())?.level?.isPoor == true
}

/** Yağış uyarısı; anlık durum istasyon ölçümünden geliyorsa çelişkiler gösterilmez. */
fun WeatherSnapshot.rainOutlook(now: LocalDateTime = forecast.localNow()): RainOutlook? =
    forecast.rainOutlook(now, observed = observation is ObservationUse.Override)
