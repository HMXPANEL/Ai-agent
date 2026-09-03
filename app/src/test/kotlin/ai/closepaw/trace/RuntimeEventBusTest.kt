package ai.closepaw.trace

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeEventBusTest {
    @Test
    fun eventsAreBoundedAndChronological() {
        val bus = RuntimeEventBus(capacity = 3)
        repeat(5) { bus.emit(RuntimeEvent("event-$it", timestampMs = it.toLong())) }
        assertEquals(listOf("event-2", "event-3", "event-4"), bus.events.value.map { it.type })
    }
}
