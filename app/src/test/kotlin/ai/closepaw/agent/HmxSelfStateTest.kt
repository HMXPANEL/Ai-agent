package ai.closepaw.agent

import ai.closepaw.model.Bounds
import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.Point
import ai.closepaw.model.ScreenSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** P14 self-state + P15 perception summary tests. */
class HmxSelfStateTest {

    private fun element(
        text: String,
        editable: Boolean = false,
        focused: Boolean = false,
        className: String = "android.widget.TextView"
    ) = PerceptionElement(
        index = 0,
        text = text,
        resourceId = "",
        className = className,
        description = "",
        isClickable = !editable,
        isEditable = editable,
        isScrollable = false,
        isEnabled = true,
        isFocused = focused,
        isLongClickable = false,
        bounds = Bounds(0, 0, 100, 50),
        center = Point(50, 25),
    )

    @Test
    fun `summarize counts and focus`() {
        val snapshot = ScreenSnapshot(
            timestamp = 1L,
            elements = listOf(
                element("Hello"),
                element("", editable = true, focused = true, className = "android.widget.EditText"),
                element("Send"),
            )
        )

        val summary = snapshot.summarize("com.whatsapp")

        assertThat(summary.packageName).isEqualTo("com.whatsapp")
        assertThat(summary.elementCount).isEqualTo(3)
        assertThat(summary.editableCount).isEqualTo(1)
        assertThat(summary.focusedText).isNull()
        assertThat(summary.dialogVisible).isFalse()
    }

    @Test
    fun `dialog heuristic fires on dialog classes`() {
        val snapshot = ScreenSnapshot(
            timestamp = 1L,
            elements = listOf(element("Allow?", className = "android.app.AlertDialog"))
        )

        assertThat(snapshot.summarize("pkg").dialogVisible).isTrue()
    }

    @Test
    fun `prompt line names provider and model`() {
        val state = HmxSelfState(
            provider = "OTHER",
            model = "gemini-2.5-flash",
            backend = "OPENAI",
            otherConfigured = true,
            sessionId = "s1",
            taskSummary = "send hi",
            screen = null,
            capabilities = mapOf("ACCESSIBILITY" to "AVAILABLE", "TERMUX_SHELL" to "UNKNOWN"),
        )

        val line = state.promptLine()

        assertThat(line).contains("OTHER")
        assertThat(line).contains("gemini-2.5-flash")
    }

    @Test
    fun `describe answers state questions`() {
        val state = HmxSelfState(
            provider = "OPENAI_CODEX",
            model = "gpt-5.4",
            backend = "OPENAI",
            otherConfigured = false,
            sessionId = null,
            taskSummary = null,
            screen = null,
        )

        val text = state.describe()

        assertThat(text).contains("OPENAI_CODEX")
        assertThat(text).contains("gpt-5.4")
    }
}
