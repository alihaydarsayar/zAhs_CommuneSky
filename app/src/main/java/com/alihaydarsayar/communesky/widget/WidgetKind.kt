package com.alihaydarsayar.communesky.widget

import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.alihaydarsayar.communesky.R
import kotlinx.serialization.Serializable

/**
 * Widget seçicide görünen 9 ana widget. Her birinin içinde birden fazla stil ([WidgetStyleId])
 * var: liste sade kalır, toplam 36 tasarım. Stil, ekleme ekranında seçilir ve sonradan
 * değiştirilebilir.
 *
 * [sizes]: Glance'in boyuta duyarlı (SizeMode.Responsive) düzeni için kırılma noktaları. Widget
 * küçültülünce daha az, büyütülünce daha çok bilgi gösterilir.
 */
enum class WidgetKind(
    @param:StringRes val nameRes: Int,
    val sizes: Set<DpSize>,
    /** Ekleme ekranında "Gösterilecek yer" seçilir mi? (Ev ve konum, Yerlerim kendi yerlerini bilir.) */
    val choosesPlace: Boolean,
    /** Ekleme ekranında açılıp kapanabilen içerikler ve varsayılan olarak açık olanlar. */
    val contents: List<WidgetContent>,
    val defaultContents: Set<WidgetContent>,
) {
    HomeLocation(
        R.string.widget_kind_home,
        setOf(size(180, 40), size(250, 40), size(250, 100), size(250, 150), size(300, 190)),
        choosesPlace = false,
        contents = listOf(WidgetContent.Hourly, WidgetContent.RainAlert, WidgetContent.HighLow, WidgetContent.FeelsLike, WidgetContent.Clock),
        defaultContents = setOf(WidgetContent.Hourly, WidgetContent.RainAlert, WidgetContent.HighLow, WidgetContent.FeelsLike),
    ),
    Clock(
        R.string.widget_kind_clock,
        setOf(size(110, 40), size(110, 100), size(150, 150), size(180, 40), size(250, 40), size(250, 100), size(300, 150)),
        choosesPlace = true,
        contents = listOf(
            WidgetContent.PlaceName, WidgetContent.Weather, WidgetContent.WeatherIcon, WidgetContent.Temperature,
            WidgetContent.Condition, WidgetContent.HighLow, WidgetContent.RainAlert,
        ),
        defaultContents = setOf(
            WidgetContent.PlaceName, WidgetContent.Weather, WidgetContent.WeatherIcon, WidgetContent.Temperature,
            WidgetContent.Condition, WidgetContent.HighLow, WidgetContent.RainAlert,
        ),
    ),
    Hourly(
        R.string.widget_kind_hourly,
        setOf(size(180, 40), size(250, 40), size(250, 100), size(250, 150)),
        choosesPlace = true,
        contents = listOf(WidgetContent.Condition, WidgetContent.HighLow, WidgetContent.Precipitation),
        defaultContents = setOf(WidgetContent.Condition, WidgetContent.HighLow, WidgetContent.Precipitation),
    ),
    Weekly(
        R.string.widget_kind_weekly,
        setOf(size(180, 100), size(250, 100), size(250, 180), size(300, 230)),
        choosesPlace = true,
        contents = listOf(WidgetContent.Current, WidgetContent.Precipitation),
        defaultContents = setOf(WidgetContent.Current, WidgetContent.Precipitation),
    ),
    Small(
        R.string.widget_kind_small,
        setOf(size(100, 40), size(100, 100), size(160, 160)),
        choosesPlace = true,
        contents = listOf(WidgetContent.PlaceName, WidgetContent.Condition, WidgetContent.HighLow),
        defaultContents = setOf(WidgetContent.PlaceName, WidgetContent.Condition, WidgetContent.HighLow),
    ),
    Text(
        R.string.widget_kind_text,
        setOf(size(100, 40), size(180, 40), size(250, 40), size(180, 100)),
        choosesPlace = true,
        contents = listOf(WidgetContent.PlaceName, WidgetContent.Condition, WidgetContent.RainAlert),
        defaultContents = setOf(WidgetContent.PlaceName, WidgetContent.Condition, WidgetContent.RainAlert),
    ),
    Places(
        R.string.widget_kind_places,
        setOf(size(180, 40), size(250, 40), size(250, 100), size(250, 180)),
        choosesPlace = false,
        contents = listOf(WidgetContent.HighLow, WidgetContent.IncludeLocation),
        defaultContents = setOf(WidgetContent.HighLow, WidgetContent.IncludeLocation),
    ),
    Precipitation(
        R.string.widget_kind_precipitation,
        setOf(size(180, 40), size(250, 40), size(250, 100)),
        choosesPlace = true,
        contents = listOf(WidgetContent.PlaceName),
        defaultContents = setOf(WidgetContent.PlaceName),
    ),
    Details(
        R.string.widget_kind_details,
        setOf(size(100, 100), size(180, 100), size(250, 100), size(250, 150), size(250, 190)),
        choosesPlace = true,
        contents = listOf(WidgetContent.TileNotes),
        defaultContents = setOf(WidgetContent.TileNotes),
    );

    val styles: List<WidgetStyleId> get() = WidgetStyleId.entries.filter { it.kind == this }

    val defaultStyle: WidgetStyleId get() = styles.first()

    /** Seçicideki alıcı sınıfı; eski sürümden kalan widget'lar silinmesin diye adlar korundu. */
    val receiverClass: Class<out GlanceAppWidgetReceiver>
        get() = when (this) {
            HomeLocation -> HomeWidgetReceiver::class.java
            Clock -> ClockWidgetReceiver::class.java
            Hourly -> MediumWeatherWidgetReceiver::class.java
            Weekly -> LargeWeatherWidgetReceiver::class.java
            Small -> SmallWeatherWidgetReceiver::class.java
            Text -> TextWidgetReceiver::class.java
            Places -> PlacesWidgetReceiver::class.java
            Precipitation -> PrecipitationWidgetReceiver::class.java
            Details -> DetailsWidgetReceiver::class.java
        }

    /** Bu türün widget'ı; [preview] verilirse kayıtlı ayar yerine o kullanılır (ekleme ekranı, testler). */
    fun widget(preview: WidgetConfig? = null, input: WidgetInput? = null): CommuneSkyWidget = when (this) {
        HomeLocation -> HomeLocationWidget(preview, input)
        Clock -> ClockWidget(preview, input)
        Hourly -> HourlyWidget(preview, input)
        Weekly -> WeeklyWidget(preview, input)
        Small -> SmallWidget(preview, input)
        Text -> TextWidget(preview, input)
        Places -> PlacesWidget(preview, input)
        Precipitation -> PrecipitationWidget(preview, input)
        Details -> DetailsWidget(preview, input)
    }

    companion object {
        fun fromReceiver(className: String?): WidgetKind? = entries.firstOrNull { it.receiverClass.name == className }
    }
}

