package ai.closepaw.tool

import ai.closepaw.termux.NeedsSetupReason
import ai.closepaw.termux.TermuxBridgeStatus
import ai.closepaw.termux.TermuxCapabilitySnapshot
import android.content.Context
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test

/**
 * P10/P12: capability states reflect reality; UNKNOWN denies by default
 * (via CapabilityManager.canUse, pinned here end to end).
 */
class DeviceCapabilitySourceTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun source(
        termux: TermuxCapabilitySnapshot = TermuxCapabilitySnapshot.Unavailable,
        overlayGranted: Boolean = true
    ): AndroidDeviceCapabilitySource {
        mockkStatic(Settings::class)
        every { Settings.canDrawOverlays(any()) } returns overlayGranted
        return AndroidDeviceCapabilitySource(
            appContext = mockk(relaxed = true),
            termuxSnapshot = termux,
            accessibilityAvailable = true
        )
    }

    private fun termux(
        available: Boolean,
        enabled: Boolean,
        status: TermuxBridgeStatus
    ) = TermuxCapabilitySnapshot(available, enabled, status)

    @Test
    fun `ready bridge is available`() {
        val s = source(termux(true, true, TermuxBridgeStatus.Ready))

        assertThat(s.termuxState()).isEqualTo(CapabilityState.AVAILABLE)
    }

    @Test
    fun `disabled bridge is unavailable`() {
        val s = source(termux(false, false, TermuxBridgeStatus.Disabled))

        assertThat(s.termuxState()).isEqualTo(CapabilityState.UNAVAILABLE)
    }

    @Test
    fun `not installed is unavailable`() {
        val s = source(termux(false, true, TermuxBridgeStatus.NotInstalled))

        assertThat(s.termuxState()).isEqualTo(CapabilityState.UNAVAILABLE)
    }

    @Test
    fun `permission-missing setup requires permission`() {
        val s = source(
            termux(
                false, true,
                TermuxBridgeStatus.NeedsSetup(NeedsSetupReason.PERMISSION_MISSING)
            )
        )

        assertThat(s.termuxState()).isEqualTo(CapabilityState.REQUIRES_PERMISSION)
    }

    @Test
    fun `health timeout is temporarily unavailable`() {
        val s = source(
            termux(
                false, true,
                TermuxBridgeStatus.NeedsSetup(NeedsSetupReason.HEALTH_TIMEOUT)
            )
        )

        assertThat(s.termuxState()).isEqualTo(CapabilityState.TEMPORARILY_UNAVAILABLE)
    }

    @Test
    fun `unknown setup reason degrades to unknown`() {
        val s = source(
            termux(
                false, true,
                TermuxBridgeStatus.NeedsSetup(NeedsSetupReason.UNKNOWN)
            )
        )

        assertThat(s.termuxState()).isEqualTo(CapabilityState.UNKNOWN)
    }

    @Test
    fun `overlay reflects system setting`() {
        assertThat(source(overlayGranted = true).snapshot()[Capability.OVERLAY])
            .isEqualTo(CapabilityState.AVAILABLE)
        assertThat(source(overlayGranted = false).snapshot()[Capability.OVERLAY])
            .isEqualTo(CapabilityState.REQUIRES_PERMISSION)
    }

    @Test
    fun `unprobed capabilities are unknown and deny declared tools`() {
        val manager = CapabilityManager(source())

        assertThat(manager.stateOf(Capability.BROWSER_CDP)).isEqualTo(CapabilityState.UNKNOWN)
        assertThat(manager.canUse(setOf(Capability.BROWSER_CDP))).isFalse()
        assertThat(manager.canUse(emptySet())).isTrue()
    }

    @Test
    fun `requires-approval states are never treated as available`() {
        val manager = CapabilityManager(source())

        for (state in listOf(
            CapabilityState.UNKNOWN,
            CapabilityState.UNAVAILABLE,
            CapabilityState.DEGRADED,
            CapabilityState.REQUIRES_PERMISSION,
            CapabilityState.REQUIRES_USER_APPROVAL,
            CapabilityState.TEMPORARILY_UNAVAILABLE,
        )) {
            val m = CapabilityManager(DeviceCapabilitySource {
                mapOf(Capability.TERMUX_SHELL to state)
            })
            assertThat(m.isAvailable(Capability.TERMUX_SHELL)).isFalse()
        }
        assertThat(manager.isAvailable(Capability.ACCESSIBILITY)).isTrue()
    }
}
