package ai.closepaw.trace

import ai.closepaw.protocol.TaskOutcomeState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** P11: typed diagnostics events carry ids/provider/model, never secrets. */
class TaskDiagnosticsTest {

    @Test
    fun `task lifecycle events carry session task provider model`() {
        val bus = RuntimeEventBus(capacity = 16)

        TaskDiagnostics.taskStarted(bus, "s1", "t1", provider = "OTHER", model = "gemini-x")
        TaskDiagnostics.actionExecuted(bus, "s1", "t1", "mobile_action", message = "ok")
        TaskDiagnostics.actionFailed(bus, "s1", "t1", "mobile_action", reason = "nope")
        TaskDiagnostics.verification(
            bus, "s1", "t1", "turn:turn-1",
            TaskOutcomeState.OUTCOME_VERIFIED, "all checks pass"
        )
        TaskDiagnostics.taskFinished(bus, "s1", "t1", completed = true)

        val types = bus.events.value.map { it.type }
        assertThat(types).containsExactly(
            TaskDiagnostics.TASK_STARTED,
            TaskDiagnostics.ACTION_EXECUTED,
            TaskDiagnostics.ACTION_FAILED,
            TaskDiagnostics.VERIFICATION_PASSED,
            TaskDiagnostics.TASK_COMPLETED,
        )
        val first = bus.events.value.first()
        assertThat(first.sessionId).isEqualTo("s1")
        assertThat(first.provider).isEqualTo("OTHER")
        assertThat(first.model).isEqualTo("gemini-x")
    }

    @Test
    fun `unverified maps to verification-failed and task-failed`() {
        val bus = RuntimeEventBus(capacity = 16)

        TaskDiagnostics.verification(
            bus, "s", "t", "a", TaskOutcomeState.OUTCOME_NOT_VERIFIED, "bubble missing"
        )
        TaskDiagnostics.taskFinished(bus, "s", "t", completed = false, message = "blocked")

        assertThat(bus.events.value.map { it.type }).containsExactly(
            TaskDiagnostics.VERIFICATION_FAILED,
            TaskDiagnostics.TASK_FAILED,
        )
    }

    @Test
    fun `null bus is a safe no-op`() {
        TaskDiagnostics.taskStarted(null, "s", "t")
        TaskDiagnostics.actionExecuted(null, "s", "t", "x")
        TaskDiagnostics.taskFinished(null, "s", "t", true)
    }

    @Test
    fun `bus stays bounded`() {
        val bus = RuntimeEventBus(capacity = 4)
        repeat(10) { TaskDiagnostics.taskStarted(bus, "s", "t$it") }

        assertThat(bus.events.value).hasSize(4)
    }
}
