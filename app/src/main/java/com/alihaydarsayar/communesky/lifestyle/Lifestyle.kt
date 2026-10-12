package com.alihaydarsayar.communesky.lifestyle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.Moon
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.today.Strings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Yaşam önerileri: şemsiye, güneş kremi, çamaşır, koşu, araba yıkama, yıldız gözlemi, soğuk algınlığı.
 * Hepsi telefonda, uygulamanın zaten aldığı hava verisinden hesaplanır; hava verisi olduğu sürece her zaman
 * bir cevap verir. Her öneri kendi dosyasında, saf bir fonksiyondur ve test edilir.
 *
 * **Yeni öneri eklemek:** bu pakete [LifestyleTip] uygulayan bir nesne ekle ve [Lifestyle.all] listesine yaz.
 */

/** Önerinin durumu: iyi / idare eder / uygun değil. */
enum class TipStatus { Good, Fair, Poor }

/**
 * Bir önerinin bugünkü sonucu. [text]: tek satırlık cümle (kaynak kimliği) ve [args] yerine konacak değerler
 * (sayılar ya da saatler; saatleri ekran, kullanıcının saat biçimiyle yazar).
 * [attention]: bugün ne kadar dikkat çekmeli (0–3); ana ekrandaki kartta en yüksek iki öneri gösterilir.
 */
data class TipResult(
    val status: TipStatus,
    @param:StringRes val text: Int,
    val args: List<Any> = emptyList(),
    val attention: Int = 0,
)

interface LifestyleTip {
    val id: String

    @get:StringRes
    val title: Int

    @get:DrawableRes
    val icon: Int

    fun evaluate(day: TodayWeather): TipResult
}

/** Önerilerin baktığı veri: bugünün saatleri, önümüzdeki 48 saat, bu gece, ay ışığı. */
data class TodayWeather(
    val now: LocalDateTime,
    /** Bugünün bütün saatleri (00–23). */
    val hours: List<HourlyForecast>,
    /** Şimdiden itibaren 48 saat. */
    val next48: List<HourlyForecast>,
    /** Bu gece: bugün 21:00 – yarın 04:00. */
    val tonight: List<HourlyForecast>,
    val today: DailyForecast?,
    /** Bu gece ayın aydınlık yüzdesi (0–1). */
    val moonIllumination: Double,
    /** Hava kalitesi kötü mü (bölüm C); bilinmiyorsa false. */
    val airQualityPoor: Boolean = false,
    /** Şu an ekranda gösterilen hava (istasyon ölçümüyle düzeltilmiş olabilir); bilinmiyorsa null. */
    val current: WeatherCondition? = null,
) {
    /** Şu an yağıyor mu (yağmur, sağanak, gök gürültüsü ya da kar)? */
    val precipitatingNow: Boolean get() = current != null && (current.isWet || current == WeatherCondition.Snow)

    val thunderNow: Boolean get() = current == WeatherCondition.Thunderstorm

    /** Şu andan gün sonuna kadar, [from]–[until] saatleri arasındaki saatler. */
    fun remaining(from: Int, until: Int): List<HourlyForecast> =
        hours.filter { it.time.hour in from until until && !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }

    companion object {
        fun from(forecast: Forecast, now: LocalDateTime = forecast.localNow(), airQualityPoor: Boolean = false): TodayWeather {
            val date = now.toLocalDate()
            val nightStart = date.atTime(21, 0)
            val offset = ZoneOffset.ofTotalSeconds(forecast.utcOffsetSeconds)
            return TodayWeather(
                now = now,
                hours = forecast.hourly.filter { it.time.toLocalDate() == date },
                next48 = forecast.upcomingHours(now, count = 48),
                tonight = forecast.hourly.filter { !it.time.isBefore(nightStart) && it.time.isBefore(nightStart.plusHours(7)) },
                today = forecast.today(now),
                moonIllumination = Moon.illumination(date.plusDays(1).atStartOfDay().toInstant(offset)),
                airQualityPoor = airQualityPoor,
                current = forecast.current.condition,
            )
        }
    }
}

/** Cümleye girecek sıcaklık farkı (°C); ekran, kullanıcının birimine çevirir. */
data class DegreeDifference(val celsius: Double)

/**
 * Önerinin cümlesi. İçindeki saatleri [time] kullanıcının saat biçimiyle, sıcaklık farklarını [difference]
 * kullanıcının birimiyle yazar.
 */
fun TipResult.sentence(strings: Strings, time: (LocalTime) -> String, difference: (Double) -> Int = { Math.round(it).toInt() }): String =
    strings.get(
        text,
        *args.map {
            when (it) {
                is LocalTime -> time(it)
                is DegreeDifference -> difference(it.celsius)
                else -> it
            }
        }.toTypedArray(),
    )

@get:StringRes
val TipStatus.label: Int
    get() = when (this) {
        TipStatus.Good -> R.string.tip_status_good
        TipStatus.Fair -> R.string.tip_status_fair
        TipStatus.Poor -> R.string.tip_status_poor
    }

object Lifestyle {
    /** Bütün öneriler, ekrandaki sırasıyla. */
    val all: List<LifestyleTip> = listOf(UmbrellaTip, SunscreenTip, LaundryTip, RunTip, CarWashTip, StargazingTip, ColdRiskTip)

    fun evaluate(day: TodayWeather): List<Pair<LifestyleTip, TipResult>> = all.map { it to it.evaluate(day) }

    /** Ana ekrandaki kart için en önemli iki öneri: önce dikkat isteyenler, eşitlikte listedeki sıra. */
    fun top(results: List<Pair<LifestyleTip, TipResult>>, count: Int = 2): List<Pair<LifestyleTip, TipResult>> =
        results.sortedByDescending { it.second.attention }.take(count)

    /** Bir saatte "yağış var" sayılan miktar (mm). */
    const val WET_MM = HourlyForecast.WET_MM
}

/** Bu saatte yağış var mı: simge yağış gösteriyor ya da ölçülebilir miktar bekleniyor. */
internal fun HourlyForecast.isWet() = isPrecipitating
