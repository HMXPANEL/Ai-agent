package ai.closepaw.agent

import ai.closepaw.agent.cognition.policy.TurnToolPolicy
import ai.closepaw.agent.cognition.policy.ToolArbitrationResult
import ai.closepaw.tool.ToolCallResult
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * P5/P6/P12: tool success must not imply task success.
 * - actuation + unverified output + complete_task(success) → blocked Error.
 * - informational turns complete freely; verified actuation completes.
 * - cross-turn: prior unresolved actuation blocks later success claims.
 */
class TaskVerificationGateTest {

    private val policy = TurnToolPolicy()

    private fun toolCall(name: String, args: JSONObject = JSONObject()): ToolCallRequest {
        // Mirrors TurnOutcomeDecisionTest helper shape (id must be unique per call).
        return ToolCallRequest(
            id = "call_${name}_${System.nanoTime()}",
            name = name,
            arguments = args
        )
    }

    private fun successExecution(vararg tools: Pair<String, String>): ExecutionPhaseResult {
        val ids = tools.mapIndexed { i, _ -> "id_$i" }.toSet()
        return ExecutionPhaseResult(
            executedToolIds = ids,
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = tools.map { (name, output) ->
                ExecutedTool(name = name, output = output, failed = false)
            }
        )
    }

    private fun completeTurn(vararg calls: ToolCallRequest): Triple<TurnResult, ToolArbitrationResult, List<ToolCallRequest>> {
        val list = calls.toList()
        val arbitration = policy.arbitrateToolCalls(list)
        val turnResult = TurnResult(content = null, toolCalls = list, isComplete = true)
        return Triple(turnResult, arbitration, list)
    }

