package ai.closepaw.agent

/**
 * Input to [Planner.plan]. Phase 2 carries the goal plus session/task identity only;
 * device state and capabilities arrive in later phases without changing this contract.
 */
data class HmxTaskInput(
    val goal: String,
    val sessionId: String? = null,
    val taskId: String? = null,
)

/**
 * Phase 2 plans always select the legacy path: the existing [Agent] execution system
 * is the only executor, and later phases (capability/risk/environment) only refine
 * how [TaskOrchestrator] interprets this plan.
 */
data class HmxPlan(
    val goal: String,
    val useLegacyPath: Boolean = true,
) {
    companion object {
        fun fallback(goal: String): HmxPlan = HmxPlan(goal = goal, useLegacyPath = true)
    }
}

/** Produces an [HmxPlan] for a task. Suspended so later phases can read device/capability state. */
interface Planner {
    suspend fun plan(input: HmxTaskInput): HmxPlan
}

/** Phase 2 planner: every goal runs through the proven legacy agent path. */
class DefaultPlanner : Planner {
    override suspend fun plan(input: HmxTaskInput): HmxPlan = HmxPlan(goal = input.goal)
}
