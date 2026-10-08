package com.alihaydarsayar.communesky.data

import java.time.LocalDateTime
import kotlin.math.max

/**
 * Open-Meteo'nun hava kodu (0 açık … 3 kapalı) toplam bulut oranından hesaplanır ve ince, yüksek
 * bulutları (sirrus) da "kapalı" sayar. Oysa bu bulutlar güneşi neredeyse hiç kesmez: gökyüzü
 * %100 sirrusla kaplıyken güneş bütün saat parlayabilir. Bu yüzden 0–3 kodlarını, modelin
 * güneşlenme süresine ve bulut katmanlarına (alçak/orta/yüksek) bakarak yeniden hesaplıyoruz.
 *
 * Kural: düzeltme sadece gökyüzünü "açar", asla daha kapalı göstermez. Yağmur, kar, sis gibi
 * diğer kodlara dokunulmaz.
 */
object SkyCorrection {

    /**
     * Saatlik (veya anlık) kod. [sunshineSeconds] o saatte güneşin göründüğü süredir (0–3600).
     * Güneş alçaktayken (gün doğumu/batımına bir saatten yakın) ışık eşiği aşılmadığı için
     * güneşlenme süresi düşer; o saatlerde sadece bulut katmanlarına bakılır.
     */
    fun hourlyCode(
        code: Int,
        cloudLow: Int?,
        cloudMid: Int?,
        cloudHigh: Int?,
        sunshineSeconds: Double?,
        hourStart: LocalDateTime,
        sunrise: LocalDateTime?,
        sunset: LocalDateTime?,
    ): Int {
        if (code !in 0..3 || cloudLow == null || cloudMid == null || cloudHigh == null) return code
        val lowMid = max(cloudLow, cloudMid).toDouble()
        val sunHighEnough = sunshineSeconds != null && sunrise != null && sunset != null &&
            !hourStart.isBefore(sunrise.plusHours(1)) &&
            !hourStart.plusHours(1).isAfter(sunset.minusHours(1))
        val cloudiness = if (sunHighEnough) {
            val cloudyFraction = 1.0 - (sunshineSeconds!! / 3600.0).coerceIn(0.0, 1.0)
            0.55 * cloudyFraction * 100 + 0.30 * lowMid + 0.15 * cloudHigh
        } else {
            // Sirrus yarı ağırlıkla sayılır.
            max(lowMid, cloudHigh * 0.5)
        }
        val corrected = when {
            cloudiness < 15 -> 0
            cloudiness < 35 -> 1
            cloudiness < 70 -> 2
            else -> 3
        }
        return minOf(code, corrected)
    }

    /**
     * Günlük kod. Open-Meteo günün "en kötü" saatini seçer; sabah kısa süren bir sis ya da
     * öğlen geçen ince bulutlar bütün günü kapalı gösterebilir. Gün boyu güneşlenme oranına bakıyoruz.
     */
    fun dailyCode(code: Int, sunshineSeconds: Double?, daylightSeconds: Double?): Int {
        if (sunshineSeconds == null || daylightSeconds == null || daylightSeconds <= 0) return code
        val sunnyFraction = sunshineSeconds / daylightSeconds
        val isFog = code == 45 || code == 48
        if (code !in 0..3 && !(isFog && sunnyFraction >= 0.35)) return code
        val corrected = when {
            sunnyFraction >= 0.9 -> 1
            sunnyFraction >= 0.6 -> 2
            else -> 3
        }
        return if (isFog) corrected else minOf(code, corrected)
    }
}
