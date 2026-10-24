package app.dongyu.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dongyu.ui.theme.extraColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

/** Grow-in animation progress (skipped in previews/screenshots). */
@Composable
private fun rememberReveal(key: Any?): Float {
    if (LocalInspectionMode.current) return 1f
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return anim.value
}

private fun niceRange(minV: Float, maxV: Float): Pair<Float, Float> {
    if (minV == maxV) return (minV - 1f) to (maxV + 1f)
    val pad = (maxV - minV) * 0.12f
    return (minV - pad) to (maxV + pad)
}

/**
 * Smooth area line chart. [invert] flips the y-axis (lower values drawn higher, e.g. pace).
 */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    xLabels: List<String> = emptyList(),
    yFormatter: (Float) -> String = { it.toInt().toString() },
    invert: Boolean = false,
    zeroBased: Boolean = false,
    showDots: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val subtle = extraColors.subtle
    val grid = extraColors.grid
    val reveal = rememberReveal(values)
    val labelStyle = TextStyle(fontSize = 10.sp, color = subtle)
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val leftPad = 44.dp.toPx()
        val bottomPad = if (xLabels.isNotEmpty()) 18.dp.toPx() else 4.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad - 6.dp.toPx()
        val top = 6.dp.toPx()
        var (lo, hi) = niceRange(values.min(), values.max())
        if (zeroBased) lo = 0f
        fun y(v: Float): Float {
            val f = (v - lo) / (hi - lo)
            return top + if (invert) f * h else (1 - f) * h
        }
        // grid + y labels
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3f
            val yy = y(v)
            drawLine(grid, Offset(leftPad, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val tl = measurer.measure(yFormatter(v), labelStyle)
            drawText(tl, topLeft = Offset(leftPad - tl.size.width - 6.dp.toPx(), yy - tl.size.height / 2))
        }
        val n = values.size
        val step = if (n > 1) w / (n - 1) else 0f
        val pts = values.mapIndexed { i, v -> Offset(leftPad + i * step, y(v)) }
        val visible = max(1, (n * reveal).toInt())
        val line = smoothPath(pts.take(visible))
        val area = Path().apply {
            addPath(line)
            lineTo(pts[visible - 1].x, top + h)
            lineTo(pts.first().x, top + h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f)), startY = top, endY = top + h))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showDots || n <= 16) pts.take(visible).forEach { drawCircle(Color.White, 3.5.dp.toPx(), it); drawCircle(color, 2.2.dp.toPx(), it) }
        else drawCircle(color, 4.dp.toPx(), pts[visible - 1])
        // x labels: first, middle, last (or all if few)
        if (xLabels.isNotEmpty()) {
            val idx = if (xLabels.size <= 6) xLabels.indices.toList() else listOf(0, xLabels.size / 2, xLabels.lastIndex)
            idx.forEach { i ->
                val tl = measurer.measure(xLabels[i], labelStyle)
                val x = (leftPad + i * (if (xLabels.size > 1) w / (xLabels.size - 1) else 0f) - tl.size.width / 2)
                    .coerceIn(leftPad - 4.dp.toPx(), size.width - tl.size.width.toFloat())
                drawText(tl, topLeft = Offset(x, size.height - tl.size.height))
            }
        }
    }
}

private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val cx = (p0.x + p1.x) / 2
        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
    }
}

data class StackSegment(val value: Float, val color: Color)

/** Vertical stacked bars with rounded tops. */
@Composable
fun StackedBarChart(
    bars: List<List<StackSegment>>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    yFormatter: (Float) -> String = { it.toInt().toString() },
    highlightLast: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val subtle = extraColors.subtle
    val grid = extraColors.grid
    val empty = extraColors.cardAlt
    val reveal = rememberReveal(bars)
    val labelStyle = TextStyle(fontSize = 10.sp, color = subtle)
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (bars.isEmpty()) return@Canvas
        val leftPad = 36.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val top = 6.dp.toPx()
        val h = size.height - bottomPad - top
        val maxV = max(1f, bars.maxOf { b -> b.sumOf { it.value.toDouble() }.toFloat() }) * 1.1f
        for (i in 0..2) {
            val v = maxV * i / 2f
            val yy = top + h - h * (v / maxV)
            drawLine(grid, Offset(leftPad, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val tl = measurer.measure(yFormatter(v), labelStyle)
            drawText(tl, topLeft = Offset(leftPad - tl.size.width - 6.dp.toPx(), yy - tl.size.height / 2))
        }
        val slot = (size.width - leftPad) / bars.size
        val bw = min(slot * 0.58f, 22.dp.toPx())
        bars.forEachIndexed { i, segs ->
            val x = leftPad + i * slot + (slot - bw) / 2
            var y = top + h
            val total = segs.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) {
                drawRoundRect(empty, Offset(x, top + h - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(2f, 2f))
            }
            val alpha = if (!highlightLast || i == bars.lastIndex) 1f else 0.85f
            segs.filter { it.value > 0 }.forEachIndexed { si, s ->
                val sh = h * (s.value / maxV) * reveal
                val isTop = si == segs.count { it.value > 0 } - 1
                y -= sh
                if (isTop) {
                    val r = min(bw / 2, sh)
                    drawRoundRect(s.color.copy(alpha = alpha), Offset(x, y), Size(bw, sh), CornerRadius(r, r))
                    drawRect(s.color.copy(alpha = alpha), Offset(x, y + r), Size(bw, sh - r))
                } else drawRect(s.color.copy(alpha = alpha), Offset(x, y), Size(bw, sh + 0.5f))
            }
            if (labels.size == bars.size && (bars.size <= 8 || i % 2 == (bars.lastIndex % 2))) {
                val tl = measurer.measure(labels[i], labelStyle)
                drawText(tl, topLeft = Offset(x + bw / 2 - tl.size.width / 2, size.height - tl.size.height))
            }
        }
    }
}

data class DonutSlice(val value: Float, val color: Color, val label: String, val valueText: String)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    val reveal = rememberReveal(slices)
    val track = extraColors.cardAlt
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val total = slices.sumOf { it.value.toDouble() }.toFloat()
                if (total <= 0) return@Canvas
                var start = -90f
                val gap = if (slices.size > 1) 2.5f else 0f
                slices.forEach { s ->
                    val sweep = 360f * s.value / total * reveal
                    if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = app.dongyu.ui.theme.NumberStyle.Medium)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
            slices.take(6).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(s.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(s.valueText, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("${(s.value / total * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.width(32.dp))
                }
            }
        }
    }
}

