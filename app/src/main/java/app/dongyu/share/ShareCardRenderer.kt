package app.dongyu.share

import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

enum class CardAspect(val w: Int, val h: Int) {
    /** 3:4 — Xiaohongshu / RedNote feed. */
    PORTRAIT(1080, 1440),
    SQUARE(1080, 1080),
    /** 9:16 — stories / WeChat Moments full screen. */
    STORY(1080, 1920),
}

enum class CardStyle { AURORA, PAPER, MAP }

data class CardMetric(val value: String, val unit: String, val label: String, val icon: ImageVector)

data class WorkoutCardData(
    val sportName: String,
    val sportColor: Color,
    val sportIcon: ImageVector,
    val dateText: String,
    val hero: CardMetric,
    val metrics: List<CardMetric>,
    val track: List<Pair<Double, Double>>,
    val hr: List<Int> = emptyList(),
    val hrLabel: String = "",
    val brand: String,
    val tagline: String,
)

data class ReportSportRow(val name: String, val color: Color, val icon: ImageVector, val detail: String, val fraction: Float)

data class ReportCardData(
    val title: String,
    val range: String,
    val accent: Color,
    val hero: CardMetric,
    val metrics: List<CardMetric>,
    val bars: List<Float>,
    val barLabels: List<String>,
    val chartTitle: String,
    val sports: List<ReportSportRow>,
    val emptyText: String,
    val brand: String,
    val tagline: String,
)

/** Map snapshot covering the whole card, produced with the same [TrackProjection]. */
class MapBackground(val image: ImageBitmap, val attribution: String = "© OpenStreetMap contributors")

private class Palette(
    val text: Color, val sub: Color, val faint: Color, val panel: Color, val dark: Boolean,
)

/**
 * Draws share cards straight into a bitmap (no view capture), so the preview, the saved PNG and tests
 * all use the same pixels. All sizes are in card pixels on a 1080-wide canvas.
 */
class ShareCardRenderer(private val measurer: TextMeasurer) {

    fun workoutProjection(d: WorkoutCardData, aspect: CardAspect): TrackProjection? =
        if (d.track.size < 2) null else TrackProjection.fit(d.track, trackArea(aspect, d.hr.isNotEmpty()), aspect.w, aspect.h)

    // ---- layout ------------------------------------------------------------------------------

    private fun topPad(a: CardAspect) = if (a == CardAspect.STORY) 150f else 64f
    private fun bottomPad(a: CardAspect) = if (a == CardAspect.STORY) 150f else 56f
    private fun headerBottom(a: CardAspect) = topPad(a) + 112f
    private fun footerTop(a: CardAspect) = a.h - bottomPad(a) - 64f
    private fun metricsHeight(a: CardAspect, hr: Boolean) = when (a) {
        CardAspect.SQUARE -> 330f
        else -> 400f + if (hr) 150f else 0f
    }
    private fun trackArea(a: CardAspect, hr: Boolean): PxRect {
        val top = headerBottom(a) + 40f
        val bottom = footerTop(a) - metricsHeight(a, hr) - 30f
        return PxRect(90f, top, a.w - 90f, bottom)
    }

    // ---- workout card ------------------------------------------------------------------------

