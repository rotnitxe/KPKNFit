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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    healthConnectAvailable: Boolean = false,
    onOpenHealthConnect: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ioScope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }
    var telemetryFolder by remember { mutableStateOf(KpknDiagnosticStorage.configuredLabel(context)) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val localTelemetryPath = remember(context) { File(context.filesDir, "kpkn_logs").absolutePath }

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
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportData(context, uri,
            onSuccess = { Toast.makeText(context, "Backup exportado correctamente", Toast.LENGTH_LONG).show() },
            onError = { error -> Toast.makeText(context, "No se pudo exportar: $error", Toast.LENGTH_LONG).show() },
        )
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    val telemetryFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        KpknDiagnosticStorage.configure(context, uri)
            .onSuccess { label ->
                telemetryFolder = label
                Toast.makeText(context, "Ruta JSONL configurada en $label", Toast.LENGTH_LONG).show()
            }
            .onFailure { error ->
                Toast.makeText(context, "No se pudo configurar la ruta: ${error.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
            }
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
                    if (exported) "ZIP JSONL exportado" else "No se pudo exportar el JSONL",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val runtimePermissions = remember(refreshToken, context) { permissionRows(context, healthConnectAvailable) }
    val systemPermissionState = remember(refreshToken, context) { PermissionGuideHelper.getPermissionState(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                SettingsBlockTitle("Permisos")
                SettingsCard {
                    runtimePermissions.forEach { row ->
                        PermissionRow(
                            icon = row.icon,
                            title = row.title,
                            description = row.description,
                            granted = row.granted,
                            onClick = {
                                when (row.action) {
                                    PermissionAction.REQUEST -> permissionLauncher.launch(row.permissions)
                                    PermissionAction.HEALTH_CONNECT -> onOpenHealthConnect()
                                }
                            },
                        )
                    }
                    PermissionRow(
                        icon = Icons.Default.Timer,
                        title = "Recordatorios y temporizadores",
                        description = "Permite alarmas precisas y evita que el sistema detenga los avisos.",
                        granted = systemPermissionState.allOk,
                        onClick = {
                            if (!systemPermissionState.exactAlarmOk) PermissionGuideHelper.openExactAlarmSettings(context)
                            else PermissionGuideHelper.openBatteryOptimizationSettings(context)
                        },
                    )
                }
            }
            item {
                SettingsBlockTitle("Backup")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Default.Backup,
                        title = "Exportar backup",
                        description = "Guarda tus rutinas, historial, nutrición, progreso y foto en un archivo JSON.",
                        actionLabel = "Exportar",
                        onClick = { backupExportLauncher.launch("kpkn-backup.json") },
                    )
                    SettingsActionRow(
                        icon = Icons.Default.Restore,
                        title = "Importar backup",
                        description = "Reemplaza los datos actuales por un archivo exportado anteriormente.",
                        actionLabel = "Importar",
                        onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                    )
                }
            }
            item {
                SettingsBlockTitle("Telemetría JSONL")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.size(8.dp))
                            Text("Ruta especial de JSONL", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text(
                            (telemetryFolder?.let { "Espejo externo: $it\n" } ?: "Sin carpeta externa.\n") +
                                "Copia local: $localTelemetryPath\n" +
                                "Áreas: workout, voice, nutrition y app (cada una con su JSONL diario).\n" +
                                "Escritura asíncrona en lotes cada 250 ms para no bloquear la sesión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { telemetryFolderLauncher.launch(null) }) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Configurar ruta")
                            }
                            if (telemetryFolder != null) {
                                TextButton(onClick = {
                                    ioScope.launch(Dispatchers.IO) {
                                        KpknDiagnosticLogger.flushSync()
                                        KpknDiagnosticStorage.mirrorRecoveryFiles(context)
                                    }
                                    Toast.makeText(context, "Sincronización iniciada", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Sincronizar")
                                }
                                TextButton(onClick = {
                                    KpknDiagnosticStorage.clear(context)
                                    telemetryFolder = null
                                }) { Text("Desvincular") }
                            }
                            TextButton(onClick = {
                                diagnosticsExportLauncher.launch(KpknDiagnosticLogger.suggestedFileName())
                            }) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Exportar ZIP")
                            }
                        }
                    }
                }
            }
            item {
                SettingsBlockTitle("Eliminar todos los datos")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.44f)),
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.DeleteForever,
                        title = "Volver a empezar",
                        description = "Borra tu progreso, rutinas, nutrición, foto y configuraciones de la app.",
                        actionLabel = "Eliminar todo",
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
            title = { Text("¿Importar este backup?") },
            text = { Text("Se reemplazarán los datos actuales por el contenido del archivo. Esta acción no recupera cambios posteriores al backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importBackupJson(
                        context = context,
                        uri = uri,
                        onSuccess = { Toast.makeText(context, "Backup importado correctamente", Toast.LENGTH_LONG).show() },
                        onError = { error -> Toast.makeText(context, "No se pudo importar: $error", Toast.LENGTH_LONG).show() },
                    )
                }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } },
        )
    }

    if (showDeleteDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar todos los datos?") },
            text = { Text("Se borrarán de forma irreversible tus rutinas, historial, nutrición, progreso, foto y configuraciones. Los archivos de backup guardados fuera de la app no se tocarán.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllAppData(
                        context = context,
                        onSuccess = {
                            showDeleteDialog = false
                            telemetryFolder = null
                            Toast.makeText(context, "La app volvió a cero", Toast.LENGTH_LONG).show()
                        },
                        onError = { error -> Toast.makeText(context, "No se pudo borrar todo: $error", Toast.LENGTH_LONG).show() },
                    )
                }) { Text("Eliminar todo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingsIntroCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Lo esencial para que KPKN funcione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Aquí puedes conceder accesos, guardar tus datos y configurar la carpeta de telemetría. No hay ajustes técnicos ocultos en esta pantalla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        actionLabel = if (granted) "Concedido" else "Activar",
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

private enum class PermissionAction { REQUEST, HEALTH_CONNECT }

private data class PermissionRowModel(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val granted: Boolean,
    val permissions: Array<String> = emptyArray(),
    val action: PermissionAction = PermissionAction.REQUEST,
)

private fun permissionRows(context: Context, healthConnectAvailable: Boolean): List<PermissionRowModel> = buildList {
    add(PermissionRowModel(Icons.Default.Notifications, "Notificaciones", "Recordatorios, avisos de descanso y estado de la sesión.", hasPermission(context, Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU), arrayOf(Manifest.permission.POST_NOTIFICATIONS)))
    add(PermissionRowModel(Icons.Default.Mic, "Micrófono", "Control por voz durante el entrenamiento.", hasPermission(context, Manifest.permission.RECORD_AUDIO), arrayOf(Manifest.permission.RECORD_AUDIO)))
    add(PermissionRowModel(Icons.Default.LocationOn, "Ubicación", "Rastreo GPS para sesiones de cardio.", hasAnyPermission(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
    add(PermissionRowModel(Icons.Default.Bluetooth, "Bluetooth", "Conexión con audífonos y accesorios compatibles.", WorkoutVoicePermissionHelper.hasBluetoothConnectPermission(context), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()))
    add(PermissionRowModel(Icons.Default.CameraAlt, "Cámara", "Fotos opcionales de ejercicios y seguimiento.", hasPermission(context, Manifest.permission.CAMERA), arrayOf(Manifest.permission.CAMERA)))
    if (healthConnectAvailable) {
        add(PermissionRowModel(Icons.Default.SettingsBackupRestore, "Health Connect", "Sincronización opcional de salud y medidas corporales.", false, action = PermissionAction.HEALTH_CONNECT))
    }
    return@buildList
}

private fun hasPermission(context: Context, permission: String, minSdk: Int = 0): Boolean =
    Build.VERSION.SDK_INT < minSdk || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAnyPermission(context: Context, vararg permissions: String): Boolean = permissions.any { hasPermission(context, it) }
