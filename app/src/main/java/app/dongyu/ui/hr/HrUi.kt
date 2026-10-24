package app.dongyu.ui.hr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dongyu.R
import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrZones
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.stats.HrTrendPoint
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.MetricTile
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.theme.extraColors
import kotlin.math.max

object HrColors {
    val zones = listOf(Color(0xFF8DA2B5), Color(0xFF3F9BFF), Color(0xFF19B36B), Color(0xFFFFA41B), Color(0xFFF0434F))
    val heart = Color(0xFFF0434F)
    val avg = Color(0xFFFF7A59)
}

@Composable
fun zoneName(i: Int): String = stringResource(
    when (i) { 0 -> R.string.hr_zone_1; 1 -> R.string.hr_zone_2; 2 -> R.string.hr_zone_3; 3 -> R.string.hr_zone_4; else -> R.string.hr_zone_5 },
)

private fun fmtZoneTime(sec: Long): String =
    if (sec >= 3600) Pace.formatDuration(sec) else "%d:%02d".format(sec / 60, sec % 60)

/** Five horizontal bars with zone name, bpm range, time and share. */
@Composable
fun ZoneBars(zoneSec: LongArray, maxHr: Int?, modifier: Modifier = Modifier) {
    val total = zoneSec.sum().coerceAtLeast(1)
    val peak = zoneSec.max().coerceAtLeast(1)
    val bounds = maxHr?.let { HrZones.bounds(it) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (z in HrZones.COUNT - 1 downTo 0) {
            val c = HrColors.zones[z]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c), contentAlignment = Alignment.Center) {
                    Text("Z${z + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.width(72.dp)) {
                    Text(zoneName(z), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    bounds?.let { b ->
                        Text(
                            if (z == 0) "<${b[0].last + 1}" else stringResource(R.string.hr_bpm_range, b[z].first, b[z].last),
                            style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, maxLines = 1,
                        )
                    }
                }
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(extraColors.cardAlt)) {
                    val f = zoneSec[z].toFloat() / peak
                    if (f > 0) Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.03f)).height(12.dp).clip(RoundedCornerShape(50)).background(c))
                }
                Spacer(Modifier.width(8.dp))
                Text(fmtZoneTime(zoneSec[z]), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp), maxLines = 1)
                Text("${(zoneSec[z] * 100 / total)}%", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.width(34.dp), maxLines = 1)
            }
        }
    }
}

/** One stacked bar showing the zone mix, with a legend underneath. */
@Composable
fun ZoneStrip(zoneSec: LongArray, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val total = zoneSec.sum().toFloat().coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50))) {
            zoneSec.forEachIndexed { z, s -> if (s > 0) Box(Modifier.weight(s / total).height(height).background(HrColors.zones[z])) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            zoneSec.indices.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(HrColors.zones[z]))
                    Spacer(Modifier.width(4.dp))
                    Text("Z${z + 1} ${(zoneSec[z] * 100 / total).toInt()}%", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
            }
        }
    }
}

/** Heart-rate over time, filled with the zone colour under each segment. */
@Composable
fun HrCurve(samples: List<HrSample>, maxHr: Int, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val measurer = rememberTextMeasurer()
    val subtle = extraColors.subtle
    val grid = extraColors.grid
    val style = TextStyle(fontSize = 10.sp, color = subtle)
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (samples.size < 2) return@Canvas
        val left = 34.dp.toPx()
        val bottom = 16.dp.toPx()
        val top = 6.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom - top
        val lo = (samples.minOf { it.bpm } - 8).coerceAtLeast(30).toFloat()
        val hi = (samples.maxOf { it.bpm } + 6).toFloat()
        val t0 = samples.first().time
        val span = max(1L, samples.last().time - t0).toFloat()
        fun x(t: Long) = left + (t - t0) / span * w
        fun y(b: Int) = top + (1 - (b - lo) / (hi - lo)) * h
        // zone boundary guides
        HrZones.bounds(maxHr).drop(1).forEachIndexed { i, r ->
            val yy = y(r.first)
            if (yy in top..(top + h)) {
                drawLine(HrColors.zones[i + 1].copy(alpha = 0.5f), Offset(left, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f)))
                val tl = measurer.measure(r.first.toString(), style)
                drawText(tl, topLeft = Offset(left - tl.size.width - 5.dp.toPx(), yy - tl.size.height / 2))
            }
        }
        drawLine(grid, Offset(left, top + h), Offset(size.width, top + h), 1.dp.toPx())
        // per-segment zone fill
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            val z = HrZones.zoneOf(b.bpm, maxHr)
            val p = Path().apply {
                moveTo(x(a.time), top + h); lineTo(x(a.time), y(a.bpm)); lineTo(x(b.time), y(b.bpm)); lineTo(x(b.time), top + h); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(HrColors.zones[z].copy(alpha = 0.55f), HrColors.zones[z].copy(alpha = 0.08f)), startY = top, endY = top + h))
        }
        val line = Path().apply {
            moveTo(x(samples[0].time), y(samples[0].bpm))
            samples.drop(1).forEach { lineTo(x(it.time), y(it.bpm)) }
        }
        drawPath(line, HrColors.heart, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // x labels in minutes
        listOf(0f, 0.5f, 1f).forEach { f ->
            val tl = measurer.measure("${(span * f / 60000).toInt()}′", style)
            val xx = (left + f * w - tl.size.width / 2).coerceIn(left, size.width - tl.size.width)
            drawText(tl, topLeft = Offset(xx, size.height - tl.size.height))
        }
    }
}

