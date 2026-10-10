package com.alihaydarsayar.communesky.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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

/** Bütün widget'ların ortak renkleri (tasarım dili, 1.4). */
object WidgetInk {
    /** Koyu zeminde ana yazı. */
    val Light = Color(0xFFF2F3F5)

    /** Açık zeminde ana yazı. */
    val Dark = Color(0xFF111214)

    /** Varsayılan koyu zemin. */
    val Ground = Color(0xFF121316)

    /** Varsayılan vurgu: saniye çizgisi, gün adı, "Bugün", "Şimdi", uyarılar. */
    val Accent = Color(0xFFE5484D)
    val Sun = Color(0xFFF5A524)
    val Rain = Color(0xFF5B9DFF)
    val Moon = Color(0xFFFDE68A)

    /** Sıcaklık aralığı: serin turkuazdan sıcak mercana. */
    val Cool = Color(0xFF78C8BE)
    val Warm = Color(0xFFFF8A65)

    /** Bilgi hapı: %38 #121A36. */
    val Pill = Color(0xFF121A36).copy(alpha = 0.38f)

    /** Renk seçicideki 12 hazır renk. */
    val Presets: List<Int> = listOf(
        0xFFF2F3F5, 0xFF111214, 0xFFE5484D, 0xFFFF8A65, 0xFFF5A524, 0xFFFDE68A,
        0xFF78C8BE, 0xFF46A758, 0xFF5B9DFF, 0xFF6E56CF, 0xFFD6409F, 0xFF8B8D98,
    ).map { it.toInt() }
}

/** Açık zeminde de canlı kalan, yine de okunur vurgu renkleri. */
object VividOnLight {
    val Sun = Color(0xFFF08C00)
    val Rain = Color(0xFF2F7BF5)
    val Moon = Color(0xFFD99A00)
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

