package ai.closepaw.ui.settings

import ai.closepaw.history.BackupMediaMirror
import ai.closepaw.history.ChatBackup
import ai.closepaw.history.ChatBackup.RestoreReport
import ai.closepaw.history.ChatBackup.ExportReport
import ai.closepaw.history.ChatBackup.BackupReport
import ai.closepaw.history.ChatPersistenceManager
import ai.closepaw.app.AppSettingsStore
import ai.closepaw.history.SessionStorage
import ai.closepaw.ui.settings.SettingsCard
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PageMastheadDrillDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import androidx.compose.ui.platform.LocalContext
import ai.closepaw.ui.settings.SettingsCard
import androidx.compose.material3.PageMastheadDrillDown
import ai.closepaw.ui.theme.closePaw

@Composable
internal fun BackupRestoreSettingsPage(
    onBack: () -> Unit,
    onClose: () -> Unit,
    context: Context,
    appSettingsStore: AppSettingsStore,
    sessionStorage: SessionStorage,
    onDismiss: () -> Unit = onClose,
) {
    val scope = remember { lifecycleScope }
    val persistenceManager = remember { ChatPersistenceManager(SessionStorage(context), AppSettingsStore(context)) }
    val mirror = remember { BackupMediaMirror(context) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    val exportReport by remember { mutableStateOf<ExportReport?>(null) }
    val restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
    val verifyReport by remember { mutableStateOf<ChatBackup.BackupReport?>(null) }

    val lastExportedCount by remember { mutableStateOf<Int?>(null) }
    val lastRestoredCount by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PageMastheadDrillDown(title = "Backup & Restore", onBack = onBack, onClose = onClose)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.closePaw.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.md)
        ) {
            // Export section
            SettingsCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)) {
                    Text(
                        text = "Create a portable backup file containing your chat history and non-secret settings (model, provider, URLs). The backup is a versioned, checksummed JSON file saved to your Downloads folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val out = ByteArrayOutputStream()
                                    val report = withContext(Dispatchers.IO) {
                                        val persistence = ChatPersistenceManager(SessionStorage(LocalContext.current), AppSettingsStore(LocalContext.current))
                                        persistence.exportBackup(out)
                                    }
                                    lastExportedCount.value = report.sessionCount
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Backup")
                        }
                    }
                }
            }

            // Import section
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)) {
                    Text(
                        text = "Import a backup file from your Downloads folder. Only new or newer sessions are added — existing newer data is never overwritten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)
                    ) {
                        FilledTonalButton(
                            onClick = { /* showImportDialog = true */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select Backup File")
                        }
                    }
                }
            }

            // Auto-restore status
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)) {
                    Text(
                        text = "When you uninstall and reinstall HMX, your chats will automatically restore from the most recent backup in Downloads — but only if the local store is empty. Your current chats are never overwritten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Stats
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.xs)) {
                    Text(
                        text = "Format: versioned JSON · SHA-256 checksum · zlib compressed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Secrets (API keys, OAuth tokens) are NEVER included in backups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}