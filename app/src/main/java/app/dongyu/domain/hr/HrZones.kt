package app.dongyu.domain.hr

import kotlin.math.max
import kotlin.math.roundToInt

/** One heart-rate reading. */
data class HrSample(val time: Long, val bpm: Int)

data class HrSummary(val avg: Int, val max: Int, val min: Int, val zoneSec: LongArray) {
    val totalZoneSec: Long get() = zoneSec.sum()
}

/**
 * Five-zone model as % of max HR: Z1 50–60, Z2 60–70, Z3 70–80, Z4 80–90, Z5 ≥ 90.
 * Readings below 50% are counted in Z1 so totals equal recorded time.
 */
object HrZones {
    const val COUNT = 5
    const val AVG_KEY = "hr_avg"
    const val MAX_KEY = "hr_max"
    val lowerPct = doubleArrayOf(0.5, 0.6, 0.7, 0.8, 0.9)
    /** Workout value keys for seconds spent in each zone. */
    val keys = (1..COUNT).map { "hrz$it" }
    /** Samples further apart than this are treated as a gap (sensor drop-out / pause). */
    const val MAX_GAP_MS = 10_000L

    fun maxHrForAge(age: Int): Int = 220 - age.coerceIn(5, 100)

    fun effectiveMax(customMax: Int, age: Int): Int = if (customMax in 100..240) customMax else maxHrForAge(age)

    /** Zone index 0..4 for [bpm]. */
    fun zoneOf(bpm: Int, maxHr: Int): Int {
        val pct = bpm.toDouble() / maxHr
        for (z in COUNT - 1 downTo 1) if (pct >= lowerPct[z]) return z
        return 0
    }

    /** BPM range (inclusive lower, exclusive upper) of each zone, for display. */
    fun bounds(maxHr: Int): List<IntRange> = (0 until COUNT).map { z ->
        val lo = (maxHr * lowerPct[z]).roundToInt()
        val hi = if (z == COUNT - 1) maxHr else (maxHr * lowerPct[z + 1]).roundToInt() - 1
        lo..hi
    }

    /** Time-weighted summary; returns null without usable samples. */
    fun summarize(samples: List<HrSample>, maxHr: Int): HrSummary? {
        val s = samples.filter { it.bpm in 25..250 }.sortedBy { it.time }
        if (s.isEmpty()) return null
        val zones = LongArray(COUNT)
        var weighted = 0.0
        var totalMs = 0L
        for (i in s.indices) {
            val next = s.getOrNull(i + 1)
            // last sample (or one before a gap) counts for 1 s
            val dt = if (next == null) 1000L else (next.time - s[i].time).let { if (it in 1..MAX_GAP_MS) it else 1000L }
            weighted += s[i].bpm * dt.toDouble()
            totalMs += dt
            zones[zoneOf(s[i].bpm, maxHr)] += dt
        }
        val zoneSec = LongArray(COUNT) { (zones[it] / 1000.0).roundToInt().toLong() }
        return HrSummary(
            avg = (weighted / max(totalMs, 1)).roundToInt(),
            max = s.maxOf { it.bpm },
            min = s.minOf { it.bpm },
            zoneSec = zoneSec,
        )
    }

    /** Reads zone seconds back from workout values (null when the workout has no zone data). */
    fun fromValues(values: Map<String, Double>): LongArray? {
        if (keys.none { values.containsKey(it) }) return null
        return LongArray(COUNT) { (values[keys[it]] ?: 0.0).toLong() }
    }

    fun toValues(summary: HrSummary): Map<String, Double> = buildMap {
        put(AVG_KEY, summary.avg.toDouble())
        put(MAX_KEY, summary.max.toDouble())
        summary.zoneSec.forEachIndexed { i, v -> put(HrZones.keys[i], v.toDouble()) }
    }
}

object HrSampling {
    /** Bucket-averages [samples] down to at most [maxPoints] for charting. */
    fun downsample(samples: List<HrSample>, maxPoints: Int): List<HrSample> {
        val s = samples.filter { it.bpm in 25..250 }
        if (s.size <= maxPoints) return s
        val per = (s.size + maxPoints - 1) / maxPoints
        return s.chunked(per).map { c -> HrSample(c[c.size / 2].time, c.sumOf { it.bpm } / c.size) }
    }
}
