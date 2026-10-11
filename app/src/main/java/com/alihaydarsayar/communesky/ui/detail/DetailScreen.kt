package com.alihaydarsayar.communesky.ui.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.detail.DayValue
import com.alihaydarsayar.communesky.model.detail.Metric
import com.alihaydarsayar.communesky.model.detail.MetricData
import com.alihaydarsayar.communesky.model.detail.MetricDetail
import com.alihaydarsayar.communesky.model.detail.MetricText
import com.alihaydarsayar.communesky.model.today.Compared
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.labelRes
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.home.SunTile
import com.alihaydarsayar.communesky.ui.theme.Outfit
import com.alihaydarsayar.communesky.ui.theme.RainAccent
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.today.rememberStrings
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val BigValue = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp)

/** Değerleri kullanıcının birimi ve diliyle yazar. Değerler hava verisinin birimlerinde gelir (bkz. [MetricData]). */
class MetricFormatter(private val context: Context, private val settings: AppSettings, private val locale: Locale) {
    private val whole = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val oneDecimal = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }

    private fun temperature(celsius: Double) = context.getString(R.string.temperature_value, settings.temperatureUnit.fromCelsius(celsius).roundToInt())
    private fun wind(kmh: Double) = settings.windUnit.fromKmh(kmh).roundToInt()
    private fun percent(value: Double) = context.getString(R.string.humidity_value, value.roundToInt())
    private val windUnit get() = context.getString(settings.windUnit.labelRes)

    fun duration(minutes: Double): String {
        val total = minutes.roundToInt().coerceAtLeast(0)
        return context.getString(R.string.detail_duration, total / 60, total % 60)
    }

    /** Birimiyle tam değer: "18 km/sa", "1015 hPa", "2,4 mm". */
    fun value(metric: Metric, value: Double): String = when (metric) {
        Metric.FeelsLike -> temperature(value)
        Metric.Wind -> context.getString(R.string.detail_value_unit, wind(value).toString(), windUnit)
        Metric.Humidity, Metric.Cloud -> percent(value)
        Metric.Uv -> oneDecimal.format(value)
        Metric.Visibility -> context.getString(R.string.visibility_value, (if (value >= 10) whole else oneDecimal).format(value))
        Metric.Pressure -> context.getString(R.string.pressure_value, value.roundToInt())
        Metric.Precipitation -> context.getString(R.string.detail_mm, oneDecimal.format(value))
        Metric.Sun -> duration(value)
    }

    /** Dar yerler için birimsiz kısa değer (7 günlük özet, eksen). */
    fun short(metric: Metric, value: Double): String = when (metric) {
        Metric.FeelsLike -> temperature(value)
        Metric.Wind -> wind(value).toString()
        Metric.Humidity, Metric.Cloud -> percent(value)
        Metric.Uv -> whole.format(value)
        Metric.Visibility -> whole.format(value)
        Metric.Pressure -> value.roundToInt().toString()
        Metric.Precipitation -> oneDecimal.format(value)
        Metric.Sun -> context.getString(R.string.detail_duration, value.roundToInt() / 60, value.roundToInt() % 60)
    }

    /** Grafikte seçilen saatin ikinci değeri: "hamle 40 km/sa", "ihtimal %70". */
    fun secondary(metric: Metric, value: Double): String? = when (metric) {
        Metric.FeelsLike -> context.getString(R.string.detail_secondary_actual, temperature(value))
        Metric.Wind -> context.getString(R.string.detail_secondary_gust, context.getString(R.string.detail_value_unit, wind(value).toString(), windUnit))
        Metric.Humidity -> context.getString(R.string.detail_secondary_dew, temperature(value))
        Metric.Precipitation -> context.getString(R.string.detail_secondary_chance, percent(value))
        else -> null
    }

    /** Büyük yazılan şimdiki değer ve yanındaki küçük birim. */
    fun headline(detail: MetricDetail): Pair<String, String?> {
        val value = detail.current ?: return "–" to null
        return when (detail.metric) {
            Metric.Wind -> wind(value).toString() to windUnit
            // Yağışta büyük değer bugünün yağış ihtimalidir.
            Metric.Precipitation -> percent(value) to null
            Metric.Uv -> whole.format(value) to null
            else -> value(detail.metric, value) to null
        }
    }

    fun gust(kmh: Double): String = context.getString(R.string.detail_gust, wind(kmh), windUnit)

    fun temperatureOnly(celsius: Double): Int = settings.temperatureUnit.fromCelsius(celsius).roundToInt()
}

@Composable
fun rememberMetricFormatter(): MetricFormatter {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val locale = currentLocale()
    return remember(context, settings, locale) { MetricFormatter(context, settings, locale) }
}

/**
 * Ayrıntı sayfaları: üstte yatay sekmeler, altında seçilen bilginin sayfası. Sayfalar arasında sekmeye dokunarak
 * ya da yana kaydırarak geçilir. [extraTabs]: hava kalitesi ve ay gibi kendi düzeni olan sayfalar.
 */
