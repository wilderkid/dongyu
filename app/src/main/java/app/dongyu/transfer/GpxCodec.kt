package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.model.TrackPoint
import app.dongyu.transfer.Xml.all
import app.dongyu.transfer.Xml.child
import app.dongyu.transfer.Xml.childText
import app.dongyu.transfer.Xml.children
import app.dongyu.transfer.Xml.find
import app.dongyu.transfer.Xml.num
import java.io.InputStream

/** GPX 1.1 with Garmin TrackPointExtension heart rate. Each &lt;trk&gt; becomes one activity. */
object GpxCodec {
    fun parse(input: InputStream): List<ActivityData> {
        val doc = Xml.parse(input)
        if (doc.documentElement.localName != "gpx") throw ImportFormatException("Not a GPX file")
        val metaTime = doc.documentElement.child("metadata")?.childText("time")
        return doc.all("trk").mapNotNull { trk ->
            val points = ArrayList<TrackPoint>()
            val hr = ArrayList<HrSample>()
            trk.children("trkseg").forEachIndexed { seg, s ->
                s.children("trkpt").forEach { p ->
                    val lat = p.getAttribute("lat").toDoubleOrNull() ?: return@forEach
                    val lon = p.getAttribute("lon").toDoubleOrNull() ?: return@forEach
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach
                    val t = Xml.time(p.childText("time")) ?: return@forEach
                    points += TrackPoint(time = t, lat = lat, lon = lon, altitude = p.childText("ele")?.toDoubleOrNull(), segment = seg)
                    p.child("extensions")?.find("hr")?.textContent?.trim()?.toIntOrNull()?.takeIf { it in 25..250 }?.let { hr += HrSample(t, it) }
                }
            }
            if (points.isEmpty()) return@mapNotNull null
            points.sortBy { it.time }
            val type = trk.childText("type")
            val name = trk.childText("name")
            ActivityData(
                sport = SportHint(SportNames.familyOf(type) ?: SportNames.familyOf(name), type),
                name = name,
                startTime = Xml.time(metaTime)?.takeIf { it <= points.first().time } ?: points.first().time,
                endTime = points.last().time,
                points = points, hr = hr.sortedBy { it.time },
            )
        }.ifEmpty { throw ImportFormatException("GPX has no timed track points") }
    }

    fun write(a: ActivityData): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Dongyu" xmlns="http://www.topografix.com/GPX/1/1" """)
        append("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1" """)
        append("""xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """)
        append("""xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""").append('\n')
        append("  <metadata><time>${Xml.iso(a.startTime)}</time></metadata>\n")
        append("  <trk>\n")
        a.name?.let { append("    <name>${Xml.esc(it)}</name>\n") }
        append("    <type>${Xml.esc(a.sport.label ?: a.sport.family?.let(SportNames::gpxType) ?: "other")}</type>\n")
        val hr = HrMatcher(a.hr)
        a.points.groupBy { it.segment }.toSortedMap().values.forEach { seg ->
            append("    <trkseg>\n")
            seg.forEach { p ->
                append("      <trkpt lat=\"${num(p.lat, 7)}\" lon=\"${num(p.lon, 7)}\">")
                (p.baroAltitude ?: p.altitude)?.let { append("<ele>${num(it, 1)}</ele>") }
                append("<time>${Xml.iso(p.time)}</time>")
                hr.at(p.time)?.let { append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>$it</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>") }
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }
}

/** Nearest heart-rate sample within [toleranceMs] of a timestamp (samples sorted by time). */
class HrMatcher(private val samples: List<HrSample>, private val toleranceMs: Long = 5_000) {
    private var i = 0
    fun at(t: Long): Int? {
        if (samples.isEmpty()) return null
        while (i + 1 < samples.size && samples[i + 1].time <= t) i++
        val cands = listOfNotNull(samples.getOrNull(i), samples.getOrNull(i + 1))
        val best = cands.minByOrNull { kotlin.math.abs(it.time - t) } ?: return null
        return best.bpm.takeIf { kotlin.math.abs(best.time - t) <= toleranceMs }
    }
}
