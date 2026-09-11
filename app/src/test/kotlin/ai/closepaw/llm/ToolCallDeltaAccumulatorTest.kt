package ai.closepaw.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P8/P12 streaming parser tests, including the Gemini index-less shapes.
 * Cases: indexed single/multi, index-less single/multi, mixed, partial args,
 * id/name backfill, drain-clear, malformed separation.
 */
class ToolCallDeltaAccumulatorTest {

    @Test
    fun `one indexed call assembles across fragments`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(index = 0L, id = "call_1", name = "tap", argsFragment = null)
        acc.onDelta(index = 0L, id = null, name = null, argsFragment = """{"x":""")
        acc.onDelta(index = 0L, id = null, name = null, argsFragment = """1}""")

        val out = acc.drain()

        assertThat(out).hasSize(1)
        assertThat(out[0].callId).isEqualTo("call_1")
        assertThat(out[0].name).isEqualTo("tap")
        assertThat(out[0].arguments).isEqualTo("""{"x":1}""")
    }

    @Test
    fun `multiple indexed calls stay distinct`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(0L, "call_a", "tap", """{"x":1}""")
        acc.onDelta(1L, "call_b", "type", """{"t":"hi"}""")

        val out = acc.drain()

        assertThat(out).hasSize(2)
        assertThat(out.map { it.callId }).containsExactly("call_a", "call_b")
    }

    @Test
    fun `id and name backfill from later fragments`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(0L, null, null, """{"x":""")
        acc.onDelta(0L, "call_9", null, null)
        acc.onDelta(0L, null, "scroll", """1}""")

        val out = acc.drain()

        assertThat(out.single().callId).isEqualTo("call_9")
        assertThat(out.single().name).isEqualTo("scroll")
    }

    @Test
    fun `missing id defaults to call-index`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(3L, null, "tap", "{}")

        assertThat(acc.drain().single().callId).isEqualTo("call_3")
    }

    @Test
    fun `one index-less call assembles across chunks (Gemini shape)`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(null, "call_g1", "mobile_action", """{"action":""")
        acc.onDelta(null, null, null, """"type"}""")

        val out = acc.drain()

        assertThat(out).hasSize(1)
        assertThat(out[0].callId).isEqualTo("call_g1")
        assertThat(out[0].arguments).isEqualTo("""{"action":"type"}""")
    }

    @Test
    fun `parallel index-less calls stay distinct by occurrence order`() {
        val acc = ToolCallDeltaAccumulator()
        // Chunk 1: two calls open.
        acc.onDelta(null, "call_A", "tap", """{"x":""", chunkOrdinal = 0)
        acc.onDelta(null, "call_B", "type", """{"t":""", chunkOrdinal = 1)
        // Chunk 2: fragments continue in the same order.
        acc.onDelta(null, null, null, """"1}""", chunkOrdinal = 0)
        acc.onDelta(null, null, null, """"hi"}""", chunkOrdinal = 1)

        val out = acc.drain()

        assertThat(out).hasSize(2)
        assertThat(out[0].callId).isEqualTo("call_A")
        assertThat(out[0].arguments).isEqualTo("""{"x":"1}""")
        assertThat(out[1].callId).isEqualTo("call_B")
        assertThat(out[1].arguments).isEqualTo("""{"t":"hi"}""")
    }

    @Test
    fun `mixed indexed and index-less deltas do not collide`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(0L, "call_idx", "tap", "{}")
        acc.onDelta(null, "call_no", "type", "{}")

        val ids = acc.drain().map { it.callId }

        assertThat(ids).containsExactly("call_idx", "call_no")
    }

    @Test
    fun `drain clears builders`() {
        val acc = ToolCallDeltaAccumulator()
        acc.onDelta(0L, "a", "tap", "{}")
        acc.drain()

        assertThat(acc.drain()).isEmpty()
        assertThat(acc.isEmpty()).isTrue()
    }

    @Test
    fun `partial arguments accumulate verbatim`() {
        val acc = ToolCallDeltaAccumulator()
        listOf("{", "\"a\"", ":", "1", "}").forEach {
            acc.onDelta(0L, "c", "f", it)
        }

        assertThat(acc.drain().single().arguments).isEqualTo("""{"a":1}""")
    }
}
