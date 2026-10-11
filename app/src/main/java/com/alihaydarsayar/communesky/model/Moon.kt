package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Ayın sekiz evresi. */
enum class MoonPhase { New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent }

/** Bir günün ay bilgisi (30 günlük şerit için): o günün öğle saatindeki evre ve aydınlanma. */
data class MoonDay(val date: LocalDate, val phase: MoonPhase, val illumination: Double, val waxing: Boolean)

/** Bir yer ve an için ayın durumu. Saatler yerin yerel saatidir. */
data class MoonInfo(
    val phase: MoonPhase,
    /** Aydınlık görünen kısım (0–1). */
    val illumination: Double,
    /** Büyüyor mu (yeni aydan dolunaya)? */
    val waxing: Boolean,
    /** Bugünkü ay doğuşu ve batışı; o gün ay doğmuyor ya da batmıyorsa null. */
    val rise: LocalDateTime?,
    val set: LocalDateTime?,
    val nextFullMoon: LocalDateTime,
    val nextNewMoon: LocalDateTime,
)

/**
 * Ay hesapları: tamamen telefonda, internet gerekmez. Formüller Jean Meeus'un "Astronomical Algorithms"
 * kitabındandır: yeni ay ve dolunay anları (bölüm 49, birkaç dakika doğrulukla), aydınlanma (bölüm 48) ve ayın
 * gökyüzündeki yeri (bölüm 47'nin ana terimleri; doğuş ve batış birkaç dakika doğrulukla). Saf hesap; bilinen
 * tarihlerle test edilir.
 */
object Moon {
    /** Bir kavuşum ayı (yeni aydan yeni aya), ortalama gün. */
    const val SYNODIC_DAYS = 29.530588861

    private const val J2000 = 2451545.0

    /** Dünya zamanı ile atom zamanı arasındaki fark (ΔT), saniye; 2020'lerde yaklaşık 70 sn. */
    private const val DELTA_T_SECONDS = 70.0

    /** Ayın üst kenarı ufukta göründüğü andaki merkez yüksekliği (derece): kırılma ve paralaks dahil. */
    private const val HORIZON = 0.125

    private fun rad(degrees: Double) = degrees * PI / 180
    private fun sinD(degrees: Double) = sin(rad(degrees))
    private fun cosD(degrees: Double) = cos(rad(degrees))
    private fun norm(degrees: Double) = ((degrees % 360) + 360) % 360

    fun julianDay(at: Instant): Double = at.toEpochMilli() / 86_400_000.0 + 2440587.5

    private fun instant(julianDay: Double): Instant = Instant.ofEpochMilli(Math.round((julianDay - 2440587.5) * 86_400_000.0))

    /** [at] anına en yakın kavuşum numarası (2000 yılının ilk yeni ayından beri geçen ay sayısı). */
    private fun lunation(at: Instant): Double = (julianDay(at) - 2451550.09766) / SYNODIC_DAYS

    /**
     * [k] numaralı evrenin anı: tam sayı yeni ay, buçuklu sayı (k + 0,5) dolunay. Meeus bölüm 49.
     */
    fun phaseTime(k: Double): Instant {
        val t = k / 1236.85
        val full = (k - floor(k)) > 0.25
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = 2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t
        val mp = 201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t
        val f = 160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t
        val omega = 124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t
        var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t - 0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        // Yeni ay ve dolunayda ilk yedi katsayı biraz farklıdır; kalan terimler ortaktır.
        val c = if (full) {
            doubleArrayOf(-0.40614, 0.17302, 0.01614, 0.01043, 0.00734, -0.00515, 0.00209)
        } else {
            doubleArrayOf(-0.40720, 0.17241, 0.01608, 0.01039, 0.00739, -0.00514, 0.00208)
        }
        jde += c[0] * sinD(mp) +
            c[1] * e * sinD(m) +
            c[2] * sinD(2 * mp) +
            c[3] * sinD(2 * f) +
            c[4] * e * sinD(mp - m) +
            c[5] * e * sinD(mp + m) +
            c[6] * e * e * sinD(2 * m) +
            -0.00111 * sinD(mp - 2 * f) +
            -0.00057 * sinD(mp + 2 * f) +
            0.00056 * e * sinD(2 * mp + m) +
            -0.00042 * sinD(3 * mp) +
            0.00042 * e * sinD(m + 2 * f) +
            0.00038 * e * sinD(m - 2 * f) +
            -0.00024 * e * sinD(2 * mp - m) +
            -0.00017 * sinD(omega) +
            -0.00007 * sinD(mp + 2 * m) +
            0.00004 * sinD(2 * mp - 2 * f) +
            0.00004 * sinD(3 * m) +
            0.00003 * sinD(mp + m - 2 * f) +
            0.00003 * sinD(2 * mp + 2 * f) +
            -0.00003 * sinD(mp + m + 2 * f) +
            0.00003 * sinD(mp - m + 2 * f) +
            -0.00002 * sinD(mp - m - 2 * f) +
            -0.00002 * sinD(3 * mp + m) +
            0.00002 * sinD(4 * mp)
        // Gezegenlerin küçük etkileri.
        jde += 0.000325 * sinD(299.77 + 0.107408 * k - 0.009173 * t * t) +
            0.000165 * sinD(251.88 + 0.016321 * k) +
            0.000164 * sinD(251.83 + 26.651886 * k) +
            0.000126 * sinD(349.42 + 36.412478 * k) +
            0.000110 * sinD(84.66 + 18.206239 * k) +
            0.000062 * sinD(141.74 + 53.303771 * k) +
            0.000060 * sinD(207.14 + 2.453732 * k) +
            0.000056 * sinD(154.84 + 7.306860 * k) +
            0.000047 * sinD(34.52 + 27.261239 * k) +
            0.000042 * sinD(207.19 + 0.121824 * k) +
            0.000040 * sinD(291.34 + 1.844379 * k) +
            0.000037 * sinD(161.72 + 24.198154 * k) +
            0.000035 * sinD(239.56 + 25.513099 * k) +
            0.000023 * sinD(331.55 + 3.592518 * k)
        return instant(jde - DELTA_T_SECONDS / 86_400.0)
    }

