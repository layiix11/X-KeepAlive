package app.xkeepalive.shizuku

import app.xkeepalive.core.ObservedPolicy
import app.xkeepalive.core.OpResult
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.PolicyAction
import app.xkeepalive.core.PolicyCommands
import app.xkeepalive.core.PolicyOutput
import app.xkeepalive.core.PolicyPlan
import app.xkeepalive.core.PolicyReport
import app.xkeepalive.core.PolicyStep
import app.xkeepalive.core.ShellResult
import app.xkeepalive.core.StoredPolicy
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.data.toStoredPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs only documented ADB commands, and only after Shizuku has been authorized.
 * Original values are read first. A setting is changed only when that read succeeds,
 * and it is put back to the saved value when the policy is turned off.
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
            return PolicyReport(packageName, "STANDARD", listOf(OpResult("shell", "", false, state.message)), false)
        }
        val existing = settings.snapshot()
        if (existing.policyPackage.isNotBlank() && existing.policyPackage != packageName) {
            return PolicyReport(
                packageName,
                "RESTORE_INCOMPLETE",
                listOf(
                    OpResult(
                        "restore",
                        "",
                        false,
                        "Policy for ${existing.policyPackage} is still applied and was not replaced.",
                    ),
                ),
                false,
            )
        }
        val existingStore = existing.toStoredPolicy()
        if (existing.policyPackage == packageName && existingStore.generation < PolicyPlan.GENERATION) {
            return revertLocked(packageName)
        }

        var store = if (existing.policyPackage == packageName && existingStore.hasOwnChanges()) {
            existingStore
        } else {
            val captured = PolicyPlan.capture(packageName, readObserved(packageName))
            settings.saveStoredPolicy(captured)
            captured
        }
        val steps = PolicyPlan.applySteps(readObserved(packageName))
        val operations = mutableListOf<OpResult>()
        try {
            for (step in steps) {
                store = PolicyPlan.markApplied(store, step)
                settings.saveStoredPolicy(store)
                if (!shell.snapshot().ready) {
                    return finishRollback(
                        packageName,
                        store,
                        operations,
                        "Shizuku was revoked before this change.",
                    )
                }
                val outcome = executeStep(packageName, step)
                operations += outcome
                if (!outcome.verified) {
                    return finishRollback(
                        packageName,
                        store,
                        operations,
                        "A change could not be confirmed.",
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            try {
                finishRollback(packageName, store, operations, "The policy change was interrupted.")
            } catch (_: Throwable) {
            }
            throw cancelled
        } catch (throwable: Throwable) {
            return finishRollback(
                packageName,
                store,
                operations,
                throwable.message ?: "Policy change failed.",
            )
        }
        val after = readObserved(packageName)
        val verified = if (steps.isEmpty()) PolicyPlan.signalsActive(after) else operations.all { it.verified }
        return PolicyReport(packageName, "SHIZUKU", operations, verified)
    }

    private suspend fun revertLocked(packageName: String): PolicyReport {
        if (!PackageNames.isValid(packageName)) {
            return PolicyReport(packageName, "SKIPPED", listOf(failedOp("Invalid package")), false)
        }
        val saved = settings.snapshot()
        if (saved.policyPackage != packageName && saved.baselinePackage != packageName) {
            return PolicyReport(
                packageName,
                "SKIPPED",
                listOf(OpResult("revoke", "", false, "No saved policy for this package.")),
                false,
            )
        }
        return restoreStore(packageName, saved.toStoredPolicy())
    }

    private suspend fun finishRollback(
        packageName: String,
        store: StoredPolicy,
        done: List<OpResult>,
        reason: String,
    ): PolicyReport {
        val rolled = restoreStore(packageName, store)
        val restored = rolled.mode == "REVERT" && rolled.anyVerified
        val note = OpResult(
            "rollback",
            "",
            false,
            if (restored) {
                "$reason Changes made by this attempt were restored."
            } else {
                "$reason Restore did not finish."
            },
        )
        return PolicyReport(
            packageName,
            if (restored) "SHIZUKU" else "RESTORE_INCOMPLETE",
            done + rolled.operations + note,
            false,
        )
    }

    private suspend fun restoreStore(packageName: String, store: StoredPolicy): PolicyReport {
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
                        state.message + " Saved settings were kept. Restore was not run and is not reported as successful.",
                    ),
                ),
                false,
            )
        }
        val gaps = PolicyPlan.missingExactTargets(store)
        val operations = mutableListOf<OpResult>()
        if (store.generation >= PolicyPlan.GENERATION && store.whitelistAlreadyPresent) {
            operations += OpResult(
                "Whitelist removal",
                "",
                true,
                "Package was already on the Doze whitelist. It was not removed.",
            )
        }
        var current = store
        for (step in PolicyPlan.restoreSteps(store)) {
            if (!shell.snapshot().ready) {
                operations += OpResult(
                    "revoke",
                    "",
                    false,
                    "Shizuku was revoked during restore. Remaining settings were left as they are.",
                )
                settings.saveStoredPolicy(current)
                return PolicyReport(packageName, "RESTORE_INCOMPLETE", operations, false)
            }
            val outcome = executeStep(packageName, step)
            operations += outcome
            if (outcome.verified) {
                current = PolicyPlan.clearChange(current, step)
                settings.saveStoredPolicy(current)
            }
        }
        val after = readObserved(packageName)
        val knownOk = operations.all { it.verified } && PolicyPlan.knownTargetsMatch(current, after)
        val complete = knownOk && gaps.isEmpty() && !current.hasOwnChanges()
        if (complete) {
            settings.clearPolicy()
            if (operations.isEmpty()) {
                operations += OpResult("revoke", "", true, "No owned settings needed to be restored.")
            }
            return PolicyReport(packageName, "REVERT", operations, true)
        }
        val reported = operations + gaps.map { gap -> OpResult("restore", "", false, gap) }
        if (knownOk && !current.hasOwnChanges()) {
            settings.clearPolicy()
        } else {
            settings.saveStoredPolicy(current)
        }
        return PolicyReport(packageName, "RESTORE_INCOMPLETE", reported, false)
    }

    private suspend fun executeStep(packageName: String, step: PolicyStep): OpResult = when (step.action) {
        PolicyAction.ADD_WHITELIST -> addWhitelist(packageName)
        PolicyAction.REMOVE_WHITELIST -> removeWhitelist(packageName)
        PolicyAction.SET_STANDBY -> {
            val set = shell.exec(PolicyCommands.setStandby(packageName, step.value))
            val now = readStandby(packageName)
            OpResult(
                step.name,
                set.command,
                now == step.value,
                detail("Bucket read: ${now ?: "unknown"}.", set),
            )
        }
        PolicyAction.SET_INACTIVE -> {
            val inactive = step.value == "true"
            val set = shell.exec(PolicyCommands.setInactive(packageName, inactive))
            val now = readInactive(packageName)
            OpResult(
                step.name,
                set.command,
                now == inactive,
                detail("Idle read: ${now ?: "unknown"}.", set),
            )
        }
        PolicyAction.SET_APP_OP -> {
            val set = shell.exec(PolicyCommands.setAppOp(packageName, step.op, step.value))
            val mode = readAppOp(packageName, step.op)
            OpResult(
                step.name,
                set.command,
                mode == step.value,
                detail("Mode read: ${mode ?: "unknown"}. Expected ${step.value}.", set),
            )
        }
    }

    private suspend fun addWhitelist(packageName: String): OpResult {
        val add = shell.exec(PolicyCommands.whitelistAdd(packageName))
        var listed = readWhitelist(packageName)
        if (listed == true) {
            return OpResult("Whitelist Doze", add.command, true, detail("Package is on the whitelist.", add))
        }
        val dump = shell.exec(PolicyCommands.dumpsysWhitelistAdd(packageName))
        listed = readWhitelist(packageName)
        return OpResult(
            "Whitelist Doze",
            add.command,
            listed == true,
            detail(
                if (listed == true) "Package present after the second attempt." else "Whitelist not confirmed.",
                add,
                dump,
            ),
        )
    }

    private suspend fun removeWhitelist(packageName: String): OpResult {
        val remove = shell.exec(PolicyCommands.whitelistRemove(packageName))
        val listed = readWhitelist(packageName)
        return OpResult(
            "Whitelist removal",
            remove.command,
            listed == false,
            detail(
                when (listed) {
                    false -> "Package is off the whitelist."
                    true -> "The package is still on the whitelist."
                    null -> "Whitelist could not be read, so removal was not confirmed."
                },
                remove,
            ),
        )
    }

    private suspend fun verifyLocked(packageName: String, standbyChanged: Boolean): Boolean {
        if (!shell.snapshot().ready || !PackageNames.isValid(packageName)) return false
        val observed = readObserved(packageName)
        if (observed.whitelistListed == true) return true
        if (observed.appOps.values.any { it == "allow" }) return true
        if (observed.standby == "exempted") return true
        return standbyChanged && observed.standby == "active"
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

    private suspend fun readObserved(packageName: String): ObservedPolicy {
        val ops = PolicyCommands.appOps.associateWith { op -> readAppOp(packageName, op) }
        return ObservedPolicy(
            whitelistListed = readWhitelist(packageName),
            standby = readStandby(packageName),
            inactive = readInactive(packageName),
            appOps = ops,
        )
    }

    private suspend fun readStandby(packageName: String): String? {
        val result = shell.exec(PolicyCommands.getStandby(packageName))
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.parseStandbyBucket(result.output)
    }

    private suspend fun readInactive(packageName: String): Boolean? {
        val result = shell.exec(PolicyCommands.getInactive(packageName))
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.parseInactive(result.output)
    }

    private suspend fun readAppOp(packageName: String, op: String): String? {
        val result = shell.exec(PolicyCommands.getAppOp(packageName, op))
        val ok = result.error == null && !PolicyOutput.commandFailed(result.exitCode, result.output)
        return PolicyOutput.observedAppOpMode(result.output, op, ok)
    }

    private suspend fun readWhitelist(packageName: String): Boolean? {
        val result = shell.exec(PolicyCommands.whitelistList())
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.whitelistContains(result.output, packageName)
    }

    private fun failedOp(detail: String) = OpResult("shell", "", false, detail)

    private fun detail(summary: String, vararg results: ShellResult): String {
        val raw = results.joinToString(" | ") { result ->
            val error = result.error?.let { " error=$it" }.orEmpty()
            "exit=${result.exitCode}$error out=${result.output.trim().replace("\n", " ").take(160)}"
        }
        return "$summary $raw".take(500)
    }
}
