package ai.closepaw.agent

import ai.closepaw.model.Bounds
import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.Point
import ai.closepaw.model.ScreenSnapshot
import ai.closepaw.test.FakeAndroidPlatform
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * P3/P15 universal verification: package, field text, sent message, recipient.
 * Includes the WhatsApp-like flow (type hi → send → bubble "hi").
 */
class OutcomeVerifierTest {

    private fun element(
        text: String,
        editable: Boolean = false,
        description: String = "",
        className: String = "android.widget.TextView"
    ) = PerceptionElement(
        index = 0,
        text = text,
        resourceId = "",
        className = className,
        description = description,
        isClickable = !editable,
        isEditable = editable,
        isScrollable = false,
        isEnabled = true,
        isFocused = false,
        isLongClickable = false,
        bounds = Bounds(0, 0, 100, 50),
        center = Point(50, 25),
    )

    private fun snapshot(vararg elements: PerceptionElement) =
        ScreenSnapshot(timestamp = 1L, elements = elements.toList())

    private fun criteria(
        pkg: String? = null,
        field: String? = null,
        sent: String? = null,
        recipient: String? = null
    ) = TaskVerificationCriteria(
        expectedPackage = pkg,
        expectedFieldText = field,
        expectedSentText = sent,
        expectedRecipient = recipient,
    )

    @Test
    fun `foreground package match passes, mismatch fails`() = runTest {
        val platform = FakeAndroidPlatform(currentPackageName = "com.whatsapp")

        val pass = OutcomeVerifier.verify(
            criteria(pkg = "com.whatsapp"), platform, snapshot()
        )
        assertThat(pass.single().passed).isTrue()

        val fail = OutcomeVerifier.verify(
            criteria(pkg = "com.android.chrome"), platform, snapshot()
        )
        assertThat(fail.single().passed).isFalse()
    }

    @Test
    fun `exact field text passes, Messagehi-style content fails`() = runTest {
        val platform = FakeAndroidPlatform()

        val pass = OutcomeVerifier.verify(
            criteria(field = "hi"), platform,
            snapshot(element("hi", editable = true))
        )
        assertThat(pass.single { it.name == "field-text" }.passed).isTrue()

        // Real-device case: field holds hint+text glued together.
        val fail = OutcomeVerifier.verify(
            criteria(field = "hi"), platform,
            snapshot(element("Messagehi", editable = true))
        )
        assertThat(fail.single { it.name == "field-text" }.passed).isFalse()
    }

    @Test
    fun `sent message requires non-editable bubble`() = runTest {
        val platform = FakeAndroidPlatform()

        // Typed-but-unsent text in the input field does NOT count as sent.
        val typedOnly = OutcomeVerifier.verify(
            criteria(sent = "hi"), platform,
            snapshot(element("hi", editable = true))
        )
        assertThat(typedOnly.single { it.name == "sent-message" }.passed).isFalse()

        // Outgoing bubble present → sent.
        val sent = OutcomeVerifier.verify(
            criteria(sent = "hi"), platform,
            snapshot(
                element("", editable = true),
                element("hi", className = "android.widget.TextView")
            )
        )
        assertThat(sent.single { it.name == "sent-message" }.passed).isTrue()
    }

    @Test
    fun `recipient context matches visible text`() = runTest {
        val platform = FakeAndroidPlatform()

        val pass = OutcomeVerifier.verify(
            criteria(recipient = "Aditya Maurya"), platform,
            snapshot(element("Aditya Maurya"))
        )
        assertThat(pass.single { it.name == "recipient-context" }.passed).isTrue()

        val fail = OutcomeVerifier.verify(
            criteria(recipient = "Aditya Maurya"), platform,
            snapshot(element("Someone Else"))
        )
        assertThat(fail.single { it.name == "recipient-context" }.passed).isFalse()
    }

    @Test
    fun `whatsapp-like full chain verifies end to end`() = runTest {
        val platform = FakeAndroidPlatform(currentPackageName = "com.whatsapp")
        val task = StructuredTaskParser.parse(
            "send hi to Aditya Maurya on WhatsApp",
            mapOf("WhatsApp" to "com.whatsapp")
        )
        val afterSend = snapshot(
            element("Aditya Maurya"),
            element("", editable = true),
            element("hi", className = "android.widget.TextView"),
        )

        val result = OutcomeVerifier.verifyTurn(
            task = task,
            executedToolNames = listOf("mobile_action", "mobile_action"),
            outputs = listOf("Success: typed", "Success: tapped send"),
            hasFailure = false,
            platform = platform,
            snapshot = afterSend,
        )

        assertThat(result.assessment)
            .isEqualTo(TaskVerification.Assessment.REQUIRED_AND_VERIFIED)
        assertThat(result.details.joinToString()).contains("sent-message: PASS")
    }

    @Test
    fun `missing bubble keeps outcome unverified`() = runTest {
        val platform = FakeAndroidPlatform(currentPackageName = "com.whatsapp")
        val task = StructuredTaskParser.parse(
            "send hi to Aditya Maurya on WhatsApp",
            mapOf("WhatsApp" to "com.whatsapp")
        )
        val noBubble = snapshot(
            element("Aditya Maurya"),
            element("hi", editable = true),
        )

        val result = OutcomeVerifier.verifyTurn(
            task = task,
            executedToolNames = listOf("mobile_action"),
            outputs = listOf("Success: tapped send"),
            hasFailure = false,
            platform = platform,
            snapshot = noBubble,
        )

        assertThat(result.assessment)
            .isEqualTo(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
    }

    @Test
    fun `no structured task falls back to tool flags`() = runTest {
        val platform = FakeAndroidPlatform()

        val verified = OutcomeVerifier.verifyTurn(
            task = null,
            executedToolNames = listOf("mobile_action"),
            outputs = listOf("Success: ok"),
            hasFailure = false,
            platform = platform,
            snapshot = snapshot(),
        )
        assertThat(verified.assessment)
            .isEqualTo(TaskVerification.Assessment.REQUIRED_AND_VERIFIED)

        val idle = OutcomeVerifier.verifyTurn(
            task = null,
            executedToolNames = emptyList(),
            outputs = emptyList(),
            hasFailure = false,
            platform = platform,
            snapshot = snapshot(),
        )
        assertThat(idle.assessment).isEqualTo(TaskVerification.Assessment.NOT_REQUIRED)
    }
}
