package app.dongyu.share

import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.domain.stats.Totals
import app.dongyu.model.Workout
import java.time.LocalDate

data class SportSlice(val sportId: Long, val count: Int, val durationSec: Long, val distanceM: Double)

data class ReportSummary(
    val period: Period,
    val start: LocalDate,
    /** Inclusive last day. */
    val end: LocalDate,
    val totals: Totals,
    /** Moving seconds per day, one entry per day of the period. */
    val daily: List<Pair<LocalDate, Long>>,
    /** Sorted by duration, largest first. */
    val bySport: List<SportSlice>,
    val longest: Workout?,
)

/** Builds the weekly / monthly report shown on share cards. */
object ReportBuilder {
    fun build(all: List<Workout>, period: Period, ref: LocalDate, agg: StatsAggregator): ReportSummary {
        require(period != Period.YEAR) { "Reports are weekly or monthly" }
        val start = agg.periodStart(period, ref)
        val endEx = agg.periodEndExclusive(period, start)
        val ws = agg.inRange(all, start, endEx)
        val byDay = ws.groupBy { agg.dateOf(it) }
        val daily = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(endEx) }
            .map { d -> d to (byDay[d]?.sumOf { it.durationSec } ?: 0L) }.toList()
        val bySport = ws.groupBy { it.sportId }.map { (id, l) ->
            SportSlice(id, l.size, l.sumOf { it.durationSec }, l.sumOf { it.distanceM })
        }.sortedWith(compareByDescending<SportSlice> { it.durationSec }.thenByDescending { it.count })
        return ReportSummary(period, start, endEx.minusDays(1), agg.totals(ws), daily, bySport, ws.maxByOrNull { it.durationSec })
    }
}
