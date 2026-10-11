package com.alihaydarsayar.communesky.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Elle deneme yardımcısı: launcher'dan bir widget'ı ana ekrana eklemesini ister (launcher "Ekle"
 * diye sorar). Gerçek ana ekranda saniye efektini, yazı tiplerini ve kayıt akışını görmek için:
 *
 *   adb shell am instrument -w -e kind Clock -e class com.alihaydarsayar.communesky.widget.PinWidgetTest ...
 */
@RunWith(AndroidJUnit4::class)
class PinWidgetTest {

    @Test
    fun pin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val kind = WidgetKind.valueOf(InstrumentationRegistry.getArguments().getString("kind") ?: "Clock")
        val manager = AppWidgetManager.getInstance(context)
        assumeTrue("Launcher widget sabitlemeyi desteklemiyor", manager.isRequestPinAppWidgetSupported)
        // İstek, uygulama öndeyken yapılmalı.
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1_500)
        manager.requestPinAppWidget(ComponentName(context, kind.receiverClass), null, null)
        Thread.sleep(1_000)
        activity.finish()
    }

    /**
     * Ana ekrandaki bir türün bütün widget'larına bir stil ve arka plan verir:
     *   -e kind Clock -e style ClockRadial [-e background Glass] [-e effect Line] [-e preset Dark]
     */
    @Test
    fun restyle() = kotlinx.coroutines.runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val kind = WidgetKind.valueOf(arguments.getString("kind") ?: "Clock")
        var style = WidgetStyleId.valueOf(arguments.getString("style") ?: kind.defaultStyle.name).defaultStyle()
        arguments.getString("background")?.let { style = style.withBackground(BackgroundKind.valueOf(it)) }
        arguments.getString("effect")?.let { style = style.copy(secondEffect = SecondEffect.valueOf(it)) }
        arguments.getString("preset")?.let { style = SsClock.apply(style, SsClock.Preset.valueOf(it)) }
        val store = WidgetConfigStore.get(context)
        for (id in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.receiverClass))) {
            store.set(id, WidgetConfig(WidgetPlace.Smart, style), kind)
            WeatherWidgetUpdater.update(context, kind, id)
        }
        // Test bitince süreç kapanır; çizimin launcher'a ulaşması için kısa bir bekleme.
        kotlinx.coroutines.delay(3_000)
    }

    /**
     * Ana ekrandaki her türün widget'larına o türün [round]. stilini verir (stili o kadar olmayan tür
     * olduğu gibi kalır): bütün stiller birkaç turda ana ekranda denenir.
     *   -e round 0 [-e background Solid]
     */
    @Test
    fun restyleRound() = kotlinx.coroutines.runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val round = arguments.getString("round")?.toInt() ?: 0
        val store = WidgetConfigStore.get(context)
        for (kind in WidgetKind.entries) {
            val id = kind.styles.getOrNull(round) ?: continue
            var style = id.defaultStyle()
            arguments.getString("background")?.let { style = style.withBackground(BackgroundKind.valueOf(it)) }
            for (widget in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.receiverClass))) {
                store.set(widget, WidgetConfig(WidgetPlace.Smart, style), kind)
                WeatherWidgetUpdater.update(context, kind, widget)
                android.util.Log.i("WidgetAdd", "${kind.name} #$widget -> ${id.name}")
            }
        }
        kotlinx.coroutines.delay(4_000)
    }

    /**
     * Aynı türden birden çok widget'a ayrı ayrı ayar verir (stil, tema, arka plan, içerik) ve her birinin
     * yalnız kendi ayarını sakladığını denetler: -e kind Clock
     */
    @Test
    fun distinct() = kotlinx.coroutines.runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val kind = WidgetKind.valueOf(InstrumentationRegistry.getArguments().getString("kind") ?: "Clock")
        val store = WidgetConfigStore.get(context)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.receiverClass)).sorted()
        val styles = kind.styles
        val configs = ids.mapIndexed { index, id ->
            var style = styles[(index * 5 + 7) % styles.size].defaultStyle()
            style = when (index % 3) {
                0 -> style.withThemePreset(ColorTheme.Paper)
                1 -> style.withBackground(BackgroundKind.Glass).copy(contents = kind.contents.take(2).toSet())
                else -> style.withBackground(BackgroundKind.Solid).copy(corners = CornerSize.Large)
            }
            id to WidgetConfig(WidgetPlace.Smart, style)
        }
        for ((id, config) in configs) {
            store.set(id, config, kind)
            WeatherWidgetUpdater.update(context, kind, id)
        }
        // Hepsi yazıldıktan sonra her biri kendi ayarını döndürmeli (sonuncusu öncekileri ezmemeli).
        for ((id, config) in configs) {
            org.junit.Assert.assertEquals("Widget #$id başka bir widget'ın ayarını aldı", config, store.get(id, kind))
            android.util.Log.i("WidgetAdd", "${kind.name} #$id kendi ayarında: ${config.style.styleId(kind).name}")
        }
        kotlinx.coroutines.delay(4_000)
    }
}
