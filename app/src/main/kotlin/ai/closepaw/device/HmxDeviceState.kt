package ai.closepaw.device

import ai.closepaw.platform.DisplayInfo
import ai.closepaw.tool.Capability
import ai.closepaw.tool.CapabilityState

/**
 * Reportable device-state fields (Phase 4).
 *
 * Each field carries its own availability so callers can distinguish "known
 * absent" ([CapabilityState.UNAVAILABLE]) from "could not be read"
 * ([CapabilityState.UNKNOWN]). Only [CapabilityState.AVAILABLE] means usable.
 */
enum class DeviceStateField {
    FOREGROUND_APP,
    ACCESSIBILITY,
    OVERLAY,
    CAPABILITIES,
    DISPLAY,
    SESSION,
}

/**
 * Structured snapshot of relevant Android device state (Phase 4).
 *
 * Immutable value: providers hand out copies, never live references. No PII —
 * package names and display metrics only, never location or identifiers.
 */
data class HmxDeviceState(
    val timestampMs: Long,
    val foregroundPackage: String?,
    val accessibilityAvailable: Boolean,
    val overlayGranted: Boolean,
    val capabilities: Map<Capability, CapabilityState>,
    val display: DisplayInfo?,
    val sessionPhase: String?,
    val fieldStates: Map<DeviceStateField, CapabilityState>,
) {
    /** True when the snapshot is older than [maxAgeMs]. Stale state must be refreshed, not trusted. */
    fun isStale(nowMs: Long, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        nowMs - timestampMs > maxAgeMs

    companion object {
        /** Bounded cache age: snapshots older than this are re-read on access. */
        const val DEFAULT_MAX_AGE_MS = 2_000L
    }
}
