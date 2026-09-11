package ai.closepaw.tool.action

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** P15 control-strategy decision table. */
class StrategyRouterTest {

    @Test
    fun `semantic target starts with direct node write`() {
        assertThat(
            StrategyRouter().firstTypeStrategy(
                hasSemanticTarget = true, coordinateFallback = false
            )
        ).isEqualTo(StrategyRouter.TypeStrategy.DIRECT_NODE_WRITE)
    }

    @Test
    fun `no target starts focused-only`() {
        assertThat(
            StrategyRouter().firstTypeStrategy(
                hasSemanticTarget = false, coordinateFallback = false
            )
        ).isEqualTo(StrategyRouter.TypeStrategy.FOCUSED_WRITE)
    }

    @Test
    fun `coordinate fallback starts tap-focus`() {
        assertThat(
            StrategyRouter().firstTypeStrategy(
                hasSemanticTarget = true, coordinateFallback = true
            )
        ).isEqualTo(StrategyRouter.TypeStrategy.TAP_FOCUS_WRITE)
    }

    @Test
    fun `direct failure retries tap-focus when taps allowed`() {
        assertThat(
            StrategyRouter().nextTypeStrategy(
                StrategyRouter.TypeStrategy.DIRECT_NODE_WRITE, allowTapToFocus = true
            )
        ).isEqualTo(StrategyRouter.TypeStrategy.TAP_FOCUS_WRITE)
    }

    @Test
    fun `direct failure exhausts in VD mode instead of tapping wrong display`() {
        assertThat(
            StrategyRouter().nextTypeStrategy(
                StrategyRouter.TypeStrategy.DIRECT_NODE_WRITE, allowTapToFocus = false
            )
        ).isNull()
    }

    @Test
    fun `tap-focus and focused failures exhaust`() {
        val router = StrategyRouter()
        assertThat(
            router.nextTypeStrategy(StrategyRouter.TypeStrategy.TAP_FOCUS_WRITE, true)
        ).isNull()
        assertThat(
            router.nextTypeStrategy(StrategyRouter.TypeStrategy.FOCUSED_WRITE, true)
        ).isNull()
    }

    @Test
    fun `failed strategies are recorded and reported`() {
        val router = StrategyRouter()
        router.recordOutcome("100,200", "DIRECT_NODE_WRITE", succeeded = false)
        router.recordOutcome("100,200", "TAP_FOCUS_WRITE", succeeded = true)

        assertThat(router.failedStrategies("100,200")).containsExactly("DIRECT_NODE_WRITE")
        assertThat(router.isExhausted("100,200")).isFalse()
        router.recordOutcome("100,200", "TAP_FOCUS_WRITE", succeeded = false)
        assertThat(router.isExhausted("100,200")).isTrue()
    }
}