    fun renderWorkout(d: WorkoutCardData, aspect: CardAspect, style: CardStyle, map: MapBackground? = null): ImageBitmap =
        render(aspect) {
            val hasHr = d.hr.size >= 2 && aspect != CardAspect.SQUARE
            val proj = workoutProjection(d, aspect)
            val useMap = style == CardStyle.MAP && map != null && proj != null
            val pal = background(style.takeUnless { it == CardStyle.MAP && !useMap } ?: CardStyle.AURORA, d.sportColor, aspect)
            if (useMap) {
                drawImage(map!!.image, dstSize = IntSize(aspect.w, aspect.h))
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.22f to Color.Transparent, 0.5f to Color.Transparent, 0.75f to Color.Black.copy(alpha = 0.7f), 1f to Color.Black.copy(alpha = 0.88f)))
            }
            header(d, aspect, pal)
            val area = trackArea(aspect, hasHr)
            if (proj != null) {
                drawTrack(d.track, proj, if (pal.dark) Color.White else d.sportColor, d.sportColor, glow = pal.dark)
            } else {
                emblem(d, area, pal)
            }
            var y = footerTop(aspect) - metricsHeight(aspect, hasHr)
            y = heroBlock(d.hero, y, pal, d.sportColor, compact = aspect == CardAspect.SQUARE)
            y = metricGrid(d.metrics, y + 26f, pal, aspect)
            if (hasHr) hrStrip(d.hr, d.hrLabel, y + 24f, pal)
            footer(d.brand, d.tagline, aspect, pal)
            if (useMap) text(map!!.attribution, aspect.w - 24f, aspect.h - 40f, 18f, Color.White.copy(alpha = 0.55f), align = 2)
        }

    private fun DrawScope.header(d: WorkoutCardData, a: CardAspect, pal: Palette) {
        val top = topPad(a)
        drawRoundRect(d.sportColor, Offset(72f, top), Size(112f, 112f), CornerRadius(32f))
        icon(d.sportIcon, 72f + 22f, top + 22f, 68f, Color.White)
        text(d.sportName, 212f, top + 6f, 50f, pal.text, FontWeight.Bold, maxWidth = 620)
        text(d.dateText, 212f, top + 68f, 30f, pal.sub, maxWidth = 620)
        logo(a.w - 72f - 76f, top + 18f, 76f)
    }

    private fun DrawScope.emblem(d: WorkoutCardData, area: PxRect, pal: Palette) {
        val r = min(area.width, area.height) / 2f * 0.78f
        val c = Offset(area.cx, area.cy)
        drawCircle(Brush.radialGradient(listOf(d.sportColor.copy(alpha = 0.42f), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        for (i in 1..3) drawCircle(pal.text.copy(alpha = 0.07f), r + i * 46f, c, style = Stroke(3f))
        drawCircle(d.sportColor, r * 0.72f, c)
        val s = r * 0.8f
        icon(d.sportIcon, c.x - s / 2, c.y - s / 2, s, Color.White)
    }

    private fun DrawScope.drawTrack(track: List<Pair<Double, Double>>, p: TrackProjection, line: Color, accent: Color, glow: Boolean) {
        val path = Path()
        track.forEachIndexed { i, (la, lo) ->
            val (x, y) = p.project(la, lo)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (glow) {
            drawPath(path, accent.copy(alpha = 0.25f), style = Stroke(44f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, accent.copy(alpha = 0.55f), style = Stroke(22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawPath(path, Color.Black.copy(alpha = 0.08f), style = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(path, line, style = Stroke(11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (sx, sy) = p.project(track.first().first, track.first().second)
        val (ex, ey) = p.project(track.last().first, track.last().second)
        dot(Offset(sx, sy), Color(0xFF19B36B))
        dot(Offset(ex, ey), Color(0xFFE5484D))
    }

    private fun DrawScope.dot(c: Offset, color: Color) {
        drawCircle(Color.Black.copy(alpha = 0.2f), 22f, c + Offset(0f, 3f))
        drawCircle(Color.White, 20f, c)
        drawCircle(color, 13f, c)
    }

    private fun DrawScope.heroBlock(m: CardMetric, top: Float, pal: Palette, accent: Color, compact: Boolean): Float {
        val size = if (compact) 128f else 168f
        val v = text(m.value, 72f, top, size, pal.text, FontWeight.Black)
        if (m.unit.isNotEmpty()) {
            val baseline = top + v.firstBaseline
            val u = measure(m.unit, 44f, FontWeight.Bold)
            drawText(u, pal.sub, Offset(72f + v.size.width + 14f, baseline - u.firstBaseline))
        }
        val labelTop = top + v.size.height - (if (compact) 4f else 8f)
        drawRoundRect(accent, Offset(72f, labelTop + 10f), Size(8f, 30f), CornerRadius(4f))
        text(m.label, 92f, labelTop + 4f, 30f, pal.sub, FontWeight.Medium)
        return labelTop + 44f
    }

    private fun DrawScope.metricGrid(ms: List<CardMetric>, top: Float, pal: Palette, a: CardAspect): Float {
        if (ms.isEmpty()) return top
        val shown = ms.take(if (a == CardAspect.SQUARE) 3 else 4)
        val cols = shown.size
        val gap = 18f
        val w = (a.w - 144f - gap * (cols - 1)) / cols
        val h = 150f
        shown.forEachIndexed { i, m ->
            val x = 72f + i * (w + gap)
            drawRoundRect(pal.panel, Offset(x, top), Size(w, h), CornerRadius(28f))
            icon(m.icon, x + 24f, top + 22f, 34f, pal.sub)
            text(m.label, x + 66f, top + 24f, 26f, pal.sub, maxWidth = (w - 80f).toInt())
            val v = text(m.value, x + 24f, top + 66f, if (cols >= 4) 50f else 56f, pal.text, FontWeight.Bold, maxWidth = (w - 40f).toInt())
            if (m.unit.isNotEmpty() && v.size.width < w - 70f) {
                val u = measure(m.unit, 24f, FontWeight.Medium, maxWidth = (w - 40f - v.size.width - 8f).toInt())
                drawText(u, pal.sub, Offset(x + 24f + v.size.width + 6f, top + 66f + v.firstBaseline - u.firstBaseline))
            }
        }
        return top + h
    }

    private fun DrawScope.hrStrip(hr: List<Int>, label: String, top: Float, pal: Palette) {
        val h = 120f
        val left = 72f; val right = size.width - 72f
        drawRoundRect(pal.panel, Offset(left, top), Size(right - left, h), CornerRadius(28f))
        icon(androidx.compose.material.icons.Icons.Filled.Favorite, left + 26f, top + 24f, 40f, Color(0xFFE5484D))
        text(label, left + 78f, top + 26f, 32f, pal.text, FontWeight.Bold, maxWidth = 260)
        val cl = left + 360f; val cr = right - 30f; val ct = top + 20f; val cb = top + h - 20f
        val lo = hr.min().toFloat() - 5f; val hi = hr.max().toFloat() + 5f
        val path = Path(); val fill = Path()
        hr.forEachIndexed { i, b ->
            val x = cl + (cr - cl) * i / (hr.size - 1)
            val y = cb - (cb - ct) * (b - lo) / (hi - lo)
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, cb); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(cr, cb); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFE5484D).copy(alpha = 0.35f), Color.Transparent), ct, cb))
        drawPath(path, Color(0xFFE5484D), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.footer(brand: String, tagline: String, a: CardAspect, pal: Palette) {
        val top = footerTop(a) + 8f
        drawLine(pal.faint, Offset(72f, top - 14f), Offset(a.w - 72f, top - 14f), 2f)
        val b = text(brand, 72f, top + 6f, 34f, pal.text, FontWeight.Bold)
        text(tagline, 72f + b.size.width + 22f, top + 12f, 28f, pal.sub, maxWidth = (a.w - 144f - b.size.width - 22f).toInt())
    }

    // ---- report card -------------------------------------------------------------------------

    fun renderReport(d: ReportCardData, aspect: CardAspect, style: CardStyle): ImageBitmap = render(aspect) {
        val pal = background(if (style == CardStyle.PAPER) CardStyle.PAPER else CardStyle.AURORA, d.accent, aspect)
        val top = topPad(aspect)
        drawRoundRect(d.accent, Offset(72f, top), Size(112f, 112f), CornerRadius(32f))
        icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, 94f, top + 22f, 68f, Color.White)
        text(d.title, 212f, top + 6f, 50f, pal.text, FontWeight.Bold, maxWidth = 620)
        text(d.range, 212f, top + 68f, 30f, pal.sub, maxWidth = 620)
        logo(aspect.w - 148f, top + 18f, 76f)

        var y = headerBottom(aspect) + 56f
        y = heroBlock(d.hero, y, pal, d.accent, compact = aspect == CardAspect.SQUARE)
        y = metricGrid(d.metrics, y + 26f, pal, aspect) + 44f
        val footer = footerTop(aspect) - 30f
        val sportRows = when (aspect) { CardAspect.SQUARE -> 0; CardAspect.PORTRAIT -> 2; CardAspect.STORY -> 4 }.coerceAtMost(d.sports.size)
        val sportsH = if (sportRows > 0) 60f + sportRows * 92f else 0f
        val chartBottom = footer - sportsH
        text(d.chartTitle, 72f, y, 30f, pal.sub, FontWeight.Medium)
        bars(d.bars, d.barLabels, PxRect(72f, y + 56f, aspect.w - 72f, chartBottom - 20f), d.accent, pal, d.emptyText)
        if (sportRows > 0) {
            var sy = chartBottom + 30f
            d.sports.take(sportRows).forEach { r ->
                drawRoundRect(r.color, Offset(72f, sy), Size(68f, 68f), CornerRadius(20f))
                icon(r.icon, 84f, sy + 12f, 44f, Color.White)
                text(r.name, 162f, sy + 2f, 32f, pal.text, FontWeight.Bold, maxWidth = 360)
                text(r.detail, aspect.w - 72f, sy + 6f, 28f, pal.sub, align = 2)
                val bl = 162f; val br = aspect.w - 72f
                drawRoundRect(pal.faint, Offset(bl, sy + 50f), Size(br - bl, 12f), CornerRadius(6f))
                drawRoundRect(r.color, Offset(bl, sy + 50f), Size(max(12f, (br - bl) * r.fraction), 12f), CornerRadius(6f))
                sy += 92f
            }
        }
        footer(d.brand, d.tagline, aspect, pal)
    }

    private fun DrawScope.bars(v: List<Float>, labels: List<String>, r: PxRect, accent: Color, pal: Palette, empty: String) {
        if (v.isEmpty()) return
        val hi = v.max()
        val labelH = 44f
        val chartB = r.bottom - labelH
        val n = v.size
        val slot = r.width / n
        val bw = slot * if (n > 10) 0.62f else 0.5f
        for (i in 0 until n) {
            val x = r.left + slot * i + (slot - bw) / 2
            drawRoundRect(pal.faint, Offset(x, r.top), Size(bw, chartB - r.top), CornerRadius(bw / 2))
            if (hi > 0 && v[i] > 0) {
                val h = max(bw, (chartB - r.top) * v[i] / hi)
                drawRoundRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.6f)), chartB - h, chartB), Offset(x, chartB - h), Size(bw, h), CornerRadius(bw / 2))
            }
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { text(it, x + bw / 2, chartB + 10f, 24f, pal.sub, align = 1) }
        }
        if (hi <= 0f) text(empty, r.cx, (r.top + chartB) / 2 - 20f, 32f, pal.sub, align = 1)
    }

    // ---- primitives --------------------------------------------------------------------------

    private fun render(a: CardAspect, block: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(a.w, a.h, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(a.w.toFloat(), a.h.toFloat()), block)
        return bmp
    }

    private fun DrawScope.background(style: CardStyle, accent: Color, a: CardAspect): Palette = when (style) {
        CardStyle.PAPER -> {
            drawRect(Color(0xFFF7F5F0))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), Offset(a.w * 0.9f, a.h * 0.12f), a.w * 0.8f), a.w * 0.8f, Offset(a.w * 0.9f, a.h * 0.12f))
            contours(Color.Black.copy(alpha = 0.035f), a)
            Palette(Color(0xFF151A24), Color(0xFF6B7385), Color(0x14000000), Color.White.copy(alpha = 0.85f), dark = false)
        }
        else -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1426), Color(0xFF0E1E33), lerpDark(accent))))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), Offset(a.w * 0.95f, a.h * 0.05f), a.w * 0.9f), a.w * 0.9f, Offset(a.w * 0.95f, a.h * 0.05f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0FA3A3).copy(alpha = 0.3f), Color.Transparent), Offset(0f, a.h * 0.7f), a.w * 0.7f), a.w * 0.7f, Offset(0f, a.h * 0.7f))
            contours(Color.White.copy(alpha = 0.045f), a)
            Palette(Color.White, Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.09f), dark = true)
        }
    }

    private fun lerpDark(c: Color) = Color(c.red * 0.35f, c.green * 0.35f, c.blue * 0.35f)

    /** Topographic-style rings, a nod to the island/mountain logo. */
    private fun DrawScope.contours(color: Color, a: CardAspect) {
        val c = Offset(a.w * 0.82f, a.h * 0.34f)
        for (i in 1..9) {
            val rx = 120f * i; val ry = 84f * i
            drawOval(color, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(2.5f))
        }
    }

    /** The 动屿 mark: teal disc, white peaks, sun and a wave. */
    private fun DrawScope.logo(x: Float, y: Float, s: Float) {
        val c = Offset(x + s / 2, y + s / 2)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF16B3A5), Color(0xFF0B7C8C)), Offset(x, y), Offset(x + s, y + s)), s / 2, c)
        drawCircle(Color(0xFFFFC94D), s * 0.09f, Offset(x + s * 0.69f, y + s * 0.28f))
        val m = Path().apply {
            moveTo(x + s * 0.17f, y + s * 0.67f); lineTo(x + s * 0.39f, y + s * 0.34f); lineTo(x + s * 0.5f, y + s * 0.47f)
            lineTo(x + s * 0.6f, y + s * 0.4f); lineTo(x + s * 0.83f, y + s * 0.67f); close()
        }
        drawPath(m, Color.White)
        val w = Path().apply {
            moveTo(x + s * 0.15f, y + s * 0.76f)
            cubicTo(x + s * 0.32f, y + s * 0.7f, x + s * 0.5f, y + s * 0.84f, x + s * 0.85f, y + s * 0.74f)
        }
        drawPath(w, Color.White.copy(alpha = 0.85f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
    }

    private fun measure(s: String, size: Float, weight: FontWeight = FontWeight.Normal, maxWidth: Int? = null): TextLayoutResult =
        measurer.measure(
            AnnotatedString(s), TextStyle(fontSize = size.sp, fontWeight = weight),
            overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
            constraints = if (maxWidth != null) Constraints(maxWidth = max(1, maxWidth)) else Constraints(),
        )

    /** Draws single-line text with its top at [y]; [align] 0=start, 1=center, 2=end. */
    private fun DrawScope.text(
        s: String, x: Float, y: Float, size: Float, color: Color, weight: FontWeight = FontWeight.Normal,
        maxWidth: Int? = null, align: Int = 0,
    ): TextLayoutResult {
        val l = measure(s, size, weight, maxWidth)
        val dx = when (align) { 1 -> l.size.width / 2f; 2 -> l.size.width.toFloat(); else -> 0f }
        drawText(l, color, Offset(x - dx, y))
        return l
    }

    private fun DrawScope.icon(v: ImageVector, x: Float, y: Float, size: Float, color: Color) {
        val sx = size / v.viewportWidth
        val sy = size / v.viewportHeight
        translate(x, y) { scale(sx, sy, pivot = Offset.Zero) { group(v.root, color) } }
    }

    private fun DrawScope.group(g: VectorGroup, color: Color) {
        withTransform({
            translate(g.translationX, g.translationY)
            if (g.rotation != 0f) rotate(g.rotation, Offset(g.pivotX, g.pivotY))
            if (g.scaleX != 1f || g.scaleY != 1f) scale(g.scaleX, g.scaleY, Offset(g.pivotX, g.pivotY))
        }) {
            for (n in g) when (n) {
                is VectorPath -> {
                    val p = PathParser().addPathNodes(n.pathData).toPath()
                    p.fillType = n.pathFillType
                    drawPath(p, color, alpha = n.fillAlpha)
                }
                is VectorGroup -> group(n, color)
            }
        }
    }
}
