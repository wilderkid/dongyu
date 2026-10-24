package app.dongyu.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.dongyu.DongyuApp
import app.dongyu.MainActivity
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type=location) that keeps GPS running while the screen is off.
 * Started only from the foreground UI, which satisfies Android 14+ FGS start restrictions.
 */
class RecordingService : LifecycleService(), LocationListener {

    private val manager: RecordingManager get() = (application as DongyuApp).container.recording
    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TOGGLE_PAUSE) {
            val s = manager.state.value
            if (s.status == RecStatus.PAUSED) manager.resume() else manager.pause()
            return START_STICKY
        }
        if (!manager.hasLocationPermission()) {
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground()
        if (intent == null) {
            // Restarted by the system after the process was killed: re-attach to the unfinished workout.
            val c = (application as DongyuApp).container
            lifecycleScope.launch { c.workouts.unfinished()?.let { manager.recover(it) } }
        }
        startLocation()
        observeForNotification()
        return START_STICKY
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(manager.state.value), type)
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dongyu:recording").apply {
                setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (locationManager != null) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = lm
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()) }
    }

    private fun observeForNotification() {
        lifecycleScope.launch {
            manager.state
                .map { Triple(it.status, it.movingMs / 5000, (it.distanceM / 10).toInt()) }
                .distinctUntilChanged()
                .collect {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(manager.state.value))
                }
        }
    }

    override fun onLocationChanged(location: Location) {
        manager.onLocation(
            time = location.time,
            lat = location.latitude,
            lon = location.longitude,
            alt = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun buildNotification(s: LiveState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (s.status) {
            RecStatus.PAUSED -> getString(R.string.rec_paused)
            RecStatus.AUTO_PAUSED -> getString(R.string.rec_auto_paused)
            else -> getString(R.string.rec_notification_title)
        }
        val text = getString(
            R.string.rec_notification_text,
            Pace.formatDuration(s.movingMs / 1000),
            "%.2f".format(s.distanceM / 1000),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dongyu)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)
            .addAction(
                0,
                getString(if (s.status == RecStatus.PAUSED) R.string.action_resume else R.string.action_pause),
                toggle,
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopSelfCleanly() {
        locationManager?.removeUpdates(this)
        locationManager = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "app.dongyu.STOP"
        const val ACTION_TOGGLE_PAUSE = "app.dongyu.TOGGLE_PAUSE"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW)
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
