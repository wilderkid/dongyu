package app.dongyu.ui.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.R
import app.dongyu.domain.stats.Period
import app.dongyu.share.CardAspect
import app.dongyu.share.CardStyle
import app.dongyu.share.MapBackground
import app.dongyu.share.ReportBuilder
import app.dongyu.share.ShareCardRenderer
import app.dongyu.share.WorkoutCardData
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.theme.extraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

sealed interface ShareTarget {
    data class WorkoutCard(val id: Long) : ShareTarget
    data object Report : ShareTarget
}

data class ShareUi(
    val aspect: CardAspect = CardAspect.PORTRAIT,
    val style: CardStyle = CardStyle.AURORA,
    val period: Period = Period.WEEK,
    val offset: Int = 0,
    val hasTrack: Boolean = false,
    val image: ImageBitmap? = null,
    val busy: Boolean = true,
    val mapLoading: Boolean = false,
    val name: String = "dongyu",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModel(private val c: AppContainer, private val app: Context, private val target: ShareTarget) : ViewModel() {
    val ui = MutableStateFlow(ShareUi(style = CardStyle.AURORA))
    val toast = MutableStateFlow<Int?>(null)
    private val zone = ZoneId.systemDefault()
    private val renderer = ShareCardRenderer(TextMeasurer(createFontFamilyResolver(app), Density(1f), LayoutDirection.Ltr))
    private val factory = ShareCardFactory(app, zone)
    private val renderCtx = Dispatchers.Default.limitedParallelism(1)
    private var workoutData: WorkoutCardData? = null
    private val maps = HashMap<CardAspect, MapBackground?>()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            if (target is ShareTarget.WorkoutCard) {
                val w = c.workouts.get(target.id) ?: return@launch
                val sport = c.sports.get(w.sportId) ?: return@launch
                val pts = c.workouts.points(w.id)
                val hr = c.workouts.hrSamples(w.id)
                workoutData = withContext(renderCtx) { factory.workout(w, sport, pts.map { it.lat to it.lon }, hr) }
                val has = workoutData!!.track.size >= 2
                ui.update { it.copy(hasTrack = has, name = "dongyu_${w.startTime / 1000}") }
            }
            render()
        }
    }

    fun setAspect(a: CardAspect) { ui.update { it.copy(aspect = a) }; render() }
    fun setStyle(s: CardStyle) { ui.update { it.copy(style = s) }; render() }
    fun setPeriod(p: Period) { ui.update { it.copy(period = p, offset = 0) }; render() }
    fun shift(by: Int) { ui.update { it.copy(offset = (it.offset + by).coerceAtMost(0)) }; render() }

    private fun render() {
        job?.cancel()
        job = viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            val s = ui.value
            val img = when (target) {
                is ShareTarget.WorkoutCard -> {
                    val d = workoutData ?: return@launch
                    var map: MapBackground? = null
                    if (s.style == CardStyle.MAP && s.hasTrack) {
                        map = if (maps.containsKey(s.aspect)) maps[s.aspect] else {
                            ui.update { it.copy(mapLoading = true) }
                            val proj = renderer.workoutProjection(d, s.aspect)
                            val m = proj?.let { ShareIO.mapSnapshot(app, it, s.aspect.w, s.aspect.h) }
                            maps[s.aspect] = m
                            ui.update { it.copy(mapLoading = false) }
                            if (m == null) toast.value = R.string.share_map_failed
                            m
                        }
                    }
                    withContext(renderCtx) { renderer.renderWorkout(d, s.aspect, s.style, map) }
                }
                ShareTarget.Report -> {
                    val all = c.workouts.finished.first()
                    val sports = c.sports.all().associateBy { it.id }
                    val today = LocalDate.now(zone)
                    val ref = if (s.period == Period.WEEK) today.plusWeeks(s.offset.toLong()) else today.plusMonths(s.offset.toLong())
                    withContext(renderCtx) {
                        val r = ReportBuilder.build(all, s.period, ref, c.stats)
                        renderer.renderReport(factory.report(r, sports, today), s.aspect, s.style)
                    }.also {
                        val tag = if (s.period == Period.WEEK) "week" else "month"
                        ui.update { u -> u.copy(name = "dongyu_${tag}_$ref") }
                    }
                }
            }
            ui.update { it.copy(image = img, busy = false) }
        }
    }

    fun save() = viewModelScope.launch {
        val img = ui.value.image ?: return@launch
        val ok = ShareIO.saveToGallery(app, img, "${ui.value.name}_${ui.value.aspect.name.lowercase()}_${System.currentTimeMillis() % 100000}")
        toast.value = if (ok) R.string.share_saved else R.string.share_save_failed
    }

    fun exportFile(ctx: Context, format: app.dongyu.transfer.FileFormat) = viewModelScope.launch {
        val id = (target as? ShareTarget.WorkoutCard)?.id ?: return@launch
        runCatching { c.transfer.exportWorkout(id, format) }
            .onSuccess { ctx.startActivity(ShareIO.shareIntent(ctx, it, format.mime)) }
            .onFailure { toast.value = R.string.share_save_failed }
    }

    fun share(ctx: Context) = viewModelScope.launch {
        val img = ui.value.image ?: return@launch
        val uri = ShareIO.cacheForShare(app, img, ui.value.name)
        ctx.startActivity(ShareIO.shareIntent(ctx, uri, "image/png"))
    }
}