    /** [custom]: "Özel" temada kullanıcının seçtiği zemin rengi. */
    fun of(context: Context, theme: ColorTheme, sky: SkyTheme, custom: Int? = null): Palette = when (theme) {
        ColorTheme.Dark -> Palette(
            top = WidgetInk.Ground,
            mid = Color(0xFF17191D),
            bottom = Color(0xFF22252B),
            solid = WidgetInk.Ground,
            container = Color(0xFFF4F2EC),
            onContainer = WidgetInk.Dark,
            containerAccent = WidgetInk.Accent,
        )
        ColorTheme.Custom -> {
            val ground = custom?.let { Color(it) } ?: WidgetInk.Ground
            val dark = ground.luminance() > 0.4f
            val tint = if (dark) Color.Black else Color.White
            Palette(
                top = ground,
                mid = lerp(ground, tint, 0.06f),
                bottom = lerp(ground, tint, 0.14f),
                solid = ground,
                gradientDarkText = dark,
                solidDarkText = dark,
            )
        }
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
 * Çizimde kullanılan renkler: yazı, vurgular ve yüzeyler.
 * [shadow]: yazı doğrudan duvar kâğıdının üstünde; her duvar kâğıdında okunsun diye gölgeli çizilir
 * (açık yazıda koyu, koyu yazıda açık gölge). [scrim]: gölge yerine yazının arkasına hafif perde.
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
    val accent: Color = WidgetInk.Accent,
    val moon: Color = WidgetInk.Moon,
    val cool: Color = WidgetInk.Cool,
    val warm: Color = WidgetInk.Warm,
    val scrim: Boolean = false,
    /** Yazı doğrudan duvar kâğıdının üstünde mi (cam, saydam ya da çok saydam dolu arka plan)? */
    val onWallpaper: Boolean = false,
) {
    val shadowKind: WShadow
        get() = when {
            !shadow -> WShadow.None
            darkText -> WShadow.Light
            else -> WShadow.Dark
        }

    companion object {
        fun resolve(
            context: Context,
            style: WidgetStyle,
            sky: SkyTheme,
            wallpaperPrefersDarkText: Boolean = WallpaperTone.prefersDarkText(context),
        ): WidgetColors {
            val palette = WidgetPalettes.of(context, style.colorTheme, sky, style.customBackground)
            val transparency = style.effectiveTransparency
            val filled = transparency < 60
            val onWallpaper = isOnWallpaper(style)
            val custom = style.colorTheme == ColorTheme.Custom
            val customText = style.customText?.takeIf { custom }?.let { Color(it) }
            val dark = when (style.textColor) {
                TextColorMode.Light -> false
                TextColorMode.Dark -> true
                TextColorMode.Auto -> when {
                    customText != null -> customText.luminance() < 0.4f
                    style.background == BackgroundKind.Sky && filled -> palette.gradientDarkText
                    style.background == BackgroundKind.Solid && filled -> palette.solidDarkText
                    style.background == BackgroundKind.Transparent && !onWallpaper -> false
                    // Cam, saydam ve çok saydam arka planlar: duvar kâğıdı açıksa koyu yazı.
                    else -> wallpaperPrefersDarkText
                }
            }
            val shadow = onWallpaper && style.legibility == Legibility.Shadow
            val scrim = onWallpaper && style.legibility == Legibility.Scrim
            val accent = style.accent?.let { Color(it) } ?: WidgetInk.Accent
            val ink = when {
                customText != null && style.textColor == TextColorMode.Auto -> customText
                dark -> WidgetInk.Dark
                else -> WidgetInk.Light
            }
            val secondary = style.customSecondary?.takeIf { custom }?.let { Color(it) } ?: ink.copy(alpha = if (onWallpaper) 0.86f else 0.70f)
            return if (dark) {
                WidgetColors(
                    palette = palette,
                    darkText = true,
                    shadow = shadow,
                    text = ink,
                    secondary = secondary,
                    tertiary = ink.copy(alpha = if (onWallpaper) 0.78f else 0.6f),
                    rain = VividOnLight.Rain,
                    sun = VividOnLight.Sun,
                    divider = ink.copy(alpha = 0.18f),
                    surface = ink.copy(alpha = 0.06f),
                    pill = ink.copy(alpha = 0.08f),
                    strip = ink.copy(alpha = 0.06f),
                    accent = accent,
                    moon = VividOnLight.Moon,
                    scrim = scrim,
                    onWallpaper = onWallpaper,
                )
            } else {
                WidgetColors(
                    palette = palette,
                    darkText = false,
                    shadow = shadow,
                    text = ink,
                    secondary = secondary,
                    tertiary = ink.copy(alpha = if (onWallpaper) 0.8f else 0.55f),
                    rain = WidgetInk.Rain,
                    sun = WidgetInk.Sun,
                    divider = ink.copy(alpha = if (onWallpaper) 0.4f else 0.14f),
                    surface = Color.White.copy(alpha = 0.06f),
                    pill = WidgetInk.Pill,
                    strip = Color.White.copy(alpha = 0.04f),
                    accent = accent,
                    scrim = scrim,
                    onWallpaper = onWallpaper,
                )
            }
        }

        /** Yazı doğrudan duvar kâğıdının üstünde mi? */
        fun isOnWallpaper(style: WidgetStyle): Boolean {
            val transparency = style.effectiveTransparency
            return when (style.background) {
                BackgroundKind.Sky, BackgroundKind.Solid -> transparency >= 60
                BackgroundKind.Glass -> true
                BackgroundKind.Transparent -> transparency > 40
            }
        }

        /**
         * Yazının arkasındaki zeminin tahmini rengi (ARGB): widget'ın arka planı, saydamlığıyla
         * birlikte [wallpaper] renginin üstüne bindirilir. Ekleme ekranındaki kontrast uyarısı için.
         */
        fun estimatedBackground(style: WidgetStyle, colors: WidgetColors, wallpaper: Int): Int {
            val alpha = (100 - style.effectiveTransparency) / 100f
            val layer = when (style.background) {
                BackgroundKind.Sky -> colors.palette.mid.copy(alpha = alpha)
                BackgroundKind.Solid -> colors.palette.solid.copy(alpha = alpha)
                BackgroundKind.Glass -> Color.White.copy(alpha = GLASS_ALPHA * alpha)
                BackgroundKind.Transparent -> TransparentTint.copy(alpha = 0.75f * alpha)
            }
            var ground = Contrast.composite(layer.toArgb(), wallpaper or (0xFF shl 24))
            if (colors.scrim) ground = Contrast.composite(scrimColor(colors.darkText).toArgb(), ground)
            return ground
        }

        /** Hafif perde: açık yazının arkasında %25 koyu, koyu yazının arkasında %25 açık. */
        fun scrimColor(darkText: Boolean): Color = (if (darkText) Color.White else Color.Black).copy(alpha = 0.25f)

        const val GLASS_ALPHA = 0.4f
        val TransparentTint = Color(0xFF0B1026)
    }
}

/** Duvar kâğıdı açık mı koyu mu? İzin gerektirmez; sadece sistemin hesapladığı renkler okunur. */
object WallpaperTone {

    /** Testler için: verilirse duvar kâğıdı yerine bu değer kullanılır. */
    @Volatile internal var override: Boolean? = null

    fun prefersDarkText(context: Context): Boolean = override ?: runCatching {
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
