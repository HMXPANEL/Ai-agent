package ai.closepaw.agent

import ai.closepaw.trace.RuntimeEventBus
import kotlinx.coroutines.Deferred

/**
 * Unified HMX agent brain (Phase 2). Owns an [HmxAgentExecutor] (the existing [Agent]
 * in production, a fake in tests) and coordinates it through [TaskOrchestrator].
 *
 * Lifecycle/pause/resume/stop delegate directly to the executor, so the legacy path
 * keeps working unchanged. [run] is the only orchestrated entry point.
 */
class HmxAgent(
    private val executor: HmxAgentExecutor,
    private val orchestrator: TaskOrchestrator = TaskOrchestrator(),
    private val goal: String = "",
    private val sessionId: String? = null,
    private val taskId: String? = null,
) {
    constructor(
        executor: HmxAgentExecutor,
        eventBus: RuntimeEventBus?,
        goal: String = "",
        sessionId: String? = null,
        taskId: String? = null,
    ) : this(executor, TaskOrchestrator(eventBus = eventBus), goal, sessionId, taskId)

    suspend fun run(): AgentStopReason =
        orchestrator.orchestrate(
            HmxTaskInput(goal = goal, sessionId = sessionId, taskId = taskId),
            execute = { executor.run() },
        )

    suspend fun pause(): Deferred<Unit> = executor.pause()

    suspend fun resume() = executor.resume()

    fun stop() = executor.stop()
}