    /** [at] anından sonraki ilk yeni ay. */
    fun nextNewMoon(at: Instant): Instant = next(at, offset = 0.0)

    /** [at] anından sonraki ilk dolunay. */
    fun nextFullMoon(at: Instant): Instant = next(at, offset = 0.5)

    /** [at] anından önceki (ya da o andaki) son yeni ay. */
    fun previousNewMoon(at: Instant): Instant {
        var k = floor(lunation(at)) + 1
        while (true) {
            val time = phaseTime(k)
            if (!time.isAfter(at)) return time
            k -= 1
        }
    }

    private fun next(at: Instant, offset: Double): Instant {
        var k = floor(lunation(at)) - 1 + offset
        while (true) {
            val time = phaseTime(k)
            if (time.isAfter(at)) return time
            k += 1
        }
    }

    /** Evre: 0 yeni ay, 0,5 dolunay, 1'e yaklaşırken yine yeni ay; iki yeni ay arasındaki zamanın oranı. */
    fun phase(at: Instant): Double {
        val previous = previousNewMoon(at)
        val next = nextNewMoon(at)
        val span = Duration.between(previous, next).toMillis().toDouble()
        return (Duration.between(previous, at).toMillis() / span).coerceIn(0.0, 1.0)
    }

    /** Ayın aydınlık görünen kısmı (0–1). Meeus bölüm 48. */
    fun illumination(at: Instant): Double {
        val t = (julianDay(at) - J2000) / 36525
        val d = 297.8501921 + 445267.1114034 * t
        val m = 357.5291092 + 35999.0502909 * t
        val mp = 134.9633964 + 477198.8675055 * t
        val angle = 180 - d - 6.289 * sinD(mp) + 2.100 * sinD(m) - 1.274 * sinD(2 * d - mp) - 0.658 * sinD(2 * d) -
            0.214 * sinD(2 * mp) - 0.110 * sinD(d)
        return ((1 + cosD(angle)) / 2).coerceIn(0.0, 1.0)
    }

    /** Evrenin adı: yeni ay ve dolunay yaklaşık bir gün, dördünler yaklaşık bir buçuk gün sürer sayılır. */
    fun phaseName(phase: Double): MoonPhase = when {
        phase < 0.03 || phase >= 0.97 -> MoonPhase.New
        phase < 0.22 -> MoonPhase.WaxingCrescent
        phase < 0.28 -> MoonPhase.FirstQuarter
        phase < 0.47 -> MoonPhase.WaxingGibbous
        phase < 0.53 -> MoonPhase.Full
        phase < 0.72 -> MoonPhase.WaningGibbous
        phase < 0.78 -> MoonPhase.LastQuarter
        else -> MoonPhase.WaningCrescent
    }

