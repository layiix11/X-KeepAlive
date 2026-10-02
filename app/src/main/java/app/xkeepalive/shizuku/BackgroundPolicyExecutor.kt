package app.xkeepalive.shizuku

import app.xkeepalive.core.OpResult
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.PolicyCommands
import app.xkeepalive.core.PolicyOutput
import app.xkeepalive.core.PolicyReport
import app.xkeepalive.core.ShellResult
import app.xkeepalive.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs only documented ADB commands, and only after Shizuku has been authorized.
 * It does not start, close, or touch the game. Every result is read back.
 */
class BackgroundPolicyExecutor(
    private val shell: ShizukuBridge,
    private val settings: SettingsRepository,
) {
    private val gate = Mutex()

    suspend fun apply(packageName: String): PolicyReport = gate.withLock { applyLocked(packageName) }

    suspend fun revert(packageName: String): PolicyReport = gate.withLock { revertLocked(packageName) }

    suspend fun verify(packageName: String, standbyChanged: Boolean): Boolean = gate.withLock {
        verifyLocked(packageName, standbyChanged)
    }

    suspend fun readPids(packageName: String): Set<Int>? = gate.withLock { readPidsLocked(packageName) }

    private suspend fun applyLocked(packageName: String): PolicyReport {
        if (!PackageNames.isValid(packageName)) {
            return PolicyReport(packageName, "STANDARD", listOf(failedOp("Invalid package")), false)
        }
        val state = shell.snapshot()
        if (!state.ready) {
            return PolicyReport(
                packageName,
                "STANDARD",
                listOf(OpResult("shell", "", false, state.message)),
                false,
            )
        }

        val bucket = readStandby(packageName)
        val inactive = readInactive(packageName)
        val current = settings.snapshot()
        if (current.baselinePackage != packageName) {
            settings.saveBaseline(
                packageName,
                bucket.orEmpty(),
                when (inactive) {
                    true -> "true"
                    false -> "false"
                    null -> ""
                },
            )
        }

        val operations = mutableListOf<OpResult>()
        var strong = false

        val add = shell.exec(PolicyCommands.whitelistAdd(packageName))
        var listed = whitelistListed(packageName)
        if (!listed) {
            val dump = shell.exec(PolicyCommands.dumpsysWhitelistAdd(packageName))
            listed = whitelistListed(packageName)
            operations += OpResult(
                "Whitelist Doze",
                add.command,
                listed,
                detail(
                    if (listed) "Package present after the second attempt." else "Whitelist not confirmed.",
                    add,
                    dump,
                ),
            )
        } else {
            operations += OpResult(
                "Whitelist Doze",
                add.command,
                true,
                detail("Package is on the whitelist.", add),
            )
        }
        strong = listed

        val inactiveResult = shell.exec(PolicyCommands.setInactive(packageName, false))
        val inactiveNow = readInactive(packageName)
        operations += OpResult(
            "App not idle",
            inactiveResult.command,
            inactiveNow == false,
            detail("Idle state read: ${inactiveNow ?: "unknown"}. This alone does not keep the process alive.", inactiveResult),
        )

        if (bucket == "exempted") {
            operations += OpResult(
                "Standby bucket",
                PolicyCommands.getStandby(packageName),
                true,
                "Bucket already exempted. It was not lowered.",
            )
            strong = true
        } else {
            val set = shell.exec(PolicyCommands.setStandby(packageName, "active"))
            val now = readStandby(packageName)
            val ok = now == "active" || now == "exempted"
            if (ok && bucket != null && bucket != "active" && bucket != "exempted") {
                settings.setStandbyChanged(true)
            }
            operations += OpResult(
                "Standby bucket",
                set.command,
                ok,
                detail("Before: ${bucket ?: "unknown"}. After: ${now ?: "unknown"}.", set),
            )
            strong = strong || ok
        }

        PolicyCommands.appOps.forEach { op ->
            val set = shell.exec(PolicyCommands.setAppOp(packageName, op, "allow"))
            val mode = readAppOp(packageName, op)
            val ok = mode == "allow"
            operations += OpResult(op, set.command, ok, detail("Mode read: ${mode ?: "unknown"}.", set))
            strong = strong || ok
        }

        if (strong) settings.setPolicyPackage(packageName)
        return PolicyReport(packageName, "SHIZUKU", operations, strong)
    }

    private suspend fun revertLocked(packageName: String): PolicyReport {
        if (!PackageNames.isValid(packageName)) {
            return PolicyReport(packageName, "SKIPPED", listOf(failedOp("Invalid package")), false)
        }
        val state = shell.snapshot()
        if (!state.ready) {
            return PolicyReport(
                packageName,
                "SKIPPED",
                listOf(
                    OpResult(
                        "revoke",
                        "",
                        false,
                        state.message + " Any policy already applied was not revoked.",
                    ),
                ),
                false,
            )
        }
        val saved = settings.snapshot()
        val operations = mutableListOf<OpResult>()
        val remove = shell.exec(PolicyCommands.whitelistRemove(packageName))
        val stillListed = whitelistListed(packageName)
        operations += OpResult(
            "Whitelist removal",
            remove.command,
            !stillListed,
            detail(
                if (!stillListed) "Package is off the whitelist." else "The package is still on the whitelist.",
                remove,
            ),
        )

        if (
            saved.baselinePackage == packageName &&
            saved.standbyChanged &&
            saved.savedStandby in PolicyCommands.standbyBuckets
        ) {
            val set = shell.exec(PolicyCommands.setStandby(packageName, saved.savedStandby))
            val now = readStandby(packageName)
            operations += OpResult(
                "Bucket restore",
                set.command,
                now == saved.savedStandby,
                detail("Current bucket: ${now ?: "unknown"}.", set),
            )
        }

        if (saved.baselinePackage == packageName && saved.savedInactive == "true") {
            val set = shell.exec(PolicyCommands.setInactive(packageName, true))
            val now = readInactive(packageName)
            operations += OpResult(
                "Idle restore",
                set.command,
                now == true,
                detail("Idle read: ${now ?: "unknown"}.", set),
            )
        }

        PolicyCommands.appOps.forEach { op ->
            val set = shell.exec(PolicyCommands.setAppOp(packageName, op, "default"))
            val mode = readAppOp(packageName, op)
            val ok = set.error == null && (mode == null || mode == "default")
            operations += OpResult(
                "Reset $op",
                set.command,
                ok,
                detail("Mode read: ${mode ?: "absent"}.", set),
            )
        }

        if (!stillListed) settings.clearPolicy()
        return PolicyReport(packageName, "REVERT", operations, !stillListed)
    }

    private suspend fun verifyLocked(packageName: String, standbyChanged: Boolean): Boolean {
        if (!shell.snapshot().ready || !PackageNames.isValid(packageName)) return false
        if (whitelistListed(packageName)) return true
        if (PolicyCommands.appOps.any { op -> readAppOp(packageName, op) == "allow" }) return true
        val bucket = readStandby(packageName)
        if (bucket == "exempted") return true
        return standbyChanged && bucket == "active"
    }

    private suspend fun readPidsLocked(packageName: String): Set<Int>? {
        if (!shell.snapshot().ready || !PackageNames.isValid(packageName)) return null
        val listed = shell.exec(PolicyCommands.psAll())
        val fromPs = if (listed.error == null && listed.exitCode == 0) {
            PolicyOutput.parseProcessPids(listed.output, packageName)
        } else {
            emptySet()
        }
        val pidof = shell.exec(PolicyCommands.pidof(packageName))
        val fromPidof = when {
            pidof.error != null -> null
            pidof.exitCode == 1 && pidof.output.isBlank() -> emptySet()
            pidof.exitCode != 0 -> emptySet()
            else -> PolicyOutput.parsePids(pidof.output)
        }
        if (listed.error != null && pidof.error != null) return null
        return fromPs + (fromPidof ?: emptySet())
    }

    private suspend fun readStandby(packageName: String): String? {
        val result = shell.exec(PolicyCommands.getStandby(packageName))
        if (result.error != null) return null
        return PolicyOutput.parseStandbyBucket(result.output)
    }

    private suspend fun readInactive(packageName: String): Boolean? {
        val result = shell.exec(PolicyCommands.getInactive(packageName))
        if (result.error != null) return null
        return PolicyOutput.parseInactive(result.output)
    }

    private suspend fun readAppOp(packageName: String, op: String): String? {
        val result = shell.exec(PolicyCommands.getAppOp(packageName, op))
        if (result.error != null) return null
        return PolicyOutput.appOpMode(result.output, op)
    }

    private suspend fun whitelistListed(packageName: String): Boolean {
        val result = shell.exec(PolicyCommands.whitelistList())
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return false
        return PolicyOutput.whitelistContains(result.output, packageName)
    }

    private fun failedOp(detail: String) = OpResult("shell", "", false, detail)

    private fun detail(summary: String, vararg results: ShellResult): String {
        val raw = results.joinToString(" | ") { result ->
            val error = result.error?.let { " errore=$it" }.orEmpty()
            "exit=${result.exitCode}$error out=${result.output.trim().replace("\n", " ").take(160)}"
        }
        return "$summary $raw".take(500)
    }
}
