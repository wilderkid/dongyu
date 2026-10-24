package app.dongyu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.MetricUnit
import app.dongyu.model.Presets
import app.dongyu.model.SportType
import app.dongyu.model.SwimStroke
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun MetricKind.labelRes(): Int = when (this) {
    MetricKind.DURATION -> R.string.metric_duration
    MetricKind.DISTANCE -> R.string.metric_distance
    MetricKind.PACE -> R.string.metric_pace
    MetricKind.SPEED -> R.string.metric_speed
    MetricKind.MAX_SPEED -> R.string.metric_max_speed
    MetricKind.SWIM_PACE -> R.string.metric_swim_pace
    MetricKind.ELEVATION_GAIN -> R.string.metric_elev_gain
    MetricKind.ELEVATION_LOSS -> R.string.metric_elev_loss
    MetricKind.MAX_ALTITUDE -> R.string.metric_max_alt
    MetricKind.ASCENT_RATE -> R.string.metric_ascent_rate
    MetricKind.HEART_RATE -> R.string.metric_hr
    MetricKind.MAX_HEART_RATE -> R.string.metric_hr_max
    MetricKind.CALORIES -> R.string.metric_calories
    MetricKind.LAPS -> R.string.metric_laps
    MetricKind.POOL_LENGTH -> R.string.metric_pool_len
    MetricKind.STROKE -> R.string.metric_stroke
    MetricKind.SETS -> R.string.metric_sets
    MetricKind.REPS -> R.string.metric_reps
    MetricKind.WEIGHT -> R.string.metric_weight
    MetricKind.COUNT -> R.string.metric_count
    MetricKind.SCORE -> R.string.metric_score
    MetricKind.RPE -> R.string.metric_rpe
    MetricKind.NOTE -> R.string.metric_note
    MetricKind.CUSTOM -> R.string.metric_custom
}

fun MetricUnit.unitRes(): Int? = when (this) {
    MetricUnit.SECONDS -> null
    MetricUnit.METERS -> R.string.unit_m
    MetricUnit.PACE_KM -> R.string.unit_per_km
    MetricUnit.PACE_100M -> R.string.unit_per_100m
    MetricUnit.KMH -> R.string.unit_kmh
    MetricUnit.BPM -> R.string.unit_bpm
    MetricUnit.KCAL -> R.string.unit_kcal
    MetricUnit.COUNT -> R.string.unit_times
    MetricUnit.KG -> R.string.unit_kg
    MetricUnit.RPE -> null
    MetricUnit.M_PER_H -> R.string.unit_m_per_h
    MetricUnit.CHOICE, MetricUnit.TEXT, MetricUnit.CUSTOM -> null
}

@Composable
fun MetricDef.label(): String = customLabel ?: stringResource(kind.labelRes())

fun SwimStroke.labelRes(): Int = when (this) {
    SwimStroke.FREESTYLE -> R.string.stroke_free
    SwimStroke.BREASTSTROKE -> R.string.stroke_breast
    SwimStroke.BACKSTROKE -> R.string.stroke_back
    SwimStroke.BUTTERFLY -> R.string.stroke_fly
    SwimStroke.MIXED -> R.string.stroke_mixed
}

/** Value + unit as separate strings so the UI can style them differently. */
data class Formatted(val value: String, val unit: String)

object Fmt {
    fun num(v: Double, decimals: Int = 0): String = String.format(Locale.US, "%.${decimals}f", v)
    fun km(m: Double): String = num(m / 1000.0, if (m >= 100_000) 0 else if (m >= 10_000) 1 else 2)
    fun hours(sec: Long): String = num(sec / 3600.0, 1)
    fun hm(sec: Long): String = "%d:%02d".format(sec / 3600, (sec % 3600) / 60)
    fun percent(d: Double): String = (if (d >= 0) "+" else "−") + num(abs(d) * 100, 0) + "%"
}

@Composable
fun formatMetric(def: MetricDef, v: Double): Formatted {
    val k = def.kind
    return when (k.unit) {
        MetricUnit.SECONDS -> Formatted(Pace.formatDuration(v.toLong()), "")
        MetricUnit.METERS -> if (k == MetricKind.DISTANCE) {
            if (v >= 1000) Formatted(Fmt.km(v), stringResource(R.string.unit_km)) else Formatted(Fmt.num(v), stringResource(R.string.unit_m))
        } else Formatted(Fmt.num(v), stringResource(R.string.unit_m))
        MetricUnit.PACE_KM -> Formatted(Pace.format(v), stringResource(R.string.unit_per_km))
        MetricUnit.PACE_100M -> Formatted(Pace.format(v), stringResource(R.string.unit_per_100m))
        MetricUnit.KMH -> Formatted(Fmt.num(v, 1), stringResource(R.string.unit_kmh))
        MetricUnit.RPE -> Formatted(Fmt.num(v, if (v % 1.0 == 0.0) 0 else 1), "/10")
        MetricUnit.CHOICE -> Formatted(
            SwimStroke.entries.getOrNull(v.roundToInt())?.let { stringResource(it.labelRes()) } ?: "-", "",
        )
        MetricUnit.CUSTOM -> Formatted(Fmt.num(v, if (v % 1.0 == 0.0) 0 else 1), def.customUnit ?: "")
        MetricUnit.KG -> Formatted(Fmt.num(v, if (v % 1.0 == 0.0) 0 else 1), stringResource(R.string.unit_kg))
        MetricUnit.COUNT -> Formatted(Fmt.num(v), stringResource(if (k == MetricKind.LAPS) R.string.unit_lengths else R.string.unit_times))
        else -> Formatted(Fmt.num(v), k.unit.unitRes()?.let { stringResource(it) } ?: "")
    }
}

@Composable
fun SportType.displayName(): String {
    val preset = Presets.all.firstOrNull { it.presetKey == presetKey }
    // Keep a user-renamed preset as typed, otherwise localize.
    if (preset == null || preset.name != name) return name
    return stringResource(
        when (presetKey) {
            Presets.RUN -> R.string.sport_run
            Presets.HIKE -> R.string.sport_hike
            Presets.MOUNTAINEER -> R.string.sport_mountaineer
            Presets.SWIM -> R.string.sport_swim
            else -> R.string.sport_cycle
        }
    )
}

@Composable
fun prLabel(key: String, sport: SportType): String = when (key) {
    DerivedKeys.BEST_1K -> stringResource(R.string.pr_1k)
    DerivedKeys.BEST_5K -> stringResource(R.string.pr_5k)
    DerivedKeys.BEST_10K -> stringResource(R.string.pr_10k)
    DerivedKeys.BEST_HALF -> stringResource(R.string.pr_half)
    else -> {
        val id = key.substringAfter('_')
        val def = sport.metrics.firstOrNull { it.id == id }
        val label = def?.label() ?: id
        if (key.startsWith("best_")) stringResource(R.string.pr_best_fmt, label) else stringResource(R.string.pr_max_fmt, label)
    }
}
