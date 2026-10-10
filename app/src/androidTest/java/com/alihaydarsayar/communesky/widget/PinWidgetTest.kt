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
}
