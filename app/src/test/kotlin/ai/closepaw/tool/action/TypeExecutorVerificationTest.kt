package ai.closepaw.tool.action

import ai.closepaw.model.Bounds
import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.Point
import ai.closepaw.model.ScreenSnapshot
import ai.closepaw.platform.ActionResult
import ai.closepaw.platform.AndroidPlatform
import ai.closepaw.platform.AppInfo
import ai.closepaw.platform.DisplayInfo
import ai.closepaw.platform.FieldContent
import ai.closepaw.platform.UIAction
import ai.closepaw.protocol.PlatformMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * P2 regression tests: platform success without exact content is NOT success.
 * Pins the WhatsApp "Messagehi" case end to end at executor level.
 */
class TypeExecutorVerificationTest {

    private val editBounds = Bounds(left = 50, top = 100, right = 500, bottom = 160)

    private fun editableElement() = PerceptionElement(
        index = 1,
        text = "",
        resourceId = "",
        className = "android.widget.EditText",
        description = "",
        isClickable = true,
        isEditable = true,
        isScrollable = false,
        isEnabled = true,
        isFocused = true,
        isLongClickable = false,
        bounds = editBounds,
        center = Point(275, 130),
    )

    private fun snapshot() = ScreenSnapshot(timestamp = 1L, elements = listOf(editableElement()))

    /** Scripted platform: action results in order + field reads in order. */
    private class VerifyingFakePlatform(
        private val actionResults: List<ActionResult>,
        private val reads: List<FieldContent?> = emptyList(),
        private val allowTap: Boolean = true,
    ) : AndroidPlatform {
        val performed = mutableListOf<UIAction>()
        val readCalls = mutableListOf<String>()
        private var actionIndex = 0
        private var readIndex = 0
        override val mode: PlatformMode = PlatformMode.ACCESSIBILITY

        override suspend fun captureScreen(): ScreenSnapshot =
            ScreenSnapshot(timestamp = 2L, elements = emptyList())

        override suspend fun performAction(action: UIAction): ActionResult {
            performed += action
            return actionResults.getOrNull(actionIndex++) ?: actionResults.last()
        }

        override suspend fun readTextAt(x: Int, y: Int): FieldContent? {
            readCalls += "at:$x,$y"
            return reads.getOrNull(readIndex++) ?: reads.lastOrNull()
        }

        override suspend fun readFocusedText(): FieldContent? {
            readCalls += "focused"
            return reads.getOrNull(readIndex++) ?: reads.lastOrNull()
        }

        override fun hasRequiredPermissions(): Boolean = true
        override fun getCurrentPackageName(): String? = "com.whatsapp"
        override fun getDisplayInfo(): DisplayInfo =
            DisplayInfo(widthPixels = 1080, heightPixels = 2400, density = 3f)
        override suspend fun getInstalledApps(): List<AppInfo> = emptyList()
        override suspend fun launchApp(packageName: String): ActionResult = ActionResult.Success()
        override fun allowTapToFocus(): Boolean = allowTap
    }

    @Test
    fun `exact content verifies success`() = runTest {
        val platform = VerifyingFakePlatform(
            actionResults = listOf(ActionResult.Success()),
            reads = listOf(FieldContent("hi", "Message", found = true)),
        )

        val outcome = TypeExecutor().execute(
            target = Target.ElementIndex(1),
            inputText = "hi",
            clear = false,
            snapshot = snapshot(),
            platform = platform,
            isCancelled = { false },
        )

        assertThat(outcome).isInstanceOf(ActionOutcome.Success::class.java)
        assertThat((outcome as ActionOutcome.Success).verified).isTrue()
        assertThat(outcome.attemptTrail.joinToString()).contains("verified exact text")
    }

    @Test
    fun `Messagehi triggers retry and reports failure not success`() = runTest {
        val platform = VerifyingFakePlatform(
            // Attempt 1 direct write OK; attempt 2 tap OK + focused write OK.
            actionResults = listOf(
                ActionResult.Success(),
                ActionResult.Success(),
                ActionResult.Success(),
            ),
            // Both re-reads show the glued content.
            reads = listOf(
                FieldContent("Messagehi", "Message", found = true),
                FieldContent("Messagehi", "Message", found = true),
            ),
        )

        val outcome = TypeExecutor().execute(
            target = Target.ElementIndex(1),
            inputText = "hi",
            clear = false,
            snapshot = snapshot(),
            platform = platform,
            isCancelled = { false },
        )

        // Must NOT be success.
        assertThat(outcome).isInstanceOf(ActionOutcome.Failed::class.java)
        val failed = outcome as ActionOutcome.Failed
        assertThat(failed.reason).contains("OUTCOME_NOT_VERIFIED")
        assertThat(failed.attemptTrail.joinToString()).contains("switching strategy")
    }

    @Test
    fun `unverified direct write recovers via clear retry`() = runTest {
        val platform = VerifyingFakePlatform(
            actionResults = listOf(
                ActionResult.Success(),
                ActionResult.Success(),
                ActionResult.Success(),
            ),
            reads = listOf(
                FieldContent("Messagehi", "Message", found = true),
                // After clear-escalated retry the field holds exactly "hi".
                FieldContent("hi", "Message", found = true),
            ),
        )

        val outcome = TypeExecutor().execute(
            target = Target.ElementIndex(1),
            inputText = "hi",
            clear = false,
            snapshot = snapshot(),
            platform = platform,
            isCancelled = { false },
        )

        assertThat(outcome).isInstanceOf(ActionOutcome.Success::class.java)
        assertThat((outcome as ActionOutcome.Success).verified).isTrue()
        // The retry escalated to clear=true.
        val focusedWrites = platform.performed.filterIsInstance<UIAction.SetTextOnFocused>()
        assertThat(focusedWrites).hasSize(1)
        assertThat(focusedWrites.single().clear).isTrue()
    }

    @Test
    fun `unsupported reads preserve legacy platform result`() = runTest {
        // No reads scripted (null): platform without the read API.
        val platform = VerifyingFakePlatform(
            actionResults = listOf(ActionResult.Success()),
            reads = emptyList(),
        )

        val outcome = TypeExecutor().execute(
            target = Target.ElementIndex(1),
            inputText = "hi",
            clear = false,
            snapshot = snapshot(),
            platform = platform,
            isCancelled = { false },
        )

        assertThat(outcome).isInstanceOf(ActionOutcome.Success::class.java)
        assertThat((outcome as ActionOutcome.Success).verified).isTrue()
    }

    @Test
    fun `hint-only field reports write did not land`() = runTest {
        val platform = VerifyingFakePlatform(
            actionResults = listOf(
                ActionResult.Success(),
                ActionResult.Success(),
                ActionResult.Success(),
            ),
            reads = listOf(
                FieldContent("Message", "Message", found = true),
                FieldContent("Message", "Message", found = true),
            ),
        )

        val outcome = TypeExecutor().execute(
            target = Target.ElementIndex(1),
            inputText = "hi",
            clear = false,
            snapshot = snapshot(),
            platform = platform,
            isCancelled = { false },
        )

        assertThat(outcome).isInstanceOf(ActionOutcome.Failed::class.java)
        assertThat((outcome as ActionOutcome.Failed).attemptTrail.joinToString())
            .contains("still shows hint/empty")
    }
}
