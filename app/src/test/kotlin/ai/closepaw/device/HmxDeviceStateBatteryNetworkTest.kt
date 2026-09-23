package ai.closepaw.device

import ai.closepaw.tool.CapabilityState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Battery + network DeviceState fields: values, per-field states, failure degradation. */
class HmxDeviceStateBatteryNetworkTest {

    private fun provider(
        battery: (() -> Int?)? = { 72 },
        network: (() -> Boolean?)? = { true },
    ) = AndroidHmxDeviceStateProvider(
        batteryPercent = battery ?: { null },
        networkAvailable = network ?: { null },
    )

    @Test
    fun `battery and network report probe values`() {
        val state = provider().current()

        assertThat(state.batteryPercent).isEqualTo(72)
        assertThat(state.networkAvailable).isTrue()
        assertThat(state.fieldStates[DeviceStateField.BATTERY])
            .isEqualTo(CapabilityState.AVAILABLE)
        assertThat(state.fieldStates[DeviceStateField.NETWORK])
            .isEqualTo(CapabilityState.AVAILABLE)
    }

    @Test
    fun `absent probes degrade to unknown`() {
        val state = provider(battery = null, network = null).current()

        assertThat(state.batteryPercent).isNull()
        assertThat(state.networkAvailable).isNull()
        assertThat(state.fieldStates[DeviceStateField.BATTERY])
            .isEqualTo(CapabilityState.UNKNOWN)
        assertThat(state.fieldStates[DeviceStateField.NETWORK])
            .isEqualTo(CapabilityState.UNKNOWN)
    }

    @Test
    fun `throwing probes degrade without throwing`() {
        val state = provider(
            battery = { throw IllegalStateException("no battery service") },
            network = { throw SecurityException("no network permission") },
        ).current()

        assertThat(state.batteryPercent).isNull()
        assertThat(state.networkAvailable).isNull()
        assertThat(state.fieldStates[DeviceStateField.BATTERY])
            .isEqualTo(CapabilityState.UNKNOWN)
        assertThat(state.fieldStates[DeviceStateField.NETWORK])
            .isEqualTo(CapabilityState.UNKNOWN)
    }

    @Test
    fun `offline network reports false as available reading`() {
        val state = provider(network = { false }).current()

        assertThat(state.networkAvailable).isFalse()
        assertThat(state.fieldStates[DeviceStateField.NETWORK])
            .isEqualTo(CapabilityState.AVAILABLE)
    }
}
