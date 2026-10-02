package app.xkeepalive.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val ownBatteryUnrestricted: Boolean = false,
) {
    val monitoringReady: Boolean
        get() = usageAccess && (notifications || Build.VERSION.SDK_INT < 33)
}

class PermissionGateway(private val context: Context) {
    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        usageAccess = hasUsageAccess(),
        notifications = hasNotifications(),
        ownBatteryUnrestricted = isIgnoringBatteryOptimizations(),
    )

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openUsageAccess() {
        val targeted = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!start(targeted)) {
            start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openOwnBatterySettings() {
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (start(request)) return
        }
        openAppDetails(context.packageName)
    }

    fun openGamePowerSettings(packageName: String) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val advanced = Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            putExtra("android.provider.extra.APP_PACKAGE", packageName)
            putExtra("app_package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!start(advanced)) start(details)
    }

    fun openAppDetails(packageName: String) {
        start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun openDeveloperOptions() {
        start(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openShizuku(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(launch)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
