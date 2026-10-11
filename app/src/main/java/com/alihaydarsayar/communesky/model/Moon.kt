package com.alihaydarsayar.communesky.model

import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

/** Ay evresi: telefonda hesaplanır, internet gerekmez. */
object Moon {
    /** Bir kavuşum ayı (yeni aydan yeni aya), gün. */
    const val SYNODIC_DAYS = 29.530588853

    /** Bilinen bir yeni ay: 6 Ocak 2000, 18:14 UTC. */
    private val NEW_MOON: Instant = Instant.parse("2000-01-06T18:14:00Z")

    /** Evre: 0 yeni ay, 0,25 ilk dördün, 0,5 dolunay, 0,75 son dördün. */
    fun phase(at: Instant): Double {
        val days = (at.epochSecond - NEW_MOON.epochSecond) / 86_400.0
        return ((days / SYNODIC_DAYS) % 1.0 + 1.0) % 1.0
    }

    /** Ayın aydınlık görünen kısmı (0–1). */
    fun illumination(at: Instant): Double = (1 - cos(2 * PI * phase(at))) / 2
}
