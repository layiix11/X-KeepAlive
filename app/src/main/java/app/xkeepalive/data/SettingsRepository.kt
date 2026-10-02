package app.xkeepalive.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "xkeepalive_settings")

data class UserSettings(
    val automationEnabled: Boolean = false,
    val restoreOnBoot: Boolean = true,
    val selectedPackage: String = "",
    val selectedLabel: String = "",
    val pollIntervalMs: Long = 2_000L,
    val adsEnabled: Boolean = false,
    val consentAccepted: Boolean = false,
    val baselinePackage: String = "",
    val savedStandby: String = "",
    val savedInactive: String = "",
    val standbyChanged: Boolean = false,
    val policyPackage: String = "",
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val flow: Flow<UserSettings> = store.data.map { it.toSettings() }

    suspend fun snapshot(): UserSettings = store.data.first().toSettings()

    suspend fun setAutomation(enabled: Boolean) = edit { it[Keys.automation] = enabled }

    suspend fun setRestoreOnBoot(enabled: Boolean) = edit { it[Keys.restoreOnBoot] = enabled }

    suspend fun setSelection(packageName: String, label: String) = edit {
        it[Keys.packageName] = packageName
        it[Keys.label] = label
    }

    suspend fun setPollInterval(intervalMs: Long) = edit {
        it[Keys.poll] = intervalMs.coerceIn(1_500L, 5_000L)
    }

    suspend fun setAdsEnabled(enabled: Boolean) = edit { it[Keys.ads] = enabled }

    suspend fun setConsentAccepted() = edit { it[Keys.consent] = true }

    suspend fun saveBaseline(packageName: String, standby: String, inactive: String) = edit {
        it[Keys.baselinePackage] = packageName
        it[Keys.savedStandby] = standby
        it[Keys.savedInactive] = inactive
        it[Keys.standbyChanged] = false
    }

    suspend fun setStandbyChanged(changed: Boolean) = edit { it[Keys.standbyChanged] = changed }

    suspend fun setPolicyPackage(packageName: String) = edit { it[Keys.policyPackage] = packageName }

    suspend fun clearPolicy() = edit {
        it.remove(Keys.baselinePackage)
        it.remove(Keys.savedStandby)
        it.remove(Keys.savedInactive)
        it.remove(Keys.standbyChanged)
        it.remove(Keys.policyPackage)
    }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        automationEnabled = this[Keys.automation] ?: false,
        restoreOnBoot = this[Keys.restoreOnBoot] ?: true,
        selectedPackage = this[Keys.packageName].orEmpty(),
        selectedLabel = this[Keys.label].orEmpty(),
        pollIntervalMs = (this[Keys.poll] ?: 2_000L).coerceIn(1_500L, 5_000L),
        adsEnabled = this[Keys.ads] ?: false,
        consentAccepted = this[Keys.consent] ?: false,
        baselinePackage = this[Keys.baselinePackage].orEmpty(),
        savedStandby = this[Keys.savedStandby].orEmpty(),
        savedInactive = this[Keys.savedInactive].orEmpty(),
        standbyChanged = this[Keys.standbyChanged] ?: false,
        policyPackage = this[Keys.policyPackage].orEmpty(),
    )

    private object Keys {
        val automation = booleanPreferencesKey("automation_enabled")
        val restoreOnBoot = booleanPreferencesKey("restore_on_boot")
        val packageName = stringPreferencesKey("selected_package")
        val label = stringPreferencesKey("selected_label")
        val poll = longPreferencesKey("poll_interval_ms")
        val ads = booleanPreferencesKey("ads_enabled")
        val consent = booleanPreferencesKey("consent_accepted")
        val baselinePackage = stringPreferencesKey("baseline_package")
        val savedStandby = stringPreferencesKey("saved_standby")
        val savedInactive = stringPreferencesKey("saved_inactive")
        val standbyChanged = booleanPreferencesKey("standby_changed")
        val policyPackage = stringPreferencesKey("policy_package")
    }
}
