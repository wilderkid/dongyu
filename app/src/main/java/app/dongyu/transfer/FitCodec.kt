package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.model.SportFamily
import app.dongyu.model.TrackPoint
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Garmin FIT reader/writer: file_id, record, lap, session, activity. Enough for
 * Strava / Garmin Connect / intervals.icu to accept our files, and to read theirs.
 */
object FitCodec {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    const val FIT_EPOCH = 631065600L
    private const val SEMI = 2147483648.0 / 180.0

    fun toSemicircles(deg: Double): Int = Math.round(deg * SEMI).toInt()
    fun fromSemicircles(s: Int): Double = s / SEMI

    // ---- CRC ---------------------------------------------------------------------------------

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun crc(bytes: ByteArray, from: Int = 0, to: Int = bytes.size, seed: Int = 0): Int {
        var c = seed
        for (i in from until to) {
            val b = bytes[i].toInt() and 0xFF
            var tmp = CRC_TABLE[c and 0xF]
            c = (c shr 4) and 0x0FFF
            c = c xor tmp xor CRC_TABLE[b and 0xF]
            tmp = CRC_TABLE[c and 0xF]
            c = (c shr 4) and 0x0FFF
            c = c xor tmp xor CRC_TABLE[(b shr 4) and 0xF]
        }
        return c
    }

    // ---- reader ------------------------------------------------------------------------------

    private class FieldDef(val num: Int, val size: Int, val baseType: Int)
    private class Def(val global: Int, val bigEndian: Boolean, val fields: List<FieldDef>, val devSize: Int)

    fun parse(input: InputStream): ActivityData {
        val bytes = input.readBytes()
        if (bytes.size < 14) throw ImportFormatException("FIT too short")
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize < 12 || String(bytes, 8, 4, Charsets.US_ASCII) != ".FIT") throw ImportFormatException("Not a FIT file")
        val dataSize = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val end = (headerSize + dataSize).coerceAtMost(bytes.size)
        if (headerSize + dataSize + 2 <= bytes.size) {
            val stored = (bytes[end].toInt() and 0xFF) or ((bytes[end + 1].toInt() and 0xFF) shl 8)
            if (stored != 0 && crc(bytes, 0, end) != stored) throw ImportFormatException("FIT CRC mismatch")
        }
        val defs = HashMap<Int, Def>()
        var pos = headerSize
        var lastTs = 0L

        val points = ArrayList<TrackPoint>()
        val hr = ArrayList<HrSample>()
        var sport: Int? = null; var subSport: Int? = null
        var start: Long? = null; var timer: Double? = null; var dist: Double? = null; var kcal: Double? = null
        var firstTs: Long? = null
        var segment = 0

