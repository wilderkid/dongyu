package app.dongyu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.stats.Comparison
import app.dongyu.domain.stats.Streaks
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.model.Workout
import app.dongyu.recording.LiveState
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.DeltaPill
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.WorkoutRow
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors

data class HomeState(
    val sports: List<SportType> = emptyList(),
    val week: Comparison? = null,
    val streaks: Streaks? = null,
    val recent: List<Workout> = emptyList(),
    val live: LiveState = LiveState(),
)

@Composable
fun HomeScreen(
    state: HomeState,
    onStart: (SportType, SwimMode?, Double) -> Unit,
    onManual: (SportType) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenLive: () -> Unit,
    onAddSport: () -> Unit,
    onSeeAll: () -> Unit,
    initialSheetSport: SportType? = null,
) {
    var sheetSport by remember { mutableStateOf(initialSheetSport) }
    val byId = remember(state.sports) { state.sports.associateBy { it.id } }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                }
                state.streaks?.let { s ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.streak_days_short, s.currentDays), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (state.live.active) item { LiveBanner(state.live, onOpenLive) }
        item { WeekHero(state.week) }
        item { SectionTitle(stringResource(R.string.home_start_title)) }
        val rows = (state.sports.map<SportType, SportType?> { it } + listOf(null)).chunked(3)
        items(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    if (s == null) AddSportTile(onAddSport, Modifier.weight(1f))
                    else SportTile(s, { sheetSport = s }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (state.recent.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.home_recent)) {
                    Text(stringResource(R.string.see_all), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(4.dp))
                }
            }
            items(state.recent, key = { it.id }) { w -> WorkoutRow(w, byId[w.sportId], { onOpenWorkout(w.id) }) }
        }
    }
    sheetSport?.let { s ->
        StartSheet(
            sport = s,
            onDismiss = { sheetSport = null },
            onStart = { mode, len -> sheetSport = null; onStart(s, mode, len) },
            onManual = { sheetSport = null; onManual(s) },
        )
    }
}

@Composable
private fun WeekHero(c: Comparison?) {
    val cur = c?.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(extraColors.heroStart, extraColors.heroEnd)))
            .padding(20.dp)
    ) {
        // decorative rings
        Box(Modifier.align(Alignment.TopEnd).size(120.dp).padding(start = 40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_this_week), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                c?.delta { it.durationSec.toDouble() }?.let { d ->
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.vs_last_week, Fmt.percent(d)), color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.km(cur?.distanceM ?: 0.0), style = NumberStyle.Large.copy(fontSize = NumberStyle.Large.fontSize * 1.4f), color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.unit_km), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HeroStat(Pace.formatDuration(cur?.durationSec ?: 0), stringResource(R.string.metric_duration))
                HeroStat((cur?.count ?: 0).toString(), stringResource(R.string.stat_workouts))
                HeroStat(Fmt.num(cur?.calories ?: 0.0), stringResource(R.string.unit_kcal))
                HeroStat("${cur?.activeDays ?: 0}/7", stringResource(R.string.stat_active_days))
            }
        }
    }
}

@Composable
private fun HeroStat(v: String, l: String) {
    Column {
        Text(v, style = NumberStyle.Small.copy(fontSize = NumberStyle.Medium.fontSize * 0.85f), color = Color.White)
        Text(l, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun SportTile(s: SportType, onClick: () -> Unit, modifier: Modifier) {
    DCard(modifier.aspectRatio(0.95f), padding = 12.dp, onClick = onClick) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(s.composeColor(), s.composeColor().copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(SportIcons.of(s.icon), null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(8.dp))
                Text(s.displayName(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Icon(
                if (s.usesGps) Icons.Filled.SatelliteAlt else if (s.family == SportFamily.SWIM) Icons.Filled.Waves else Icons.Filled.Timer,
                null, tint = extraColors.subtle.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
            )
        }
    }
}

@Composable
private fun AddSportTile(onClick: () -> Unit, modifier: Modifier) {
    DCard(modifier.aspectRatio(0.95f), padding = 12.dp, onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconBadge(Icons.Filled.Add, MaterialTheme.colorScheme.primary, 54.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.add_sport), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LiveBanner(live: LiveState, onClick: () -> Unit) {
    DCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.live_in_progress, live.sport?.displayName() ?: ""), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Pace.formatDuration(live.movingMs / 1000), style = NumberStyle.Small)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSheet(sport: SportType, onDismiss: () -> Unit, onStart: (SwimMode?, Double) -> Unit, onManual: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        StartSheetContent(sport, onStart, onManual)
    }
}

@Composable
fun StartSheetContent(sport: SportType, onStart: (SwimMode?, Double) -> Unit, onManual: () -> Unit) {
    var mode by remember { mutableStateOf(SwimMode.POOL) }
    var pool by remember { mutableStateOf("25") }
    val isSwim = sport.family == SportFamily.SWIM
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(sport, 52.dp, filled = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(sport.displayName(), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (sport.usesGps) R.string.start_hint_gps else if (isSwim) R.string.start_hint_swim else R.string.start_hint_timer),
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
        }
        if (isSwim) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipButton(stringResource(R.string.swim_pool), mode == SwimMode.POOL, sport.composeColor(), Icons.Filled.Pool) { mode = SwimMode.POOL }
                ChipButton(stringResource(R.string.swim_open_water), mode == SwimMode.OPEN_WATER, sport.composeColor(), Icons.Filled.Waves) { mode = SwimMode.OPEN_WATER }
            }
            if (mode == SwimMode.POOL) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.pool_length), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("25", "50").forEach { l -> ChipButton("$l m", pool == l, sport.composeColor()) { pool = l } }
                    OutlinedTextField(
                        value = if (pool == "25" || pool == "50") "" else pool,
                        onValueChange = { pool = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        placeholder = { Text(stringResource(R.string.custom)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onStart(if (isSwim) mode else null, pool.toDoubleOrNull()?.takeIf { it > 0 } ?: 25.0) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = sport.composeColor()),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.start_recording), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Filled.EditCalendar, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.manual_entry))
        }
    }
}
