package app.dongyu.domain.hr

/**
 * Decoded Heart Rate Measurement characteristic (0x2A37) of the standard Heart Rate Service (0x180D).
 * [rrMs] are RR intervals converted from 1/1024 s to milliseconds.
 */
data class HrMeasurement(
    val bpm: Int,
    /** null = sensor does not support contact detection. */
    val contact: Boolean?,
    val energyKj: Int?,
    val rrMs: List<Int>,
)

object HeartRateParser {
    /** Returns null for malformed payloads. All multi-byte fields are little-endian. */
    fun parse(data: ByteArray): HrMeasurement? {
        if (data.size < 2) return null
        val flags = data[0].toInt() and 0xFF
        val hr16 = flags and 0x01 != 0
        val contactSupported = flags and 0x04 != 0
        val contactDetected = flags and 0x02 != 0
        val energyPresent = flags and 0x08 != 0
        val rrPresent = flags and 0x10 != 0
        var i = 1
        val bpm: Int
        if (hr16) {
            if (data.size < 3) return null
            bpm = u16(data, i); i += 2
        } else {
            bpm = data[i].toInt() and 0xFF; i += 1
        }
        var energy: Int? = null
        if (energyPresent) {
            if (data.size < i + 2) return null
            energy = u16(data, i); i += 2
        }
        val rr = mutableListOf<Int>()
        if (rrPresent) {
            while (i + 1 < data.size) {
                rr += Math.round(u16(data, i) * 1000.0 / 1024.0).toInt()
                i += 2
            }
        }
        return HrMeasurement(bpm, if (contactSupported) contactDetected else null, energy, rr)
    }

    private fun u16(d: ByteArray, i: Int) = (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8)
}
