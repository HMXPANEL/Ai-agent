package ai.closepaw.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityManagerTest {
    private fun managerOf(vararg pairs: Pair<Capability, CapabilityState>): CapabilityManager =
        CapabilityManager(DeviceCapabilitySource { mapOf(*pairs) })

    @Test
    fun availableIsTheOnlyUsableState() {
        val usable = managerOf(Capability.ACCESSIBILITY to CapabilityState.AVAILABLE)
        assertTrue(usable.isAvailable(Capability.ACCESSIBILITY))

        val unusableStates = CapabilityState.entries - CapabilityState.AVAILABLE
        assertEquals(6, unusableStates.size)
        unusableStates.forEach { state ->
            val manager = managerOf(Capability.ACCESSIBILITY to state)
            assertFalse("must fail closed for $state", manager.isAvailable(Capability.ACCESSIBILITY))
        }
    }

    @Test
    fun unreportedCapabilityIsUnknownAndFailsClosed() {
        val manager = managerOf(Capability.TERMUX_SHELL to CapabilityState.AVAILABLE)

        assertEquals(CapabilityState.UNKNOWN, manager.stateOf(Capability.SHIZUKU))
        assertFalse(manager.isAvailable(Capability.SHIZUKU))
    }

    @Test
    fun stubSourceFailsClosedForEverything() {
        val manager = CapabilityManager()

        assertTrue(manager.snapshot().isEmpty())
        Capability.entries.forEach { assertFalse(manager.isAvailable(it)) }
    }

    @Test
    fun failingSourceDegradesToEmptySnapshotWithoutThrowing() {
        val manager = CapabilityManager(DeviceCapabilitySource {
            throw IllegalStateException("provider down")
        })

        assertTrue(manager.snapshot().isEmpty())
        assertFalse(manager.isAvailable(Capability.ACCESSIBILITY))
    }

    @Test
    fun canUseRequiresEveryCapabilityAvailable() {
        val manager = managerOf(
            Capability.ACCESSIBILITY to CapabilityState.AVAILABLE,
            Capability.SHIZUKU to CapabilityState.DEGRADED,
        )

        assertTrue(manager.canUse(emptySet()))
        assertTrue(manager.canUse(setOf(Capability.ACCESSIBILITY)))
        assertFalse(manager.canUse(setOf(Capability.ACCESSIBILITY, Capability.SHIZUKU)))
    }

    @Test
    fun alternativesReturnsOnlyAvailableFallbacks() {
        val manager = managerOf(
            Capability.SHIZUKU to CapabilityState.UNAVAILABLE,
            Capability.ACCESSIBILITY to CapabilityState.AVAILABLE,
        )

        assertEquals(setOf(Capability.ACCESSIBILITY), manager.alternatives(Capability.SHIZUKU))
        assertEquals(setOf(Capability.ACCESSIBILITY), manager.alternatives(Capability.TERMUX_SHELL))
    }
}
