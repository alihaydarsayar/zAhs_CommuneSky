package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.alihaydarsayar.communesky.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Bütün widget alıcılarının ortak davranışı. */
abstract class CommuneSkyWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Uygulama hiç açılmasa bile widget güncel kalsın.
        RefreshScheduler.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Kaldırılan widget'ların ayarlarını sil.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetConfigStore.get(context).remove(appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Hava durumu widget'ı seçicide üç ayrı seçenek olarak görünsün diye üç alıcı (receiver) var:
 * kompakt (2x2), saatlik (4x2) ve tahmin (4x4). Hepsi aynı [WeatherWidget]'ı kullanır; widget
 * yeniden boyutlandırılınca düzen kendiliğinden değişir.
 */
abstract class BaseWeatherWidgetReceiver : CommuneSkyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

class SmallWeatherWidgetReceiver : BaseWeatherWidgetReceiver()
class MediumWeatherWidgetReceiver : BaseWeatherWidgetReceiver()
class LargeWeatherWidgetReceiver : BaseWeatherWidgetReceiver()

/** Saatli widget. */
class ClockWidgetReceiver : CommuneSkyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}

/** Ev + Bulunduğum yer widget'ı. */
class HomeWidgetReceiver : CommuneSkyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}

object WeatherWidgetUpdater {
    /** Önbellek ya da ayarlar değişince ana ekrandaki tüm widget'ları yeniden çizer. */
    suspend fun updateAll(context: Context) {
        WeatherWidget().updateAll(context)
        ClockWidget().updateAll(context)
        HomeWidget().updateAll(context)
    }
}
