package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.STEPPER_CHROME_SIZE_DP
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.screens.workout.completionKeysForSet
import com.example.kpkn.screens.workout.seriesOptionsAvailability
import com.example.kpkn.screens.workout.seriesOptionsSummary
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Velo que oscurece la sesión detrás de las opciones de la serie (rgba(0,0,0,0.45) del prototipo C1). */
private const val SERIES_OPTIONS_SCRIM_ALPHA = 0.45f

/**
 * Opciones de la serie (LV11, A1; prototipo C1): lo que se abre con la pulsación larga en un paso de la serie. Una tarjeta flotante
 * sobre la barra inferior, que se queda a la vista debajo del velo:
 * - cabecera con la serie real («Serie 2 · 95 kg × 6 · RIR 2») o, si no está hecha, su plan;
 * - cuatro acciones: «Editar» (las series del ejercicio), «Añadir» (una serie más; el lector de pantalla dice «Añadir serie»), «Saltar» (la serie de hoy) y «Borrar» (con «Deshacer»);
 * - «Tipo de serie» (Normal · Drop-set · Rest-pause) y el alcance de siempre: los nodos S1…Sn (las hechas, apagadas; mantener
 *   pulsado marca varias), «Desde S{n} hasta el final» o «Solo S{n}», y «Aplicar».
 *
 * Las acciones llevan el ejercicio y la serie en los que se pulsó (en una superserie puede no ser el ejercicio de turno); las que no
 * valen en ese momento se ven apagadas. La lógica de cada una vive en el ViewModel: aquí solo se llama.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SeriesOptionsSheet(
    exercise: Exercise,
    target: SeriesTypeTarget,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    onDismiss: () -> Unit,
    onApply: (SeriesTypeTarget, SeriesTechnique) -> Unit,
    onEdit: () -> Unit,
    onAddSet: () -> Unit,
    onSkipSet: (setIndex: Int) -> Unit,
    onDeleteSet: (setIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    setDraftKeys: Collection<String> = emptyList(),
    manualLoadKeys: Collection<String> = emptyList(),
    bottomClearance: Dp = 0.dp,
) {
    val lastIndex = (exercise.sets.size - 1).coerceAtLeast(0)
    // La serie en la que se pulsó: las acciones y la cabecera hablan de ella aunque luego se marquen otras para el tipo.
    val focusIdx = target.fromSetIdx.coerceIn(0, lastIndex)
    val completedSetIndices = remember(exercise, completedSets) {
        exercise.sets.indices.filter { idx -> exercise.completionKeysForSet(idx).any { it in completedSets } }.toSet()
    }
    val summary = remember(exercise, focusIdx, completedSets) { seriesOptionsSummary(exercise, focusIdx, completedSets) }
    val availability = remember(exercise, focusIdx, completedSets, omittedSetKeys, setDraftKeys, manualLoadKeys) {
        seriesOptionsAvailability(exercise, focusIdx, completedSets, omittedSetKeys, setDraftKeys, manualLoadKeys)
    }

    var selectedTechnique by remember(target) { mutableStateOf(SeriesTechnique.NORMAL) }
    val initialIndices = remember(target, exercise.sets.size) {
        val fromTarget = target.selectedSetIndices.filter { it in exercise.sets.indices }.toSet()
        if (fromTarget.isNotEmpty()) fromTarget
        else (target.fromSetIdx..target.toSetIdx).filter { it in exercise.sets.indices }.toSet()
            .ifEmpty { setOf(focusIdx) }
    }
    var selectedIndices by remember(target) { mutableStateOf(initialIndices) }
    var multiMarkStarted by remember(target) { mutableStateOf(initialIndices.size > 1) }
    var anchorIdx by remember(target) { mutableStateOf(focusIdx) }
    var includeAllFollowing by remember(target) {
        mutableStateOf(target.toSetIdx >= exercise.sets.lastIndex && selectedIndices.size > 1)
    }

    LaunchedEffect(target, exercise.id) {
        val first = exercise.sets.getOrNull(anchorIdx) ?: exercise.sets.getOrNull(target.fromSetIdx)
        selectedTechnique = when {
            first?.isDropSet == true -> SeriesTechnique.DROPSET
            first?.isRestPause == true -> SeriesTechnique.REST_PAUSE
            else -> SeriesTechnique.NORMAL
        }
    }

    val fromEndSet = if (exercise.sets.isNotEmpty()) (anchorIdx..exercise.sets.lastIndex).toSet() else emptySet()
    val rangeChipSelected = includeAllFollowing && selectedIndices.containsAll(fromEndSet)
    val soloChipSelected = !multiMarkStarted && selectedIndices == setOf(anchorIdx)

    fun toggleIndex(idx: Int) {
        if (idx in completedSetIndices) return
        val next = if (idx in selectedIndices) {
            val remaining = selectedIndices - idx
            if (remaining.isEmpty()) selectedIndices else remaining
        } else {
            selectedIndices + idx
        }
        selectedIndices = next
        multiMarkStarted = next.size > 1
        includeAllFollowing = false
        anchorIdx = idx
    }

    BackHandler(onBack = onDismiss)

    val cardShape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(20f)) {
        // La tarjeta nunca sube más allá de lo que cabe sobre la barra: si no cabe (letra grande), se desplaza.
        val cardMaxHeight = (maxHeight - bottomClearance - 24.dp).coerceAtLeast(240.dp)
        // El velo cubre la sesión pero no la barra inferior, que queda por encima (la barra vive fuera de este cuerpo).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SERIES_OPTIONS_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomClearance + 8.dp)
                .fillMaxWidth()
                .heightIn(max = cardMaxHeight)
                .shadow(elevation = 16.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .background(KpknSurface.inset, cardShape)
                .border(1.dp, WizardColors.text.copy(alpha = 0.10f), cardShape)
                // Tocar la tarjeta no cierra: el velo de detrás es el que cierra.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (exercise.supersetGroupRefOrLegacyId() != null) {
                // En una superserie cada ronda tiene varios ejercicios: se dice de cuál es la serie.
                Text(
                    text = exercise.name,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.title,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                )
                Text(
                    text = summary.detail,
                    modifier = Modifier.weight(1f),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveOptionTile(
                    label = "Editar",
                    icon = KpknLineIcons.Edit,
                    onClick = {
                        onEdit()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                LiveOptionTile(
                    label = "Añadir",
                    contentDescription = "Añadir serie",
                    icon = KpknLineIcons.Plus,
                    onClick = {
                        onAddSet()
                        onDismiss()
                    },
                    enabled = availability.canAdd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                LiveOptionTile(
                    label = "Saltar",
                    icon = LiveOptionIcons.Skip,
                    onClick = {
                        onSkipSet(focusIdx)
                        onDismiss()
                    },
                    enabled = availability.canSkip,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                LiveOptionTile(
                    label = "Borrar",
                    icon = KpknLineIcons.Trash,
                    onClick = {
                        onDeleteSet(focusIdx)
                        onDismiss()
                    },
                    enabled = availability.canDelete,
                    danger = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Text(
                text = "Tipo de serie".uppercase(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            val onlyFirstSetSelected = selectedIndices.size == 1 && selectedIndices.contains(0)
            // Las píldoras llevan su ancho natural y, si no caben (letra grande), pasan a otra línea entera: nunca se parte una palabra.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesChoiceChip(
                    text = "Normal",
                    selected = selectedTechnique == SeriesTechnique.NORMAL,
                    onClick = { selectedTechnique = SeriesTechnique.NORMAL },
                )
                SeriesChoiceChip(
                    text = "Drop-set",
                    selected = selectedTechnique == SeriesTechnique.DROPSET && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.DROPSET },
                    enabled = !onlyFirstSetSelected,
                )
                SeriesChoiceChip(
                    text = "Rest-pause",
                    selected = selectedTechnique == SeriesTechnique.REST_PAUSE && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.REST_PAUSE },
                    enabled = !onlyFirstSetSelected,
                )
            }
            Text(
                text = when {
                    onlyFirstSetSelected -> "La Serie 1 no puede ser Drop-set ni Rest-pause (requiere descanso previo completo de cambio de ejercicio)."
                    selectedTechnique == SeriesTechnique.NORMAL -> "Serie estándar. Quita drop-set o rest-pause y el descanso especial."
                    selectedTechnique == SeriesTechnique.DROPSET -> "Entre las series marcadas: sin descanso y −5 kg en la siguiente."
                    selectedTechnique == SeriesTechnique.REST_PAUSE -> "Entre las series marcadas: 15 s de descanso y el mismo peso."
                    else -> ""
                },
                style = KpknType.caption,
                color = if (onlyFirstSetSelected) WizardColors.danger else WizardColors.textMuted,
            )

            Text(
                text = "Elige qué series cambiar. Las completadas no se tocan. Mantén pulsado un nodo para marcar varias.",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { idx, set ->
                    val isCompleted = idx in completedSetIndices
                    val isSelected = idx in selectedIndices
                    val label = "S${idx + 1}"
                    val fill = when {
                        isCompleted -> WizardColors.done
                        isSelected -> WizardColors.text
                        else -> Color.Transparent
                    }
                    val edge = when {
                        isCompleted || isSelected -> Color.Transparent
                        set.isDropSet || set.isRestPause -> WizardColors.text.copy(alpha = 0.40f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .size(STEPPER_CHROME_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(fill, CircleShape)
                            .border(1.dp, edge, CircleShape)
                            .combinedClickable(
                                enabled = !isCompleted,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (multiMarkStarted) {
                                        toggleIndex(idx)
                                    } else {
                                        selectedIndices = setOf(idx)
                                        anchorIdx = idx
                                        includeAllFollowing = false
                                    }
                                },
                                onLongClick = { toggleIndex(idx) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCompleted) {
                            Icon(
                                KpknLineIcons.Check,
                                contentDescription = "Completada",
                                modifier = Modifier.size(14.dp),
                                tint = WizardColors.ctaContent,
                            )
                        } else {
                            Text(
                                text = label,
                                style = KpknType.chip,
                                color = if (isSelected) WizardColors.ctaContent else WizardColors.text.copy(alpha = 0.9f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesChoiceChip(
                    text = "Solo S${anchorIdx + 1}",
                    selected = soloChipSelected,
                    onClick = {
                        includeAllFollowing = false
                        multiMarkStarted = false
                        selectedIndices = setOf(anchorIdx)
                    },
                )
                SeriesChoiceChip(
                    text = "Desde S${anchorIdx + 1} hasta el final",
                    selected = rangeChipSelected,
                    onClick = {
                        includeAllFollowing = true
                        multiMarkStarted = true
                        selectedIndices = fromEndSet
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpknCompactPill(
                    text = "Cancelar",
                    onClick = onDismiss,
                    filled = false,
                    fullWidth = true,
                    modifier = Modifier.weight(1f),
                )
                KpknCompactPill(
                    text = "Aplicar",
                    onClick = {
                        val rawIndices = selectedIndices.filter { it in exercise.sets.indices }.toSet()
                        val indices = if (selectedTechnique != SeriesTechnique.NORMAL) {
                            rawIndices.filter { it > 0 }.toSet()
                        } else {
                            rawIndices
                        }
                        if (indices.isNotEmpty()) {
                            val from = indices.minOrNull() ?: anchorIdx
                            val to = indices.maxOrNull() ?: from
                            onApply(
                                SeriesTypeTarget(
                                    exerciseId = exercise.id,
                                    fromSetIdx = from,
                                    toSetIdx = to,
                                    selectedSetIndices = indices,
                                ),
                                selectedTechnique,
                            )
                        } else {
                            onDismiss()
                        }
                    },
                    fullWidth = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Opción de alcance o de tipo: una píldora de 32 dp con su ancho natural (quien la monta usa `FlowRow`: con la letra grande pasa a
 * otra línea entera en vez de partir una palabra). Elegida, va en tinta; sin elegir, solo lleva un filete de 1 dp al 12 % (la tarjeta de
 * detrás ya es `inset`: otro relleno `inset` no se vería). Toda la píldora es tocable (48 dp) y sin ripple.
 */
@Composable
private fun SeriesChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 32.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(KpknShape.pill)
                .background(if (selected) WizardColors.text else Color.Transparent, KpknShape.pill)
                .border(
                    1.dp,
                    if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    KpknShape.pill,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = if (selected) WizardColors.ctaContent else WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
