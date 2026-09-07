package ai.closepaw.agent

import ai.closepaw.trace.RuntimeEventBus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrchestratorTest {
    @Test
    fun happyPathPlansThenExecutesOnceInOrder(): Unit = runBlocking {
        val bus = RuntimeEventBus()
        val orchestrator = TaskOrchestrator(eventBus = bus)
        var executions = 0

        val result = orchestrator.orchestrate(HmxTaskInput("goal", "s", "t")) {
            executions++
            AgentStopReason.GoalAchieved("ok")
        }

        assertEquals(AgentStopReason.GoalAchieved("ok"), result)
        assertEquals(1, executions)
        assertEquals(
            listOf("task_started", "planning_started", "planning_completed", "execution_started", "execution_completed"),
            bus.events.value.map { it.type },
        )
    }

    @Test
    fun plannerFailureFallsBackToSingleExecution(): Unit = runBlocking {
        val bus = RuntimeEventBus()
        val orchestrator = TaskOrchestrator(planner = object : Planner {
            override suspend fun plan(input: HmxTaskInput): HmxPlan = throw IllegalStateException("no plan")
        }, eventBus = bus)
        var executions = 0

        val result = orchestrator.orchestrate(HmxTaskInput("goal")) {
            executions++
            AgentStopReason.TaskImpossible("nope")
        }

        assertEquals(AgentStopReason.TaskImpossible("nope"), result)
        assertEquals(1, executions)
        assertTrue(bus.events.value.map { it.type }.contains("planning_failed"))
    }

    @Test
    fun executionFailureReturnsErrorAndNeverRetries(): Unit = runBlocking {
        val orchestrator = TaskOrchestrator(eventBus = null)
        var executions = 0

        val result = orchestrator.orchestrate(HmxTaskInput("goal")) {
            executions++
            throw IllegalStateException("boom")
        }

        assertTrue(result is AgentStopReason.Error)
        assertEquals(1, executions)
    }

    @Test
    fun worksWithoutEventBus(): Unit = runBlocking {
        val orchestrator = TaskOrchestrator(eventBus = null)

        val result = orchestrator.orchestrate(HmxTaskInput("goal")) { AgentStopReason.UserRequested }

        assertEquals(AgentStopReason.UserRequested, result)
    }
}
