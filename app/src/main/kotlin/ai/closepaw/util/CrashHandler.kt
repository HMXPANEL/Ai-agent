package ai.closepaw.util

import java.util.concurrent.atomic.AtomicBoolean

data class DiagnosticContext(
    val sessionId: String? = null,
    val taskId: String? = null,
    val currentScreen: String? = null,
    val currentTool: String? = null,
)

/**
 * Crash reporter that performs a small bounded capture and then delegates to Android's prior
 * uncaught-exception handler. The handler is deliberately defensive because it runs during a
 * process failure and must never mask the original exception.
 */
class CrashHandler(
    private val logger: RuntimeLogger,
    private val reportStore: CrashReportStore?,
    private val contextProvider: () -> DiagnosticContext = { DiagnosticContext() },
    private val previousHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
    private val reportListener: ((CrashReport) -> Unit)? = null,
) : Thread.UncaughtExceptionHandler {
    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        if (handling.compareAndSet(false, true)) {
            runCatching {
                val context = runCatching { contextProvider() }.getOrDefault(DiagnosticContext())
                val report = CrashReport(
                    timestampMs = System.currentTimeMillis(),
                    exceptionType = error::class.qualifiedName ?: "Throwable",
                    message = error.message.orEmpty(),
                    stackTrace = RuntimeLogger.stackTrace(error),
                    recentLogs = logger.snapshot().takeLast(LogBuffer.DEFAULT_CAPACITY),
                    sessionId = context.sessionId,
                    taskId = context.taskId,
                    currentScreen = context.currentScreen,
                    currentTool = context.currentTool,
                )
                runCatching { reportStore?.save(report) }
                runCatching { reportListener?.invoke(report) }
            }
        }
        // Always preserve the prior/default handler. Its behavior is outside this diagnostics
        // layer and may terminate the process or show the platform crash UI.
        runCatching { previousHandler?.uncaughtException(thread, error) }
    }

    fun restoreIfInstalled() {
        runCatching {
            if (Thread.getDefaultUncaughtExceptionHandler() === this) {
                Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            }
        }
    }
}
