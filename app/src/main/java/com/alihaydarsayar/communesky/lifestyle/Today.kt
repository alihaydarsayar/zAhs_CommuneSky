package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.ClothingAdvice
import java.time.LocalDateTime

/** Bir önerinin kendisi ve bugünkü sonucu. */
data class EvaluatedTip(val tip: LifestyleTip, val result: TipResult)

/**
 * "Bugün için" bölümünün bütün içeriği: ne giysem ve yaşam önerileri. Ana ekrandaki kart, "Bugün için"
 * sayfası, widget'lar ve sabah özeti aynı hesabı kullanır.
 */
data class TodayAdvice(
    val wear: ClothingAdvice?,
    val tips: List<EvaluatedTip>,
) {
    /** Kartta gösterilen en önemli iki öneri. */
    val top: List<EvaluatedTip> get() = tips.sortedByDescending { it.result.attention }.take(TOP_COUNT)

    companion object {
        const val TOP_COUNT = 2

        fun of(forecast: Forecast, now: LocalDateTime, wearShift: Double = 0.0, airQualityPoor: Boolean = false): TodayAdvice = TodayAdvice(
            wear = Clothing.advise(forecast, now, wearShift),
            tips = Lifestyle.evaluate(TodayWeather.from(forecast, now, airQualityPoor)).map { EvaluatedTip(it.first, it.second) },
        )
    }
}
