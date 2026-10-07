package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.alihaydarsayar.communesky.work.RefreshScheduler

/**
 * Widget seçicide üç ayrı seçenek görünsün diye üç alıcı (receiver) var: kompakt (2x2),
 * saatlik (4x2) ve tahmin (4x4). Hepsi aynı [WeatherWidget]'ı kullanır; widget yeniden
 * boyutlandırılınca düzen kendiliğinden değişir.
 */
abstract class BaseWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Uygulama hiç açılmasa bile widget güncel kalsın.
        RefreshScheduler.schedule(context)
    }
}

class SmallWeatherWidgetReceiver : BaseWeatherWidgetReceiver()
class MediumWeatherWidgetReceiver : BaseWeatherWidgetReceiver()
class LargeWeatherWidgetReceiver : BaseWeatherWidgetReceiver()

object WeatherWidgetUpdater {
    /** Önbellek değişince ana ekrandaki tüm widget'ları yeniden çizer. */
    suspend fun updateAll(context: Context) {
        WeatherWidget().updateAll(context)
    }
}
