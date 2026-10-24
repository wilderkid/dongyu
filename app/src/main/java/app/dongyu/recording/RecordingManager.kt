package app.dongyu.recording

import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.dongyu.data.prefs.SettingsStore
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import app.dongyu.domain.metrics.AutoPauseDetector
import app.dongyu.domain.metrics.ElevationCalculator
import app.dongyu.domain.metrics.GeoMath
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.metrics.WorkoutSummaryCalculator
import app.dongyu.domain.hr.HrZones
import app.dongyu.model.MetricKind
import app.dongyu.recording.ble.HrConnState
import app.dongyu.recording.ble.HeartRateMonitor
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import app.dongyu.model.WorkoutStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RecStatus { IDLE, RECORDING, PAUSED, AUTO_PAUSED }

data class LiveState(
    val status: RecStatus = RecStatus.IDLE,
    val workoutId: Long? = null,
    val sport: SportType? = null,
    val usesGps: Boolean = false,
    val swimMode: SwimMode? = null,
    val movingMs: Long = 0,
    val distanceM: Double = 0.0,
    val currentPace: Double? = null,
    val currentSpeedKmh: Double? = null,
    val elevationGain: Double = 0.0,
    val altitude: Double? = null,
    val gpsAccuracy: Float? = null,
    val hasFix: Boolean = false,
    val lengths: Int = 0,
    val poolLength: Double = 25.0,
    val path: List<Pair<Double, Double>> = emptyList(),
    val lastSplitPace: Double? = null,
    val barometer: Boolean = false,
    /** Non-null when a heart-rate strap is configured for this session. */
    val hrState: HrConnState? = null,
    val heartRate: Int? = null,
    val maxHr: Int = 190,
) {
    val usesHr: Boolean get() = hrState != null
    val active: Boolean get() = status != RecStatus.IDLE
    val avgPace: Double? get() = Pace.secPerKm(distanceM, movingMs / 1000.0)
    val avgSpeedKmh: Double? get() = Pace.kmh(distanceM, movingMs / 1000.0)
    val swimPace: Double? get() = Pace.secPer100m(distanceM, movingMs / 1000.0)
}

/**
 * Owns the single in-progress workout. GPS fixes are delivered by [RecordingService];
 * every accepted point is written to Room immediately so a killed process loses nothing.
 */
