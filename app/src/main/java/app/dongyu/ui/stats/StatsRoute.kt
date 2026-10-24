package app.dongyu.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

object StatsBuilder {
    fun dashboard(agg: StatsAggregator, sports: List<SportType>, ws: List<Workout>, period: Period, today: LocalDate, maxHr: Int = 190): StatsState {
        val start = agg.periodStart(period, today)
        val inPeriod = agg.inRange(ws, start, agg.periodEndExclusive(period, start))
        val used = ws.map { it.sportId }.toSet()
        val shown = sports.filter { it.id in used }
        return StatsState(
            period = period,
            today = today,
            comparison = agg.compare(ws, period, today, toDate = true),
            streaks = agg.streaks(ws, today),
            heatmap = agg.heatmap(ws, today),
            shares = agg.timeBySport(inPeriod),
            weekly = agg.weeklyStacked(ws, today, 12),
            sports = shown,
            perSport = shown.map { s -> SportRow(s, agg.totals(inPeriod.filter { it.sportId == s.id })) }
                .sortedByDescending { it.totals.durationSec },
            hasData = ws.isNotEmpty(),
            hrZones = agg.hrZoneTotals(inPeriod),
            hrTrend = agg.hrTrend(ws, if (period == Period.YEAR) Period.MONTH else Period.WEEK, today, 12).takeIf { t -> t.size >= 1 } ?: emptyList(),
            hrTrendMonthly = period == Period.YEAR,
            maxHr = maxHr,
        )
    }
}

class StatsViewModel(private val c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(Period.WEEK)
    val state = combine(c.sports.allSports, c.workouts.finished, period, c.settings.settings) { s, w, p, st ->
        StatsBuilder.dashboard(c.stats, s, w, p, LocalDate.now(), st.effectiveMaxHr)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())
    fun setPeriod(p: Period) { period.value = p }
}

@Composable
fun StatsRoute(container: AppContainer, onOpenSport: (Long) -> Unit, onShareReport: () -> Unit = {}) {
    val vm: StatsViewModel = viewModel { StatsViewModel(container) }
    val st by vm.state.collectAsStateWithLifecycle()
    StatsScreen(st, vm::setPeriod, onOpenSport, onShareReport)
}
