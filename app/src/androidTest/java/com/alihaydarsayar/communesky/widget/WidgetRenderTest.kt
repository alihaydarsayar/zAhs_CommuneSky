package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.widget.config.WidgetSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Bütün widget stillerini ana ekrana eklemeden, örnek veriyle çizer ve PNG olarak kaydeder:
 * Android/data/com.alihaydarsayar.communesky/files/widget_*.png
 * Hem çizimin hatasız çalıştığını doğrular hem de tasarımı görmeyi sağlar. Duvar kâğıdının
 * üstünde duran stiller (saydam, cam) hem koyu hem açık bir duvar kâğıdı üstünde çizilir.
 *
 * "preview_*.png" dosyaları widget seçicisinin önizleme resimleridir
 * (res/drawable-nodpi ve drawable-tr-nodpi/widget_preview_image_*.png).
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context = localized(InstrumentationRegistry.getInstrumentation().targetContext, "tr")
    private val input = WidgetSamples.input(context)

    @Test
    fun renderEveryStyle() = runBlocking {
        for (style in WidgetStyleId.entries) {
            val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle())
            val onWallpaper = style.background == BackgroundKind.Transparent || style.background == BackgroundKind.Glass
            render(style.kind, config, style.previewSize, "${style.name}_dark", lightWallpaper = false)
            if (onWallpaper) render(style.kind, config, style.previewSize, "${style.name}_light", lightWallpaper = true)
            // Büyük yazı boyutunda da hiçbir yazı kesilmemeli.
            val large = config.copy(style = config.style.copy(textSize = TextSize.Large))
            render(style.kind, large, style.previewSize, "${style.name}_large", lightWallpaper = false)
        }
    }

    @Test
    fun renderSmallSizes() = runBlocking {
        // Her türün en küçük boyutu: yazılar kesilmeden daha az bilgi gösterilmeli.
        for (kind in WidgetKind.entries) {
            val smallest = kind.sizes.minBy { it.width.value * it.height.value }
            for (style in kind.styles) {
                render(kind, WidgetConfig(WidgetPlace.Smart, style.defaultStyle()), smallest, "small_${style.name}", lightWallpaper = false)
            }
        }
    }

    @Test
    fun renderLooks() = runBlocking {
        // Ortak kişiselleştirme: arka plan, renk teması, yazı rengi, köşe, yazı, okunurluk.
        val base = WidgetStyleId.HomeSmart.defaultStyle()
        val looks = mapOf(
            "glass" to base.withBackground(BackgroundKind.Glass),
            "glass_light_wallpaper" to base.withBackground(BackgroundKind.Glass),
            "glass_scrim_light_wallpaper" to base.withBackground(BackgroundKind.Glass).copy(legibility = Legibility.Scrim),
            "clear_scrim" to base.withBackground(BackgroundKind.Transparent).copy(legibility = Legibility.Scrim),
            "solid_ocean" to base.withColorTheme(ColorTheme.Ocean),
            "sky" to base.withBackground(BackgroundKind.Sky).withColorTheme(ColorTheme.Sky),
            "sky_sunset_half" to base.withBackground(BackgroundKind.Sky).withColorTheme(ColorTheme.Sunset).withTransparency(50),
            "pastel" to base.withColorTheme(ColorTheme.Pastel),
            "wallpaper" to base.withColorTheme(ColorTheme.Wallpaper),
            "custom" to base.withColorTheme(ColorTheme.Custom).copy(customBackground = 0xFF102A43.toInt(), customText = 0xFFFDE68A.toInt(), accent = 0xFF78C8BE.toInt()),
            "large_text_round" to base.copy(textSize = TextSize.Large, corners = CornerSize.Large),
            "bold" to base.copy(weight = WeightChoice.Bold),
            "thin" to base.copy(weight = WeightChoice.Thin),
            "info_pill" to base.copy(infoRow = InfoRow.Pill, contents = setOf(WidgetContent.RainAlert, WidgetContent.HighLow)),
            "clear_dark_text_light_wallpaper" to base.withBackground(BackgroundKind.Transparent).copy(textColor = TextColorMode.Dark),
        )
        for ((name, style) in looks) {
            render(
                WidgetKind.HomeLocation, WidgetConfig(WidgetPlace.Smart, style), WidgetStyleId.HomeSmart.previewSize, "look_$name",
                lightWallpaper = name.contains("light_wallpaper"),
                input = WidgetSamples.input(context, scenario = WidgetSamples.Scenario.AtHome),
                wallpaperPrefersDarkText = name.contains("light_wallpaper"),
            )
        }
    }

    @Test
    fun renderClockOptions() = runBlocking {
        val radial = WidgetStyleId.ClockRadial.defaultStyle()
        val sizes = mapOf("4x2" to DpSize(340.dp, 150.dp), "5x2" to DpSize(420.dp, 170.dp), "4x2_tall" to DpSize(300.dp, 190.dp))
        for ((label, size) in sizes) render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, radial), size, "radial_$label", lightWallpaper = false)
        val radials = mapOf(
            "red_hours" to radial.copy(hourColor = 0xFFE5484D.toInt()),
            "light" to radial.withColorTheme(ColorTheme.Custom).copy(customBackground = 0xFFF4F2EC.toInt()),
            "blue" to radial.withColorTheme(ColorTheme.Custom).copy(customBackground = 0xFF102A43.toInt(), minuteColor = 0xFF78C8FF.toInt(), lineColor = 0xFF78C8FF.toInt()),
            "line" to radial.copy(secondEffect = SecondEffect.Line),
            "off" to radial.copy(secondEffect = SecondEffect.Off),
            "glass" to radial.withBackground(BackgroundKind.Glass),
            "clear" to radial.withBackground(BackgroundKind.Transparent),
            "sky" to radial.withBackground(BackgroundKind.Sky).withColorTheme(ColorTheme.Sky),
            "digits_solid" to radial.copy(clockFont = ClockFont.Digits),
        )
        for ((name, style) in radials) render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, style), DpSize(340.dp, 150.dp), "radial_$name", lightWallpaper = false)

        val analogs = mapOf(
            "plain_circle" to WidgetStyleId.ClockAnalog.defaultStyle().copy(secondSymbol = SecondSymbol.Circle),
            "plain_no_seconds" to WidgetStyleId.ClockAnalog.defaultStyle().copy(secondHand = false),
            "field_8_13" to WidgetStyleId.ClockField.defaultStyle().copy(highlighted = setOf(8, 13)),
            "ring_colors" to WidgetStyleId.ClockRing.defaultStyle().copy(ringCool = 0xFF6E56CF.toInt(), ringWarm = 0xFFF5A524.toInt()),
            "field_custom" to WidgetStyleId.ClockField.defaultStyle().copy(dialColor = 0xFF102A43.toInt(), handColor = 0xFFFDE68A.toInt(), accent = 0xFF78C8BE.toInt(), highlighted = setOf(12)),
        )
        for ((name, style) in analogs) render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, style), DpSize(170.dp, 170.dp), "analog_$name", lightWallpaper = false)

        // Mevcut saat stillerinde saat yazı tipi ve ayrı saat/dakika rengi.
        for (font in ClockFont.entries) {
            val style = WidgetStyleId.ClockBig.defaultStyle().copy(clockFont = font, minuteColor = 0xFFE5484D.toInt())
            render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, style), WidgetStyleId.ClockBig.previewSize, "clockfont_${font.name}", lightWallpaper = false)
        }
    }

    @Test
    fun renderSsClock() = runBlocking {
        // SS saati: tasarımdaki beş hâl (design/png/ss-saat-final.png) ve boyutlar.
        val base = WidgetStyleId.ClockSS.defaultStyle()
        val weatherOnly = setOf(WidgetContent.WeatherIcon, WidgetContent.Temperature, WidgetContent.Condition)
        val looks = mapOf(
            "default" to base,
            "weather_short" to base.copy(contents = weatherOnly),
            "no_weather_no_date" to base.copy(contents = emptySet(), showDate = false),
            "no_date" to base.copy(showDate = false),
            "no_logo_no_seconds" to base.copy(logo = false, secondHand = false),
            "dark" to SsClock.apply(base, SsClock.Preset.Dark),
            "night_blue" to SsClock.apply(base, SsClock.Preset.NightBlue),
            "custom_accent" to base.copy(accent = 0xFF30C85A.toInt(), windowColor = 0xFFFFD84D.toInt()),
            "large_text" to base.copy(textSize = TextSize.Large),
        )
        val sample = WidgetSamples.input(context, scenario = WidgetSamples.Scenario.AtHome)
        for ((name, style) in looks) {
            render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, style), DpSize(170.dp, 170.dp), "ss_$name", lightWallpaper = false, input = sample)
        }
        for (side in listOf(110, 150, 250, 330)) {
            render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, base), DpSize(side.dp, side.dp), "ss_size_$side", lightWallpaper = false, input = sample)
        }
        render(WidgetKind.Clock, WidgetConfig(WidgetPlace.Smart, base), DpSize(340.dp, 170.dp), "ss_wide", lightWallpaper = false, input = sample)
    }

    @Test
    fun renderHomeScenarios() = runBlocking {
        // Evde / yakında / uzakta / ev yok: Ev ve konum widget'ı ve saat + ev stili.
        for (scenario in WidgetSamples.Scenario.entries) {
            val sample = WidgetSamples.input(context, scenario = scenario)
            for (style in listOf(WidgetStyleId.HomeSmart, WidgetStyleId.HomeRow, WidgetStyleId.HomeList, WidgetStyleId.ClockHome)) {
                for (size in listOf(style.previewSize, DpSize(style.previewSize.width, style.previewSize.height + 40.dp))) {
                    render(style.kind, WidgetConfig(WidgetPlace.Smart, style.defaultStyle()), size, "scenario_${scenario}_${style.name}_${size.height.value.toInt()}", lightWallpaper = false, input = sample)
                }
            }
        }
    }

    @Test
    fun renderPickerPreviews() = runBlocking {
        // Seçici görselleri iki dilde: örnek ev Türkçede "Beşiktaş", diğer dillerde "Besiktas".
        for (language in listOf("en", "tr")) {
            val local = localized(context, language)
            val sample = WidgetSamples.input(local, scenario = WidgetSamples.Scenario.AtHome)
            for (kind in WidgetKind.entries) {
                val style = kind.defaultStyle
                render(kind, WidgetConfig.default(kind), style.previewSize, "preview_${language}_${kind.name.lowercase()}", lightWallpaper = false, wallpaper = false, input = sample, context = local)
            }
        }
    }

    @Test
    fun sampleHomeNameFollowsTheLanguage() {
        assertEquals("Beşiktaş", localized(context, "tr").getString(R.string.sample_place_home))
        for (language in listOf("en", "de", "fr", "ja")) {
            assertEquals(language, "Besiktas", localized(context, language).getString(R.string.sample_place_home))
        }
        assertEquals("Beşiktaş", WidgetSamples.input(localized(context, "tr")).home!!.snapshot.city.name)
        assertEquals("Besiktas", WidgetSamples.input(localized(context, "en")).home!!.snapshot.city.name)
    }

    /**
     * Çizim süresi: veri hazırken bir widget'ın baştan sona çizilmesi (Glance bileşimi + RemoteViews).
     * Her stil birkaç kez çizilir; ortanca süre loglanır ve dosyaya yazılır. Hedef: 50 ms'nin altı.
     */
    @Test
    fun measureDrawTimes() = runBlocking {
        val lines = mutableListOf("style,size,first_ms,median_ms,max_ms")
        // Isınma: ilk çizim sınıfları ve yazı tiplerini yükler.
        repeat(3) { WidgetKind.Small.widget(WidgetConfig.default(WidgetKind.Small), input).compose(context, size = DpSize(160.dp, 160.dp)) }
        for (style in WidgetStyleId.entries) {
            val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle())
            val size = WidgetSizes.of(style.kind, style.previewSize).draw
            val times = (0 until 9).map {
                val start = SystemClock.elapsedRealtimeNanos()
                style.kind.widget(config, input).compose(context, size = size)
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            }
            val sorted = times.drop(1).sorted()
            val line = "%s,%dx%d,%.1f,%.1f,%.1f".format(Locale.US, style.name, size.width.value.toInt(), size.height.value.toInt(), times.first(), sorted[sorted.size / 2], sorted.last())
            lines += line
            Log.i("WidgetTiming", line)
        }
        File(context.getExternalFilesDir(null), "timing.csv").writeText(lines.joinToString("\n"))
    }

    private suspend fun render(
        kind: WidgetKind,
        config: WidgetConfig,
        size: DpSize,
        name: String,
        lightWallpaper: Boolean,
        wallpaper: Boolean = true,
        input: WidgetInput = this.input,
        context: Context = this.context,
        wallpaperPrefersDarkText: Boolean? = null,
    ) {
        val density = context.resources.displayMetrics.density
        // Ana ekrandaki gibi: içerik boyut basamağına göre çizilir, widget gerçek boyutunda durur.
        val draw = WidgetSizes.of(kind, size).draw
        WallpaperTone.override = wallpaperPrefersDarkText ?: lightWallpaper
        val remoteViews = try {
            kind.widget(config, input).compose(context, size = draw)
        } finally {
            WallpaperTone.override = null
        }
        val bitmap = withContext(Dispatchers.Main) {
            val view = remoteViews.apply(context, FrameLayout(context))
            draw(view, size, density, lightWallpaper, wallpaper)
        }
        val file = File(context.getExternalFilesDir(null), "widget_$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    private fun draw(view: View, size: DpSize, density: Float, lightWallpaper: Boolean, wallpaper: Boolean): Bitmap {
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            // Açık bir duvar kâğıdı taklidi: en zor durum. Seçici önizlemeleri saydam kalır.
            if (wallpaper) canvas.drawColor(if (lightWallpaper) 0xFFE9EEF3.toInt() else 0xFF44564F.toInt())
            view.draw(canvas)
        }
    }

    private fun localized(base: Context, language: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }
}
