package ai.closepaw.perception

import ai.closepaw.model.Bounds
import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.Point
import ai.closepaw.model.ScreenSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase I: prompt-payload budget pins (CI-runnable, deterministic).
 *
 * Builds a worst-case 500-element snapshot (the [PerceptorFilterConfig] cap) and
 * records the serialized prompt JSON size. The byte bound guards against prompt
 * bloat regressions; wall time is logged for profiling but never asserted
 * (CI hardware varies).
 */
class PerceptionPayloadBudgetTest {

    private fun element(i: Int) = PerceptionElement(
        index = i,
        text = "Item number $i with a moderately long label for realism",
        resourceId = "com.example.app:id/element_$i",
        className = "android.widget.TextView",
        description = "content description $i",
        isClickable = i % 2 == 0,
        isEditable = false,
        isScrollable = false,
        isEnabled = true,
        isFocused = false,
        isLongClickable = false,
        bounds = Bounds(0, i * 4, 1080, i * 4 + 100),
        center = Point(540, i * 4 + 50),
    )

    @Test
    fun `max element snapshot stays within prompt budget`() {
        val snapshot = ScreenSnapshot(
            timestamp = 1L,
            elements = List(500) { element(it) },
            textEnriched = true,
        )

        val startNs = System.nanoTime()
        val json = Perceptor.toPromptJson(snapshot)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        val bytes = json.toByteArray(Charsets.UTF_8).size
        println("PerceptionPayloadBudget: elements=500 bytes=$bytes elapsedMs=$elapsedMs")

        assertThat(json).isNotEmpty()
        // ~500 elements must stay well under typical context windows even before compaction.
        assertThat(bytes).isLessThan(512 * 1024)
    }

    @Test
    fun `empty snapshot serializes to empty array`() {
        val json = Perceptor.toPromptJson(ScreenSnapshot(timestamp = 1L, elements = emptyList()))
        assertThat(json).isEqualTo("[]")
    }
}
