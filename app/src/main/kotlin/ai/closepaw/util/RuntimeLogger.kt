package ai.closepaw.util

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

enum class RuntimeLogLevel(val priority: Int) {
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
}

/** A structured, bounded, failure-isolated runtime logger. */
class RuntimeLogger(
    private val buffer: LogBuffer = LogBuffer(),
    private val filter: SensitiveDataFilter = SensitiveDataFilter(),
    private val sink: DiagnosticLogSink = buffer,
    private val tag: String = "HmxDiagnostics",
    private val logcat: (Int, String, String, Throwable?) -> Unit = { priority, logTag, message, error ->
        Log.println(priority, logTag, message + (error?.let { "\\n${stackTrace(it)}" } ?: ""))
    },
) {
    fun debug(event: String, message: String, fields: Map<String, Any?> = emptyMap()) =
        log(RuntimeLogLevel.DEBUG, event, message, fields)

    fun info(event: String, message: String, fields: Map<String, Any?> = emptyMap()) =
        log(RuntimeLogLevel.INFO, event, message, fields)

    fun warn(event: String, message: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        log(RuntimeLogLevel.WARN, event, message, fields, error)

    fun error(event: String, message: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        log(RuntimeLogLevel.ERROR, event, message, fields, error)

    fun log(
        level: RuntimeLogLevel,
        event: String,
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        // Diagnostics are best effort. Every step, including user-supplied filter/sink/logcat
        // implementations, is isolated so logging can never become an application failure.
        try {
            val safeEvent = safeRedact(event)
            val safeMessage = safeRedact(message)
            val safeFields = fields.entries.joinToString(",") { (key, value) ->
                "${safeRedact(key)}=${safeRedact(value?.toString().orEmpty())}"
            }
            val line = buildString {
                append(System.currentTimeMillis())
                append(" ")
                append(level.name)
                append(" event=")
                append(safeEvent)
                append(" message=")
                append(safeMessage)
                if (safeFields.isNotEmpty()) append(" fields={$safeFields}")
                error?.let { append(" error=").append(safeRedact(stackTrace(it))) }
            }
            runCatching { sink.append(line) }
            runCatching { logcat(level.priority, tag, line, null) }
        } catch (_: Throwable) {
            // Deliberately empty: diagnostics must not affect the application.
        }
    }

    fun snapshot(): List<String> = runCatching { buffer.snapshot() }.getOrDefault(emptyList())

    private fun safeRedact(value: String): String = runCatching { filter.redact(value) }.getOrDefault("[REDACTED]")

    companion object {
        internal fun stackTrace(error: Throwable): String = runCatching {
            StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
        }.getOrDefault(error::class.java.name)
    }
}
