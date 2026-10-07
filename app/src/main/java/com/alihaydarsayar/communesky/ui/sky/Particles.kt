package com.alihaydarsayar.communesky.ui.sky

import com.alihaydarsayar.communesky.model.WeatherCondition
import kotlin.random.Random

/**
 * Bir hava efektinin parçacıkları (yağmur damlası, kar tanesi, yıldız, bulut).
 * Değerler bir kez rastgele üretilir ve FloatArray'lerde tutulur; her karede yeni nesne
 * oluşturulmaz (çöp toplayıcı devreye girip animasyonu takıltmasın diye).
 * Konumlar 0..1 arası oranlardır; çizerken ekran boyutuyla çarpılır.
 */
class ParticleField(count: Int, seed: Int, init: ParticleField.(index: Int, random: Random) -> Unit) {
    val size = count
    val x = FloatArray(count)
    val y = FloatArray(count)
    val speed = FloatArray(count)
    val scale = FloatArray(count)
    val alpha = FloatArray(count)
    val phase = FloatArray(count)

    init {
        val random = Random(seed)
        for (i in 0 until count) init(i, random)
    }
}

/** Hava durumuna göre hangi efektlerin, ne yoğunlukta çizileceği. */
class SceneEffects(condition: WeatherCondition, isNight: Boolean, isGoldenHour: Boolean) {

    val stars: ParticleField? =
        if (isNight && (condition == WeatherCondition.Clear || condition == WeatherCondition.PartlyCloudy)) {
            ParticleField(count = 110, seed = 1) { i, r ->
                x[i] = r.nextFloat()
                // Yıldızlar ufka yaklaştıkça seyrekleşsin.
                y[i] = r.nextFloat().let { it * it } * 0.7f
                scale[i] = 0.5f + r.nextFloat() * 1.3f
                alpha[i] = 0.35f + r.nextFloat() * 0.65f
                speed[i] = 0.6f + r.nextFloat() * 2.2f
                phase[i] = r.nextFloat() * 6.28f
            }
        } else {
            null
        }

    val clouds: ParticleField? = cloudCount(condition).takeIf { it > 0 }?.let { count ->
        ParticleField(count = count, seed = 2) { i, r ->
            x[i] = r.nextFloat()
            // Bulutlar ekranın üst yarısında, birbirinin üstüne binerek katman oluştursun.
            y[i] = -0.04f + (i.toFloat() / count) * 0.42f + r.nextFloat() * 0.05f
            scale[i] = 0.9f + r.nextFloat() * 0.9f
            speed[i] = 5f + r.nextFloat() * 10f
            alpha[i] = 0.45f + r.nextFloat() * 0.45f
        }
    }

    val cloudTint: CloudTint = when {
        condition == WeatherCondition.Thunderstorm || condition == WeatherCondition.HeavyRain -> CloudTint.Storm
        condition.isWet -> CloudTint.Rain
        isGoldenHour -> CloudTint.Golden
        isNight -> CloudTint.Night
        else -> CloudTint.Day
    }

    val rain: ParticleField? = when (condition) {
        WeatherCondition.Drizzle -> 80
        WeatherCondition.Rain -> 150
        WeatherCondition.HeavyRain -> 240
        WeatherCondition.Thunderstorm -> 200
        else -> 0
    }.takeIf { it > 0 }?.let { count ->
        val drizzle = condition == WeatherCondition.Drizzle
        ParticleField(count = count, seed = 3) { i, r ->
            x[i] = r.nextFloat()
            y[i] = r.nextFloat()
            // Uzaktaki damlalar (küçük scale) daha yavaş, kısa ve soluk: derinlik hissi.
            scale[i] = 0.4f + r.nextFloat() * 0.6f
            speed[i] = (if (drizzle) 420f else 1050f) * (0.7f + scale[i] * 0.5f)
            alpha[i] = (if (drizzle) 0.22f else 0.18f) + scale[i] * 0.32f
        }
    }
    val isDrizzle = condition == WeatherCondition.Drizzle

    val snow: ParticleField? = if (condition == WeatherCondition.Snow) {
        ParticleField(count = 120, seed = 4) { i, r ->
            x[i] = r.nextFloat()
            y[i] = r.nextFloat()
            scale[i] = 0.35f + r.nextFloat() * 0.65f
            speed[i] = 22f + scale[i] * 70f
            alpha[i] = 0.45f + scale[i] * 0.5f
            phase[i] = r.nextFloat() * 6.28f
        }
    } else {
        null
    }

    val fog: Boolean = condition == WeatherCondition.Fog
    val lightning: Boolean = condition == WeatherCondition.Thunderstorm

    private fun cloudCount(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.Clear -> 0
        WeatherCondition.PartlyCloudy -> 4
        WeatherCondition.Cloudy -> 8
        WeatherCondition.Fog -> 4
        WeatherCondition.Drizzle -> 7
        WeatherCondition.Rain -> 8
        WeatherCondition.HeavyRain -> 9
        WeatherCondition.Snow -> 6
        WeatherCondition.Thunderstorm -> 9
    }
}

enum class CloudTint { Day, Night, Golden, Rain, Storm }
