package ai.closepaw.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    @Test
    fun defaultPlannerSelectsLegacyPathWithGoal(): Unit = runBlocking {
        val plan = DefaultPlanner().plan(HmxTaskInput("open settings", "s", "t"))

        assertEquals("open settings", plan.goal)
        assertTrue(plan.useLegacyPath)
    }

    @Test
    fun fallbackPlanAlwaysUsesLegacyPath(): Unit = runBlocking {
        val plan = HmxPlan.fallback("goal")

        assertEquals("goal", plan.goal)
        assertTrue(plan.useLegacyPath)
    }
}
