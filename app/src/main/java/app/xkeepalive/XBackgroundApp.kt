package app.xkeepalive

import android.app.Application
import android.content.Context
import app.xkeepalive.data.EventLogRepository
import app.xkeepalive.data.SessionRepository
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.detect.ForegroundAppDetector
import app.xkeepalive.permissions.PermissionGateway
import app.xkeepalive.service.MonitorService
import app.xkeepalive.shizuku.BackgroundPolicyExecutor
import app.xkeepalive.shizuku.ShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XBackgroundApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Shizuku carica l'Application anche nel processo shell (uid 2000).
        // Quel processo deve solo eseguire i comandi consentiti.
        val uid = android.os.Process.myUid()
        if (uid == 2000 || uid == 0) return
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsRepository(appContext)
    val events = EventLogRepository(appContext)
    val session = SessionRepository()
    val shizuku = ShizukuBridge(appContext)
    val policy = BackgroundPolicyExecutor(shizuku, settings)
    val detector = ForegroundAppDetector(appContext)
    val permissions = PermissionGateway(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { events.load() }
    }

    fun startMonitor() = MonitorService.start(appContext)

    fun stopMonitor() = MonitorService.stop(appContext)
}
