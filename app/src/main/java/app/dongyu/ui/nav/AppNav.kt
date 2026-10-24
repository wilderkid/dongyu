package app.dongyu.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dongyu.AppContainer
import app.dongyu.R
import app.dongyu.model.SwimMode
import app.dongyu.model.Workout
import app.dongyu.ui.components.displayName
import app.dongyu.ui.detail.DetailRoute
import app.dongyu.ui.edit.EditRoute
import app.dongyu.ui.editor.SportEditorRoute
import app.dongyu.ui.guide.OemGuideRoute
import app.dongyu.ui.history.HistoryRoute
import app.dongyu.ui.home.HomeScreen
import app.dongyu.ui.home.HomeViewModel
import app.dongyu.ui.live.LiveRoute
import app.dongyu.ui.sports.MeRoute
import app.dongyu.ui.stats.SportStatsRoute
import app.dongyu.ui.stats.StatsRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val ME = "me"
    const val OEM = "oem"
    const val HR = "hr"
    fun live(sportId: Long, mode: SwimMode? = null, pool: Double = 25.0) = "live/$sportId?mode=${mode?.name ?: ""}&pool=$pool"
    fun edit(workoutId: Long? = null, sportId: Long? = null, finish: Boolean = false) =
        "edit?workoutId=${workoutId ?: -1}&sportId=${sportId ?: -1}&finish=$finish"
    fun detail(id: Long) = "detail/$id"
    fun sportStats(id: Long) = "sportStats/$id"
    fun editor(id: Long? = null) = "editor?sportId=${id ?: -1}"
}

private data class Tab(val route: String, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(Routes.HOME, Icons.Filled.Home, R.string.tab_home),
    Tab(Routes.HISTORY, Icons.Filled.ReceiptLong, R.string.tab_history),
    Tab(Routes.STATS, Icons.Filled.BarChart, R.string.tab_stats),
    Tab(Routes.ME, Icons.Filled.Person, R.string.tab_me),
)

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val scope = rememberCoroutineScope()
    var recoverable by remember { mutableStateOf<Workout?>(null) }

    // Crash / process-death recovery and the one-time OEM guide.
    LaunchedEffect(Unit) {
        if (!container.recording.state.value.active) recoverable = container.workouts.unfinished()
        if (recoverable == null && !container.settings.settings.first().oemGuideSeen) nav.navigate(Routes.OEM)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize().padding(pad),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel { HomeViewModel(container) }
                val st by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    st,
                    onStart = { s, mode, pool -> nav.navigate(Routes.live(s.id, mode, pool)) },
                    onManual = { s -> nav.navigate(Routes.edit(sportId = s.id)) },
                    onOpenWorkout = { nav.navigate(Routes.detail(it)) },
                    onOpenLive = { st.live.sport?.let { nav.navigate(Routes.live(it.id, st.live.swimMode, st.live.poolLength)) } },
                    onAddSport = { nav.navigate(Routes.editor()) },
                    onSeeAll = { nav.switchTab(Routes.HISTORY) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(container, onOpen = { nav.navigate(Routes.detail(it)) }, onManual = { nav.navigate(Routes.edit(sportId = it)) })
            }
            composable(Routes.STATS) { StatsRoute(container, onOpenSport = { nav.navigate(Routes.sportStats(it)) }) }
            composable(Routes.ME) {
                MeRoute(container, onAddSport = { nav.navigate(Routes.editor()) }, onEditSport = { nav.navigate(Routes.editor(it)) }, onOemGuide = { nav.navigate(Routes.OEM) }, onHr = { nav.navigate(Routes.HR) })
            }
            composable(Routes.HR) { app.dongyu.ui.hr.HrSettingsRoute(container, onBack = { nav.popBackStack() }) }
            composable(
                "live/{sportId}?mode={mode}&pool={pool}",
                arguments = listOf(
                    navArgument("sportId") { type = NavType.LongType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("pool") { type = NavType.FloatType; defaultValue = 25f },
                ),
            ) { e ->
                val args = e.arguments!!
                val mode = args.getString("mode")?.takeIf { it.isNotEmpty() }?.let { runCatching { SwimMode.valueOf(it) }.getOrNull() }
                LiveRoute(
                    container, args.getLong("sportId"), mode, args.getFloat("pool").toDouble(),
                    onFinished = { id -> nav.navigate(Routes.edit(workoutId = id, finish = true)) { popUpTo(Routes.HOME) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "edit?workoutId={workoutId}&sportId={sportId}&finish={finish}",
                arguments = listOf(
                    navArgument("workoutId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("sportId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("finish") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                val a = e.arguments!!
                val wid = a.getLong("workoutId").takeIf { it > 0 }
                val finish = a.getBoolean("finish")
                EditRoute(
                    container, wid, a.getLong("sportId").takeIf { it > 0 }, finish,
                    onDone = { id ->
                        if (wid == null || finish) nav.navigate(Routes.detail(id)) { popUpTo(Routes.HOME) } else nav.popBackStack()
                    },
                    onBack = { if (finish) nav.navigate(Routes.detail(wid!!)) { popUpTo(Routes.HOME) } else nav.popBackStack() },
                )
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                DetailRoute(container, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(workoutId = it)) })
            }
            composable("sportStats/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SportStatsRoute(container, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onOpenWorkout = { nav.navigate(Routes.detail(it)) })
            }
            composable("editor?sportId={sportId}", arguments = listOf(navArgument("sportId") { type = NavType.LongType; defaultValue = -1L })) { e ->
                SportEditorRoute(container, e.arguments!!.getLong("sportId").takeIf { it > 0 }, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.OEM) { OemGuideRoute(container, onDone = { nav.popBackStack() }) }
        }
    }

    recoverable?.let { w ->
        val sportName = remember(w.sportId) { mutableStateOf<app.dongyu.model.SportType?>(null) }
        LaunchedEffect(w.sportId) { sportName.value = container.sports.get(w.sportId) }
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Restore, null) },
            title = { Text(stringResource(R.string.recover_title)) },
            text = { Text(stringResource(R.string.recover_message, sportName.value?.displayName() ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    recoverable = null
                    scope.launch {
                        if (container.recording.recover(w)) nav.navigate(Routes.live(w.sportId, w.swimMode, w.values["pool_len"] ?: 25.0))
                    }
                }) { Text(stringResource(R.string.recover_resume)) }
            },
            dismissButton = {
                Box {
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = {
                            recoverable = null
                            scope.launch { container.recording.discard(w.id) }
                        }) { Text(stringResource(R.string.discard)) }
                        TextButton(onClick = {
                            recoverable = null
                            scope.launch {
                                if (container.recording.recover(w)) container.recording.stop()?.let { nav.navigate(Routes.edit(workoutId = it, finish = true)) }
                            }
                        }) { Text(stringResource(R.string.recover_save)) }
                    }
                }
            },
        )
    }
}
