package ai.closepaw.util

import android.content.Context
import ai.closepaw.protocol.ActionExecuted
import ai.closepaw.protocol.AgentEvent
import ai.closepaw.protocol.SessionError
import ai.closepaw.protocol.SessionStarted
import ai.closepaw.protocol.TaskStarted
import ai.closepaw.protocol.TaskCompleted
import ai.closepaw.trace.RuntimeEvent
import ai.closepaw.trace.RuntimeEventBus
import ai.closepaw.ui.DiagnosticOverlayController
import ai.closepaw.protocol.ActionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-scoped diagnostics facade used by DI and the service event collector. */
class HmxDiagnostics private constructor(
    val logger: RuntimeLogger,
    val eventBus: RuntimeEventBus,
    val crashReportStore: CrashReportStore?,
    private val crashHandler: CrashHandler?,
) {
    private val _latestCrash = MutableStateFlow<CrashReport?>(null)
    val latestCrash: StateFlow<CrashReport?> = _latestCrash.asStateFlow()
    @Volatile private var context = DiagnosticContext()

    fun installCrashHandler() {
        runCatching { crashHandler?.let { if (Thread.getDefaultUncaughtExceptionHandler() !== it) Thread.setDefaultUncaughtExceptionHandler(it) } }
    }

    fun updateContext(value: DiagnosticContext) {
        context = value
    }

    fun onAgentEvent(event: AgentEvent) {
        when (event) {
            is SessionStarted -> agentStarted(event.sessionId.value, event.goal)
            is TaskStarted -> eventBus.emit(RuntimeEvent("task_started", event.timestamp, event.sessionId.value, event.taskId, message = event.input))
            is ActionExecuted -> toolExecution(event.sessionId.value, event.toolName, event.outcome.name, event.result)
            is SessionError -> failure(event.sessionId.value, event.message)
            is TaskCompleted -> if (event.outcome != ai.closepaw.protocol.TaskOutcome.GOAL_ACHIEVED) failure(event.sessionId.value, event.result.orEmpty())
            else -> Unit
        }
    }

    fun agentStarted(sessionId: String?, goal: String?) {
        eventBus.emit(RuntimeEvent("agent_started", sessionId = sessionId, message = goal))
        logger.info("agent_started", goal.orEmpty(), mapOf("session_id" to sessionId))
        context = context.copy(sessionId = sessionId)
    }

    fun toolExecution(sessionId: String?, toolName: String?, outcome: String?, message: String? = null) {
        eventBus.emit(RuntimeEvent("tool_execution", sessionId = sessionId, toolName = toolName, outcome = outcome, message = message))
        logger.info("tool_execution", message.orEmpty(), mapOf("session_id" to sessionId, "tool" to toolName, "outcome" to outcome))
        context = context.copy(sessionId = sessionId, currentTool = toolName)
    }

    fun failure(sessionId: String?, message: String?, error: Throwable? = null) {
        eventBus.emit(RuntimeEvent("error", sessionId = sessionId, message = message))
        logger.error("error", message.orEmpty(), mapOf("session_id" to sessionId), error)
        context = context.copy(sessionId = sessionId)
    }

    fun close() {
        runCatching { crashHandler?.restoreIfInstalled() }
    }

    companion object {
        fun create(context: Context, enabled: Boolean): HmxDiagnostics {
            val buffer = LogBuffer()
            val logger = RuntimeLogger(buffer = buffer)
            val store = CrashReportStore(context)
            lateinit var diagnostics: HmxDiagnostics
            val overlay = DiagnosticOverlayController(context, logger, store)
            val handler = CrashHandler(
                logger = logger,
                reportStore = store,
                contextProvider = { diagnostics.contextSnapshot() },
                reportListener = {
                    diagnostics._latestCrash.value = it
                    overlay.show(it)
                },
            )
            diagnostics = HmxDiagnostics(logger, RuntimeEventBus(), store, handler)
            if (enabled) diagnostics.installCrashHandler()
            return diagnostics
        }

        fun disabled(): HmxDiagnostics = HmxDiagnostics(
            logger = RuntimeLogger(logcat = { _, _, _, _ -> }),
            eventBus = RuntimeEventBus(),
            crashReportStore = null,
            crashHandler = null,
        )
    }

    private fun contextSnapshot(): DiagnosticContext = context
}
