package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.kpkn.data.models.Session
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.components.SessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknDropdownMenu

@Composable
internal fun SessionHero(
    session: Session,
    hasChanges: Boolean,
    autoSaveEnabled: Boolean,
    latestBodyMeasurement: BodyMeasurementEntry?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMeetDayChange: (Boolean) -> Unit,
    onMeetBodyweightChange: (Double?) -> Unit,
    onSyncMeetBodyweight: () -> Unit,
    onSave: () -> Unit,
    onOpenCoverSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    sessionsOnSameDay: List<Session> = emptyList(),
    onSwitchSession: (String) -> Unit = {},
    onSetMainSession: (String) -> Unit = {},
    // Feature 2: duración objetivo
    targetDurationMinutes: Int? = null,
    sessionTimeBreakdown: com.example.kpkn.domain.calculations.SessionTimeBreakdown? = null,
    onSetTargetDuration: (Int?) -> Unit = {},
    // Feature 3: variantes derivadas de la original
    activeVariant: WeekVariant = WeekVariant.A,
    availableVariants: List<WeekVariant> = listOf(WeekVariant.A),
    onCreateVariant: (WeekVariant, String) -> Unit = { _, _ -> },
    onDeleteVariant: (WeekVariant) -> Unit = {},
    onSwitchVariant: (WeekVariant) -> Unit = {},
) {
    var showVariantMenu by remember { mutableStateOf(false) }
    var showCreateVariantDialog by remember { mutableStateOf(false) }
    var newVariantName by remember { mutableStateOf("") }
    val nextVariant = remember(availableVariants, session) {
        listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D)
            .firstOrNull { it !in availableVariants }
    }
      val background = session.background
      val brightness = background?.style?.brightness ?: 0.92f
      val blur = (background?.style?.blur ?: 0f).dp
      Box(
          modifier = Modifier.fillMaxWidth(),
      ) {
         Box(
             modifier = Modifier
                 .matchParentSize()
                 .background(Color.Black)
         ) {
             SessionBackgroundLayer(background = background, blurDp = blur)
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .background(
                         Brush.verticalGradient(
                             colors = listOf(
                                 Color.Black.copy(alpha = (1f - brightness.coerceIn(0.25f, 1f)) * 0.55f),
                                 Color.Black.copy(alpha = 0.12f),
                                 Color.Black.copy(alpha = 0.78f),
                             ),
                         )
                     ),
             )
         }

         Column(
             modifier = Modifier
                 .fillMaxWidth()
                 .statusBarsPadding()
                 .padding(horizontal = 16.dp, vertical = 10.dp),
             verticalArrangement = Arrangement.spacedBy(6.dp),
         ) {
             Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(0.dp),
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      // Left side: Variant chips
                      if (availableVariants.size > 1 || session.sessionB == null && session.sessionC == null && session.sessionD == null) {
                          Row(
                              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                              horizontalArrangement = Arrangement.spacedBy(6.dp),
                              verticalAlignment = Alignment.CenterVertically,
                          ) {
                              availableVariants.forEach { variant ->
                                  val isActive = variant == activeVariant
                                  val variantName = when (variant) {
                                      WeekVariant.A -> "Original"
                                      WeekVariant.B -> session.sessionB?.name ?: "Derivada"
                                      WeekVariant.C -> session.sessionC?.name ?: "Derivada"
                                      WeekVariant.D -> session.sessionD?.name ?: "Derivada"
                                  }
                                  AssistChip(
                                      onClick = { if (!isActive) onSwitchVariant(variant) },
                                      label = {
                                          Text(
                                              variantName,
                                              style = MaterialTheme.typography.labelSmall,
                                              fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal,
                                          )
                                      },
                                      leadingIcon = if (isActive) ({ Icon(Icons.Default.Check, null, Modifier.size(13.dp)) }) else null,
                                      trailingIcon = if (isActive && variant != WeekVariant.A) ({
                                          Box {
                                              Icon(
                                                  Icons.Default.MoreVert, null,
                                                  Modifier.size(13.dp).clickable { showVariantMenu = true },
                                              )
                                              KpknDropdownMenu(expanded = showVariantMenu, onDismissRequest = { showVariantMenu = false }) {
                                                  DropdownMenuItem(
                                                      text = { Text("Eliminar variante") },
                                                      onClick = { showVariantMenu = false; onDeleteVariant(variant) },
                                                  )
                                              }
                                          }
                                      }) else null,
                                      shape = RoundedCornerShape(999.dp),
                                      colors = AssistChipDefaults.assistChipColors(
                                          containerColor = if (isActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                                          labelColor = Color.White,
                                      ),
                                  )
                              }
                              // Botón para crear nueva variante derivada
                              if (nextVariant != null) {
                                  Surface(
                                      onClick = {
                                          newVariantName = "${session.name} – Rápida"
                                          showCreateVariantDialog = true
                                      },
                                      shape = RoundedCornerShape(999.dp),
                                      color = Color.White.copy(alpha = 0.08f),
                                  ) {
                                      Row(
                                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                          horizontalArrangement = Arrangement.spacedBy(3.dp),
                                          verticalAlignment = Alignment.CenterVertically,
                                      ) {
                                          Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.75f))
                                          Text("Derivada", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                                      }
                                  }
                              }
                          }
                      }
                      Spacer(Modifier.width(8.dp))
                      // Right side: Auto:on, color, save
                      Row(
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          DarkChoiceChip(
                              label = if (autoSaveEnabled) "Auto: On" else "Auto: Off",
                              selected = autoSaveEnabled,
                              onClick = onAutoSaveToggle,
                          )
                          Surface(
                              onClick = { onOpenCoverSheet() },
                              shape = CircleShape,
                              color = DarkEditorChip,
                          ) {
                              Box(
                                  modifier = Modifier.size(34.dp),
                                  contentAlignment = Alignment.Center,
                                  ) {
                                  Icon(
                                      Icons.Default.Palette,
                                      "Editar fondo",
                                      tint = Color.White,
                                      modifier = Modifier.size(18.dp),
                                  )
                              }
                          }
                          HeroGlassIconButton(
                              icon = Icons.Default.Save,
                              contentDescription = "Guardar sesión",
                              onClick = onSave,
                              showUnsavedDot = hasChanges,
                          )
                      }
                  }

                  val titleFontSize = when {
                      session.name.length < 15 -> 36.sp
                      session.name.length < 25 -> 28.sp
                      else -> 22.sp
                  }

                  BasicTextField(
                      value = session.name,
                      onValueChange = onNameChange,
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 18.dp),
                      singleLine = true,
                      textStyle = MaterialTheme.typography.displaySmall.copy(
                          fontSize = titleFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                      ),
                      cursorBrush = SolidColor(Color.White),
                      decorationBox = { innerTextField ->
                          Box(Modifier.fillMaxWidth()) {
                              if (session.name.isBlank()) Text("Nueva sesión", color = Color.White.copy(alpha = 0.72f), fontSize = titleFontSize, fontWeight = FontWeight.Bold)
                              innerTextField()
                          }
                      },
                  )

                  BasicTextField(
                      value = session.description.orEmpty(),
                      onValueChange = onDescriptionChange,
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 4.dp, bottom = 8.dp),
                      singleLine = false,
                      maxLines = 2,
                      textStyle = MaterialTheme.typography.bodyMedium.copy(
                          color = Color.White.copy(alpha = 0.86f),
                          fontWeight = FontWeight.Medium,
                      ),
                      cursorBrush = SolidColor(Color.White),
                      decorationBox = { innerTextField ->
                          Box(Modifier.fillMaxWidth()) {
                              if (session.description.isNullOrBlank()) Text("Añadir descripción", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodyMedium)
                              innerTextField()
                          }
                      },
                  )

                  // Action chips row — compact screens collapse secondary actions into a menu
                  val heroBreakpoint = rememberSessionEditorBreakpoint()
                  if (heroBreakpoint == SessionEditorBreakpoint.Compact) {
                      var showSecondaryMenu by remember { mutableStateOf(false) }
                      Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          SessionHeroActionChip("Reglas y tiempo", Icons.Default.Settings, onOpenRules)
                          Box {
                              SessionHeroActionChip("Más", Icons.Default.MoreVert) { showSecondaryMenu = true }
                              KpknDropdownMenu(
                                  expanded = showSecondaryMenu,
                                  onDismissRequest = { showSecondaryMenu = false },
                              ) {
                                  DropdownMenuItem(
                                      text = { Text("Transferir") },
                                      onClick = { showSecondaryMenu = false; onOpenTransfer() },
                                      leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                                  )
                                  DropdownMenuItem(
                                      text = { Text("Historial") },
                                      onClick = { showSecondaryMenu = false; onOpenHistory() },
                                      leadingIcon = { Icon(Icons.Default.History, null) },
                                  )
                              }
                          }
                      }
                  } else {
                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .horizontalScroll(rememberScrollState())
                              .padding(horizontal = 4.dp),
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          SessionHeroActionChip("Transferir", Icons.Default.SwapHoriz, onOpenTransfer)
                          SessionHeroActionChip("Historial", Icons.Default.History, onOpenHistory)
                          SessionHeroActionChip("Reglas y tiempo", Icons.Default.Settings, onOpenRules)
                      }
                  }

                // Multi-session day: session switcher row
                if (sessionsOnSameDay.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        sessionsOnSameDay.forEach { ssn ->
                            val isCurrent = ssn.id == session.id
                            val isPrimary = ssn.isMainSession
                            AssistChip(
                                onClick = { if (!isCurrent) onSwitchSession(ssn.id) },
                                label = {
                                    Text(
                                        if (isPrimary) "★ ${ssn.name.ifBlank { "Sesión" }}" else ssn.name.ifBlank { "Sesión" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    if (isCurrent) {
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                                    } else if (!isPrimary) {
                                        Icon(
                                            Icons.Default.StarBorder,
                                            "Marcar como principal",
                                            Modifier.size(14.dp).clickable { onSetMainSession(ssn.id) },
                                        )
                                    } else {
                                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isCurrent) Color.White.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.10f),
                                    labelColor = Color.White,
                                ),
                            )
                        }
                    }
                }

                if (session.isMeetDay) {
                    OutlinedTextField(
                        value = session.meetBodyweight?.let(::formatEditableNumber).orEmpty(),
                        onValueChange = { onMeetBodyweightChange(it.safeDoubleOrNull()) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Peso corporal objetivo (kg)", color = Color.White.copy(alpha = 0.72f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.22f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.22f),
                            focusedBorderColor = Color.White.copy(alpha = 0.38f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White.copy(alpha = 0.82f),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.62f),
                            cursorColor = Color.White,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val measurementText = latestBodyMeasurement?.weight?.let { weight ->
                            "Medición reciente: ${formatEditorOneDecimal(weight)} kg (${latestBodyMeasurement.date})"
                        } ?: "Sin medición corporal reciente"
                        Text(
                            text = measurementText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        OutlinedButton(
                            onClick = onSyncMeetBodyweight,
                            enabled = latestBodyMeasurement?.weight != null,
                        ) {
                            Text("Usar medición")
                        }
                    }
                }

                // Dialog para nombrar la variante al crearla
                if (showCreateVariantDialog && nextVariant != null) {
                    KpknAlertDialog(
                        onDismissRequest = { showCreateVariantDialog = false },
                        title = { Text("Nueva variante") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Crea una variante derivada de la sesión original. " +
                                    "Tendrá sus propios ejercicios, series y descansos independientes.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = newVariantName,
                                    onValueChange = { newVariantName = it },
                                    label = { Text("Nombre de la variante") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (newVariantName.isNotBlank()) {
                                        onCreateVariant(nextVariant, newVariantName.trim())
                                        showCreateVariantDialog = false
                                    }
                                },
                                enabled = newVariantName.isNotBlank(),
                            ) { Text("Crear variante") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateVariantDialog = false }) { Text("Cancelar") }
                        },
                    )
                }
            }
        }
    }
}
