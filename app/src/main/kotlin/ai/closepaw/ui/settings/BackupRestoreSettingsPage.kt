package ai.closepaw.ui.settings

import ai.closepaw.app.AppSettingsStore
import ai.closepaw.storage.BackupManager
import ai.closepaw.storage.ClosePawStorage
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
import java.io.File
import androidx.compose.ui.Modifier
import ai.closepaw.ui.theme.PageMastheadDrillDown
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
    val closePawStorage = remember { ClosePawStorage.getInstance(context) }
    val backupManager = remember { BackupManager(context, closePawStorage) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    val exportReport by remember { mutableStateOf<BackupManager.BackupReport?>(null) }
    val restoreReport by remember { mutableStateOf<BackupManager.RestoreReport?>(null) }

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
                        text = "Create a portable backup file containing your chat history and non-secret settings (model, provider, URLs). The backup is a versioned, checksummed JSON file saved to Internal Storage/ClosePaw/backups/.",
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
                                    val report = withContext(Dispatchers.IO) {
                                        backupManager.createBackup()
                                    }
                                    lastExportedCount.value = report.sessionCount
                                    resultMessage = "Exported ${report.sessionCount} sessions"
                                    showResultDialog = true
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
                        text = "Import a backup file from Internal Storage/ClosePaw/backups/. Only new or newer sessions are added — existing newer data is never overwritten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)
                    ) {
                        FilledTonalButton(
                            onClick = { showImportDialog = true },
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
                        text = "When you uninstall and reinstall HMX, your chats will automatically restore from the most recent backup in Internal Storage/ClosePaw/backups/ — but only if the local store is empty. Your current chats are never overwritten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Backup list
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.sm)) {
                    Text(
                        text = "Available Backups",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val backups = remember { mutableStateOf(backupManager.listBackups()) }
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.closePaw.spacing.xs)) {
                        backups.value.forEach { backup ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Backup: ${backup.file.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${backup.sessionCount} sessions · ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(backup.timestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            val report = withContext(Dispatchers.IO) {
                                                backupManager.restoreBackup(backup.file)
                                            }
                                            if (report.success) {
                                                lastRestoredCount.value = report.restoredCount
                                                resultMessage = "Restored ${report.restoredCount} sessions"
                                            } else {
                                                resultMessage = "Restore failed: ${report.errorMessage}"
                                            }
                                            showResultDialog = true
                                            backups.value = backupManager.listBackups()
                                        }
                                    }
                                ) {
                                    Text("Restore")
                                }
                            }
                        }
                        if (backups.value.isEmpty()) {
                            Text(
                                text = "No backups found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
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
                    Text(
                        text = "Location: Internal Storage/ClosePaw/backups/ (survives uninstall)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}