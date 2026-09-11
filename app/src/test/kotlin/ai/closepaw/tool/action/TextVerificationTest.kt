package ai.closepaw.tool.action

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P2 regression tests: hint text must never masquerade as typed content.
 * The WhatsApp "Messagehi" case is pinned by `hint plus requested is mismatch`.
 */
class TextVerificationTest {

    @Test
    fun `exact match passes`() {
        assertThat(TextVerification.verify("hi", "hi", "Message"))
            .isEqualTo(TextVerification.Verdict.EXACT)
    }

    @Test
    fun `exact match passes without hint`() {
        assertThat(TextVerification.verify("hi", "hi", null))
            .isEqualTo(TextVerification.Verdict.EXACT)
    }

    @Test
    fun `empty field is still-hint`() {
        assertThat(TextVerification.verify("hi", "", "Message"))
            .isEqualTo(TextVerification.Verdict.STILL_HINT_OR_EMPTY)
    }

    @Test
    fun `field showing only hint is still-hint`() {
        assertThat(TextVerification.verify("hi", "Message", "Message"))
            .isEqualTo(TextVerification.Verdict.STILL_HINT_OR_EMPTY)
    }

    @Test
    fun `hint plus requested is mismatch not success`() {
        // Real-device case: requested "hi", field read "Messagehi".
        assertThat(TextVerification.verify("hi", "Messagehi", "Message"))
            .isEqualTo(TextVerification.Verdict.MISMATCH)
    }

    @Test
    fun `unrelated content is mismatch`() {
        assertThat(TextVerification.verify("hi", "hello there", "Message"))
            .isEqualTo(TextVerification.Verdict.MISMATCH)
    }

    @Test
    fun `unreadable field is mismatch`() {
        assertThat(TextVerification.verify("hi", null, "Message"))
            .isEqualTo(TextVerification.Verdict.MISMATCH)
    }

    @Test
    fun `append-mode content matching combined expectation passes`() {
        // clear=false with real pre-existing content: expectation is combined.
        assertThat(TextVerification.verify("Hello World", "Hello World", null))
            .isEqualTo(TextVerification.Verdict.EXACT)
    }

    @Test
    fun `treatAsEmptyForWrite honors hint equality`() {
        assertThat(TextVerification.treatAsEmptyForWrite("", "Message")).isTrue()
        assertThat(TextVerification.treatAsEmptyForWrite(null, "Message")).isTrue()
        assertThat(TextVerification.treatAsEmptyForWrite("Message", "Message")).isTrue()
        assertThat(TextVerification.treatAsEmptyForWrite("Messagehi", "Message")).isFalse()
        assertThat(TextVerification.treatAsEmptyForWrite("hello", "Message")).isFalse()
        assertThat(TextVerification.treatAsEmptyForWrite("hello", null)).isFalse()
    }
}