private fun size(width: Int, height: Int) = DpSize(width.dp, height.dp)

/** Ekleme ekranındaki "İçerik" anahtarları. Hangisinin görüneceği widget türüne bağlı. */
@Serializable
enum class WidgetContent(@param:StringRes val labelRes: Int) {
    Hourly(R.string.widget_content_hourly),
    RainAlert(R.string.widget_content_rain_alert),
    HighLow(R.string.widget_content_high_low),
    FeelsLike(R.string.widget_content_feels_like),
    Clock(R.string.widget_content_clock),
    PlaceName(R.string.widget_content_place_name),
    Weather(R.string.widget_content_weather),
    Condition(R.string.widget_content_condition),
    Precipitation(R.string.widget_content_precipitation),
    Current(R.string.widget_content_current),
    IncludeLocation(R.string.widget_content_include_location),
    TileNotes(R.string.widget_content_tile_notes),

    /** SS saatinde hava alanının parçaları ayrı ayrı açılıp kapanır. */
    WeatherIcon(R.string.widget_content_weather_icon),
    Temperature(R.string.widget_content_temperature),
}

/**
 * Bir widget'ın stilleri. [previewSize]: seçici görselinin ve küçük önizlemelerin boyutu (dp);
 * [background], [colorTheme]: stil seçilince gelen varsayılan görünüm (kullanıcı o ayara
 * dokunmadıysa). 1.4'te ortak görünüm koyu düz zemin; Küçük cam, Yazı arka plansız.
 */
