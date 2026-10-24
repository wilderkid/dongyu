package app.dongyu.model

import kotlinx.serialization.Serializable

/** How values of a metric are combined across samples / workouts. */
@Serializable
enum class Aggregation { SUM, AVG, MAX, MIN, LAST }

/** Physical unit / formatting family of a metric. */
enum class MetricUnit { SECONDS, METERS, PACE_KM, PACE_100M, KMH, BPM, KCAL, COUNT, KG, RPE, M_PER_H, CHOICE, TEXT, CUSTOM }

/**
 * Catalog of metric kinds a sport type can track.
 * [auto] metrics are computed by the app (GPS / timer / sensors); others are entered by the user.
 * [lowerIsBetter] is used for personal-record direction (e.g. pace).
 */
enum class MetricKind(
    val key: String,
    val unit: MetricUnit,
    val aggregation: Aggregation,
    val auto: Boolean,
    val lowerIsBetter: Boolean = false,
    val needsGps: Boolean = false,
) {
    DURATION("duration", MetricUnit.SECONDS, Aggregation.SUM, auto = true),
    DISTANCE("distance", MetricUnit.METERS, Aggregation.SUM, auto = true),
    PACE("pace", MetricUnit.PACE_KM, Aggregation.AVG, auto = true, lowerIsBetter = true),
    SPEED("speed", MetricUnit.KMH, Aggregation.AVG, auto = true),
    MAX_SPEED("max_speed", MetricUnit.KMH, Aggregation.MAX, auto = true, needsGps = true),
    SWIM_PACE("swim_pace", MetricUnit.PACE_100M, Aggregation.AVG, auto = true, lowerIsBetter = true),
    ELEVATION_GAIN("elev_gain", MetricUnit.METERS, Aggregation.SUM, auto = true, needsGps = true),
    ELEVATION_LOSS("elev_loss", MetricUnit.METERS, Aggregation.SUM, auto = true, needsGps = true),
    MAX_ALTITUDE("max_alt", MetricUnit.METERS, Aggregation.MAX, auto = true, needsGps = true),
    ASCENT_RATE("ascent_rate", MetricUnit.M_PER_H, Aggregation.AVG, auto = true, needsGps = true),
    HEART_RATE("hr_avg", MetricUnit.BPM, Aggregation.AVG, auto = false),
    MAX_HEART_RATE("hr_max", MetricUnit.BPM, Aggregation.MAX, auto = false),
    CALORIES("calories", MetricUnit.KCAL, Aggregation.SUM, auto = false),
    LAPS("laps", MetricUnit.COUNT, Aggregation.SUM, auto = false),
    POOL_LENGTH("pool_len", MetricUnit.METERS, Aggregation.LAST, auto = false),
    STROKE("stroke", MetricUnit.CHOICE, Aggregation.LAST, auto = false),
    SETS("sets", MetricUnit.COUNT, Aggregation.SUM, auto = false),
    REPS("reps", MetricUnit.COUNT, Aggregation.SUM, auto = false),
    WEIGHT("weight", MetricUnit.KG, Aggregation.MAX, auto = false),
    COUNT("count", MetricUnit.COUNT, Aggregation.SUM, auto = false),
    SCORE("score", MetricUnit.COUNT, Aggregation.MAX, auto = false),
    RPE("rpe", MetricUnit.RPE, Aggregation.AVG, auto = false),
    NOTE("note", MetricUnit.TEXT, Aggregation.LAST, auto = false),
    CUSTOM("custom", MetricUnit.CUSTOM, Aggregation.SUM, auto = false);

    val isNumeric: Boolean get() = unit != MetricUnit.TEXT && unit != MetricUnit.CHOICE

    companion object {
        fun fromKey(key: String): MetricKind? = entries.firstOrNull { it.key == key }
        /** Kinds a user may pick in the custom sport editor (CUSTOM is added separately). */
        val pickable: List<MetricKind> = entries.filter { it != CUSTOM }
    }
}

/** Swim strokes for the STROKE choice metric; stored as the ordinal (Double) in workout values. */
enum class SwimStroke { FREESTYLE, BREASTSTROKE, BACKSTROKE, BUTTERFLY, MIXED }

/** A metric attached to a sport type. [id] is the key used to store values on workouts. */
@Serializable
data class MetricDef(
    val kindKey: String,
    val id: String = kindKey,
    val customLabel: String? = null,
    val customUnit: String? = null,
    val customAggregation: Aggregation? = null,
) {
    val kind: MetricKind get() = MetricKind.fromKey(kindKey) ?: MetricKind.CUSTOM
    val aggregation: Aggregation get() = customAggregation ?: kind.aggregation

    companion object {
        fun of(kind: MetricKind) = MetricDef(kind.key)
        fun custom(id: String, label: String, unit: String, aggregation: Aggregation) =
            MetricDef(MetricKind.CUSTOM.key, id = id, customLabel = label, customUnit = unit, customAggregation = aggregation)
    }
}
