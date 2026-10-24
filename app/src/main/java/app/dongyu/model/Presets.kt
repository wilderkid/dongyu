package app.dongyu.model

import app.dongyu.model.MetricKind.*

object Presets {
    const val RUN = "run"
    const val HIKE = "hike"
    const val MOUNTAINEER = "mountaineer"
    const val SWIM = "swim"
    const val CYCLE = "cycle"

    private fun defs(vararg kinds: MetricKind) = kinds.map { MetricDef.of(it) }

    val all: List<SportType> = listOf(
        SportType(
            presetKey = RUN, name = "跑步", icon = "run", color = 0xFFFF6B35, usesGps = true,
            family = SportFamily.RUN, met = 9.8, sortOrder = 0,
            metrics = defs(DURATION, DISTANCE, PACE, SPEED, ELEVATION_GAIN, HEART_RATE, CALORIES, RPE, NOTE),
        ),
        SportType(
            presetKey = HIKE, name = "爬山/徒步", icon = "hike", color = 0xFF2EAD6B, usesGps = true,
            family = SportFamily.HIKE, met = 6.0, sortOrder = 1,
            metrics = defs(DURATION, DISTANCE, PACE, ELEVATION_GAIN, ELEVATION_LOSS, MAX_ALTITUDE, HEART_RATE, CALORIES, RPE, NOTE),
        ),
        SportType(
            presetKey = MOUNTAINEER, name = "登山", icon = "mountain", color = 0xFF7C5CFF, usesGps = true,
            family = SportFamily.MOUNTAINEER, met = 8.0, sortOrder = 2,
            metrics = defs(DURATION, DISTANCE, ELEVATION_GAIN, ELEVATION_LOSS, MAX_ALTITUDE, ASCENT_RATE, HEART_RATE, CALORIES, RPE, NOTE),
        ),
        SportType(
            presetKey = SWIM, name = "游泳", icon = "swim", color = 0xFF1E9BF0, usesGps = false,
            family = SportFamily.SWIM, met = 8.3, sortOrder = 3,
            metrics = defs(DURATION, DISTANCE, SWIM_PACE, LAPS, POOL_LENGTH, STROKE, HEART_RATE, CALORIES, RPE, NOTE),
        ),
        SportType(
            presetKey = CYCLE, name = "骑行", icon = "bike", color = 0xFFF2B705, usesGps = true,
            family = SportFamily.CYCLE, met = 7.5, sortOrder = 4,
            metrics = defs(DURATION, DISTANCE, SPEED, MAX_SPEED, ELEVATION_GAIN, ELEVATION_LOSS, HEART_RATE, CALORIES, RPE, NOTE),
        ),
    )
}
