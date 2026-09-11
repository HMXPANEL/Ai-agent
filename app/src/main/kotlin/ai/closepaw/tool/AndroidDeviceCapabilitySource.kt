package ai.closepaw.tool

import ai.closepaw.termux.TermuxBridgeStatus
import ai.closepaw.termux.TermuxCapabilitySnapshot
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Live device-capability source (P10/P15).
 *
 * Reports what this device can actually do right now so [CapabilityManager]
 * hides tools that cannot execute instead of advertising them to the LLM.
 * States follow [CapabilityState] semantics strictly:
 * - UNKNOWN (or absent): deny by default ([CapabilityManager.canUse]).
 * - REQUIRES_PERMISSION / REQUIRES_USER_APPROVAL: never auto-execute.
 * - TEMPORARILY_UNAVAILABLE: hide until it recovers.
 *
 * Honesty rules: capabilities with a real probe report real states; anything
 * without a probe reports UNKNOWN (fail-closed) rather than a guessed
 * AVAILABLE. Today no tool declares non-empty requirements, so wiring this in
 * changes no advertised list — it arms the mechanism and the states are
 * observable in diagnostics.
 */
class AndroidDeviceCapabilitySource(
    private val appContext: Context,
    private val termuxSnapshot: TermuxCapabilitySnapshot =
        TermuxCapabilitySnapshot.Unavailable,
    private val accessibilityAvailable: Boolean = true,
) : DeviceCapabilitySource {

    companion object {
        private const val TAG = "DeviceCapabilities"
    }

    override fun snapshot(): Map<Capability, CapabilityState> = runCatching {
        buildMap {
            put(Capability.ACCESSIBILITY, accessibilityState())
            put(Capability.OVERLAY, overlayState())
            put(Capability.TERMUX_SHELL, termuxState())
            // No live probes wired yet: UNKNOWN fails closed for any tool that
            // declares them, without affecting tools that declare nothing.
            put(Capability.BROWSER_CDP, CapabilityState.UNKNOWN)
            put(Capability.SHIZUKU, CapabilityState.UNKNOWN)
            put(Capability.VIRTUAL_DISPLAY, CapabilityState.UNKNOWN)
            put(Capability.BACKGROUND_EXECUTION, CapabilityState.UNKNOWN)
        }
    }.getOrElse { e ->
        Log.w(TAG, "Capability snapshot failed; degrading to UNKNOWN", e)
        emptyMap()
    }

    private fun accessibilityState(): CapabilityState =
        if (accessibilityAvailable) CapabilityState.AVAILABLE
        else CapabilityState.TEMPORARILY_UNAVAILABLE

    private fun overlayState(): CapabilityState {
        val granted = runCatching {
            Settings.canDrawOverlays(appContext)
        }.getOrDefault(false)
        return if (granted) CapabilityState.AVAILABLE
        else CapabilityState.REQUIRES_PERMISSION
    }

    internal fun termuxState(): CapabilityState {
        if (!termuxSnapshot.enabled) return CapabilityState.UNAVAILABLE
        return when (val status = termuxSnapshot.status) {
            is TermuxBridgeStatus.Ready ->
                if (termuxSnapshot.available) CapabilityState.AVAILABLE
                else CapabilityState.TEMPORARILY_UNAVAILABLE
            is TermuxBridgeStatus.Disabled -> CapabilityState.UNAVAILABLE
            is TermuxBridgeStatus.SetupInProgress -> CapabilityState.TEMPORARILY_UNAVAILABLE
            is TermuxBridgeStatus.NeedsSetup -> when (status.reason) {
                ai.closepaw.termux.NeedsSetupReason.PERMISSION_MISSING,
                ai.closepaw.termux.NeedsSetupReason.ALLOW_EXTERNAL_APPS_MISSING ->
                    CapabilityState.REQUIRES_PERMISSION
                ai.closepaw.termux.NeedsSetupReason.HEALTH_TIMEOUT,
                ai.closepaw.termux.NeedsSetupReason.TERMUX_TIMEOUT ->
                    CapabilityState.TEMPORARILY_UNAVAILABLE
                else -> CapabilityState.UNKNOWN
            }
            is TermuxBridgeStatus.NotInstalled -> CapabilityState.UNAVAILABLE
        }
    }
}
