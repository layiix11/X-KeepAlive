package app.xkeepalive.data

import app.xkeepalive.core.Blocker
import app.xkeepalive.core.Phase
import app.xkeepalive.core.PolicyReport
import app.xkeepalive.core.ProcessOutcome
import app.xkeepalive.permissions.PermissionSnapshot
import app.xkeepalive.shizuku.ShizukuSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionUi(
    val phase: Phase = Phase.INACTIVE,
    val blocker: Blocker = Blocker.NONE,
    val outcome: ProcessOutcome = ProcessOutcome.NOT_CHECKED,
    val limitNote: String? = null,
    val foregroundPackage: String? = null,
    val policyVerified: Boolean = false,
    val backgroundPids: Set<Int> = emptySet(),
    val observedPids: Set<Int> = emptySet(),
    val pidChange: String? = null,
    val policy: PolicyReport? = null,
    val shizuku: ShizukuSnapshot = ShizukuSnapshot(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val serviceRunning: Boolean = false,
)

class SessionRepository {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SessionUi())
    val state: StateFlow<SessionUi> = _state.asStateFlow()

    suspend fun update(block: (SessionUi) -> SessionUi) {
        mutex.withLock {
            _state.value = block(_state.value)
        }
    }
}