@Composable
fun ShareRoute(container: AppContainer, target: ShareTarget, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm: ShareViewModel = viewModel(key = target.toString()) { ShareViewModel(container, ctx.applicationContext, target) }
    val st by vm.ui.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast.value = null } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.save() else vm.toast.value = R.string.share_save_failed
    }
    ShareScreen(
        st, isReport = target == ShareTarget.Report, onBack = onBack,
        onAspect = vm::setAspect, onStyle = vm::setStyle, onPeriod = vm::setPeriod, onShift = vm::shift,
        onSave = {
            if (Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) perm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.save()
        },
        onShare = { vm.share(ctx) },
        onExportFile = { vm.exportFile(ctx, it) },
    )
}

@Composable
fun ShareScreen(
    st: ShareUi, isReport: Boolean, onBack: () -> Unit,
    onAspect: (CardAspect) -> Unit, onStyle: (CardStyle) -> Unit, onPeriod: (Period) -> Unit, onShift: (Int) -> Unit,
    onSave: () -> Unit, onShare: () -> Unit,
    onExportFile: (app.dongyu.transfer.FileFormat) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(if (isReport) R.string.report_share else R.string.share_action), style = MaterialTheme.typography.titleLarge)
        }
        if (isReport) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentedTabs(
                    listOf(stringResource(R.string.report_tab_week), stringResource(R.string.report_tab_month)),
                    if (st.period == Period.WEEK) 0 else 1, { onPeriod(if (it == 0) Period.WEEK else Period.MONTH) }, Modifier.weight(1f),
                )
                IconButton(onClick = { onShift(-1) }) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.report_prev)) }
                IconButton(onClick = { onShift(1) }, enabled = st.offset < 0) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.report_next)) }
            }
        }
        // Preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val ratio = st.aspect.w.toFloat() / st.aspect.h
            Box(
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true).shadow(12.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(extraColors.card),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(st.image, label = "card") { img ->
                    if (img != null) Image(img, stringResource(R.string.share_title), Modifier.fillMaxSize())
                }
                if (st.busy || st.mapLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                        if (st.mapLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.share_map_loading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (!isReport && !st.hasTrack) {
            Text(
                stringResource(R.string.share_no_track), style = MaterialTheme.typography.labelMedium, color = extraColors.subtle,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
            )
        }
        OptionRow(stringResource(R.string.share_size)) {
            listOf(
                Triple(CardAspect.PORTRAIT, R.string.share_aspect_portrait, Icons.Filled.CropPortrait),
                Triple(CardAspect.SQUARE, R.string.share_aspect_square, Icons.Filled.CropSquare),
                Triple(CardAspect.STORY, R.string.share_aspect_story, Icons.Filled.Smartphone),
            ).forEach { (a, label, icon) -> ChipButton(stringResource(label), st.aspect == a, icon = icon) { onAspect(a) } }
        }
        OptionRow(stringResource(R.string.share_template)) {
            val styles = buildList {
                add(Triple(CardStyle.AURORA, R.string.share_style_aurora, Icons.Filled.AutoAwesome))
                add(Triple(CardStyle.PAPER, R.string.share_style_paper, Icons.Filled.Description))
                if (!isReport && st.hasTrack) add(Triple(CardStyle.MAP, R.string.share_style_map, Icons.Filled.Map))
            }
            styles.forEach { (s, label, icon) -> ChipButton(stringResource(label), st.style == s, icon = icon) { onStyle(s) } }
        }
        if (!isReport) OptionRow(stringResource(R.string.share_file)) {
            app.dongyu.transfer.FileFormat.entries
                .filter { it != app.dongyu.transfer.FileFormat.GPX || st.hasTrack }
                .forEach { f -> ChipButton(f.name, false, icon = Icons.Filled.InsertDriveFile) { onExportFile(f) } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onSave, enabled = st.image != null && !st.busy, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share_save))
            }
            Button(onClick = onShare, enabled = st.image != null && !st.busy, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share_send))
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = extraColors.subtle, modifier = Modifier.width(44.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

