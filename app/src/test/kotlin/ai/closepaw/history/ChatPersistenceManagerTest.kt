package ai.closepaw.history

import ai.closepaw.app.AppSettingsStore
import ai.closepaw.auth.FakeSharedPreferences
import ai.closepaw.history.model.SessionRecord
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P1/P12: live delegates + export/verify/restore/reconcile over the real
 * SessionStorage (temp dir). Uninstall/reinstall device flow is BLOCKED on
 * hardware; this pins every machine-checkable property of it.
 */
class ChatPersistenceManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private val prefs = FakeSharedPreferences()

    private fun contextFor(dir: java.io.File): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns dir
        every { ctx.getSharedPreferences(any(), any()) } returns prefs
        every { ctx.applicationContext } returns ctx
        return ctx
    }

    private fun manager(dir: java.io.File): ChatPersistenceManager {
        val ctx = contextFor(dir)
        return ChatPersistenceManager(SessionStorage(ctx), AppSettingsStore(ctx))
    }

    private fun record(id: String, updated: Long, text: String) = SessionRecord(
        sessionId = id,
        startTime = 1L,
        lastUpdated = updated,
        messages = listOf(
            ai.closepaw.history.model.MessageRecord.User("$id-m", 2L, text)
        ),
    )

    @Test
    fun `save message toolcall taskresult round-trip on live record`() = runTest {
        val dir = tempDir.newFolder("live")
        val mgr = manager(dir)
        mgr.saveConversation(record("s1", 10L, "hi")).getOrThrow()

        mgr.saveMessage("s1", "m2", 11L, "second").getOrThrow()
        mgr.saveToolCall("s1", "mobile_action", """{"a":"tap"}""", "ok").getOrThrow()
        mgr.saveTaskResult("s1", true, "done").getOrThrow()

        val out = ByteArrayOutputStream()
        val report = mgr.exportBackup(out)
        assertThat(report.sessionCount).isEqualTo(1)
        assertThat(report.messageCount).isEqualTo(3)

        val parsed = ChatBackup.parse(out.toString(Charsets.UTF_8.name())).getOrThrow()
        val saved = parsed.sessions.single()
        assertThat(saved.messages).hasSize(3)
        assertThat(saved.metadata.completedNormally).isTrue()
    }

    @Test
    fun `writes to unknown sessions fail loudly`() = runTest {
        val mgr = manager(tempDir.newFolder("empty"))

        assertThat(mgr.saveMessage("ghost", "m", 1L, "x").isFailure).isTrue()
        assertThat(mgr.saveToolCall("ghost", "t", "{}", null).isFailure).isTrue()
        assertThat(mgr.saveTaskResult("ghost", true, "x").isFailure).isTrue()
    }

    @Test
    fun `export restore round-trip across stores (reinstall shape)`() = runTest {
        val src = manager(tempDir.newFolder("src"))
        src.saveConversation(record("a", 10L, "hello")).getOrThrow()
        src.saveConversation(record("b", 20L, "world")).getOrThrow()
        val bytes = ByteArrayOutputStream().also { src.exportBackup(it) }.toByteArray()

        // Fresh store, as after reinstall.
        val dst = manager(tempDir.newFolder("dst"))
        val restored = dst.restoreBackup(bytes.inputStream())

        assertThat(restored.verified.valid).isTrue()
        assertThat(restored.written).isEqualTo(2)
        assertThat(restored.adopted).containsExactly("a", "b")
        assertThat(restored.rolledBack).isFalse()

        val verifyOut = ByteArrayOutputStream()
        val check = dst.exportBackup(verifyOut)
        assertThat(check.sessionCount).isEqualTo(2)
        assertThat(check.messageCount).isEqualTo(2)
    }

    @Test
    fun `duplicate restore adopts nothing`() = runTest {
        val src = manager(tempDir.newFolder("s"))
        src.saveConversation(record("a", 10L, "x")).getOrThrow()
        val bytes = ByteArrayOutputStream().also { src.exportBackup(it) }.toByteArray()

        val dst = manager(tempDir.newFolder("d"))
        dst.restoreBackup(bytes.inputStream())
        val second = dst.restoreBackup(bytes.inputStream())

        assertThat(second.verified.valid).isTrue()
        assertThat(second.adopted).isEmpty()
        assertThat(second.written).isEqualTo(0)
    }

    @Test
    fun `newer live data is never overwritten`() = runTest {
        val live = manager(tempDir.newFolder("l"))
        live.saveConversation(record("a", 100L, "live-new")).getOrThrow()

        val backupMgr = manager(tempDir.newFolder("b"))
        backupMgr.saveConversation(record("a", 10L, "backup-old")).getOrThrow()
        val bytes = ByteArrayOutputStream().also { backupMgr.exportBackup(it) }.toByteArray()

        val report = live.restoreBackup(bytes.inputStream())

        assertThat(report.verified.valid).isTrue()
        assertThat(report.adopted).isEmpty()
        assertThat(report.keptLive).containsExactly("a")
        val out = ByteArrayOutputStream()
        live.exportBackup(out)
        val kept = ChatBackup.parse(out.toString(Charsets.UTF_8.name())).getOrThrow()
        assertThat(kept.sessions.single().messages.first()).isEqualTo(
            live.exportTextOf("a")
        )
    }

    @Test
    fun `corrupt backup writes nothing`() = runTest {
        val mgr = manager(tempDir.newFolder("c"))
        mgr.saveConversation(record("keep", 10L, "x")).getOrThrow()

        val report = mgr.restoreBackup("{corrupt".byteInputStream())

        assertThat(report.verified.valid).isFalse()
        assertThat(report.written).isEqualTo(0)
        val out = ByteArrayOutputStream()
        assertThat(mgr.exportBackup(out).sessionCount).isEqualTo(1)
    }

    @Test
    fun `restoreIfEmpty skips non-empty stores`() = runTest {
        val mgr = manager(tempDir.newFolder("n"))
        mgr.saveConversation(record("a", 10L, "x")).getOrThrow()

        assertThat(mgr.restoreIfEmpty(FailingMirror())).isNull()
    }

    @Test
    fun `backup envelope carries no secret-shaped fields`() = runTest {
        val mgr = manager(tempDir.newFolder("sec"))
        mgr.saveConversation(record("a", 10L, "x")).getOrThrow()
        val out = ByteArrayOutputStream()
        mgr.exportBackup(out)
        val doc = out.toString(Charsets.UTF_8.name()).lowercase()

        assertThat(doc).doesNotContain("apikey")
        assertThat(doc).doesNotContain("api_key")
        assertThat(doc).doesNotContain("token")
        assertThat(doc).doesNotContain("secret")
        assertThat(doc).doesNotContain("password")
        assertThat(doc).doesNotContain("bearer")
    }

    private suspend fun ChatPersistenceManager.exportTextOf(sessionId: String): Any {
        val out = ByteArrayOutputStream()
        exportBackup(out)
        return ChatBackup.parse(out.toString(Charsets.UTF_8.name())).getOrThrow()
            .sessions.single { it.sessionId == sessionId }.messages.first()
    }

    private class FailingMirror : BackupMediaMirror(mockk(relaxed = true)) {
        override fun latestBackupUri(): android.net.Uri? =
            throw AssertionError("mirror must not be consulted for non-empty stores")
    }
}
