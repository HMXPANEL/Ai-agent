package ai.closepaw.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P4 request-understanding tests. Generic intent/entity extraction —
 * assertions never hard-code any single app.
 */
class StructuredTaskParserTest {

    private val apps = mapOf(
        "WhatsApp" to "com.whatsapp",
        "Chrome" to "com.android.chrome",
        "Gmail" to "com.google.android.gm",
    )

    @Test
    fun `send request extracts intent app recipient content`() {
        val task = StructuredTaskParser.parse("send hi to Aditya Maurya on WhatsApp", apps)

        assertThat(task.intent).isEqualTo(TaskIntent.SEND_MESSAGE)
        assertThat(task.application).isEqualTo("WhatsApp")
        assertThat(task.packageName).isEqualTo("com.whatsapp")
        assertThat(task.entities).contains("Aditya Maurya")
        assertThat(task.content).isEqualTo("hi")
        assertThat(task.verification.expectedSentText).isEqualTo("hi")
        assertThat(task.verification.expectedPackage).isEqualTo("com.whatsapp")
        assertThat(task.verification.expectedRecipient).isEqualTo("Aditya Maurya")
        assertThat(task.rawRequest).isEqualTo("send hi to Aditya Maurya on WhatsApp")
    }

    @Test
    fun `quoted content wins over heuristic`() {
        val task = StructuredTaskParser.parse("send \"good morning\" to Mom on WhatsApp", apps)

        assertThat(task.content).isEqualTo("good morning")
    }

    @Test
    fun `open app intent resolves package`() {
        val task = StructuredTaskParser.parse("open Chrome", apps)

        assertThat(task.intent).isEqualTo(TaskIntent.OPEN_APP)
        assertThat(task.packageName).isEqualTo("com.android.chrome")
        assertThat(task.verification.expectedPackage).isEqualTo("com.android.chrome")
    }

    @Test
    fun `unknown app leaves package null without failing`() {
        val task = StructuredTaskParser.parse("send hi to Ann on FictionalApp", apps)

        assertThat(task.intent).isEqualTo(TaskIntent.SEND_MESSAGE)
        assertThat(task.packageName).isNull()
        assertThat(task.verification.expectedPackage).isNull()
    }

    @Test
    fun `call search and email intents`() {
        assertThat(StructuredTaskParser.parse("call John", apps).intent).isEqualTo(TaskIntent.CALL)
        assertThat(StructuredTaskParser.parse("search nearby pizza on Chrome", apps).intent)
            .isEqualTo(TaskIntent.SEARCH)
        assertThat(StructuredTaskParser.parse("send report to Ann via Gmail", apps).intent)
            .isEqualTo(TaskIntent.SEND_EMAIL)
    }

    @Test
    fun `self requests detected without app parsing`() {
        val task = StructuredTaskParser.parse("Which model are you using?", apps)

        assertThat(task.intent).isEqualTo(TaskIntent.HMX_SELF)
        assertThat(task.isSelfRequest).isTrue()
    }

    @Test
    fun `gibberish degrades to unknown, never crashes`() {
        val task = StructuredTaskParser.parse("!!!", apps)

        assertThat(task.intent).isEqualTo(TaskIntent.UNKNOWN)
        assertThat(task.expectedOutcome).isNotEmpty()
    }
}