    /** Ayın [at] anında, verilen yerde ufuktan yüksekliği (derece). Meeus bölüm 47'nin ana terimleri. */
    fun altitude(at: Instant, latitude: Double, longitude: Double): Double {
        val jd = julianDay(at)
        val t = (jd - J2000) / 36525
        val l = 218.3164477 + 481267.88123421 * t
        val d = 297.8501921 + 445267.1114034 * t
        val m = 357.5291092 + 35999.0502909 * t
        val mp = 134.9633964 + 477198.8675055 * t
        val f = 93.2720950 + 483202.0175233 * t
        val lon = l + 6.288774 * sinD(mp) + 1.274027 * sinD(2 * d - mp) + 0.658314 * sinD(2 * d) + 0.213618 * sinD(2 * mp) -
            0.185116 * sinD(m) - 0.114332 * sinD(2 * f) + 0.058793 * sinD(2 * d - 2 * mp) + 0.057066 * sinD(2 * d - m - mp) +
            0.053322 * sinD(2 * d + mp) + 0.045758 * sinD(2 * d - m) - 0.040923 * sinD(m - mp) - 0.034720 * sinD(d) -
            0.030383 * sinD(m + mp) + 0.015327 * sinD(2 * d - 2 * f) - 0.012528 * sinD(mp + 2 * f) + 0.010980 * sinD(mp - 2 * f)
        val lat = 5.128122 * sinD(f) + 0.280602 * sinD(mp + f) + 0.277693 * sinD(mp - f) + 0.173237 * sinD(2 * d - f) +
            0.055413 * sinD(2 * d - mp + f) + 0.046271 * sinD(2 * d - mp - f) + 0.032573 * sinD(2 * d + f) + 0.017198 * sinD(2 * mp + f)
        val obliquity = 23.4393 - 0.0130 * t
        // Ekliptik koordinatlardan ekvator koordinatlarına.
        val rightAscension = atan2(sinD(lon) * cosD(obliquity) - Math.tan(rad(lat)) * sinD(obliquity), cosD(lon))
        val declination = asin(sinD(lat) * cosD(obliquity) + cosD(lat) * sinD(obliquity) * sinD(lon))
        // Yerel yıldız zamanı ve saat açısı.
        val sidereal = norm(280.46061837 + 360.98564736629 * (jd - J2000) + longitude)
        val hourAngle = rad(sidereal) - rightAscension
        val phi = rad(latitude)
        return asin(sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle)) * 180 / PI
    }

    /**
     * [date] günü içinde (yerel saatle) ayın ilk doğuşu ve ilk batışı. Gün on dakikalık adımlarla taranır, ufuk
     * geçişi iki adım arasında oranlanır. Ay o gün doğmuyor ya da batmıyorsa ilgili değer null.
     */
    fun riseAndSet(date: LocalDate, latitude: Double, longitude: Double, utcOffsetSeconds: Int): Pair<LocalDateTime?, LocalDateTime?> {
        val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        val start = date.atStartOfDay().toInstant(offset)
        val step = Duration.ofMinutes(STEP_MINUTES)
        var rise: LocalDateTime? = null
        var set: LocalDateTime? = null
        var previous = altitude(start, latitude, longitude) - HORIZON
        for (i in 1..(24 * 60 / STEP_MINUTES).toInt()) {
            val time = start.plus(step.multipliedBy(i.toLong()))
            val current = altitude(time, latitude, longitude) - HORIZON
            if ((previous < 0) != (current < 0)) {
                val share = previous / (previous - current)
                val crossing = time.minus(step).plusMillis((share * step.toMillis()).toLong())
                val local = LocalDateTime.ofInstant(crossing, offset)
                if (previous < 0 && rise == null) rise = local
                if (previous >= 0 && set == null) set = local
            }
            previous = current
        }
        return rise to set
    }

    private const val STEP_MINUTES = 10L

    /** Bir yer ve an için ayın bütün bilgisi. */
    fun info(now: Instant, latitude: Double, longitude: Double, utcOffsetSeconds: Int): MoonInfo {
        val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        val phase = phase(now)
        val (rise, set) = riseAndSet(LocalDateTime.ofInstant(now, offset).toLocalDate(), latitude, longitude, utcOffsetSeconds)
        return MoonInfo(
            phase = phaseName(phase),
            illumination = illumination(now),
            waxing = phase < 0.5,
            rise = rise,
            set = set,
            nextFullMoon = LocalDateTime.ofInstant(nextFullMoon(now), offset),
            nextNewMoon = LocalDateTime.ofInstant(nextNewMoon(now), offset),
        )
    }

    /** [from] gününden başlayarak [count] günün ayı (her gün yerel öğle saatine göre). */
    fun days(from: LocalDate, count: Int, utcOffsetSeconds: Int): List<MoonDay> {
        val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        return (0 until count).map { i ->
            val date = from.plusDays(i.toLong())
            val noon = date.atTime(12, 0).toInstant(offset)
            val phase = phase(noon)
            MoonDay(date, phaseName(phase), illumination(noon), waxing = phase < 0.5)
        }
    }
}
