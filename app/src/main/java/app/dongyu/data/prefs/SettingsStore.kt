package app.dongyu.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** 0 = follow system, 1 = light, 2 = dark */
data class Settings(
    val oemGuideSeen: Boolean = false,
    val autoPause: Boolean = true,
    val themeMode: Int = 0,
    val weightKg: Double = 65.0,
    val keepScreenOn: Boolean = true,
    /** Remembered BLE heart-rate strap (MAC address), null = none. */
    val hrDeviceAddress: String? = null,
    val hrDeviceName: String? = null,
    /** 0 = derive from age. */
    val maxHr: Int = 0,
    val age: Int = 30,
) {
    val effectiveMaxHr: Int get() = app.dongyu.domain.hr.HrZones.effectiveMax(maxHr, age)
}

class SettingsStore(private val context: Context) {
    private object K {
        val oemGuideSeen = booleanPreferencesKey("oem_guide_seen")
        val autoPause = booleanPreferencesKey("auto_pause")
        val themeMode = intPreferencesKey("theme_mode")
        val weightKg = doublePreferencesKey("weight_kg")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val hrAddress = stringPreferencesKey("hr_address")
        val hrName = stringPreferencesKey("hr_name")
        val maxHr = intPreferencesKey("max_hr")
        val age = intPreferencesKey("age")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            oemGuideSeen = p[K.oemGuideSeen] ?: false,
            autoPause = p[K.autoPause] ?: true,
            themeMode = p[K.themeMode] ?: 0,
            weightKg = p[K.weightKg] ?: 65.0,
            keepScreenOn = p[K.keepScreenOn] ?: true,
            hrDeviceAddress = p[K.hrAddress],
            hrDeviceName = p[K.hrName],
            maxHr = p[K.maxHr] ?: 0,
            age = p[K.age] ?: 30,
        )
    }

    suspend fun setOemGuideSeen() = context.dataStore.edit { it[K.oemGuideSeen] = true }
    suspend fun setAutoPause(v: Boolean) = context.dataStore.edit { it[K.autoPause] = v }
    suspend fun setThemeMode(v: Int) = context.dataStore.edit { it[K.themeMode] = v }
    suspend fun setWeightKg(v: Double) = context.dataStore.edit { it[K.weightKg] = v }
    suspend fun setHrDevice(address: String?, name: String?) = context.dataStore.edit {
        if (address == null) { it.remove(K.hrAddress); it.remove(K.hrName) } else { it[K.hrAddress] = address; it[K.hrName] = name ?: address }
    }
    suspend fun setMaxHr(v: Int) = context.dataStore.edit { it[K.maxHr] = v }
    suspend fun setAge(v: Int) = context.dataStore.edit { it[K.age] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.keepScreenOn] = v }
}
