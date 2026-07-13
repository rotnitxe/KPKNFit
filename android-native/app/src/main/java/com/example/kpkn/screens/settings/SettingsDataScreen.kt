package com.example.kpkn.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.settings.components.SettingsActionItem
import com.example.kpkn.screens.settings.components.SettingsInfoRow
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val versionLabel = rememberVersionValue(context)

    var snapshots by remember { mutableStateOf(viewModel.getSnapshots(context)) }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    var snapshotToRestore by remember { mutableStateOf<File?>(null) }
    var snapshotToDelete by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBackupJson(
                context = context,
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "Respaldo importado con éxito. Datos recargados.", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Error al importar el respaldo: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datos y app", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SettingsSectionHeader("Respaldo y Migración") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Exportar archivo de respaldo",
                        description = "Genera un JSON compartible con tus rutinas, historial, nutrición y progreso.",
                        icon = Icons.Default.Download,
                        onClick = { viewModel.exportData(context) },
                    )
                    SettingsActionItem(
                        title = "Importar archivo de respaldo",
                        description = "Carga un archivo JSON exportado previamente para clonar tu progreso.",
                        icon = Icons.Default.Upload,
                        onClick = { filePickerLauncher.launch("application/json") },
                    )
                }
            }

            item { SettingsSectionHeader("Snapshots (Recuperación ante Errores)") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Crear snapshot de base de datos",
                        description = "Guarda una copia de seguridad física de la base de datos actual.",
                        icon = Icons.Default.Backup,
                        onClick = {
                            viewModel.createSnapshot(
                                context = context,
                                onSuccess = { filename ->
                                    snapshots = viewModel.getSnapshots(context)
                                    Toast.makeText(context, "Snapshot creado con éxito: $filename", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Error al crear snapshot: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                    )
                }
            }

            if (snapshots.isNotEmpty()) {
                item { SettingsSectionHeader("Snapshots Guardados") }
                items(snapshots, key = { it.absolutePath }) { file ->
                    SettingsSectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatSnapshotName(file.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { snapshotToRestore = file }) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Restaurar",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { snapshotToDelete = file }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Borrar",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SettingsSectionHeader("Limpieza de Datos") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Restablecer de cero",
                        description = "Elimina permanentemente todo tu progreso, entrenamientos, nutrición y configuraciones.",
                        icon = Icons.Default.DeleteForever,
                        destructive = true,
                        onClick = { showClearDataDialog = true },
                    )
                    SettingsActionItem(
                        title = "Restablecer visitas de bienvenida",
                        description = "Te permitirá volver a ver los tutoriales y tours iniciales.",
                        icon = Icons.Default.Flag,
                        onClick = {
                            viewModel.resetOnboarding()
                            Toast.makeText(context, "Visitas iniciales restablecidas", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            item { SettingsSectionHeader("Información") }
            item {
                SettingsSectionCard {
                    SettingsInfoRow(
                        title = "Versión de la app",
                        value = versionLabel,
                    )
                    SettingsInfoRow(
                        title = "Tours iniciales completados",
                        value = if (settings.hasSeenWelcome || settings.hasSeenHomeTour) "Sí" else "No",
                    )
                    SettingsActionItem(
                        title = "Licencias de código abierto",
                        description = "Consulta las tecnologías base de este desarrollo.",
                        icon = Icons.Default.Info,
                        onClick = { showLicensesDialog = true },
                    )
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("¿Restablecer aplicación de cero?") },
            text = {
                Text("Esta acción eliminará de forma irreversible toda tu información (logs de nutrición, historial de entrenamientos, programas y configuraciones). La aplicación se reiniciará completamente en blanco.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllAppData(
                            context = context,
                            onSuccess = {
                                snapshots = emptyList()
                                showClearDataDialog = false
                                Toast.makeText(context, "Todos los datos han sido borrados con éxito", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "Error al borrar datos: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                ) {
                    Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (snapshotToRestore != null) {
        AlertDialog(
            onDismissRequest = { snapshotToRestore = null },
            title = { Text("¿Restaurar snapshot?") },
            text = {
                Text("Se reemplazará toda la base de datos actual con los datos respaldados en este snapshot (${snapshotToRestore?.name?.let { formatSnapshotName(it) }}). Cualquier cambio desde entonces se perderá.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        snapshotToRestore?.let { file ->
                            viewModel.restoreSnapshot(
                                context = context,
                                file = file,
                                onSuccess = {
                                    snapshotToRestore = null
                                    Toast.makeText(context, "Snapshot restaurado con éxito", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Error al restaurar: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToRestore = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (snapshotToDelete != null) {
        AlertDialog(
            onDismissRequest = { snapshotToDelete = null },
            title = { Text("¿Eliminar snapshot?") },
            text = {
                Text("¿Estás seguro de que deseas eliminar permanentemente este snapshot de seguridad?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        snapshotToDelete?.let { file ->
                            val deleted = viewModel.deleteSnapshot(file)
                            if (deleted) {
                                snapshots = viewModel.getSnapshots(context)
                                Toast.makeText(context, "Snapshot eliminado", Toast.LENGTH_SHORT).show()
                            }
                            snapshotToDelete = null
                        }
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Licencias de código abierto") },
            text = {
                Text("Este desarrollo nativo utiliza: Jetpack Compose, Navigation Compose, Room Database, Material Design 3 y Kotlinx Serialization.")
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Cerrar")
                }
            },
        )
    }
}

private fun formatSnapshotName(filename: String): String {
    return runCatching {
        val parts = filename.removePrefix("kpkn_snapshot_").removeSuffix(".db").split("_")
        val datePart = parts[0] // 20260710
        val timePart = parts[1] // 231500
        val parser = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val parsedDate = parser.parse(datePart + timePart)
        parsedDate?.let { formatter.format(it) } ?: filename
    }.getOrDefault(filename)
}

@Composable
private fun rememberVersionValue(context: android.content.Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return "${packageInfo.versionName} (${packageInfo.longVersionCode})"
}
