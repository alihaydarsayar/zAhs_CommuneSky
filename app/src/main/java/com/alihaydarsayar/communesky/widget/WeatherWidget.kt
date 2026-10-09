package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle

/**
 * Ana ekran hava durumu widget'ı. Tek bir widget sınıfı, kaplanan alana göre dört farklı düzen
 * çizer (kompakt, geniş, saatlik, tahmin). Hangi yeri ve hangi arka planı göstereceği widget
 * eklenirken seçilir. Veri, uygulamanın önbelleğinden (Room) okunur; internete çıkmaz.
 *
 * [previewConfig]: testlerde ve önizlemede gerçek widget kimliği olmadan ayar vermek için.
 */
class WeatherWidget(private val previewConfig: WidgetConfig? = null) : GlanceAppWidget() {

    companion object {
        val Small = DpSize(110.dp, 110.dp)
        val Wide = DpSize(250.dp, 110.dp)
        val Medium = DpSize(250.dp, 180.dp)
        val Large = DpSize(250.dp, 280.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Medium, Large))

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
            WidgetTheme(settings, config.background) { WeatherWidgetContent(data, config.background) }
        }
    }
}

/** Widget'ın her yerinde birim ayarları ve arka plan türü okunabilsin. */
@Composable
fun WidgetTheme(settings: AppSettings, background: WidgetBackground, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalWidgetBackground provides background,
        content = content,
    )
}

@Composable
private fun WeatherWidgetContent(data: WidgetPlaceData?, background: WidgetBackground) {
    val size = LocalSize.current
    val snapshot = data?.snapshot
    val now = snapshot?.forecast?.localNow()
    val theme = if (snapshot != null && now != null) snapshot.currentScene(now).theme else SkyTheme.ClearNight
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(background, theme)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when {
            data == null || now == null -> EmptyContent()
            size.width >= WeatherWidget.Large.width && size.height >= WeatherWidget.Large.height ->
                LargeContent(data, now)
            size.width >= WeatherWidget.Medium.width && size.height >= WeatherWidget.Medium.height ->
                MediumContent(data, now)
            size.width >= WeatherWidget.Wide.width -> CurrentRow(data, now)
            else -> SmallContent(data, now)
        }
    }
}

@Composable
internal fun EmptyContent() {
    val context = LocalContext.current
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WText(context.getString(R.string.widget_no_data), 13.sp, align = TextAlign.Center, maxLines = 3)
    }
}

