package app.dongyu.domain.metrics

/**
 * Speed based auto-pause with hysteresis.
 * Pauses after [pauseAfterMs] continuously below [stopSpeedMps];
 * resumes after [resumeSamples] consecutive samples at or above [resumeSpeedMps].
 */
class AutoPauseDetector(
    private val stopSpeedMps: Double = 0.7,
    private val resumeSpeedMps: Double = 1.0,
    private val pauseAfterMs: Long = 6_000,
    private val resumeSamples: Int = 2,
) {
    enum class Event { NONE, PAUSE, RESUME }

    var paused: Boolean = false
        private set
    private var slowSince: Long? = null
    private var fastCount = 0

    fun reset() {
        paused = false; slowSince = null; fastCount = 0
    }

    fun update(timeMs: Long, speedMps: Double): Event {
        if (!paused) {
            if (speedMps < stopSpeedMps) {
                val since = slowSince ?: timeMs.also { slowSince = it }
                if (timeMs - since >= pauseAfterMs) {
                    paused = true; fastCount = 0; slowSince = null
                    return Event.PAUSE
                }
            } else slowSince = null
        } else {
            if (speedMps >= resumeSpeedMps) {
                fastCount++
                if (fastCount >= resumeSamples) {
                    paused = false; fastCount = 0
                    return Event.RESUME
                }
            } else fastCount = 0
        }
        return Event.NONE
    }

    companion object {
        /** Sport-appropriate thresholds: walking-speed sports need lower limits. */
        fun forFamily(family: app.dongyu.model.SportFamily): AutoPauseDetector = when (family) {
            app.dongyu.model.SportFamily.CYCLE -> AutoPauseDetector(stopSpeedMps = 1.2, resumeSpeedMps = 1.8)
            app.dongyu.model.SportFamily.HIKE, app.dongyu.model.SportFamily.MOUNTAINEER ->
                AutoPauseDetector(stopSpeedMps = 0.25, resumeSpeedMps = 0.45, pauseAfterMs = 20_000)
            else -> AutoPauseDetector()
        }
    }
}
