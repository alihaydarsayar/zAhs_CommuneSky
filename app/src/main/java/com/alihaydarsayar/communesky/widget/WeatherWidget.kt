package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.CompositionLocalProvider
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.common.widgetBackgroundRes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import kotlin.math.roundToInt

/** Widget, Hilt'in yönettiği depoya bu "giriş noktası" üzerinden ulaşır. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun settingsRepository(): SettingsRepository
}

/**
 * Ana ekran widget'ı. Tek bir widget sınıfı, kaplanan alana göre üç farklı düzen çizer
 * (kompakt, saatlik, tahmin). Veri, uygulamanın önbelleğinden (Room) okunur; internete çıkmaz.
 */
class WeatherWidget : GlanceAppWidget() {

    companion object {
        val Small = DpSize(110.dp, 110.dp)
        val Wide = DpSize(250.dp, 110.dp)
        val Medium = DpSize(250.dp, 180.dp)
        val Large = DpSize(250.dp, 280.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val snapshot = entryPoint.weatherRepository().snapshot(DEVICE_PLACE_ID)
        val settings = entryPoint.settingsRepository().settings.first()
        provideContent {
            CompositionLocalProvider(LocalAppSettings provides settings) { WidgetContent(snapshot) }
        }
    }
}

private val White = Color.White
private val WhiteSecondary = Color.White.copy(alpha = 0.78f)
private val RainBlue = Color(0xFFB5E3FF)

private fun style(
    size: TextUnit,
    color: Color = White,
    weight: FontWeight = FontWeight.Normal,
    light: Boolean = false,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = ColorProvider(color),
    fontSize = size,
    fontWeight = weight,
    fontFamily = if (light) FontFamily("sans-serif-light") else null,
    textAlign = align,
)

@Composable
private fun WidgetContent(snapshot: WeatherSnapshot?) {
    val size = LocalSize.current
    val now = snapshot?.forecast?.localNow()
    val theme = if (snapshot != null && now != null) snapshot.currentScene(now).theme else SkyTheme.ClearNight
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(theme.widgetBackgroundRes))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when {
            snapshot == null || now == null -> EmptyContent()
            size.width >= WeatherWidget.Large.width && size.height >= WeatherWidget.Large.height ->
                LargeContent(snapshot, now)
            size.width >= WeatherWidget.Medium.width && size.height >= WeatherWidget.Medium.height ->
                MediumContent(snapshot, now)
            size.width >= WeatherWidget.Wide.width -> CurrentRow(snapshot, now)
            else -> SmallContent(snapshot, now)
        }
    }
}

@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            context.getString(R.string.widget_no_data),
            style = style(13.sp, align = TextAlign.Center),
        )
    }
}

