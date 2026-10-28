package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WorkoutDrawer(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    showCloseButton: Boolean = true,
    hazeState: HazeState? = LocalHazeState.current,
    /**
     * G1/G2: la hoja ocupa casi toda la pantalla (bajo la barra de estado) y lleva un asa arriba. Con [footer], ese pie va fijo
     * debajo del contenido, que es lo único que se desplaza.
     */
    fullHeight: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Refinamiento 02: todos los cajones de la sesión usan la hoja del prototipo (asa, título en Syne, ✕, panel y velo). [hazeState]
    // queda por compatibilidad: el panel ya no es de vidrio. Sin [footer] no se añade «Listo»: cada cajón trae sus acciones.
    @Suppress("UNUSED_VARIABLE") val unusedHaze = hazeState
    com.example.kpkn.screens.workout.components.LiveSheet(
        title = title,
        onDismiss = { if (dismissible) onDismiss() },
        tag = "workout-drawer",
        stableHeightFraction = if (fullHeight) .92f else null,
        footer = if (footer == null) null else { _ -> footer(this) },
        showClose = showCloseButton,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

/**
 * «Reportar molestias» rápido tras un fallo (LR1c1): el cuerpo va dentro de la hoja de siempre (`KpknSheet`, sin tocar).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun QuickExecutionErrorDiscomfortSheet(
    exerciseName: String,
    onSave: (discomfortIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, 
        onDismissRequest = {},
        dismissible = false,
    ) {
        // Atrás no cierra esta hoja (hay que decir si hubo molestias), pero ya no se queda muda: avisa de por qué (LV4, 15).
        BlockedBackHint()
        QuickDiscomfortSheetBody(exerciseName = exerciseName, onSave = onSave)
    }
}

/**
 * Contenido de «Reportar molestias» (LR1c1): campo de búsqueda, molestias con su detalle, las elegidas con su X y los dos botones
 * («Sin molestias» y «Guardar»). Sin la hoja, para que la hoja de render lo pinte con las piezas reales. [initialQuery] y
 * [initialSelectedIds] solo sirven para esa hoja.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun QuickDiscomfortSheetBody(
    exerciseName: String,
    onSave: (discomfortIds: List<String>) -> Unit,
    initialQuery: String = "",
    initialSelectedIds: List<String> = emptyList(),
) {
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(initialSelectedIds) } }
    val filteredEntries = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) emptyList()
        else DISCOMFORT_CATALOG.filter { it.label.lowercase(Locale.ROOT).contains(normalized) || it.description.lowercase(Locale.ROOT).contains(normalized) }.sortedBy { it.label }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Reportar molestias", style = KpknType.dialogTitle, color = WizardColors.text)
        Text(
            exerciseName,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
        )
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Tuviste alguna molestia al realizar este ejercicio?", style = KpknType.bodyStrong, color = WizardColors.text)
            KpknTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Buscar molestia",
                leadingIcon = Icons.Default.Search,
            )
            if (filteredEntries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredEntries.forEach { entry ->
                        val selected = selectedIds.contains(entry.id)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                KpknChip(
                                    text = entry.label,
                                    selected = selected,
                                    onClick = { if (selected) selectedIds.remove(entry.id) else selectedIds.add(entry.id) },
                                )
                            }
                            KpknIconButton(
                                icon = KpknLineIcons.Info,
                                contentDescription = "Detalle",
                                onClick = { infoEntry = entry },
                            )
                        }
                    }
                }
            } else if (searchQuery.isBlank()) {
                Text("Escribe para buscar molestias…", style = KpknType.caption, color = WizardColors.textFaint)
            } else {
                Text("No se encontraron resultados para \"$searchQuery\"", style = KpknType.caption, color = WizardColors.textFaint)
            }
            if (selectedIds.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedIds.forEach { id ->
                        val entry = DISCOMFORT_CATALOG.find { it.id == id }
                        val label = entry?.label ?: id
                        DiscomfortSelectedTag(
                            label = label,
                            onRemove = { selectedIds.remove(id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Sin peso: la pastilla mide lo que dice al 130 % y «Guardar» ocupa el resto (LR1c1).
            KpknSecondaryPill(
                text = "Sin molestias",
                onClick = { onSave(emptyList()) },
            )
            KpknPrimaryPill(
                text = "Guardar",
                onClick = { onSave(selectedIds.toList()) },
                modifier = Modifier.weight(1f),
                enabled = selectedIds.isNotEmpty(),
            )
        }
    }
    infoEntry?.let { entry ->
        KpknAlertDialog(
            onDismissRequest = { infoEntry = null },
            title = { Text(entry.label, style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.description, style = KpknType.body, color = WizardColors.text)
                    Text("Sección: ${entry.section.label}", style = KpknType.caption, color = WizardColors.textMuted)
                }
            },
            confirmButton = {
                KpknTextAction(text = "Entendido", onClick = { infoEntry = null })
            },
        )
    }
}
