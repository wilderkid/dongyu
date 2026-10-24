package app.dongyu.domain

import app.dongyu.domain.hr.HeartRateParser
import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrZones
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun uint8Value() {
        val m = HeartRateParser.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertNull(m.contact)
        assertNull(m.energyKj)
        assertEquals(emptyList<Int>(), m.rrMs)
    }

    @Test fun uint8HighValueIsUnsigned() {
        assertEquals(200, HeartRateParser.parse(bytes(0x00, 200))!!.bpm)
    }

    @Test fun uint16Value() {
        // 0x0134 = 308 -> unrealistic but checks little-endian decoding
        assertEquals(308, HeartRateParser.parse(bytes(0x01, 0x34, 0x01))!!.bpm)
        assertEquals(150, HeartRateParser.parse(bytes(0x01, 150, 0x00))!!.bpm)
    }

    @Test fun contactBits() {
        assertEquals(true, HeartRateParser.parse(bytes(0x06, 80))!!.contact)
        assertEquals(false, HeartRateParser.parse(bytes(0x04, 80))!!.contact)
        assertNull(HeartRateParser.parse(bytes(0x02, 80))!!.contact) // detected bit without supported bit
    }

    @Test fun energyAndRr() {
        // flags: energy (0x08) + RR (0x10); hr=65; energy=0x0102=258; RR 1024 -> 1000 ms, 512 -> 500 ms
        val m = HeartRateParser.parse(bytes(0x18, 65, 0x02, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(65, m.bpm)
        assertEquals(258, m.energyKj)
        assertEquals(listOf(1000, 500), m.rrMs)
    }

    @Test fun rrWithUint16Hr() {
        // Polar H10 style: flags 0x10|0x01, hr16=60, one RR = 0x03E8 (1000/1024 s ≈ 977 ms)
        val m = HeartRateParser.parse(bytes(0x11, 60, 0x00, 0xE8, 0x03))!!
        assertEquals(60, m.bpm)
        assertEquals(listOf(977), m.rrMs)
    }

    @Test fun oddTrailingByteIgnored() {
        assertEquals(listOf(1000), HeartRateParser.parse(bytes(0x10, 70, 0x00, 0x04, 0x07))!!.rrMs)
    }

    @Test fun malformed() {
        assertNull(HeartRateParser.parse(bytes()))
        assertNull(HeartRateParser.parse(bytes(0x00)))
        assertNull(HeartRateParser.parse(bytes(0x01, 0x50)))
        assertNull(HeartRateParser.parse(bytes(0x08, 70, 0x01)))
    }
}

class HrZonesTest {
    @Test fun maxFromAgeAndCustom() {
        assertEquals(190, HrZones.maxHrForAge(30))
        assertEquals(185, HrZones.effectiveMax(185, 30))
        assertEquals(190, HrZones.effectiveMax(0, 30))
    }

    @Test fun zoneBoundaries() {
        assertEquals(0, HrZones.zoneOf(80, 200))   // 40% -> counted in Z1
        assertEquals(0, HrZones.zoneOf(119, 200))
        assertEquals(1, HrZones.zoneOf(120, 200))
        assertEquals(2, HrZones.zoneOf(140, 200))
        assertEquals(3, HrZones.zoneOf(160, 200))
        assertEquals(4, HrZones.zoneOf(180, 200))
        assertEquals(4, HrZones.zoneOf(210, 200))
    }

    @Test fun boundsCoverRanges() {
        val b = HrZones.bounds(200)
        assertEquals(100..119, b[0]); assertEquals(180..200, b[4])
    }

    @Test fun summaryIsTimeWeighted() {
        // 60 s at 100 bpm (Z1 for max 200), then 60 s at 170 (Z4), 1 s samples
        val s = (0 until 60).map { HrSample(it * 1000L, 100) } + (60 until 120).map { HrSample(it * 1000L, 170) }
        val sum = HrZones.summarize(s, 200)!!
        assertEquals(135, sum.avg)
        assertEquals(170, sum.max)
        assertEquals(100, sum.min)
        assertArrayEquals(longArrayOf(60, 0, 0, 60, 0), sum.zoneSec)
    }

    @Test fun gapsDoNotInflateTime() {
        val s = listOf(HrSample(0, 150), HrSample(1000, 150), HrSample(600_000, 150))
        assertEquals(3L, HrZones.summarize(s, 200)!!.totalZoneSec)
    }

    @Test fun valuesRoundTrip() {
        val sum = HrZones.summarize(listOf(HrSample(0, 150), HrSample(5000, 150)), 200)!!
        val v = HrZones.toValues(sum)
        assertEquals(150.0, v["hr_avg"]!!, 0.0)
        assertArrayEquals(sum.zoneSec, HrZones.fromValues(v))
        assertNull(HrZones.fromValues(emptyMap()))
        assertNull(HrZones.summarize(listOf(HrSample(0, 0)), 200))
    }
}
