package app.xkeepalive.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.apps.InstalledApp
import app.xkeepalive.apps.InstalledApps
import app.xkeepalive.core.PackageNames
import app.xkeepalive.data.UserSettings
import app.xkeepalive.service.MonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as XBackgroundApp).container
    private var pendingEnable = false

    val settings = container.settings.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )
    val session = container.session.state
    val events = container.events.events

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading = _appsLoading.asStateFlow()

    private val _consentVisible = MutableStateFlow(false)
    val consentVisible = _consentVisible.asStateFlow()

    private val _askNotification = MutableStateFlow(false)
    val askNotification = _askNotification.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val permissions = container.permissions.snapshot()
                val shizuku = container.shizuku.snapshot()
                container.session.update {
                    it.copy(
                        permissions = permissions,
                        shizuku = shizuku,
                        serviceRunning = MonitorService.running.get(),
                    )
                }
                delay(1_500)
            }
        }
        refreshApps()
        viewModelScope.launch {
            val current = container.settings.snapshot()
            if (current.automationEnabled || !PackageNames.isValid(current.policyPackage)) return@launch
            val report = container.policy.revert(current.policyPackage)
            val restored = report.mode == "REVERT" && report.anyVerified
            container.events.append(
                "POLICY",
                if (restored) {
                    "Leftover policy for ${report.packageName} was restored."
                } else {
                    "Leftover policy for ${report.packageName} was not restored (${report.mode})."
                },
            )
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _appsLoading.value = true
            val loaded = withContext(Dispatchers.Default) {
                InstalledApps.load(getApplication())
            }
            _apps.value = loaded
            _appsLoading.value = false
        }
    }

    fun selectApp(app: InstalledApp) {
        viewModelScope.launch {
            if (!PackageNames.isValid(app.packageName)) {
                _messages.emit("Package cannot be used.")
                return@launch
            }
            container.settings.setSelection(app.packageName, app.label)
            _messages.emit("Selected ${app.label}.")
        }
    }

    fun setAutomation(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                container.settings.setAutomation(false)
                try {
                    container.stopMonitor()
                } catch (throwable: Throwable) {
                    _messages.emit(throwable.message ?: "Service was not stopped.")
                }
                return@launch
            }
            val current = container.settings.snapshot()
            if (!PackageNames.isValid(current.selectedPackage)) {
                _messages.emit("Choose a game first.")
                return@launch
            }
            if (!current.consentAccepted) {
                _consentVisible.value = true
                return@launch
            }
            if (!container.permissions.hasUsageAccess()) {
                _messages.emit("Grant usage access to X-KeepAlive, then turn the switch on again.")
                container.permissions.openUsageAccess()
                return@launch
            }
            if (!container.permissions.hasNotifications()) {
                pendingEnable = true
                _askNotification.value = true
                return@launch
            }
            startMonitoring()
        }
    }

    fun confirmConsent() {
        viewModelScope.launch {
            _consentVisible.value = false
            container.settings.setConsentAccepted()
            setAutomation(true)
        }
    }

    fun dismissConsent() {
        _consentVisible.value = false
    }

    fun onNotificationResult(granted: Boolean) {
        _askNotification.value = false
        if (!pendingEnable) return
        pendingEnable = false
        if (!granted) {
            viewModelScope.launch {
                _messages.emit("Without the notification, Android will not keep the foreground service.")
            }
            return
        }
        viewModelScope.launch { startMonitoring() }
    }

    fun requestShizukuPermission() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (!Shizuku.pingBinder()) {
                    _messages.emit("Shizuku is not running. Open it and start it with wireless debugging.")
                    return@launch
                }
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _messages.emit("Shizuku authorization is already granted.")
                    return@launch
                }
                Shizuku.requestPermission(SHIZUKU_REQUEST)
            } catch (throwable: Throwable) {
                _messages.emit(throwable.message ?: "Shizuku request failed.")
            }
        }
    }

    fun setRestoreOnBoot(enabled: Boolean) {
        viewModelScope.launch { container.settings.setRestoreOnBoot(enabled) }
    }

    fun setPollInterval(intervalMs: Long) {
        viewModelScope.launch { container.settings.setPollInterval(intervalMs) }
    }

    fun clearLog() {
        viewModelScope.launch { container.events.clear() }
    }

    fun openUsageAccess() = container.permissions.openUsageAccess()

    fun openOwnBattery() = container.permissions.openOwnBatterySettings()

    fun openGamePower() {
        viewModelScope.launch {
            val packageName = container.settings.snapshot().selectedPackage
            if (!PackageNames.isValid(packageName)) {
                _messages.emit("Choose a game first.")
                return@launch
            }
            container.permissions.openGamePowerSettings(packageName)
        }
    }

    fun openDeveloperOptions() = container.permissions.openDeveloperOptions()

    fun openShizukuApp() {
        if (!container.permissions.openShizuku()) {
            viewModelScope.launch { _messages.emit("Shizuku is not installed.") }
        }
    }

    fun recordTest(result: TestRecord) {
        viewModelScope.launch {
            container.events.append(
                "TEST",
                listOf(
                    "Wait: ${result.waitLabel}",
                    "Foreground app: ${result.foregroundApp}",
                    "Process: ${result.processResult}",
                    "X-KeepAlive service: ${result.serviceResult}",
                    "Shizuku: ${result.shizukuResult}",
                    result.notes.take(300),
                ).filter { it.isNotBlank() }.joinToString(" | "),
            )
            _messages.emit("Result saved in the local log.")
        }
    }

    private suspend fun startMonitoring() {
        try {
            container.settings.setAutomation(true)
            container.startMonitor()
        } catch (throwable: Throwable) {
            container.settings.setAutomation(false)
            _messages.emit(throwable.message ?: "Android refused to start the service.")
        }
    }

    companion object {
        const val SHIZUKU_REQUEST = 42
    }
}

data class TestRecord(
    val waitLabel: String,
    val foregroundApp: String,
    val processResult: String,
    val serviceResult: String,
    val shizukuResult: String,
    val notes: String,
)
