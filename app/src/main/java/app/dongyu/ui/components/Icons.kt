package app.dongyu.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import app.dongyu.model.MetricKind

object SportIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "hike" to Icons.Filled.Hiking,
        "mountain" to Icons.Filled.Terrain,
        "swim" to Icons.Filled.Pool,
        "bike" to Icons.AutoMirrored.Filled.DirectionsBike,
        "walk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "nordic" to Icons.Filled.NordicWalking,
        "badminton" to Icons.Filled.SportsTennis,
        "basketball" to Icons.Filled.SportsBasketball,
        "soccer" to Icons.Filled.SportsSoccer,
        "volleyball" to Icons.Filled.SportsVolleyball,
        "baseball" to Icons.Filled.SportsBaseball,
        "golf" to Icons.Filled.SportsGolf,
        "strength" to Icons.Filled.FitnessCenter,
        "yoga" to Icons.Filled.SelfImprovement,
        "boxing" to Icons.Filled.SportsMma,
        "ski" to Icons.Filled.DownhillSkiing,
        "snowboard" to Icons.Filled.Snowboarding,
        "skate" to Icons.Filled.IceSkating,
        "skateboard" to Icons.Filled.Skateboarding,
        "rowing" to Icons.Filled.Rowing,
        "kayak" to Icons.Filled.Kayaking,
        "surf" to Icons.Filled.Surfing,
        "sail" to Icons.Filled.Sailing,
        "dive" to Icons.Filled.ScubaDiving,
        "paraglide" to Icons.Filled.Paragliding,
        "handball" to Icons.Filled.SportsHandball,
        "landscape" to Icons.Filled.Landscape,
    )
    private val map = all.toMap()
    fun of(key: String): ImageVector = map[key] ?: Icons.Filled.EmojiEvents

    fun metric(kind: MetricKind): ImageVector = when (kind) {
        MetricKind.DURATION -> Icons.Filled.Timer
        MetricKind.DISTANCE -> Icons.Filled.Straighten
        MetricKind.PACE -> Icons.Filled.Speed
        MetricKind.SPEED -> Icons.Filled.Speed
        MetricKind.MAX_SPEED -> Icons.Filled.Bolt
        MetricKind.SWIM_PACE -> Icons.Filled.Waves
        MetricKind.ELEVATION_GAIN -> Icons.AutoMirrored.Filled.TrendingUp
        MetricKind.ELEVATION_LOSS -> Icons.AutoMirrored.Filled.TrendingDown
        MetricKind.MAX_ALTITUDE -> Icons.Filled.Landscape
        MetricKind.ASCENT_RATE -> Icons.Filled.North
        MetricKind.HEART_RATE -> Icons.Filled.Favorite
        MetricKind.MAX_HEART_RATE -> Icons.Filled.MonitorHeart
        MetricKind.CALORIES -> Icons.Filled.LocalFireDepartment
        MetricKind.LAPS -> Icons.Filled.Repeat
        MetricKind.POOL_LENGTH -> Icons.Filled.Pool
        MetricKind.STROKE -> Icons.Filled.Waves
        MetricKind.SETS -> Icons.Filled.FitnessCenter
        MetricKind.REPS -> Icons.Filled.Replay
        MetricKind.WEIGHT -> Icons.Filled.MonitorWeight
        MetricKind.COUNT -> Icons.Filled.Numbers
        MetricKind.SCORE -> Icons.Filled.EmojiEvents
        MetricKind.RPE -> Icons.Filled.Whatshot
        MetricKind.NOTE -> Icons.Filled.EditNote
        MetricKind.CUSTOM -> Icons.Filled.Tune
    }

    /** Palette offered in the sport editor. */
    val palette: List<Long> = listOf(
        0xFFFF6B35, 0xFFE5487B, 0xFFB548E5, 0xFF7C5CFF, 0xFF3F6BFF, 0xFF1E9BF0, 0xFF00A8A8,
        0xFF2EAD6B, 0xFF8BC34A, 0xFFF2B705, 0xFFFF9800, 0xFF8D6E63, 0xFF607D8B, 0xFF263238,
    )
}
