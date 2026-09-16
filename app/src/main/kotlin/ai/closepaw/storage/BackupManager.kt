package ai.closepaw.storage

import android.content.Context
import android.util.Log
import ai.closepaw.history.BackupMediaMirror
import ai.closepaw.history.ChatBackup
import ai.closepaw.history.ChatPersistenceManager
import ai.closepaw.history.storage.SessionStorage
import ai.closepaw.app.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    fun createBackup(): BackupReport = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val backupFileName = "$BACKUP_FILE_PREFIX${timestamp}$BACKUP_FILE_EXTENSION"
        val backupFile = storage.backupsDir.resolve(backupFileName)

        val persistenceManager = ChatPersistenceManager(
            SessionStorage(context),
            AppSettingsStore(context)
        )

        val out = ByteArrayOutputStream()
        val report = persistenceManager.exportBackup(out)

        val compressedData = compress(out.toByteArray())
        val checksum = storage.computeBackupChecksum(File(backupFile.path))

        storage.writeFileSafely(backupFile, compressedData)

        val metaFile = storage.backupsDir.resolve("$BACKUP_FILE_PREFIX$timestamp.meta.json")
        storage.writeJsonFile(metaFile, BackupMetadata(
            backupFileName = backupFileName,
            checksum = checksum,
            sessionCount = report.sessionCount,
            timestamp = timestamp,
            dataFormatVersion = storage.dataFormatVersion
        ))

        Log.i(TAG, "Created backup: $backupFileName with ${report.sessionCount} sessions")

        BackupReport(
            success = true,
            backupFile = backupFile,
            sessionCount = report.sessionCount,
            checksum = checksum,
            errorMessage = null
        )
    }

    fun restoreBackup(backupFile: File): RestoreReport = withContext(Dispatchers.IO) {
        val metaFile = storage.backupsDir.resolve(backupFile.name.replace(BACKUP_FILE_EXTENSION, ".meta.json"))

        val metadata = storage.readJsonFile<BackupMetadata>(metaFile)
        if (metadata == null) {
            return@withContext RestoreReport(
                success = false,
                restoredCount = 0,
                errorMessage = "Missing backup metadata"
            )
        }

        if (!storage.verifyBackupChecksum(backupFile, metadata.checksum)) {
            return@withContext RestoreReport(
                success = false,
                restoredCount = 0,
                errorMessage = "Backup checksum mismatch - file may be corrupted"
            )
        }

        val decompressed = decompress(backupFile.readBytes())
        val persistenceManager = ChatPersistenceManager(
            SessionStorage(context),
            AppSettingsStore(context)
        )

        val input = java.io.ByteArrayInputStream(decompressed)
        val restoreReport = persistenceManager.restoreBackup(input)

        Log.i(TAG, "Restored backup: ${backupFile.name} with ${restoreReport.sessionCount} sessions")

        RestoreReport(
            success = true,
            restoredCount = restoreReport.sessionCount,
            errorMessage = null
        )
    }

    fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        storage.backupsDir.listFiles()?.filter { it.name.startsWith(BACKUP_FILE_PREFIX) && it.name.endsWith(BACKUP_FILE_EXTENSION) }
            ?.mapNotNull { file ->
                val metaFile = storage.backupsDir.resolve(file.name.replace(BACKUP_FILE_EXTENSION, ".meta.json"))
                storage.readJsonFile<BackupMetadata>(metaFile)?.let { meta ->
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

    fun getLatestBackup(): File? = withContext(Dispatchers.IO) {
        listBackups().firstOrNull()?.file
    }

    fun autoBackupIfNeeded(): Boolean = withContext(Dispatchers.IO) {
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
    )
}