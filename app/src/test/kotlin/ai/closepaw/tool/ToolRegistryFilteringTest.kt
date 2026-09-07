package ai.closepaw.tool

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTool(
    override val name: String,
    override val requiredCapabilities: Set<Capability> = emptySet(),
) : ToolSpec {
    override val description: String = "fake tool"
    override val parameterSchema: JSONObject = JSONObject()
    override fun validate(params: JSONObject): ValidationResult = ValidationResult.Valid
    override fun createInvocation(params: JSONObject): ToolInvocation =
        throw UnsupportedOperationException("not needed for filtering tests")
}

class ToolRegistryFilteringTest {
    @Test
    fun getAvailableExcludesToolsWithUnavailableCapabilities() {
        val registry = ToolRegistry()
        registry.register(FakeTool("free_tool"))
        registry.register(FakeTool("a11y_tool", setOf(Capability.ACCESSIBILITY)))
        registry.register(FakeTool("shizuku_tool", setOf(Capability.SHIZUKU)))

        val manager = CapabilityManager(DeviceCapabilitySource {
            mapOf(Capability.ACCESSIBILITY to CapabilityState.AVAILABLE)
        })

        val available = registry.getAvailable(manager).map { it.name }.toSet()
        assertEquals(setOf("free_tool", "a11y_tool"), available)
    }

    @Test
    fun unknownCapabilitiesFailClosedWhileGetAllIsUnchanged() {
        val registry = ToolRegistry()
        registry.register(FakeTool("a11y_tool", setOf(Capability.ACCESSIBILITY)))

        val available = registry.getAvailable(CapabilityManager()).map { it.name }

        assertTrue(available.isEmpty())
        assertEquals(1, registry.getAll().size)
    }
}
