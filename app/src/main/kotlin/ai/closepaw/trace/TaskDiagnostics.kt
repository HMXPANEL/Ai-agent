package ai.closepaw.trace

import ai.closepaw.protocol.TaskOutcomeState

/**
 * Structured per-task runtime events (P11).
 *
 * Every task should emit this lifecycle so behavior is observable without logcat archaeology:
 * task_started → planning_started → observation_taken → action_requested → action_executed /
 * action_failed → verification_started → verification_passed / verification_failed →
 * task_completed / task_failed.
 *
 * Secrets policy: only ids, names, and short summaries travel here — never API keys,
 * OAuth tokens, passwords, or full message bodies.
 */
object TaskDiagnostics {

    const val TASK_STARTED = "task_started"
    const val PLANNING_STARTED = "planning_started"
    const val OBSERVATION_TAKEN = "observation_taken"
    const val ACTION_REQUESTED = "action_requested"
    const val ACTION_EXECUTED = "action_executed"
    const val ACTION_FAILED = "action_failed"
    const val VERIFICATION_STARTED = "verification_started"
    const val VERIFICATION_PASSED = "verification_passed"
    const val VERIFICATION_FAILED = "verification_failed"
    const val TASK_COMPLETED = "task_completed"
    const val TASK_FAILED = "task_failed"

    fun taskStarted(
        bus: RuntimeEventBus?,
        sessionId: String?,
        taskId: String?,
        provider: String? = null,
        model: String? = null,
        message: String? = null,
    ) = bus?.emit(
        RuntimeEvent(
            type = TASK_STARTED,
            sessionId = sessionId,
            taskId = taskId,
            provider = provider,
            model = model,
            message = message?.take(200),
        )
    )

    fun actionExecuted(
        bus: RuntimeEventBus?,
        sessionId: String?,
        taskId: String?,
        toolName: String?,
        action: String? = null,
        message: String? = null,
    ) = bus?.emit(
        RuntimeEvent(
            type = ACTION_EXECUTED,
            sessionId = sessionId,
            taskId = taskId,
            toolName = toolName,
            action = action,
            message = message?.take(200),
        )
    )

    fun actionFailed(
        bus: RuntimeEventBus?,
        sessionId: String?,
        taskId: String?,
        toolName: String?,
        reason: String?,
    ) = bus?.emit(
        RuntimeEvent(
            type = ACTION_FAILED,
            sessionId = sessionId,
            taskId = taskId,
            toolName = toolName,
            message = reason?.take(200),
            outcome = TaskOutcomeState.ACTION_FAILED.name,
        )
    )

    fun verification(
        bus: RuntimeEventBus?,
        sessionId: String?,
        taskId: String?,
        action: String?,
        state: TaskOutcomeState,
        result: String?,
    ) = bus?.emit(
        RuntimeEvent(
            type = when (state) {
                TaskOutcomeState.OUTCOME_VERIFIED -> VERIFICATION_PASSED
                TaskOutcomeState.OUTCOME_VERIFICATION_STARTED -> VERIFICATION_STARTED
                else -> VERIFICATION_FAILED
            },
            sessionId = sessionId,
            taskId = taskId,
            action = action,
            verification = state.name,
            result = result?.take(200),
        )
    )

    fun taskFinished(
        bus: RuntimeEventBus?,
        sessionId: String?,
        taskId: String?,
        completed: Boolean,
        message: String? = null,
    ) = bus?.emit(
        RuntimeEvent(
            type = if (completed) TASK_COMPLETED else TASK_FAILED,
            sessionId = sessionId,
            taskId = taskId,
            message = message?.take(200),
            outcome = if (completed) {
                TaskOutcomeState.TASK_COMPLETED.name
            } else {
                TaskOutcomeState.TASK_FAILED.name
            },
        )
    )
}