        while (pos < end) {
            val h = bytes[pos].toInt() and 0xFF; pos++
            val compressed = h and 0x80 != 0
            val local = if (compressed) (h shr 5) and 0x3 else h and 0xF
            if (!compressed && h and 0x40 != 0) {
                // definition message
                if (pos + 5 > end) break
                val big = bytes[pos + 1].toInt() == 1
                val bb = ByteBuffer.wrap(bytes, pos + 2, 2).order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
                val global = bb.short.toInt() and 0xFFFF
                val n = bytes[pos + 4].toInt() and 0xFF
                pos += 5
                val fs = (0 until n).map { i -> FieldDef(bytes[pos + i * 3].toInt() and 0xFF, bytes[pos + i * 3 + 1].toInt() and 0xFF, bytes[pos + i * 3 + 2].toInt() and 0xFF) }
                pos += n * 3
                var dev = 0
                if (h and 0x20 != 0) {
                    val dn = bytes[pos].toInt() and 0xFF; pos++
                    for (i in 0 until dn) dev += bytes[pos + i * 3 + 1].toInt() and 0xFF
                    pos += dn * 3
                }
                defs[local] = Def(global, big, fs, dev)
                continue
            }
            val d = defs[local] ?: throw ImportFormatException("FIT data before definition")
            val vals = HashMap<Int, Long>()
            for (f in d.fields) {
                if (pos + f.size > end) break
                readField(bytes, pos, f, d.bigEndian)?.let { vals[f.num] = it }
                pos += f.size
            }
            pos += d.devSize
            if (compressed) {
                val off = (h and 0x1F).toLong()
                lastTs = (lastTs and 0x1F.inv().toLong()) + off + (if (off < (lastTs and 0x1F)) 0x20 else 0)
                vals[253] = lastTs
            } else vals[253]?.let { lastTs = it }
            val ts = vals[253]?.let { (it + FIT_EPOCH) * 1000 }
            when (d.global) {
                20 -> { // record
                    ts ?: continue
                    if (firstTs == null) firstTs = ts
                    vals[3]?.toInt()?.takeIf { it in 25..250 }?.let { hr += HrSample(ts, it) }
                    val la = vals[0]?.toInt(); val lo = vals[1]?.toInt()
                    if (la != null && lo != null) {
                        val alt = vals[78]?.let { it / 5.0 - 500 } ?: vals[2]?.let { it / 5.0 - 500 }
                        points += TrackPoint(time = ts, lat = fromSemicircles(la), lon = fromSemicircles(lo), altitude = alt, segment = segment)
                    }
                }
                21 -> { // event: timer stop starts a new segment
                    if (vals[0] == 0L && vals[1] == 4L && points.isNotEmpty()) segment++
                }
                18 -> { // session
                    vals[5]?.let { sport = it.toInt() }
                    vals[6]?.let { subSport = it.toInt() }
                    vals[2]?.let { start = (it + FIT_EPOCH) * 1000 }
                    vals[8]?.let { timer = (timer ?: 0.0) + it / 1000.0 }
                    vals[9]?.let { dist = (dist ?: 0.0) + it / 100.0 }
                    vals[11]?.let { kcal = (kcal ?: 0.0) + it }
                }
                12 -> if (sport == null) vals[0]?.let { sport = it.toInt(); subSport = vals[1]?.toInt() } // sport mesg
            }
        }
        val startMs = start ?: firstTs ?: throw ImportFormatException("FIT has no records")
        val endMs = maxOf(points.lastOrNull()?.time ?: 0, hr.lastOrNull()?.time ?: 0, startMs + ((timer ?: 0.0) * 1000).toLong())
        // Collapse empty segments left by repeated stop events.
        val segs = points.map { it.segment }.distinct()
        val fixed = points.map { it.copy(segment = segs.indexOf(it.segment)) }
        return ActivityData(
            sport = SportHint(SportNames.fromFit(sport, subSport), null),
            startTime = startMs, endTime = endMs,
            durationSec = timer?.toLong(), distanceM = dist, calories = kcal,
            points = fixed, hr = hr,
        )
    }

    /** Returns null for FIT "invalid" sentinels. */
    private fun readField(b: ByteArray, pos: Int, f: FieldDef, big: Boolean): Long? {
        val bt = f.baseType and 0x1F
        val bb = ByteBuffer.wrap(b, pos, f.size).order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        return when {
            (bt == 0 || bt == 2 || bt == 10 || bt == 13) && f.size >= 1 -> (b[pos].toLong() and 0xFF).takeIf { it != if (bt == 10) 0L else 0xFFL }
            bt == 1 && f.size >= 1 -> b[pos].toLong().takeIf { it != 0x7FL }
            bt == 3 && f.size >= 2 -> bb.short.toLong().takeIf { it != 0x7FFFL }
            (bt == 4 || bt == 11) && f.size >= 2 -> (bb.short.toLong() and 0xFFFF).takeIf { it != if (bt == 11) 0L else 0xFFFFL }
            bt == 5 && f.size >= 4 -> bb.int.toLong().takeIf { it != 0x7FFFFFFFL }
            (bt == 6 || bt == 12) && f.size >= 4 -> (bb.int.toLong() and 0xFFFFFFFFL).takeIf { it != if (bt == 12) 0L else 0xFFFFFFFFL }
            else -> null
        }
    }

    // ---- writer ------------------------------------------------------------------------------

    private class Field(val num: Int, val size: Int, val type: Int)

    private class Writer {
        val out = ByteArrayOutputStream()
        private val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        fun def(local: Int, global: Int, fields: List<Field>) {
            out.write(0x40 or local); out.write(0); out.write(0)
            out.write(global and 0xFF); out.write(global shr 8)
            out.write(fields.size)
            fields.forEach { out.write(it.num); out.write(it.size); out.write(it.type) }
        }
        fun data(local: Int) = out.write(local)
        fun u8(v: Int?) = out.write(v ?: 0xFF)
        fun u16(v: Int?) { val x = v ?: 0xFFFF; out.write(x and 0xFF); out.write((x shr 8) and 0xFF) }
        fun u32(v: Long?) { bb.clear(); bb.putInt((v ?: 0xFFFFFFFFL).toInt()); out.write(bb.array(), 0, 4) }
        fun s32(v: Int?) { bb.clear(); bb.putInt(v ?: 0x7FFFFFFF); out.write(bb.array(), 0, 4) }
    }

    private const val ENUM = 0x00; private const val U8 = 0x02; private const val U16 = 0x84
    private const val S32 = 0x85; private const val U32 = 0x86; private const val U32Z = 0x8C

    private fun fitTs(ms: Long) = ms / 1000 - FIT_EPOCH

    fun write(a: ActivityData): ByteArray {
        val w = Writer()
        val family = a.sport.family ?: SportFamily.GENERIC
        val timerSec = a.durationSec ?: ((a.endTime - a.startTime) / 1000)
        val distance = a.distanceM ?: cumulativeDistance(a.points).lastOrNull() ?: 0.0
        // file_id
        w.def(0, 0, listOf(Field(0, 1, ENUM), Field(1, 2, U16), Field(2, 2, U16), Field(3, 4, U32Z), Field(4, 4, U32)))
        w.data(0); w.u8(4); w.u16(255); w.u16(1); w.u32(a.startTime / 1000 and 0x7FFFFFFF); w.u32(fitTs(a.startTime))
        // event: timer start
        w.def(2, 21, listOf(Field(253, 4, U32), Field(0, 1, ENUM), Field(1, 1, ENUM)))
        w.data(2); w.u32(fitTs(a.startTime)); w.u8(0); w.u8(0)
        // records
        w.def(1, 20, listOf(Field(253, 4, U32), Field(0, 4, S32), Field(1, 4, S32), Field(2, 2, U16), Field(3, 1, U8), Field(5, 4, U32)))
        val hr = HrMatcher(a.hr)
        if (a.points.isNotEmpty()) {
            val cum = cumulativeDistance(a.points)
            var lastSeg = a.points.first().segment
            a.points.forEachIndexed { i, p ->
                if (p.segment != lastSeg) {
                    w.data(2); w.u32(fitTs(a.points[i - 1].time)); w.u8(0); w.u8(4) // timer stop
                    w.data(2); w.u32(fitTs(p.time)); w.u8(0); w.u8(0) // timer start
                    lastSeg = p.segment
                }
                w.data(1); w.u32(fitTs(p.time)); w.s32(toSemicircles(p.lat)); w.s32(toSemicircles(p.lon))
                w.u16((p.baroAltitude ?: p.altitude)?.let { Math.round((it + 500) * 5).toInt().coerceIn(0, 0xFFFE) })
                w.u8(hr.at(p.time)); w.u32(Math.round(cum[i] * 100))
            }
        } else {
            a.hr.forEach { s -> w.data(1); w.u32(fitTs(s.time)); w.s32(null); w.s32(null); w.u16(null); w.u8(s.bpm); w.u32(null) }
        }
        w.data(2); w.u32(fitTs(a.endTime)); w.u8(0); w.u8(4)
        val avgHr = a.hr.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
        val maxHr = a.hr.maxOfOrNull { it.bpm }
        val elapsedMs = (a.endTime - a.startTime).coerceAtLeast(timerSec * 1000)
        // lap
        w.def(3, 19, listOf(Field(253, 4, U32), Field(2, 4, U32), Field(7, 4, U32), Field(8, 4, U32), Field(9, 4, U32), Field(25, 1, ENUM)))
        w.data(3); w.u32(fitTs(a.endTime)); w.u32(fitTs(a.startTime)); w.u32(elapsedMs); w.u32(timerSec * 1000); w.u32(Math.round(distance * 100)); w.u8(SportNames.toFit(family))
        // session
        w.def(4, 18, listOf(
            Field(253, 4, U32), Field(2, 4, U32), Field(7, 4, U32), Field(8, 4, U32), Field(9, 4, U32),
            Field(5, 1, ENUM), Field(6, 1, ENUM), Field(11, 2, U16), Field(16, 1, U8), Field(17, 1, U8), Field(26, 2, U16), Field(25, 2, U16),
        ))
        w.data(4); w.u32(fitTs(a.endTime)); w.u32(fitTs(a.startTime)); w.u32(elapsedMs); w.u32(timerSec * 1000); w.u32(Math.round(distance * 100))
        w.u8(SportNames.toFit(family)); w.u8(0); w.u16(a.calories?.toInt()); w.u8(avgHr); w.u8(maxHr); w.u16(1); w.u16(0)
        // activity
        w.def(5, 34, listOf(Field(253, 4, U32), Field(0, 4, U32), Field(1, 2, U16), Field(2, 1, ENUM), Field(3, 1, ENUM), Field(4, 1, ENUM)))
        w.data(5); w.u32(fitTs(a.endTime)); w.u32(timerSec * 1000); w.u16(1); w.u8(0); w.u8(26); w.u8(1)

        val data = w.out.toByteArray()
        val header = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(14); put(0x20); putShort(2132); putInt(data.size); put(".FIT".toByteArray(Charsets.US_ASCII))
        }.array()
        val hc = crc(header, 0, 12)
        header[12] = (hc and 0xFF).toByte(); header[13] = (hc shr 8).toByte()
        val file = header + data
        val c = crc(file)
        return file + byteArrayOf((c and 0xFF).toByte(), (c shr 8).toByte())
    }
}
