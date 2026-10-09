package com.alihaydarsayar.communesky.data.observation

/**
 * Ölçülen hava durumu: WMO kodu (görsel karşılığı yoksa null), yağış ve gök gürültüsü.
 * [isKnown] false ise kod tanınmadı (yeni ya da hatalı bir MGM kodu); loglanır.
 */
data class ObservedWeather(
    val wmoCode: Int?,
    val isWet: Boolean,
    val hasThunder: Boolean,
    val isKnown: Boolean = true,
)

/**
 * MGM'nin "hadise" kodlarını (sondurumlar.hadiseKodu) WMO hava kodlarına çevirir.
 * Kodlar MGM'nin web sitesinde kullanılan kısaltmalardır (A = Açık, GSY = Gök gürültülü
 * sağanak yağışlı…). Liste, MGM sitesinin ve Breezy Weather'ın kullandığı tam listeyle
 * karşılaştırıldı. Görsel karşılığı olmayan olaylar (rüzgârlı, sıcak, toz…) durumu değiştirmez.
 */
object MgmWeatherCodes {

    /** 10 dakikada bu kadar (mm) ya da daha fazla yağış ölçülmüşse "yağış var" sayılır (0,6 mm/sa). */
    const val WET_MM_PER_10_MIN = 0.1

    private fun dry(code: Int?) = ObservedWeather(code, isWet = false, hasThunder = false)
    private fun wet(code: Int, thunder: Boolean = false) = ObservedWeather(code, isWet = true, hasThunder = thunder)

    fun map(code: String?): ObservedWeather = when (code?.trim()?.uppercase()) {
        null, "", "-9999" -> dry(null)
        "A" -> dry(0) // Açık
        "AB" -> dry(1) // Az bulutlu
        "PB" -> dry(2) // Parçalı bulutlu
        "CB" -> dry(3) // Çok bulutlu
        "SIS" -> dry(45) // Sisli
        "PUS" -> dry(45) // Puslu
        "DNM" -> dry(null) // Dumanlı: ayrı bir ikonumuz yok, gökyüzü modelden gelir
        "HHY" -> wet(61) // Yağışlı (türü belirtilmemiş); miktara göre aşağıda inceltilir
        "HY" -> wet(61) // Hafif yağmurlu
        "Y" -> wet(63) // Yağmurlu
        "KY" -> wet(65) // Kuvvetli yağmurlu
        "KKY" -> wet(71) // Karla karışık yağmurlu
        "HKY" -> wet(71) // Hafif kar yağışlı
        "K" -> wet(73) // Kar yağışlı
        "YKY", "KYK" -> wet(75) // Yoğun kar yağışlı
        "HSY", "MSY" -> wet(80) // Hafif / mevzi sağanak
        "SY" -> wet(81) // Sağanak yağışlı
        "KSY" -> wet(82) // Kuvvetli sağanak
        "GSY", "KGY" -> wet(95, thunder = true) // Gök gürültülü sağanak
        "DY" -> wet(96, thunder = true) // Dolu
        // Rüzgârlı, kuvvetli rüzgâr, toz fırtınası, sıcak, soğuk: durumu değiştirmez.
        "R", "GKR", "KKR", "KF", "SCK", "SGK" -> dry(null)
        else -> ObservedWeather(null, isWet = false, hasThunder = false, isKnown = false)
    }

    /**
     * Koda ek olarak istasyonun son 10 dakikada ölçtüğü yağışa bakar. Anlamlı yağış varsa kod ne
     * derse desin "yağış var" sayılır; kod yağış söylemiyorsa (açık/bulutlu/eksik) ya da sadece
     * "yağışlı" diyorsa (HHY), miktardan uygun yağmur/kar kodu seçilir.
     */
    fun map(code: String?, precipitation10MinMm: Double?, temperature: Double?): ObservedWeather {
        val byCode = map(code)
        val amount = precipitation10MinMm?.takeIf { it >= WET_MM_PER_10_MIN } ?: return byCode
        val generic = byCode.wmoCode == null || byCode.wmoCode in 0..3 || byCode.wmoCode == 45 ||
            code?.trim()?.uppercase() == "HHY"
        if (!generic) return byCode.copy(isWet = true)
        val rate = amount * 6 // mm/saat
        val snow = temperature != null && temperature <= 0.5
        val wmo = when {
            snow -> if (rate < 1.0) 71 else if (rate < 4.0) 73 else 75
            rate < 0.5 -> 51
            rate < 2.5 -> 61
            rate < 7.6 -> 63
            else -> 65
        }
        return byCode.copy(wmoCode = wmo, isWet = true)
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
