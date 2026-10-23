package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.glance.unit.ColorProvider
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.min
import kotlin.math.roundToInt

// --- Dijital ışınsal --------------------------------------------------------------------------------

/**
 * Işınsal saatte saniye efektinin fiilen nasıl çizileceği. Şablon (stencil) yöntemi zemini kendisi
 * boyadığı için yalnızca tam dolu arka planda çalışır; cam, saydam ya da yarı saydam arka planda
 * şablon gerektirmeyen "tek çizgi" kullanılır. Saf mantık; test edilir.
 */
enum class RadialSeconds { None, StencilTail, StencilLine, StripLine }

fun radialSeconds(style: WidgetStyle, sdk: Int = Build.VERSION.SDK_INT): RadialSeconds {
    // AnalogClock'un saniye kolu Android 12 ile geldi; öncesinde çizgiler sabit görünür.
    if (sdk < Build.VERSION_CODES.S || style.secondEffect == SecondEffect.Off) return RadialSeconds.None
    val opaque = (style.background == BackgroundKind.Solid || style.background == BackgroundKind.Sky) && style.effectiveTransparency == 0
    return when {
        !opaque -> RadialSeconds.StripLine
        style.secondEffect == SecondEffect.Line -> RadialSeconds.StencilLine
        else -> RadialSeconds.StencilTail
    }
}

/**
 * Tasarım "Dijital ışınsal" (4×2, 5×2): çevrede 60 çizgi, ortada dolu saat ve çizgi dakika
 * rakamları; solda gün, sağda hava. Çizgiler saniyeyle birlikte parlar: arkada Android'in
 * AnalogClock'u (yalnızca saniye kolu) döner, önündeki şablonun deliklerinden görünür.
 * Uygulama saniyede ya da dakikada bir uyanmaz.
 */
@Composable
fun ClockRadial(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val style = theme.style
    val w = size.width.value
    val h = size.height.value
    val density = context.resources.displayMetrics.density
    val corner = style.corners.radius(LocalContext.current).value
    val palette = theme.colors.palette
    // Sönük çizgiler net görünsün; parlayan çizgi tam beyaz (koyu yazıda tam siyah).
    val dim = (style.lineColor?.let { Color(it) } ?: theme.colors.text).copy(alpha = 0.38f)
    val lit = style.lineColor?.let { Color(it) } ?: if (theme.colors.darkText) Color.Black else Color.White
    val seconds = radialSeconds(style)

    WidgetSurface(horizontal = 0.dp, vertical = 0.dp, contentAlignment = Alignment.Center) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (seconds == RadialSeconds.StencilTail || seconds == RadialSeconds.StencilLine) {
                val ground = if (style.background == BackgroundKind.Solid) listOf(palette.solid) else palette.gradient
                // Deliklerin arkası: sönük çizgi rengi; üstünde dönen yelpaze; en üstte şablon.
                val base = Color(Contrast.composite(dim.toArgb(), ground[ground.size / 2].toArgb()))
                Box(GlanceModifier.fillMaxSize().background(base)) {}
                val layout = if (seconds == RadialSeconds.StencilTail) R.layout.widget_radial_fan else R.layout.widget_radial_bar
                AndroidRemoteViews(
                    RemoteViews(context.packageName, layout).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setColorStateList(R.id.seconds, "setSecondHandTintList", ColorStateList.valueOf(lit.toArgb()))
                        }
                    },
                    GlanceModifier.fillMaxSize(),
                )
                // Tek renkli zeminde şablon tek kanallı maskedir (dörtte bir bellek), zemin rengine boyanır.
                // Gradyan zeminde şablon parçalara bölünür: ortası küçük çizilmiş gradyan, kenar şeritleri durak
                // renklerine boyanan tek kanallı maskeler (bkz. RadialCover). Görüntü tek resimle aynıdır.
                val cover = if (ground.size == 1) null else WidgetBitmaps.radialCover(w, h, density, corner, ground)
                if (cover != null) {
                    RadialCoverLayers(cover, density)
                } else {
                    Image(
                        ImageProvider(if (ground.size == 1) WidgetBitmaps.radialStencilMask(w, h, density, corner) else WidgetBitmaps.radialStencil(w, h, density, corner, ground)),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = if (ground.size == 1) ColorFilter.tint(ColorProvider(ground[0])) else null,
                    )
                }
            } else {
                Image(
                    ImageProvider(WidgetBitmaps.radialTicks(w, h, density, corner, dim.alpha)),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(ColorProvider(dim.copy(alpha = 1f))),
                )
                if (seconds == RadialSeconds.StripLine) {
                    AndroidRemoteViews(radialStrips(context, w, h, lit), GlanceModifier.fillMaxSize())
                }
            }
            RadialContent(data, hollowGround = if (style.background == BackgroundKind.Solid && style.effectiveTransparency == 0) palette.solid else null)
        }
    }
}

