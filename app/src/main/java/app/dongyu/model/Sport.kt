package app.dongyu.model

/** Drives special recording behaviour; custom sports are always GENERIC. */
enum class SportFamily { RUN, HIKE, MOUNTAINEER, SWIM, CYCLE, GENERIC }

data class SportType(
    val id: Long = 0,
    /** Non-null for built-in presets; used to resolve a localized name. */
    val presetKey: String? = null,
    val name: String,
    val icon: String,
    val color: Long,
    val usesGps: Boolean,
    val family: SportFamily = SportFamily.GENERIC,
    val metrics: List<MetricDef>,
    /** Metabolic equivalent used for calorie estimates. */
    val met: Double = 6.0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
) {
    fun has(kind: MetricKind) = metrics.any { it.kind == kind }
    /** Split length for per-split tables, null when splits don't apply. */
    val splitDistanceM: Double?
        get() = when {
            !usesGps && family != SportFamily.SWIM -> null
            family == SportFamily.SWIM -> 100.0
            family == SportFamily.CYCLE -> 5000.0
            else -> 1000.0
        }
    val showsPace: Boolean get() = has(MetricKind.PACE)
}

enum class WorkoutSource { GPS, TIMER, MANUAL, IMPORT }
enum class WorkoutStatus { RECORDING, PAUSED, FINISHED }
enum class SwimMode { POOL, OPEN_WATER }

data class Workout(
    val id: Long = 0,
    val sportId: Long,
    val startTime: Long,
    val endTime: Long,
    /** Moving time (pauses excluded). */
    val durationSec: Long,
    val distanceM: Double = 0.0,
    val values: Map<String, Double> = emptyMap(),
    val note: String = "",
    val source: WorkoutSource = WorkoutSource.MANUAL,
    val status: WorkoutStatus = WorkoutStatus.FINISHED,
    val swimMode: SwimMode? = null,
) {
    /** Uniform accessor used by stats: core columns first, then the values map. */
    fun valueOf(id: String): Double? = when (id) {
        MetricKind.DURATION.key -> durationSec.toDouble()
        MetricKind.DISTANCE.key -> distanceM.takeIf { it > 0 }
        else -> values[id]
    }
    val elevationGain: Double get() = values[MetricKind.ELEVATION_GAIN.key] ?: 0.0
    val calories: Double get() = values[MetricKind.CALORIES.key] ?: 0.0
}

/** One GPS fix. Coordinates are raw WGS-84. */
data class TrackPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val segment: Int = 0,
    /** Barometric altitude (m) if a pressure sensor was available. */
    val baroAltitude: Double? = null,
)

/** Well-known keys for derived values stored on workouts. */
object DerivedKeys {
    const val BEST_1K = "best_1k"
    const val BEST_5K = "best_5k"
    const val BEST_10K = "best_10k"
    const val BEST_HALF = "best_21k"
}
