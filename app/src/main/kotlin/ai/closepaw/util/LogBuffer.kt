package ai.closepaw.util

/**
 * Small, thread-safe FIFO buffer for diagnostic log lines.
 *
 * The capacity is fixed at construction time. Adding beyond the capacity drops the oldest
 * entry, so a diagnostic path can never grow memory without bound.
 */
class LogBuffer(val capacity: Int = DEFAULT_CAPACITY) : DiagnosticLogSink {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val lock = Any()
    private val entries = ArrayDeque<String>(capacity)

    override fun append(entry: String) {
        push(entry)
    }

    fun push(entry: String) {
        synchronized(lock) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(entry)
        }
    }

    fun pop(): String? = synchronized(lock) {
        if (entries.isEmpty()) null else entries.removeFirst()
    }

    fun snapshot(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    val size: Int
        get() = synchronized(lock) { entries.size }

    companion object {
        const val DEFAULT_CAPACITY: Int = 50
    }
}

/** Minimal sink abstraction used to make logger failure handling testable. */
fun interface DiagnosticLogSink {
    fun append(entry: String)
}
