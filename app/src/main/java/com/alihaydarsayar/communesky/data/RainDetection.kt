package com.alihaydarsayar.communesky.data

/**
 * Open-Meteo'nun hava kodu bazen yağışı kaçırır: model aynı dilimde yağış miktarı hesaplarken
 * kod yine de "parçalı bulutlu" olabilir (Atakum, 8 Ekim akşamı: istasyonda gök gürültülü sağanak
 * varken kod 2 geliyordu). Bu yüzden açık/bulutlu kodları (0–3) yağış miktarına bakarak yeniden
 * değerlendiriyoruz.
 *
 * Kural: sadece yağış *ekler*. Zaten yağışlı, sisli veya gök gürültülü bir koda dokunmaz.
 */
object RainDetection {

    /** Bunun altındaki yağış (mm/saat) model gürültüsü sayılır; ekranda yağmur göstermeye değmez. */
    const val MIN_RATE_MM_PER_HOUR = 0.2

    /**
     * @param code Open-Meteo'nun (veya bulut düzeltmesinden geçmiş) WMO kodu.
     * @param precipitationMm Dilimdeki toplam yağış (mm), kar dahil.
     * @param showersMm Toplamın sağanak kısmı (mm); bilinmiyorsa null.
     * @param snowfallCm Dilimdeki kar (cm); bilinmiyorsa null.
     * @param sliceHours Dilimin uzunluğu: anlık veri için 0.25 (15 dk), saatlik için 1.
     */
    fun code(
        code: Int,
        precipitationMm: Double?,
        showersMm: Double?,
        snowfallCm: Double?,
        sliceHours: Double,
    ): Int {
        if (code !in 0..3 || precipitationMm == null || sliceHours <= 0) return code
        val rate = precipitationMm / sliceHours
        if (rate < MIN_RATE_MM_PER_HOUR) return code

        val isSnow = (snowfallCm ?: 0.0) > 0.0
        val isShower = showersMm != null && showersMm >= precipitationMm / 2
        return when {
            isSnow && isShower -> if (rate < 2.5) 85 else 86
            isSnow -> when {
                rate < 1.0 -> 71
                rate < 4.0 -> 73
                else -> 75
            }
            isShower -> when {
                rate < 2.5 -> 80
                rate < 7.6 -> 81
                else -> 82
            }
            rate < 0.5 -> 51
            rate < 2.5 -> 61
            rate < 7.6 -> 63
            else -> 65
        }
    }

    fun isWet(precipitationMm: Double?, sliceHours: Double): Boolean =
        precipitationMm != null && sliceHours > 0 && precipitationMm / sliceHours >= MIN_RATE_MM_PER_HOUR
}
