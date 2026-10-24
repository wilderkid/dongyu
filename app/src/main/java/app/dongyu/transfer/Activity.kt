package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.model.SportFamily
import app.dongyu.model.TrackPoint

/** Sport as declared by a foreign file, before it is matched to one of the user's sports. */
data class SportHint(val family: SportFamily?, val label: String? = null)

/** A workout read from (or about to be written to) GPX / TCX / FIT. */
data class ActivityData(
    val sport: SportHint,
    val name: String? = null,
    val startTime: Long,
    val endTime: Long,
    /** Timer (moving) time when the file states it; otherwise derived from the track. */
    val durationSec: Long? = null,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val points: List<TrackPoint> = emptyList(),
    val hr: List<HrSample> = emptyList(),
)

enum class FileFormat(val ext: String, val mime: String) {
    GPX("gpx", "application/gpx+xml"),
    TCX("tcx", "application/vnd.garmin.tcx+xml"),
    FIT("fit", "application/vnd.ant.fit");

    companion object {
        /** Sniffs the format from the first bytes, falling back to the file name. */
        fun detect(head: ByteArray, name: String?): FileFormat? {
            if (head.size >= 12 && String(head, 8, 4, Charsets.US_ASCII) == ".FIT") return FIT
            val text = String(head, Charsets.UTF_8).lowercase()
            if ("<gpx" in text) return GPX
            if ("<trainingcenterdatabase" in text) return TCX
            val ext = name?.substringAfterLast('.', "")?.lowercase()
            return entries.firstOrNull { it.ext == ext }
        }
    }
}

class ImportFormatException(msg: String) : Exception(msg)

/** Maps the many ways files name sports onto our families. */
object SportNames {
    fun familyOf(raw: String?): SportFamily? {
        val s = raw?.trim()?.lowercase()?.replace('_', ' ') ?: return null
        if (s.isEmpty()) return null
        return when {
            listOf("mountaineer", "alpin", "climb", "登山").any { it in s } -> SportFamily.MOUNTAINEER
            listOf("hik", "walk", "trek", "徒步", "爬山").any { it in s } -> SportFamily.HIKE
            listOf("swim", "游泳").any { it in s } -> SportFamily.SWIM
            listOf("bik", "cycl", "ride", "骑行").any { it in s } -> SportFamily.CYCLE
            listOf("run", "jog", "跑").any { it in s } -> SportFamily.RUN
            else -> null
        }
    }

    /** Value written to GPX &lt;type&gt; / TCX Sport for each family. */
    fun gpxType(f: SportFamily): String = when (f) {
        SportFamily.RUN -> "running"
        SportFamily.HIKE -> "hiking"
        SportFamily.MOUNTAINEER -> "mountaineering"
        SportFamily.SWIM -> "swimming"
        SportFamily.CYCLE -> "cycling"
        SportFamily.GENERIC -> "other"
    }

    fun tcxSport(f: SportFamily): String = when (f) {
        SportFamily.RUN -> "Running"
        SportFamily.CYCLE -> "Biking"
        else -> "Other"
    }

    // FIT sport / sub_sport enums (FIT profile).
    fun fromFit(sport: Int?, subSport: Int?): SportFamily? = when (sport) {
        1 -> SportFamily.RUN
        2 -> SportFamily.CYCLE
        5 -> SportFamily.SWIM
        11, 17 -> SportFamily.HIKE
        16 -> SportFamily.MOUNTAINEER
        else -> null
    }

    fun toFit(f: SportFamily): Int = when (f) {
        SportFamily.RUN -> 1
        SportFamily.CYCLE -> 2
        SportFamily.SWIM -> 5
        SportFamily.HIKE -> 17
        SportFamily.MOUNTAINEER -> 16
        SportFamily.GENERIC -> 0
    }
}
