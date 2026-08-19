package com.example.kpkn.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.home.OnboardingState
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState

/**
 * Overlay de bienvenida rediseñado: título centrado, 3 tarjetas uniformes con check,
 * inputs inline semitransparentes y flujo SIGUIENTE → resumen.
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
    var nameExpanded by rememberSaveable { mutableStateOf(false) }
    var programExpanded by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var programNameInput by rememberSaveable { mutableStateOf("") }
    var programNameError by rememberSaveable { mutableStateOf(false) }

    val successGreen = Color(0xFF4CAF50)

    // Si se completa todo y se estaba en resumen, no resetear; si se deshace algo, volver al onboarding
    LaunchedEffect(state.allTasksDone) {
        if (!state.allTasksDone && showSummary) showSummary = false
    }
    // Autocolapsar tarjetas cuando ya están done
    LaunchedEffect(state.nameDone) { if (state.nameDone) nameExpanded = false }
    LaunchedEffect(state.programDone) { if (state.programDone) programExpanded = false }
    LaunchedEffect(state.displayName) {
        // Sincronizar input con nombre guardado si viene persistido
        if (state.nameDone && nameInput.isBlank() && state.displayName != "Usuario") {
            // no-op, mantener input vacío para próxima edición
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
                if (!showSummary) {
                    // ── Header centrado ──────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "¡Bienvenido a KPKN!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar por ahora",
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Text(
                        "Somos más que una app para el gimnasio. Aquí podrás programar tus entrenamientos, " +
                            "crear planes de alimentación, registrar tus comidas y ver tu progreso; " +
                            "una suite completa para que completes tus objetivos, respetando tu privacidad.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // ── Tarjeta 1: Nombre ────────────────────────────────────
                    val nameDone = state.nameDone
                    OnboardingCard(
                        done = nameDone,
                        title = if (nameDone) "Nombre elegido" else "Elige tu nombre",
                        subtitle = if (nameDone) "¡Te llamaremos ${state.displayName}!" else "Toca para escribir cómo te llamaremos",
                        expanded = nameExpanded && !nameDone,
                        onToggle = { if (!nameDone) nameExpanded = !nameExpanded },
                    ) {
                        TextField(
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
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                errorContainerColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = Color(0xFF8FB7B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color.White.copy(alpha = 0.6f),
                                errorTextColor = Color.White,
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    onSaveName("Usuario")
                                    nameInput = ""
                                    nameExpanded = false
                                },
                            ) {
                                Text("Omitir", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                            }
                            Button(
                                onClick = {
                                    val trimmed = nameInput.trim()
                                    if (trimmed.length < 3) {
                                        nameError = true
                                    } else {
                                        onSaveName(trimmed)
                                        nameInput = ""
                                        nameExpanded = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB7B8), contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Guardar", fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // ── Tarjeta 2: Programa ──────────────────────────────────
                    val programDone = state.programDone
                    val programSubtitle = if (programDone) {
                        state.programName?.let { "Programa \"$it\" activo" } ?: "Programa creado y activado"
                    } else "Toca para ponerle nombre y activarlo"

                    OnboardingCard(
                        done = programDone,
                        title = if (programDone) "Programa creado" else "Crea tu programa",
                        subtitle = programSubtitle,
                        expanded = programExpanded && !programDone,
                        onToggle = { if (!programDone) programExpanded = !programExpanded },
                    ) {
                        TextField(
                            value = programNameInput,
                            onValueChange = {
                                programNameInput = it
                                programNameError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej. Hypertrophy I", color = Color.White.copy(alpha = 0.4f)) },
                            singleLine = true,
                            isError = programNameError,
                            supportingText = if (programNameError) {
                                { Text("Ponle un nombre al programa", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                errorContainerColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = Color(0xFF8FB7B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color.White.copy(alpha = 0.6f),
                                errorTextColor = Color.White,
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { programExpanded = false; programNameInput = "" }) {
                                Text("Cancelar", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                            }
                            Button(
                                onClick = {
                                    val trimmed = programNameInput.trim()
                                    if (trimmed.isEmpty()) {
                                        programNameError = true
                                    } else {
                                        onCreateProgram(trimmed)
                                        programNameInput = ""
                                        programExpanded = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB7B8), contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Crear y activar", fontWeight = FontWeight.Black)
                            }
                        }
                        if (!programDone) {
                            Text(
                                "Se activará de inmediato, sin necesidad de ir a Entreno.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }

                    // ── Tarjeta 3: Nutrición ─────────────────────────────────
                    val nutritionDone = state.nutritionDone
                    val nutritionSubtitle = if (nutritionDone) {
                        state.nutritionGoalLabel ?: "Plan creado"
                    } else "Te guiamos paso a paso"

                    OnboardingCard(
                        done = nutritionDone,
                        title = if (nutritionDone) "Plan de nutrición" else "Crea tu plan de nutrición",
                        subtitle = nutritionSubtitle,
                        expanded = false,
                        onToggle = { if (!nutritionDone) onNavigateToNutritionWizard() },
                        showChevron = !nutritionDone,
                    ) {
                        // No hay contenido expandido; la acción es navegar al wizard
                    }

                    // ── Mensaje de tareas completadas + botón SIGUIENTE ──────
                    AnimatedVisibility(
                        visible = state.allTasksDone,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = successGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Tareas completadas",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = successGreen,
                                )
                            }
                            Text(
                                "Presiona siguiente para ver el resumen",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { showSummary = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB7B8), contentColor = Color.Black),
                            ) {
                                Text("SIGUIENTE", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    // ── Resumen ──────────────────────────────────────────────
                    Text(
                        "¡Todo listo!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Así quedó tu configuración inicial. Puedes cambiarla cuando quieras desde Ajustes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    SummaryRow(
                        label = "Nombre",
                        value = state.displayName,
                        done = state.nameDone,
                    )
                    SummaryRow(
                        label = "Programa",
                        value = state.programName ?: if (state.programDone) "Programa activo" else "—",
                        done = state.programDone,
                    )
                    SummaryRow(
                        label = "Plan de nutrición",
                        value = state.nutritionGoalLabel ?: if (state.nutritionDone) "Plan activo" else "—",
                        done = state.nutritionDone,
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onAllTasksDone,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB7B8), contentColor = Color.Black),
                    ) {
                        Text("EMPEZAR", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, fontSize = 15.sp)
                    }
                    TextButton(
                        onClick = { showSummary = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Volver", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    done: Boolean,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    showChevron: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (done) Color(0xFF4CAF50).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .animateContentSize(),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (done) "Hecho" else "Pendiente",
                    tint = if (done) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.68f),
                    )
                }
                if (!done && showChevron) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    done: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (done) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
