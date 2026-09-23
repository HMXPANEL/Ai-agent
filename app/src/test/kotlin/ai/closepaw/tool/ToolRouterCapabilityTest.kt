package ai.closepaw.tool

import ai.closepaw.protocol.ApprovalDecision
import ai.closepaw.protocol.ApprovalMode
import ai.closepaw.test.FakeAndroidPlatform
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * Execution-time capability gate ([ToolRouter] + [CapabilityManager]):
 * fail-closed re-check on the live source immediately before EXECUTING.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolRouterCapabilityTest {

    private class GatedToolSpec(
        // Real category names drive PolicyEngine: "scratchpad" is non-screen-changing
        // (always Allow, no approval); "mobile_action" is screen-changing (asks).
        override val name: String = "scratchpad",
        override val requiredCapabilities: Set<Capability> = setOf(Capability.ACCESSIBILITY),
    ) : ToolSpec {
        var executed = false
        override val description = "Capability-gated test tool"
        override val parameterSchema: JSONObject = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject())
            put("required", JSONArray())
            put("additionalProperties", false)
        }

        override fun validate(params: JSONObject): ValidationResult = ValidationResult.Valid

        override fun createInvocation(params: JSONObject): ToolInvocation {
            return object : ToolInvocation {
                override val toolName: String = name
                override val params: JSONObject = params
                override fun getDescription(): String = "gated invocation"
                override suspend fun execute(context: ToolExecutionContext): ToolExecutionResult {
                    executed = true
                    return ToolExecutionResult.Success("ok")
                }
            }
        }
    }

    private val states = mutableMapOf<Capability, CapabilityState>()
    private fun manager() = CapabilityManager(DeviceCapabilitySource { states.toMap() })
    private fun routerWith(spec: GatedToolSpec, mode: ApprovalMode = ApprovalMode.SMART) =
        ToolRouter(
            ToolRegistry().apply { register(spec) },
            PolicyEngine(mode, AppClassifier(emptyMap())),
            manager(),
        )

    private fun context() = SimpleToolRouterContext(FakeAndroidPlatform())

    @Test
    fun `capability available executes tool`() = runTest {
        states[Capability.ACCESSIBILITY] = CapabilityState.AVAILABLE
        val spec = GatedToolSpec()

        val result = routerWith(spec).execute("scratchpad", JSONObject(), context())

        assertThat(result).isInstanceOf(ToolCallResult.Success::class.java)
        assertThat(spec.executed).isTrue()
    }

    @Test
    fun `capability missing denies without executing`() = runTest {
        val spec = GatedToolSpec()
        val router = routerWith(spec)

        val result = router.execute("scratchpad", JSONObject(), context())

        assertThat(result).isInstanceOf(ToolCallResult.Cancelled::class.java)
        assertThat((result as ToolCallResult.Cancelled).reason).contains("ACCESSIBILITY")
        assertThat(spec.executed).isFalse()
        assertThat(router.getActiveCallIds()).isEmpty()
    }

    @Test
    fun `capability revoked after approval denies`() = runTest {
        states[Capability.ACCESSIBILITY] = CapabilityState.AVAILABLE
        val spec = GatedToolSpec(name = "mobile_action")
        val router = routerWith(spec, ApprovalMode.ALWAYS_ASK)

        val result = router.execute(
            toolName = "mobile_action",
            params = JSONObject(),
            context = context(),
            packageName = "com.example.fake",
            onApprovalRequired = { details ->
                // Capability lost during the approval wait (Shizuku death, Termux kill).
                states.remove(Capability.ACCESSIBILITY)
                router.resolveApproval(details.callId, ApprovalDecision.APPROVED)
            }
        )

        assertThat(result).isInstanceOf(ToolCallResult.Cancelled::class.java)
        assertThat((result as ToolCallResult.Cancelled).reason).contains("Capability unavailable at execution")
        assertThat(spec.executed).isFalse()
    }

    @Test
    fun `degraded capability denies fail-closed`() = runTest {
        states[Capability.ACCESSIBILITY] = CapabilityState.DEGRADED
        val spec = GatedToolSpec()

        val result = routerWith(spec).execute("scratchpad", JSONObject(), context())

        assertThat(result).isInstanceOf(ToolCallResult.Cancelled::class.java)
        assertThat(spec.executed).isFalse()
    }

    @Test
    fun `tool without requirements executes under unknown capabilities`() = runTest {
        val spec = GatedToolSpec(name = "plain_tool", requiredCapabilities = emptySet())

        val result = routerWith(spec).execute("plain_tool", JSONObject(), context())

        assertThat(result).isInstanceOf(ToolCallResult.Success::class.java)
        assertThat(spec.executed).isTrue()
    }

    @Test
    fun `null manager preserves legacy behavior`() = runTest {
        val spec = GatedToolSpec()
        val router = ToolRouter(
            ToolRegistry().apply { register(spec) },
            PolicyEngine(ApprovalMode.SMART, AppClassifier(emptyMap())),
            null,
        )

        val result = router.execute("scratchpad", JSONObject(), context())

        assertThat(result).isInstanceOf(ToolCallResult.Success::class.java)
        assertThat(spec.executed).isTrue()
    }
}
