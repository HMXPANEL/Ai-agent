package ai.closepaw.agent

import kotlinx.coroutines.Deferred

/**
 * Minimal execution handle for HMX orchestration.
 *
 * Implemented by [Agent] (via `override` keywords only, no behavior change) so
 * [HmxAgent]/[TaskOrchestrator] stay unit-testable with fakes and never need
 * Android dependencies in tests.
 */
interface HmxAgentExecutor {
    suspend fun run(): AgentStopReason
    suspend fun pause(): Deferred<Unit>
    suspend fun resume()
    fun stop()
}
