package app.dongyu.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sport_types")
data class SportTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetKey: String?,
    val name: String,
    val icon: String,
    val color: Long,
    val usesGps: Boolean,
    val family: String,
    /** JSON list of MetricDef. */
    val metricsJson: String,
    val met: Double,
    val sortOrder: Int,
    val archived: Boolean,
)

@Entity(
    tableName = "workouts",
    indices = [Index("sportId"), Index("startTime"), Index("status")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sportId: Long,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Long,
    val distanceM: Double,
    /** JSON map metricId -> value. */
    val valuesJson: String,
    val note: String,
    val source: String,
    val status: String,
    val swimMode: String?,
    /** Wall-clock time of the last persisted progress; used for crash recovery. */
    val updatedAt: Long,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val segment: Int,
    val baroAltitude: Double?,
)

/** One heart-rate reading from a BLE strap, stored independently of GPS fixes. */
@Entity(
    tableName = "hr_samples",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId")],
)
data class HrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val time: Long,
    val bpm: Int,
)
