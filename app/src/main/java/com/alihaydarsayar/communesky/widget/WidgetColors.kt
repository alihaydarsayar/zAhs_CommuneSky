package com.alihaydarsayar.communesky.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.alihaydarsayar.communesky.model.SkyTheme

/**
 * Bir renk temasının renkleri. [top]–[mid]–[bottom] gradyanı (sol üstten sağ alta), [solid] düz
 * arka plan. [container]/[onContainer]/[containerAccent]: "Kalın" saat gibi açık dolu kutular.
 */
data class Palette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val solid: Color,
    val gradientDarkText: Boolean = false,
    val solidDarkText: Boolean = false,
    val container: Color = lerp(mid, Color.White, 0.84f),
    val onContainer: Color = lerp(top, Color.Black, 0.35f),
    val containerAccent: Color = lerp(mid, Color.Black, 0.1f),
) {
    val gradient: List<Color> get() = listOf(top, mid, bottom)
}

object WidgetPalettes {

    /** Gökyüzü teması: hava ve günün saatine göre (tasarım: akşam #22305E → #5E5590, yağmur #263852 → #3F5068). */
    fun sky(theme: SkyTheme): Palette = when (theme) {
        SkyTheme.ClearDay -> palette(0xFF2F6FD6, 0xFF4C8EE8, 0xFF7DB3F2)
        SkyTheme.ClearNight -> palette(0xFF22305E, 0xFF38407A, 0xFF5E5590)
        SkyTheme.Sunrise -> palette(0xFF3A4A8A, 0xFFA0628F, 0xFFE39A7A)
        SkyTheme.Sunset -> palette(0xFF2E3266, 0xFF7E4C86, 0xFFD9826A)
        SkyTheme.CloudyDay -> palette(0xFF4E6584, 0xFF6A819E, 0xFF8FA5BD)
        SkyTheme.CloudyNight -> palette(0xFF2A3566, 0xFF3B4176, 0xFF4D4C86)
        SkyTheme.RainDay -> palette(0xFF34485F, 0xFF4A5D76, 0xFF6A7E96)
        SkyTheme.RainNight -> palette(0xFF263852, 0xFF32445D, 0xFF3F5068)
        SkyTheme.SnowDay -> palette(0xFF5A7AA3, 0xFF7F9DC0, 0xFFA9C0DA)
        SkyTheme.SnowNight -> palette(0xFF1F2A44, 0xFF2E3D5C, 0xFF46597A)
        SkyTheme.FogDay -> palette(0xFF66788C, 0xFF8494A6, 0xFFA5B3C1)
        SkyTheme.FogNight -> palette(0xFF262C38, 0xFF363E4C, 0xFF4D5666)
        SkyTheme.Storm -> palette(0xFF1A1D33, 0xFF2C3052, 0xFF4A4570)
    }

    fun of(context: Context, theme: ColorTheme, sky: SkyTheme): Palette = when (theme) {
        ColorTheme.Sky -> sky(sky)
        ColorTheme.Wallpaper -> dynamic(context) ?: Palette(
            top = Color(0xFF22305E),
            mid = Color(0xFF38407A),
            bottom = Color(0xFF5E5590),
            solid = Color(0xFFEADDFB),
            solidDarkText = true,
            container = Color(0xFFEADDFB),
            onContainer = Color(0xFF3B2A73),
            containerAccent = Color(0xFF8A5A9E),
        )
        ColorTheme.Sunset -> palette(0xFF3B2F63, 0xFF9A5680, 0xFFEE946B)
        ColorTheme.Ocean -> palette(0xFF0D3550, 0xFF176A87, 0xFF2FA3B5)
        ColorTheme.Forest -> palette(0xFF1C3527, 0xFF2D5A43, 0xFF5E8C5F)
        ColorTheme.Night -> Palette(
            top = Color(0xFF0F1630),
            mid = Color(0xFF1A2440),
            bottom = Color(0xFF2A3566),
            solid = Color(0xFF121A36),
        )
        ColorTheme.Pastel -> Palette(
            top = Color(0xFFF7D9E6),
            mid = Color(0xFFDCE6F8),
            bottom = Color(0xFFD2F0E6),
            solid = Color(0xFFE8E1F7),
            gradientDarkText = true,
            solidDarkText = true,
            container = Color(0xFFF3E8FF),
            onContainer = Color(0xFF3B2A73),
            containerAccent = Color(0xFF8A5A9E),
        )
    }

