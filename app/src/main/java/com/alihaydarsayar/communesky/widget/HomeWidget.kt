package com.alihaydarsayar.communesky.widget

import android.content.Context
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
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
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/** Ev + Bulunduğum yer widget'ının gösterecekleri. */
sealed interface HomeWidgetState {
    /** Evdesin: tek satır. */
    data class AtHome(val home: WidgetPlaceData) : HomeWidgetState

    /** Evden uzaktasın: üstte bulunduğun yer, altta Ev. */
    data class Away(val here: WidgetPlaceData, val home: WidgetPlaceData) : HomeWidgetState

    /** Ev seçilmemiş ya da cihaz konumu yok: tek yer ve bir ipucu. */
    data class Single(val place: WidgetPlaceData, val noHome: Boolean) : HomeWidgetState

    data object Empty : HomeWidgetState

    companion object {
        /** Saf mantık; test edilir. "Evde" kararı konumun hata payıyla verilir (bkz. HomeDetection). */
        fun of(device: WeatherSnapshot?, home: WidgetPlaceData?): HomeWidgetState {
            val here = device?.takeIf { it.isCurrentLocation }?.let { WidgetPlaceData(it, isHome = false) }
            return when {
                here != null && home != null -> {
                    val atHome = HomeDetection.isAtHome(
                        device = GeoPoint(here.snapshot.city.latitude, here.snapshot.city.longitude),
                        accuracyMeters = here.snapshot.accuracyMeters,
                        home = GeoPoint(home.snapshot.city.latitude, home.snapshot.city.longitude),
                    )
                    if (atHome) AtHome(home) else Away(here, home)
                }
                home != null -> Single(home, noHome = false)
                here != null -> Single(here, noHome = true)
                // İzin yok, Ev yok: yedek şehri göster.
                device != null -> Single(WidgetPlaceData(device, isHome = false), noHome = true)
                else -> Empty
            }
        }
    }
}

/**
 * Ev + Bulunduğum yer widget'ı: iki yeri alt alta gösterir. Evdeysen iki satır aynı havayı
 * tekrar etmesin diye tek satıra düşer ve bunu "Evdesin" diye belli eder.
 */
class HomeWidget(private val previewConfig: WidgetConfig? = null) : GlanceAppWidget() {

    companion object {
        val Compact = DpSize(250.dp, 110.dp)
        val Roomy = DpSize(250.dp, 160.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Roomy))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val configFlow = widgetConfigFlow(context, id, previewConfig)
        val loader = WidgetDataLoader(context)
        val stateFlow = loader.homeState()
        val initialConfig = configFlow.first()
        val initialState = stateFlow.first()
        val initialSettings = loader.settings.first()
        provideContent {
            val config by configFlow.collectAsState(initialConfig)
            val state by stateFlow.collectAsState(initialState)
            val settings by loader.settings.collectAsState(initialSettings)
            WidgetTheme(settings, config.background) { HomeWidgetContent(state, config.background) }
        }
    }
}

@Composable
private fun HomeWidgetContent(state: HomeWidgetState, background: WidgetBackground) {
    val context = LocalContext.current
    val main = when (state) {
        is HomeWidgetState.AtHome -> state.home
        is HomeWidgetState.Away -> state.here
        is HomeWidgetState.Single -> state.place
        HomeWidgetState.Empty -> null
    }
    val now = main?.snapshot?.forecast?.localNow()
    val theme = if (main != null && now != null) main.snapshot.currentScene(now).theme else SkyTheme.ClearNight
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(background, theme)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            HomeWidgetState.Empty -> EmptyContent()
            is HomeWidgetState.AtHome -> {
                PlaceLine(state.home, label = context.getString(R.string.widget_at_home, state.home.snapshot.displayName(context)))
            }
            is HomeWidgetState.Away -> {
                PlaceLine(state.here)
                Spacer(GlanceModifier.height(if (LocalSize.current.height >= HomeWidget.Roomy.height) 10.dp else 6.dp))
                Divider()
                Spacer(GlanceModifier.height(if (LocalSize.current.height >= HomeWidget.Roomy.height) 10.dp else 6.dp))
                PlaceLine(state.home)
            }
            is HomeWidgetState.Single -> {
                PlaceLine(state.place)
                if (state.noHome) {
                    Spacer(GlanceModifier.height(6.dp))
                    WText(
                        context.getString(R.string.widget_no_home),
                        11.sp,
                        color = WidgetWhiteSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** Bir yerin tek satırı: ad ve durum solda, ikon ve sıcaklık sağda. */
@Composable
private fun PlaceLine(data: WidgetPlaceData, label: String? = null) {
    val context = LocalContext.current
    val forecast = data.snapshot.forecast
    val current = forecast.current
    val now: LocalDateTime = forecast.localNow()
    val today = forecast.today(now)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            PlaceName(data, 14.sp, label)
            val detail = buildString {
                append(context.getString(weatherDescriptionRes(current.weatherCode)))
                if (today != null) append("  ·  ").append(highLowText(context, today.maxTemperature, today.minTemperature))
            }
            WText(detail, 12.sp, color = WidgetWhiteSecondary)
        }
        Image(
            ImageProvider(current.condition.iconRes(!current.isDay)),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        WText(temperatureText(current.temperature), 30.sp, font = WidgetFont.Light, align = TextAlign.End)
    }
}
