package app.dongyu.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Pressure-sensor altimeter. Barometric altitude is precise relative to itself but its absolute
 * level drifts with weather, so it is calibrated against the first accurate GPS altitudes.
 */
class Barometer(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
    val available: Boolean get() = sensor != null

    @Volatile private var rawAltitude: Double? = null
    private var offset: Double? = null
    private val calibration = mutableListOf<Double>()

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val hPa = event.values.firstOrNull() ?: return
        // light exponential smoothing
        val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa).toDouble()
        rawAltitude = rawAltitude?.let { it * 0.8 + alt * 0.2 } ?: alt
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Feed GPS altitude from good fixes; after a few samples the offset is fixed. */
    fun calibrate(gpsAltitude: Double, accuracy: Float) {
        val raw = rawAltitude ?: return
        if (offset != null || accuracy > 15f) return
        calibration += gpsAltitude - raw
        if (calibration.size >= 5) offset = calibration.sorted()[calibration.size / 2]
    }

    /** Calibrated altitude, or raw pressure altitude if not yet calibrated. */
    fun altitude(): Double? = rawAltitude?.let { it + (offset ?: 0.0) }
}
