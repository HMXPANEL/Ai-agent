package ai.closepaw.device

import ai.closepaw.platform.DisplayInfo
import ai.closepaw.tool.Capability
import ai.closepaw.tool.CapabilityState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase 4: bounded cached snapshots, stale refresh, failure degradation,
 * event invalidation, and per-field availability.
 */
class HmxDeviceStateProviderTest {

    private class FakeClock(var now: Long = 1_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun provider(
        clock: FakeClock = FakeClock(),
        maxAgeMs: Long = HmxDeviceState.DEFAULT_MAX_AGE_MS,
        reads: MutableList<Long> = mutableListOf(),
        pkg: String? = "com.example.app",
        a11y: Boolean = true,
        overlay: Boolean = true,
    ) = AndroidHmxDeviceStateProvider(
        clock = { clock.now },
        maxAgeMs = maxAgeMs,
        foregroundPackage = {
            reads.add(clock.now)
            pkg
        },
        accessibilityAvailable = { a11y },
        overlayGranted = { overlay },
        capabilities = { mapOf(Capability.ACCESSIBILITY to CapabilityState.AVAILABLE) },
        display = { DisplayInfo(1080, 2400, 2.5f) },
        sessionPhase = { "Running" },
    )

    @Test
    fun `fresh snapshot reports probe values`() {
        val state = provider().current()

        assertThat(state.foregroundPackage).isEqualTo("com.example.app")
        assertThat(state.accessibilityAvailable).isTrue()
        assertThat(state.overlayGranted).isTrue()
        assertThat(state.display).isEqualTo(DisplayInfo(1080, 2400, 2.5f))
        assertThat(state.sessionPhase).isEqualTo("Running")
        assertThat(state.fieldStates[DeviceStateField.FOREGROUND_APP])
            .isEqualTo(CapabilityState.AVAILABLE)
    }

    @Test
    fun `current within max age does not re-probe`() {
        val reads = mutableListOf<Long>()
        val clock = FakeClock()
        val p = provider(clock = clock, reads = reads)

        p.current()
        clock.advance(HmxDeviceState.DEFAULT_MAX_AGE_MS - 1)
        p.current()

        assertThat(reads).hasSize(1)
    }

    @Test
    fun `stale current re-reads`() {
        val reads = mutableListOf<Long>()
        val clock = FakeClock()
        val p = provider(clock = clock, reads = reads)

        p.current()
        clock.advance(HmxDeviceState.DEFAULT_MAX_AGE_MS + 1)
        val stale = p.current()

        assertThat(reads).hasSize(2)
        assertThat(stale.isStale(clock.now)).isFalse()
    }

    @Test
    fun `refresh forces re-read and emits`() {
        val p = provider()
        val before = p.observe().value

        p.refresh()

        assertThat(p.observe().value.timestampMs).isAtLeast(before.timestampMs)
        assertThat(p.observe().value).isEqualTo(p.current())
    }

    @Test
    fun `external event invalidates cache`() {
        val reads = mutableListOf<Long>()
        val clock = FakeClock()
        val p = provider(clock = clock, reads = reads)

        p.current()
        p.onExternalEvent()

        assertThat(reads).hasSize(2)
    }

    @Test
    fun `failing probes degrade to unknown without throwing`() {
        val p = AndroidHmxDeviceStateProvider(
            foregroundPackage = { throw RuntimeException("boom") },
            accessibilityAvailable = { throw RuntimeException("boom") },
            overlayGranted = { throw RuntimeException("boom") },
            capabilities = { throw RuntimeException("boom") },
            display = { throw RuntimeException("boom") },
            sessionPhase = { throw RuntimeException("boom") },
        )

        val state = p.current()

        assertThat(state.foregroundPackage).isNull()
        assertThat(state.accessibilityAvailable).isFalse()
        assertThat(state.display).isNull()
        assertThat(state.fieldStates[DeviceStateField.FOREGROUND_APP])
            .isEqualTo(CapabilityState.UNKNOWN)
        assertThat(state.fieldStates[DeviceStateField.CAPABILITIES])
            .isEqualTo(CapabilityState.UNKNOWN)
    }

    @Test
    fun `absent package and denied overlay map to field states`() {
        val p = provider(pkg = null, a11y = false, overlay = false)

        val state = p.current()

        assertThat(state.fieldStates[DeviceStateField.FOREGROUND_APP])
            .isEqualTo(CapabilityState.UNKNOWN)
        assertThat(state.fieldStates[DeviceStateField.ACCESSIBILITY])
            .isEqualTo(CapabilityState.UNAVAILABLE)
        assertThat(state.fieldStates[DeviceStateField.OVERLAY])
            .isEqualTo(CapabilityState.UNAVAILABLE)
    }
}
