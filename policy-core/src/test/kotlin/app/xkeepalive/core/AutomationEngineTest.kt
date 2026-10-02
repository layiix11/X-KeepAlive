package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {
    private val game = "com.example.game"
    private val chrome = "com.android.chrome"

    @Test
    fun staysInactiveUntilEnabled() {
        val engine = AutomationEngine()
        val snapshot = engine.onSample(sample(enabled = false, foreground = game))
        assertEquals(Phase.INACTIVE, snapshot.phase)
    }

    @Test
    fun waitsUntilTheSelectedGameIsOpened() {
        val engine = AutomationEngine()
        val snapshot = engine.onSample(sample(foreground = "com.android.launcher3"))
        assertEquals(Phase.WAITING_FOR_GAME, snapshot.phase)
        assertEquals(Blocker.NONE, snapshot.blocker)
    }

    @Test
    fun openingTheGameDoesNotCountAsAReturn() {
        val engine = AutomationEngine()
        val snapshot = engine.onSample(sample(foreground = game, pids = setOf(10)))
        assertEquals(Phase.GAME_FOREGROUND, snapshot.phase)
        assertEquals(ProcessOutcome.NOT_CHECKED, snapshot.outcome)
        val again = engine.onSample(sample(foreground = game, pids = setOf(10)))
        assertEquals(Phase.GAME_FOREGROUND, again.phase)
    }

    @Test
    fun leaveAndReturnWithTheSamePidStaysInMemory() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(10), policy = true))
        val left = engine.onSample(sample(foreground = chrome, pids = setOf(10), policy = true))
        assertEquals(Phase.BACKGROUND_MANAGEMENT_ACTIVE, left.phase)
        assertEquals(setOf(10), left.backgroundPids)
        val back = engine.onSample(sample(foreground = game, pids = setOf(10), policy = true))
        assertEquals(Phase.GAME_RETURNED, back.phase)
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, back.outcome)
        assertEquals(ProcessNotes.STILL_IN_MEMORY, back.limitNote)
        val still = engine.onSample(sample(foreground = game, pids = setOf(10), policy = true))
        assertEquals(Phase.GAME_RETURNED, still.phase)
    }

    @Test
    fun standardModeNeverClaimsBackgroundManagement() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = null, checked = false))
        val left = engine.onSample(sample(foreground = chrome, pids = null, checked = false))
        assertEquals(Phase.GAME_BACKGROUND, left.phase)
        assertEquals(ProcessOutcome.UNKNOWN_NO_PRIVILEGE, left.outcome)
    }

    @Test
    fun emptyPidOnReturnMeansAndroidTerminatedTheProcess() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(42)))
        engine.onSample(sample(foreground = chrome, pids = setOf(42)))
        val back = engine.onSample(sample(foreground = game, pids = emptySet()))
        assertEquals(ProcessOutcome.TERMINATED, back.outcome)
        assertTrue(back.limitNote!!.contains("terminated"))
    }

    @Test
    fun newPidMeansTheProcessWasRecreated() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(7)))
        engine.onSample(sample(foreground = chrome, pids = setOf(7)))
        val back = engine.onSample(sample(foreground = game, pids = setOf(90)))
        assertEquals(ProcessOutcome.RESTARTED_NEW_PID, back.outcome)
        assertEquals(setOf(90), back.observedPids)
        assertTrue(back.pidChange!!.contains("90"))
    }

    @Test
    fun oneEmptyPollDoesNotDropThePid() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(7)))
        engine.onSample(sample(foreground = chrome, pids = setOf(7)))
        val flicker = engine.onSample(sample(foreground = chrome, pids = emptySet()))
        assertEquals(setOf(7), flicker.observedPids)
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, flicker.outcome)
        val gone = engine.onSample(sample(foreground = chrome, pids = emptySet()))
        assertEquals(Phase.GAME_BACKGROUND, gone.phase)
        assertEquals(ProcessOutcome.TERMINATED, gone.outcome)
        assertTrue(gone.observedPids.isEmpty())
    }

    @Test
    fun backgroundPidReplacementIsFollowed() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(9894)))
        engine.onSample(sample(foreground = chrome, pids = setOf(9894)))
        val replaced = engine.onSample(sample(foreground = chrome, pids = setOf(10231)))
        assertEquals(setOf(10231), replaced.observedPids)
        assertEquals(setOf(10231), replaced.backgroundPids)
        assertEquals(ProcessOutcome.RESTARTED_NEW_PID, replaced.outcome)
        val back = engine.onSample(sample(foreground = game, pids = setOf(10231)))
        assertEquals(Phase.GAME_RETURNED, back.phase)
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, back.outcome)
        assertEquals(setOf(10231), back.observedPids)
    }

    @Test
    fun extraChildPidKeepsTheProcessInMemory() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(10)))
        engine.onSample(sample(foreground = chrome, pids = setOf(10)))
        val extra = engine.onSample(sample(foreground = chrome, pids = setOf(10, 11)))
        assertEquals(setOf(10, 11), extra.observedPids)
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, extra.outcome)
    }

    @Test
    fun pidDisappearingWhileStillInBackgroundIsTermination() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(7)))
        engine.onSample(sample(foreground = chrome, pids = setOf(7)))
        engine.onSample(sample(foreground = chrome, pids = emptySet()))
        val later = engine.onSample(sample(foreground = chrome, pids = emptySet()))
        assertEquals(Phase.GAME_BACKGROUND, later.phase)
        assertEquals(ProcessOutcome.TERMINATED, later.outcome)
    }

    @Test
    fun missingPermissionsBlockWithoutForgettingTheSelection() {
        val engine = AutomationEngine()
        val snapshot = engine.onSample(sample(permissions = false, foreground = game))
        assertEquals(Blocker.PERMISSIONS, snapshot.blocker)
        assertEquals(Phase.WAITING_FOR_GAME, snapshot.phase)
    }

    @Test
    fun blankOrInjectedPackageIsRejected() {
        val engine = AutomationEngine()
        assertEquals(Blocker.NO_GAME_SELECTED, engine.onSample(sample(packageName = "")).blocker)
        assertEquals(
            Blocker.NO_GAME_SELECTED,
            engine.onSample(sample(packageName = "com.foo;reboot")).blocker,
        )
    }

    @Test
    fun switchingPackageStartsOver() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(1)))
        engine.onSample(sample(foreground = chrome, pids = setOf(1)))
        val other = engine.onSample(
            sample(packageName = "com.other.game", foreground = "com.other.game", pids = setOf(2)),
        )
        assertEquals(Phase.GAME_FOREGROUND, other.phase)
        assertEquals(ProcessOutcome.NOT_CHECKED, other.outcome)
    }

    @Test
    fun verifiedPolicyWhileAlreadyInBackgroundPromotesThePhase() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(3)))
        val left = engine.onSample(sample(foreground = chrome, pids = setOf(3)))
        assertEquals(Phase.GAME_BACKGROUND, left.phase)
        val managed = engine.onSample(sample(foreground = chrome, pids = setOf(3), policy = true))
        assertEquals(Phase.BACKGROUND_MANAGEMENT_ACTIVE, managed.phase)
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, managed.outcome)
    }

    @Test
    fun turningOffResetsTheLatch() {
        val engine = AutomationEngine()
        engine.onSample(sample(foreground = game, pids = setOf(1)))
        engine.onSample(sample(foreground = chrome, pids = setOf(1)))
        engine.onSample(sample(foreground = game, pids = setOf(1)))
        val off = engine.onSample(sample(enabled = false, foreground = game))
        assertEquals(Phase.INACTIVE, off.phase)
        assertNull(off.limitNote)
        val on = engine.onSample(sample(foreground = game, pids = setOf(1)))
        assertEquals(Phase.GAME_FOREGROUND, on.phase)
    }

    private fun sample(
        enabled: Boolean = true,
        permissions: Boolean = true,
        packageName: String = game,
        foreground: String? = null,
        policy: Boolean = false,
        pids: Set<Int>? = emptySet(),
        checked: Boolean = true,
    ): Sample = Sample(
        automationEnabled = enabled,
        permissionsReady = permissions,
        selectedPackage = packageName,
        foregroundPackage = foreground,
        foregroundKnown = foreground != null,
        policyVerified = policy,
        selectedPids = pids,
        pidsChecked = checked,
    )
}
