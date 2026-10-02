package app.xkeepalive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.data.EventLogRepository
import app.xkeepalive.permissions.PermissionGateway
import app.xkeepalive.service.MonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (appContext as? XBackgroundApp)?.container
                val settings = container?.settings ?: SettingsRepository(appContext)
                val events = container?.events ?: EventLogRepository(appContext)
                val permissions = container?.permissions ?: PermissionGateway(appContext)
                if (container == null) events.load()
                val snapshot = settings.snapshot()
                if (!snapshot.automationEnabled || !snapshot.restoreOnBoot || snapshot.selectedPackage.isBlank()) {
                    events.append(
                        "BOOT",
                        "No automatic restore. Shizuku and wireless debugging do not restart on their own.",
                    )
                    return@launch
                }
                if (!permissions.hasUsageAccess() || !permissions.hasNotifications()) {
                    events.append(
                        "BOOT",
                        "Automation is saved, but permissions are missing. Open X-KeepAlive and turn it on again.",
                    )
                    return@launch
                }
                MonitorService.start(appContext)
                events.append(
                    "BOOT",
                    "Monitoring restarted for ${snapshot.selectedLabel.ifBlank { snapshot.selectedPackage }}. Start Shizuku by hand if the phone was rebooted.",
                )
            } catch (throwable: Throwable) {
                // Boot must not crash the system process that delivers the broadcast.
                throwable.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
