package ai.closepaw.tool

/**
 * Answers "what can I do right now?" (Phase 3).
 *
 * Reads availability from a [DeviceCapabilitySource] (a stub until the Phase 4
 * DeviceStateProvider exists) and filters [ToolSpec]s by their
 * [ToolSpec.requiredCapabilities]. Never throws: a failing source degrades to
 * all-[CapabilityState.UNKNOWN], which fails closed via [isAvailable].
 */
class CapabilityManager(
    private val source: DeviceCapabilitySource = UnknownDeviceCapabilities,
) {
    /** Current availability per capability; empty when the source fails. */
    fun snapshot(): Map<Capability, CapabilityState> =
        runCatching { source.snapshot() }.getOrDefault(emptyMap())

    /** State of one capability, defaulting to UNKNOWN when unreported. */
    fun stateOf(capability: Capability): CapabilityState =
        snapshot()[capability] ?: CapabilityState.UNKNOWN

    /** True only for AVAILABLE. Everything else (incl. DEGRADED/UNKNOWN) fails closed. */
    fun isAvailable(capability: Capability): Boolean =
        stateOf(capability) == CapabilityState.AVAILABLE

    /** True when every required capability is AVAILABLE. */
    fun canUse(required: Set<Capability>): Boolean =
        required.all { isAvailable(it) }

    /** Available fallbacks for [capability], e.g. ACCESSIBILITY for SHIZUKU. */
    fun alternatives(capability: Capability): Set<Capability> =
        (DEFAULT_ALTERNATIVES[capability].orEmpty()).filter { isAvailable(it) }.toSet()

    companion object {
        private val DEFAULT_ALTERNATIVES: Map<Capability, Set<Capability>> = mapOf(
            Capability.SHIZUKU to setOf(Capability.ACCESSIBILITY),
            Capability.VIRTUAL_DISPLAY to setOf(Capability.ACCESSIBILITY),
            Capability.TERMUX_SHELL to setOf(Capability.ACCESSIBILITY),
            Capability.BROWSER_CDP to setOf(Capability.ACCESSIBILITY),
        )
    }
}

/** Availability source. Phase 4 replaces the stub with the real DeviceStateProvider. */
fun interface DeviceCapabilitySource {
    fun snapshot(): Map<Capability, CapabilityState>
}

/** Pre-Phase-4 stub: every capability is UNKNOWN (fails closed). */
object UnknownDeviceCapabilities : DeviceCapabilitySource {
    override fun snapshot(): Map<Capability, CapabilityState> = emptyMap()
}
