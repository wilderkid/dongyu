package app.dongyu.recording

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Background-keepalive helpers for Chinese OEM ROMs that aggressively kill background apps. */
object OemGuide {
    enum class Vendor { XIAOMI, HUAWEI, HONOR, OPPO, VIVO, SAMSUNG, OTHER }

    fun vendor(manufacturer: String = Build.MANUFACTURER): Vendor = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> Vendor.XIAOMI
        "huawei" -> Vendor.HUAWEI
        "honor" -> Vendor.HONOR
        "oppo", "realme", "oneplus" -> Vendor.OPPO
        "vivo", "iqoo" -> Vendor.VIVO
        "samsung" -> Vendor.SAMSUNG
        else -> Vendor.OTHER
    }

    /** Candidate intents for the vendor auto-start / background manager, most specific first. */
    fun autoStartIntents(v: Vendor): List<Intent> {
        fun cmp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        return when (v) {
            Vendor.XIAOMI -> listOf(
                cmp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Vendor.HUAWEI, Vendor.HONOR -> listOf(
                cmp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                cmp("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                cmp("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            Vendor.OPPO -> listOf(
                cmp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                cmp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                cmp("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            Vendor.VIVO -> listOf(
                cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            Vendor.SAMSUNG -> listOf(
                cmp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            Vendor.OTHER -> emptyList()
        }
    }

    fun batteryOptimizationIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Launches the first intent that resolves; falls back to app details. Returns true if anything opened. */
    fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        for (i in intents + appDetailsIntent(context)) {
            val ok = runCatching {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }
}
