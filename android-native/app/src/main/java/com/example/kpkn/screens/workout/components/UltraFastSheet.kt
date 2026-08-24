package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.ui.components.KpknSheet

private fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (s == 0) "${m} min" else "${m}m ${s.toString().padStart(2, '0')}s"
}

@Composable
fun UltraFastPreviewSheet(
    preview: UltraFastPreview?,
    savedSeconds: Int,
    visibleExercises: List<Exercise>,
    ultraFastManualOverrides: Map<String, Boolean>,
    onToggleOverride: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (preview == null) {
        KpknSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Modo Ultrarrápido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                Text("Calculando compresión de sesión…", color = Color.White.copy(0.7f))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
        return
    }
    val totalSaved = preview.savedSeconds.takeIf { it > 0 } ?: savedSeconds
    val beforeSec = preview.beforeSeconds.coerceAtLeast(60)
    val afterSec = preview.afterSeconds.coerceAtLeast(60)

    // State for local adjustments to dropsets / rest-pauses per exercise
    var customSetCounts by remember { mutableStateOf(mapOf<String, Int>()) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Clean, bold typography title (No AI icon)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Modo Ultrarrápido",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    "Optimiza descansos y densifica series para finalizar en menos tiempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                )
            }

            // Visual Comparative Duration Chart
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1D1E20),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val maxSec = maxOf(beforeSec, afterSec).toFloat()

                    // Row 1: Duración Estándar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Duración estándar", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
                            Text(formatSeconds(beforeSec), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            val standardProgress by animateFloatAsState(
                                targetValue = (beforeSec / maxSec).coerceIn(0.1f, 1f),
                                animationSpec = tween(600),
                                label = "standardProgress",
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(standardProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.35f)),
                            )
                        }
                    }

                    // Row 2: Duración Modo Ultrarrápido
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Con Modo Ultrarrápido", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF66BB6A))
                            Text(formatSeconds(afterSec), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF66BB6A))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            val ultraProgress by animateFloatAsState(
                                targetValue = (afterSec / maxSec).coerceIn(0.1f, 1f),
                                animationSpec = tween(600),
                                label = "ultraProgress",
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ultraProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF66BB6A)),
                            )
                        }
                    }

                    // Savings Pill
                    if (totalSaved > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF66BB6A).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "⚡ Ahorras ~${formatSeconds(totalSaved)} (${((totalSaved.toFloat() / beforeSec) * 100).toInt()}% más rápida)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF66BB6A),
                                )
                            }
                        }
                    }
                }
            }

            // Per-exercise changes (Minimalist & Configurable)
            Text("Ajustes por ejercicio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.perExercise.forEach { ch ->
                    val isOverridden = ultraFastManualOverrides[ch.exerciseId]
                    val count = customSetCounts[ch.exerciseId] ?: ch.afterSets
                    val reasonLabel = when (ch.reason) {
                        UltraFastReason.PROTECTED_BASIC -> "Básico protegido · ${ch.beforeSets} → $count series"
                        UltraFastReason.DANGEROUS_COMPLEX -> "Complejo · ${ch.beforeSets} → $count series"
                        UltraFastReason.ISOLATION_DENSIFIED -> "Aislado · ${ch.beforeSets} series → $count× ${ch.afterTechnique}"
                        UltraFastReason.MANUAL_OVERRIDE_ALLOWED -> "Manual: forzado a ${ch.afterTechnique}"
                        else -> "${ch.beforeSets} → $count series · ${ch.afterTechnique}"
                    }
                    val badgeBg = when {
                        ch.wasReduced -> Color(0xFF90CAF9)
                        ch.wasDensified -> Color(0xFF81C784)
                        else -> Color.White.copy(alpha = 0.20f)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1D1E20),
                        border = BorderStroke(1.dp, Color.White.copy(0.10f)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ch.exerciseName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = badgeBg.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, badgeBg.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        if (ch.wasDensified) ch.afterTechnique else if (ch.wasReduced) "${ch.beforeSets}→$count" else "Sin cambio",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = badgeBg,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    reasonLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(0.65f),
                                    modifier = Modifier.weight(1f),
                                )

                                // Mini Stepper to configure set count / dropset count
                                if (ch.wasDensified || ch.wasReduced) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Surface(
                                            onClick = {
                                                if (count > 1) customSetCounts = customSetCounts + (ch.exerciseId to count - 1)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                                        }
                                        Text(
                                            "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                        Surface(
                                            onClick = {
                                                if (count < 6) customSetCounts = customSetCounts + (ch.exerciseId to count + 1)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Manual override toggle for protected/dangerous exercises
                            if (ch.reason == UltraFastReason.PROTECTED_BASIC || ch.reason == UltraFastReason.DANGEROUS_COMPLEX) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = isOverridden == true,
                                        onClick = { onToggleOverride(ch.exerciseId) },
                                        label = { Text(if (isOverridden == true) "Permitir técnica intensa" else "Mantener protegido") },
                                    )
                                    if (isOverridden == true) {
                                        Text("⚠︎ Mayor riesgo", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Supersets summary
            if (preview.supersets.isNotEmpty()) {
                Text("Supersets (misma máquina, antagónicos)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                preview.supersets.forEach { ss ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1D1E20), border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.25f))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, tint = Color(0xFF66BB6A), modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${ss.nameA} + ${ss.nameB}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Máquina: ${ss.machineKey} · antagónicos", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(0.62f))
                            }
                        }
                    }
                }
            }

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Text("Cancelar", color = Color.White)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    enabled = totalSaved >= 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aplicar", fontWeight = FontWeight.Black)
                }
            }
            Text(
                "Reversible: podrás deshacer desde el banner o el menú de tiempo.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.45f),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun UltraFastAppliedBanner(
    savedSeconds: Int,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF222222),
        border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Modo Ultrarrápido activo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF66BB6A))
                Text(
                    if (savedSeconds > 0) "Ahorro ~${formatSeconds(savedSeconds)} · Deshacer revierte la sesión"
                    else "Aplicado · Deshacer disponible",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(0.78f),
                )
            }
            TextButton(onClick = onUndo) { Text("Deshacer", fontWeight = FontWeight.Black, color = Color(0xFF66BB6A)) }
            TextButton(onClick = onDismiss) { Text("×", color = Color.White.copy(0.6f)) }
        }
    }
}
