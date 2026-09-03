package ai.closepaw.util

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashHandlerTest {
    @Test
    fun capturesExceptionContextAndBoundedRecentLogsThenChains() {
        val dir = Files.createTempDirectory("hmx-crash-test").toFile()
        val store = CrashReportStore(dir)
        val logger = RuntimeLogger(logcat = { _, _, _, _ -> })
        repeat(60) { logger.info("event", "log-$it") }
        val chained = AtomicInteger(0)
        var captured: CrashReport? = null
        val handler = CrashHandler(
            logger = logger,
            reportStore = store,
            contextProvider = { DiagnosticContext("session-1", "task-2", "screen", "tool") },
            previousHandler = Thread.UncaughtExceptionHandler { _, _ -> chained.incrementAndGet() },
            reportListener = { captured = it },
        )

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        val report = captured
        assertNotNull(report)
        assertEquals("session-1", report!!.sessionId)
        assertEquals("task-2", report.taskId)
        assertTrue(report.stackTrace.contains("IllegalStateException"))
        assertEquals(50, report.recentLogs.size)
        assertTrue(report.recentLogs.first().contains("log-10"))
        assertEquals(1, chained.get())
        assertEquals(1, store.list().size)
    }

    @Test
    fun reportStoreRotatesToTenFiles() {
        val dir = Files.createTempDirectory("hmx-crash-rotation").toFile()
        val store = CrashReportStore(dir)
        repeat(12) { index ->
            store.save(CrashReport(index.toLong(), "Error", "safe", "trace", listOf("log")))
        }
        assertEquals(10, store.list().size)
    }
}
