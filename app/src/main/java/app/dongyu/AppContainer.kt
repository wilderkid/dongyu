package app.dongyu

import android.content.Context
import app.dongyu.data.db.AppDatabase
import app.dongyu.data.prefs.SettingsStore
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import app.dongyu.data.seed.Seeder
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.recording.RecordingManager
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
    val recording = RecordingManager(context.applicationContext, sports, workouts, settings, appScope)
}
