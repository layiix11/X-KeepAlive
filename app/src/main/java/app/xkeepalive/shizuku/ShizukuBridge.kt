package app.xkeepalive.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.xkeepalive.BuildConfig
import app.xkeepalive.IShellService
import app.xkeepalive.core.PolicyCommands
import app.xkeepalive.core.PolicyOutput
import app.xkeepalive.core.ShellResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

data class ShizukuSnapshot(
    val managerInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val apiVersion: Int? = null,
    val message: String = "Shizuku not checked",
) {
    val ready: Boolean get() = managerInstalled && binderAlive && permissionGranted
}

class ShizukuBridge(private val context: Context) {
    private val mutex = Mutex()
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellUserService::class.java.name),
    ).daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(SERVICE_VERSION)
        .tag("xkeepalive-shell")

    @Volatile
    private var service: IShellService? = null
    private var waiter: CompletableDeferred<IShellService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.isBinderAlive) {
                waiter?.completeExceptionally(IllegalStateException("Shell service binder missing"))
                waiter = null
                return
            }
            val stub = IShellService.Stub.asInterface(binder)
            service = stub
            waiter?.complete(stub)
            waiter = null
            try {
                binder.linkToDeath({ service = null }, 0)
            } catch (_: Throwable) {
                service = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun snapshot(): ShizukuSnapshot {
        val installed = managerInstalled()
        if (!installed) {
            return ShizukuSnapshot(
                managerInstalled = false,
                message = "Shizuku is not installed. X-KeepAlive stays in standard mode.",
            )
        }
        val alive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!alive) {
            return ShizukuSnapshot(
                managerInstalled = true,
                binderAlive = false,
                message = "Shizuku is installed but its service is stopped. This app cannot see the wireless debugging pairing until you start Shizuku.",
            )
        }
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        val version = try {
            Shizuku.getVersion()
        } catch (_: Throwable) {
            null
        }
        return ShizukuSnapshot(
            managerInstalled = true,
            binderAlive = true,
            permissionGranted = granted,
            apiVersion = version,
            message = if (granted) {
                "Shizuku running and authorized" + (version?.let { " (API $it)" } ?: "")
            } else {
                "Shizuku is running. It needs your authorization, which is not requested on its own."
            },
        )
    }

    suspend fun exec(command: String): ShellResult {
        if (!PolicyCommands.isAllowed(command)) {
            return ShellResult(-1, "", command, "Command not allowed")
        }
        val state = snapshot()
        if (!state.ready) {
            return ShellResult(-1, "", command, state.message)
        }
        return try {
            val shell = awaitService()
            val payload = withContext(Dispatchers.IO) { shell.exec(command) }
            val (code, output) = PolicyOutput.parseExitPayload(payload)
            ShellResult(code, output, command, null)
        } catch (_: TimeoutCancellationException) {
            service = null
            ShellResult(-1, "", command, "Shizuku service timed out")
        } catch (throwable: Throwable) {
            service = null
            ShellResult(-1, "", command, throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    fun unbind() {
        service = null
        waiter = null
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.unbindUserService(args, connection, true)
            }
        } catch (_: Throwable) {
            // Shizuku is already stopped: there is no service to close.
        }
    }

    private suspend fun awaitService(): IShellService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return mutex.withLock {
            service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
            val deferred = CompletableDeferred<IShellService>()
            waiter = deferred
            try {
                withContext(Dispatchers.Main.immediate) {
                    Shizuku.bindUserService(args, connection)
                }
            } catch (throwable: Throwable) {
                waiter = null
                if (deferred.isActive) deferred.completeExceptionally(throwable)
                throw throwable
            }
            withTimeout(8_000) { deferred.await() }
        }
    }

    private fun managerInstalled(): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                SHIZUKU_PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SERVICE_VERSION = 1
    }
}

@Suppress("unused")
class ShellUserService private constructor(
    @Suppress("UNUSED_PARAMETER") owner: String,
) : IShellService.Stub() {
    constructor() : this("")
    constructor(context: Context) : this(context.packageName)

    override fun destroy() {
        kotlin.system.exitProcess(0)
    }

    override fun exec(command: String?): String = synchronized(lock) {
        val raw = command?.trim().orEmpty()
        if (!PolicyCommands.isAllowed(raw)) return "-1\ncommand not allowed"
        return try {
            val process = ProcessBuilder("sh", "-c", raw)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().use { source ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (output.length < MAX_OUTPUT) {
                            output.append(buffer, 0, count.coerceAtMost(MAX_OUTPUT - output.length))
                        }
                    }
                }
            }
            reader.start()
            val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(500)
                return "124\ntimeout"
            }
            reader.join(1_000)
            "${process.exitValue()}\n$output"
        } catch (throwable: Throwable) {
            "-1\n${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
        }
    }

    private companion object {
        val lock = Any()
        const val MAX_OUTPUT = 48_000
    }
}
