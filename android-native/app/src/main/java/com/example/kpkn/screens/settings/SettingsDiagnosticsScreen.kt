package com.example.kpkn.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.remote.DeepSeekV4FlashClient
import com.example.kpkn.data.secure.DeepSeekCredentialStore
import com.example.kpkn.screens.reports.ReportRequestBus
import com.example.kpkn.services.diagnostics.KpknDiagnosticNotificationManager
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.diagnostics.KpknReportManager
import com.example.kpkn.services.diagnostics.ReportEnrichmentScheduler
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshToken by remember { mutableStateOf(0) }
    var selectedReport by remember { mutableStateOf<String?>(null) }
    var folderLabel by remember { mutableStateOf(KpknDiagnosticStorage.configuredLabel(context)) }
    val summaries = remember(refreshToken, folderLabel) { KpknDiagnosticLogger.areaSummaries(context) }
    val reports = remember(refreshToken) { KpknReportManager.reportMarkdownFiles(context) }
    val keyConfigured = remember(refreshToken) { DeepSeekCredentialStore.hasKey(context) }
    val allExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val exported = uri?.let { KpknDiagnosticLogger.exportAllTo(context, it) } == true
        Toast.makeText(
            context,
            if (exported) "Todos los diagnósticos exportados (ZIP completo)" else "No había diagnósticos para exportar",
            Toast.LENGTH_LONG,
        ).show()
        refreshToken += 1
    }
    val voiceExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val exported = uri?.let(WorkoutVoiceDiagnosticLogger::exportTo) == true
        Toast.makeText(
            context,
            if (exported) "Diagnósticos de voz exportados" else "No había diagnósticos de voz para exportar",
            Toast.LENGTH_LONG,
        ).show()
        refreshToken += 1
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        KpknDiagnosticStorage.configure(context, uri)
            .onSuccess { label ->
                folderLabel = label
                Toast.makeText(context, "Espejo SAF configurado en $label", Toast.LENGTH_LONG).show()
                refreshToken += 1
            }
            .onFailure { error ->
                Toast.makeText(context, "No se pudo configurar el espejo: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnósticos", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshToken += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estado de IA", fontWeight = FontWeight.Bold)
                            Text(if (keyConfigured) "Clave configurada" else "Clave ausente")
                        }
                        Text(
                            if (keyConfigured) "Proveedor activo: ${DeepSeekV4FlashClient.MODEL}"
                            else "Configurá la clave de DeepSeek en Ajustes > General para enriquecer reportes.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                ReportEnrichmentScheduler.resumePending(context)
                                Toast.makeText(context, "Reportes pendientes reenqueued", Toast.LENGTH_SHORT).show()
                            }) { Text("Generar pendientes") }
                            TextButton(onClick = { ReportRequestBus.requestGesture("settings/diagnostics") }) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(Modifier.height(1.dp))
                                Text("Reportar problema")
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Espejo automático y Exportación", fontWeight = FontWeight.Bold)
                        Text(
                            folderLabel?.let { "Activo: $it" }
                                ?: "Sin carpeta configurada. Los archivos siguen guardándose localmente.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { folderLauncher.launch(null) }) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Text("Configurar carpeta")
                            }
                            if (folderLabel != null) {
                                TextButton(onClick = {
                                    KpknDiagnosticStorage.mirrorRecoveryFiles(context)
                                    Toast.makeText(context, "Sincronizando archivos al espejo...", Toast.LENGTH_SHORT).show()
                                    refreshToken += 1
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = null)
                                    Text("Sincronizar ahora")
                                }
                            }
                            TextButton(onClick = {
                                KpknDiagnosticStorage.clear(context)
                                folderLabel = null
                                refreshToken += 1
                            }) { Text("Desvincular") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                allExportLauncher.launch(KpknDiagnosticLogger.suggestedFileName())
                            }) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Text("Exportar Todo (ZIP)")
                            }
                            TextButton(onClick = {
                                voiceExportLauncher.launch(WorkoutVoiceDiagnosticLogger.suggestedFileName() ?: "kpkn-voice-diagnostics.zip")
                            }) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Text("Solo Voz (ZIP)")
                            }
                        }
                    }
                }
            }
            item { Text("Salud por área", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(summaries, key = { it.area }) { summary ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(summary.area, fontWeight = FontWeight.Bold)
                            Text("${summary.files} archivos · ${summary.bytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(summary.lastEventAgeMin?.let { "hace ${it} min" } ?: "sin eventos")
                    }
                }
            }
            item { Text("Reportes legibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (reports.isEmpty()) {
                item { Text("Todavía no hay reportes .md generados.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(reports, key = { it.name }) { file ->
                    Card(
                        Modifier.fillMaxWidth().clickable { selectedReport = file.readText(Charsets.UTF_8) },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(file.name, fontWeight = FontWeight.Bold)
                            Text("Toca para leer el resultado del análisis", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    selectedReport?.let { markdown ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedReport = null },
            confirmButton = { TextButton(onClick = { selectedReport = null }) { Text("Cerrar") } },
            title = { Text("Reporte") },
            text = { Text(markdown) },
        )
    }
}
