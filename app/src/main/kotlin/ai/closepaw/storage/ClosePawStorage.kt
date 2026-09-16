package ai.closepaw.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

object ClosePawStorage {

    private const val STORAGE_DIRECTORY_NAME = "ClosePaw"
    private const val METADATA_DIRECTORY_NAME = "metadata"
    private const val BACKUP_DIRECTORY_NAME = "backups"
    private const val MEMORY_DIRECTORY_NAME = "memory"
    private const val SESSIONS_DIRECTORY_NAME = "sessions"
    private const val SETTINGS_DIRECTORY_NAME = "settings"
    private const val SKILLS_DIRECTORY_NAME = "skills"
    private const val DIAGNOSTICS_DIRECTORY_NAME = "diagnostics"
    private const val EXPORTS_DIRECTORY_NAME = "exports"
    private const val STORAGE_FILE_NAME = "storage.json"

    private const val SCHEMA_VERSION = 2
    private const val DATA_FORMAT_VERSION = "1.0.0"

    private val instanceRef = AtomicReference<ClosePawStorage?>()

    val schemaVersion: Int get() = SCHEMA_VERSION
    val dataFormatVersion: String get() = DATA_FORMAT_VERSION

    val storageDir: File
    val metadataDir: File
    val backupDir: File
    val memoryDir: File
    val sessionsDir: File
    val settingsDir: File
    val skillsDir: File
    val diagnosticsDir: File
    val exportsDir: File

    @Volatile
    private var _initialized = false

    fun getInstance(context: Context): ClosePawStorage = synchronized(this) {
        instanceRef.getAndUpdate { existing ->
            existing ?: run {
                val storage = ClosePawStorage(context)
                _initialized = true
                storage
            }
        } ?: instanceRef.get()!!
    }

    @VisibleForTesting
    internal fun resetInstance() {
        instanceRef.set(null)
        _initialized = false
    }

    private constructor(context: Context) {
        val rootDir = getOrCreateClosePawRootDirectory(context)

        storageDir = rootDir.resolve(STORAGE_DIRECTORY_NAME).apply { mkdirs() }
        metadataDir = storageDir.resolve(METADATA_DIRECTORY_NAME).apply { mkdirs() }
        backupDir = storageDir.resolve(BACKUP_DIRECTORY_NAME).apply { mkdirs() }
        memoryDir = storageDir.resolve(MEMORY_DIRECTORY_NAME).apply { mkdirs() }
        sessionsDir = storageDir.resolve(SESSIONS_DIRECTORY_NAME).apply { mkdirs() }
        settingsDir = storageDir.resolve(SETTINGS_DIRECTORY_NAME).apply { mkdirs() }
        skillsDir = storageDir.resolve(SKILLS_DIRECTORY_NAME).apply { mkdirs() }
        diagnosticsDir = storageDir.resolve(DIAGNOSTICS_DIRECTORY_NAME).apply { mkdirs() }
        exportsDir = storageDir.resolve(EXPORTS_DIRECTORY_NAME).apply { mkdirs() }

        initializeStorageMetadataIfNeeded()
    }

    private fun getOrCreateClosePawRootDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getOrCreateMediaStoreDirectory(context)
        } else {
            getLegacySharedDirectory(context)
        }
    }

    private fun getOrCreateMediaStoreDirectory(context: Context): File {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.DirectoryColumns._ID, MediaStore.DirectoryColumns.DATA)

        try {
            val cursor = resolver.query(
                MediaStore.Directories.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.DirectoryColumns.RELATIVE_PATH} = ?",
                arrayOf("ClosePaw/"),
                null
            )

            if (cursor != null) {
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val path = c.getString(c.getColumnIndexOrThrow(MediaStore.DirectoryColumns.DATA))
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory) {
                            return dir
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }

        return tryCreateMediaStoreDirectory(context)
    }

    private fun tryCreateMediaStoreDirectory(context: Context): File {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.DirectoryColumns.RELATIVE_PATH, "ClosePaw/")
            put(MediaStore.DirectoryColumns.DISPLAY_NAME, "ClosePaw")
            put(MediaStore.DirectoryColumns.MEDIA_TYPE, 0)
        }

        try {
            val uri = resolver.insert(MediaStore.Directories.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val path = resolver.query(uri, arrayOf(MediaStore.DirectoryColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(c.getColumnIndexOrThrow(MediaStore.DirectoryColumns.DATA))
                    } else null
                }
                path?.let { File(it) }?.also { if (it.exists()) return it }
            }
        } catch (e: Exception) {
        }

        return getLegacySharedDirectory(context)
    }

    private fun getLegacySharedDirectory(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        val baseDir = externalDir?.parentFile
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.parentFile
            ?: context.filesDir.parentFile

        return baseDir.resolve(STORAGE_DIRECTORY_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun initializeStorageMetadataIfNeeded() {
        val metadataFile = metadataDir.resolve(STORAGE_FILE_NAME)
        if (!metadataFile.exists()) {
            val now = System.currentTimeMillis()
            val data = StorageMetadata(
                schemaVersion = SCHEMA_VERSION,
                createdAt = now,
                lastUpdatedAt = now,
                dataFormatVersion = DATA_FORMAT_VERSION
            )
            writeJsonFile(metadataFile, data)
        }
    }

    fun getMetadata(): StorageMetadata {
        val metadataFile = metadataDir.resolve(STORAGE_FILE_NAME)
        return readJsonFile(metadataFile) ?: StorageMetadata(
            schemaVersion = SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis(),
            dataFormatVersion = DATA_FORMAT_VERSION
        )
    }

    fun updateMetadata(lastUpdatedAt: Long) {
        val metadataFile = metadataDir.resolve(STORAGE_FILE_NAME)
        val currentMeta = getMetadata()
        val data = StorageMetadata(
            schemaVersion = SCHEMA_VERSION,
            createdAt = currentMeta.createdAt,
            lastUpdatedAt = lastUpdatedAt,
            dataFormatVersion = DATA_FORMAT_VERSION
        )
        writeJsonFile(metadataFile, data)
    }

    @VisibleForTesting
    data class StorageMetadata(
        val schemaVersion: Int,
        val createdAt: Long,
        val lastUpdatedAt: Long,
        val dataFormatVersion: String
    )

    @VisibleForTesting
    fun writeJsonFile(file: File, data: Any) {
        file.parentFile.mkdirs()
        val json = serializeToJson(data)
        writeFileSafely(file, json.toByteArray())
    }

    @VisibleForTesting
    fun readJsonFile(file: File): StorageMetadata? {
        if (!file.exists()) return null
        try {
            val json = file.readText()
            return deserializeFromJson(json)
        } catch (e: Exception) {
            return null
        }
    }

    @VisibleForTesting
    fun serializeToJson(data: Any): String {
        return when (data) {
            is StorageMetadata -> """
                {
                    "schemaVersion": ${data.schemaVersion},
                    "createdAt": ${data.createdAt},
                    "lastUpdatedAt": ${data.lastUpdatedAt},
                    "dataFormatVersion": "${data.dataFormatVersion}"
                }
            """.trimIndent()
            else -> "{}"
        }
    }

    @VisibleForTesting
    fun deserializeFromJson(json: String): StorageMetadata? {
        try {
            val schemaVersion = json.decodeStringField("schemaVersion") { it.toInt() }
            val createdAt = json.decodeLongField("createdAt") { System.currentTimeMillis() }
            val lastUpdatedAt = json.decodeLongField("lastUpdatedAt") { System.currentTimeMillis() }
            val dataFormatVersion = json.decodeStringField("dataFormatVersion") { "1.0.0" }
            return StorageMetadata(schemaVersion, createdAt, lastUpdatedAt, dataFormatVersion)
        } catch (e: Exception) {
            return null
        }
    }

    @VisibleForTesting
    fun SHA256(content: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").apply { digest(content) }.joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun verifyBackupChecksum(backupFile: File, expectedChecksum: String): Boolean {
        val actual = SHA256(backupFile.readBytes())
        return actual == expectedChecksum
    }

    @JvmStatic
    fun computeBackupChecksum(backupFile: File): String {
        return SHA256(backupFile.readBytes())
    }

    @JvmInline
    fun writeFileSafely(file: File, content: ByteArray): Boolean {
        val tempFile = file.parentFile.resolve(file.name + ".tmp")
        try {
            tempFile.writeBytes(content)
            tempFile.sync()
            tempFile.renameTo(file)
            return true
        } catch (e: Exception) {
            tempFile.deleteQuietly()
            return false
        }
    }

    @JvmInline
    fun readFileContent(file: File): ByteArray? {
        return if (file.exists()) file.readBytes() else null
    }

    fun memorySubdirectory(subdir: String): File {
        return memoryDir.resolve(subdir).apply { mkdirs() }
    }

    fun chatsSubdirectory(subdir: String): File {
        return sessionsDir.resolve(subdir).apply { mkdirs() }
    }

    fun sessionsSubdirectory(subdir: String): File {
        return sessionsDir.resolve(subdir).apply { mkdirs() }
    }

    fun backupsSubdirectory(subdir: String): File {
        return backupDir.resolve(subdir).apply { mkdirs() }
    }

    fun settingsSubdirectory(subdir: String): File {
        return settingsDir.resolve(subdir).apply { mkdirs() }
    }

    fun skillsSubdirectory(subdir: String): File {
        return skillsDir.resolve(subdir).apply { mkdirs() }
    }

    fun diagnosticsSubdirectory(subdir: String): File {
        return diagnosticsDir.resolve(subdir).apply { mkdirs() }
    }

    fun exportsSubdirectory(subdir: String): File {
        return exportsDir.resolve(subdir).apply { mkdirs() }
    }

    @JvmInline
    fun atomicRenameSafely(from: File, to: File): Boolean {
        from.parentFile.mkdirs()
        to.parentFile.mkdirs()
        return from.renameTo(to)
    }

    private fun String.decodeStringField(fieldName: String, default: (String) -> String): String {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        val match = pattern.find(this)
        return match?.groupValues?.get(1)?.let { default(it) } ?: default("")
    }

    private fun String.decodeLongField(fieldName: String, default: (String) -> Long): Long {
        val pattern = "\"$fieldName\"\\s*:\\s*(\\d+)".toRegex()
        val match = pattern.find(this)
        return match?.groupValues?.get(1)?.let { default(it) } ?: default("")
    }
}