package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.debug.DemoWeather
import com.alihaydarsayar.communesky.debug.DemoWeather.Entry
import com.alihaydarsayar.communesky.debug.DemoWeather.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Elle çalıştırılan yardımcı: README'deki widget görselleri. Her stil, kendi tasarım boyutunda ve örnek veriyle
 * (yalnız Beşiktaş, Londra, Paris) çizilir; duvar kâğıdı hepsinde aynı nötr koyu mavi-gri gradyandır. Telefonun
 * saati önce ayarlanır (design/store/araclar/demo.sh): `-e set day` (21 Temmuz 19:03, İstanbul) ya da `-e set night`
 * (23 Ocak 19:03). Görüntüler Android/data/…/files/readme-widgets/<tür>/<stil>.png altına yazılır.
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class ReadmeWidgetShotsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private enum class Set { Day, Night }
    private enum class State { AtHome, Nearby, Away }

    /** Renk çeşitliliği için varsayılandan farklı arka plan ve renk teması (yeşil temalar kullanılmaz). */
    private data class Look(val background: BackgroundKind, val theme: ColorTheme)

    private val SKY = Look(BackgroundKind.Sky, ColorTheme.Sky)
    private val PAPER = Look(BackgroundKind.Solid, ColorTheme.Paper)
    private val CORAL = Look(BackgroundKind.Solid, ColorTheme.Coral)
    private val GLASS = Look(BackgroundKind.Glass, ColorTheme.Dark)
    private val SUNSET = Look(BackgroundKind.Solid, ColorTheme.Sunset)
    private val OCEAN = Look(BackgroundKind.Solid, ColorTheme.Ocean)
    private val NIGHT = Look(BackgroundKind.Solid, ColorTheme.Night)

    /** Bir görselin örnek verisi. [state] yalnız Ev ve konum widget'ında; diğerlerinde evde sayılır. */
    private data class Shot(val style: WidgetStyleId, val set: Set, val sky: String, val state: State = State.AtHome, val others: Map<String, String> = emptyMap(), val suffix: String = "", val look: Look? = null)

    private val shots: List<Shot> = listOf(
        // Ev ve konum: üç stil, üç durum (evde, yakında, uzakta).
        Shot(WidgetStyleId.HomeSmart, Set.Day, "clear", State.AtHome, suffix = "at-home"),
        Shot(WidgetStyleId.HomeSmart, Set.Day, "partly_cloudy", State.Nearby, suffix = "nearby", look = SKY),
        Shot(WidgetStyleId.HomeSmart, Set.Day, "clear", State.Away, mapOf("london" to "rain"), suffix = "away", look = CORAL),
        Shot(WidgetStyleId.HomeRow, Set.Day, "mostly_clear", State.AtHome, suffix = "at-home", look = PAPER),
        Shot(WidgetStyleId.HomeRow, Set.Day, "overcast", State.Nearby, suffix = "nearby"),
        Shot(WidgetStyleId.HomeRow, Set.Day, "hot", State.Away, mapOf("london" to "drizzle"), suffix = "away", look = SKY),
        Shot(WidgetStyleId.HomeList, Set.Day, "windy", State.AtHome, suffix = "at-home", look = GLASS),
        Shot(WidgetStyleId.HomeList, Set.Day, "fog", State.Nearby, suffix = "nearby", look = PAPER),
        Shot(WidgetStyleId.HomeList, Set.Day, "clear", State.Away, mapOf("london" to "heavy_rain"), suffix = "away"),
        // Saat
        Shot(WidgetStyleId.ClockRadial, Set.Night, "clear_w"),
        Shot(WidgetStyleId.ClockAnalog, Set.Day, "clear"),
        Shot(WidgetStyleId.ClockField, Set.Day, "mostly_clear"),
        Shot(WidgetStyleId.ClockRing, Set.Night, "mostly_clear_w"),
        Shot(WidgetStyleId.ClockSS, Set.Day, "partly_cloudy"),
        Shot(WidgetStyleId.ClockWeather, Set.Day, "overcast", look = SKY),
        Shot(WidgetStyleId.ClockBig, Set.Day, "hot"),
        Shot(WidgetStyleId.ClockSide, Set.Day, "windy"),
        Shot(WidgetStyleId.ClockLine, Set.Night, "snow"),
        Shot(WidgetStyleId.ClockBold, Set.Day, "clear", look = CORAL),
        Shot(WidgetStyleId.ClockHome, Set.Day, "mostly_clear"),
        Shot(WidgetStyleId.ClockSun, Set.Day, "clear", look = OCEAN),
        Shot(WidgetStyleId.ClockGlance, Set.Day, "drizzle"),
        Shot(WidgetStyleId.ClockCard, Set.Night, "freezing", look = PAPER),
        // Saatlik, haftalık
        Shot(WidgetStyleId.HourlyCurve, Set.Day, "rain", look = SKY),
        Shot(WidgetStyleId.HourlyIcons, Set.Night, "partly_cloudy_w"),
        Shot(WidgetStyleId.HourlyRow, Set.Day, "storm", look = GLASS),
        Shot(WidgetStyleId.WeeklyBars, Set.Night, "freezing"),
        Shot(WidgetStyleId.WeeklyColumns, Set.Day, "mostly_clear", look = SKY),
        Shot(WidgetStyleId.WeeklyList, Set.Night, "freezing", look = PAPER),
        // Küçük, yazı
        Shot(WidgetStyleId.SmallClassic, Set.Day, "clear"),
        Shot(WidgetStyleId.SmallCentered, Set.Night, "snow", look = SKY),
        Shot(WidgetStyleId.SmallMinimal, Set.Day, "fog", look = CORAL),
        Shot(WidgetStyleId.SmallWear, Set.Night, "freezing", look = NIGHT),
        Shot(WidgetStyleId.TextStack, Set.Day, "drizzle"),
        Shot(WidgetStyleId.TextLine, Set.Day, "windy"),
        Shot(WidgetStyleId.TextTemperature, Set.Night, "sleet"),
        Shot(WidgetStyleId.TextWear, Set.Day, "mostly_clear"),
        // Yerler, yağış, ayrıntılar, bugün
        Shot(WidgetStyleId.PlacesColumns, Set.Day, "clear", others = mapOf("london" to "rain", "paris" to "partly_cloudy")),
        Shot(WidgetStyleId.PlacesList, Set.Day, "storm", others = mapOf("london" to "overcast", "paris" to "fog"), look = SUNSET),
        Shot(WidgetStyleId.PrecipitationBars, Set.Day, "heavy_rain", look = SKY),
        Shot(WidgetStyleId.PrecipitationSentence, Set.Day, "rain"),
        Shot(WidgetStyleId.DetailsTiles, Set.Day, "windy"),
        Shot(WidgetStyleId.DetailsSun, Set.Day, "clear", look = SKY),
        Shot(WidgetStyleId.DetailsList, Set.Day, "hot", look = CORAL),
        Shot(WidgetStyleId.TodayCard, Set.Day, "rain", look = OCEAN),
    )

    @Test
    fun readmeWidgets() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val wanted = arguments.getString("set")?.let { name -> Set.entries.first { it.name.equals(name, ignoreCase = true) } }
        val only = arguments.getString("style")?.split(",")
        val out = File(context.getExternalFilesDir(null), "readme-widgets").apply { mkdirs() }
        for (shot in shots) {
            if (wanted != null && shot.set != wanted) continue
            if (only != null && shot.style.name !in only) continue
            install(shot)
            val input = com.alihaydarsayar.communesky.widget.WidgetDataLoader(context).input.first()
            val file = File(File(out, shot.style.kind.name).apply { mkdirs() }, shot.style.name + (if (shot.suffix.isEmpty()) "" else "_" + shot.suffix) + ".png")
            draw(input, shot.style, file, shot.look)
        }
    }

    private suspend fun install(shot: Shot) {
        val besiktas = DemoWeather.BESIKTAS
        val sky = { key: String -> DemoWeather.SKIES.getValue(key) }
        val entries = mutableListOf<Entry>()
        when (shot.state) {
            State.AtHome -> entries += Entry(besiktas, sky(shot.sky), Role.Device, aqi = null)
            State.Nearby -> entries += Entry(besiktas, sky(shot.sky), Role.Device, aqi = null, deviceAt = DemoWeather.DemoPlace("near", "", null, 41.0660, 29.0260, besiktas.zone), deviceName = null)
            State.Away -> entries += Entry(DemoWeather.LONDON, sky(shot.others.getValue("london")), Role.Device, aqi = null)
        }
        entries += Entry(besiktas, sky(shot.sky), Role.Home, aqi = null)
        shot.others["london"]?.let { if (shot.state != State.Away) entries += Entry(DemoWeather.LONDON, sky(it), Role.Saved, aqi = null) }
        shot.others["paris"]?.let { entries += Entry(DemoWeather.PARIS, sky(it), Role.Saved, aqi = null) }
        if (shot.state == State.Away && shot.style.kind != WidgetKind.HomeLocation) error("Uzaktayken yalnız Ev ve konum widget'ı çizilir")
        DemoWeather.install(context, entries)
    }

    private fun tickAnalog(view: View) {
        if (view is android.widget.AnalogClock) {
            runCatching { android.widget.AnalogClock::class.java.getDeclaredMethod("onTimeChanged").apply { isAccessible = true }.invoke(view) }
        }
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) tickAnalog(view.getChildAt(i))
    }

    private suspend fun draw(input: WidgetInput, style: WidgetStyleId, file: File, look: Look? = null) {
        val base = look?.let { style.defaultStyle().copy(background = it.background, colorTheme = it.theme, transparency = it.background.defaultTransparency) } ?: style.defaultStyle()
        // Hava halkasının serin dilimleri varsayılanda yeşilimsi turkuaz; görsellerde yeşil olmasın diye mavi.
        val styled = if (style == WidgetStyleId.ClockRing) base.copy(ringCool = 0xFF5B8DEF.toInt()) else base
        val widget = style.kind.widget(WidgetConfig(WidgetPlace.Smart, styled), input)
        val size = style.previewSize
        WallpaperTone.override = false
        val views = try {
            widget.compose(context, size = size)
        } finally {
            WallpaperTone.override = null
        }
        val density = context.resources.displayMetrics.density
        val pad = (14 * density).toInt()
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        withContext(Dispatchers.Main) {
            val view = views.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Duvar kâğıdı: nötr, sakin, koyu mavi-gri gradyan (hepsinde aynı).
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, bitmap.height.toFloat(), 0xFF2A3550.toInt(), 0xFF141B2E.toInt(), Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            canvas.translate(pad.toFloat(), pad.toFloat())
            // Analog kadran görünüm bir pencereye bağlı olmadığı için saat güncellenmez; şimdiki zamana getirilir.
            tickAnalog(view)
            // Launcher widget'ı köşelerinden yuvarlak keser; arka planı olan widget'lar aynı yarıçapla kırpılır.
            if (styled.background != BackgroundKind.Transparent) {
                val radius = styled.corners.radius(context).value * density
                canvas.clipPath(android.graphics.Path().apply { addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, android.graphics.Path.Direction.CW) })
            }
            view.draw(canvas)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
