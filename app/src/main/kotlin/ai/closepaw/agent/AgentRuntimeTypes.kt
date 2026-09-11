package ai.closepaw.agent

import ai.closepaw.agent.cognition.context.NavigationState
import ai.closepaw.agent.cognition.policy.ToolArbitrationResult
import ai.closepaw.agent.cognition.policy.TurnToolPolicy
import ai.closepaw.tool.ToolCallResult
import ai.closepaw.tool.ToolName

/** Reason why the agent stopped. */
sealed class AgentStopReason {
    data class GoalAchieved(val message: String = "Goal achieved") : AgentStopReason()
    data object UserRequested : AgentStopReason()
    data class TaskImpossible(val message: String) : AgentStopReason()
    data class Error(val message: String) : AgentStopReason()
}

/** Outcome of a single turn. */
sealed class TurnOutcome {
    data object Continue : TurnOutcome()
    data class Complete(val message: String, val success: Boolean = true) : TurnOutcome()
    data class Error(val message: String, val recoverable: Boolean) : TurnOutcome()
    data object Cancelled : TurnOutcome()
}

/**
 * Mutable runtime state carried across turns.
 *
 * `navigationState` powers loop detection (stable-screen warning).
 */
internal data class TurnRunnerState(
    val navigationState: NavigationState = NavigationState()
)

/**
 * Full output of one `AgentTurnRunner.executeTurn()` call:
 * - `outcome`: control decision for the outer Agent loop
 * - `nextState`: state to feed into the next turn
 */
internal data class TurnExecutionResult(
    val outcome: TurnOutcome,
    val nextState: TurnRunnerState
)

/**
 * One executed tool call, distilled for verification decisions.
 */
internal data class ExecutedTool(
    /** Canonical tool name (e.g. "mobile_action"). */
    val name: String,
    /** Text output returned to the agent loop. */
    val output: String,
    /** True when the tool returned error/cancel rather than success. */
    val failed: Boolean,
)

/**
 * Outcome of executing the selected tool calls for a turn.
 *
 * Tracks which tools actually reached a terminal state (success/failure/cancelled)
 * so callers can distinguish "planned but not executed" from "executed and succeeded".
 */
internal data class ExecutionPhaseResult(
    val executedToolIds: Set<String>,
    val terminatedEarly: Boolean,
    val lastTerminalResult: ToolCallResult?,
    /** Per-tool execution records for the verification gate. Empty = unknown (legacy). */
    val executedTools: List<ExecutedTool> = emptyList(),
    /** Automatic outcome verification against the structured task, when present. */
    val verification: TurnVerificationResult? = null
) {
    companion object {
        val EMPTY = ExecutionPhaseResult(
            executedToolIds = emptySet(),
            terminatedEarly = false,
            lastTerminalResult = null
        )
    }
}

/**
 * Automatic post-actuation verification attached to a turn.
 *
 * @param assessment policy outcome over executed tools and their outputs.
 * @param details human-readable check lines, also surfaced to the agent.
 */
internal data class TurnVerificationResult(
    val assessment: TaskVerification.Assessment,
    val details: List<String> = emptyList()
)

/**
 * Maps the planning + execution results to the control-loop outcome.
 *
 * Only emits [TurnOutcome.Complete] when `complete_task` was planned AND actually executed.
 * If the execution loop aborted early (failure or cancellation) before reaching
 * `complete_task`, emits [TurnOutcome.Error] or [TurnOutcome.Cancelled] instead.
 *
 * complete_task hardening (P6): when the turn actuated the device (mobile_action /
 * browser_script executed) but some outcome is unverified, a `success` completion is
 * refused with a recoverable error — the model must verify first, not claim. Purely
 * informational turns are unaffected.
 */
internal fun decideTurnOutcome(
    policy: TurnToolPolicy,
    turnResult: TurnResult,
    arbitration: ToolArbitrationResult,
    execution: ExecutionPhaseResult,
    /**
     * True when an EARLIER turn of the same task actuated the device without
     * fresh proof since. Closes the cross-turn hole where the model completes
     * in a later, actuation-free turn while prior outcomes stay unverified.
     */
    priorUnresolvedActuation: Boolean = false
): TurnOutcome {
    if (execution.terminatedEarly) {
        return when (val last = execution.lastTerminalResult) {
            is ToolCallResult.Cancelled -> TurnOutcome.Cancelled
            is ToolCallResult.Error -> TurnOutcome.Error(
                message = last.error,
                recoverable = true
            )
            else -> TurnOutcome.Error(
                message = "Tool execution aborted before completion",
                recoverable = true
            )
        }
    }
    val completeTaskCall = arbitration.selectedToolCalls.find { it.name == ToolName.CompleteTask.raw }
    if (completeTaskCall != null && completeTaskCall.id !in execution.executedToolIds) {
        return TurnOutcome.Error(
            message = "complete_task was planned but did not execute",
            recoverable = true
        )
    }
    val decision = policy.decideCompletion(turnResult, arbitration)
    if (!decision.shouldComplete) return TurnOutcome.Continue
    if (decision.success &&
        (blocksUnverifiedCompletion(execution) || priorUnresolvedActuation)
    ) {
        return TurnOutcome.Error(
            message = "complete_task(success) blocked: device actions are " +
                "OUTCOME_NOT_VERIFIED (this turn or an earlier one). Re-observe the " +
                "screen, verify the exact outcome (field text, sent message, " +
                "foreground app), then complete.",
            recoverable = true
        )
    }
    return TurnOutcome.Complete(
        message = decision.summary ?: "Goal achieved",
        success = decision.success
    )
}

/**
 * True when the turn actuated the device but verification did not pass.
 * Prefers the automatic [TurnVerificationResult] when the runner attached one;
 * otherwise falls back to the output-marker policy (no records = unknown = pass,
 * preserving legacy behavior for paths that do not report executions).
 */
internal fun blocksUnverifiedCompletion(execution: ExecutionPhaseResult): Boolean {
    execution.verification?.let {
        return it.assessment == TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED
    }
    if (execution.executedTools.isEmpty()) return false
    return TaskVerification.assess(
        executedToolNames = execution.executedTools.map { it.name },
        outputs = execution.executedTools.map { it.output },
        hasFailure = execution.executedTools.any { it.failed }
    ) == TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED
}
