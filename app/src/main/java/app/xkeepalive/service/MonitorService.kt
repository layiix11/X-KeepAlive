package app.xkeepalive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.xkeepalive.MainActivity
import app.xkeepalive.R
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.core.AutomationEngine
import app.xkeepalive.core.Blocker
import app.xkeepalive.core.EngineSnapshot
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.Phase
import app.xkeepalive.core.PhaseLabels
import app.xkeepalive.core.PolicyReport
import app.xkeepalive.core.ProcessOutcome
import app.xkeepalive.core.Sample
import app.xkeepalive.data.SessionUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = AutomationEngine()
    private val shuttingDown = AtomicBoolean(false)
    private var loopJob: Job? = null
    private var previous = EngineSnapshot.inactive()
    private var policyVerified = false
    private var shizukuWasReady = false
    private var loggedStandard = false
    private var nextPolicyAttemptAt = 0L
    private var nextVerifyAt = 0L
    private var appliedPackage = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        running.set(true)
        startInForeground("X-KeepAlive", "Starting monitoring")
        if (intent?.action == ACTION_STOP) {
            scope.launch { shutdown() }
            return START_NOT_STICKY
        }
        if (loopJob?.isActive != true && !shuttingDown.get()) {
            loopJob = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val container = container()
        container.detector.reset()
        engine.reset()
        previous = EngineSnapshot.inactive()
        running.set(true)
        container.events.append("SERVICE_STARTED", "Monitoring started. The service does not keep the game alive by itself.")
        container.session.update { it.copy(serviceRunning = true) }
        while (coroutineContext.isActive && !shuttingDown.get()) {
            try {
                tick()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                container.events.append("ERROR", throwable.message ?: throwable.javaClass.simpleName)
            }
            if (shuttingDown.get()) break
            val interval = container.settings.snapshot().pollIntervalMs
            val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
            delay(if (interactive) interval else maxOf(interval, SCREEN_OFF_INTERVAL_MS))
        }
    }

    private suspend fun tick() {
        if (shuttingDown.get()) return
        val container = container()
        val settings = container.settings.snapshot()
        if (!settings.automationEnabled) {
            shutdown()
            return
        }
        val permissions = container.permissions.snapshot()
        val shizuku = container.shizuku.snapshot()
        val readyBefore = shizukuWasReady
        val selected = settings.selectedPackage
        if (readyBefore && !shizuku.ready) {
            container.events.append("SHIZUKU", "Shizuku connection lost. Continuing in standard mode, with no new commands.")
            policyVerified = false
            appliedPackage = ""
        }
        shizukuWasReady = shizuku.ready

        if (shizuku.ready && PackageNames.isValid(selected)) {
            if (settings.policyPackage.isNotBlank() && settings.policyPackage != selected) {
                val report = container.policy.revert(settings.policyPackage)
                container.events.append("POLICY", describe(report))
                policyVerified = false
                appliedPackage = ""
            }
            val now = System.currentTimeMillis()
            val refreshed = container.settings.snapshot()
            if (!policyVerified || appliedPackage != selected) {
                if (now >= nextPolicyAttemptAt) {
                    val report = container.policy.apply(selected)
                    policyVerified = report.anyVerified
                    appliedPackage = if (report.anyVerified) selected else ""
                    nextPolicyAttemptAt = now + POLICY_RETRY_MS
                    nextVerifyAt = now + VERIFY_MS
                    container.session.update { it.copy(policy = report) }
                    container.events.append("POLICY", describe(report))
                }
            } else if (now >= nextVerifyAt) {
                val still = container.policy.verify(selected, refreshed.standbyChanged)
                if (still != policyVerified) {
                    container.events.append(
                        "POLICY",
                        if (still) "Policy still verified for $selected." else "Policy is no longer verified for $selected.",
                    )
                }
                policyVerified = still
                nextVerifyAt = now + VERIFY_MS
            }
        } else if (!loggedStandard) {
            container.events.append("SHIZUKU", "Standard mode: ${shizuku.message}")
            loggedStandard = true
            policyVerified = false
        }
        if (shizuku.ready) loggedStandard = false
        if (shuttingDown.get()) return

        val foreground = if (permissions.usageAccess) container.detector.poll() else null
        val pids = if (shizuku.ready && PackageNames.isValid(selected)) container.policy.readPids(selected) else null
        val snapshot = engine.onSample(
            Sample(
                automationEnabled = true,
                permissionsReady = permissions.monitoringReady,
                selectedPackage = selected,
                foregroundPackage = foreground?.packageName,
                foregroundKnown = foreground?.known == true,
                policyVerified = policyVerified,
                selectedPids = pids,
                pidsChecked = pids != null,
            ),
        )
        container.session.update {
            it.copy(
                phase = snapshot.phase,
                blocker = snapshot.blocker,
                outcome = snapshot.outcome,
                limitNote = snapshot.limitNote,
                foregroundPackage = snapshot.foregroundPackage,
                policyVerified = snapshot.policyVerified,
                backgroundPids = snapshot.backgroundPids,
                observedPids = snapshot.observedPids,
                pidChange = snapshot.pidChange,
                shizuku = shizuku,
                permissions = permissions,
                serviceRunning = true,
            )
        }
        logTransitions(snapshot)
        if (snapshot.phase != previous.phase || snapshot.blocker != previous.blocker || shizuku.ready != readyBefore) {
            val title = headline(snapshot)
            val body = buildString {
                append(settings.selectedLabel.ifBlank { selected.ifBlank { "No game" } })
                if (!shizuku.ready) append(" · ADB/Shizuku unavailable")
            }
            withContext(Dispatchers.Main) { startInForeground(title, body) }
        }
        previous = snapshot
    }

    private suspend fun logTransitions(snapshot: EngineSnapshot) {
        val events = container().events
        if (snapshot.phase != previous.phase || snapshot.blocker != previous.blocker) {
            events.append("PHASE", headline(snapshot))
        }
        if (snapshot.outcome != previous.outcome && snapshot.outcome != ProcessOutcome.NOT_CHECKED) {
            events.append("PROCESS", "${snapshot.outcome} pid=${snapshot.observedPids.sorted().joinToString(",")}")
        }
        if (
            snapshot.observedPids != previous.observedPids &&
            (snapshot.observedPids.isNotEmpty() || previous.observedPids.isNotEmpty())
        ) {
            events.append(
                "PID",
                snapshot.pidChange
                    ?: "Current PID: ${if (snapshot.observedPids.isEmpty()) "none" else snapshot.observedPids.sorted().joinToString(", ")}",
            )
        }
        val note = snapshot.limitNote
        if (note != null && note != previous.limitNote) {
            events.append("LIMIT", note)
        }
    }

    private suspend fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return
        val container = container()
        val settings = container.settings.snapshot()
        container.settings.setAutomation(false)
        if (settings.policyPackage.isNotBlank()) {
            val report = container.policy.revert(settings.policyPackage)
            container.events.append("POLICY", describe(report))
        }
        container.shizuku.unbind()
        container.session.update {
            SessionUi(
                shizuku = container.shizuku.snapshot(),
                permissions = container.permissions.snapshot(),
                serviceRunning = false,
            )
        }
        container.events.append("SERVICE_STOPPED", "Monitoring stopped.")
        running.set(false)
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun container() = (application as XBackgroundApp).container

    private fun headline(snapshot: EngineSnapshot): String = when (snapshot.blocker) {
        Blocker.PERMISSIONS -> "Permissions missing"
        Blocker.NO_GAME_SELECTED -> "No game selected"
        Blocker.NONE -> PhaseLabels.english(snapshot.phase)
    }

    private fun describe(report: PolicyReport): String {
        val header = "${report.mode} verified=${report.anyVerified}"
        val lines = report.operations.joinToString(" · ") { op ->
            "${op.name}:${if (op.verified) "ok" else "no"} ${op.detail}"
        }
        return "$header $lines".take(900)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(title: String, text: String) {
        val notification = notification(title, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_x)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "xkeepalive_monitor"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "app.xkeepalive.STOP"
        private const val POLICY_RETRY_MS = 30_000L
        private const val VERIFY_MS = 60_000L
        private const val SCREEN_OFF_INTERVAL_MS = 8_000L

        val running = AtomicBoolean(false)

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