    /** Android 12+ Material You renkleri (duvar kâğıdından). */
    private fun dynamic(context: Context): Palette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        fun c(id: Int) = Color(context.getColor(id))
        return Palette(
            top = c(android.R.color.system_accent1_800),
            mid = c(android.R.color.system_accent1_700),
            bottom = c(android.R.color.system_accent2_600),
            solid = c(android.R.color.system_accent1_100),
            solidDarkText = true,
            container = c(android.R.color.system_accent1_100),
            onContainer = c(android.R.color.system_accent1_800),
            containerAccent = c(android.R.color.system_accent3_600),
        )
    }

    private fun palette(top: Long, mid: Long, bottom: Long) =
        Palette(top = Color(top), mid = Color(mid), bottom = Color(bottom), solid = Color(top))
}

/**
 * Çizimde kullanılan renkler: yazı, vurgular (yağış #B5E3FF, güneş/ay #FDE68A) ve yüzeyler.
 * [shadow]: yazı doğrudan duvar kâğıdının üstünde; her duvar kâğıdında okunsun diye gölgeli çizilir.
 */
data class WidgetColors(
    val palette: Palette,
    val darkText: Boolean,
    val shadow: Boolean,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val rain: Color,
    val sun: Color,
    val divider: Color,
    val surface: Color,
    val pill: Color,
    val strip: Color,
) {
    companion object {
        fun resolve(context: Context, style: WidgetStyle, sky: SkyTheme): WidgetColors {
            val palette = WidgetPalettes.of(context, style.colorTheme, sky)
            val filled = style.transparency < 60
            // Yazı doğrudan duvar kâğıdının üstünde mi?
            val onWallpaper = when (style.background) {
                BackgroundKind.Sky, BackgroundKind.Solid -> !filled
                BackgroundKind.Glass -> true
                BackgroundKind.Transparent -> style.transparency > 40
            }
            val dark = when (style.textColor) {
                TextColorMode.Light -> false
                TextColorMode.Dark -> true
                TextColorMode.Auto -> when {
                    style.background == BackgroundKind.Sky && filled -> palette.gradientDarkText
                    style.background == BackgroundKind.Solid && filled -> palette.solidDarkText
                    style.background == BackgroundKind.Transparent && !onWallpaper -> false
                    else -> WallpaperTone.prefersDarkText(context)
                }
            }
            val shadow = !dark && onWallpaper && !(style.background == BackgroundKind.Glass && style.transparency <= 80)
            return if (dark) {
                val ink = Color(0xFF1B2236)
                WidgetColors(
                    palette = palette,
                    darkText = true,
                    shadow = false,
                    text = ink,
                    secondary = ink.copy(alpha = 0.74f),
                    tertiary = ink.copy(alpha = 0.6f),
                    rain = Color(0xFF1F6CB0),
                    sun = Color(0xFFB7791F),
                    divider = ink.copy(alpha = 0.14f),
                    surface = ink.copy(alpha = 0.06f),
                    pill = ink.copy(alpha = 0.08f),
                    strip = ink.copy(alpha = 0.08f),
                )
            } else {
                // Duvar kâğıdı açık renkli olabilir: gölgeli yazıda soluk tonlar neredeyse tam beyaz olsun.
                val soft = if (shadow) 0.92f else 0.82f
                WidgetColors(
                    palette = palette,
                    darkText = false,
                    shadow = shadow,
                    text = Color.White,
                    secondary = Color.White.copy(alpha = soft),
                    tertiary = Color.White.copy(alpha = if (shadow) 0.88f else 0.7f),
                    rain = if (shadow) Color(0xFFDDF1FF) else Color(0xFFB5E3FF),
                    sun = Color(0xFFFDE68A),
                    divider = Color.White.copy(alpha = if (shadow) 0.55f else 0.18f),
                    surface = Color.White.copy(alpha = 0.10f),
                    pill = if (onWallpaper) Color(0xFF141830).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f),
                    strip = Color(0xFF182036).copy(alpha = 0.92f),
                )
            }
        }
    }
}

/** Duvar kâğıdı açık mı koyu mu? İzin gerektirmez; sadece sistemin hesapladığı renkler okunur. */
object WallpaperTone {

    fun prefersDarkText(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        } else {
            Color(colors.primaryColor.toArgb()).luminance() > 0.6f
        }
    }.getOrDefault(false)

    /** Ekleme ekranında önizlemenin arkasına duvar kâğıdının renkleri; bilinmiyorsa null. */
    fun colors(context: Context): List<Color>? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?: return null
        listOfNotNull(colors.primaryColor, colors.secondaryColor ?: colors.primaryColor).map { Color(it.toArgb()) }
    }.getOrNull()
}
