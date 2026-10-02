package app.xkeepalive.core

sealed class PidEvent {
    data class Unchecked(val pids: Set<Int>) : PidEvent()
    data class Unchanged(val pids: Set<Int>) : PidEvent()
    data class Appeared(val pids: Set<Int>) : PidEvent()
    data class Updated(val previous: Set<Int>, val pids: Set<Int>) : PidEvent()
    data class Replaced(val previous: Set<Int>, val pids: Set<Int>) : PidEvent()
    data class Lost(val previous: Set<Int>) : PidEvent()
}

/**
 * Keeps the last confirmed PID. One empty poll does not drop the process:
 * pidof sometimes skips a cycle. A new PID replaces the previous one immediately.
 */
class PidTracker(private val emptyNeeded: Int = 2) {
    var confirmed: Set<Int> = emptySet()
        private set
    private var emptyStreak = 0

    fun reset() {
        confirmed = emptySet()
        emptyStreak = 0
    }

    fun ingest(sample: Set<Int>?): PidEvent {
        if (sample == null) return PidEvent.Unchecked(confirmed)
        if (sample.isEmpty()) {
            emptyStreak++
            if (emptyStreak >= emptyNeeded && confirmed.isNotEmpty()) {
                val previous = confirmed
                confirmed = emptySet()
                return PidEvent.Lost(previous)
            }
            return PidEvent.Unchanged(confirmed)
        }
        emptyStreak = 0
        return accept(sample)
    }

    fun force(sample: Set<Int>): PidEvent {
        emptyStreak = 0
        if (sample.isEmpty()) {
            if (confirmed.isEmpty()) return PidEvent.Unchanged(emptySet())
            val previous = confirmed
            confirmed = emptySet()
            return PidEvent.Lost(previous)
        }
        return accept(sample)
    }

    private fun accept(sample: Set<Int>): PidEvent {
        val previous = confirmed
        if (previous == sample) return PidEvent.Unchanged(sample)
        confirmed = sample
        return when {
            previous.isEmpty() -> PidEvent.Appeared(sample)
            previous.intersect(sample).isEmpty() -> PidEvent.Replaced(previous, sample)
            else -> PidEvent.Updated(previous, sample)
        }
    }
}

object PidText {
    fun format(pids: Set<Int>): String =
        if (pids.isEmpty()) "none" else pids.sorted().joinToString(", ")

    fun describe(event: PidEvent): String? = when (event) {
        is PidEvent.Appeared -> "Current PID: ${format(event.pids)}"
        is PidEvent.Updated ->
            "PID updated: ${format(event.previous)} → ${format(event.pids)}. The process is still in memory. X-KeepAlive follows the active ones."
        is PidEvent.Replaced ->
            "PID changed: ${format(event.previous)} → ${format(event.pids)}. The previous one is gone; the new one is followed from now on."
        is PidEvent.Lost ->
            "PID ${format(event.previous)} disappeared. The process is no longer in memory."
        is PidEvent.Unchanged, is PidEvent.Unchecked -> null
    }
}
