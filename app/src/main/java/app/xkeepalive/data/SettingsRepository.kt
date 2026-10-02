package app.xkeepalive.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.xkeepalive.core.PolicyPlan
import app.xkeepalive.core.StoredPolicy
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
    val consentAccepted: Boolean = false,
    val baselinePackage: String = "",
    val savedStandby: String = "",
    val savedInactive: String = "",
    val standbyChanged: Boolean = false,
    val policyPackage: String = "",
    val whitelistAlreadyPresent: Boolean = false,
    val whitelistAddedByUs: Boolean = false,
    val inactiveChanged: Boolean = false,
    val savedAppOps: String = "",
    val changedAppOps: String = "",
    val policyGeneration: Int = 0,
)

fun UserSettings.toStoredPolicy(): StoredPolicy = StoredPolicy(
    packageName = baselinePackage.ifBlank { policyPackage },
    generation = policyGeneration,
    whitelistAlreadyPresent = whitelistAlreadyPresent,
    whitelistAddedByUs = whitelistAddedByUs,
    savedStandby = savedStandby,
    standbyChanged = standbyChanged,
    savedInactive = savedInactive,
    inactiveChanged = inactiveChanged,
    savedAppOps = PolicyPlan.decodeAppOps(savedAppOps),
    changedAppOps = PolicyPlan.decodeChangedOps(changedAppOps),
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

    suspend fun setConsentAccepted() = edit { it[Keys.consent] = true }

    suspend fun saveStoredPolicy(policy: StoredPolicy) = edit {
        it[Keys.baselinePackage] = policy.packageName
        it[Keys.savedStandby] = policy.savedStandby
        it[Keys.savedInactive] = policy.savedInactive
        it[Keys.standbyChanged] = policy.standbyChanged
        it[Keys.policyPackage] = policy.packageName
        it[Keys.whitelistAlreadyPresent] = policy.whitelistAlreadyPresent
        it[Keys.whitelistAddedByUs] = policy.whitelistAddedByUs
        it[Keys.inactiveChanged] = policy.inactiveChanged
        it[Keys.savedAppOps] = PolicyPlan.encodeAppOps(policy.savedAppOps)
        it[Keys.changedAppOps] = PolicyPlan.encodeChangedOps(policy.changedAppOps)
        it[Keys.policyGeneration] = policy.generation
    }

    suspend fun clearPolicy() = edit {
        it.remove(Keys.baselinePackage)
        it.remove(Keys.savedStandby)
        it.remove(Keys.savedInactive)
        it.remove(Keys.standbyChanged)
        it.remove(Keys.policyPackage)
        it.remove(Keys.whitelistAlreadyPresent)
        it.remove(Keys.whitelistAddedByUs)
        it.remove(Keys.inactiveChanged)
        it.remove(Keys.savedAppOps)
        it.remove(Keys.changedAppOps)
        it.remove(Keys.policyGeneration)
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
        consentAccepted = this[Keys.consent] ?: false,
        baselinePackage = this[Keys.baselinePackage].orEmpty(),
        savedStandby = this[Keys.savedStandby].orEmpty(),
        savedInactive = this[Keys.savedInactive].orEmpty(),
        standbyChanged = this[Keys.standbyChanged] ?: false,
        policyPackage = this[Keys.policyPackage].orEmpty(),
        whitelistAlreadyPresent = this[Keys.whitelistAlreadyPresent] ?: false,
        whitelistAddedByUs = this[Keys.whitelistAddedByUs] ?: false,
        inactiveChanged = this[Keys.inactiveChanged] ?: false,
        savedAppOps = this[Keys.savedAppOps].orEmpty(),
        changedAppOps = this[Keys.changedAppOps].orEmpty(),
        policyGeneration = this[Keys.policyGeneration] ?: 0,
    )

    private object Keys {
        val automation = booleanPreferencesKey("automation_enabled")
        val restoreOnBoot = booleanPreferencesKey("restore_on_boot")
        val packageName = stringPreferencesKey("selected_package")
        val label = stringPreferencesKey("selected_label")
        val poll = longPreferencesKey("poll_interval_ms")
        val consent = booleanPreferencesKey("consent_accepted")
        val baselinePackage = stringPreferencesKey("baseline_package")
        val savedStandby = stringPreferencesKey("saved_standby")
        val savedInactive = stringPreferencesKey("saved_inactive")
        val standbyChanged = booleanPreferencesKey("standby_changed")
        val policyPackage = stringPreferencesKey("policy_package")
        val whitelistAlreadyPresent = booleanPreferencesKey("whitelist_already_present")
        val whitelistAddedByUs = booleanPreferencesKey("whitelist_added_by_us")
        val inactiveChanged = booleanPreferencesKey("inactive_changed")
        val savedAppOps = stringPreferencesKey("saved_app_ops")
        val changedAppOps = stringPreferencesKey("changed_app_ops")
        val policyGeneration = intPreferencesKey("policy_generation")
    }
}