enum class WidgetStyleId(
    val kind: WidgetKind,
    @param:StringRes val nameRes: Int,
    val previewSize: DpSize,
    val background: BackgroundKind = BackgroundKind.Solid,
    val colorTheme: ColorTheme = ColorTheme.Dark,
) {
    HomeSmart(WidgetKind.HomeLocation, R.string.widget_style_home_smart, size(320, 170)),
    HomeRow(WidgetKind.HomeLocation, R.string.widget_style_home_row, size(320, 86)),
    HomeList(WidgetKind.HomeLocation, R.string.widget_style_home_list, size(320, 150)),

    ClockRadial(WidgetKind.Clock, R.string.widget_style_clock_radial, size(340, 150)),
    ClockAnalog(WidgetKind.Clock, R.string.widget_style_clock_analog, size(170, 170), BackgroundKind.Transparent),
    ClockField(WidgetKind.Clock, R.string.widget_style_clock_field, size(170, 170), BackgroundKind.Transparent),
    ClockRing(WidgetKind.Clock, R.string.widget_style_clock_ring, size(170, 170), BackgroundKind.Transparent),
    ClockSS(WidgetKind.Clock, R.string.widget_style_clock_ss, size(170, 170), BackgroundKind.Transparent),
    ClockWeather(WidgetKind.Clock, R.string.widget_style_clock_weather, size(320, 170)),
    ClockBig(WidgetKind.Clock, R.string.widget_style_clock_big, size(340, 170), BackgroundKind.Transparent),
    ClockSide(WidgetKind.Clock, R.string.widget_style_clock_side, size(340, 170), BackgroundKind.Transparent),
    ClockLine(WidgetKind.Clock, R.string.widget_style_clock_line, size(340, 100), BackgroundKind.Transparent),
    ClockBold(WidgetKind.Clock, R.string.widget_style_clock_bold, size(170, 170), BackgroundKind.Solid, ColorTheme.Wallpaper),
    ClockHome(WidgetKind.Clock, R.string.widget_style_clock_home, size(340, 170), BackgroundKind.Transparent),
    ClockSun(WidgetKind.Clock, R.string.widget_style_clock_sun, size(340, 170)),
    ClockGlance(WidgetKind.Clock, R.string.widget_style_clock_glance, size(340, 100), BackgroundKind.Transparent),
    ClockCard(WidgetKind.Clock, R.string.widget_style_clock_card, size(170, 170)),

    HourlyCurve(WidgetKind.Hourly, R.string.widget_style_hourly_curve, size(320, 170)),
    HourlyIcons(WidgetKind.Hourly, R.string.widget_style_hourly_icons, size(320, 150)),
    HourlyRow(WidgetKind.Hourly, R.string.widget_style_hourly_row, size(320, 86)),

    WeeklyBars(WidgetKind.Weekly, R.string.widget_style_weekly_bars, size(320, 240)),
    WeeklyColumns(WidgetKind.Weekly, R.string.widget_style_weekly_columns, size(320, 150)),
    WeeklyList(WidgetKind.Weekly, R.string.widget_style_weekly_list, size(320, 240)),

    SmallClassic(WidgetKind.Small, R.string.widget_style_small_classic, size(160, 160), BackgroundKind.Glass),
    SmallCentered(WidgetKind.Small, R.string.widget_style_small_centered, size(160, 160)),
    SmallMinimal(WidgetKind.Small, R.string.widget_style_small_minimal, size(160, 160)),

    TextStack(WidgetKind.Text, R.string.widget_style_text_stack, size(220, 100), BackgroundKind.Transparent),
    TextLine(WidgetKind.Text, R.string.widget_style_text_line, size(320, 60), BackgroundKind.Transparent),
    TextTemperature(WidgetKind.Text, R.string.widget_style_text_temperature, size(160, 100), BackgroundKind.Transparent),

    PlacesColumns(WidgetKind.Places, R.string.widget_style_places_columns, size(340, 100)),
    PlacesList(WidgetKind.Places, R.string.widget_style_places_list, size(320, 190)),

    PrecipitationBars(WidgetKind.Precipitation, R.string.widget_style_precipitation_bars, size(340, 100)),
    PrecipitationSentence(WidgetKind.Precipitation, R.string.widget_style_precipitation_sentence, size(220, 86)),

    DetailsTiles(WidgetKind.Details, R.string.widget_style_details_tiles, size(340, 190)),
    DetailsSun(WidgetKind.Details, R.string.widget_style_details_sun, size(170, 170)),
    DetailsList(WidgetKind.Details, R.string.widget_style_details_list, size(320, 170)),
    ;

    /** Bu stil seçilince gelen görünüm; kullanıcı sonra değiştirebilir. */
    fun defaultStyle(): WidgetStyle = WidgetStyle(
        style = name,
        background = background,
        transparency = background.defaultTransparency,
        colorTheme = colorTheme,
    )

    /** Analog kadranlı stiller: kare çizilir, arka plan ayarı kadranın çevresini etkiler. */
    val isAnalog: Boolean get() = this == ClockAnalog || this == ClockField || this == ClockRing || this == ClockSS

    /** Bu stilde ekleme ekranında açılıp kapanabilen içerikler. */
    val contents: List<WidgetContent>
        get() = when (this) {
            // SS: hava alanının her parçası ayrı; tek bir "Hava durumu" anahtarı yok.
            ClockSS -> listOf(
                WidgetContent.WeatherIcon, WidgetContent.Temperature, WidgetContent.Condition,
                WidgetContent.HighLow, WidgetContent.PlaceName, WidgetContent.RainAlert,
            )
            else -> kind.contents - setOf(WidgetContent.WeatherIcon, WidgetContent.Temperature)
        }

    /** Stilin kendi saat yazı tipi (kullanıcı başka bir tane seçmediyse). */
    val clockFont: ClockFont get() = if (this == ClockRadial) ClockFont.DigitsOutline else ClockFont.System
}

