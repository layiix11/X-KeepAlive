package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPlanTest {
    private val allow = mapOf(
        "RUN_IN_BACKGROUND" to "allow",
        "RUN_ANY_IN_BACKGROUND" to "allow",
    )

    private fun observed(
        listed: Boolean? = false,
        standby: String? = "rare",
        inactive: Boolean? = true,
        ops: Map<String, String?> = mapOf(
            "RUN_IN_BACKGROUND" to "ignore",
            "RUN_ANY_IN_BACKGROUND" to "default",
        ),
    ) = ObservedPolicy(listed, standby, inactive, ops)

    @Test
    fun applySkipsUnknownReadsAndValuesThatAreAlreadyInPlace() {
        val steps = PolicyPlan.applySteps(
            observed(
                listed = null,
                standby = null,
                inactive = null,
                ops = mapOf("RUN_IN_BACKGROUND" to null, "RUN_ANY_IN_BACKGROUND" to "allow"),
            ),
        )
        assertTrue(steps.isEmpty())

        val present = PolicyPlan.applySteps(
            observed(listed = true, standby = "exempted", inactive = false, ops = allow),
        )
        assertTrue(present.isEmpty())
        assertTrue(PolicyPlan.capture("com.example.game", observed(listed = true)).whitelistAlreadyPresent)
    }

    @Test
    fun applyChangesOnlyTheSettingsThatNeedIt() {
        val steps = PolicyPlan.applySteps(observed(standby = "active"))
        assertEquals(listOf(PolicyAction.ADD_WHITELIST, PolicyAction.SET_INACTIVE, PolicyAction.SET_APP_OP, PolicyAction.SET_APP_OP), steps.map { it.action })
        assertFalse(steps.any { it.action == PolicyAction.SET_STANDBY })
        assertEquals("allow", steps.last().value)
    }

    @Test
    fun restoreWritesTheSavedAppOpAndLeavesAPreexistingWhitelist() {
        val captured = PolicyPlan.capture("com.example.game", observed(listed = true, standby = "rare"))
        var store = captured
        PolicyPlan.applySteps(observed(listed = true, standby = "rare")).forEach { step ->
            store = PolicyPlan.markApplied(store, step)
        }
        val steps = PolicyPlan.restoreSteps(store)
        assertFalse(steps.any { it.action == PolicyAction.REMOVE_WHITELIST })
        assertEquals("rare", steps.first { it.action == PolicyAction.SET_STANDBY }.value)
        assertEquals("true", steps.first { it.action == PolicyAction.SET_INACTIVE }.value)
        val restoredOps = steps.filter { it.action == PolicyAction.SET_APP_OP }.associate { it.op to it.value }
        assertEquals("ignore", restoredOps["RUN_IN_BACKGROUND"])
        assertEquals("default", restoredOps["RUN_ANY_IN_BACKGROUND"])
    }

    @Test
    fun restoreRemovesWhitelistOnlyWhenThisAppAddedIt() {
        val captured = PolicyPlan.capture("com.example.game", observed(listed = false))
        val added = PolicyPlan.markApplied(captured, PolicyStep("Whitelist Doze", PolicyAction.ADD_WHITELIST))
        assertTrue(PolicyPlan.restoreSteps(added).any { it.action == PolicyAction.REMOVE_WHITELIST })
        val after = observed(listed = false, standby = "rare", inactive = true, ops = mapOf(
            "RUN_IN_BACKGROUND" to "ignore",
            "RUN_ANY_IN_BACKGROUND" to "default",
        ))
        assertTrue(PolicyPlan.readsMatch(added, after))
        assertFalse(PolicyPlan.readsMatch(added, after.copy(whitelistListed = true)))
        assertFalse(PolicyPlan.readsMatch(added, after.copy(whitelistListed = null)))
    }

    @Test
    fun failedWhitelistReadIsNotTreatedAsAbsent() {
        val captured = PolicyPlan.capture("com.example.game", observed(listed = null))
        assertFalse(captured.whitelistAlreadyPresent)
        assertFalse(captured.whitelistAddedByUs)
        assertTrue(PolicyPlan.restoreSteps(captured).none { it.action == PolicyAction.REMOVE_WHITELIST })
    }

    @Test
    fun legacyRecordRestoresOnlyValuesThatWereSaved() {
        val legacy = StoredPolicy(
            packageName = "com.example.game",
            generation = 0,
            whitelistAlreadyPresent = false,
            whitelistAddedByUs = false,
            savedStandby = "frequent",
            standbyChanged = true,
            savedInactive = "true",
            inactiveChanged = false,
            savedAppOps = emptyMap(),
            changedAppOps = emptySet(),
        )
        val steps = PolicyPlan.restoreSteps(legacy)
        assertEquals(listOf(PolicyAction.SET_STANDBY, PolicyAction.SET_INACTIVE), steps.map { it.action })
        assertTrue(PolicyPlan.missingExactTargets(legacy).any { it.contains("whitelist", ignoreCase = true) })
        assertTrue(PolicyPlan.missingExactTargets(legacy).any { it.contains("App ops") })
        val after = observed(standby = "frequent", inactive = true)
        assertTrue(PolicyPlan.knownTargetsMatch(legacy, after))
        assertFalse(PolicyPlan.readsMatch(legacy, after) && PolicyPlan.missingExactTargets(legacy).isEmpty())
    }

    @Test
    fun missingOriginalAppOpIsNotReportedAsRestored() {
        val store = PolicyPlan.capture("com.example.game", observed()).copy(
            changedAppOps = setOf("RUN_IN_BACKGROUND"),
            savedAppOps = emptyMap(),
        )
        assertTrue(PolicyPlan.restoreSteps(store).none { it.op == "RUN_IN_BACKGROUND" })
        assertTrue(PolicyPlan.missingExactTargets(store).any { it.contains("RUN_IN_BACKGROUND") })
        assertFalse(PolicyPlan.knownTargetsMatch(store, observed(ops = allow)))
    }

    @Test
    fun appOpCodecRoundTripAndDefaultRead() {
        val encoded = PolicyPlan.encodeAppOps(
            mapOf("RUN_ANY_IN_BACKGROUND" to "ignore", "RUN_IN_BACKGROUND" to "default", "OTHER" to "allow"),
        )
        assertEquals("RUN_ANY_IN_BACKGROUND=ignore;RUN_IN_BACKGROUND=default", encoded)
        assertEquals("ignore", PolicyPlan.decodeAppOps(encoded)["RUN_ANY_IN_BACKGROUND"])
        assertEquals(setOf("RUN_IN_BACKGROUND"), PolicyPlan.decodeChangedOps("NOPE,RUN_IN_BACKGROUND"))
        assertEquals("default", PolicyOutput.observedAppOpMode("No operations.", "RUN_IN_BACKGROUND", true))
        assertNull(PolicyOutput.observedAppOpMode("No operations.", "RUN_IN_BACKGROUND", false))
        assertEquals("allow", PolicyOutput.observedAppOpMode("RUN_IN_BACKGROUND: allow", "RUN_IN_BACKGROUND", true))
    }
}
