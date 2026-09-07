package ai.closepaw.agent

import ai.closepaw.trace.RuntimeEvent
import ai.closepaw.trace.RuntimeEventBus
import kotlinx.coroutines.CancellationException

/**
 * Single choke point for HMX task execution (Phase 2).
 *
 * Flow: task_started → planning_started → planning_completed|planning_failed →
 * execution_started → execution_completed|execution_failed.
 *
 * Fallback semantics (no infinite loop by construction):
 * - Planner throws → proceed to the single legacy [execute] call with a fallback plan.
 * - [execute] throws → return [AgentStopReason.Error]; [execute] is invoked at most once.
 */
class TaskOrchestrator(
    private val planner: Planner = DefaultPlanner(),
    private val eventBus: RuntimeEventBus? = null,
) {
    suspend fun orchestrate(
        input: HmxTaskInput,
        execute: suspend () -> AgentStopReason,
    ): AgentStopReason {
        emit("task_started", input)
        val plan = try {
            emit("planning_started", input)
            planner.plan(input).also { emit("planning_completed", input) }
        } catch (e: CancellationException) {
            // Cancellation is not a failure: propagate so the caller's
            // CancellationException handler (e.g. SessionAgentRunner's
            // user-interrupt path) keeps working.
            emit("planning_cancelled", input)
            throw e
        } catch (e: Exception) {
            emit("planning_failed", input, e.message)
            HmxPlan.fallback(input.goal)
        }
        emit("execution_started", input)
        return try {
            execute().also { emit("execution_completed", input) }
        } catch (e: CancellationException) {
            emit("execution_cancelled", input)
            throw e
        } catch (e: Exception) {
            emit("execution_failed", input, e.message ?: e::class.java.simpleName)
            AgentStopReason.Error("HMX execution failed for '${plan.goal}': ${e.message}")
        }
    }

    private fun emit(type: String, input: HmxTaskInput, message: String? = null) {
        runCatching {
            eventBus?.emit(
                RuntimeEvent(
                    type = type,
                    sessionId = input.sessionId,
                    taskId = input.taskId,
                    message = message ?: input.goal,
                )
            )
        }
    }
}
