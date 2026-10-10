package com.alihaydarsayar.communesky.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uçtan uca kayıt testi: kaydet → uygulamayı tamamen kapat → yeniden aç → bütün ayarlar aynı.
 * İki adım ayrı ayrı çalıştırılır, arada uygulama süreci öldürülür:
 *
 *   adb shell appwidget grantbind --package com.alihaydarsayar.communesky --user 0
 *   adb shell am instrument -w -e class com.alihaydarsayar.communesky.widget.WidgetSavingEndToEndTest#step1_save ...
 *   adb shell am force-stop com.alihaydarsayar.communesky
 *   adb shell am instrument -w -e class com.alihaydarsayar.communesky.widget.WidgetSavingEndToEndTest#step2_verifyAfterProcessDeath ...
 *
 * Widget bağlama izni yoksa test atlanır.
 */
@RunWith(AndroidJUnit4::class)
class WidgetSavingEndToEndTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = WidgetConfigStore.get(context)
    private val notes = File(context.filesDir, "saving_test.txt")

    /** Her alanı varsayılandan farklı ayarlar; tür başına bir tane. */
    private fun sample(kind: WidgetKind): WidgetConfig {
        val style = kind.styles.last().defaultStyle()
            .withBackground(BackgroundKind.Glass)
            .withTransparency(35)
            .withColorTheme(ColorTheme.Custom)
            .copy(
                textColor = TextColorMode.Light,
                corners = CornerSize.Large,
                textSize = TextSize.Large,
                contents = kind.contents.take(1).toSet(),
                weight = WeightChoice.Bold,
                infoRow = InfoRow.Pill,
                legibility = Legibility.Scrim,
                accent = 0xFF5B9DFF.toInt(),
                customBackground = 0xFF102A43.toInt(),
                customText = 0xFFF2F3F5.toInt(),
                customSecondary = 0xFF8B8D98.toInt(),
                clockFont = ClockFont.Digits,
                hourColor = 0xFFE5484D.toInt(),
                minuteColor = 0xFF78C8BE.toInt(),
                lineColor = 0xFFFDE68A.toInt(),
                pillColor = 0xFF111214.toInt(),
                secondEffect = SecondEffect.Line,
                secondHand = false,
                secondSymbol = SecondSymbol.Circle,
                dialColor = 0xFFF4F2EC.toInt(),
                numeralColor = 0xFF111214.toInt(),
                handColor = 0xFF46A758.toInt(),
                highlighted = setOf(8, 13),
                ringCool = 0xFF6E56CF.toInt(),
                ringWarm = 0xFFD6409F.toInt(),
                ringRain = 0xFF46A758.toInt(),
            )
        return WidgetConfig(WidgetPlace.Saved(7), style)
    }

    @Test
    fun step1_save() = runBlocking<Unit> {
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, HOST_ID)
        host.deleteHost()
        val lines = mutableListOf<String>()
        for (kind in WidgetKind.entries) {
            val id = host.allocateAppWidgetId()
            assumeTrue("Widget bağlama izni yok", manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, kind.receiverClass)))
            // Kaydet düğmesiyle aynı yol: önce yazma biter, sonra yalnızca bu widget çizilir.
            store.set(id, sample(kind), kind)
            WeatherWidgetUpdater.update(context, kind, id)
            lines += "${kind.name}=$id"
        }
        notes.writeText(lines.joinToString("\n"))
        assertEquals(WidgetKind.entries.size, lines.size)
    }

    @Test
    fun step2_verifyAfterProcessDeath() = runBlocking<Unit> {
        assumeTrue("Önce step1_save çalışmalı", notes.isFile)
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, HOST_ID)
        val ids = notes.readLines().associate { line -> WidgetKind.valueOf(line.substringBefore('=')) to line.substringAfter('=').toInt() }
        assertEquals(WidgetKind.entries.size, ids.size)
        for ((kind, id) in ids) {
            // Süreç öldükten sonra: diskten okunan ayar, kaydedilenle alan alan aynı.
            assertTrue("${kind.name} kaydı yok", store.exists(id))
            assertEquals(kind.name, sample(kind), store.get(id, kind))
            assertEquals(kind.name, sample(kind), store.initial(id, kind))
            // Aynı türden yeni bir widget (silip yeniden ekleme): son kaydedilen ayarla açılır.
            assertEquals(kind.name, sample(kind), store.initial(id + 10_000, kind))
            // Widget hâlâ sistemde bağlı ve Widget'larım ekranının listesinde.
            assertTrue(kind.name, id in manager.getAppWidgetIds(ComponentName(context, kind.receiverClass)))
            WeatherWidgetUpdater.update(context, kind, id)
        }
        host.deleteHost()
        notes.delete()
    }

    private companion object {
        const val HOST_ID = 4712
    }
}
