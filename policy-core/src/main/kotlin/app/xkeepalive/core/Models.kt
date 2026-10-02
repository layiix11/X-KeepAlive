package app.xkeepalive.core

enum class Phase {
    INACTIVE,
    WAITING_FOR_GAME,
    GAME_FOREGROUND,
    GAME_BACKGROUND,
    BACKGROUND_MANAGEMENT_ACTIVE,
    GAME_RETURNED,
}

enum class Blocker {
    NONE,
    PERMISSIONS,
    NO_GAME_SELECTED,
}

enum class ProcessOutcome {
    NOT_CHECKED,
    UNKNOWN_NO_PRIVILEGE,
    STILL_IN_MEMORY,
    TERMINATED,
    RESTARTED_NEW_PID,
    NOT_RUNNING,
}

data class Sample(
    val automationEnabled: Boolean,
    val permissionsReady: Boolean,
    val selectedPackage: String,
    val foregroundPackage: String?,
    val foregroundKnown: Boolean,
    val policyVerified: Boolean,
    val selectedPids: Set<Int>?,
    val pidsChecked: Boolean,
)

data class EngineSnapshot(
    val phase: Phase,
    val blocker: Blocker,
    val outcome: ProcessOutcome,
    val limitNote: String?,
    val foregroundPackage: String?,
    val selectedPackage: String,
    val policyVerified: Boolean,
    val backgroundPids: Set<Int>,
    val observedPids: Set<Int>,
    val pidChange: String? = null,
) {
    companion object {
        fun inactive(): EngineSnapshot = EngineSnapshot(
            phase = Phase.INACTIVE,
            blocker = Blocker.NONE,
            outcome = ProcessOutcome.NOT_CHECKED,
            limitNote = null,
            foregroundPackage = null,
            selectedPackage = "",
            policyVerified = false,
            backgroundPids = emptySet(),
            observedPids = emptySet(),
        )
    }
}

data class OpResult(
    val name: String,
    val command: String,
    val verified: Boolean,
    val detail: String,
)

data class PolicyReport(
    val packageName: String,
    val mode: String,
    val operations: List<OpResult>,
    val anyVerified: Boolean,
)

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val command: String,
    val error: String? = null,
)

object PhaseLabels {
    fun english(phase: Phase): String = when (phase) {
        Phase.INACTIVE -> "Inactive"
        Phase.WAITING_FOR_GAME -> "Waiting for the game"
        Phase.GAME_FOREGROUND -> "Game in the foreground"
        Phase.GAME_BACKGROUND -> "Game in the background"
        Phase.BACKGROUND_MANAGEMENT_ACTIVE -> "Background management active"
        Phase.GAME_RETURNED -> "Game in the foreground again"
    }
}

object ProcessNotes {
    const val STILL_IN_MEMORY =
        "The game process is still in memory. If the graphics surface was destroyed, the game can still reload on its own."
    const val TERMINATED =
        "The process seen in the background is gone. Android terminated the game. A new load is expected when you return."
    const val RESTARTED =
        "The process has a new PID. The previous one died and another was created. The game already reloaded the process."
    const val NOT_RUNNING =
        "No game process is running."
    const val NOT_RUNNING_ON_LEAVE =
        "Right after moving to the background, the process is not running."
    const val ALIVE_ON_LEAVE =
        "The process is in memory right after you leave. The useful check is when you return to the game."
    const val UNKNOWN =
        "Without Shizuku the PID cannot be read. A process still in memory cannot be told apart from one Android terminated."
    const val PID_FOLLOWED =
        "The game PID changed. X-KeepAlive dropped the old one and now follows the current process."

    fun forOutcome(outcome: ProcessOutcome): String? = when (outcome) {
        ProcessOutcome.STILL_IN_MEMORY -> STILL_IN_MEMORY
        ProcessOutcome.TERMINATED -> TERMINATED
        ProcessOutcome.RESTARTED_NEW_PID -> RESTARTED
        ProcessOutcome.NOT_RUNNING -> NOT_RUNNING
        ProcessOutcome.UNKNOWN_NO_PRIVILEGE -> UNKNOWN
        ProcessOutcome.NOT_CHECKED -> null
    }
}

object ProcessVerdict {
    fun compare(before: Set<Int>, after: Set<Int>): ProcessOutcome = when {
        before.isNotEmpty() && after.isEmpty() -> ProcessOutcome.TERMINATED
        before.isNotEmpty() && after.isNotEmpty() && before.intersect(after).isEmpty() ->
            ProcessOutcome.RESTARTED_NEW_PID
        after.isNotEmpty() -> ProcessOutcome.STILL_IN_MEMORY
        else -> ProcessOutcome.NOT_RUNNING
    }
}
