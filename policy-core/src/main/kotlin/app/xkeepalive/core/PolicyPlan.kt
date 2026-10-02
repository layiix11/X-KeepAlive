package app.xkeepalive.core

data class ObservedPolicy(
    val whitelistListed: Boolean?,
    val standby: String?,
    val inactive: Boolean?,
    val appOps: Map<String, String?>,
)

data class StoredPolicy(
    val packageName: String,
    val generation: Int,
    val whitelistAlreadyPresent: Boolean,
    val whitelistAddedByUs: Boolean,
    val savedStandby: String,
    val standbyChanged: Boolean,
    val savedInactive: String,
    val inactiveChanged: Boolean,
    val savedAppOps: Map<String, String>,
    val changedAppOps: Set<String>,
) {
    fun hasOwnChanges(): Boolean =
        whitelistAddedByUs || standbyChanged || inactiveChanged || changedAppOps.isNotEmpty()
}

enum class PolicyAction {
    ADD_WHITELIST,
    REMOVE_WHITELIST,
    SET_STANDBY,
    SET_INACTIVE,
    SET_APP_OP,
}

data class PolicyStep(
    val name: String,
    val action: PolicyAction,
    val value: String = "",
    val op: String = "",
)

object PolicyPlan {
    const val GENERATION = 2

    fun capture(packageName: String, observed: ObservedPolicy): StoredPolicy {
        val ops = linkedMapOf<String, String>()
        PolicyCommands.appOps.forEach { op ->
            val mode = observed.appOps[op]
            if (mode != null && mode in PolicyCommands.appOpModes) ops[op] = mode
        }
        val standby = observed.standby?.takeIf { it == "exempted" || it in PolicyCommands.standbyBuckets }.orEmpty()
        return StoredPolicy(
            packageName = packageName,
            generation = GENERATION,
            whitelistAlreadyPresent = observed.whitelistListed == true,
            whitelistAddedByUs = false,
            savedStandby = standby,
            standbyChanged = false,
            savedInactive = when (observed.inactive) {
                true -> "true"
                false -> "false"
                null -> ""
            },
            inactiveChanged = false,
            savedAppOps = ops,
            changedAppOps = emptySet(),
        )
    }

    fun applySteps(observed: ObservedPolicy): List<PolicyStep> {
        val steps = mutableListOf<PolicyStep>()
        if (observed.whitelistListed == false) {
            steps += PolicyStep("Whitelist Doze", PolicyAction.ADD_WHITELIST)
        }
        val bucket = observed.standby
        if (bucket != null && bucket in PolicyCommands.standbyBuckets && bucket != "active") {
            steps += PolicyStep("Standby bucket", PolicyAction.SET_STANDBY, value = "active")
        }
        if (observed.inactive == true) {
            steps += PolicyStep("App not idle", PolicyAction.SET_INACTIVE, value = "false")
        }
        PolicyCommands.appOps.forEach { op ->
            val mode = observed.appOps[op]
            if (mode != null && mode in PolicyCommands.appOpModes && mode != "allow") {
                steps += PolicyStep(op, PolicyAction.SET_APP_OP, value = "allow", op = op)
            }
        }
        return steps
    }

    fun markApplied(store: StoredPolicy, step: PolicyStep): StoredPolicy = when (step.action) {
        PolicyAction.ADD_WHITELIST -> store.copy(whitelistAddedByUs = true)
        PolicyAction.SET_STANDBY -> store.copy(standbyChanged = true)
        PolicyAction.SET_INACTIVE -> store.copy(inactiveChanged = true)
        PolicyAction.SET_APP_OP -> store.copy(changedAppOps = store.changedAppOps + step.op)
        PolicyAction.REMOVE_WHITELIST -> store
    }

    fun clearChange(store: StoredPolicy, step: PolicyStep): StoredPolicy = when (step.action) {
        PolicyAction.REMOVE_WHITELIST -> store.copy(whitelistAddedByUs = false)
        PolicyAction.SET_STANDBY -> store.copy(standbyChanged = false)
        PolicyAction.SET_INACTIVE -> store.copy(inactiveChanged = false)
        PolicyAction.SET_APP_OP -> store.copy(changedAppOps = store.changedAppOps - step.op)
        PolicyAction.ADD_WHITELIST -> store
    }

