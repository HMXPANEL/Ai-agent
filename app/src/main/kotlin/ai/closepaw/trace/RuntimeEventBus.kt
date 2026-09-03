package ai.closepaw.trace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeEvent(
    val type: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String? = null,
    val taskId: String? = null,
    val toolName: String? = null,
    val message: String? = null,
    val outcome: String? = null,
)

/** Bounded live diagnostics events; persistent TraceRecorder remains the durable trace path. */
class RuntimeEventBus(private val capacity: Int = DEFAULT_CAPACITY) {
    init { require(capacity > 0) { "capacity must be positive" } }
    private val lock = Any()
    private val _events = MutableStateFlow<List<RuntimeEvent>>(emptyList())
    val events: StateFlow<List<RuntimeEvent>> = _events.asStateFlow()
    fun emit(event: RuntimeEvent) {
        runCatching { synchronized(lock) { _events.value = (_events.value + event).takeLast(capacity) } }
    }
    companion object { const val DEFAULT_CAPACITY: Int = 64 }
}
