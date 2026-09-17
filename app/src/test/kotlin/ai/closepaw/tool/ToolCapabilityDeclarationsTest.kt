package ai.closepaw.tool

import ai.closepaw.tool.impl.MobileActionTool
import ai.closepaw.tool.impl.SystemButtonTool
import ai.closepaw.tool.impl.TermuxShellTool
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase 3: tools that cannot execute without a capability declare it, so the
 * LLM is only offered allowed ∩ available tools. BROWSER_CDP/SHIZUKU/
 * VIRTUAL_DISPLAY stay undeclared until Phase 4 probes exist (declaring them
 * now would hide the tools permanently behind UNKNOWN).
 */
class ToolCapabilityDeclarationsTest {

    @Test
    fun `gesture and button tools require accessibility`() {
        assertThat(MobileActionTool().requiredCapabilities)
            .containsExactly(Capability.ACCESSIBILITY)
        assertThat(SystemButtonTool().requiredCapabilities)
            .containsExactly(Capability.ACCESSIBILITY)
    }

    @Test
    fun `termux tool requires termux shell`() {
        assertThat(TermuxShellTool().requiredCapabilities)
            .containsExactly(Capability.TERMUX_SHELL)
    }

    @Test
    fun `registry filters declared tools against live states`() {
        val registry = ToolRegistry().apply {
            register(MobileActionTool())
            register(SystemButtonTool())
            register(TermuxShellTool())
        }
        val allAvailable = CapabilityManager(DeviceCapabilitySource {
            mapOf(
                Capability.ACCESSIBILITY to CapabilityState.AVAILABLE,
                Capability.TERMUX_SHELL to CapabilityState.AVAILABLE,
            )
        })
        assertThat(registry.getAvailable(allAvailable).map { it.name })
            .containsExactly("mobile_action", "system_button", "termux_shell")

        val termuxDown = CapabilityManager(DeviceCapabilitySource {
            mapOf(
                Capability.ACCESSIBILITY to CapabilityState.AVAILABLE,
                Capability.TERMUX_SHELL to CapabilityState.UNAVAILABLE,
            )
        })
        assertThat(registry.getAvailable(termuxDown).map { it.name })
            .containsExactly("mobile_action", "system_button")
    }
}
