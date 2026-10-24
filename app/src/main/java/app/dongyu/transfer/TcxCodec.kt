package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.model.TrackPoint
import app.dongyu.transfer.Xml.all
import app.dongyu.transfer.Xml.child
import app.dongyu.transfer.Xml.childText
import app.dongyu.transfer.Xml.children
import app.dongyu.transfer.Xml.num
import java.io.InputStream

/** Garmin Training Center XML v2. */
object TcxCodec {
    fun parse(input: InputStream): List<ActivityData> {
        val doc = Xml.parse(input)
        if (doc.documentElement.localName != "TrainingCenterDatabase") throw ImportFormatException("Not a TCX file")
        return doc.all("Activity").mapNotNull { act ->
            val sport = act.getAttribute("Sport").takeIf { it.isNotEmpty() }
            val notes = act.childText("Notes")
            val laps = act.children("Lap")
            val points = ArrayList<TrackPoint>()
            val hr = ArrayList<HrSample>()
            var timer = 0.0; var dist = 0.0; var kcal = 0.0
            var hasTimer = false; var hasDist = false
            var trackIndex = 0
            laps.forEach { lap ->
                lap.childText("TotalTimeSeconds")?.toDoubleOrNull()?.let { timer += it; hasTimer = true }
                lap.childText("DistanceMeters")?.toDoubleOrNull()?.let { dist += it; hasDist = true }
                lap.childText("Calories")?.toDoubleOrNull()?.let { kcal += it }
                lap.children("Track").forEach { trk ->
                    // Garmin starts a new <Track> after each pause, so tracks map onto our segments.
                    val seg = trackIndex++
                    trk.children("Trackpoint").forEach { tp ->
                        val t = Xml.time(tp.childText("Time")) ?: return@forEach
                        tp.child("HeartRateBpm")?.childText("Value")?.toIntOrNull()?.takeIf { it in 25..250 }?.let { hr += HrSample(t, it) }
                        val pos = tp.child("Position") ?: return@forEach
                        val lat = pos.childText("LatitudeDegrees")?.toDoubleOrNull() ?: return@forEach
                        val lon = pos.childText("LongitudeDegrees")?.toDoubleOrNull() ?: return@forEach
                        points += TrackPoint(time = t, lat = lat, lon = lon, altitude = tp.childText("AltitudeMeters")?.toDoubleOrNull(), segment = seg)
                    }
                }
            }
            val start = Xml.time(act.childText("Id")) ?: Xml.time(laps.firstOrNull()?.getAttribute("StartTime"))
                ?: points.firstOrNull()?.time ?: hr.firstOrNull()?.time ?: return@mapNotNull null
            val end = maxOf(points.lastOrNull()?.time ?: 0, hr.lastOrNull()?.time ?: 0, start + (timer * 1000).toLong())
            ActivityData(
                sport = SportHint(SportNames.familyOf(sport?.takeUnless { it == "Other" }) ?: SportNames.familyOf(notes), notes ?: sport),
                name = notes, startTime = start, endTime = end,
                durationSec = if (hasTimer) timer.toLong() else null,
                distanceM = if (hasDist) dist else null,
                calories = kcal.takeIf { it > 0 },
                points = points.sortedBy { it.time }, hr = hr.sortedBy { it.time },
            )
        }.ifEmpty { throw ImportFormatException("TCX has no activities") }
    }

    fun write(a: ActivityData): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""").append('\n')
        append("  <Activities>\n")
        append("    <Activity Sport=\"${a.sport.family?.let(SportNames::tcxSport) ?: "Other"}\">\n")
        append("      <Id>${Xml.iso(a.startTime)}</Id>\n")
        append("      <Lap StartTime=\"${Xml.iso(a.startTime)}\">\n")
        append("        <TotalTimeSeconds>${a.durationSec ?: ((a.endTime - a.startTime) / 1000)}</TotalTimeSeconds>\n")
        append("        <DistanceMeters>${num(a.distanceM ?: 0.0, 1)}</DistanceMeters>\n")
        append("        <Calories>${(a.calories ?: 0.0).toInt()}</Calories>\n")
        if (a.hr.isNotEmpty()) {
            append("        <AverageHeartRateBpm><Value>${a.hr.map { it.bpm }.average().toInt()}</Value></AverageHeartRateBpm>\n")
            append("        <MaximumHeartRateBpm><Value>${a.hr.maxOf { it.bpm }}</Value></MaximumHeartRateBpm>\n")
        }
        append("        <Intensity>Active</Intensity>\n        <TriggerMethod>Manual</TriggerMethod>\n")
        val cum = cumulativeDistance(a.points)
        if (a.points.isNotEmpty()) {
            val hr = HrMatcher(a.hr)
            a.points.forEachIndexed { i, p ->
                if (i == 0 || p.segment != a.points[i - 1].segment) {
                    if (i > 0) append("        </Track>\n")
                    append("        <Track>\n")
                }
                append("          <Trackpoint><Time>${Xml.iso(p.time)}</Time>")
                append("<Position><LatitudeDegrees>${num(p.lat, 7)}</LatitudeDegrees><LongitudeDegrees>${num(p.lon, 7)}</LongitudeDegrees></Position>")
                (p.baroAltitude ?: p.altitude)?.let { append("<AltitudeMeters>${num(it, 1)}</AltitudeMeters>") }
                append("<DistanceMeters>${num(cum[i], 1)}</DistanceMeters>")
                hr.at(p.time)?.let { append("<HeartRateBpm><Value>$it</Value></HeartRateBpm>") }
                append("</Trackpoint>\n")
            }
        } else {
            append("        <Track>\n")
            a.hr.forEach { s -> append("          <Trackpoint><Time>${Xml.iso(s.time)}</Time><HeartRateBpm><Value>${s.bpm}</Value></HeartRateBpm></Trackpoint>\n") }
        }
        append("        </Track>\n      </Lap>\n")
        (a.name ?: a.sport.label)?.let { append("      <Notes>${Xml.esc(it)}</Notes>\n") }
        append("      <Creator xsi:type=\"Device_t\"><Name>Dongyu</Name><UnitId>0</UnitId><ProductID>0</ProductID>")
        append("<Version><VersionMajor>0</VersionMajor><VersionMinor>2</VersionMinor></Version></Creator>\n")
        append("    </Activity>\n  </Activities>\n</TrainingCenterDatabase>\n")
    }
}

/** Running distance along the track, not counting jumps between segments. */
internal fun cumulativeDistance(points: List<TrackPoint>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        out[i] = out[i - 1] + if (a.segment == b.segment) app.dongyu.domain.metrics.GeoMath.haversine(a.lat, a.lon, b.lat, b.lon) else 0.0
    }
    return out
}
