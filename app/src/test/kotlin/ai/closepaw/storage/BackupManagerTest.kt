package ai.closepaw.storage

import ai.closepaw.app.AppSettingsStore
import ai.closepaw.history.ChatPersistenceManager
import ai.closepaw.history.model.ContentBlockRecord
import ai.closepaw.history.model.MessageRecord
import ai.closepaw.history.model.SessionMetadata
import ai.closepaw.history.model.SessionRecord
import ai.closepaw.history.storage.SessionStorage
import ai.closepaw.test.buildTestContext
import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Behavior-level coverage for [BackupManager] (P0 gap): create / list / restore /
 * checksum / corruption / rollback / auto-backup / credential exclusion.
 *
 * Pure JVM: temp dirs + mockk Context (mirrors [ai.closepaw.history.SessionStorageTest]).
 */
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storage: ClosePawStorage
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        ClosePawStorage.resetInstance()
        val root = tempFolder.newFolder("root")
        context = buildTestContext(root)
        every { context.getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>(relaxed = true)
        storage = ClosePawStorage.getInstance(context)
        manager = BackupManager(context, storage)
    }

    @After
    fun tearDown() {
        ClosePawStorage.resetInstance()
    }

    private fun record(id: String, updated: Long, vararg texts: String) = SessionRecord(
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
        metadata = SessionMetadata(model = "test-model"),
    )

    private suspend fun seed(vararg records: SessionRecord) {
        val persistence = ChatPersistenceManager(
            SessionStorage(storage.sessionsDir),
            AppSettingsStore(context),
        )
        records.forEach { persistence.saveConversation(it).getOrThrow() }
    }

    private fun gunzip(file: File): String =
        GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `create backup writes file metadata and accurate report`() = runTest {
        seed(record("a", 10L, "hello-backup-marker"), record("b", 20L, "world"))

        val report = manager.createBackup()

        assertThat(report.success).isTrue()
        assertThat(report.sessionCount).isEqualTo(2)
        assertThat(report.backupFile).isNotNull()
        assertThat(report.backupFile!!.exists()).isTrue()
        assertThat(report.checksum).isNotEmpty()
        val meta = File(report.backupFile!!.path.replace(".json.gz", ".meta.json"))
        assertThat(meta.exists()).isTrue()
        val metaJson = JSONObject(meta.readText())
        assertThat(metaJson.getInt("sessionCount")).isEqualTo(2)
        assertThat(metaJson.getString("checksum")).isEqualTo(report.checksum)
    }

    @Test
    fun `list backups returns newest first and skips orphan files`() = runTest {
        seed(record("a", 10L, "hi"))
        val first = manager.createBackup().backupFile!!

        // Orphan data file without metadata must be ignored.
        File(storage.backupDir, "closepaw_backup_9999999999999.json.gz").writeBytes(byteArrayOf(1, 2, 3))

        // Second backup with a forged-newer timestamp to force ordering deterministically.
        val newerTs = System.currentTimeMillis() + 60_000L
        val newerGz = File(storage.backupDir, "closepaw_backup_${newerTs}.json.gz")
        first.copyTo(newerGz)
        val metaJson = JSONObject(
            File(first.path.replace(".json.gz", ".meta.json")).readText()
        ).put("timestamp", newerTs)
        File(storage.backupDir, "closepaw_backup_${newerTs}.meta.json").writeText(metaJson.toString())

        val listed = manager.listBackups()
        assertThat(listed).hasSize(2)
        assertThat(listed[0].file.name).isEqualTo(newerGz.name)
        assertThat(listed[1].file.name).isEqualTo(first.name)
    }

    @Test
    fun `restore round trip readopts wiped sessions`() = runTest {
        seed(record("a", 10L, "restore-me"), record("b", 20L, "restore-me-too"))
        val backupFile = manager.createBackup().backupFile!!

        storage.sessionsDir.listFiles()?.forEach { it.delete() }
        assertThat(SessionStorage(storage.sessionsDir).listSessionFiles()).isEmpty()

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isTrue()
        assertThat(restore.restoredCount).isEqualTo(2)
        val reread = SessionStorage(storage.sessionsDir)
        assertThat(reread.listSessionFiles()).hasSize(2)
        val ids = reread.listSessionFiles().mapNotNull {
            reread.readSession(it.name).getOrNull()?.sessionId
        }
        assertThat(ids).containsExactly("a", "b")
    }

    @Test
    fun `restore rejects tampered bytes with checksum mismatch`() = runTest {
        seed(record("a", 10L, "tamper-test"))
        val backupFile = manager.createBackup().backupFile!!

        val bytes = backupFile.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        backupFile.writeBytes(bytes)

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isFalse()
        assertThat(restore.restoredCount).isEqualTo(0)
        assertThat(restore.errorMessage).contains("checksum mismatch")
    }

    @Test
    fun `restore without metadata fails gracefully`() = runTest {
        seed(record("a", 10L, "no-meta"))
        val backupFile = manager.createBackup().backupFile!!
        File(backupFile.path.replace(".json.gz", ".meta.json")).delete()

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isFalse()
        assertThat(restore.errorMessage).contains("metadata")
    }

    @Test
    fun `restore with malformed metadata fails gracefully`() = runTest {
        seed(record("a", 10L, "bad-meta"))
        val backupFile = manager.createBackup().backupFile!!
        File(backupFile.path.replace(".json.gz", ".meta.json")).writeText("{not json")

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isFalse()
        assertThat(restore.errorMessage).contains("metadata")
    }

    @Test
    fun `restore of corrupted archive with forged checksum fails without throwing`() = runTest {
        seed(record("a", 10L, "corrupt"))
        val backupFile = manager.createBackup().backupFile!!
        backupFile.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x00, 0x01, 0x02))

        // Forge the sidecar so the checksum gate passes and the gzip layer is reached.
        val forged = JSONObject(
            File(backupFile.path.replace(".json.gz", ".meta.json")).readText()
        ).put("checksum", storage.computeBackupChecksum(backupFile))
        File(backupFile.path.replace(".json.gz", ".meta.json")).writeText(forged.toString())

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isFalse()
        assertThat(restore.errorMessage).contains("corrupted")
    }

    @Test
    fun `restore of deleted data file fails gracefully`() = runTest {
        seed(record("a", 10L, "gone"))
        val backupFile = manager.createBackup().backupFile!!
        backupFile.delete()

        val restore = manager.restoreBackup(backupFile)

        assertThat(restore.success).isFalse()
    }

    @Test
    fun `auto backup creates once then skips while fresh`() = runTest {
        seed(record("a", 10L, "auto"))

        assertThat(manager.autoBackupIfNeeded()).isTrue()
        assertThat(manager.getLatestBackup()).isNotNull()
        assertThat(manager.autoBackupIfNeeded()).isFalse()
    }

    @Test
    fun `exported backup contains no credential material`() = runTest {
        seed(record("a", 10L, "credential-exclusion-probe"))
        val backupFile = manager.createBackup().backupFile!!

        val payload = gunzip(backupFile)
        assertThat(payload).contains("credential-exclusion-probe")
        assertThat(payload.lowercase()).doesNotContain("auth_store")
        assertThat(payload.lowercase()).doesNotContain("api_key")
        assertThat(payload.lowercase()).doesNotContain("refresh_token")
        assertThat(payload).contains("\"sessions\"")
    }
}
