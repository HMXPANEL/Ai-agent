package ai.closepaw.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {
    @Test
    fun pushSixtyEntriesDropsOldestTenAndPreservesOrder() {
        val buffer = LogBuffer()
        repeat(60) { buffer.push("entry-$it") }

        assertEquals(50, buffer.size)
        assertEquals((10 until 60).map { "entry-$it" }, buffer.snapshot())
        assertEquals("entry-10", buffer.pop())
        assertTrue(buffer.snapshot().first() == "entry-11")
    }
}
