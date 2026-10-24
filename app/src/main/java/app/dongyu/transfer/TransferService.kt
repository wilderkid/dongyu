package app.dongyu.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import app.dongyu.data.prefs.SettingsStore
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportReport(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: List<String> = emptyList(),
    val restoredSports: Int = 0,
    val lastWorkoutId: Long? = null,
) {
    operator fun plus(o: ImportReport) = ImportReport(
        imported + o.imported, duplicates + o.duplicates, failed + o.failed, restoredSports + o.restoredSports, o.lastWorkoutId ?: lastWorkoutId,
    )
}

/** Moves workouts in and out of the app: GPX/TCX/FIT files, bulk zips and full backups. */
class TransferService(
    private val context: Context,
    private val sports: SportRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()
    private val maxBytes = 64L * 1024 * 1024

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Imports any mix of activity files, zips/gzips of them, and Dongyu backups. */
    suspend fun importUris(uris: List<Uri>): ImportReport = withContext(Dispatchers.IO) {
        lock.withLock {
            uris.fold(ImportReport()) { acc, uri ->
                val name = displayName(uri) ?: "file"
                acc + runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                    if (bytes.size > maxBytes) error("too large")
                    importBytes(name, bytes)
                }.getOrElse { ImportReport(failed = listOf(name)) }
            }
        }
    }

    private suspend fun importBytes(name: String, bytes: ByteArray, depth: Int = 0): ImportReport {
        if (depth > 1) return ImportReport()
        // gzip (e.g. Strava bulk export *.gpx.gz)
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return importBytes(name.removeSuffix(".gz"), GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }, depth + 1)
        }
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val entries = ArrayList<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var total = 0L
                generateSequence { z.nextEntry }.filterNot(ZipEntry::isDirectory).forEach { e ->
                    val data = z.readBytes(); total += data.size
                    if (total > maxBytes * 4) error("archive too large")
                    entries += e.name to data
                }
            }
            entries.firstOrNull { it.first.substringAfterLast('/') == BackupCodec.ENTRY }?.let { return restore(BackupCodec.decode(it.second)) }
            return entries.filter { (n, _) -> n.substringAfterLast('.').lowercase() in setOf("gpx", "tcx", "fit", "gz") }
                .fold(ImportReport()) { acc, (n, data) ->
                    acc + runCatching { importBytes(n.substringAfterLast('/'), data, depth + 1) }.getOrElse { ImportReport(failed = listOf(n.substringAfterLast('/'))) }
                }
        }
        if (name.endsWith(".json", true)) return restore(BackupCodec.decode(bytes))
        val acts = ActivityCodec.parse(bytes, name)
        val s = settings.settings.first()
        val all = sports.all()
        var report = ImportReport()
        for (a in acts) {
            if (workouts.startingNear(a.startTime).isNotEmpty()) { report += ImportReport(duplicates = 1); continue }
            val sport = ActivityMapper.matchSport(a, all)
            val imp = ActivityMapper.toWorkout(a, sport, s.weightKg, s.effectiveMaxHr)
            val id = workouts.insertWithTrack(imp.workout, imp.points, imp.hr)
            report += ImportReport(imported = 1, lastWorkoutId = id)
        }
        return report
    }

    private suspend fun restore(f: BackupFile): ImportReport {
        val plan = BackupCodec.planSports(f.sports, sports.all())
        var added = 0
        val idMap = plan.mapValues { (_, v) ->
            v.first?.id ?: sports.save(v.second!!).also { added++ }
        }
        var imported = 0; var dupes = 0; var last: Long? = null
        for (bw in f.workouts) {
            val sportId = idMap[bw.sportId] ?: sports.all().firstOrNull()?.id ?: continue
            if (workouts.startingNear(bw.startTime).isNotEmpty()) { dupes++; continue }
            last = workouts.insertWithTrack(BackupCodec.workout(bw, sportId), BackupCodec.points(bw), BackupCodec.hr(bw))
            imported++
        }
        f.settings.weightKg?.let { settings.setWeightKg(it) }
        f.settings.maxHr?.let { settings.setMaxHr(it) }
        f.settings.age?.let { settings.setAge(it) }
        return ImportReport(imported, dupes, restoredSports = added, lastWorkoutId = last)
    }

    private suspend fun activity(id: Long): Pair<ActivityData, String>? {
        val w = workouts.get(id) ?: return null
        val sport = sports.get(w.sportId) ?: return null
        val a = ActivityMapper.toActivity(w, sport, workouts.points(id), workouts.hrSamples(id))
        return a to fileStem(w.startTime, sport.name)
    }

    /** Writes one workout to cache/export and returns a shareable content Uri. */
    suspend fun exportWorkout(id: Long, format: FileFormat): Uri = withContext(Dispatchers.IO) {
        val (a, stem) = activity(id) ?: error("workout $id not found")
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$stem.${format.ext}")
        file.writeBytes(ActivityCodec.write(a, format))
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** All finished workouts as one zip of activity files. Workouts without a track are skipped for GPX. */
    suspend fun exportAll(format: FileFormat, out: Uri): Int = withContext(Dispatchers.IO) {
        var n = 0
        context.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os).use { z ->
                val used = HashSet<String>()
                for (w in workouts.allFinished()) {
                    val (a, stem) = activity(w.id) ?: continue
                    if (format == FileFormat.GPX && a.points.isEmpty()) continue
                    var entry = "$stem.${format.ext}"; var k = 2
                    while (!used.add(entry)) entry = "$stem-${k++}.${format.ext}"
                    z.putNextEntry(ZipEntry(entry)); z.write(ActivityCodec.write(a, format)); z.closeEntry()
                    n++
                }
            }
        } ?: error("cannot open output")
        n
    }

    suspend fun backup(out: Uri): Int = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val list = workouts.allFinished()
        val file = BackupFile(
            appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(weightKg = s.weightKg, maxHr = s.maxHr, age = s.age),
            sports = sports.all().map(BackupCodec::sport),
            workouts = list.map { BackupCodec.workout(it, workouts.points(it.id), workouts.hrSamples(it.id)) },
        )
        context.contentResolver.openOutputStream(out)?.use { it.write(BackupCodec.encode(file)) } ?: error("cannot open output")
        list.size
    }

    companion object {
        private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        fun fileStem(start: Long, sportName: String): String {
            val t = stamp.format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))
            val safe = sportName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(24)
            return "dongyu_${t}_$safe"
        }
        fun backupName(now: Long = System.currentTimeMillis()) =
            "dongyu_backup_${stamp.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.zip"
        fun exportName(format: FileFormat, now: Long = System.currentTimeMillis()) =
            "dongyu_${format.ext}_${stamp.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.zip"
    }
}
