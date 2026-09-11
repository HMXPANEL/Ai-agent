package ai.closepaw.history

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream

/**
 * Shared-storage mirror for chat backups (P1).
 *
 * Writes export documents into the shared Downloads collection so they survive
 * app uninstall; re-import reads them back (SAF picker or newest match). All
 * operations are best-effort nullables — backup must never crash the app.
 * Only ever handles [ChatBackup] documents (no credentials by construction).
 */
class BackupMediaMirror(private val appContext: Context) {

    companion object {
        private const val TAG = "BackupMirror"
    }

    /** Write a backup document to Downloads. Returns its Uri, or null on failure. */
    fun save(fileName: String, write: (OutputStream) -> Unit): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, ChatBackup.BACKUP_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { write(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse { e ->
            Log.w(TAG, "Backup mirror save failed", e)
            null
        }
    }

    /** Newest backup document Uri in Downloads, or null when none exists. */
    fun latestBackupUri(): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = appContext.contentResolver
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED,
            )
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("${ChatBackup.BACKUP_FILE_PREFIX}%")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "$id")
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /** Read a backup document's bytes, or null on failure. */
    fun read(uri: Uri): ByteArray? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}