/** 1.3'te her stilin önerdiği arka plan ve renk teması; eski kayıtları yükseltirken kullanılır. */
internal object LegacyLooks {
    fun of(style: WidgetStyleId): Pair<BackgroundKind, ColorTheme> = when (style) {
        WidgetStyleId.ClockBig, WidgetStyleId.ClockSide, WidgetStyleId.ClockLine, WidgetStyleId.ClockAnalog,
        WidgetStyleId.ClockHome, WidgetStyleId.ClockGlance, WidgetStyleId.TextStack, WidgetStyleId.TextLine,
        WidgetStyleId.TextTemperature,
        -> BackgroundKind.Transparent to ColorTheme.Sky
        WidgetStyleId.ClockBold, WidgetStyleId.SmallMinimal -> BackgroundKind.Solid to ColorTheme.Wallpaper
        WidgetStyleId.ClockSun, WidgetStyleId.PrecipitationBars, WidgetStyleId.DetailsSun -> BackgroundKind.Solid to ColorTheme.Night
        WidgetStyleId.WeeklyList, WidgetStyleId.SmallClassic, WidgetStyleId.PlacesList, WidgetStyleId.DetailsList,
        -> BackgroundKind.Glass to ColorTheme.Sky
        // 1.3'te olmayan stiller: bugünkü önerileri.
        WidgetStyleId.ClockRadial, WidgetStyleId.ClockField, WidgetStyleId.ClockRing, WidgetStyleId.ClockSS -> style.background to style.colorTheme
        else -> BackgroundKind.Sky to ColorTheme.Sky
    }
}
