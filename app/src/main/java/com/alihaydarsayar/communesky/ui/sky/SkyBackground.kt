package com.alihaydarsayar.communesky.ui.sky

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherScene
import com.alihaydarsayar.communesky.ui.common.colorRes
import com.alihaydarsayar.communesky.ui.common.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Havaya ve günün saatine göre değişen, canlı gökyüzü.
 *
 * Katmanlar (alttan üste): renk gradyanı → güneş/ay ışıltısı → yıldızlar → bulutlar → yağmur/kar/sis
 * → şimşek. Her katman ayrı bir çizim katmanında (graphicsLayer) olduğu için üstteki kartlar
 * kaydırılırken gökyüzü yeniden çizilmez; gökyüzü de her karede kartları yeniden çizdirmez.
 */
@Composable
fun SkyBackground(scene: WeatherScene, modifier: Modifier = Modifier) {
    val colors = scene.theme.colorRes
    val colorSpec = tween<Color>(durationMillis = 1600, easing = FastOutSlowInEasing)
    val top by animateColorAsState(colorResource(colors.top), colorSpec, label = "skyTop")
    val mid by animateColorAsState(colorResource(colors.mid), colorSpec, label = "skyMid")
    val bottom by animateColorAsState(colorResource(colors.bottom), colorSpec, label = "skyBottom")
    // Telefon koyu temadaysa parlak gündüz gökyüzlerini biraz karart; göz yormasın.
    val dim by animateFloatAsState(
        targetValue = if (LocalDarkTheme.current && scene.theme.isLight) 0.22f else 0f,
        animationSpec = tween(1600),
        label = "skyDim",
    )
    val time = rememberSceneTime()
    val density = LocalDensity.current
    val cloudSprite = remember(density) { createCloudSprite(density) }

    Box(modifier) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    // Gradyan sadece renkler değişince yeniden oluşturulur.
                    val gradient = Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom)
                    onDrawBehind { drawRect(gradient) }
                },
        )
        // Hava değişince efektler yumuşakça birbirine geçer.
        Crossfade(targetState = scene, animationSpec = tween(1200), label = "skyEffects") { target ->
            val golden = target.theme == SkyTheme.Sunrise || target.theme == SkyTheme.Sunset
            val effects = remember(target.condition, target.isNight, golden) {
                SceneEffects(target.condition, target.isNight, golden)
            }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val rays = createSunRays(size)
                        onDrawBehind { drawScene(target, effects, time, cloudSprite, rays) }
                    },
            )
        }
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { if (dim > 0f) drawRect(Color.Black.copy(alpha = dim)) },
        )
    }
}

private fun DrawScope.drawScene(
    scene: WeatherScene,
    effects: SceneEffects,
    timeState: State<Float>,
    cloudSprite: ImageBitmap,
    sunRays: Path,
) {
    // Saat değeri burada, çizim sırasında okunur: her karede sadece bu katman yeniden çizilir.
    val t = timeState.value
    drawCelestial(scene, t, sunRays)
    effects.stars?.let { drawStars(it, t) }
    effects.clouds?.let { drawClouds(it, effects.cloudTint, cloudSprite, t) }
    if (effects.fog) drawFog(cloudSprite, t)
    effects.rain?.let { drawRain(it, effects.isDrizzle, t) }
    effects.snow?.let { drawSnow(it, t) }
    if (effects.lightning) drawLightning(t)
}

// --- Güneş, ay, gün doğumu/batımı ---

private fun DrawScope.drawCelestial(scene: WeatherScene, t: Float, sunRays: Path) {
    when (scene.theme) {
        SkyTheme.ClearDay -> {
            val center = Offset(size.width * 0.84f, size.height * 0.07f)
            val pulse = 1f + 0.035f * sin(t * 0.5f)
            val radius = size.width * 0.9f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFFF6DC).copy(alpha = 0.6f),
                    0.3f to Color(0xFFFFE7A3).copy(alpha = 0.2f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            // Yavaşça dönen ışık huzmeleri.
            rotate(degrees = t * 1.2f, pivot = center) {
                drawPath(sunRays, Color.White.copy(alpha = 0.045f))
            }
            val core = 30.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White,
                    0.45f to Color(0xFFFFF4C9),
                    1f to Color.Transparent,
                    center = center,
                    radius = core * 2.2f,
                ),
                radius = core * 2.2f,
                center = center,
            )
        }
        SkyTheme.Sunrise, SkyTheme.Sunset -> {
            val horizon = Offset(size.width * 0.5f, size.height * 1.02f)
            val radius = size.width * 1.2f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFFC58A).copy(alpha = 0.6f),
                    0.45f to Color(0xFFFF8E6E).copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = horizon,
                    radius = radius,
                ),
                radius = radius,
                center = horizon,
            )
            val sunCenter = Offset(size.width * 0.26f, size.height * (0.84f + 0.004f * sin(t * 0.4f)))
            val sunRadius = 70.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFFF3D6),
                    0.35f to Color(0xFFFFD08A).copy(alpha = 0.85f),
                    1f to Color.Transparent,
                    center = sunCenter,
                    radius = sunRadius,
                ),
                radius = sunRadius,
                center = sunCenter,
            )
        }
        SkyTheme.ClearNight -> drawMoon(t)
        else -> Unit
    }
}

