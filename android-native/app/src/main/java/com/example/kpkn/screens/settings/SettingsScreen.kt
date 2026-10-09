package com.example.kpkn.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.workout.PermissionGuideHelper
import com.example.kpkn.services.workout.WorkoutVoicePermissionHelper
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
    dataControlsOnly: Boolean = false,
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ioScope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }
    var telemetryFolder by remember { mutableStateOf(KpknDiagnosticStorage.configuredLabel(context)) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        viewModel.setContext(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshToken++
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportData(context, uri,
            onSuccess = { Toast.makeText(context, R.string.settings_export_success, Toast.LENGTH_LONG).show() },
            onError = { Toast.makeText(context, R.string.settings_export_failed, Toast.LENGTH_LONG).show() },
        )
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    val dbSnapshotExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-sqlite3"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportDatabaseSnapshot(
            context = context,
            destination = uri,
            onSuccess = { Toast.makeText(context, R.string.settings_export_success, Toast.LENGTH_LONG).show() },
            onError = { Toast.makeText(context, R.string.settings_export_failed, Toast.LENGTH_LONG).show() },
        )
    }
    val dbSnapshotImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importDatabaseSnapshot(
            context = context,
            uri = uri,
            onSuccess = { Toast.makeText(context, R.string.settings_import_success, Toast.LENGTH_LONG).show() },
            onError = { Toast.makeText(context, R.string.settings_import_failed, Toast.LENGTH_LONG).show() },
        )
    }
    val telemetryFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ioScope.launch {
        withContext(Dispatchers.IO) { KpknDiagnosticStorage.configure(context, uri) }
            .onSuccess { label ->
                telemetryFolder = label
                Toast.makeText(context, R.string.settings_folder_success, Toast.LENGTH_LONG).show()
            }
            .onFailure {
                Toast.makeText(context, R.string.settings_folder_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    val mediaZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportWorkoutMediaZip(
            context,
            uri,
            onSuccess = { count ->
                Toast.makeText(
                    context,
                    if (count == 0) resources.getString(R.string.settings_media_empty) else resources.getString(R.string.settings_media_exported, count),
                    Toast.LENGTH_LONG,
                ).show()
            },
            onError = { Toast.makeText(context, R.string.settings_export_failed, Toast.LENGTH_LONG).show() },
        )
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ioScope.launch(Dispatchers.IO) {
            KpknDiagnosticLogger.flushSync()
            val exported = KpknDiagnosticLogger.exportAllTo(context, uri)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (exported) resources.getString(R.string.settings_diagnostics_exported) else resources.getString(R.string.settings_diagnostics_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val runtimePermissions = remember(refreshToken, context) { permissionRows(context) }
    val systemPermissionState = remember(refreshToken, context) { PermissionGuideHelper.getPermissionState(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsIntroCard()
            }
            item {
                Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.example.kpkn.R.string.account_title)) }
            }
            if (!dataControlsOnly) item {
                SettingsBlockTitle(stringResource(R.string.settings_permissions))
                SettingsCard {
                    runtimePermissions.forEach { row ->
                        PermissionRow(
                            icon = row.icon,
                            title = row.title,
                            description = row.description,
                            granted = row.granted,
                            onClick = {
                                permissionLauncher.launch(row.permissions)
                            },
                        )
                    }
                    PermissionRow(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.settings_timer_title),
                        description = stringResource(R.string.settings_timer_description),
                        granted = systemPermissionState.allOk,
                        onClick = {
                            if (!systemPermissionState.exactAlarmOk) PermissionGuideHelper.openExactAlarmSettings(context)
                            else PermissionGuideHelper.openBatteryOptimizationSettings(context)
                        },
                    )
                }
            }
            if (!dataControlsOnly) item {
                SettingsBlockTitle("Recorridos")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Default.Restore,
                        title = "Repetir el recorrido",
                        description = "Vuelve a mostrar las explicaciones de cada pantalla la próxima vez que entres.",
                        actionLabel = "Repetir",
                        onClick = {
                            viewModel.resetTours()
                            Toast.makeText(context, "Recorridos restablecidos", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            item {
                SettingsBlockTitle(stringResource(R.string.settings_data))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.settings_media_title),
                        description = stringResource(R.string.settings_media_description),
                        actionLabel = stringResource(R.string.settings_export),
                        onClick = { mediaZipLauncher.launch("kpkn-workout-media.zip") },
                    )
                }
            }
            item {
                SettingsBlockTitle(stringResource(R.string.settings_backup))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Default.Backup,
                        title = androidx.compose.ui.res.stringResource(com.example.kpkn.R.string.portable_export_title),
                        description = androidx.compose.ui.res.stringResource(com.example.kpkn.R.string.portable_export_description),
                        actionLabel = stringResource(R.string.settings_export),
                        onClick = { backupExportLauncher.launch("kpkn-backup.kpkn.zip") },
                    )
                    if (!dataControlsOnly) SettingsActionRow(
                        icon = Icons.Default.Restore,
                        title = androidx.compose.ui.res.stringResource(com.example.kpkn.R.string.portable_import_title),
                        description = stringResource(R.string.settings_restore_description),
                        actionLabel = stringResource(R.string.settings_import),
                        onClick = { backupImportLauncher.launch(arrayOf("application/zip", "application/json", "text/plain")) },
                    )
                    SettingsActionRow(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = stringResource(R.string.settings_database_export_title),
                        description = stringResource(R.string.settings_database_export_description),
                        actionLabel = stringResource(R.string.settings_database_export_action),
                        onClick = { dbSnapshotExportLauncher.launch("kpkn-backup.db") },
                    )
                    if (!dataControlsOnly) SettingsActionRow(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = stringResource(R.string.settings_database_import_title),
                        description = stringResource(R.string.settings_database_import_description),
                        actionLabel = stringResource(R.string.settings_database_import_action),
                        onClick = { dbSnapshotImportLauncher.launch(arrayOf("application/x-sqlite3", "application/octet-stream", "*/*")) },
                    )
                }
            }
            if (!dataControlsOnly) item {
                SettingsBlockTitle(stringResource(R.string.settings_diagnostics))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.settings_diagnostics_folder_title), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text(
                            stringResource(R.string.settings_diagnostics_description) + "\n" +
                                (telemetryFolder?.let { stringResource(R.string.settings_diagnostics_external, it) }
                                    ?: stringResource(R.string.settings_diagnostics_private)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { telemetryFolderLauncher.launch(null) }) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.settings_folder_select))
                            }
                            if (telemetryFolder != null) {
                                TextButton(onClick = {
                                    ioScope.launch {
                                        val saved = withContext(Dispatchers.IO) { runCatching {
                                            KpknDiagnosticLogger.flushSync()
                                            KpknDiagnosticStorage.mirrorRecoveryFilesAndWait(context)
                                        }.isSuccess }
                                        Toast.makeText(context, if (saved) R.string.settings_mirror_success else R.string.settings_mirror_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text(stringResource(R.string.settings_mirror))
                                }
                                TextButton(onClick = {
                                    ioScope.launch {
                                        runCatching { withContext(Dispatchers.IO) { KpknDiagnosticStorage.clear(context) } }
                                            .onSuccess { telemetryFolder = null }
                                            .onFailure { Toast.makeText(context, R.string.settings_folder_failed, Toast.LENGTH_LONG).show() }
                                    }
                                }) { Text(stringResource(R.string.settings_unlink)) }
                            }
                            TextButton(onClick = {
                                diagnosticsExportLauncher.launch(KpknDiagnosticLogger.suggestedFileName())
                            }) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.settings_diagnostics_export))
                            }
                        }
                    }
                }
            }
            item {
                SettingsBlockTitle(stringResource(R.string.settings_delete_heading))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.44f)),
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.DeleteForever,
                        title = stringResource(R.string.settings_delete_title),
                        description = stringResource(R.string.settings_delete_description),
                        actionLabel = stringResource(R.string.settings_delete_action),
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    )
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    pendingImport?.let { uri ->
        KpknAlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_import_confirm_description),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importBackupJson(
                        context = context,
                        uri = uri,
                        onSuccess = { Toast.makeText(context, R.string.settings_import_success, Toast.LENGTH_LONG).show() },
                        onError = { Toast.makeText(context, R.string.settings_import_failed, Toast.LENGTH_LONG).show() },
                    )
                }) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (showDeleteDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllAppData(
                        context = context,
                        onSuccess = {
                            showDeleteDialog = false
                            telemetryFolder = null
                            Toast.makeText(context, R.string.settings_delete_success, Toast.LENGTH_LONG).show()
                        },
                        onError = { Toast.makeText(context, R.string.settings_delete_failed, Toast.LENGTH_LONG).show() },
                    )
                }) { Text(stringResource(R.string.settings_delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun SettingsIntroCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_intro_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.settings_intro_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsBlockTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    SettingsActionRow(
        icon = icon,
        title = title,
        description = description,
        actionLabel = stringResource(if (granted) R.string.settings_granted else R.string.settings_enable),
        onClick = onClick,
        destructive = !granted,
    )
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, description: String, actionLabel: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(actionLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
}

private data class PermissionRowModel(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val granted: Boolean,
    val permissions: Array<String> = emptyArray(),
)

private fun permissionRows(context: Context): List<PermissionRowModel> = buildList {
    add(PermissionRowModel(Icons.Default.Notifications, context.getString(R.string.settings_notification_title), context.getString(R.string.settings_notification_description), hasPermission(context, Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU), arrayOf(Manifest.permission.POST_NOTIFICATIONS)))
    add(PermissionRowModel(Icons.Default.Mic, context.getString(R.string.settings_microphone_title), context.getString(R.string.settings_microphone_description), hasPermission(context, Manifest.permission.RECORD_AUDIO), arrayOf(Manifest.permission.RECORD_AUDIO)))
    add(PermissionRowModel(Icons.Default.LocationOn, context.getString(R.string.settings_location_title), context.getString(R.string.settings_location_description), hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
    add(PermissionRowModel(Icons.Default.Bluetooth, context.getString(R.string.settings_bluetooth_title), context.getString(R.string.settings_bluetooth_description), WorkoutVoicePermissionHelper.hasBluetoothConnectPermission(context), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()))
    add(PermissionRowModel(Icons.Default.CameraAlt, context.getString(R.string.settings_camera_title), context.getString(R.string.settings_camera_description), hasPermission(context, Manifest.permission.CAMERA), arrayOf(Manifest.permission.CAMERA)))
    return@buildList
}

private fun hasPermission(context: Context, permission: String, minSdk: Int = 0): Boolean =
    Build.VERSION.SDK_INT < minSdk || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAnyPermission(context: Context, vararg permissions: String): Boolean = permissions.any { hasPermission(context, it) }
