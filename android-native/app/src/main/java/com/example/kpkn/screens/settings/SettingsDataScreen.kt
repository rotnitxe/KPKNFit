package com.example.kpkn.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.settings.components.SettingsActionItem
import com.example.kpkn.screens.settings.components.SettingsInfoRow
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val versionLabel = rememberVersionValue(context)
    var showResetDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

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
            item { SettingsSectionHeader("Gestion") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Exportar datos",
                        description = "Comparte un JSON con programas, historial, nutricion y recuperación",
                        icon = Icons.Default.Download,
                        onClick = { viewModel.exportData(context) },
                    )
                    SettingsActionItem(
                        title = "Restablecer ajustes",
                        description = "Vuelve todos los ajustes a sus valores por defecto",
                        icon = Icons.Default.DeleteSweep,
                        destructive = true,
                        onClick = { showResetDialog = true },
                    )
                    SettingsActionItem(
                        title = "Restablecer bienvenida",
                        description = "Permite volver a ver los tours iniciales",
                        icon = Icons.Default.Flag,
                        onClick = {
                            viewModel.resetOnboarding()
                            Toast.makeText(context, "Bienvenida restablecida", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            item { SettingsSectionHeader("Salud") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Health Connect",
                        description = "Sincroniza datos de actividad, peso y composicion corporal",
                        icon = Icons.Default.Favorite,
                        onClick = { /* Navigation handled in MainActivity */ },
                    )
                }
            }

            item { SettingsSectionHeader("Informacion") }
            item {
                SettingsSectionCard {
                    SettingsInfoRow(
                        title = "Version de la app",
                        value = versionLabel,
                    )
                    SettingsInfoRow(
                        title = "Tours vistos",
                        value = if (settings.hasSeenWelcome || settings.hasSeenHomeTour) "Si" else "No",
                    )
                    SettingsActionItem(
                        title = "Licencias open source",
                        description = "Consulta las dependencias base usadas por la app",
                        icon = Icons.Default.Info,
                        onClick = { showLicensesDialog = true },
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Restablecer ajustes") },
            text = {
                Text("Esto restablecera todos los ajustes a sus valores por defecto. Esta accion no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetSettings()
                        showResetDialog = false
                        Toast.makeText(context, "Ajustes restablecidos", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Licencias open source") },
            text = {
                Text("Base principal: Jetpack Compose Material 3, Navigation Compose, Room, Coil y kotlinx-serialization.")
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Cerrar")
                }
            },
        )
    }
}

@Composable
private fun rememberVersionValue(context: android.content.Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return "${packageInfo.versionName} (${packageInfo.longVersionCode})"
}
