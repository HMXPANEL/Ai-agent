package ai.closepaw.tool

/**
 * Availability of a [Capability].
 *
 * Fail-closed rule: only [AVAILABLE] means "safe to use". Every other state —
 * including [DEGRADED] and [UNKNOWN] — must be treated as not safely available.
 */
enum class CapabilityState {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
    DEGRADED,
    REQUIRES_PERMISSION,
    REQUIRES_USER_APPROVAL,
    TEMPORARILY_UNAVAILABLE,
}
