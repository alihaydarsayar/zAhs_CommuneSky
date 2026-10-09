package com.alihaydarsayar.communesky.data.observation

/** Ölçülen hava durumu: WMO kodu (bilinmiyorsa null), yağış ve gök gürültüsü. */
data class ObservedWeather(val wmoCode: Int?, val isWet: Boolean, val hasThunder: Boolean)

/**
 * MGM'nin "hadise" kodlarını (sondurumlar.hadiseKodu) WMO hava kodlarına çevirir.
 * Kodlar MGM'nin web sitesinde kullanılan kısaltmalardır (A = Açık, GSY = Gök gürültülü
 * sağanak yağışlı…). Görsel karşılığı olmayan olaylar (rüzgârlı, sıcak, toz…) durumu değiştirmez.
 */
object MgmWeatherCodes {
    fun map(code: String?): ObservedWeather = when (code?.trim()?.uppercase()) {
        "A" -> ObservedWeather(0, isWet = false, hasThunder = false) // Açık
        "AB" -> ObservedWeather(1, isWet = false, hasThunder = false) // Az bulutlu
        "PB" -> ObservedWeather(2, isWet = false, hasThunder = false) // Parçalı bulutlu
        "CB" -> ObservedWeather(3, isWet = false, hasThunder = false) // Çok bulutlu
        "SIS" -> ObservedWeather(45, isWet = false, hasThunder = false) // Sisli
        "HY" -> ObservedWeather(61, isWet = true, hasThunder = false) // Hafif yağmurlu
        "Y" -> ObservedWeather(63, isWet = true, hasThunder = false) // Yağmurlu
        "KY" -> ObservedWeather(65, isWet = true, hasThunder = false) // Kuvvetli yağmurlu
        "KKY" -> ObservedWeather(71, isWet = true, hasThunder = false) // Karla karışık yağmurlu
        "HKY" -> ObservedWeather(71, isWet = true, hasThunder = false) // Hafif kar yağışlı
        "K" -> ObservedWeather(73, isWet = true, hasThunder = false) // Kar yağışlı
        "YKY" -> ObservedWeather(75, isWet = true, hasThunder = false) // Yoğun kar yağışlı
        "HSY", "MSY" -> ObservedWeather(80, isWet = true, hasThunder = false) // Hafif / mevzi sağanak
        "SY" -> ObservedWeather(81, isWet = true, hasThunder = false) // Sağanak yağışlı
        "KSY" -> ObservedWeather(82, isWet = true, hasThunder = false) // Kuvvetli sağanak
        "GSY", "KGY" -> ObservedWeather(95, isWet = true, hasThunder = true) // Gök gürültülü sağanak
        "DY" -> ObservedWeather(96, isWet = true, hasThunder = true) // Dolu
        else -> ObservedWeather(null, isWet = false, hasThunder = false)
    }
}

/**
 * METAR'ın "şu anki hava" grubunu (ör. "-TSRA", "SHRA", "BR") WMO koduna çevirir.
 * "RE" ile başlayanlar (RETSRA: az önce gök gürültülü yağmur) geçmişi anlatır; şimdiyi değil.
 * "VC" (yakında) olanlar istasyonun kendisinde değildir; yağış saymayız ama gök gürültüsünü sayarız.
 */
object MetarWeatherCodes {
    fun map(weather: String?, cloudCover: String?): ObservedWeather {
        val groups = weather.orEmpty().split(' ').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("RE") }
        val here = groups.filter { !it.startsWith("VC") }
        val thunder = groups.any { "TS" in it }
        val heavy = here.any { it.startsWith("+") }
        val light = here.any { it.startsWith("-") }
        fun has(code: String) = here.any { code in it }
        val code = when {
            thunder && has("GR") -> 96
            thunder && here.any { "TS" in it } -> 95
            has("SN") || has("SG") -> if (heavy) 75 else if (light) 71 else 73
            has("SHRA") || (has("SH") && has("RA")) -> if (heavy) 82 else if (light) 80 else 81
            has("RA") -> if (heavy) 65 else if (light) 61 else 63
            has("DZ") -> if (heavy) 55 else if (light) 51 else 53
            has("FG") -> 45
            else -> cloudCode(cloudCover)
        }
        val wet = here.any { g -> listOf("RA", "SN", "DZ", "SG", "GR", "GS", "PL").any { it in g } }
        return ObservedWeather(code, isWet = wet, hasThunder = thunder)
    }

    /** Bulut örtüsü: CLR/SKC/NSC/CAVOK açık, FEW az, SCT parçalı, BKN/OVC kapalı. */
    private fun cloudCode(cover: String?): Int? = when (cover?.uppercase()) {
        "CLR", "SKC", "NSC", "NCD", "CAVOK" -> 0
        "FEW" -> 1
        "SCT" -> 2
        "BKN", "OVC" -> 3
        else -> null
    }
}