private fun DrawScope.drawMoon(t: Float) {
    val center = Offset(size.width * 0.8f, size.height * 0.11f)
    val radius = 22.dp.toPx()
    val glow = radius * (6f + 0.3f * sin(t * 0.4f))
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFF6E0).copy(alpha = 0.2f),
            1f to Color.Transparent,
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
    drawCircle(Color(0xFFF4F0E3), radius, center)
    // Ay yüzeyindeki hafif lekeler.
    val crater = Color(0xFFB9B29C)
    drawCircle(crater, radius * 0.22f, center + Offset(-radius * 0.3f, -radius * 0.2f), alpha = 0.18f)
    drawCircle(crater, radius * 0.16f, center + Offset(radius * 0.35f, radius * 0.1f), alpha = 0.15f)
    drawCircle(crater, radius * 0.12f, center + Offset(-radius * 0.05f, radius * 0.45f), alpha = 0.14f)
}

/** Güneşten dışarı açılan ince, saydam ışık üçgenleri; boyut değişmedikçe bir kez oluşturulur. */
private fun createSunRays(size: Size): Path {
    val center = Offset(size.width * 0.84f, size.height * 0.07f)
    val length = size.maxDimension * 1.3f
    val halfAngle = (2.2 * PI / 180).toFloat()
    return Path().apply {
        for (i in 0 until 12) {
            val angle = (i * 30 * PI / 180).toFloat()
            moveTo(center.x, center.y)
            lineTo(center.x + cos(angle - halfAngle) * length, center.y + sin(angle - halfAngle) * length)
            lineTo(center.x + cos(angle + halfAngle) * length, center.y + sin(angle + halfAngle) * length)
            close()
        }
    }
}

// --- Yıldızlar ---

private fun DrawScope.drawStars(stars: ParticleField, t: Float) {
    val unit = 1.dp.toPx()
    for (i in 0 until stars.size) {
        val twinkle = 0.55f + 0.45f * sin(t * stars.speed[i] + stars.phase[i])
        drawCircle(
            color = Color.White,
            radius = stars.scale[i] * unit,
            center = Offset(stars.x[i] * size.width, stars.y[i] * size.height),
            alpha = stars.alpha[i] * twinkle,
        )
    }
}

// --- Bulutlar ve sis ---

private val CloudFilters = mapOf(
    CloudTint.Day to null,
    CloudTint.Night to ColorFilter.tint(Color(0xFF8E9AB5), BlendMode.Modulate),
    CloudTint.Golden to ColorFilter.tint(Color(0xFFFFC9B5), BlendMode.Modulate),
    CloudTint.Rain to ColorFilter.tint(Color(0xFFB4BFCD), BlendMode.Modulate),
    CloudTint.Storm to ColorFilter.tint(Color(0xFF6B7590), BlendMode.Modulate),
)

private fun DrawScope.drawClouds(clouds: ParticleField, tint: CloudTint, sprite: ImageBitmap, t: Float) {
    val filter = CloudFilters[tint]
    val alphaFactor = if (tint == CloudTint.Night) 0.55f else 0.95f
    val unit = 1.dp.toPx()
    for (i in 0 until clouds.size) {
        val width = sprite.width * clouds.scale[i]
        val height = sprite.height * clouds.scale[i]
        val travel = size.width + width
        val x = (clouds.x[i] * travel + clouds.speed[i] * unit * t) % travel - width
        drawImage(
            image = sprite,
            dstOffset = IntOffset(x.toInt(), (clouds.y[i] * size.height).toInt()),
            dstSize = IntSize(width.toInt(), height.toInt()),
            alpha = clouds.alpha[i] * alphaFactor,
            colorFilter = filter,
        )
    }
}