@Composable
fun DetailScreen(weather: WeatherSnapshot?, initial: DetailTab, title: String?, onBack: () -> Unit) {
    val tabs = DetailTab.entries
    val pager = rememberPagerState(initialPage = tabs.indexOf(initial).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    val tabList = rememberLazyListState()
    // Seçilen sekme görünür kalsın.
    LaunchedEffect(pager.currentPage) { tabList.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title ?: stringResource(R.string.my_location), onBack)
        LazyRow(
            state = tabList,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs.size) { index ->
                TabChip(stringResource(tabs[index].label), selected = pager.currentPage == index) {
                    scope.launch { pager.animateScrollToPage(index) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (weather == null) return@Column
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { tabs[it].name }) { index ->
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val metric = tabs[index].metric
                if (metric != null) MetricPage(metric, weather)
            }
        }
    }
}

/** Sayfaların sırası ve sekme adları. */
enum class DetailTab(val metric: Metric?) {
    FeelsLike(Metric.FeelsLike),
    Wind(Metric.Wind),
    Humidity(Metric.Humidity),
    Uv(Metric.Uv),
    Visibility(Metric.Visibility),
    Pressure(Metric.Pressure),
    Cloud(Metric.Cloud),
    Precipitation(Metric.Precipitation),
    Sun(Metric.Sun),
    ;

    val label: Int get() = MetricText.tab(metric!!)

    companion object {
        fun of(metric: Metric): DetailTab = entries.first { it.metric == metric }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color(0xFF14284A) else TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Bir bilginin sayfası: şimdiki değer, bugünün grafiği, dünle karşılaştırma, 7 gün, "Bu nedir?". */
@Composable
private fun MetricPage(metric: Metric, weather: WeatherSnapshot) {
    val forecast = weather.forecast
    val now = remember(weather) { forecast.localNow() }
    // Veri bir kez hazırlanır; kaydırırken ya da grafiğe dokunurken yeniden hesaplanmaz.
    val detail = remember(weather, metric) { MetricData.of(metric, forecast, now) }
    val formatter = rememberMetricFormatter()
    val strings = rememberStrings()
    val today = remember(weather) { forecast.today(now) }

    // 1. Büyük değer ve durum.
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(MetricText.title(metric))) {
        val (headline, unit) = formatter.headline(detail)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(headline, color = TextPrimary, style = if (headline.length > 6) BigValue.copy(fontSize = 40.sp, lineHeight = 46.sp) else BigValue)
            if (unit != null) {
                Spacer(Modifier.width(8.dp))
                Text(unit, color = TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 9.dp))
            }
        }
        val status = remember(detail, strings) { MetricText.status(detail, strings) }
        if (status != null) {
            Spacer(Modifier.height(4.dp))
            Text(status, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        val note = headerNote(detail, weather, formatter)
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(note, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // 2. Bugünün grafiği (güneş sayfasında güneşin yolu).
    val chart = detail.chart
    if (metric == Metric.Sun) {
        val sunrise = today?.sunrise
        val sunset = today?.sunset
        if (sunrise != null && sunset != null) SunTile(sunrise, sunset, now, Modifier.fillMaxWidth())
    } else if (chart != null) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.today)) {
            var selected by remember(chart) { mutableStateOf<Int?>(null) }
            val hour = rememberHourFormatter()
            val time = rememberTimeFormatter()
            val point = selected?.let { chart.points.getOrNull(it) }
            val readout = if (point?.value != null) {
                val parts = listOfNotNull(formatter.value(metric, point.value), point.secondary?.let { formatter.secondary(metric, it) })
                stringResource(R.string.detail_readout, point.time.format(time), parts.joinToString(" · "))
            } else {
                stringResource(R.string.detail_chart_hint)
            }
            Text(readout, color = if (point != null) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            MetricChart(
                chart = chart,
                selected = selected,
                onSelect = { selected = it },
                hourLabel = { LocalTime.of(it, 0).format(hour) },
                axisLabel = { formatter.short(metric, it) },
                color = chartColor(metric),
                showDirections = metric == Metric.Wind,
                // Çiy noktası (°) nemle (%) aynı ölçekte çizilemez; yalnız seçilen saatin yazısında görünür.
                showSecondary = metric != Metric.Humidity,
            )
        }
    }

    // 3. Dünle karşılaştırma.
    val compared = detail.comparison
    val sentence = remember(detail, strings) { MetricText.comparison(detail, strings) }
    if (compared != null && sentence != null) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.detail_vs_yesterday)) {
            ComparisonBars(metric, compared, formatter)
            Spacer(Modifier.height(10.dp))
            Text(sentence, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(MetricText.compareCaption(metric)), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }

    // 4. Yedi günlük özet.
    if (detail.week.any { it.value != null }) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.detail_week)) {
            WeekRow(metric, detail.week, now.toLocalDate(), weather, formatter)
        }
    }

    // 5. Bu nedir?
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.detail_what)) {
        Text(stringResource(MetricText.what(metric)), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun chartColor(metric: Metric): Color = when (metric) {
    Metric.FeelsLike -> Color(0xFFFFC27A)
    Metric.Uv -> Color(0xFFFACC15)
    Metric.Pressure -> Color(0xFFE2E8F0)
    Metric.Cloud, Metric.Visibility -> Color(0xFFCBD5E1)
    else -> RainAccent
}

/** Büyük değerin altındaki ek bilgi: gerçek sıcaklık, yön ve hamle, çiy noktası, bugünün en yükseği… */
@Composable
private fun headerNote(detail: MetricDetail, weather: WeatherSnapshot, formatter: MetricFormatter): String? {
    val current = weather.forecast.current
    val today = weather.forecast.today(weather.forecast.localNow())
    val decimal = com.alihaydarsayar.communesky.ui.common.rememberDecimalFormat()
    val time = rememberTimeFormatter()
    return when (detail.metric) {
        Metric.FeelsLike -> stringResource(R.string.detail_actual, formatter.temperatureOnly(current.temperature))
        Metric.Wind -> {
            val points = stringArrayResource(R.array.compass_points)
            listOfNotNull(
                detail.direction?.let { stringResource(R.string.wind_from, points[((it + 22.5) / 45).toInt() % 8]) },
                detail.currentSecondary?.let { formatter.gust(it) },
            ).joinToString(" · ").ifEmpty { null }
        }
        Metric.Humidity -> detail.currentSecondary?.let { stringResource(R.string.dew_point, formatter.temperatureOnly(it)) }
        Metric.Uv -> detail.currentSecondary?.let { stringResource(R.string.uv_max_today, decimal.format(it)) }
        Metric.Precipitation -> {
            val sum = detail.currentSecondary ?: 0.0
            if (sum >= 0.1) stringResource(R.string.precipitation_expected, decimal.format(sum)) else stringResource(R.string.precipitation_none)
        }
        Metric.Sun -> {
            val sunrise = today?.sunrise ?: return null
            val sunset = today.sunset ?: return null
            val night = 24 * 60 - (detail.current ?: return null)
            listOf(
                stringResource(R.string.widget_sunrise_at, sunrise.format(time)),
                stringResource(R.string.widget_sunset_at, sunset.format(time)),
                stringResource(R.string.detail_night_length, formatter.duration(night)),
            ).joinToString(" · ")
        }
        else -> null
    }
}

/** Bugün ve dün yan yana iki çubuk; uzun olan tam genişlikte. */
@Composable
private fun ComparisonBars(metric: Metric, compared: Compared, formatter: MetricFormatter) {
    // Basınç ve sıcaklık sıfırdan başlamaz: çubuklar aradaki farkı gösterecek biçimde ortak bir tabandan ölçülür.
    val base = when (metric) {
        Metric.Pressure -> minOf(compared.today, compared.yesterday) - 10
        Metric.FeelsLike -> minOf(compared.today, compared.yesterday, 0.0) - 5
        else -> 0.0
    }
    val top = maxOf(compared.today, compared.yesterday) - base
    @Composable
    fun bar(label: String, value: Double, color: Color) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
            Box(Modifier.weight(1f)) {
                val fraction = if (top <= 0) 0f else ((value - base) / top).toFloat().coerceIn(0.02f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Text(
                formatter.value(metric, value),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 10.dp).width(86.dp),
            )
        }
    }
    bar(stringResource(R.string.today), compared.today, chartColor(metric))
    bar(stringResource(R.string.detail_yesterday), compared.yesterday, Color.White.copy(alpha = 0.35f))
}

/** Yedi gün yan yana: gün adı ve değer; hissedilende en yüksek ve en düşük, güneşte doğuş ve batış. */
@Composable
private fun WeekRow(metric: Metric, week: List<DayValue>, today: LocalDate, weather: WeatherSnapshot, formatter: MetricFormatter) {
    val locale = currentLocale()
    val time = rememberTimeFormatter()
    Row(Modifier.fillMaxWidth()) {
        for (day in week) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val name = if (day.date == today) {
                    stringResource(R.string.today)
                } else {
                    day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
                }
                Text(name, color = TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                val small = MaterialTheme.typography.labelMedium
                if (metric == Metric.Sun) {
                    val daily = weather.forecast.daily.firstOrNull { it.date == day.date }
                    Text(daily?.sunrise?.format(time) ?: "–", color = TextPrimary, style = small, maxLines = 1)
                    Text(daily?.sunset?.format(time) ?: "–", color = TextSecondary, style = small, maxLines = 1)
                } else {
                    Text(
                        day.value?.let { formatter.short(metric, it) } ?: "–",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    val second = when (metric) {
                        Metric.FeelsLike -> day.low?.let { formatter.short(metric, it) }
                        Metric.Precipitation -> day.secondary?.let { stringResource(R.string.humidity_value, it.roundToInt()) }
                        else -> null
                    }
                    if (second != null) Text(second, color = TextSecondary, style = small, maxLines = 1)
                }
            }
        }
    }
}