/**
 * Cam ve saydam arka planlar için "tek çizgi": çizgilerin durduğu dört şeridin her birinde, merkezi
 * widget'ın merkezine denk gelen bir AnalogClock. İnce saniye çubuğunun yalnızca şeride düşen
 * parçası görünür. Ölçüler dp; widget'ın gerçek boyutu gerekir (Saat widget'ı SizeMode.Exact).
 */
/**
 * Parçalı şablon: üstte ve altta tam genişlikte şeritler, arada sol şerit, orta ve sağ şerit. Şeritlerin
 * kalınlığı tam piksele denk gelir; maskeler yeniden örneklenmez.
 */
@Composable
private fun RadialCoverLayers(cover: RadialCover, density: Float) {
    val band = (cover.bandPx / density).dp
    @Composable
    fun strip(layers: List<RadialCover.Layer>, modifier: GlanceModifier) {
        Box(modifier) { layers.forEach { TintedLayer(ImageProvider(it.mask), it.color) } }
    }
    Column(GlanceModifier.fillMaxSize()) {
        strip(cover.top, GlanceModifier.fillMaxWidth().height(band))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            strip(cover.left, GlanceModifier.width(band).fillMaxHeight())
            Image(ImageProvider(cover.center), contentDescription = null, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), contentScale = ContentScale.FillBounds)
            strip(cover.right, GlanceModifier.width(band).fillMaxHeight())
        }
        strip(cover.bottom, GlanceModifier.fillMaxWidth().height(band))
    }
}

private fun radialStrips(context: Context, w: Float, h: Float, color: Color): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_radial_strips).apply {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@apply
        val inset = WidgetBitmaps.RADIAL_INSET
        val band = WidgetBitmaps.RADIAL_LENGTH
        val reach = w + h
        val dp = TypedValue.COMPLEX_UNIT_DIP
        val tint = ColorStateList.valueOf(color.toArgb())
        fun strip(strip: Int, clock: Int, left: Float, top: Float, width: Float?, height: Float?) {
            width?.let { setViewLayoutWidth(strip, it, dp) }
            height?.let { setViewLayoutHeight(strip, it, dp) }
            setViewLayoutMargin(strip, RemoteViews.MARGIN_LEFT, left, dp)
            setViewLayoutMargin(strip, RemoteViews.MARGIN_TOP, top, dp)
            // Saat, şeridi her yönde aşacak kadar büyük; merkezi widget'ın merkezinde.
            setViewLayoutWidth(clock, reach, dp)
            setViewLayoutHeight(clock, reach, dp)
            setViewLayoutMargin(clock, RemoteViews.MARGIN_LEFT, w / 2f - reach / 2f - left, dp)
            setViewLayoutMargin(clock, RemoteViews.MARGIN_TOP, h / 2f - reach / 2f - top, dp)
            setColorStateList(clock, "setSecondHandTintList", tint)
        }
        strip(R.id.strip_top, R.id.clock_top, 0f, inset, null, band)
        strip(R.id.strip_bottom, R.id.clock_bottom, 0f, h - inset - band, null, band)
        strip(R.id.strip_left, R.id.clock_left, inset, inset + band, band, h - 2 * (inset + band))
        strip(R.id.strip_right, R.id.clock_right, w - inset - band, inset + band, band, h - 2 * (inset + band))
    }

/**
 * Işınsal saatin içeriği: gün, saat : dakika, hava. Saat widget'ın hem yatayda hem dikeyde tam ortasındadır.
 * Gün solda, hava sağda durur ve ikisi AYNI genişliktedir; böylece iki nokta tam ortada kalır. Widget daracıksa
 * (rakamlar yan sütunlar yüzünden belirgin küçülecekse) yan sütunlar kalkar, yalnız saat ortada durur.
 * Widget gereğinden yüksekse boşluk eşit olarak üstte ve altta kalır (yüksekliği ana ekranda küçültmek serbesttir).
 */
/**
 * [hollowGround]: zemin tek renk ve tam doluysa o renk. O zaman dakika "içi boş" çizilir: ince rakamın
 * çevresinde kaydırılmış kopyaları düz, keskin bir çerçeve oluşturur (dakika renginde), ortadaki kopya
 * zemin rengiyle içini açar. Uygulamanın kendi çizgi rakamlı yazı tipi widget'larda yüklenemediği için
 * bu yol kullanılır; hepsi TextClock.
 */
