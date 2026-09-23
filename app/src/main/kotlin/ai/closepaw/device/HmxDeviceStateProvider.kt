package ai.closepaw.device

import ai.closepaw.platform.DisplayInfo
import ai.closepaw.tool.Capability
import ai.closepaw.tool.CapabilityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Source of [HmxDeviceState] snapshots (Phase 4).
 *
 * Event-driven + bounded: snapshots are cached for [HmxDeviceState.DEFAULT_MAX_AGE_MS]
 * and re-read on access only when stale; [onExternalEvent] invalidates the cache
 * from lifecycle callbacks (package change, permission flip). No polling loops.
 */
interface HmxDeviceStateProvider {
    /** Cached snapshot, refreshing first when stale. Never throws. */
    fun current(): HmxDeviceState

    /** Force a fresh snapshot and emit it to [observe]. Never throws. */
    fun refresh(): HmxDeviceState

    /** Latest snapshot; emits on every [refresh]. */
    fun observe(): StateFlow<HmxDeviceState>

    /** Invalidate the cache from an external lifecycle event. Never throws. */
    fun onExternalEvent()
}

/**
 * Probe-based provider. Every probe is an injected lambda so the logic is
 * unit-testable with zero Android coupling; a failing probe degrades its
 * field to UNKNOWN/null instead of crashing the caller.
 */
class AndroidHmxDeviceStateProvider(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = HmxDeviceState.DEFAULT_MAX_AGE_MS,
    private val foregroundPackage: () -> String? = { null },
    private val accessibilityAvailable: () -> Boolean = { false },
    private val overlayGranted: () -> Boolean = { false },
    private val capabilities: () -> Map<Capability, CapabilityState> = { emptyMap() },
    private val display: () -> DisplayInfo? = { null },
    private val sessionPhase: () -> String? = { null },
    private val batteryPercent: () -> Int? = { null },
    private val networkAvailable: () -> Boolean? = { null },
    initial: HmxDeviceState? = null,
) : HmxDeviceStateProvider {

    @Volatile
    private var cached: HmxDeviceState? = initial

    private val _updates = MutableStateFlow(initial ?: read())

    override fun observe(): StateFlow<HmxDeviceState> = _updates.asStateFlow()

    override fun current(): HmxDeviceState {
        val snap = cached
        val now = runCatching { clock() }.getOrDefault(0L)
        return if (snap != null && !snap.isStale(now, maxAgeMs)) snap else refresh()
    }

    override fun refresh(): HmxDeviceState =
        read().also { cached = it; _updates.value = it }

    override fun onExternalEvent() {
        runCatching { refresh() }
    }

    private fun read(): HmxDeviceState {
        val now = runCatching { clock() }.getOrDefault(0L)
        val pkg = runCatching { foregroundPackage() }.getOrNull()
        val a11y = runCatching { accessibilityAvailable() }.getOrDefault(false)
        val overlay = runCatching { overlayGranted() }.getOrDefault(false)
        val caps = runCatching { capabilities() }.getOrDefault(emptyMap())
        val disp = runCatching { display() }.getOrNull()
        val phase = runCatching { sessionPhase() }.getOrNull()
        val battery = runCatching { batteryPercent() }.getOrNull()
        val network = runCatching { networkAvailable() }.getOrNull()
        return HmxDeviceState(
            timestampMs = now,
            foregroundPackage = pkg,
            accessibilityAvailable = a11y,
            overlayGranted = overlay,
            capabilities = caps,
            display = disp,
            sessionPhase = phase,
            batteryPercent = battery,
            networkAvailable = network,
            fieldStates = mapOf(
                DeviceStateField.FOREGROUND_APP to
                    if (pkg != null) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
                DeviceStateField.ACCESSIBILITY to
                    if (a11y) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE,
                DeviceStateField.OVERLAY to
                    if (overlay) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE,
                DeviceStateField.CAPABILITIES to
                    if (caps.isNotEmpty()) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
                DeviceStateField.DISPLAY to
                    if (disp != null) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
                DeviceStateField.SESSION to
                    if (phase != null) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
                DeviceStateField.BATTERY to
                    if (battery != null) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
                DeviceStateField.NETWORK to
                    if (network != null) CapabilityState.AVAILABLE else CapabilityState.UNKNOWN,
            ),
        )
    }
}
