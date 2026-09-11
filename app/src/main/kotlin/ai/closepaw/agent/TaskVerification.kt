package ai.closepaw.agent

import ai.closepaw.protocol.TaskOutcomeState
import ai.closepaw.tool.ToolName

/**
 * Turn-level verification policy (pure, JVM-testable).
 *
 * Rule: when a turn executed a device-actuating tool, `complete_task(success)`
 * is accepted only if every actuation outcome was verified. Informational
 * turns (no actuation) are completable without UI verification.
 *
 * The `[unverified]` marker is appended by `MobileActionInvocation` whenever
 * an executor reports `ActionOutcome.Success(verified = false)`.
 */
internal object TaskVerification {

    const val UNVERIFIED_MARKER = "[unverified]"

    /** Tools whose effects are externally observable and must be verified. */
    val VERIFICATION_REQUIRED_TOOLS: Set<String> = setOf(
        ToolName.MobileAction.canonical,
        ToolName.BrowserScript.canonical,
    )

    enum class Assessment {
        /** No actuation this turn — completion needs no UI proof. */
        NOT_REQUIRED,

        /** Actuation happened and every outcome verified. */
        REQUIRED_AND_VERIFIED,

        /** Actuation happened but some outcome is unverified/failed. */
        REQUIRED_BUT_UNVERIFIED,
    }

    /**
     * Assess a turn from the executed tool names and their text outputs.
     *
     * @param executedToolNames canonical tool names executed this turn.
     * @param outputs tool result outputs (scanned for [UNVERIFIED_MARKER]).
     * @param hasFailure true if any executed tool returned error/cancel.
     */
    fun assess(
        executedToolNames: Collection<String>,
        outputs: Collection<String>,
        hasFailure: Boolean,
    ): Assessment {
        val actuated = executedToolNames.any { it in VERIFICATION_REQUIRED_TOOLS }
        if (!actuated) return Assessment.NOT_REQUIRED
        if (hasFailure) return Assessment.REQUIRED_BUT_UNVERIFIED
        val unverified = outputs.any { it.contains(UNVERIFIED_MARKER) }
        return if (unverified) Assessment.REQUIRED_BUT_UNVERIFIED
        else Assessment.REQUIRED_AND_VERIFIED
    }

    /** Map an assessment to the public outcome state for diagnostics. */
    fun toOutcomeState(assessment: Assessment): TaskOutcomeState = when (assessment) {
        Assessment.NOT_REQUIRED,
        Assessment.REQUIRED_AND_VERIFIED -> TaskOutcomeState.OUTCOME_VERIFIED
        Assessment.REQUIRED_BUT_UNVERIFIED -> TaskOutcomeState.OUTCOME_NOT_VERIFIED
    }
}