    @Test
    fun `unverified actuation blocks success completion`() {
        // Reachable shape: completion planned+executed while the execution
        // record carries an unverified actuation (defense in depth — the turn
        // runner normally separates these across turns, see tracker tests).
        val complete = toolCall("complete_task", JSONObject("""{"answer":"sent"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = ExecutionPhaseResult(
            executedToolIds = setOf(complete.id),
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = listOf(
                ExecutedTool("mobile_action", "Success: typed [unverified]", failed = false),
                ExecutedTool("complete_task", "Success: Task completed successfully.", failed = false)
            )
        )

        val outcome = decideTurnOutcome(policy, turnResult, arbitration, execution)

        assertThat(outcome).isInstanceOf(TurnOutcome.Error::class.java)
        assertThat((outcome as TurnOutcome.Error).message).contains("OUTCOME_NOT_VERIFIED")
        assertThat(outcome.recoverable).isTrue()
    }

    @Test
    fun `verified actuation allows success completion`() {
        val complete = toolCall("complete_task", JSONObject("""{"answer":"sent"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = ExecutionPhaseResult(
            executedToolIds = setOf(complete.id),
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = listOf(
                ExecutedTool("mobile_action", "Success: Typed into element", failed = false),
                ExecutedTool("complete_task", "Success: Task completed successfully.", failed = false)
            )
        )

        val outcome = decideTurnOutcome(policy, turnResult, arbitration, execution)

        assertThat(outcome).isInstanceOf(TurnOutcome.Complete::class.java)
        assertThat((outcome as TurnOutcome.Complete).success).isTrue()
    }

    @Test
    fun `informational turn completes without verification`() {
        val complete = toolCall("complete_task", JSONObject("""{"answer":"42"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = successExecution(
            "complete_task" to "Success: Task completed successfully."
        ).copy(executedToolIds = setOf(complete.id))

        val outcome = decideTurnOutcome(policy, turnResult, arbitration, execution)

        assertThat(outcome).isInstanceOf(TurnOutcome.Complete::class.java)
    }

    @Test
    fun `failed actuation blocks success completion`() {
        val complete = toolCall("complete_task", JSONObject("""{"answer":"sent"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = ExecutionPhaseResult(
            executedToolIds = setOf(complete.id),
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = listOf(
                ExecutedTool("mobile_action", "Success: typed", failed = true),
                ExecutedTool("complete_task", "Success.", failed = false)
            )
        )

        val outcome = decideTurnOutcome(policy, turnResult, arbitration, execution)

        assertThat(outcome).isInstanceOf(TurnOutcome.Error::class.java)
    }

    @Test
    fun `failure completion is not blocked by unverified actuation`() {
        val complete = toolCall("complete_task", JSONObject("""{"status":"failure","answer":"blocked"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = ExecutionPhaseResult(
            executedToolIds = setOf(complete.id),
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = listOf(
                ExecutedTool("mobile_action", "Success: typed [unverified]", failed = false),
                ExecutedTool("complete_task", "Task failed.", failed = false)
            )
        )

        val outcome = decideTurnOutcome(policy, turnResult, arbitration, execution)

        // Reporting failure is always allowed — the gate only stops FALSE success.
        assertThat(outcome).isInstanceOf(TurnOutcome.Complete::class.java)
        assertThat((outcome as TurnOutcome.Complete).success).isFalse()
    }

    @Test
    fun `prior unresolved actuation blocks later success claim`() {
        val complete = toolCall("complete_task", JSONObject("""{"answer":"sent"}"""))
        val (turnResult, arbitration, _) = completeTurn(complete)
        val execution = successExecution(
            "complete_task" to "Success: Task completed successfully."
        ).copy(executedToolIds = setOf(complete.id))

        val outcome = decideTurnOutcome(
            policy, turnResult, arbitration, execution,
            priorUnresolvedActuation = true
        )

        assertThat(outcome).isInstanceOf(TurnOutcome.Error::class.java)
        assertThat((outcome as TurnOutcome.Error).message).contains("OUTCOME_NOT_VERIFIED")
    }

    @Test
    fun `policy assessment matrix`() {
        val a = TaskVerification::assess
        assertThat(a(emptyList(), emptyList(), false))
            .isEqualTo(TaskVerification.Assessment.NOT_REQUIRED)
        assertThat(a(listOf("mobile_action"), listOf("Success: ok"), false))
            .isEqualTo(TaskVerification.Assessment.REQUIRED_AND_VERIFIED)
        assertThat(a(listOf("mobile_action"), listOf("Success: ok [unverified]"), false))
            .isEqualTo(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
        assertThat(a(listOf("browser_script"), listOf("Success: ok"), true))
            .isEqualTo(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
        assertThat(a(listOf("complete_task"), listOf("Success."), false))
            .isEqualTo(TaskVerification.Assessment.NOT_REQUIRED)
    }

    @Test
    fun `blocksUnverifiedCompletion helper matrix`() {
        fun exec(
            tools: List<ExecutedTool>,
            verification: TurnVerificationResult? = null
        ) = ExecutionPhaseResult(
            executedToolIds = emptySet(),
            terminatedEarly = false,
            lastTerminalResult = null,
            executedTools = tools,
            verification = verification
        )
        val ok = ExecutedTool("mobile_action", "Success: typed", false)
        val unverified = ExecutedTool("mobile_action", "Success: typed [unverified]", false)
        val failed = ExecutedTool("mobile_action", "Success: typed", true)
        val info = ExecutedTool("complete_task", "Success.", false)

        assertThat(blocksUnverifiedCompletion(exec(emptyList()))).isFalse()
        assertThat(blocksUnverifiedCompletion(exec(listOf(info)))).isFalse()
        assertThat(blocksUnverifiedCompletion(exec(listOf(ok)))).isFalse()
        assertThat(blocksUnverifiedCompletion(exec(listOf(unverified)))).isTrue()
        assertThat(blocksUnverifiedCompletion(exec(listOf(failed)))).isTrue()
        assertThat(
            blocksUnverifiedCompletion(
                exec(
                    listOf(ok),
                    TurnVerificationResult(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
                )
            )
        ).isTrue()
        assertThat(
            blocksUnverifiedCompletion(
                exec(
                    listOf(unverified),
                    TurnVerificationResult(TaskVerification.Assessment.REQUIRED_AND_VERIFIED)
                )
            )
        ).isFalse()
    }
}