/**
 * GitHub-style activity heatmap: columns are weeks (Mon-Sun rows), ending at [end].
 */
@Composable
fun CalendarHeatmap(
    data: Map<LocalDate, Double>,
    end: LocalDate,
    weeks: Int,
    color: Color,
    monthLabel: (LocalDate) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val subtle = extraColors.subtle
    val empty = extraColors.heat0
    val labelStyle = TextStyle(fontSize = 9.sp, color = subtle)
    val maxV = data.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val firstMonday = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1L)
    Canvas(modifier.fillMaxWidth().height(126.dp)) {
        val top = 14.dp.toPx()
        val gap = 3.dp.toPx()
        val cell = min((size.width - gap * (weeks - 1)) / weeks, (size.height - top - gap * 6) / 7)
        var lastMonth = -1
        for (wk in 0 until weeks) {
            val monday = firstMonday.plusWeeks(wk.toLong())
            if (monday.monthValue != lastMonth) {
                if (wk < weeks - 2) {
                    val tl = measurer.measure(monthLabel(monday), labelStyle)
                    drawText(tl, topLeft = Offset(wk * (cell + gap), 0f))
                }
                lastMonth = monday.monthValue
            }
            for (d in 0 until 7) {
                val date = monday.plusDays(d.toLong())
                if (date.isAfter(end)) continue
                val v = data[date] ?: 0.0
                val c = if (v <= 0) empty else {
                    val f = (v / maxV).coerceIn(0.0, 1.0)
                    val level = when { f < 0.25 -> 0.3f; f < 0.5 -> 0.5f; f < 0.75 -> 0.75f; else -> 1f }
                    color.copy(alpha = level)
                }
                drawRoundRect(c, Offset(wk * (cell + gap), top + d * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.28f))
            }
        }
    }
}

/** Horizontal progress-like bars, used for per-km splits. */
@Composable
fun HBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val track = extraColors.cardAlt
    Canvas(modifier.height(height)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)),
            size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
    }
}

/** Tiny sparkline without axes. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max().let { if (it == lo) lo + 1 else it }
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - (v - lo) / (hi - lo) * size.height) }
        drawPath(smoothPath(pts), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Draws a GPS path scaled into the canvas; used for the live screen and as an offline map fallback. */
fun DrawScope.drawTrack(path: List<Pair<Double, Double>>, color: Color, strokeWidth: Float, padding: Float) {
    if (path.size < 2) return
    val lats = path.map { it.first }
    val lons = path.map { it.second }
    val minLat = lats.min(); val maxLat = lats.max(); val minLon = lons.min(); val maxLon = lons.max()
    val cosLat = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
    val spanX = ((maxLon - minLon) * cosLat).coerceAtLeast(1e-6)
    val spanY = (maxLat - minLat).coerceAtLeast(1e-6)
    val scale = min((size.width - 2 * padding) / spanX, (size.height - 2 * padding) / spanY)
    val offX = (size.width - spanX * scale) / 2
    val offY = (size.height - spanY * scale) / 2
    fun p(lat: Double, lon: Double) = Offset((offX + (lon - minLon) * cosLat * scale).toFloat(), (offY + (maxLat - lat) * scale).toFloat())
    val pth = Path()
    path.forEachIndexed { i, (la, lo) -> val o = p(la, lo); if (i == 0) pth.moveTo(o.x, o.y) else pth.lineTo(o.x, o.y) }
    drawPath(pth, Color.White.copy(alpha = 0.9f), style = Stroke(strokeWidth + 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(pth, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val s = p(path.first().first, path.first().second)
    val e = p(path.last().first, path.last().second)
    drawCircle(Color.White, strokeWidth * 1.6f, s); drawCircle(Color(0xFF19B36B), strokeWidth * 1.1f, s)
    drawCircle(Color.White, strokeWidth * 1.6f, e); drawCircle(Color(0xFFE5484D), strokeWidth * 1.1f, e)
}

fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