    fun restoreSteps(store: StoredPolicy): List<PolicyStep> {
        if (store.generation < GENERATION) return legacyRestoreSteps(store)
        val steps = mutableListOf<PolicyStep>()
        if (store.whitelistAddedByUs && !store.whitelistAlreadyPresent) {
            steps += PolicyStep("Whitelist removal", PolicyAction.REMOVE_WHITELIST)
        }
        if (store.standbyChanged && store.savedStandby in PolicyCommands.standbyBuckets) {
            steps += PolicyStep("Bucket restore", PolicyAction.SET_STANDBY, value = store.savedStandby)
        }
        if (store.inactiveChanged && store.savedInactive == "true") {
            steps += PolicyStep("Idle restore", PolicyAction.SET_INACTIVE, value = "true")
        }
        PolicyCommands.appOps.forEach { op ->
            if (op !in store.changedAppOps) return@forEach
            val mode = store.savedAppOps[op]
            if (mode != null && mode in PolicyCommands.appOpModes) {
                steps += PolicyStep("Restore $op", PolicyAction.SET_APP_OP, value = mode, op = op)
            }
        }
        return steps
    }

    fun missingExactTargets(store: StoredPolicy): List<String> {
        if (store.generation < GENERATION) {
            return listOf(
                "Doze whitelist was not changed back: the previous version did not save whether this package was already listed.",
                "App ops were not reset: the previous version did not save the original modes.",
            )
        }
        val gaps = mutableListOf<String>()
        if (store.standbyChanged && store.savedStandby !in PolicyCommands.standbyBuckets) {
            gaps += "Standby bucket was changed, but the original bucket is unknown, so it was not restored."
        }
        if (store.inactiveChanged && store.savedInactive != "true") {
            gaps += "Idle state was changed, but the original value is unknown, so it was not restored."
        }
        store.changedAppOps.forEach { op ->
            val mode = store.savedAppOps[op]
            if (mode == null || mode !in PolicyCommands.appOpModes) {
                gaps += "$op was changed, but the original mode was not saved, so it was not restored."
            }
        }
        return gaps
    }

    fun readsMatch(store: StoredPolicy, after: ObservedPolicy): Boolean {
        if (
            store.generation >= GENERATION &&
            store.whitelistAddedByUs &&
            !store.whitelistAlreadyPresent &&
            after.whitelistListed != false
        ) {
            return false
        }
        if (store.standbyChanged) {
            if (store.savedStandby !in PolicyCommands.standbyBuckets) return false
            if (after.standby != store.savedStandby) return false
        }
        val restoreIdle = if (store.generation >= GENERATION) {
            store.inactiveChanged && store.savedInactive == "true"
        } else {
            store.savedInactive == "true"
        }
        if (restoreIdle && after.inactive != true) return false
        if (store.generation >= GENERATION) {
            for (op in store.changedAppOps) {
                val expected = store.savedAppOps[op] ?: return false
                if (expected !in PolicyCommands.appOpModes) return false
                if (after.appOps[op] != expected) return false
            }
        }
        return true
    }

    fun knownTargetsMatch(store: StoredPolicy, after: ObservedPolicy): Boolean {
        val known = if (store.generation >= GENERATION) {
            store
        } else {
            store.copy(
                generation = GENERATION,
                whitelistAddedByUs = false,
                changedAppOps = emptySet(),
                inactiveChanged = store.savedInactive == "true",
            )
        }
        return readsMatch(known, after)
    }

    fun signalsActive(observed: ObservedPolicy): Boolean {
        if (observed.whitelistListed == true) return true
        if (observed.standby == "active" || observed.standby == "exempted") return true
        return observed.appOps.values.any { it == "allow" }
    }

    fun encodeAppOps(modes: Map<String, String>): String =
        modes.entries
            .filter { (op, mode) -> op in PolicyCommands.appOps && mode in PolicyCommands.appOpModes }
            .sortedBy { it.key }
            .joinToString(";") { (op, mode) -> "$op=$mode" }

    fun decodeAppOps(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) return@forEach
            val op = pieces[0]
            val mode = pieces[1]
            if (op in PolicyCommands.appOps && mode in PolicyCommands.appOpModes) out[op] = mode
        }
        return out
    }

    fun decodeChangedOps(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filter { it in PolicyCommands.appOps }.toSet()

    fun encodeChangedOps(ops: Set<String>): String =
        ops.filter { it in PolicyCommands.appOps }.sorted().joinToString(",")

    private fun legacyRestoreSteps(store: StoredPolicy): List<PolicyStep> {
        val steps = mutableListOf<PolicyStep>()
        if (store.standbyChanged && store.savedStandby in PolicyCommands.standbyBuckets) {
            steps += PolicyStep("Bucket restore", PolicyAction.SET_STANDBY, value = store.savedStandby)
        }
        if (store.savedInactive == "true") {
            steps += PolicyStep("Idle restore", PolicyAction.SET_INACTIVE, value = "true")
        }
        return steps
    }
}
