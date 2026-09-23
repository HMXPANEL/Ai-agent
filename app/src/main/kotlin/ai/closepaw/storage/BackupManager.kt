package ai.closepaw.storage

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import ai.closepaw.history.ChatPersistenceManager
import ai.closepaw.history.storage.SessionStorage
import ai.closepaw.app.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

class BackupManager(
    private val context: Context,
    private val storage: ClosePawStorage = ClosePawStorage.getInstance(context)
) {

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FILE_PREFIX = "closepaw_backup_"
        private const val BACKUP_FILE_EXTENSION = ".json.gz"
    }

    suspend fun createBackup(): BackupReport = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val backupFileName = "$BACKUP_FILE_PREFIX${timestamp}$BACKUP_FILE_EXTENSION"
        val backupFile = storage.backupDir.resolve(backupFileName)

        val persistenceManager = ChatPersistenceManager(
            SessionStorage(storage.sessionsDir),
            AppSettingsStore(context)
        )

        val out = ByteArrayOutputStream()
        val report = persistenceManager.exportBackup(out)

        val compressedData = compress(out.toByteArray())
        storage.writeFileSafely(backupFile, compressedData)
        val checksum = storage.computeBackupChecksum(backupFile)

        val metaFile = storage.backupDir.resolve("$BACKUP_FILE_PREFIX$timestamp.meta.json")
        metaFile.parentFile?.mkdirs()
        metaFile.writeText(BackupMetadata(
            backupFileName = backupFileName,
            checksum = checksum,
            sessionCount = report.sessionCount,
            timestamp = timestamp,
            dataFormatVersion = storage.dataFormatVersion
        ).toJson())

        Log.i(TAG, "Created backup: $backupFileName with ${report.sessionCount} sessions")

        BackupReport(
            success = true,
            backupFile = backupFile,
            sessionCount = report.sessionCount,
            checksum = checksum,
            errorMessage = null
        )
    }

    suspend fun restoreBackup(backupFile: File): RestoreReport = withContext(Dispatchers.IO) {
        val metaFile = storage.backupDir.resolve(backupFile.name.replace(BACKUP_FILE_EXTENSION, ".meta.json"))

        val metadata = metaFile.takeIf { it.exists() }?.let { parseMetadata(it.readText()) }
        if (metadata == null) {
            return@withContext RestoreReport(
                success = false,
                restoredCount = 0,
                errorMessage = "Missing backup metadata"
            )
        }

        if (!runCatching { storage.verifyBackupChecksum(backupFile, metadata.checksum) }.getOrDefault(false)) {
            return@withContext RestoreReport(
                success = false,
                restoredCount = 0,
                errorMessage = "Backup checksum mismatch - file may be corrupted"
            )
        }

        val decompressed = try {
            decompress(backupFile.readBytes())
        } catch (e: Exception) {
            Log.w(TAG, "Backup archive unreadable: ${backupFile.name}", e)
            return@withContext RestoreReport(
                success = false,
                restoredCount = 0,
                errorMessage = "Backup archive is corrupted or unreadable"
            )
        }
        val persistenceManager = ChatPersistenceManager(
            SessionStorage(storage.sessionsDir),
            AppSettingsStore(context)
        )

        val input = java.io.ByteArrayInputStream(decompressed)
        val restoreReport = persistenceManager.restoreBackup(input)

        Log.i(TAG, "Restored backup: ${backupFile.name} with ${restoreReport.written} sessions")

        RestoreReport(
            success = true,
            restoredCount = restoreReport.written,
            errorMessage = null
        )
    }

    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        storage.backupDir.listFiles()?.filter { it.name.startsWith(BACKUP_FILE_PREFIX) && it.name.endsWith(BACKUP_FILE_EXTENSION) }
            ?.mapNotNull { file ->
                val metaFile = storage.backupDir.resolve(file.name.replace(BACKUP_FILE_EXTENSION, ".meta.json"))
                metaFile.takeIf { it.exists() }?.let { parseMetadata(it.readText()) }?.let { meta ->
                    BackupInfo(
                        file = file,
                        timestamp = meta.timestamp,
                        sessionCount = meta.sessionCount,
                        checksum = meta.checksum,
                        dataFormatVersion = meta.dataFormatVersion
                    )
                }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    suspend fun getLatestBackup(): File? = withContext(Dispatchers.IO) {
        listBackups().firstOrNull()?.file
    }

    suspend fun autoBackupIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val latestBackup = getLatestBackup()
        val metadata = storage.getMetadata()

        val timeSinceLastBackup = if (latestBackup != null) {
            System.currentTimeMillis() - metadata.lastUpdatedAt
        } else {
            Long.MAX_VALUE
        }

        val ONE_DAY_MS = 24L * 60 * 60 * 1000
        if (timeSinceLastBackup > ONE_DAY_MS) {
            val report = createBackup()
            if (report.success) {
                storage.updateMetadata(System.currentTimeMillis())
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    private fun compress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(data)
        }
        return out.toByteArray()
    }

    private fun decompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(java.io.ByteArrayInputStream(data)).use { gzip ->
            gzip.readAllBytes().also { out.write(it) }
        }
        return out.toByteArray()
    }

    data class BackupReport(
        val success: Boolean,
        val backupFile: File? = null,
        val sessionCount: Int = 0,
        val checksum: String? = null,
        val errorMessage: String? = null
    )

    data class RestoreReport(
        val success: Boolean,
        val restoredCount: Int = 0,
        val errorMessage: String? = null
    )

    data class BackupInfo(
        val file: File,
        val timestamp: Long,
        val sessionCount: Int,
        val checksum: String,
        val dataFormatVersion: String
    )

    @VisibleForTesting
    data class BackupMetadata(
        val backupFileName: String,
        val checksum: String,
        val sessionCount: Int,
        val timestamp: Long,
        val dataFormatVersion: String
    ) {
        fun toJson(): String = JSONObject()
            .put("backupFileName", backupFileName)
            .put("checksum", checksum)
            .put("sessionCount", sessionCount)
            .put("timestamp", timestamp)
            .put("dataFormatVersion", dataFormatVersion)
            .toString()
    }

    private fun parseMetadata(json: String): BackupMetadata? = runCatching {
        val o = JSONObject(json)
        BackupMetadata(
            backupFileName = o.getString("backupFileName"),
            checksum = o.getString("checksum"),
            sessionCount = o.getInt("sessionCount"),
            timestamp = o.getLong("timestamp"),
            dataFormatVersion = o.getString("dataFormatVersion")
        )
    }.getOrNull()
}