class RecordingManager(
    private val context: Context,
    private val sports: SportRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    val hr: HeartRateMonitor,
) {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private val mutex = Mutex()
    private var ticker: Job? = null
    private var lastTick = 0L
    private var segment = 0
    private var lastPoint: TrackPoint? = null
    private val altitudes = mutableListOf<Double?>()
    private var autoPause: AutoPauseDetector? = null
    private var splitAnchorDist = 0.0
    private var splitAnchorMs = 0L
    private var barometer: Barometer? = null
    private var hrJob: Job? = null
    @Volatile private var lastHrSave = 0L

    suspend fun start(sport: SportType, swimMode: SwimMode? = null, poolLength: Double = 25.0): Long = mutex.withLock {
        val gps = sport.usesGps && swimMode != SwimMode.POOL || swimMode == SwimMode.OPEN_WATER
        val now = System.currentTimeMillis()
        val id = workouts.insert(
            Workout(
                sportId = sport.id, startTime = now, endTime = now, durationSec = 0,
                source = if (gps) WorkoutSource.GPS else WorkoutSource.TIMER,
                status = WorkoutStatus.RECORDING, swimMode = swimMode,
                values = if (swimMode == SwimMode.POOL) mapOf(MetricKind.POOL_LENGTH.key to poolLength) else emptyMap(),
            )
        )
        resetInternals(sport)
        _state.value = LiveState(
            status = RecStatus.RECORDING, workoutId = id, sport = sport, usesGps = gps,
            swimMode = swimMode, poolLength = poolLength, barometer = barometer?.available == true,
        )
        startTicker()
        startHr(id)
        if (gps || _state.value.usesHr) RecordingService.start(context)
        id
    }

    /** Connects the remembered strap (if any) and streams readings into the live state and the DB. */
    private suspend fun startHr(workoutId: Long) {
        stopHr()
        val st = settings.settings.first()
        val addr = st.hrDeviceAddress ?: return
        if (!hr.isSupported || !hr.hasPermissions()) return
        _state.update { it.copy(hrState = HrConnState.CONNECTING, maxHr = st.effectiveMaxHr) }
        hr.onMeasurement = { t, m ->
            if (_state.value.status == RecStatus.RECORDING && m.bpm in 25..250 && m.contact != false && t - lastHrSave >= 1000) {
                lastHrSave = t
                scope.launch { workouts.addHr(workoutId, t, m.bpm) }
            }
        }
        hr.connect(addr)
        hrJob = scope.launch {
            combine(hr.state, hr.bpm) { a, b -> a to b }.collect { (a, b) ->
                _state.update { if (it.active) it.copy(hrState = a, heartRate = b) else it }
            }
        }
    }

    private fun stopHr() {
        hr.onMeasurement = null
        hrJob?.cancel(); hrJob = null
        hr.disconnect()
    }

    private suspend fun resetInternals(sport: SportType) {
        segment = 0
        lastPoint = null; lastFix = null
        altitudes.clear()
        splitAnchorDist = 0.0
        splitAnchorMs = 0
        autoPause = if (settings.settings.first().autoPause && sport.family != SportFamily.SWIM)
            AutoPauseDetector.forFamily(sport.family) else null
        barometer?.stop()
        barometer = if (sport.family == SportFamily.MOUNTAINEER || sport.family == SportFamily.HIKE)
            Barometer(context).takeIf { it.available }?.also { it.start() } else null
    }

    /** Re-attach to an unfinished workout found in the database after a crash or process death. */
    suspend fun recover(w: Workout): Boolean = mutex.withLock {
        if (_state.value.active) return@withLock false
        val sport = sports.get(w.sportId) ?: return@withLock false
        val gps = w.source == WorkoutSource.GPS
        resetInternals(sport)
        val pts = workouts.points(w.id)
        // New segment after recovery: the gap is treated as a pause for GPS sessions.
        segment = (pts.maxOfOrNull { it.segment } ?: -1) + 1
        altitudes += pts.map { it.baroAltitude ?: it.altitude }
        lastPoint = null; lastFix = null
        var moving = w.durationSec * 1000
        if (!gps && w.status == WorkoutStatus.RECORDING) {
            // timer sessions keep running while the app was dead
            val entity = workouts.get(w.id)
            val gap = System.currentTimeMillis() - (entity?.endTime ?: w.startTime).coerceAtLeast(w.startTime)
            if (gap in 0..6 * 3600_000L) moving += gap
        }
        val lengths = w.values[MetricKind.LAPS.key]?.toInt() ?: 0
        val pool = w.values[MetricKind.POOL_LENGTH.key] ?: 25.0
        _state.value = LiveState(
            status = if (w.status == WorkoutStatus.PAUSED) RecStatus.PAUSED else RecStatus.RECORDING,
            workoutId = w.id, sport = sport, usesGps = gps, swimMode = w.swimMode,
            movingMs = moving, distanceM = w.distanceM, lengths = lengths, poolLength = pool,
            path = pts.map { it.lat to it.lon },
            elevationGain = ElevationCalculator.compute(altitudes).gain,
            barometer = barometer != null,
        )
        splitAnchorDist = (w.distanceM / 1000).toInt() * 1000.0
        splitAnchorMs = moving
        startTicker()
        startHr(w.id)
        if (gps || _state.value.usesHr) RecordingService.start(context)
        true
    }

    private fun startTicker() {
        ticker?.cancel()
        lastTick = SystemClock.elapsedRealtime()
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val dt = now - lastTick
                lastTick = now
                _state.update { if (it.status == RecStatus.RECORDING) it.copy(movingMs = it.movingMs + dt) else it }
                if (++n % 5 == 0) persistProgress()
            }
        }
    }

    private suspend fun persistProgress() {
        val s = _state.value
        val id = s.workoutId ?: return
        val status = if (s.status == RecStatus.RECORDING) WorkoutStatus.RECORDING else WorkoutStatus.PAUSED
        workouts.get(id)?.let {
            workouts.update(
                it.copy(
                    durationSec = s.movingMs / 1000, distanceM = s.distanceM, status = status,
                    endTime = System.currentTimeMillis(),
                    values = it.values + (if (s.swimMode == SwimMode.POOL) mapOf(MetricKind.LAPS.key to s.lengths.toDouble()) else emptyMap()),
                )
            )
        }
    }

    fun pause() {
        _state.update { if (it.active) it.copy(status = RecStatus.PAUSED, currentPace = null, currentSpeedKmh = null) else it }
        autoPause?.reset()
        scope.launch { persistProgress() }
    }

    fun resume() {
        if (!_state.value.active) return
        segment++
        lastPoint = null; lastFix = null
        lastTick = SystemClock.elapsedRealtime()
        _state.update { it.copy(status = RecStatus.RECORDING) }
    }

    /** Swimming pool mode: one more pool length. */
    fun addLength(delta: Int = 1) {
        _state.update {
            val l = (it.lengths + delta).coerceAtLeast(0)
            it.copy(lengths = l, distanceM = l * it.poolLength)
        }
    }

    /** Called by the service for every location fix. */
    private var lastFix: Triple<Long, Double, Double>? = null

    fun onLocation(time: Long, lat: Double, lon: Double, alt: Double?, accuracy: Float?, speed: Float?) {
        val s = _state.value
        if (!s.active || !s.usesGps) return
        val id = s.workoutId ?: return
        val acc = accuracy ?: 99f
        _state.update { it.copy(gpsAccuracy = accuracy, hasFix = acc <= 30f, altitude = barometer?.altitude() ?: alt) }
        if (acc > 30f) return
        if (alt != null) barometer?.calibrate(alt, acc)

        val prev = lastPoint
        val d = if (prev != null && prev.segment == segment) GeoMath.haversine(prev.lat, prev.lon, lat, lon) else 0.0
        val dt = if (prev != null) (time - prev.time) / 1000.0 else 0.0
        // Some devices (and mock providers) report 0 or no speed: fall back to displacement between raw fixes.
        val lf = lastFix
        lastFix = Triple(time, lat, lon)
        val derived = if (lf != null && time > lf.first) {
            val moved = GeoMath.haversine(lf.second, lf.third, lat, lon)
            val sec = (time - lf.first) / 1000.0
            if (sec <= 30 && moved > acc * 0.5) moved / sec else 0.0
        } else 0.0
        val reported = speed?.toDouble()?.takeIf { it > 0.3 }
        val v = reported ?: derived

        autoPause?.let { ap ->
            when (ap.update(time, v)) {
                AutoPauseDetector.Event.PAUSE -> { _state.update { it.copy(status = RecStatus.AUTO_PAUSED, currentPace = null, currentSpeedKmh = null) }; return }
                AutoPauseDetector.Event.RESUME -> if (_state.value.status == RecStatus.AUTO_PAUSED) {
                    segment++; lastPoint = null; lastTick = SystemClock.elapsedRealtime()
                    _state.update { it.copy(status = RecStatus.RECORDING) }
                }
                AutoPauseDetector.Event.NONE -> {}
            }
        }
        if (_state.value.status != RecStatus.RECORDING) return
        // reject teleports (> 50 m/s)
        if (dt > 0 && d / dt > 50) return

        val point = TrackPoint(time, lat, lon, alt, accuracy, speed, segment, barometer?.altitude())
        lastPoint = point
        altitudes += point.baroAltitude ?: point.altitude
        val gain = if (altitudes.size % 5 == 0) ElevationCalculator.compute(altitudes, threshold = if (barometer != null) 1.0 else 3.0).gain else _state.value.elevationGain
        _state.update {
            val dist = it.distanceM + d
            var lastSplit = it.lastSplitPace
            if (dist - splitAnchorDist >= 1000) {
                lastSplit = (it.movingMs - splitAnchorMs) / 1000.0 / ((dist - splitAnchorDist) / 1000.0)
                splitAnchorDist += 1000; splitAnchorMs = it.movingMs
            }
            it.copy(
                distanceM = dist,
                currentPace = Pace.mpsToSecPerKm(v),
                currentSpeedKmh = v * 3.6,
                elevationGain = gain,
                path = it.path + (lat to lon),
                lastSplitPace = lastSplit,
            )
        }
        scope.launch { workouts.addPoint(id, point) }
    }

    /** Finalizes the workout; returns its id so the UI can open the finish editor. */
    suspend fun stop(): Long? = mutex.withLock {
        val s = _state.value
        val id = s.workoutId ?: return@withLock null
        ticker?.cancel()
        barometer?.stop()
        stopHr()
        RecordingService.stop(context)
        val w = workouts.get(id) ?: return@withLock null
        val sport = s.sport ?: sports.get(w.sportId)
        val st = settings.settings.first()
        val weight = st.weightKg
        var values = w.values.toMutableMap()
        var distance = s.distanceM
        var moving = s.movingMs / 1000
        if (s.usesGps && sport != null) {
            val pts = workouts.points(id)
            val summary = WorkoutSummaryCalculator.fromTrack(pts, sport, weight)
            values.putAll(summary.values)
            if (summary.distanceM > 0) distance = summary.distanceM
            if (sport.family == SportFamily.SWIM) {
                Pace.secPer100m(distance, moving.toDouble())?.let { values[MetricKind.SWIM_PACE.key] = it }
            }
        } else if (sport != null) {
            values[MetricKind.CALORIES.key] = WorkoutSummaryCalculator.estimateCalories(sport.met, moving.toDouble(), weight)
            if (s.swimMode == SwimMode.POOL) {
                values[MetricKind.LAPS.key] = s.lengths.toDouble()
                values[MetricKind.POOL_LENGTH.key] = s.poolLength
                Pace.secPer100m(distance, moving.toDouble())?.let { values[MetricKind.SWIM_PACE.key] = it }
            }
        }
        HrZones.summarize(workouts.hrSamples(id), st.effectiveMaxHr)?.let { values.putAll(HrZones.toValues(it)) }
        workouts.update(
            w.copy(
                endTime = System.currentTimeMillis(), durationSec = moving, distanceM = distance,
                values = values, status = WorkoutStatus.FINISHED,
            )
        )
        _state.value = LiveState()
        id
    }

    /** Discards the current (or a recovered-but-not-resumed) workout. */
    suspend fun discard(id: Long? = _state.value.workoutId) = mutex.withLock {
        ticker?.cancel()
        barometer?.stop()
        stopHr()
        RecordingService.stop(context)
        id?.let { workouts.delete(it) }
        _state.value = LiveState()
    }

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
