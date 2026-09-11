package ai.closepaw.agent

import ai.closepaw.llm.LLMClient
import ai.closepaw.llm.LLMStreamEvent
import ai.closepaw.llm.LLMToolCall
import ai.closepaw.llm.ResponsesResult
import ai.closepaw.tool.Capability
import ai.closepaw.tool.CapabilityManager
import ai.closepaw.tool.CapabilityState
import ai.closepaw.tool.DeviceCapabilitySource
import ai.closepaw.tool.ToolExecutionContext
import ai.closepaw.tool.ToolExecutionResult
import ai.closepaw.tool.ToolInvocation
import ai.closepaw.tool.ToolRegistry
import ai.closepaw.tool.ToolSpec
import ai.closepaw.tool.ValidationResult
import com.google.common.truth.Truth.assertThat
import com.openai.models.responses.EasyInputMessage
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * P10/P12: capability-gated tools are withheld from the LLM; ungated tools
 * pass through; UNKNOWN denies declared requirements by default.
 */
class TurnCapabilityFilterTest {

    private val minimalInputItems = listOf(
        ResponseInputItem.ofEasyInputMessage(
            EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content("Screen state (0 elements):\n```json\n[]\n```")
                .build()
        )
    )

    private class GatedTool(
        override val name: String,
        override val requiredCapabilities: Set<Capability>
    ) : ToolSpec {
        override val description: String = "test"
        override val parameterSchema: JSONObject =
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
                put("required", JSONArray())
                put("additionalProperties", false)
            }

        override fun validate(params: JSONObject): ValidationResult = ValidationResult.Valid

        override fun createInvocation(params: JSONObject): ToolInvocation =
            object : ToolInvocation {
                override val toolName: String = name
                override val params: JSONObject = params
                override fun getDescription(): String = "test"
                override suspend fun execute(context: ToolExecutionContext): ToolExecutionResult =
                    ToolExecutionResult.Success("ok")
            }
    }

    private class CapturingClient : LLMClient() {
        var lastToolNames: List<String> = emptyList()
        override suspend fun chatWithTools(
            systemPrompt: String,
            inputItems: List<ResponseInputItem>,
            tools: List<FunctionTool>,
            modelId: String,
            maxOutputTokens: Long?
        ): ResponsesResult {
            lastToolNames = tools.map { it.name() }
            return ResponsesResult(textContent = "done", toolCalls = emptyList(), responseId = "r")
        }

        override fun chatWithToolsStreaming(
            systemPrompt: String,
            inputItems: List<ResponseInputItem>,
            tools: List<FunctionTool>,
            modelId: String
        ): Flow<LLMStreamEvent> = flow { }
    }

    private fun managerOf(vararg states: Pair<Capability, CapabilityState>) =
        CapabilityManager(DeviceCapabilitySource { states.toMap() })

    @Test
    fun `unavailable capability hides declaring tool, keeps others`() = runTest {
        val llm = CapturingClient()
        val registry = ToolRegistry().apply {
            register(GatedTool("mobile_action", emptySet()))
            register(GatedTool("termux_shell", setOf(Capability.TERMUX_SHELL)))
        }
        val turn = Turn(
            toolRegistry = registry,
            llmClient = llm,
            capabilityManager = managerOf(Capability.TERMUX_SHELL to CapabilityState.UNAVAILABLE)
        )

        turn.run(systemPrompt = "planner", inputItems = minimalInputItems)

        assertThat(llm.lastToolNames).containsExactly("mobile_action")
    }

    @Test
    fun `unknown capability denies declaring tool by default`() = runTest {
        val llm = CapturingClient()
        val registry = ToolRegistry().apply {
            register(GatedTool("termux_shell", setOf(Capability.TERMUX_SHELL)))
        }
        // Source reports nothing → UNKNOWN → deny.
        val turn = Turn(
            toolRegistry = registry,
            llmClient = llm,
            capabilityManager = managerOf()
        )

        turn.run(systemPrompt = "planner", inputItems = minimalInputItems)

        assertThat(llm.lastToolNames).isEmpty()
    }

    @Test
    fun `available capability keeps declaring tool`() = runTest {
        val llm = CapturingClient()
        val registry = ToolRegistry().apply {
            register(GatedTool("termux_shell", setOf(Capability.TERMUX_SHELL)))
        }
        val turn = Turn(
            toolRegistry = registry,
            llmClient = llm,
            capabilityManager = managerOf(Capability.TERMUX_SHELL to CapabilityState.AVAILABLE)
        )

        turn.run(systemPrompt = "planner", inputItems = minimalInputItems)

        assertThat(llm.lastToolNames).containsExactly("termux_shell")
    }

    @Test
    fun `null manager preserves legacy allowlist-only behavior`() = runTest {
        val llm = CapturingClient()
        val registry = ToolRegistry().apply {
            register(GatedTool("termux_shell", setOf(Capability.TERMUX_SHELL)))
        }
        val turn = Turn(
            toolRegistry = registry,
            llmClient = llm,
            capabilityManager = null
        )

        turn.run(systemPrompt = "planner", inputItems = minimalInputItems)

        assertThat(llm.lastToolNames).containsExactly("termux_shell")
    }
}
