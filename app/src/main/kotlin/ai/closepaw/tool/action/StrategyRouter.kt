package ai.closepaw.tool.action

/**
 * Control-strategy decision layer (P15).
 *
 * Chooses HOW to act, separately from WHAT to do. Ordering rule: the most
 * reliable semantic method first, coordinates only as a last resort, and a
 * failed strategy is never blindly repeated for the same target — the caller
 * must switch approach or stop. Bounded: at most [MAX_STRATEGIES_PER_TARGET]
 * distinct strategies per target key per turn.
 *
 * Today this drives text entry ([TypeStrategy]); the generic registry
 * ([recordOutcome]/[failedStrategies]) is the extension point for tap/scroll
 * and INTENT/API strategies.
 */
class StrategyRouter {

    companion object {
        const val MAX_STRATEGIES_PER_TARGET = 2
    }

    private val failures = mutableMapOf<String, MutableSet<String>>()

    /** Text-entry strategy ordered by reliability. */
    enum class TypeStrategy {
        /** Semantic node write (ACCESSIBILITY). Preferred when a node resolved. */
        DIRECT_NODE_WRITE,

        /** Tap-to-focus then focused write (ACCESSIBILITY + gesture). */
        TAP_FOCUS_WRITE,

        /** Focused write only (no tap; VD-safe). */
        FOCUSED_WRITE,
    }

    /** First strategy for a type request. */
    fun firstTypeStrategy(
        hasSemanticTarget: Boolean,
        coordinateFallback: Boolean,
    ): TypeStrategy = when {
        !hasSemanticTarget -> TypeStrategy.FOCUSED_WRITE
        coordinateFallback -> TypeStrategy.TAP_FOCUS_WRITE
        else -> TypeStrategy.DIRECT_NODE_WRITE
    }

    /**
     * Next strategy after [failed] failed, or null when exhausted (caller must
     * stop and report — never loop the same failure).
     */
    fun nextTypeStrategy(
        failed: TypeStrategy,
        allowTapToFocus: Boolean,
        targetKey: String? = null,
    ): TypeStrategy? {
        targetKey?.let {
            failures.getOrPut(it) { mutableSetOf() } += failed.name
        }
        return when (failed) {
            TypeStrategy.DIRECT_NODE_WRITE ->
                if (allowTapToFocus) TypeStrategy.TAP_FOCUS_WRITE else null
            TypeStrategy.TAP_FOCUS_WRITE,
            TypeStrategy.FOCUSED_WRITE -> null
        }
    }

    /** True once the target exhausted its strategy budget. */
    fun isExhausted(targetKey: String): Boolean =
        (failures[targetKey]?.size ?: 0) >= MAX_STRATEGIES_PER_TARGET

    /** Generic registry: record an outcome for any (target, strategy) pair. */
    fun recordOutcome(targetKey: String, strategy: String, succeeded: Boolean) {
        if (!succeeded) {
            failures.getOrPut(targetKey) { mutableSetOf() } += strategy
        }
    }

    /** Strategies already failed for this target (must not be retried as-is). */
    fun failedStrategies(targetKey: String): Set<String> =
        failures[targetKey]?.toSet().orEmpty()
}