@Composable
private fun RadialContent(data: WidgetPlaceData, hollowGround: Color? = null) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val showWeather = theme.shows(WidgetContent.Weather)
    val textScale = (theme.style.textSize.scale * theme.fontScaleDp).coerceAtLeast(1f)
    val plan = RadialLayout.plan(size.width.value, size.height.value, textScale)
    val digitsDp = plan.digitsDp
    val digits = dpToBase(digitsDp, theme, context)
    Row(
        GlanceModifier.fillMaxSize().padding(horizontal = RadialLayout.RING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (plan.sides) {
            Column(GlanceModifier.width(plan.sideWidth.dp), horizontalAlignment = Alignment.CenterHorizontally) { RadialDate() }
            HSpace(4.dp)
        }
        RadialDigits(digits, digitsDp, hollowGround)
        if (plan.sides) {
            HSpace(4.dp)
            Column(GlanceModifier.width(plan.sideWidth.dp).clickableApp(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showWeather) RadialWeather(data, plan.sideWidth, plan.sideWidth + 8f)
            }
        }
    }
}

/** Gün: iki satır (GÜN / sayı), ortalı. */
@Composable
private fun RadialDate() {
    val theme = LocalWidgetTheme.current
    WClock(ClockPart.Date, 13f, weight = WWeight.Bold, color = theme.colors.accent, dateSkeleton = "EEE", uppercase = true, contentAlignment = Alignment.Center)
    WClock(ClockPart.Date, 24f, color = theme.colors.text, dateSkeleton = "d", contentAlignment = Alignment.Center, font = WFont.Digits)
}

/**
 * Hava: küçük hap (ikon ve sıcaklık) ve yanında ya da altında yer adı. Tarihle aynı ölçekte: hap 4+4 dp dolgulu,
 * yazılar 15 ve 10 sp.
 */
@Composable
private fun RadialWeather(data: WidgetPlaceData, maxWidth: Float, textWidth: Float) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val current = data.snapshot.forecast.current
    // Hava hapı: koyu zeminde açık hap ve koyu yazı (rengi ayarlanabilir).
    val pill = theme.style.pillColor?.let { Color(it) } ?: theme.colors.text
    val onPill = if (pill.luminance() > 0.45f) WidgetInk.Dark else WidgetInk.Light
    val name = if (theme.shows(WidgetContent.PlaceName)) data.snapshot.displayName(context) else null
    val condition = if (theme.shows(WidgetContent.Condition)) context.getString(weatherDescriptionRes(current.weatherCode)) else null
    // "Beşiktaş · Açık"; sığmazsa yalnız yer, o da sığmazsa yalnız durum. Yazı kesilmez.
    val text = listOfNotNull(listOfNotNull(name, condition).joinToString(" · ").ifEmpty { null }, name, condition)
        .firstOrNull { estimateWidthDp(it, 9f, theme) <= textWidth }
    @Composable
    fun weatherPill() = Pill(color = pill, horizontal = 7f, vertical = 3f) {
        WithColors(theme.colors.copy(shadow = false, text = onPill)) {
            WeatherIconTinted(current.condition, !current.isDay, 12.dp, if (pill.luminance() > 0.45f) VividOnLight.Sun else null)
            HSpace(3.dp)
            val temperature = temp(current.temperature)
            WText(temperature, min(13f, fitWidth(temperature, 13f, maxWidth - 14f - 12f - 3f - 4f, theme)), weight = WWeight.Bold)
        }
    }
    weatherPill()
    if (text != null) {
        VSpace(3.dp)
        WText(text, 9f, color = theme.colors.secondary, align = TextAlign.Center)
    }
}

