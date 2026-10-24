package app.dongyu.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.recording.OemGuide
import app.dongyu.ui.components.DCard
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.extraColors

enum class GuideAction { BATTERY, AUTOSTART, APP_DETAILS }

fun vendorName(v: OemGuide.Vendor): Int = when (v) {
    OemGuide.Vendor.XIAOMI -> R.string.vendor_xiaomi
    OemGuide.Vendor.HUAWEI -> R.string.vendor_huawei
    OemGuide.Vendor.HONOR -> R.string.vendor_honor
    OemGuide.Vendor.OPPO -> R.string.vendor_oppo
    OemGuide.Vendor.VIVO -> R.string.vendor_vivo
    OemGuide.Vendor.SAMSUNG -> R.string.vendor_samsung
    OemGuide.Vendor.OTHER -> R.string.vendor_other
}

fun vendorTip(v: OemGuide.Vendor): Int = when (v) {
    OemGuide.Vendor.XIAOMI -> R.string.tip_xiaomi
    OemGuide.Vendor.HUAWEI, OemGuide.Vendor.HONOR -> R.string.tip_huawei
    OemGuide.Vendor.OPPO -> R.string.tip_oppo
    OemGuide.Vendor.VIVO -> R.string.tip_vivo
    OemGuide.Vendor.SAMSUNG -> R.string.tip_samsung
    OemGuide.Vendor.OTHER -> R.string.tip_other
}

@Composable
fun OemGuideScreen(vendor: OemGuide.Vendor, onAction: (GuideAction) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(extraColors.heroStart, extraColors.heroEnd)))
                    .statusBarsPadding().padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.oem_guide_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.oem_guide_intro, stringResource(vendorName(vendor))), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Step(1, Icons.Filled.BatteryChargingFull, Brand.Up, stringResource(R.string.oem_step_battery), stringResource(R.string.oem_step_battery_desc), stringResource(R.string.go_settings)) { onAction(GuideAction.BATTERY) }
                Step(2, Icons.Filled.RocketLaunch, Brand.Coral, stringResource(R.string.oem_step_autostart), stringResource(R.string.oem_step_autostart_desc), stringResource(R.string.go_settings)) { onAction(GuideAction.AUTOSTART) }
                Step(3, Icons.Filled.NotificationsActive, Brand.Ocean, stringResource(R.string.oem_step_notify), stringResource(R.string.oem_step_notify_desc), stringResource(R.string.go_settings)) { onAction(GuideAction.APP_DETAILS) }
                Step(4, Icons.Filled.Lock, Brand.Sun, stringResource(R.string.oem_step_lock), stringResource(vendorTip(vendor)), null) {}
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp)) {
            Text(stringResource(R.string.oem_done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Step(n: Int, icon: ImageVector, color: Color, title: String, desc: String, action: String?, onClick: () -> Unit) {
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$n. $title", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                if (action != null) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onClick) { Text(action) }
                }
            }
        }
    }
}