/** Avg (filled) and max (dashed) HR per bucket. */
@Composable
fun HrTrendChart(points: List<HrTrendPoint>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val measurer = rememberTextMeasurer()
    val subtle = extraColors.subtle
    val grid = extraColors.grid
    val style = TextStyle(fontSize = 10.sp, color = subtle)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (points.isEmpty()) return@Canvas
            val left = 34.dp.toPx(); val bottom = 16.dp.toPx(); val top = 8.dp.toPx()
            val w = size.width - left; val h = size.height - bottom - top
            val lo = (points.minOf { it.avg } - 10).coerceAtLeast(40.0).toFloat()
            val hi = (points.maxOf { it.max } + 6).toFloat()
            val n = points.size
            val step = if (n > 1) w / (n - 1) else 0f
            fun x(i: Int) = left + if (n > 1) i * step else w / 2
            fun y(v: Double) = top + (1 - (v.toFloat() - lo) / (hi - lo)) * h
            for (k in 0..3) {
                val v = lo + (hi - lo) * k / 3
                val yy = y(v.toDouble())
                drawLine(grid, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val tl = measurer.measure(v.toInt().toString(), style)
                drawText(tl, topLeft = Offset(left - tl.size.width - 5.dp.toPx(), yy - tl.size.height / 2))
            }
            val avgPath = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.avg)) else lineTo(x(i), y(p.avg)) } }
            val area = Path().apply { addPath(avgPath); lineTo(x(n - 1), top + h); lineTo(x(0), top + h); close() }
            drawPath(area, Brush.verticalGradient(listOf(HrColors.avg.copy(alpha = 0.3f), HrColors.avg.copy(alpha = 0.02f)), startY = top, endY = top + h))
            drawPath(avgPath, HrColors.avg, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val maxPath = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.max)) else lineTo(x(i), y(p.max)) } }
            drawPath(maxPath, HrColors.heart, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            points.forEachIndexed { i, p ->
                drawCircle(Color.White, 3.5.dp.toPx(), Offset(x(i), y(p.avg))); drawCircle(HrColors.avg, 2.3.dp.toPx(), Offset(x(i), y(p.avg)))
                drawCircle(HrColors.heart, 2.5.dp.toPx(), Offset(x(i), y(p.max)))
            }
            if (labels.size == n) {
                val idx = if (n <= 6) labels.indices.toList() else listOf(0, n / 2, n - 1)
                idx.forEach { i ->
                    val tl = measurer.measure(labels[i], style)
                    drawText(tl, topLeft = Offset((x(i) - tl.size.width / 2).coerceIn(left - 4.dp.toPx(), size.width - tl.size.width), size.height - tl.size.height))
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 3.dp).background(HrColors.avg))
            Text(" " + stringResource(R.string.hr_legend_avg), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(width = 14.dp, height = 3.dp).background(HrColors.heart))
            Text(" " + stringResource(R.string.hr_legend_max), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
        }
    }
}

/** Workout-detail card: avg/max/min, curve, time in zones. */
@Composable
fun HrDetailCard(avg: Int?, peak: Int?, min: Int?, samples: List<HrSample>, zoneSec: LongArray?, maxHr: Int, modifier: Modifier = Modifier) {
    val bpm = stringResource(R.string.unit_bpm)
    DCard(modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.hr_title))
        Row(Modifier.fillMaxWidth()) {
            MetricTile(Icons.Filled.Favorite, HrColors.avg, avg?.toString() ?: "--", bpm, stringResource(R.string.hr_avg), Modifier.weight(1f))
            MetricTile(Icons.Filled.MonitorHeart, HrColors.heart, peak?.toString() ?: "--", bpm, stringResource(R.string.hr_peak), Modifier.weight(1f))
            if (min != null) MetricTile(Icons.Filled.SouthEast, HrColors.zones[1], min.toString(), bpm, stringResource(R.string.hr_min), Modifier.weight(1f))
        }
        if (samples.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.hr_chart), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
            Spacer(Modifier.height(6.dp))
            HrCurve(samples, maxHr)
        }
        if (zoneSec != null && zoneSec.sum() > 0) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.hr_time_in_zone), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
            Spacer(Modifier.height(8.dp))
            ZoneBars(zoneSec, maxHr)
        }
    }
}