/** Saat, iki nokta ve dakika (dakika içi boş çizilebilir). */
@Composable
private fun RadialDigits(digits: Float, digitsDp: Float, hollowGround: Color?) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WClock(ClockPart.Hours, digits, color = theme.hourColor, font = WFont.Digits, scaleX = RADIAL_SCALE_X)
        HSpace((digitsDp * 0.04f).dp)
        ClockColon(digits * 1.15f, theme.hourColor)
        HSpace((digitsDp * 0.04f).dp)
        if (theme.clockFont == ClockFont.DigitsOutline && hollowGround != null && !OUTLINE_FONT_IN_WIDGETS) {
            // Çerçeve kalınlığı rakamın boyuna göre: yaklaşık yüzde 4,5.
            val hollowLayout = when ((digitsDp * 0.045f).roundToInt().coerceIn(2, 5)) {
                2 -> R.layout.wc_hollow_2
                3 -> R.layout.wc_hollow_3
                4 -> R.layout.wc_hollow_4
                else -> R.layout.wc_hollow_5
            }
            Box {
                AndroidRemoteViews(
                    RemoteViews(context.packageName, hollowLayout).apply {
                        for (id in HOLLOW_FRAME_IDS) setTextColor(id, theme.minuteColor.toArgb())
                        for (id in HOLLOW_FRAME_IDS + R.id.clock) {
                            setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, theme.sp(digits).value)
                            setFloat(id, "setTextScaleX", RADIAL_SCALE_X)
                            setCharSequence(id, "setFormat12Hour", "mm")
                            setCharSequence(id, "setFormat24Hour", "mm")
                        }
                        setTextColor(R.id.clock, hollowGround.toArgb())
                        setOnClickPendingIntent(R.id.clock, clockAppIntent(context))
                    },
                )
            }
        } else {
            WClock(ClockPart.Minutes, digits, color = theme.minuteColor, font = if (theme.clockFont == ClockFont.DigitsOutline) WFont.Outline else WFont.Digits, scaleX = RADIAL_SCALE_X)
        }
    }
}

/**
 * Işınsal saatin yerleşim kararı (saf hesap; test edilir). Ölçüler dp.
 * Rakam boyu ([Plan.digitsDp]) hem genişliğe hem yüksekliğe sığar; "17:22" ≈ 2,2 em genişliğinde (yatayda sıkıştırılmış).
 * Kalan genişliğe pay bırakılır: sistem yazı tipinin rakamları tahminden biraz geniş olabilir; ölçü tam doldurulunca
 * en küçük fark bile günü ve hapı keser.
 */
object RadialLayout {
    /** Çizgi halkasının kalınlığı: kenardan içeri boşluk + çizgi derinliği + pay. */
    val RING: Float get() = WidgetBitmaps.RADIAL_INSET + WidgetBitmaps.RADIAL_LENGTH + 2f

    /** Rakamla çizgiler arasında dikeyde ek nefes payı. */
    private const val BREATH = 4f
    private const val EMS = 2.2f

    /** Rakam kutusunun yüksekliği (em); gövde yazı tipinin satır yüksekliği rakamın kendisinden büyüktür. */
    private const val BOX = 1.12f
    private const val MIN_DIGITS = 24f
    private const val MAX_DIGITS = 150f
    private const val MIN_SIDE = 50f

    /** [sides]: gün ve hava yan sütunları var mı; [sideWidth]: ikisinin ortak genişliği. */
    data class Plan(val sides: Boolean, val digitsDp: Float, val sideWidth: Float)

    fun plan(w: Float, h: Float, textScale: Float): Plan {
        val ring = RING
        val roomy = (if (w >= 380f) 62f else 56f) * textScale
        val heightFit = (h - 2 * (ring + BREATH)) / BOX
        val alone = min((w - 2 * ring - 8f) / EMS, heightFit)
        // Yan sütunlar aynı genişlikte (iki nokta tam ortada). Alçak widget'ta daralır (en az hap kadar) ki rakamlar
        // yüksekliğe kadar büyüyebilsin.
        val needed = (w - 2 * ring - 8f - EMS * heightFit) / 2f
        val sideWidth = needed.coerceIn(MIN_SIDE * textScale, roomy)
        val withSides = min((w - 2 * ring - 2 * sideWidth - 8f) / EMS, heightFit)
        // Yan sütunlar rakamları yarıdan çok küçültecekse kalkar.
        val sides = withSides >= 0.45f * alone
        return Plan(sides, (if (sides) withSides else alone).coerceIn(MIN_DIGITS, MAX_DIGITS), sideWidth)
    }
}

/** İçi boş rakamın çerçevesini oluşturan kaydırılmış kopyalar (bkz. res/layout/wc_hollow_*.xml). */
private val HOLLOW_FRAME_IDS = intArrayOf(
    R.id.clock_halo_1, R.id.clock_halo_2, R.id.clock_halo_3, R.id.clock_halo_4, R.id.clock_halo_5, R.id.clock_halo_6,
    R.id.clock_halo_7, R.id.clock_halo_8, R.id.clock_halo_9, R.id.clock_halo_10, R.id.clock_halo_11, R.id.clock_halo_12,
    R.id.clock_halo_13, R.id.clock_halo_14, R.id.clock_halo_15, R.id.clock_halo_16, R.id.clock_halo_17, R.id.clock_halo_18,
    R.id.clock_halo_19, R.id.clock_halo_20, R.id.clock_halo_21, R.id.clock_halo_22, R.id.clock_halo_23, R.id.clock_halo_24,
)

