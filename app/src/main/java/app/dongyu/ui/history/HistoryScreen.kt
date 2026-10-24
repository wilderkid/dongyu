package app.dongyu.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.EmptyState
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.WorkoutRow
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.theme.extraColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class HistoryState(val sports: List<SportType> = emptyList(), val workouts: List<Workout> = emptyList(), val filter: Long? = null)

@Composable
fun HistoryScreen(
    state: HistoryState,
    onFilter: (Long?) -> Unit,
    onOpen: (Long) -> Unit,
    onManual: (Long) -> Unit,
) {
    val byId = remember(state.sports) { state.sports.associateBy { it.id } }
    val shown = remember(state) { state.workouts.filter { state.filter == null || it.sportId == state.filter } }
    val groups = remember(shown) {
        shown.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault())) }
    }
    var pick by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ChipButton(stringResource(R.string.filter_all), state.filter == null, icon = Icons.Filled.Apps) { onFilter(null) } }
                    items(state.sports, key = { it.id }) { s ->
                        ChipButton(s.displayName(), state.filter == s.id, s.composeColor(), SportIcons.of(s.icon)) { onFilter(s.id) }
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.History, stringResource(R.string.history_empty)) }
            groups.forEach { (ym, list) ->
                item(key = "h$ym") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.fmt_year_month, ym.year, ym.monthValue), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val dist = list.sumOf { it.distanceM }
                        val dur = list.sumOf { it.durationSec }
                        Text(
                            stringResource(R.string.history_month_summary, list.size, Fmt.km(dist), Pace.formatDuration(dur)),
                            style = MaterialTheme.typography.labelSmall, color = extraColors.subtle,
                        )
                    }
                }
                items(list, key = { it.id }) { w ->
                    WorkoutRow(w, byId[w.sportId], { onOpen(w.id) }, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { state.filter?.let(onManual) ?: run { pick = true } },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.manual_entry)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text(stringResource(R.string.pick_sport)) },
            text = {
                Column {
                    state.sports.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { pick = false; onManual(s.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SportBadge(s, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(s.displayName(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
