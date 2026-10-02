package app.xkeepalive.core

/**
 * Monitoring state machine. It does not start the game and does not decide on its own
 * that the process is protected: it receives the policy result after it has been verified.
 */
class AutomationEngine {
    private val pids = PidTracker()
    private var trackedPackage: String = ""
    private var phase: Phase = Phase.INACTIVE
    private var outcome: ProcessOutcome = ProcessOutcome.NOT_CHECKED
    private var limitNote: String? = null
    private var pidChange: String? = null
    private var lastForeground: String? = null
    private var sawGame: Boolean = false
    private var backgroundPids: Set<Int> = emptySet()
    private var policyVerified: Boolean = false

    fun reset() {
        pids.reset()
        trackedPackage = ""
        phase = Phase.INACTIVE
        outcome = ProcessOutcome.NOT_CHECKED
        limitNote = null
        pidChange = null
        lastForeground = null
        sawGame = false
        backgroundPids = emptySet()
        policyVerified = false
    }

    fun onSample(sample: Sample): EngineSnapshot {
        pidChange = null
        if (!sample.automationEnabled) {
            reset()
            return build(sample)
        }
        if (sample.selectedPackage.isBlank() || !PackageNames.isValid(sample.selectedPackage)) {
            phase = Phase.INACTIVE
            return build(sample, Blocker.NO_GAME_SELECTED)
        }
        if (sample.selectedPackage != trackedPackage) {
            trackedPackage = sample.selectedPackage
            sawGame = false
            lastForeground = null
            backgroundPids = emptySet()
            outcome = ProcessOutcome.NOT_CHECKED
            limitNote = null
            phase = Phase.WAITING_FOR_GAME
            pids.reset()
        }
        policyVerified = sample.policyVerified
        if (!sample.permissionsReady) {
            return build(sample, Blocker.PERMISSIONS)
        }
        if (!sample.foregroundKnown || sample.foregroundPackage.isNullOrBlank()) {
            if (phase == Phase.INACTIVE) phase = Phase.WAITING_FOR_GAME
            ingestPids(sample, force = false)
            return build(sample)
        }

        val foreground = sample.foregroundPackage
        val selected = sample.selectedPackage
        val wasGame = lastForeground == selected
        val isGame = foreground == selected
        val returning = isGame && !wasGame && sawGame && phase != Phase.GAME_FOREGROUND && phase != Phase.INACTIVE
        val event = ingestPids(sample, force = returning || (wasGame && !isGame))
        applyPidEvent(event)

        if (isGame) {
            if (returning) {
                phase = Phase.GAME_RETURNED
                applyReturnVerdict(sample.pidsChecked)
            } else if (phase != Phase.GAME_RETURNED) {
                phase = Phase.GAME_FOREGROUND
            }
            sawGame = true
        } else if (wasGame) {
            backgroundPids = pids.confirmed
            phase = backgroundPhase(sample.policyVerified)
            applyBackgroundVerdict(sample.pidsChecked)
        } else if (phase == Phase.GAME_BACKGROUND || phase == Phase.BACKGROUND_MANAGEMENT_ACTIVE) {
            phase = backgroundPhase(sample.policyVerified)
            if (event is PidEvent.Lost) {
                outcome = ProcessOutcome.TERMINATED
                limitNote = ProcessNotes.TERMINATED
            } else if (event is PidEvent.Replaced) {
                backgroundPids = pids.confirmed
                outcome = ProcessOutcome.RESTARTED_NEW_PID
                limitNote = ProcessNotes.PID_FOLLOWED
            } else if (pids.confirmed.isNotEmpty()) {
                backgroundPids = pids.confirmed
            }
        } else {
            phase = Phase.WAITING_FOR_GAME
        }

        lastForeground = foreground
        return build(sample)
    }

    private fun ingestPids(sample: Sample, force: Boolean): PidEvent {
        if (!sample.pidsChecked) return PidEvent.Unchecked(pids.confirmed)
        val incoming = sample.selectedPids ?: return PidEvent.Unchecked(pids.confirmed)
        return if (force) pids.force(incoming) else pids.ingest(incoming)
    }

    private fun applyPidEvent(event: PidEvent) {
        pidChange = PidText.describe(event)
        when (event) {
            is PidEvent.Replaced -> {
                outcome = ProcessOutcome.RESTARTED_NEW_PID
                limitNote = ProcessNotes.PID_FOLLOWED
            }
            is PidEvent.Lost -> {
                outcome = ProcessOutcome.TERMINATED
                limitNote = ProcessNotes.TERMINATED
            }
            is PidEvent.Updated, is PidEvent.Appeared -> {
                if (phase == Phase.GAME_BACKGROUND || phase == Phase.BACKGROUND_MANAGEMENT_ACTIVE) {
                    if (outcome != ProcessOutcome.RESTARTED_NEW_PID) {
                        outcome = ProcessOutcome.STILL_IN_MEMORY
                    }
                }
            }
            is PidEvent.Unchanged, is PidEvent.Unchecked -> Unit
        }
    }

    private fun backgroundPhase(verified: Boolean): Phase =
        if (verified) Phase.BACKGROUND_MANAGEMENT_ACTIVE else Phase.GAME_BACKGROUND

    private fun applyReturnVerdict(checked: Boolean) {
        if (!checked) {
            outcome = ProcessOutcome.UNKNOWN_NO_PRIVILEGE
            limitNote = ProcessNotes.UNKNOWN
            return
        }
        val current = pids.confirmed
        outcome = ProcessVerdict.compare(backgroundPids, current)
        limitNote = when (outcome) {
            ProcessOutcome.RESTARTED_NEW_PID -> ProcessNotes.PID_FOLLOWED
            else -> ProcessNotes.forOutcome(outcome)
        }
    }

    private fun applyBackgroundVerdict(checked: Boolean) {
        if (!checked) {
            outcome = ProcessOutcome.UNKNOWN_NO_PRIVILEGE
            limitNote = ProcessNotes.UNKNOWN
            return
        }
        if (pids.confirmed.isEmpty()) {
            outcome = ProcessOutcome.NOT_RUNNING
            limitNote = ProcessNotes.NOT_RUNNING_ON_LEAVE
        } else if (outcome != ProcessOutcome.RESTARTED_NEW_PID) {
            outcome = ProcessOutcome.STILL_IN_MEMORY
            limitNote = ProcessNotes.ALIVE_ON_LEAVE
        }
    }

    private fun build(sample: Sample, blocker: Blocker = Blocker.NONE): EngineSnapshot = EngineSnapshot(
        phase = phase,
        blocker = blocker,
        outcome = outcome,
        limitNote = limitNote,
        foregroundPackage = sample.foregroundPackage,
        selectedPackage = sample.selectedPackage,
        policyVerified = policyVerified,
        backgroundPids = backgroundPids,
        observedPids = pids.confirmed,
        pidChange = pidChange,
    )
}
