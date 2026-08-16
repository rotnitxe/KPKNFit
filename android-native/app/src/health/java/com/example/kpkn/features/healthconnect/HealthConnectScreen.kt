package com.example.kpkn.features.healthconnect

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val healthConnectRepo = remember { HealthConnectRepository.getInstance(context) }
    val augeHealthIntegration = remember { AugeHealthIntegration.getInstance(context) }
    
    val isAvailable by healthConnectRepo.isAvailable.collectAsState(initial = false)
    val hasPermissions by healthConnectRepo.hasPermissions.collectAsState(initial = false)
    val hasWritePermissions by healthConnectRepo.hasWritePermissions.collectAsState(initial = false)
    val lastSyncDate by healthConnectRepo.lastSyncDate.collectAsState(initial = null)
    val currentMetrics by healthConnectRepo.currentMetrics.collectAsState(initial = HealthMetrics())
    val healthImpact by augeHealthIntegration.healthImpact.collectAsState(initial = null)
    val isSyncing by healthConnectRepo.isSyncing.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        healthConnectRepo.checkAvailability()
        healthConnectRepo.checkPermissions()
    }
    
    var showSyncDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAvailable && hasPermissions)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isAvailable && hasPermissions) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isAvailable && hasPermissions) "Conectado" else "No disponible",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (!isAvailable) "Health Connect no está instalado"
                            else if (!hasPermissions) "Permisos no otorgados"
                            else if (!hasWritePermissions) "Lectura corporal activa · escritura opcional pendiente"
                            else "Lectura y escritura corporal activas",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Last Sync Info
            if (lastSyncDate != null) {
                Text(
                    "Última sincronización: ${lastSyncDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Sync Button
            Button(
                onClick = { showSyncDialog = true },
                enabled = isAvailable && hasPermissions && !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSyncing) "Sincronizando..." else "Sincronizar datos")
            }
            
            // Permissions Button
            if (!hasPermissions && isAvailable) {
                OutlinedButton(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otorgar permisos")
                }
            }
            
            // Current Metrics
            if (currentMetrics.totalSteps > 0 || currentMetrics.latestWeightKg != null) {
                Text(
                    "Datos actuales",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (currentMetrics.totalSteps > 0) {
                        MetricCard(
                            icon = Icons.Default.DirectionsWalk,
                            value = "${currentMetrics.totalSteps}",
                            label = "Pasos"
                        )
                    }
                    
                    if (currentMetrics.totalExerciseMinutes > 0) {
                        MetricCard(
                            icon = Icons.Default.FitnessCenter,
                            value = "${currentMetrics.totalExerciseMinutes} min",
                            label = "Ejercicio"
                        )
                    }
                    
                    if (currentMetrics.latestWeightKg != null) {
                        MetricCard(
                            icon = Icons.Default.MonitorWeight,
                            value = "${String.format("%.1f", currentMetrics.latestWeightKg)} kg",
                            label = "Peso"
                        )
                    }
                }
            }
            
            // AUGE Impact
            healthImpact?.let { impact ->
                Text(
                    "Impacto AUGE",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Modificador de readiness:")
                            Text(
                                "${String.format("%.2f", impact.readinessModifier)}x",
                                color = when {
                                    impact.readinessModifier > 1.05f -> MaterialTheme.colorScheme.primary
                                    impact.readinessModifier < 0.95f -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        
                        impact.activityImpact.let { activity ->
                            LinearProgressIndicator(
                                progress = { (activity.score / 1.15f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                activity.recommendation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (impact.insights.isNotEmpty()) {
                            Divider()
                            Text(
                                "Insights",
                                style = MaterialTheme.typography.labelMedium
                            )
                            impact.insights.forEach { insight ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("• ", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        insight,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Info Text
            Text(
                "Health Connect sincroniza datos de actividad física, peso y composición corporal desde tu dispositivo para mejorar los cálculos de fatigue y recovery de AUGE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // Sync Confirmation Dialog
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Sincronizar datos") },
            text = { Text("¿Deseas sincronizar los últimos 7 días de datos desde Health Connect?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSyncDialog = false
                        scope.launch {
                            augeHealthIntegration.checkAndSyncHealthData()
                        }
                    }
                ) {
                    Text("Sincronizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null)
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