@Composable
private fun SmallContent(data: WidgetPlaceData, now: LocalDateTime) {
    val context = LocalContext.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(now)
    Column(GlanceModifier.fillMaxSize()) {
        PlaceName(data, 13.sp)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WText(temperatureText(current.temperature), 36.sp, font = WidgetFont.Light)
            Spacer(GlanceModifier.defaultWeight())
            Image(
                ImageProvider(current.condition.iconRes(!current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(34.dp),
            )
        }
        WText(context.getString(weatherDescriptionRes(current.weatherCode)), 12.sp, font = WidgetFont.Medium)
        // En küçük boyutta yer kalmazsa en yüksek/en düşük satırı gizlenir.
        if (today != null && LocalSize.current.height >= 130.dp) {
            WText(highLowText(context, today.maxTemperature, today.minTemperature), 12.sp, color = WidgetWhiteSecondary)
        }
    }
}

/** Yer adı, büyük sıcaklık ve sağda ikon, durum ve en yüksek/en düşük. */
@Composable
internal fun CurrentRow(data: WidgetPlaceData, now: LocalDateTime, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val context = LocalContext.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(now)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            PlaceName(data, 14.sp)
            WText(temperatureText(current.temperature), 38.sp, font = WidgetFont.Light)
        }
        Column(horizontalAlignment = Alignment.End) {
            Image(
                ImageProvider(current.condition.iconRes(!current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(40.dp),
            )
            WText(
                context.getString(weatherDescriptionRes(current.weatherCode)),
                12.sp,
                font = WidgetFont.Medium,
                align = TextAlign.End,
            )
            if (today != null) {
                WText(
                    highLowText(context, today.maxTemperature, today.minTemperature),
                    12.sp,
                    color = WidgetWhiteSecondary,
                    align = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun MediumContent(data: WidgetPlaceData, now: LocalDateTime) {
    Column(GlanceModifier.fillMaxSize()) {
        CurrentRow(data, now)
        Spacer(GlanceModifier.defaultWeight())
        HourlyRow(data, now)
    }
}

@Composable
private fun LargeContent(data: WidgetPlaceData, now: LocalDateTime) {
    val size = LocalSize.current
    // Yükseklik arttıkça daha çok gün sığdır.
    val dayCount = ((size.height.value - 200f) / 28f).toInt().coerceIn(3, 6)
    Column(GlanceModifier.fillMaxSize()) {
        CurrentRow(data, now)
        Spacer(GlanceModifier.height(10.dp))
        HourlyRow(data, now)
        Spacer(GlanceModifier.height(8.dp))
        Divider()
        Spacer(GlanceModifier.height(4.dp))
        DailyRows(data, now, dayCount)
    }
}

@Composable
internal fun Divider() {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    ) {}
}

@Composable
internal fun HourlyRow(data: WidgetPlaceData, now: LocalDateTime, count: Int = 6) {
    val context = LocalContext.current
    val snapshot = data.snapshot
    val hours = snapshot.forecast.upcomingHours(now, count = count)
    val formatter = hourFormatter(context)
    Row(GlanceModifier.fillMaxWidth()) {
        hours.forEachIndexed { index, hour ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                WText(
                    if (index == 0) context.getString(R.string.now) else hour.time.format(formatter),
                    11.sp,
                    color = WidgetWhiteSecondary,
                    align = TextAlign.Center,
                )
                Spacer(GlanceModifier.height(3.dp))
                Image(
                    ImageProvider(hour.condition.iconRes(!hour.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                )
                Spacer(GlanceModifier.height(3.dp))
                WText(
                    temperatureText(if (index == 0) snapshot.forecast.current.temperature else hour.temperature),
                    13.sp,
                    font = WidgetFont.Medium,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DailyRows(data: WidgetPlaceData, now: LocalDateTime, count: Int) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    data.snapshot.forecast.upcomingDays(now).take(count).forEachIndexed { index, day ->
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WText(
                if (index == 0) {
                    context.getString(R.string.today)
                } else {
                    day.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
                        .replaceFirstChar { it.titlecase(locale) }
                },
                13.sp,
                font = WidgetFont.Medium,
                modifier = GlanceModifier.defaultWeight(),
            )
            WText(
                context.getString(R.string.precipitation_value, day.precipitationProbability),
                11.sp,
                color = if (day.precipitationProbability >= 20) WidgetRainBlue else Color.White.copy(alpha = 0.6f),
                align = TextAlign.End,
                modifier = GlanceModifier.width(36.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Image(
                ImageProvider(day.condition.iconRes(isNight = false)),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
            WText(
                temperatureText(day.minTemperature),
                13.sp,
                color = WidgetWhiteSecondary,
                align = TextAlign.End,
                modifier = GlanceModifier.width(40.dp),
            )
            WText(
                temperatureText(day.maxTemperature),
                13.sp,
                font = WidgetFont.Medium,
                align = TextAlign.End,
                modifier = GlanceModifier.width(40.dp),
            )
        }
    }
}

/** Yer adı; cihaz konumuysa konum işareti, Ev ise ev işareti önünde. */
@Composable
internal fun PlaceName(data: WidgetPlaceData, size: TextUnit, label: String? = null) {
    val context = LocalContext.current
    val icon = when {
        data.isHome -> R.drawable.ic_home
        data.snapshot.isCurrentLocation -> R.drawable.ic_location
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(
                ImageProvider(icon),
                contentDescription = context.getString(
                    if (data.isHome) R.string.home_place else R.string.current_location,
                ),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
        }
        WText(label ?: data.snapshot.displayName(context), size, font = WidgetFont.Medium)
    }
}