/** Işınsal saatin rakamları yatayda bu kadar sıkıştırılır: sistemin dar rakamları tasarımdaki oranlara yaklaşır. */
private const val RADIAL_SCALE_X = 0.8f

/** Hap içindeki hava ikonu: açık hapta güneş koyu sarı, yağış koyu mavi. */
@Composable
private fun WeatherIconTinted(condition: WeatherCondition, isNight: Boolean, size: androidx.compose.ui.unit.Dp, sunOnLight: Color?) {
    val colors = LocalWidgetTheme.current.colors
    val tint = when {
        condition == WeatherCondition.Clear -> sunOnLight ?: if (isNight) colors.moon else colors.sun
        condition.isWet -> if (sunOnLight != null) VividOnLight.Rain else colors.rain
        else -> colors.text
    }
    WIcon(condition.lineIconRes(isNight), size, tint)
}

/** Yazı boyutu ayarından ve sistemin yazı ölçeğinden bağımsız, tam [dp] yüksekliğinde yazı için taban boyut. */
fun dpToBase(dp: Float, theme: WidgetTheme, context: Context): Float =
    dp / (theme.style.textSize.scale * theme.fontScaleFix * context.resources.configuration.fontScale)

// --- Analog -----------------------------------------------------------------------------------------

/**
 * Analog saatler (2×2): Sade, Saha, Hava halkası. Kadran sabit bir resimdir (yalnızca ayar ya da
 * halkanın verisi değişince yeniden çizilir); kolları ve saniye kolunu Android'in AnalogClock'u
 * çizer ve döndürür. Hava bilgisi kadranın üst yarısında durur.
 */
@Composable
fun ClockAnalogFace(styleId: WidgetStyleId, data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val style = theme.style
    val kind = when (styleId) {
        WidgetStyleId.ClockField -> WidgetBitmaps.Dial.Field
        WidgetStyleId.ClockRing -> WidgetBitmaps.Dial.Ring
        else -> WidgetBitmaps.Dial.Plain
    }
    val light = kind == WidgetBitmaps.Dial.Plain
    val ground = style.dialColor?.let { Color(it) } ?: if (light) Color(0xFFF4F2EC) else Color(0xFF141414)
    val onGround = if (ground.luminance() > 0.45f) WidgetInk.Dark else WidgetInk.Light
    val marks = style.numeralColor?.let { Color(it) } ?: onGround
    val hands = style.handColor?.let { Color(it) } ?: if (kind == WidgetBitmaps.Dial.Field) Color(0xFFF4F2EC) else onGround
    val accent = theme.colors.accent
    // Android 12+: kadran widget'la birlikte büyür (kollar kadranın boyutunda çizilir). Öncesinde
    // kolların boyutu sabit olduğu için kadran en fazla 200 dp.
    val dial = min(size.width.value, size.height.value).coerceAtMost(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MAX_DIAL_DP else 200f)
    val scale = dial / 200f
    val forecast = data.snapshot.forecast
    val now = forecast.localNow()

    // Hava halkası: önümüzdeki 12 saatin renkleri, kadrandaki yerlerine göre (0 = saat 12).
    val arcs: Map<Int, Color> = if (kind == WidgetBitmaps.Dial.Ring) {
        val hours = forecast.upcomingHours(now, count = 12)
        val low = hours.minOfOrNull { it.temperature } ?: 0.0
        val high = hours.maxOfOrNull { it.temperature } ?: 0.0
        val cool = style.ringCool?.let { Color(it) } ?: theme.colors.cool
        val warm = style.ringWarm?.let { Color(it) } ?: theme.colors.warm
        val rain = style.ringRain?.let { Color(it) } ?: WidgetInk.Rain
        hours.mapIndexed { i, hour ->
            val wet = hour.condition.isWet || hour.condition == WeatherCondition.Snow
            (now.hour + i) % 12 to if (wet) rain else lerp(cool, warm, if (high - low < 0.5) 0.5f else ((hour.temperature - low) / (high - low)).toFloat())
        }.toMap()
    } else {
        emptyMap()
    }

    val dialColors = theme.colors.copy(
        darkText = onGround == WidgetInk.Dark,
        shadow = false,
        scrim = false,
        text = onGround,
        secondary = onGround.copy(alpha = 0.7f),
        tertiary = onGround.copy(alpha = 0.55f),
        sun = if (onGround == WidgetInk.Dark) VividOnLight.Sun else WidgetInk.Sun,
        rain = if (onGround == WidgetInk.Dark) VividOnLight.Rain else WidgetInk.Rain,
        moon = if (onGround == WidgetInk.Dark) VividOnLight.Moon else WidgetInk.Moon,
    )
    WidgetSurface(horizontal = 0.dp, vertical = 0.dp, contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(dial.dp), contentAlignment = Alignment.Center) {
            // Kadran vektör katmanlardan çizilir (her boyutta keskin); kenardan 2 dp içeride.
            Box(GlanceModifier.fillMaxSize().clickable(clockAction(context)).padding(2.dp)) {
                DialFace(kind, ground, marks, accent, style.highlighted, arcs)
            }
            WithColors(dialColors) {
                if (theme.shows(WidgetContent.Weather)) AnalogWeather(kind, data, scale)
                if (kind == WidgetBitmaps.Dial.Plain) {
                    // Tarih penceresi: saat 3 yönünde, ince çerçeveli.
                    Box(GlanceModifier.fillMaxSize().padding(end = (30 * scale).dp), contentAlignment = Alignment.CenterEnd) {
                        Box(GlanceModifier.rounded(marks, 4.dp).padding(1.5.dp), contentAlignment = Alignment.Center) {
                            Box(GlanceModifier.rounded(ground, 4.dp).padding(horizontal = (2 * scale).dp), contentAlignment = Alignment.Center) {
                                WClock(ClockPart.Date, 13f * scale, color = marks, dateSkeleton = "d", contentAlignment = Alignment.Center, font = WFont.Digits)
                            }
                        }
                    }
                }
            }
            // Kollar en üstte; dokunmayı tutmaz, alttaki hava bilgisine ve kadrana geçer.
            AndroidRemoteViews(analogHands(context, kind, style, hands, accent, dial), GlanceModifier.fillMaxSize())
        }
    }
}

