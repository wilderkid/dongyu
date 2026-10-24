package app.dongyu.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.R
import app.dongyu.transfer.FileFormat
import app.dongyu.transfer.ImportReport
import app.dongyu.transfer.TransferService
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.sports.NavRow
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.extraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Busy { IMPORT, EXPORT, BACKUP }

/** A one-line outcome shown under the busy area; [kind] picks the string. */
data class Notice(val kind: Kind, val count: Int = 0, val error: String? = null) {
    enum class Kind { EXPORTED, BACKED_UP, FAILED }
}

data class TransferState(
    val busy: Busy? = null,
    val format: FileFormat = FileFormat.GPX,
    val workoutCount: Int = 0,
    val trackCount: Int = 0,
    val report: ImportReport? = null,
    val notice: Notice? = null,
)

data class TransferActions(
    val onBack: () -> Unit = {},
    val onPickImport: () -> Unit = {},
    val onFormat: (FileFormat) -> Unit = {},
    val onExportAll: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onOpenWorkout: (Long) -> Unit = {},
)

class TransferViewModel(private val c: AppContainer) : ViewModel() {
    private val local = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = combine(local, c.workouts.finished) { s, list ->
        s.copy(workoutCount = list.size, trackCount = list.count { it.distanceM > 0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferState())

    init {
        // Files that arrived through "Open with" / share.
        viewModelScope.launch {
            c.pendingImports.filter { it.isNotEmpty() }.collect { uris ->
                c.pendingImports.value = emptyList()
                import(uris)
            }
        }
    }

    fun setFormat(f: FileFormat) = local.update { it.copy(format = f) }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || local.value.busy != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = Busy.IMPORT, report = null, notice = null) }
            val r = c.transfer.importUris(uris)
            local.update { it.copy(busy = null, report = r) }
        }
    }

    private fun run(kind: Busy, done: Notice.Kind, block: suspend () -> Int) {
        if (local.value.busy != null) return
        viewModelScope.launch {
            local.update { it.copy(busy = kind, notice = null, report = null) }
            val n = runCatching { block() }
            local.update {
                it.copy(busy = null, notice = n.fold({ v -> Notice(done, v) }, { e -> Notice(Notice.Kind.FAILED, error = e.message ?: e.javaClass.simpleName) }))
            }
        }
    }

    fun exportAll(out: Uri) = run(Busy.EXPORT, Notice.Kind.EXPORTED) { c.transfer.exportAll(local.value.format, out) }
    fun backup(out: Uri) = run(Busy.BACKUP, Notice.Kind.BACKED_UP) { c.transfer.backup(out) }
}

@Composable
fun TransferRoute(container: AppContainer, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val vm: TransferViewModel = viewModel { TransferViewModel(container) }
    val st by vm.state.collectAsStateWithLifecycle()
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.import(listOf(u)) } }
    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportAll) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::backup) }
    TransferScreen(
        st,
        TransferActions(
            onBack = onBack,
            onPickImport = { runCatching { pickImport.launch(arrayOf("*/*")) } },
            onFormat = vm::setFormat,
            onExportAll = { runCatching { createExport.launch(TransferService.exportName(st.format)) } },
            onBackup = { runCatching { createBackup.launch(TransferService.backupName()) } },
            onRestore = { runCatching { pickRestore.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) } },
            onOpenWorkout = onOpenWorkout,
        ),
    )
}

@Composable
fun TransferScreen(s: TransferState, a: TransferActions) {
    val idle = s.busy == null
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = a.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item { ImportHero(s, a) }
        s.report?.let { r -> item { ReportCard(r, a) } }
        s.notice?.let { n -> item { NoticeCard(n) } }

        item { SectionTitle(stringResource(R.string.export_title), Modifier.padding(top = 4.dp)) }
        item {
            DCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.FolderZip, Brand.Ocean, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.export_desc), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle, modifier = Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileFormat.entries.forEach { f -> ChipButton(f.name, s.format == f, Brand.Ocean) { a.onFormat(f) } }
                }
                Text(
                    stringResource(
                        when (s.format) {
                            FileFormat.GPX -> R.string.export_hint_gpx
                            FileFormat.TCX -> R.string.export_hint_tcx
                            FileFormat.FIT -> R.string.export_hint_fit
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(top = 8.dp),
                )
                val n = if (s.format == FileFormat.GPX) s.trackCount else s.workoutCount
                FilledTonalButton(onClick = a.onExportAll, enabled = idle && n > 0, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Filled.FolderZip, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.export_run, n))
                }
                if (s.busy == Busy.EXPORT) Progress(stringResource(R.string.exporting))
            }
        }

        item { SectionTitle(stringResource(R.string.backup_section), Modifier.padding(top = 4.dp)) }
        item {
            DCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                NavRow(Icons.Filled.Backup, Brand.Teal, stringResource(R.string.backup_create), stringResource(R.string.backup_create_desc)) { if (idle) a.onBackup() }
                NavRow(Icons.Filled.Restore, Brand.Coral, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) { if (idle) a.onRestore() }
                if (s.busy == Busy.BACKUP) Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Progress(stringResource(R.string.exporting)) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(16.dp), tint = Brand.Sun)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.transfer_tip), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
        }
    }
}

@Composable
private fun ImportHero(s: TransferState, a: TransferActions) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0FA3A3), Color(0xFF1565C0))))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.FileUpload, Color.White, 42.dp, filled = false)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(stringResource(R.string.import_desc), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("GPX", "TCX", "FIT", "ZIP").forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Button(
            onClick = a.onPickImport, enabled = s.busy == null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1565C0)),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(48.dp),
        ) {
            Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(if (s.busy == Busy.IMPORT) R.string.importing else R.string.import_pick))
        }
        if (s.busy == Busy.IMPORT) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))
    }
}

@Composable
private fun ReportCard(r: ImportReport, a: TransferActions) {
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.import_result_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            r.lastWorkoutId?.let { id ->
                FilledTonalButton(onClick = { a.onOpenWorkout(id) }) {
                    Text(stringResource(R.string.import_view)); Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ResultLine(Icons.Filled.CheckCircle, Brand.Up, stringResource(R.string.import_imported, r.imported))
        if (r.restoredSports > 0) ResultLine(Icons.Filled.Category, Brand.Teal, stringResource(R.string.import_sports_added, r.restoredSports))
        if (r.duplicates > 0) ResultLine(Icons.Filled.ContentCopy, Brand.Sun, stringResource(R.string.import_duplicates, r.duplicates))
        if (r.failed.isNotEmpty()) {
            ResultLine(Icons.Filled.ErrorOutline, Brand.Down, stringResource(R.string.import_failed, r.failed.size))
            Text(r.failed.take(5).joinToString("、"), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(start = 30.dp))
        }
    }
}

@Composable
private fun ResultLine(icon: ImageVector, color: Color, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoticeCard(n: Notice) {
    val (icon, color, text) = when (n.kind) {
        Notice.Kind.EXPORTED -> Triple(Icons.Filled.CheckCircle, Brand.Up, stringResource(R.string.export_done, n.count))
        Notice.Kind.BACKED_UP -> Triple(Icons.Filled.CheckCircle, Brand.Up, stringResource(R.string.backup_done, n.count))
        Notice.Kind.FAILED -> Triple(Icons.Filled.ErrorOutline, Brand.Down, stringResource(R.string.transfer_failed, n.error.orEmpty()))
    }
    DCard(Modifier.fillMaxWidth(), padding = 12.dp) { ResultLine(icon, color, text) }
}

@Composable
private fun Progress(label: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(top = 4.dp))
    }
}
