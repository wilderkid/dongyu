package app.dongyu

import android.content.Context
import app.dongyu.data.db.AppDatabase
import app.dongyu.data.prefs.SettingsStore
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import app.dongyu.data.seed.Seeder
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.recording.RecordingManager
import app.dongyu.recording.ble.HeartRateMonitor
import app.dongyu.transfer.TransferService
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency injection container (kept small instead of Hilt for build speed). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsStore(context)
    val sports = SportRepository(db)
    val workouts = WorkoutRepository(db)
    val stats = StatsAggregator()
    val seeder = Seeder(sports, workouts)
    val heartRate = HeartRateMonitor(context.applicationContext, appScope)
    val recording = RecordingManager(context.applicationContext, sports, workouts, settings, appScope, heartRate)
    val transfer = TransferService(context.applicationContext, sports, workouts, settings)
    /** Files handed to us via "Open with" / share; consumed by the transfer screen. */
    val pendingImports = MutableStateFlow<List<Uri>>(emptyList())
    /** Bumped for every incoming file intent so the UI can bring the import screen forward. */
    val incomingFiles = MutableStateFlow(0)
}
