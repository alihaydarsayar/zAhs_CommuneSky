package com.alihaydarsayar.communesky.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.DayForecastRepository
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.PrecipitationOutlook
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.detail.Chart
import com.alihaydarsayar.communesky.model.detail.DayData
import com.alihaydarsayar.communesky.model.detail.DayDetail
import com.alihaydarsayar.communesky.model.detail.Metric
import com.alihaydarsayar.communesky.model.detail.MetricText
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.WeatherIcon
import com.alihaydarsayar.communesky.ui.common.asTemperature
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.rememberDecimalFormat
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.home.shortNameRes
import com.alihaydarsayar.communesky.ui.theme.Outfit
import com.alihaydarsayar.communesky.ui.theme.RainAccent
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import javax.inject.Inject

/** Bir günün saatlik verisinin durumu: bekleniyor, geldi ya da alınamadı (internet yok). */
sealed interface DayHours {
    data object Loading : DayHours
    data class Ready(val hours: List<HourlyForecast>, val source: ForecastSource) : DayHours
    data object Unavailable : DayHours
}

/** Gün sayfalarının, telefonda saatlik verisi olmayan günler için istediği veriler. */
@HiltViewModel
class DayViewModel @Inject constructor(private val repository: DayForecastRepository) : ViewModel() {
    private val _days = MutableStateFlow<Map<LocalDate, DayHours>>(emptyMap())
    val days: StateFlow<Map<LocalDate, DayHours>> = _days

    /** O günün saatlerini ister; daha önce geldiyse ya da istek sürüyorsa bir şey yapmaz. */
    fun load(weather: WeatherSnapshot, date: LocalDate) {
        val known = _days.value[date]
        if (known is DayHours.Loading || known is DayHours.Ready) return
        _days.update { it + (date to DayHours.Loading) }
        viewModelScope.launch {
            val day = repository.day(weather, date)
            _days.update { it + (date to (day?.let { d -> DayHours.Ready(d.hours, d.source) } ?: DayHours.Unavailable)) }
        }
    }
}

private val DayHigh = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp)
private val DayLow = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 36.sp)

/**
 * Gün sayfası: 7 günlük karttaki bir günün ayrıntısı. Üstte günler, altında seçilen günün sayfası; günler
 * arasında sekmeye dokunarak ya da yana kaydırarak geçilir. Günlük bilgiler hemen görünür; saat saat
 * grafikler veri gelince dolar. [initialHour]: saatlik karttan açıldıysa seçili gelecek saat.
 */
@Composable
fun DayScreen(
    weather: WeatherSnapshot?,
    initialDate: LocalDate?,
    initialHour: Int?,
    title: String?,
    onBack: () -> Unit,
    viewModel: DayViewModel = hiltViewModel(),
) {
    val now = remember(weather) { weather?.forecast?.localNow() }
    val days = remember(weather) { if (weather == null || now == null) emptyList() else DayData.days(weather.forecast, now) }
    val pager = rememberPagerState(initialPage = days.indexOfFirst { it.date == initialDate }.coerceAtLeast(0)) { days.size }
    val scope = rememberCoroutineScope()
    val tabList = rememberLazyListState()
    val locale = currentLocale()
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
            items(days.size) { index ->
                val date = days[index].date
                val label = if (index == 0) {
                    stringResource(R.string.today_short)
                } else {
                    date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) } + " " + date.dayOfMonth
                }
                TabChip(label, selected = pager.currentPage == index) { scope.launch { pager.animateScrollToPage(index) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (weather == null || now == null || days.isEmpty()) return@Column
        val fetched by viewModel.days.collectAsStateWithLifecycle()
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { days[it].date.toString() }) { index ->
            val day = days[index]
            // Bugün ve yarın eldeki saatlerden; sonraki günler sayfa görünür olunca istenir.
            val local = remember(weather, day.date) { DayData.localHours(weather.forecast, day.date) }
            if (local == null) LaunchedEffect(weather.placeId, day.date) { viewModel.load(weather, day.date) }
            val state: DayHours = if (local != null) {
                DayHours.Ready(local, weather.sources?.forecast ?: ForecastSource.OpenMeteo)
            } else {
                fetched[day.date] ?: DayHours.Loading
            }
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DayPage(weather, day, state, now, initialHour.takeIf { day.date == initialDate })
            }
        }
    }
}

