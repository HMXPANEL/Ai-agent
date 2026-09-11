package ai.closepaw.agent

import ai.closepaw.protocol.TaskOutcomeState

/**
 * Per-task verification memory (P5).
 *
 * The turn-level gate ([blocksUnverifiedCompletion]) stops false success within
 * the actuating turn. This tracker closes the cross-turn hole: a model must not
 * complete in a later turn while an earlier turn's device actuation is still
 * unverified. Only a fresh VERIFIED assessment clears the flag — observation-only
 * turns deliberately do not, so "look again" without new proof changes nothing.
 *
 * Lifecycle: one instance per agent task; [reset] on new goal.
 */
class TaskVerificationTracker {

    private var unresolvedActuation: Boolean = false
    private var actuatedTurns: Int = 0
    private var verifiedTurns: Int = 0

    /** Record one turn's verification outcome. */
    fun noteTurn(assessment: TaskVerification.Assessment) {
        when (assessment) {
            TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED -> {
                actuatedTurns++
                unresolvedActuation = true
            }
            TaskVerification.Assessment.REQUIRED_AND_VERIFIED -> {
                actuatedTurns++
                verifiedTurns++
                unresolvedActuation = false
            }
            TaskVerification.Assessment.NOT_REQUIRED -> {
                // Observation-only turn: no new evidence either way.
            }
        }
    }

    /** True when some earlier turn actuated the device without fresh proof since. */
    fun hasUnresolvedActuation(): Boolean = unresolvedActuation

    fun reset() {
        unresolvedActuation = false
        actuatedTurns = 0
        verifiedTurns = 0
    }

    fun toOutcomeState(): TaskOutcomeState =
        if (unresolvedActuation) TaskOutcomeState.OUTCOME_NOT_VERIFIED
        else TaskOutcomeState.OUTCOME_VERIFIED
}
