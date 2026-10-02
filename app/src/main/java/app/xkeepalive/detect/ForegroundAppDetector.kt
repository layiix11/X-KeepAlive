package app.xkeepalive.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

class ForegroundAppDetector(context: Context) {
    private val usageStats = context.applicationContext.getSystemService(UsageStatsManager::class.java)
    private var cursor = System.currentTimeMillis() - LOOKBACK_MS
    private var lastPackage: String? = null

    fun reset() {
        cursor = System.currentTimeMillis() - LOOKBACK_MS
        lastPackage = null
    }

    fun poll(): ForegroundSample {
        val now = System.currentTimeMillis()
        val start = (cursor - OVERLAP_MS).coerceAtLeast(0L)
        val events = try {
            usageStats.queryEvents(start, now)
        } catch (_: SecurityException) {
            return ForegroundSample(lastPackage, lastPackage != null)
        } ?: return ForegroundSample(lastPackage, lastPackage != null)

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!isForegroundEvent(event.eventType)) continue
            val packageName = event.packageName ?: continue
            if (packageName.isNotBlank()) lastPackage = packageName
        }
        cursor = now
        return ForegroundSample(lastPackage, lastPackage != null)
    }

    @Suppress("DEPRECATION")
    private fun isForegroundEvent(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        return Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED
    }

    private companion object {
        const val LOOKBACK_MS = 20_000L
        const val OVERLAP_MS = 500L
    }
}

data class ForegroundSample(
    val packageName: String?,
    val known: Boolean,
)
