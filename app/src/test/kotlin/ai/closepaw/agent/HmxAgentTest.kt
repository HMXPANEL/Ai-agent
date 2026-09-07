package ai.closepaw.agent

import ai.closepaw.trace.RuntimeEventBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeExecutor(
    var result: AgentStopReason = AgentStopReason.GoalAchieved("done"),
    var failure: Throwable? = null,
) : HmxAgentExecutor {
    var runs = 0
    var pauses = 0
    var resumes = 0
    var stops = 0

    override suspend fun run(): AgentStopReason {
        runs++
        failure?.let { throw it }
        return result
    }

    override suspend fun pause(): Deferred<Unit> {
        pauses++
        return CompletableDeferred(Unit)
    }

    override suspend fun resume() {
        resumes++
    }

    override fun stop() {
        stops++
    }
}

private class FailingPlanner : Planner {
    override suspend fun plan(input: HmxTaskInput): HmxPlan = throw IllegalStateException("no plan")
}

class HmxAgentTest {
    @Test
    fun runDelegatesToExecutorOnceAndEmitsOrchestrationEvents(): Unit = runBlocking {
        val executor = FakeExecutor()
        val bus = RuntimeEventBus()
        val agent = HmxAgent(executor, bus, goal = "goal", sessionId = "s", taskId = "t")

        val result = agent.run()

        assertEquals(AgentStopReason.GoalAchieved("done"), result)
        assertEquals(1, executor.runs)
        assertEquals(
            listOf("task_started", "planning_started", "planning_completed", "execution_started", "execution_completed"),
            bus.events.value.map { it.type },
        )
    }

    @Test
    fun pauseResumeStopDelegateDirectlyToExecutor(): Unit = runBlocking {
        val executor = FakeExecutor()
        val agent = HmxAgent(executor, null)

        agent.pause()
        agent.resume()
        agent.stop()

        assertEquals(1, executor.pauses)
        assertEquals(1, executor.resumes)
        assertEquals(1, executor.stops)
        assertEquals(0, executor.runs)
    }

    @Test
    fun plannerFailureStillExecutesLegacyPathExactlyOnce(): Unit = runBlocking {
        val executor = FakeExecutor()
        val bus = RuntimeEventBus()
        val agent = HmxAgent(
            executor,
            TaskOrchestrator(planner = FailingPlanner(), eventBus = bus),
            goal = "goal",
        )

        val result = agent.run()

        assertEquals(AgentStopReason.GoalAchieved("done"), result)
        assertEquals(1, executor.runs)
        assertTrue(bus.events.value.map { it.type }.contains("planning_failed"))
    }

    @Test
    fun executorFailureReturnsErrorWithoutLoopOrThrow(): Unit = runBlocking {
        val executor = FakeExecutor(failure = IllegalStateException("boom"))
        val agent = HmxAgent(executor, null)

        val result = agent.run()

        assertTrue(result is AgentStopReason.Error)
        assertEquals(1, executor.runs)
    }
}
