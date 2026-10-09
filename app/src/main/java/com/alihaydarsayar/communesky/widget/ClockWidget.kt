package com.alihaydarsayar.communesky.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Saatli widget: büyük saat ve tarih, yanında hava durumu.
 * Saat Android'in TextClock bileşeniyle çizilir; uygulama dakikada bir uyanmaz, pil harcamaz.
 * Saate dokununca telefonun saat/alarm uygulaması, havaya dokununca Commune Sky açılır.
 */
class ClockWidget(private val previewConfig: WidgetConfig? = null) : GlanceAppWidget() {

    companion object {
        /** Dar: saat üstte, hava altta. */
        val Compact = DpSize(180.dp, 110.dp)

        /** Geniş: saat solda, hava sağda. */
        val Wide = DpSize(250.dp, 110.dp)

        /** Yüksek: geniş düzenin altına saatlik tahmin eklenir. */
        val Tall = DpSize(250.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Wide, Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val configFlow = widgetConfigFlow(context, id, previewConfig)
        val loader = WidgetDataLoader(context)
        val placeFlow = loader.place(configFlow)
        // İlk çizim hemen dolu gelsin; sonrası akışlardan.
        val initialConfig = configFlow.first()
        val initialData = placeFlow.first()
        val initialSettings = loader.settings.first()
        provideContent {
            val config by configFlow.collectAsState(initialConfig)
            val data by placeFlow.collectAsState(initialData)
            val settings by loader.settings.collectAsState(initialSettings)
            WidgetTheme(settings, config.background) { ClockWidgetContent(data, config.background) }
        }
    }
}

@Composable
private fun ClockWidgetContent(data: WidgetPlaceData?, background: WidgetBackground) {
    val size = LocalSize.current
    val snapshot = data?.snapshot
    val now = snapshot?.forecast?.localNow()
    val theme = if (snapshot != null && now != null) snapshot.currentScene(now).theme else SkyTheme.ClearNight
    val wide = size.width >= ClockWidget.Wide.width
    val tall = wide && size.height >= ClockWidget.Tall.height
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(background, theme)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (wide) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Clock(timeSize = 52f, modifier = GlanceModifier.defaultWeight())
                if (data != null && now != null) WeatherSummary(data, now, showHighLow = tall)
            }
            if (tall && data != null && now != null) {
                Spacer(GlanceModifier.defaultWeight())
                HourlyRow(data, now, count = 5)
            }
        } else {
            // Dar düzen: solda saat ve tarih, sağda sadece ikon ve sıcaklık.
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Clock(timeSize = 40f, modifier = GlanceModifier.defaultWeight())
                if (data != null && now != null) CompactWeather(data)
            }
        }
    }
}

/** Android'in saat bileşeni. Dokununca saat/alarm uygulaması açılır. */
@Composable
private fun Clock(timeSize: Float, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val shadow = LocalWidgetBackground.current == WidgetBackground.Transparent
    val views = RemoteViews(
        context.packageName,
        if (shadow) R.layout.widget_clock_shadow else R.layout.widget_clock_plain,
    ).apply {
        setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, timeSize)
        // Tarih biçimi telefonun diline göre: "Thu, Oct 8" / "8 Eki Per".
        val locale = context.resources.configuration.locales[0]
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        setCharSequence(R.id.clock_date, "setFormat12Hour", datePattern)
        setCharSequence(R.id.clock_date, "setFormat24Hour", datePattern)
        // Biçimi ayrıca vermek saatin ilk çizimde de dolu gelmesini sağlar.
        setCharSequence(R.id.clock_time, "setFormat12Hour", "h:mm")
        setCharSequence(R.id.clock_time, "setFormat24Hour", "HH:mm")
        setOnClickPendingIntent(R.id.clock_root, clockAppIntent(context))
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) { AndroidRemoteViews(views) }
}

private fun clockAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Geniş düzende sağdaki hava bloğu: ikon + sıcaklık, durum, yer. Dokununca uygulama açılır. */
@Composable
private fun WeatherSummary(data: WidgetPlaceData, now: LocalDateTime, showHighLow: Boolean) {
    val context = LocalContext.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(now)
    val align = TextAlign.End
    Column(
        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(current.condition.iconRes(!current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(34.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            WText(temperatureText(current.temperature), 32.sp, font = WidgetFont.Light)
        }
        WText(context.getString(weatherDescriptionRes(current.weatherCode)), 12.sp, font = WidgetFont.Medium, align = align)
        // Alçak (110 dp) widget'ta yer kalmadığı için en yüksek/en düşük sadece yüksek düzende.
        if (showHighLow && today != null) {
            WText(highLowText(context, today.maxTemperature, today.minTemperature), 12.sp, color = WidgetWhiteSecondary, align = align)
        }
        Spacer(GlanceModifier.height(2.dp))
        PlaceName(data, 12.sp)
    }
}

/** Dar düzende sağdaki küçük blok: ikon ve sıcaklık. Dokununca uygulama açılır. */
@Composable
private fun CompactWeather(data: WidgetPlaceData) {
    val context = LocalContext.current
    val current = data.snapshot.forecast.current
    Column(
        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            ImageProvider(current.condition.iconRes(!current.isDay)),
            contentDescription = context.getString(weatherDescriptionRes(current.weatherCode)),
            modifier = GlanceModifier.size(32.dp),
        )
        WText(temperatureText(current.temperature), 22.sp, font = WidgetFont.Light, align = TextAlign.Center)
    }
}
