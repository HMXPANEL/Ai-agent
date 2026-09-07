package ai.closepaw.util

import org.junit.Assert.fail
import org.junit.Test

class RuntimeLoggerFailureTest {
    @Test
    fun loggingCallNeverThrowsWhenFilterAndSinkFail() {
        val brokenFilter = object : SensitiveDataFilter() {
            override fun redact(value: String): String = error("filter failure")
        }
        val brokenSink = DiagnosticLogSink { error("sink failure") }
        val logger = RuntimeLogger(filter = brokenFilter, sink = brokenSink, logcat = { _, _, _, _ -> error("logcat failure") })

        try {
            logger.error("failure", "message", mapOf("secret" to "value"), IllegalStateException("boom"))
        } catch (e: Throwable) {
            fail("logging call must never throw, but threw: $e")
        }
    }
}