private fun DrawScope.drawFog(sprite: ImageBitmap, t: Float) {
    val unit = 1.dp.toPx()
    for (i in 0 until 4) {
        val width = size.width * 2.4f
        val height = size.height * 0.24f
        val travel = size.width + width
        val x = (i * 0.37f * travel + (6f + i * 4f) * unit * t) % travel - width
        drawImage(
            image = sprite,
            dstOffset = IntOffset(x.toInt(), (size.height * (0.4f + i * 0.15f)).toInt()),
            dstSize = IntSize(width.toInt(), height.toInt()),
            alpha = 0.28f,
        )
    }
}

// --- Yağmur ve kar ---

private val RainColor = Color(0xFFDDEBFF)

private fun DrawScope.drawRain(rain: ParticleField, drizzle: Boolean, t: Float) {
    val unit = 1.dp.toPx()
    val slant = 0.16f // Rüzgâr: damlalar hafif eğik düşsün.
    val baseLength = (if (drizzle) 9f else 22f) * unit
    val span = size.width + size.height * slant
    for (i in 0 until rain.size) {
        val length = baseLength * (0.5f + rain.scale[i])
        val fall = size.height + length
        val y = (rain.y[i] * fall + rain.speed[i] * unit * t) % fall - length
        val x = rain.x[i] * span - slant * y
        drawLine(
            color = RainColor,
            start = Offset(x, y),
            end = Offset(x - slant * length, y + length),
            strokeWidth = (0.8f + rain.scale[i] * 0.9f) * unit,
            cap = StrokeCap.Round,
            alpha = rain.alpha[i],
        )
    }
}

private fun DrawScope.drawSnow(snow: ParticleField, t: Float) {
    val unit = 1.dp.toPx()
    for (i in 0 until snow.size) {
        val scale = snow.scale[i]
        val fall = size.height + 20f * unit
        val y = (snow.y[i] * fall + snow.speed[i] * unit * t) % fall - 10f * unit
        val sway = sin(t * (0.5f + scale * 0.5f) + snow.phase[i]) * (8f + scale * 18f) * unit
        val x = ((snow.x[i] * size.width + sway) % size.width + size.width) % size.width
        drawCircle(
            color = Color.White,
            radius = (1.2f + scale * 2.6f) * unit,
            center = Offset(x, y),
            alpha = snow.alpha[i],
        )
    }
}

// --- Şimşek ---

private fun DrawScope.drawLightning(t: Float) {
    val period = 7.5f
    val cycle = floor(t / period).toInt()
    // Her döngüde çakmasın; düzensiz görünsün.
    if (cycle % 3 == 1) return
    val local = t - cycle * period
    val flash = when {
        local < 0.07f -> 0.5f
        local < 0.14f -> 0.08f
        local < 0.22f -> 0.38f
        local < 0.6f -> 0.38f * (1f - (local - 0.22f) / 0.38f)
        else -> 0f
    }
    if (flash > 0f) drawRect(Color(0xFFE8EEFF), alpha = flash)
}

// --- Bulut görseli ---

/**
 * Yumuşak kenarlı bir bulut görseli; uygulama açılırken bir kez çizilir ve tüm bulutlar
 * bunu farklı boyut/saydamlıkta tekrar kullanır. Her karede bulut şekli hesaplamaktan çok daha ucuz.
 */
private fun createCloudSprite(density: Density): ImageBitmap {
    val width = with(density) { 300.dp.toPx() }
    val height = with(density) { 150.dp.toPx() }
    val bitmap = ImageBitmap(width.toInt(), height.toInt())
    // (x, y, yarıçap): x genişliğe, y ve yarıçap yüksekliğe oranla.
    val puffs = listOf(
        Triple(0.22f, 0.64f, 0.30f),
        Triple(0.38f, 0.56f, 0.40f),
        Triple(0.56f, 0.52f, 0.44f),
        Triple(0.74f, 0.60f, 0.36f),
        Triple(0.86f, 0.68f, 0.26f),
        Triple(0.50f, 0.70f, 0.28f),
    )
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width, height)) {
        for ((cx, cy, r) in puffs) {
            val center = Offset(cx * width, cy * height)
            val radius = r * height
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.7f),
                    0.55f to Color.White.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
    return bitmap
}
