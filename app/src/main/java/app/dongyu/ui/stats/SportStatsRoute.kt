package app.dongyu.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.MetricKind
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate

object SportStatsBuilder {
    private val skipTrend = setOf(MetricKind.NOTE, MetricKind.STROKE, MetricKind.POOL_LENGTH)

    fun build(agg: StatsAggregator, sport: SportType, ws: List<Workout>, bucket: Period, today: LocalDate, maxHr: Int = 190): SportStatsState {
        val mine = ws.filter { it.sportId == sport.id }
        val n = if (bucket == Period.WEEK) 12 else 12
        val hrTrend = agg.hrTrend(mine, bucket, today, n)
        val trends = sport.metrics.filter { it.kind.isNumeric && it.kind !in skipTrend }
            .map { MetricTrend(it, agg.metricTrend(mine, it, bucket, today, n)) }
            .filter { t -> t.points.count { it.value > 0 } >= 1 }
            .filter { hrTrend.isEmpty() || (it.metric.kind != MetricKind.HEART_RATE && it.metric.kind != MetricKind.MAX_HEART_RATE) }
        return SportStatsState(
            sport = sport,
            allTime = agg.totals(mine),
            bucket = bucket,
            records = agg.personalRecords(sport, mine),
            comparison = agg.compareMetrics(mine, sport.metrics.filter { it.kind !in skipTrend && it.kind != MetricKind.RPE }, Period.MONTH, today),
            trends = trends,
            hrZones = agg.hrZoneTotals(mine),
            hrTrend = hrTrend,
            maxHr = maxHr,
        )
    }
}

class SportStatsViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow<SportStatsState?>(null)
    private val bucket = MutableStateFlow(Period.WEEK)
    private var started = false

    fun load(id: Long) {
        if (started) return
        started = true
        viewModelScope.launch {
            val sport = c.sports.get(id) ?: return@launch
            combine(c.workouts.finishedBySport(id), bucket, c.settings.settings) { ws, b, st -> SportStatsBuilder.build(c.stats, sport, ws, b, LocalDate.now(), st.effectiveMaxHr) }
                .flowOn(Dispatchers.Default)
                .collect { state.value = it }
        }
    }
    fun setBucket(p: Period) { bucket.value = p }
}

@Composable
fun SportStatsRoute(container: AppContainer, id: Long, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val vm: SportStatsViewModel = viewModel { SportStatsViewModel(container) }
    LaunchedEffect(id) { vm.load(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    st?.let { SportStatsScreen(it, vm::setBucket, onBack, onOpenWorkout) }
}
