package com.alihaydarsayar.communesky.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.model.detail.Chart
import com.alihaydarsayar.communesky.model.detail.ChartKind
import com.alihaydarsayar.communesky.ui.theme.RainAccent
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.theme.TextTertiary
import kotlin.math.roundToInt

/**
 * Ayrıntı sayfalarının ortak grafiği: çizgi (altı dolgulu) ya da çubuk; kesikli ikinci çizgi, "şimdi" noktası,
 * yatay kılavuz çizgileri ve eksen yazıları, altta saat etiketleri, istenirse rüzgâr yönü okları. Dokununca ya
 * da parmak sürüklenince o saat seçilir ([onSelect]); değerini sayfa grafiğin üstünde yazar.
 *
 * Veri bir kez hazırlanır ([Chart]) ve çizim sırasında yeniden hesaplanmaz; kaydırma akıcı kalır.
 */
@Composable
fun MetricChart(
    chart: Chart,
    selected: Int?,
    onSelect: (Int) -> Unit,
    hourLabel: (Int) -> String,
    axisLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = RainAccent,
    showDirections: Boolean = false,
    /** İkinci değer kesikli çizgiyle çizilsin mi (aynı ölçekte ya da yüzde olarak anlamlıysa). */
    showSecondary: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 10.sp)
    val count = chart.points.size
    // Eksen ve saat yazıları bir kez ölçülür.
    val axis = remember(chart, axisLabel) { listOf(chart.max, (chart.max + chart.min) / 2, chart.min).map { axisLabel(it) } }
    val hours = remember(chart, hourLabel) { (0 until count step 6).map { it to hourLabel(chart.points[it].time.hour) } }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (showDirections) 190.dp else 170.dp)
            .clearAndSetSemantics { }
            .pointerInput(chart) { detectTapGestures { onSelect(indexAt(it.x, size.width - AxisGutter.toPx(), count, chart.kind)) } }
            .pointerInput(chart) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect(indexAt(change.position.x, size.width - AxisGutter.toPx(), count, chart.kind))
                }
            },
    ) {
        if (count < 2) return@Canvas
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx() - (if (showDirections) 20.dp.toPx() else 0f)
        val plot = bottom - top
        val span = (chart.max - chart.min).takeIf { it > 0 } ?: 1.0
        // Sağda eksen yazıları için dar bir şerit; çizim alanı onun soluna kadar.
        val width = size.width - AxisGutter.toPx()
        fun x(index: Float) = index / (count - 1) * width
        fun y(value: Double) = bottom - ((value - chart.min) / span).toFloat().coerceIn(0f, 1f) * plot
        fun ySecondary(value: Double) = if (chart.secondaryPercent) bottom - (value / 100).toFloat().coerceIn(0f, 1f) * plot else y(value)

        // Kılavuz çizgileri ve eksen yazıları (üst, orta, alt).
        axis.forEachIndexed { index, text ->
            val lineY = top + plot * index / 2f
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, lineY), Offset(width, lineY), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(text, axisStyle)
            val textY = (lineY - layout.size.height / 2f).coerceIn(0f, bottom - layout.size.height + 4.dp.toPx())
            drawText(layout, topLeft = Offset(size.width - layout.size.width, textY))
        }

        val values = chart.points.mapIndexedNotNull { index, point -> point.value?.let { index to it } }
        when (chart.kind) {
            ChartKind.Line -> {
                val line = smoothPath(values.map { Offset(x(it.first.toFloat()), y(it.second)) })
                if (values.isNotEmpty()) {
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(x(values.last().first.toFloat()), bottom)
                        lineTo(x(values.first().first.toFloat()), bottom)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), startY = top, endY = bottom))
                }
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            ChartKind.Bars -> {
                val slot = width / count
                val bar = slot * 0.62f
                for ((index, value) in values) {
                    val barTop = y(value)
                    if (bottom - barTop < 0.5f) continue
                    drawRoundRect(
                        color = color.copy(alpha = if (selected == index) 1f else 0.85f),
                        topLeft = Offset(slot * index + (slot - bar) / 2, barTop),
                        size = Size(bar, bottom - barTop),
                        cornerRadius = CornerRadius(bar / 2.5f),
                    )
                }
            }
        }
        // İkinci değer (gerçek sıcaklık, hamle, yağış ihtimali): ince kesikli çizgi.
        val secondary = chart.points.mapIndexedNotNull { index, point -> point.secondary?.let { index to it } }
        if (showSecondary && secondary.size >= 2) {
            fun sx(index: Int) = if (chart.kind == ChartKind.Bars) width / count * (index + 0.5f) else x(index.toFloat())
            drawPath(
                smoothPath(secondary.map { Offset(sx(it.first), ySecondary(it.second)) }),
                Color.White.copy(alpha = 0.55f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
            )
        }

        fun pointX(position: Float) = if (chart.kind == ChartKind.Bars) width / count * (position + 0.5f) else x(position)

        /** [position] (kesirli saat sırası) yerindeki değer: iki komşu saatin karışımı. */
        fun valueAt(position: Float): Double? {
            val before = chart.points.getOrNull(position.toInt())?.value ?: return null
            val after = chart.points.getOrNull(position.toInt() + 1)?.value ?: return before
            return before + (after - before) * (position - position.toInt())
        }

        // Şimdi: dolu nokta ve çevresinde hale.
        chart.nowPosition?.let { now ->
            val position = now.coerceIn(0f, count - 1f)
            val value = if (chart.kind == ChartKind.Bars) chart.points.getOrNull(position.toInt())?.value else valueAt(position)
            if (value != null) {
                val center = Offset(pointX(if (chart.kind == ChartKind.Bars) position.toInt().toFloat() else position), y(value))
                drawCircle(Color.White.copy(alpha = 0.28f), 9.dp.toPx(), center)
                drawCircle(Color.White, 4.5.dp.toPx(), center)
            }
        }
        // Seçilen saat: dikey çizgi ve nokta.
        selected?.let { index ->
            val value = chart.points.getOrNull(index)?.value ?: return@let
            val center = Offset(pointX(index.toFloat()), y(value))
            drawLine(Color.White.copy(alpha = 0.45f), Offset(center.x, top), Offset(center.x, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(color, 5.dp.toPx(), center)
            drawCircle(Color.White, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }

        // Rüzgâr yönü okları: üç saatte bir, rüzgârın estiği yöne doğru.
        if (showDirections) {
            val arrowY = bottom + 12.dp.toPx()
            for (index in 0 until count step 3) {
                val direction = chart.points[index].direction ?: continue
                drawArrow(Offset(pointX(index.toFloat()).coerceIn(6.dp.toPx(), width - 6.dp.toPx()), arrowY), direction + 180f)
            }
        }
        // Saat etiketleri: altı saatte bir.
        val labelY = size.height - 16.dp.toPx()
        // Öncekinin üstüne binecek etiket atlanır (48 saatlik grafikte "9 PM" ile "3 AM" birbirine yapışıyordu).
        var drawnUntil = Float.NEGATIVE_INFINITY
        for ((index, text) in hours) {
            val layout = measurer.measure(text, labelStyle)
            val left = (pointX(index.toFloat()) - layout.size.width / 2f).coerceIn(0f, width - layout.size.width)
            if (left < drawnUntil + 6.dp.toPx()) continue
            drawText(layout, topLeft = Offset(left, labelY))
            drawnUntil = left + layout.size.width
        }
    }
}

/** Dokunulan yatay yerdeki saat: çizgide en yakın nokta, çubukta içine dokunulan sütun. */
private val AxisGutter = 34.dp

private fun indexAt(x: Float, width: Float, count: Int, kind: ChartKind): Int = when {
    width <= 0f || count < 2 -> 0
    kind == ChartKind.Bars -> (x / width * count).toInt().coerceIn(0, count - 1)
    else -> (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Noktalardan geçen yumuşak eğri. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val middle = (previous.x + current.x) / 2
        cubicTo(middle, previous.y, middle, current.y, current.x, current.y)
    }
}

/** Küçük ok; [degrees] 0 iken yukarıyı (kuzeyi) gösterir. */
private fun DrawScope.drawArrow(center: Offset, degrees: Float) {
    val half = 6.dp.toPx()
    rotate(degrees % 360f, center) {
        val tip = Offset(center.x, center.y - half)
        drawLine(Color.White.copy(alpha = 0.75f), Offset(center.x, center.y + half), tip, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.75f), tip, Offset(center.x - 3.5.dp.toPx(), center.y - half + 4.5.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.75f), tip, Offset(center.x + 3.5.dp.toPx(), center.y - half + 4.5.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}