/** Kadranın üst yarısı: ikon, sıcaklık ve kısa durum; altında yer ya da Y/D; yağmur bekleniyorsa saat aralığı. */
@Composable
private fun AnalogWeather(kind: WidgetBitmaps.Dial, data: WidgetPlaceData, scale: Float) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val forecast = data.snapshot.forecast
    val current = forecast.current
    val today = forecast.today(forecast.localNow())
    val headline = buildList {
        add(temp(current.temperature))
        if (theme.shows(WidgetContent.Condition)) add(context.getString(weatherDescriptionRes(current.weatherCode)))
    }
    val detail = buildList {
        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
        if (theme.shows(WidgetContent.HighLow) && today != null && kind == WidgetBitmaps.Dial.Plain) add(highLowCompact(today.maxTemperature, today.minTemperature))
    }.joinToString(" · ")
    val rain = if (theme.shows(WidgetContent.RainAlert)) rainRange(context, data.snapshot) else null
    // Saha kadranında rakamların arasında yer dar: tek satır, saat 12'nin altında.
    val top = when (kind) {
        WidgetBitmaps.Dial.Plain -> 46f
        WidgetBitmaps.Dial.Field -> 66f
        WidgetBitmaps.Dial.Ring -> 50f
    } * scale
    val widthDp = (if (kind == WidgetBitmaps.Dial.Field) 56f else 104f) * scale
    Column(
        GlanceModifier.fillMaxSize().padding(top = top.dp).clickableApp(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (kind) {
            WidgetBitmaps.Dial.Plain -> {
                WeatherGlyph(current.condition, !current.isDay, (17 * scale).dp)
                WText(fitJoined(headline, 14f * scale, widthDp, theme), 14f * scale, weight = WWeight.Medium, align = TextAlign.Center)
                if (rain != null) {
                    AnalogRain(rain, 10f * scale)
                } else if (detail.isNotEmpty() && estimateWidthDp(detail, 9.5f * scale, theme) <= widthDp + 20f * scale) {
                    WText(detail, 9.5f * scale, color = theme.colors.secondary, align = TextAlign.Center)
                }
            }
            WidgetBitmaps.Dial.Field -> Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(current.condition, !current.isDay, (11 * scale).dp)
                HSpace(2.dp)
                WText(fitJoined(headline, 10.5f * scale, widthDp - 13f * scale, theme), 10.5f * scale, weight = WWeight.Medium, font = WFont.Text)
            }
            WidgetBitmaps.Dial.Ring -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(current.condition, !current.isDay, (16 * scale).dp)
                    HSpace(3.dp)
                    WText(temp(current.temperature), 20f * scale, weight = WWeight.Medium)
                }
                val line = buildList {
                    if (theme.shows(WidgetContent.Condition)) add(context.getString(weatherDescriptionRes(current.weatherCode)))
                    if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
                }
                val text = fitJoined(line, 9.5f * scale, widthDp, theme, separator = " · ")
                if (text.isNotEmpty()) WText(text, 9.5f * scale, color = theme.colors.secondary, align = TextAlign.Center)
                if (rain != null) AnalogRain(rain, 9.5f * scale)
            }
        }
    }
}