@Composable
private fun DayPage(weather: WeatherSnapshot, day: DailyForecast, state: DayHours, now: java.time.LocalDateTime, initialHour: Int?) {
    val hours = (state as? DayHours.Ready)?.hours.orEmpty()
    // Veri bir kez hazırlanır; kaydırırken ya da grafiğe dokunurken yeniden hesaplanmaz.
    val detail = remember(day, hours, now.toLocalDate()) { DayData.of(day, hours, now) }
    val formatter = rememberMetricFormatter()
    val decimal = rememberDecimalFormat()
    val locale = currentLocale()
    val time = rememberTimeFormatter()
    val total = detail.precipitationTotal ?: 0.0

    // 1. Günün simgesi, en yüksek ve en düşük (7 günlük karttaki satırla aynı değerler), kısa cümle.
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
    GlassCard(Modifier.fillMaxWidth(), title = day.date.format(dateFormatter).replaceFirstChar { it.titlecase(locale) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.temperature_value, day.maxTemperature.asTemperature()), color = TextPrimary, style = DayHigh)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.temperature_value, day.minTemperature.asTemperature()),
                    color = TextSecondary,
                    style = DayLow,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            WeatherIcon(condition = day.condition, isNight = false, size = 60.dp, contentDescription = null)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(weatherDescriptionRes(day.weatherCode)), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val rain = when {
            total >= PrecipitationOutlook.EXPECTED_MM -> stringResource(R.string.day_precipitation_expected, decimal.format(total))
            day.precipitationOutlook == PrecipitationOutlook.Possible -> stringResource(R.string.precipitation_possible)
            else -> stringResource(R.string.day_precipitation_none)
        }
        Text(rain, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        val feelsHigh = detail.feelsHigh
        val feelsLow = detail.feelsLow
        if (feelsHigh != null && feelsLow != null) {
            Text(
                stringResource(R.string.day_feels_like, feelsHigh.asTemperature(), feelsLow.asTemperature()),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    // 2–4. Saat saat grafikler: sıcaklık, yağış, rüzgâr. Bir grafikte seçilen saat hepsinde seçilir.
    var selected by remember(day.date, hours) { mutableStateOf(DayData.indexOfHour(hours, initialHour)) }
    when {
        state is DayHours.Loading -> GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.day_hourly_loading), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        state is DayHours.Unavailable -> GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.day_hourly_unavailable), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        else -> {
            detail.temperature?.let { chart ->
                DayChartCard(stringResource(R.string.day_temperature), chart, selected, { selected = it }, Color(0xFFFFC27A), formatter, Metric.FeelsLike) { point ->
                    listOfNotNull(
                        point.value?.let { formatter.value(Metric.FeelsLike, it) },
                        point.secondary?.let { stringResource(R.string.day_secondary_feels, formatter.value(Metric.FeelsLike, it)) },
                    )
                }
            }
            detail.precipitation?.let { chart ->
                val summary = stringResource(R.string.day_rain_summary, day.precipitationProbability, decimal.format(total))
                DayChartCard(stringResource(MetricText.title(Metric.Precipitation)), chart, selected, { selected = it }, RainAccent, formatter, Metric.Precipitation, summary) { point ->
                    listOfNotNull(
                        point.value?.let { formatter.value(Metric.Precipitation, it) },
                        point.secondary?.let { formatter.secondary(Metric.Precipitation, it) },
                    )
                }
            }
            detail.wind?.let { chart ->
                DayChartCard(stringResource(MetricText.title(Metric.Wind)), chart, selected, { selected = it }, RainAccent, formatter, Metric.Wind, showDirections = true) { point ->
                    listOfNotNull(
                        point.value?.let { formatter.value(Metric.Wind, it) },
                        point.secondary?.let { formatter.secondary(Metric.Wind, it) },
                    )
                }
            }
        }
    }

    // 5. Günün öteki bilgileri.
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.day_details)) {
        val rows = buildList {
            detail.windMax?.let { wind ->
                val gust = detail.gustMax?.let { " · " + formatter.gust(it) }.orEmpty()
                add(stringResource(R.string.wind) to formatter.value(Metric.Wind, wind) + gust)
            }
            val points = stringArrayResource(R.array.compass_points)
            day.windDirection?.let { add(stringResource(R.string.day_wind_direction) to points[((it + 22.5) / 45).toInt() % 8]) }
            detail.uvMax?.let { add(stringResource(R.string.day_uv_max) to decimal.format(it)) }
            val low = detail.humidityLow
            val high = detail.humidityHigh
            if (low != null && high != null) {
                val value = if (low == high) stringResource(R.string.humidity_value, low) else stringResource(R.string.day_humidity_range, low, high)
                add(stringResource(R.string.humidity) to value)
            }
            day.sunrise?.let { add(stringResource(R.string.sunrise) to it.format(time)) }
            day.sunset?.let { add(stringResource(R.string.sunset) to it.format(time)) }
            detail.dayLengthMinutes?.let { add(stringResource(R.string.day_length) to formatter.duration(it)) }
        }
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    // 6. Kaynak: günlük değerler ana ekrandaki kaynaktan; saatler başka bir kaynaktansa o da yazılır.
    val sources = weather.sources
    if (sources != null) {
        val daily = stringResource(sources.forecast.shortNameRes)
        val line = if (sources.forecast == sources.precipitation) {
            stringResource(R.string.source_line_single, daily)
        } else {
            stringResource(R.string.source_line, daily, stringResource(sources.precipitation.shortNameRes))
        }
        val hourly = (state as? DayHours.Ready)?.source?.takeIf { it != sources.forecast }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(line, color = TextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            if (hourly != null) {
                Text(
                    stringResource(R.string.day_source_hourly, stringResource(hourly.shortNameRes)),
                    color = TextSecondary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Bir grafiğin kartı: üstte seçilen saatin değeri (ya da özet), altında grafik. Ayrıntı sayfalarındaki kartla aynı düzen. */
@Composable
private fun DayChartCard(
    title: String,
    chart: Chart,
    selected: Int?,
    onSelect: (Int) -> Unit,
    color: Color,
    formatter: MetricFormatter,
    metric: Metric,
    summary: String? = null,
    showDirections: Boolean = false,
    readout: @Composable (com.alihaydarsayar.communesky.model.detail.ChartPoint) -> List<String>,
) {
    GlassCard(Modifier.fillMaxWidth(), title = title) {
        val hour = rememberHourFormatter()
        val time = rememberTimeFormatter()
        val point = selected?.let { chart.points.getOrNull(it) }
        val parts = point?.let { readout(it) }.orEmpty()
        val text = if (point != null && parts.isNotEmpty()) {
            stringResource(R.string.detail_readout, point.time.format(time), parts.joinToString(" · "))
        } else {
            summary ?: stringResource(R.string.detail_chart_hint)
        }
        Text(text, color = if (point != null && parts.isNotEmpty()) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        MetricChart(
            chart = chart,
            selected = selected,
            onSelect = onSelect,
            hourLabel = { LocalTime.of(it, 0).format(hour) },
            axisLabel = { formatter.short(metric, it) },
            color = color,
            showDirections = showDirections,
        )
    }
}
