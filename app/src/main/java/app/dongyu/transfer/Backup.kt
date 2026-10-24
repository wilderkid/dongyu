package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.model.MetricDef
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import app.dongyu.model.WorkoutStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupSport(
    val id: Long, val presetKey: String? = null, val name: String, val icon: String, val color: Long,
    val usesGps: Boolean, val family: String = "GENERIC", val metrics: List<MetricDef> = emptyList(),
    val met: Double = 6.0, val sortOrder: Int = 0, val archived: Boolean = false,
)

/** Compact point: t, lat, lon, alt?, baro?, segment. */
@Serializable
data class BackupPoint(val t: Long, val la: Double, val lo: Double, val a: Double? = null, val b: Double? = null, val s: Int = 0)

@Serializable
data class BackupWorkout(
    val sportId: Long, val startTime: Long, val endTime: Long, val durationSec: Long, val distanceM: Double = 0.0,
    val values: Map<String, Double> = emptyMap(), val note: String = "", val source: String = "MANUAL",
    val swimMode: String? = null, val points: List<BackupPoint> = emptyList(), val hr: List<List<Long>> = emptyList(),
)

@Serializable
data class BackupSettings(
    val weightKg: Double? = null, val maxHr: Int? = null, val age: Int? = null,
    val autoPause: Boolean? = null, val keepScreenOn: Boolean? = null, val themeMode: Int? = null,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT, val version: Int = 1, val appVersion: String = "", val exportedAt: Long = 0,
    val settings: BackupSettings = BackupSettings(),
    val sports: List<BackupSport> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
) {
    companion object { const val FORMAT = "dongyu-backup" }
}

object BackupCodec {
    const val ENTRY = "backup.json"
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    fun sport(s: SportType) = BackupSport(s.id, s.presetKey, s.name, s.icon, s.color, s.usesGps, s.family.name, s.metrics, s.met, s.sortOrder, s.archived)

    fun sport(b: BackupSport) = SportType(
        id = 0, presetKey = b.presetKey, name = b.name, icon = b.icon, color = b.color, usesGps = b.usesGps,
        family = runCatching { SportFamily.valueOf(b.family) }.getOrDefault(SportFamily.GENERIC),
        metrics = b.metrics, met = b.met, sortOrder = b.sortOrder, archived = b.archived,
    )

    fun workout(w: Workout, pts: List<TrackPoint>, hr: List<HrSample>) = BackupWorkout(
        w.sportId, w.startTime, w.endTime, w.durationSec, w.distanceM, w.values, w.note, w.source.name, w.swimMode?.name,
        pts.map { BackupPoint(it.time, it.lat, it.lon, it.altitude, it.baroAltitude, it.segment) },
        hr.map { listOf(it.time, it.bpm.toLong()) },
    )

    fun workout(b: BackupWorkout, sportId: Long) = Workout(
        sportId = sportId, startTime = b.startTime, endTime = b.endTime, durationSec = b.durationSec, distanceM = b.distanceM,
        values = b.values, note = b.note,
        source = runCatching { WorkoutSource.valueOf(b.source) }.getOrDefault(WorkoutSource.MANUAL),
        status = WorkoutStatus.FINISHED,
        swimMode = b.swimMode?.let { runCatching { SwimMode.valueOf(it) }.getOrNull() },
    )

    fun points(b: BackupWorkout) = b.points.map { TrackPoint(time = it.t, lat = it.la, lon = it.lo, altitude = it.a, baroAltitude = it.b, segment = it.s) }
    fun hr(b: BackupWorkout) = b.hr.filter { it.size >= 2 }.map { HrSample(it[0], it[1].toInt()) }

    fun encode(f: BackupFile): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry(ENTRY))
            z.write(json.encodeToString(BackupFile.serializer(), f).toByteArray())
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    /** Accepts our zip or a bare backup.json. */
    fun decode(bytes: ByteArray): BackupFile {
        val text = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                generateSequence { z.nextEntry }.firstOrNull { it.name.substringAfterLast('/') == ENTRY }
                    ?: throw ImportFormatException("No $ENTRY in archive")
                z.readBytes().toString(Charsets.UTF_8)
            }
        } else bytes.toString(Charsets.UTF_8)
        val f = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrElse { throw ImportFormatException("Invalid backup: ${it.message}") }
        if (f.format != BackupFile.FORMAT) throw ImportFormatException("Not a Dongyu backup")
        if (f.version > 1) throw ImportFormatException("Backup from a newer version")
        return f
    }

    /**
     * Maps backup sport ids to local ones: presets by key, custom sports by name; anything else is
     * created. Returns old id → (existing local sport or a new one to insert).
     */
    fun planSports(backup: List<BackupSport>, local: List<SportType>): Map<Long, Pair<SportType?, SportType?>> =
        backup.associate { b ->
            val existing = if (b.presetKey != null) local.firstOrNull { it.presetKey == b.presetKey }
            else local.firstOrNull { it.presetKey == null && it.name == b.name }
            b.id to if (existing != null) existing to null else null to sport(b)
        }
}
