package app.dongyu

import android.app.Application
import app.dongyu.recording.RecordingService
import kotlinx.coroutines.launch

class DongyuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecordingService.createChannel(this)
        container.appScope.launch { container.sports.ensurePresets() }
    }
}
