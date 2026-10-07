package com.alihaydarsayar.communesky.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R

/**
 * Outfit: geometrik, modern bir yazı tipi (res/font/outfit.ttf, SIL Open Font Lisansı).
 * Tek bir "değişken" (variable) dosya bütün kalınlıkları içerir; uygulamaya ~110 KB ekler.
 */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Outfit = FontFamily(
    outfit(FontWeight.Thin),
    outfit(FontWeight.ExtraLight),
    outfit(FontWeight.Light),
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
)

private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.withOutfit(),
    displayMedium = Default.displayMedium.withOutfit(),
    displaySmall = Default.displaySmall.withOutfit(),
    headlineLarge = Default.headlineLarge.withOutfit(),
    headlineMedium = Default.headlineMedium.withOutfit(FontWeight.Medium),
    headlineSmall = Default.headlineSmall.withOutfit(),
    titleLarge = Default.titleLarge.withOutfit(FontWeight.Medium),
    titleMedium = Default.titleMedium.withOutfit(FontWeight.Medium),
    titleSmall = Default.titleSmall.withOutfit(FontWeight.Medium),
    bodyLarge = Default.bodyLarge.withOutfit(),
    bodyMedium = Default.bodyMedium.withOutfit(),
    bodySmall = Default.bodySmall.withOutfit(),
    labelLarge = Default.labelLarge.withOutfit(FontWeight.Medium),
    labelMedium = Default.labelMedium.withOutfit(FontWeight.Medium),
    labelSmall = Default.labelSmall.withOutfit(FontWeight.Medium),
)

/** Ana ekrandaki dev sıcaklık yazısı. */
val HeroTemperatureStyle = TextStyle(
    fontFamily = Outfit,
    fontWeight = FontWeight.Thin,
    fontSize = 132.sp,
    lineHeight = 132.sp,
    letterSpacing = (-4).sp,
)

private fun TextStyle.withOutfit(weight: FontWeight? = null) =
    copy(fontFamily = Outfit, fontWeight = weight ?: fontWeight)
