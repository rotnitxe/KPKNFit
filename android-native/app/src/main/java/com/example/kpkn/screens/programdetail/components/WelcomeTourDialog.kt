package com.example.kpkn.screens.programdetail.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.kpknWindowGlass

private data class TourStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val TOUR_STEPS = listOf(
    TourStep(
        icon = Icons.Default.Build,
        title = "Entrenamiento",
        description = "La grilla muestra tu semana completa. Usa el selector de bloques/semanas para abrir la estructura. Toca una sesión para iniciar o editar.",
    ),
    TourStep(
        icon = Icons.Default.Star,
        title = "Analytics",
        description = "El panel de Analytics muestra volumen, fuerza, recuperación y más en tiempo real con inteligencia adaptativa.",
    ),
)

private suspend fun isTourSeen(context: Context, programId: String): Boolean {
    return try {
        val prefs = context.getSharedPreferences("kpkn_tour", Context.MODE_PRIVATE)
        prefs.getBoolean("tour_seen_$programId", false)
    } catch (_: Exception) {
        false
    }
}

private fun markTourSeen(context: Context, programId: String) {
    val prefs = context.getSharedPreferences("kpkn_tour", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("tour_seen_$programId", true).apply()
}

@Composable
fun rememberTourState(programId: String): TourState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tourStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(programId) {
        val seen = isTourSeen(context, programId)
        if (!seen && programId.isNotEmpty()) {
            tourStep = 1
        }
    }

    return remember(tourStep) {
        TourState(
            currentStep = tourStep,
            onNext = {
                if (tourStep < 2) tourStep++ else {
                    tourStep = 0
                    scope.launch { markTourSeen(context, programId) }
                }
            },
            onDismiss = {
                tourStep = 0
                scope.launch { markTourSeen(context, programId) }
            },
        )
    }
}

class TourState(
    val currentStep: Int,
    val onNext: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun WelcomeTourDialog(
    tourState: TourState,
    modifier: Modifier = Modifier,
) {
    if (tourState.currentStep <= 0) return

    val step = TOUR_STEPS.getOrNull(tourState.currentStep - 1) ?: return
    val isLastStep = tourState.currentStep >= 2

    Dialog(
        onDismissRequest = tourState.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = tourState.onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tourCardShape = RoundedCornerShape(40.dp)
            Card(
                modifier = modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .kpknWindowGlass(tourCardShape),
                shape = tourCardShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                // Top blue bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                        ),
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Close button
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        IconButton(
                            onClick = tourState.onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            step.icon,
                            null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Title
                    Text(
                        step.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Description
                    Text(
                        step.description,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Bottom row: dots + button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Step dots
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..2).forEach { dot ->
                                val isActive = tourState.currentStep == dot
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (isActive) Modifier.width(32.dp)
                                            else Modifier.size(12.dp)
                                        )
                                        .height(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                )
                            }
                        }

                        // Action button
                        Button(
                            onClick = tourState.onNext,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                if (isLastStep) "¡Entendido!" else "Siguiente",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
