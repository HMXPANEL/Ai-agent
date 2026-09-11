package ai.closepaw.protocol

/**
 * Universal task-outcome lifecycle.
 *
 * Distinguishes TOOL success (an action executed) from TASK success (the
 * externally observable outcome was verified). A tool returning success must
 * never automatically mean the task completed.
 */
enum class TaskOutcomeState {
    /** Tool call was requested but not yet executed. */
    ACTION_REQUESTED,

    /** Tool call returned success (says nothing about the real world). */
    ACTION_EXECUTED,

    /** Tool call returned failure/cancel. */
    ACTION_FAILED,

    /** Fresh observation for outcome checking has started. */
    OUTCOME_VERIFICATION_STARTED,

    /** Fresh observation confirms the expected outcome. */
    OUTCOME_VERIFIED,

    /** Fresh observation does not confirm it (or none was possible). */
    OUTCOME_NOT_VERIFIED,

    /** Task ended without its outcome. */
    TASK_FAILED,

    /** Task ended with a verified (or non-observable) outcome. */
    TASK_COMPLETED,
}