@Composable
private fun SmallContent(snapshot: WeatherSnapshot, now: java.time.LocalDateTime) {
    val context = LocalContext.current
    val current = snapshot.forecast.current
    val today = snapshot.forecast.today(now)
    Column(GlanceModifier.fillMaxSize()) {
        CityName(snapshot, 13.sp)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(temperature(current.temperature), style = style(36.sp, light = true))
            Spacer(GlanceModifier.defaultWeight())
            Image(
                ImageProvider(current.condition.iconRes(!current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(34.dp),
            )
        }
        Text(
            context.getString(weatherDescriptionRes(current.weatherCode)),
            style = style(12.sp, weight = FontWeight.Medium),
            maxLines = 1,
        )
        // En küçük boyutta yer kalmazsa en yüksek/en düşük satırı gizlenir.
        if (today != null && LocalSize.current.height >= 130.dp) {
            Text(highLow(context, today.maxTemperature, today.minTemperature), style = style(12.sp, WhiteSecondary))
        }
    }
}

@Composable
private fun CurrentRow(snapshot: WeatherSnapshot, now: java.time.LocalDateTime) {
    val context = LocalContext.current
    val current = snapshot.forecast.current
    val today = snapshot.forecast.today(now)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            CityName(snapshot, 14.sp)
            Text(temperature(current.temperature), style = style(38.sp, light = true))
        }
        Column(horizontalAlignment = Alignment.End) {
            Image(
                ImageProvider(current.condition.iconRes(!current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(40.dp),
            )
            Text(
                context.getString(weatherDescriptionRes(current.weatherCode)),
                style = style(12.sp, weight = FontWeight.Medium, align = TextAlign.End),
                maxLines = 1,
            )
            if (today != null) {
                Text(
                    highLow(context, today.maxTemperature, today.minTemperature),
                    style = style(12.sp, WhiteSecondary, align = TextAlign.End),
                )
            }
        }
    }
}

@Composable
private fun MediumContent(snapshot: WeatherSnapshot, now: java.time.LocalDateTime) {
    Column(GlanceModifier.fillMaxSize()) {
        CurrentRow(snapshot, now)
        Spacer(GlanceModifier.defaultWeight())
        HourlyRow(snapshot, now)
    }
}

@Composable
private fun LargeContent(snapshot: WeatherSnapshot, now: java.time.LocalDateTime) {
    val size = LocalSize.current
    // Yükseklik arttıkça daha çok gün sığdır.
    val dayCount = ((size.height.value - 200f) / 28f).toInt().coerceIn(3, 6)
    Column(GlanceModifier.fillMaxSize()) {
        CurrentRow(snapshot, now)
        Spacer(GlanceModifier.height(10.dp))
        HourlyRow(snapshot, now)
        Spacer(GlanceModifier.height(8.dp))
        Box(
            GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {}
        Spacer(GlanceModifier.height(4.dp))
        DailyRows(snapshot, now, dayCount)
    }
}

@Composable
private fun HourlyRow(snapshot: WeatherSnapshot, now: java.time.LocalDateTime) {
    val context = LocalContext.current
    val hours = snapshot.forecast.upcomingHours(now, count = 6)
    val formatter = hourFormatter(context)
    Row(GlanceModifier.fillMaxWidth()) {
        hours.forEachIndexed { index, hour ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (index == 0) context.getString(R.string.now) else hour.time.format(formatter),
                    style = style(11.sp, WhiteSecondary, align = TextAlign.Center),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(3.dp))
                Image(
                    ImageProvider(hour.condition.iconRes(!hour.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    temperature(if (index == 0) snapshot.forecast.current.temperature else hour.temperature),
                    style = style(13.sp, weight = FontWeight.Medium, align = TextAlign.Center),
                )
            }
        }
    }
}

@Composable
private fun DailyRows(snapshot: WeatherSnapshot, now: java.time.LocalDateTime, count: Int) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    snapshot.forecast.upcomingDays(now).take(count).forEachIndexed { index, day ->
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (index == 0) {
                    context.getString(R.string.today)
                } else {
                    day.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
                        .replaceFirstChar { it.titlecase(locale) }
                },
                style = style(13.sp, weight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                context.getString(R.string.precipitation_value, day.precipitationProbability),
                style = style(
                    11.sp,
                    if (day.precipitationProbability >= 20) RainBlue else Color.White.copy(alpha = 0.5f),
                    align = TextAlign.End,
                ),
                modifier = GlanceModifier.width(36.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Image(
                ImageProvider(day.condition.iconRes(isNight = false)),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
            Text(
                temperature(day.minTemperature),
                style = style(13.sp, WhiteSecondary, align = TextAlign.End),
                modifier = GlanceModifier.width(40.dp),
            )
            Text(
                temperature(day.maxTemperature),
                style = style(13.sp, weight = FontWeight.Medium, align = TextAlign.End),
                modifier = GlanceModifier.width(40.dp),
            )
        }
    }
}

@Composable
private fun CityName(snapshot: WeatherSnapshot, size: TextUnit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (snapshot.isCurrentLocation) {
            Image(
                ImageProvider(R.drawable.ic_location),
                contentDescription = context.getString(R.string.current_location),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            snapshot.city.name ?: context.getString(R.string.my_location),
            style = style(size, weight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun temperature(value: Double) =
    "${LocalAppSettings.current.temperatureUnit.fromCelsius(value).roundToInt()}°"

@Composable
private fun highLow(context: Context, max: Double, min: Double): String {
    val unit = LocalAppSettings.current.temperatureUnit
    return context.getString(R.string.high_low, unit.fromCelsius(max).roundToInt(), unit.fromCelsius(min).roundToInt())
}

private fun hourFormatter(context: Context): DateTimeFormatter {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "h a"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
