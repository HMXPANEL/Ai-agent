package ai.closepaw.history

import ai.closepaw.history.model.SessionRecord
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uninstall-surviving chat backup (P1).
 *
 * Two complementary mechanisms:
 * 1. Android Auto Backup (manifest rules): restores app-private `sessions/`
 *    + `agent_prefs` automatically on reinstall. Secrets (`auth_store`) are
 *    explicitly excluded.
 * 2. User-visible export file ([ChatBackupEnvelope] JSON): written to shared
 *    storage (Downloads) via [BackupMediaMirror], re-imported via SAF picker
 *    or auto-restored when the live store is empty.
 *
 * NEVER stored here: OAuth tokens, API keys, passwords, or any credential.
 * Only conversation content + non-secret settings (model/provider/backend names,
 * base URLs, model ids).
 */
object ChatBackup {

    const val BACKUP_VERSION = 1
    const val BACKUP_MIME_TYPE = "application/json"
    const val BACKUP_FILE_PREFIX = "hmx-chat-backup-"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }

    @Serializable
    data class PrefsBackup(
        val selectedModel: String? = null,
        val selectedProvider: String? = null,
        val llmBackend: String? = null,
        val openaiBaseUrl: String? = null,
        val otherBaseUrl: String? = null,
        val otherModelId: String? = null,
    )

    @Serializable
    data class ChatBackupEnvelope(
        val backupVersion: Int = BACKUP_VERSION,
        val exportedAt: Long = 0L,
        val appVersion: String? = null,
        val sessions: List<SessionRecord> = emptyList(),
        val prefs: PrefsBackup = PrefsBackup(),
        /** SHA-256 over the canonical sessions payload; empty = legacy/unsigned. */
        val integritySha256: String = "",
    )

    data class BackupReport(
        val valid: Boolean,
        val sessionCount: Int = 0,
        val messageCount: Int = 0,
        val errors: List<String> = emptyList(),
    )

    data class ReconcileResult(
        /** Union of both sides; newer `lastUpdated` wins per session id. */
        val merged: List<SessionRecord>,
        /** Session ids taken from incoming (newer or new). */
        val adoptedFromBackup: List<String>,
        /** Session ids kept from live store (newer or backup missing). */
        val keptLive: List<String>,
    )

    /** Serialize sessions + safe prefs into an export document. */
    fun export(
        sessions: List<SessionRecord>,
        prefs: PrefsBackup,
        exportedAt: Long = System.currentTimeMillis(),
        appVersion: String? = null,
    ): String {
        val envelope = ChatBackupEnvelope(
            exportedAt = exportedAt,
            appVersion = appVersion,
            sessions = sessions,
            prefs = prefs,
            integritySha256 = sha256Of(sessions),
        )
        return json.encodeToString(ChatBackupEnvelope.serializer(), envelope)
    }

    /** Parse + validate an export document. Unknown future fields are ignored. */
    fun parse(raw: String): Result<ChatBackupEnvelope> = runCatching {
        json.decodeFromString(ChatBackupEnvelope.serializer(), raw)
    }

    /** Structural + integrity validation without touching the live store. */
    fun verify(raw: String): BackupReport {
        val envelope = parse(raw).getOrElse {
            return BackupReport(valid = false, errors = listOf("unparseable: ${it.message}"))
        }
        val errors = mutableListOf<String>()
        if (envelope.backupVersion !in 1..BACKUP_VERSION) {
            errors += "unsupported backupVersion=${envelope.backupVersion}"
        }
        if (envelope.exportedAt < 0) errors += "negative exportedAt"
        val seen = mutableSetOf<String>()
        var messages = 0
        for (s in envelope.sessions) {
            if (s.sessionId.isBlank()) errors += "blank sessionId"
            if (!seen.add(s.sessionId)) errors += "duplicate sessionId=${s.sessionId}"
            if (s.startTime < 0 || s.lastUpdated < 0) errors += "negative timestamp in ${s.sessionId}"
            messages += s.messages.size
        }
        if (envelope.integritySha256.isNotEmpty() &&
            envelope.integritySha256 != sha256Of(envelope.sessions)
        ) {
            errors += "integrity mismatch (file tampered or corrupted)"
        }
        return BackupReport(
            valid = errors.isEmpty(),
            sessionCount = envelope.sessions.size,
            messageCount = messages,
            errors = errors,
        )
    }

    /**
     * Deterministic union of live + backup sessions. Newer `lastUpdated` wins;
     * ties keep the live copy. Nothing newer is ever overwritten — this is what
     * makes "both exist" safe.
     */
    fun reconcile(
        live: List<SessionRecord>,
        incoming: List<SessionRecord>,
    ): ReconcileResult {
        val liveById = live.associateBy { it.sessionId }
        val inById = incoming.associateBy { it.sessionId }
        val merged = LinkedHashMap<String, SessionRecord>()
        val adopted = mutableListOf<String>()
        val kept = mutableListOf<String>()
        for (id in liveById.keys + inById.keys) {
            val l = liveById[id]
            val b = inById[id]
            when {
                l != null && b != null -> if (b.lastUpdated > l.lastUpdated) {
                    merged[id] = b
                    adopted += id
                } else {
                    merged[id] = l
                    kept += id
                }
                b != null -> {
                    merged[id] = b
                    adopted += id
                }
                else -> {
                    merged[id] = l!!
                    kept += id
                }
            }
        }
        return ReconcileResult(
            merged = merged.values.toList(),
            adoptedFromBackup = adopted,
            keptLive = kept,
        )
    }

    /**
     * Migrate a single stored session document forward. kotlinx deserialization
     * already applies field defaults for missing keys and ignores unknown keys,
     * so migration is validation + normalization, never data invention.
     */
    fun migrateRecord(raw: String): Result<SessionRecord> = runCatching {
        val record = Json { ignoreUnknownKeys = true }
            .decodeFromString(SessionRecord.serializer(), raw)
        require(record.sessionId.isNotBlank()) { "blank sessionId" }
        record
    }

    internal fun sha256Of(sessions: List<SessionRecord>): String {
        val canonical = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SessionRecord.serializer()),
            sessions.sortedBy { it.sessionId },
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
