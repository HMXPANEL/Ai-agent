package ai.closepaw.util

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

data class CrashReport(
    val timestampMs: Long,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val recentLogs: List<String>,
    val sessionId: String? = null,
    val taskId: String? = null,
    val currentScreen: String? = null,
    val currentTool: String? = null,
) {
    fun asText(filter: SensitiveDataFilter = SensitiveDataFilter()): String = buildString {
        appendLine("HMX crash report")
        appendLine("timestamp_ms=$timestampMs")
        appendLine("exception=${filter.redact(exceptionType)}")
        appendLine("message=${filter.redact(message)}")
        sessionId?.let { appendLine("session_id=${filter.redact(it)}") }
        taskId?.let { appendLine("task_id=${filter.redact(it)}") }
        currentScreen?.let { appendLine("screen=${filter.redact(it)}") }
        currentTool?.let { appendLine("tool=${filter.redact(it)}") }
        appendLine("stack_trace")
        appendLine(filter.redact(stackTrace))
        appendLine("recent_logs")
        recentLogs.forEach { appendLine(filter.redact(it)) }
    }
}

/** Bounded app-private crash report persistence with simple file rotation. */
class CrashReportStore(
    private val directory: File,
    private val maxReports: Int = DEFAULT_MAX_REPORTS,
    private val filter: SensitiveDataFilter = SensitiveDataFilter(),
) {
    init {
        require(maxReports > 0) { "maxReports must be positive" }
    }

    constructor(context: Context, maxReports: Int = DEFAULT_MAX_REPORTS) : this(
        File(context.applicationContext.filesDir, DIRECTORY_NAME),
        maxReports,
    )

    fun save(report: CrashReport): File? = runCatching {
        if (!directory.exists() && !directory.mkdirs()) return@runCatching null
        val safeText = filter.redact(report.asText(filter)).take(MAX_REPORT_CHARS)
        val file = File(directory, "crash_${report.timestampMs}_${System.nanoTime()}.log")
        val temp = File(directory, ".${file.name}.tmp")
        temp.writeText(safeText, StandardCharsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.delete()
            return@runCatching null
        }
        rotate()
        file
    }.getOrNull()

    fun list(): List<File> = runCatching {
        directory.listFiles { file -> file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun rotate() {
        list().drop(maxReports).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val DEFAULT_MAX_REPORTS: Int = 10
        private const val DIRECTORY_NAME = "diagnostics/crashes"
        private const val MAX_REPORT_CHARS = 64 * 1024
    }
}
