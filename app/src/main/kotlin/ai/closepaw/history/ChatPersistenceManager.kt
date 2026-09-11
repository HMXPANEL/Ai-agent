package ai.closepaw.history

import ai.closepaw.app.AppSettingsStore
import ai.closepaw.history.ChatBackup.BackupReport
import ai.closepaw.history.ChatBackup.PrefsBackup
import ai.closepaw.history.model.SessionRecord
import ai.closepaw.llm.LLMProvider
import ai.closepaw.protocol.LLMBackendType
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chat persistence + uninstall-surviving backup (P1).
 *
 * Live history stays in [SessionStorage] (app-private JSON; single source of
 * truth — never duplicated). This manager adds the durable layer:
 * - [exportBackup]: sessions + NON-SECRET settings → versioned, checksummed JSON.
 * - [restoreBackup]: verify → reconcile → write adopted records only.
 *   Never deletes live data; newer live records always win.
 * - [restoreIfEmpty]: startup auto-restore used after reinstall.
 * - [applyPrefsBackup]: restores model/provider/backend/URLs. Credentials are
 *   never in the backup and therefore never restored from it.
 *
 * Writes are staged to temp files + atomically renamed (transactional per
 * record); a failed import rolls back the files it wrote and reports partial.
 */
class ChatPersistenceManager(
    private val storage: SessionStorage,
    private val settingsStore: AppSettingsStore,
) {
    companion object {
        private const val TAG = "ChatPersistence"
    }

    data class ExportReport(val sessionCount: Int, val messageCount: Int, val bytes: Long)
    data class RestoreReport(
        val verified: BackupReport,
        val adopted: List<String>,
        val keptLive: List<String>,
        val written: Int,
        val rolledBack: Boolean,
    )

    // ── Live-path delegates (spec operations; storage stays authoritative) ──

    /** Persist one conversation record (create or overwrite by id). */
    suspend fun saveConversation(record: SessionRecord): Result<Unit> =
        writeAtomic(storage.generateFileName(record.sessionId), serializeRecord(record))

    /** Append a user message to the session's live record. */
    suspend fun saveMessage(sessionId: String, id: String, timestamp: Long, text: String): Result<Unit> =
        mutateRecord(sessionId) { record ->
            record.copy(
                messages = record.messages +
                    ai.closepaw.history.model.MessageRecord.User(id, timestamp, text),
                lastUpdated = timestamp,
            )
        }

    /**
     * Record a tool call + result on the live record as an agent action block.
     * Appends to the latest agent message (or starts one); unknown sessions
     * fail loudly instead of inventing a conversation.
     */
    suspend fun saveToolCall(
        sessionId: String,
        toolName: String,
        arguments: String,
        result: String?,
    ): Result<Unit> = mutateRecord(sessionId) { record ->
        val now = System.currentTimeMillis()
        val block = ai.closepaw.history.model.ContentBlockRecord.Action(
            id = "tc-$now-${toolName.hashCode().toString(16)}",
            toolName = toolName,
            description = arguments.take(500),
            state = "success",
            resultSummary = result?.take(500),
        )
        val messages = record.messages.toMutableList()
        val lastIdx = messages.indexOfLast {
            it is ai.closepaw.history.model.MessageRecord.Agent
        }
        if (lastIdx >= 0) {
            val agent = messages[lastIdx] as ai.closepaw.history.model.MessageRecord.Agent
            messages[lastIdx] = agent.copy(contentBlocks = agent.contentBlocks + block)
        } else {
            messages += ai.closepaw.history.model.MessageRecord.Agent(
                id = "agent-$now",
                timestamp = now,
                contentBlocks = listOf(block),
                isComplete = true,
            )
        }
        record.copy(messages = messages, lastUpdated = now)
    }

    /** Record the terminal task result marker on the live record. */
    suspend fun saveTaskResult(sessionId: String, success: Boolean, summary: String): Result<Unit> =
        mutateRecord(sessionId) { record ->
            record.copy(
                lastUpdated = System.currentTimeMillis(),
                metadata = record.metadata.copy(completedNormally = success),
            )
        }.map {
            Log.d(TAG, "saveTaskResult for $sessionId success=$success: ${summary.take(120)}")
            Unit
        }

    // ── Backup / restore ──

    suspend fun exportBackup(out: OutputStream): ExportReport = withContext(Dispatchers.IO) {
        val sessions = readAllRecords()
        val doc = ChatBackup.export(
            sessions = sessions,
            prefs = collectPrefsBackup(),
            appVersion = currentAppVersion(),
        )
        val bytes = doc.toByteArray(Charsets.UTF_8)
        out.write(bytes)
        out.flush()
        ExportReport(
            sessionCount = sessions.size,
            messageCount = sessions.sumOf { it.messages.size },
            bytes = bytes.size.toLong(),
        )
    }

    suspend fun verifyBackup(input: InputStream): BackupReport = withContext(Dispatchers.IO) {
        ChatBackup.verify(input.readBytes().toString(Charsets.UTF_8))
    }

    /**
     * Verify → reconcile → adopt. Only records that are new or strictly newer
     * than live are written (staged temp + atomic rename each). Live records
     * are never deleted or overwritten with older data.
     */
    suspend fun restoreBackup(input: InputStream): RestoreReport = withContext(Dispatchers.IO) {
        val raw = input.readBytes().toString(Charsets.UTF_8)
        val verified = ChatBackup.verify(raw)
        if (!verified.valid) {
            return@withContext RestoreReport(verified, emptyList(), emptyList(), 0, false)
        }
        val envelope = ChatBackup.parse(raw).getOrThrow()
        val live = readAllRecords()
        val reconciled = ChatBackup.reconcile(live, envelope.sessions)
        val writtenFiles = mutableListOf<File>()
        var written = 0
        try {
            for (id in reconciled.adoptedFromBackup) {
                val record = reconciled.merged.first { it.sessionId == id }
                val fileName = storage.generateFileName(id)
                val target = storage.getSessionFile(fileName)
                val tmp = File(target.parent, "$fileName.tmp-restore")
                tmp.writeText(serializeRecord(record), Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    throw IllegalStateException("atomic rename failed for $fileName")
                }
                writtenFiles += target
                written++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Restore interrupted after $written writes; rolling back", e)
            writtenFiles.forEach { runCatching { it.delete() } }
            return@withContext RestoreReport(verified, emptyList(), emptyList(), 0, true)
        }
        applyPrefsBackup(envelope.prefs)
        RestoreReport(verified, reconciled.adoptedFromBackup, reconciled.keptLive, written, false)
    }

    /**
     * Startup auto-restore: only when the live store holds zero sessions and a
     * shared-storage backup exists. Returns null when there was nothing to do.
     */
    suspend fun restoreIfEmpty(mirror: BackupMediaMirror): RestoreReport? =
        withContext(Dispatchers.IO) {
            if (storage.listSessionFiles().isNotEmpty()) return@withContext null
            val uri = mirror.latestBackupUri() ?: return@withContext null
            val bytes = mirror.read(uri) ?: return@withContext null
            Log.i(TAG, "Empty store + backup found; auto-restoring")
            restoreBackup(bytes.inputStream())
        }

    // ── Internals ──

    private suspend fun readAllRecords(): List<SessionRecord> =
        storage.listSessionFiles().mapNotNull { file ->
            storage.readSession(file.name).getOrNull()
        }

    private suspend fun mutateRecord(
        sessionId: String,
        transform: (SessionRecord) -> SessionRecord,
    ): Result<Unit> {
        val fileName = storage.generateFileName(sessionId)
        val current = storage.readSession(fileName).getOrElse {
            return Result.failure(IllegalArgumentException("unknown session $sessionId"))
        }
        return writeAtomic(fileName, serializeRecord(transform(current)))
    }

    private suspend fun writeAtomic(fileName: String, json: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = storage.getSessionFile(fileName)
                val tmp = File(target.parent, "$fileName.tmp")
                tmp.writeText(json, Charsets.UTF_8)
                check(tmp.renameTo(target)) { "atomic rename failed for $fileName" }
            }
        }

    private fun serializeRecord(record: SessionRecord): String =
        kotlinx.serialization.json.Json.encodeToString(
            SessionRecord.serializer(), record
        )

    internal fun collectPrefsBackup(): PrefsBackup {
        val s = settingsStore.load()
        return PrefsBackup(
            selectedModel = s.selectedModel,
            selectedProvider = s.selectedProvider?.name,
            llmBackend = s.llmBackend.name,
            openaiBaseUrl = s.openaiBaseUrl.takeIf { it.isNotBlank() },
            otherBaseUrl = s.otherBaseUrl.takeIf { it.isNotBlank() },
            otherModelId = s.otherModelId.takeIf { it.isNotBlank() },
        )
    }

    internal fun applyPrefsBackup(prefs: PrefsBackup) {
        // Non-secret settings only. Credentials live in AuthStore and are
        // deliberately absent from backups — nothing here can leak or clobber them.
        prefs.selectedModel?.takeIf { it.isNotBlank() }?.let { settingsStore.saveModel(it) }
        prefs.selectedProvider?.let { raw ->
            runCatching { LLMProvider.valueOf(raw) }.getOrNull()?.let {
                settingsStore.saveProvider(it)
            }
        }
        prefs.llmBackend?.let { raw ->
            runCatching { LLMBackendType.valueOf(raw) }.getOrNull()?.let {
                settingsStore.saveBackend(it)
            }
        }
        prefs.openaiBaseUrl?.let { settingsStore.saveOpenaiBaseUrl(it) }
        prefs.otherBaseUrl?.let { settingsStore.saveOtherBaseUrl(it) }
        prefs.otherModelId?.let { settingsStore.saveOtherModelId(it) }
        Log.i(TAG, "Applied non-secret prefs from backup (model/backend/URLs only)")
    }

    private fun currentAppVersion(): String? = runCatching {
        ai.closepaw.BuildConfig.VERSION_NAME
    }.getOrNull()
}
