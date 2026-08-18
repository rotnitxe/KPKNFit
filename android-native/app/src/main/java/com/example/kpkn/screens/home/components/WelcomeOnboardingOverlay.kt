package com.example.kpkn.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.home.OnboardingState
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/**
 * Overlay de bienvenida de primera vez. Mismo lenguaje visual que
 * [NutritionTodayGlassOverlay] (scrim + tarjeta glass) y botones de tarea
 * idénticos al botón "Agregar comida" (TextButton con icono Add 16dp,
 * spacer 6dp y texto en FontWeight.Black).
 */
@Composable
fun WelcomeOnboardingOverlay(
    state: OnboardingState,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onCreateProgram: (String) -> Unit,
    onNavigateToNutritionWizard: () -> Unit,
    onAllTasksDone: () -> Unit,
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var nameSaved by rememberSaveable { mutableStateOf(false) }
    var showProgramNameDialog by remember { mutableStateOf(false) }

    // Cuando ambas tareas están hechas, mostrar los checks un instante y cerrar.
    LaunchedEffect(state.allTasksDone) {
        if (state.allTasksDone) {
            delay(1800)
            onAllTasksDone()
        }
    }

    // Recupera la confirmación del nombre desde el estado persistido: al volver
    // del NutritionWizard el overlay se re-crea y nameSaved (rememberSaveable
    // local) se pierde; el nombre guardado vive en Settings.
    LaunchedEffect(state.displayName) {
        if (state.displayName.isNotBlank() && state.displayName != "Usuario") nameSaved = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // El scrim consume los toques para que no operen Home/NavigationBar
            // "por detrás"; la tarjeta hija sigue recibiendo sus propios toques.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .kpknGlass(hazeState, RoundedCornerShape(28.dp)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "¡Bienvenido a KPKN!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tu suite completa de entrenamiento",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.66f),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar por ahora",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }

                Text(
                    "Somos más que una app para el gym. Acá podrás programar tus entrenamientos, " +
                        "crear planes de alimentación, registrar tus comidas y ver tu progreso; " +
                        "una suite completa para que completes tus objetivos, respetando tu privacidad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // ── Nombre ────────────────────────────────────────────────────
                if (nameSaved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF8FB7B8),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "¡Listo! Te llamaremos ${state.displayName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }
                } else {
                    Text(
                        "Cómo quieres que te llamemos",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tu nombre", color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = true,
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Escribe al menos 3 caracteres o usa Omitir", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF8FB7B8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color(0xFF8FB7B8),
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val trimmed = nameInput.trim()
                                if (trimmed.length < 3) {
                                    nameError = true
                                } else {
                                    onSaveName(trimmed)
                                    nameSaved = true
                                }
                            },
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Black)
                        }
                        TextButton(
                            onClick = {
                                onSaveName("Usuario")
                                nameSaved = true
                            },
                        ) {
                            Text("Omitir", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // ── Tarea 1: programa de entrenamiento ─────────────────────────
                OnboardingTaskRow(
                    done = state.programDone,
                    title = "Crea tu primer programa de entrenamiento",
                    subtitle = if (state.programDone) "Programa creado" else "Ponle nombre y listo",
                    actionLabel = "Crear programa",
                    onAction = { showProgramNameDialog = true },
                )

                if (state.programDone) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.06f),
                    ) {
                        Text(
                            "Un programa de entrenamiento es la base para planificar tus sesiones en el gimnasio, " +
                                "en la pestaña de \"Entreno\" podrás añadir sesiones a tu semana, " +
                                "o podrás configurarlo para opciones avanzadas.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }

                // ── Tarea 2: plan de alimentación ──────────────────────────────
                OnboardingTaskRow(
                    done = state.nutritionDone,
                    title = "Crea tu plan de alimentación",
                    subtitle = if (state.nutritionDone) "Plan creado" else "Te guiamos paso a paso",
                    actionLabel = "Crear plan",
                    onAction = onNavigateToNutritionWizard,
                )
            }
        }
    }

    if (showProgramNameDialog) {
        CreateProgramNameDialog(
            onDismiss = { showProgramNameDialog = false },
            onConfirm = { programName ->
                showProgramNameDialog = false
                onCreateProgram(programName)
            },
        )
    }
}

@Composable
private fun OnboardingTaskRow(
    done: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (done) "Hecho" else "Pendiente",
            tint = if (done) Color(0xFF8FB7B8) else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        if (!done) {
            // Botón idéntico al "Agregar comida" del registro de hoy.
            TextButton(onClick = onAction) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CreateProgramNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var programName by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo programa", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Cómo se llamará tu programa de entrenamiento?")
                OutlinedTextField(
                    value = programName,
                    onValueChange = {
                        programName = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. Hypertrophy I") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Ponle un nombre al programa", color = MaterialTheme.colorScheme.error) }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (programName.trim().isEmpty()) {
                        showError = true
                    } else {
                        onConfirm(programName)
                    }
                },
            ) {
                Text("Crear", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontWeight = FontWeight.Bold)
            }
        },
    )
}