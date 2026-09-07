package ai.closepaw.tool

/**
 * HMX capabilities (Phase 3). Each maps to an execution environment or Android
 * permission the agent may need. The real availability truth arrives with the
 * Phase 4 DeviceStateProvider; Phase 3 only defines the vocabulary.
 */
enum class Capability {
    /** AccessibilityService: UI reading + gestures. */
    ACCESSIBILITY,

    /** Draw-over-apps overlay (diagnostics, approval UI). */
    OVERLAY,

    /** Termux shell bridge (localhost:18422). */
    TERMUX_SHELL,

    /** Browser automation via Chrome DevTools Protocol. */
    BROWSER_CDP,

    /** Privileged operations via the Shizuku binder service. */
    SHIZUKU,

    /** Running target apps on a Shizuku-backed virtual display. */
    VIRTUAL_DISPLAY,

    /** Long-running background execution. */
    BACKGROUND_EXECUTION,
}