@Composable
private fun AnalogRain(text: String, size: Float) {
    val theme = LocalWidgetTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WIcon(R.drawable.ic_wl_drop, (size + 1).dp, theme.colors.rain)
        HSpace(2.dp)
        WText(text, size, color = theme.colors.rain)
    }
}

/** Parçaları " · " ile birleştirir; sığmıyorsa sondan düşürür (yazı kesilmez). */
private fun fitJoined(parts: List<String>, sizeSp: Float, widthDp: Float, theme: WidgetTheme, separator: String = " "): String =
    generateSequence(parts) { if (it.size > 1) it.dropLast(1) else null }
        .map { it.joinToString(separator) }
        .firstOrNull { estimateWidthDp(it, sizeSp, theme) <= widthDp }
        ?: parts.firstOrNull().orEmpty()

/** "Y 19° D 12°": kadranda yer dar. */
@Composable
private fun highLowCompact(max: Double, min: Double): String =
    LocalContext.current.getString(R.string.widget_high_low_compact, temp(max), temp(min))

/**
 * Önümüzdeki 12 saatteki ilk yağışın saat aralığı ("20:00–23:00"); yağış yoksa null. Şu an
 * yağıyorsa aralık şimdiki saatten başlar.
 */
fun rainRange(context: Context, snapshot: WeatherSnapshot): String? {
    val forecast = snapshot.forecast
    val hours = forecast.upcomingHours(forecast.localNow(), count = 12)
    fun wet(index: Int) = hours[index].condition.let { it.isWet || it == WeatherCondition.Snow }
    val first = hours.indices.firstOrNull(::wet) ?: return null
    var last = first
    while (last + 1 < hours.size && wet(last + 1)) last++
    val formatter = timeFormatter(context)
    return "${hours[first].time.format(formatter)}–${hours[last].time.plusHours(1).format(formatter)}"
}

private const val MAX_DIAL_DP = 400f

private val FIELD_NUMERALS = intArrayOf(
    R.drawable.widget_dial_field_n1, R.drawable.widget_dial_field_n2, R.drawable.widget_dial_field_n3, R.drawable.widget_dial_field_n4,
    R.drawable.widget_dial_field_n5, R.drawable.widget_dial_field_n6, R.drawable.widget_dial_field_n7, R.drawable.widget_dial_field_n8,
    R.drawable.widget_dial_field_n9, R.drawable.widget_dial_field_n10, R.drawable.widget_dial_field_n11, R.drawable.widget_dial_field_n12,
    R.drawable.widget_dial_field_n13, R.drawable.widget_dial_field_n14, R.drawable.widget_dial_field_n15, R.drawable.widget_dial_field_n16,
    R.drawable.widget_dial_field_n17, R.drawable.widget_dial_field_n18, R.drawable.widget_dial_field_n19, R.drawable.widget_dial_field_n20,
    R.drawable.widget_dial_field_n21, R.drawable.widget_dial_field_n22, R.drawable.widget_dial_field_n23, R.drawable.widget_dial_field_n24,
)

private val RING_ARCS = intArrayOf(
    R.drawable.widget_dial_ring_arc_0, R.drawable.widget_dial_ring_arc_1, R.drawable.widget_dial_ring_arc_2, R.drawable.widget_dial_ring_arc_3,
    R.drawable.widget_dial_ring_arc_4, R.drawable.widget_dial_ring_arc_5, R.drawable.widget_dial_ring_arc_6, R.drawable.widget_dial_ring_arc_7,
    R.drawable.widget_dial_ring_arc_8, R.drawable.widget_dial_ring_arc_9, R.drawable.widget_dial_ring_arc_10, R.drawable.widget_dial_ring_arc_11,
)

/**
 * Analog saatin kadranı, vektör katmanlar olarak: zemin, çizgiler, rakamlar ya da hava halkası. Her katman
 * beyaz çizilmiş bir vektördür ve kendi rengine boyanır. Kadran sabittir; kolları AnalogClock çizer.
 *
 * [marks]: çizgi ve rakam rengi; [highlighted]: vurgu renginde gösterilecek rakamlar (Saha);
 * [arcs]: kadrandaki yerine (0 = saat 12) göre önümüzdeki saatlerin renkleri (Hava halkası).
 */
