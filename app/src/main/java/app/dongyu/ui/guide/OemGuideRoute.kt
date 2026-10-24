package app.dongyu.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.dongyu.AppContainer
import app.dongyu.recording.OemGuide
import kotlinx.coroutines.launch

@Composable
fun OemGuideRoute(container: AppContainer, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vendor = OemGuide.vendor()
    OemGuideScreen(
        vendor,
        onAction = { a ->
            when (a) {
                GuideAction.BATTERY -> OemGuide.launchFirst(ctx, listOf(OemGuide.batteryOptimizationIntent()))
                GuideAction.AUTOSTART -> OemGuide.launchFirst(ctx, OemGuide.autoStartIntents(vendor))
                GuideAction.APP_DETAILS -> OemGuide.launchFirst(ctx, emptyList())
            }
        },
        onDone = { scope.launch { container.settings.setOemGuideSeen(); onDone() } },
    )
}
