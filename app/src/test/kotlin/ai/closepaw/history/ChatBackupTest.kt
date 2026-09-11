package ai.closepaw.history

import ai.closepaw.history.model.ContentBlockRecord
import ai.closepaw.history.model.MessageRecord
import ai.closepaw.history.model.SessionMetadata
import ai.closepaw.history.model.SessionRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** P1/P12 backup envelope tests: round-trip, corruption, duplicates, migration. */
class ChatBackupTest {

    private fun session(
        id: String,
        updated: Long,
        vararg texts: String
    ) = SessionRecord(
        sessionId = id,
        startTime = 1L,
        lastUpdated = updated,
        messages = texts.mapIndexed { i, t ->
            MessageRecord.User(id = "$id-m$i", timestamp = i.toLong(), text = t)
        } + MessageRecord.Agent(
            id = "$id-a",
            timestamp = 99L,
            contentBlocks = listOf(ContentBlockRecord.Text("done")),
            isComplete = true,
        ),
        metadata = SessionMetadata(model = "gemini-2.5-flash"),
    )

    @Test
    fun `export verify round-trip`() {
        val doc = ChatBackup.export(
            sessions = listOf(session("a", 10L, "hi"), session("b", 20L, "hello", "bye")),
            prefs = ChatBackup.PrefsBackup(selectedModel = "m", selectedProvider = "OTHER"),
        )

        val report = ChatBackup.verify(doc)

        assertThat(report.valid).isTrue()
        assertThat(report.sessionCount).isEqualTo(2)
        assertThat(report.messageCount).isEqualTo(5)
        assertThat(report.errors).isEmpty()
    }

    @Test
    fun `empty history exports and verifies`() {
        val report = ChatBackup.verify(ChatBackup.export(emptyList(), ChatBackup.PrefsBackup()))

        assertThat(report.valid).isTrue()
        assertThat(report.sessionCount).isEqualTo(0)
    }

    @Test
    fun `garbage document fails verification without throwing`() {
        val report = ChatBackup.verify("{not json")

        assertThat(report.valid).isFalse()
        assertThat(report.errors).isNotEmpty()
    }

    @Test
    fun `tampered payload fails integrity`() {
        val doc = ChatBackup.export(listOf(session("a", 10L, "hi")), ChatBackup.PrefsBackup())
        val tampered = doc.replace(""""text":"hi"""", """"text":"bye"""")

        val report = ChatBackup.verify(tampered)

        assertThat(report.valid).isFalse()
        assertThat(report.errors.joinToString()).contains("integrity")
    }

    @Test
    fun `duplicate session ids fail verification`() {
        val doc = ChatBackup.export(
            listOf(session("a", 10L, "x"), session("a", 20L, "y")),
            ChatBackup.PrefsBackup()
        )

        assertThat(ChatBackup.verify(doc).valid).isFalse()
    }

    @Test
    fun `reconcile prefers newer lastUpdated on both sides`() {
        val live = listOf(session("keep", 30L, "live-new"), session("live-only", 5L, "x"))
        val incoming = listOf(session("keep", 10L, "backup-old"), session("new", 40L, "y"))

        val result = ChatBackup.reconcile(live, incoming)

        assertThat(result.merged.map { it.sessionId })
            .containsExactly("keep", "live-only", "new")
        assertThat(result.merged.first { it.sessionId == "keep" }.messages.first())
            .isEqualTo(live.first().messages.first())
        assertThat(result.adoptedFromBackup).containsExactly("new")
        assertThat(result.keptLive).containsExactly("keep", "live-only")
    }

    @Test
    fun `reconcile adopts strictly newer backup`() {
        val live = listOf(session("a", 10L, "old"))
        val incoming = listOf(session("a", 50L, "new"))

        val result = ChatBackup.reconcile(live, incoming)

        assertThat(result.merged.single().messages.first()).isEqualTo(incoming.single().messages.first())
        assertThat(result.adoptedFromBackup).containsExactly("a")
        assertThat(result.keptLive).isEmpty()
    }

    @Test
    fun `duplicate restore is a no-op second time`() {
        val backup = listOf(session("a", 50L, "new"))
        val first = ChatBackup.reconcile(emptyList(), backup)
        val second = ChatBackup.reconcile(first.merged, backup)

        assertThat(second.adoptedFromBackup).isEmpty()
        assertThat(second.merged.map { it.sessionId }).containsExactly("a")
    }

    @Test
    fun `large history round-trips`() {
        val sessions = (1..200).map { i -> session("s$i", i.toLong(), "m$i") }
        val doc = ChatBackup.export(sessions, ChatBackup.PrefsBackup())

        val report = ChatBackup.verify(doc)
        val parsed = ChatBackup.parse(doc).getOrThrow()

        assertThat(report.valid).isTrue()
        assertThat(report.sessionCount).isEqualTo(200)
        assertThat(parsed.sessions.map { it.sessionId }.toSet()).hasSize(200)
    }

    @Test
    fun `legacy record without new fields migrates`() {
        val legacy = """{"sessionId":"old","startTime":1,"lastUpdated":2,"messages":[]}"""

        val record = ChatBackup.migrateRecord(legacy).getOrThrow()

        assertThat(record.sessionId).isEqualTo("old")
        assertThat(record.screenStates).isEmpty()
    }

    @Test
    fun `migrate rejects blank ids`() {
        assertThat(
            ChatBackup.migrateRecord(
                """{"sessionId":"","startTime":1,"lastUpdated":2,"messages":[]}"""
            ).isFailure
        ).isTrue()
    }
}