@Composable
private fun DialFace(kind: WidgetBitmaps.Dial, ground: Color, marks: Color, accent: Color, highlighted: Set<Int>, arcs: Map<Int, Color>) {
    @Composable
    fun layer(drawable: Int, color: Color) = TintedLayer(ImageProvider(drawable), color)
    layer(R.drawable.widget_dial_ground, ground)
    when (kind) {
        WidgetBitmaps.Dial.Plain -> layer(R.drawable.widget_dial_plain_marks, marks)
        WidgetBitmaps.Dial.Field -> {
            // Çizgilerin %70 saydamlığı vektörün içindedir.
            layer(R.drawable.widget_dial_field_ticks, marks)
            if (highlighted.isEmpty()) {
                layer(R.drawable.widget_dial_field_outer, marks)
                layer(R.drawable.widget_dial_field_inner, marks.copy(alpha = marks.alpha * 0.6f))
            } else {
                // Vurgulu rakam varsa her rakam kendi renginde. Glance bir kutuda en çok 10 öğe çizer: altışar.
                (1..24).chunked(6).forEach { group ->
                    Box(GlanceModifier.fillMaxSize()) {
                        group.forEach { n ->
                            val color = if (n <= 12) marks else marks.copy(alpha = marks.alpha * 0.6f)
                            layer(FIELD_NUMERALS[n - 1], if (n in highlighted) accent else color)
                        }
                    }
                }
            }
        }
        WidgetBitmaps.Dial.Ring -> {
            arcs.entries.sortedBy { it.key }.chunked(6).forEach { group ->
                Box(GlanceModifier.fillMaxSize()) { group.forEach { (position, color) -> layer(RING_ARCS[position], color) } }
            }
            // Ara saat çizgilerinin %50 saydamlığı vektörün içindedir.
            layer(R.drawable.widget_dial_ring_minor, marks)
            layer(R.drawable.widget_dial_ring_major, marks)
        }
    }
}

/**
 * Analog saatin kolları: düzendeki vektörler, AnalogClock ile. Android 12+: kollar kadranla birlikte
 * büyür ve seçilen renklere boyanır; saniye kolu ve simgesi için ayrı düzenler vardır. Öncesinde
 * düzendeki sabit (200 dp, boyanamayan) kollar kullanılır.
 */
private fun analogHands(context: Context, kind: WidgetBitmaps.Dial, style: WidgetStyle, hands: Color, accent: Color, dialDp: Float): RemoteViews {
    val seconds = style.secondHand
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Sıra: saniye kolu yok, simgesiz, yıldız, daire.
        val layouts = when (kind) {
            WidgetBitmaps.Dial.Plain -> intArrayOf(R.layout.widget_analog31_plain, R.layout.widget_analog31_plain_none, R.layout.widget_analog31_plain_star, R.layout.widget_analog31_plain_circle)
            WidgetBitmaps.Dial.Field -> intArrayOf(R.layout.widget_analog31_field, R.layout.widget_analog31_field_none, R.layout.widget_analog31_field_star, R.layout.widget_analog31_field_circle)
            WidgetBitmaps.Dial.Ring -> intArrayOf(R.layout.widget_analog31_ring, R.layout.widget_analog31_ring_none, R.layout.widget_analog31_ring_star, R.layout.widget_analog31_ring_circle)
        }
        val layout = layouts[
            when {
                !seconds -> 0
                style.secondSymbol == SecondSymbol.Star -> 2
                style.secondSymbol == SecondSymbol.Circle -> 3
                else -> 1
            },
        ]
        return RemoteViews(context.packageName, layout).apply {
            val tint = ColorStateList.valueOf(hands.toArgb())
            setColorStateList(R.id.clock, "setHourHandTintList", tint)
            setColorStateList(R.id.clock, "setMinuteHandTintList", tint)
            if (seconds) setColorStateList(R.id.clock, "setSecondHandTintList", ColorStateList.valueOf(accent.toArgb()))
        }
    }
    val layout = when (kind) {
        WidgetBitmaps.Dial.Plain -> R.layout.widget_analog_plain
        WidgetBitmaps.Dial.Field -> R.layout.widget_analog_field
        WidgetBitmaps.Dial.Ring -> R.layout.widget_analog_ring
    }
    return RemoteViews(context.packageName, layout)
}

/** Saate dokununca telefonun saat/alarm uygulaması; yoksa Commune Sky. */
fun clockAction(context: Context): Action {
    val clock = ClockApp.intent(context)
    return if (clock != null) actionStartActivity(clock) else actionStartActivity<MainActivity>()
}
