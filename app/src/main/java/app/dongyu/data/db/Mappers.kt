package app.dongyu.data.db

import app.dongyu.model.MetricDef
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import app.dongyu.model.WorkoutStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
private val metricListSer = ListSerializer(MetricDef.serializer())
private val valuesSer = MapSerializer(String.serializer(), Double.serializer())

fun SportTypeEntity.toModel() = SportType(
    id = id, presetKey = presetKey, name = name, icon = icon, color = color, usesGps = usesGps,
    family = runCatching { SportFamily.valueOf(family) }.getOrDefault(SportFamily.GENERIC),
    metrics = runCatching { json.decodeFromString(metricListSer, metricsJson) }.getOrDefault(emptyList()),
    met = met, sortOrder = sortOrder, archived = archived,
)

fun SportType.toEntity() = SportTypeEntity(
    id = id, presetKey = presetKey, name = name, icon = icon, color = color, usesGps = usesGps,
    family = family.name, metricsJson = json.encodeToString(metricListSer, metrics),
    met = met, sortOrder = sortOrder, archived = archived,
)

fun WorkoutEntity.toModel() = Workout(
    id = id, sportId = sportId, startTime = startTime, endTime = endTime, durationSec = durationSec,
    distanceM = distanceM,
    values = runCatching { json.decodeFromString(valuesSer, valuesJson) }.getOrDefault(emptyMap()),
    note = note,
    source = WorkoutSource.valueOf(source),
    status = WorkoutStatus.valueOf(status),
    swimMode = swimMode?.let { SwimMode.valueOf(it) },
)

fun Workout.toEntity(updatedAt: Long = System.currentTimeMillis()) = WorkoutEntity(
    id = id, sportId = sportId, startTime = startTime, endTime = endTime, durationSec = durationSec,
    distanceM = distanceM, valuesJson = json.encodeToString(valuesSer, values.filterValues { !it.isNaN() && !it.isInfinite() }),
    note = note, source = source.name, status = status.name, swimMode = swimMode?.name, updatedAt = updatedAt,
)

fun TrackPointEntity.toModel() = TrackPoint(time, lat, lon, altitude, accuracy, speed, segment, baroAltitude)
fun TrackPoint.toEntity(workoutId: Long) = TrackPointEntity(
    workoutId = workoutId, time = time, lat = lat, lon = lon, altitude = altitude, accuracy = accuracy,
    speed = speed, segment = segment, baroAltitude = baroAltitude,
)
