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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
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
import com.example.kpkn.ui.components.KpknPortal
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
    /** «Avanzado» y «Parciales y ROM» / «Nota» de la rejilla (abren la cara Avanzado o «Detalle del registro» de esa serie). */
    onAdvanced: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
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

    val onlyFirstSetSelected = selectedIndices.size == 1 && selectedIndices.contains(0)
    fun apply() {
        val rawIndices = selectedIndices.filter { it in exercise.sets.indices }.toSet()
        val indices = if (selectedTechnique != SeriesTechnique.NORMAL) rawIndices.filter { it > 0 }.toSet() else rawIndices
        if (indices.isNotEmpty()) {
            val from = indices.minOrNull() ?: anchorIdx
            val to = indices.maxOrNull() ?: from
            onApply(SeriesTypeTarget(exerciseId = exercise.id, fromSetIdx = from, toSetIdx = to, selectedSetIndices = indices), selectedTechnique)
        } else {
            onDismiss()
        }
    }

    // Prototipo 02 (`series`): resumen grande de la serie, rejilla de acciones, filas para añadir, omitir y quitar, y la técnica con
    // su alcance en secciones; «Aplicar» en el pie.
    LiveSheet(
        title = "Opciones de la serie",
        onDismiss = onDismiss,
        modifier = modifier,
        tag = "series-options-sheet",
        footer = { close ->
            LiveSheetPrimary("Aplicar técnica", { apply() }, tag = "series-options-apply")
            LiveSheetTextAction("Cancelar", close)
        },
    ) { close ->
        if (exercise.supersetGroupRefOrLegacyId() != null) {
            LiveSheetSubtitle(exercise.name)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(summary.title, style = KpknType.caption.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
            Text(summary.detail, Modifier.weight(1f), style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 26.sp), color = WizardColors.text)
        }
        // `.v2-options-grid`: Configurar serie, Avanzado, Parciales y ROM y Nota; debajo, «Añadir serie», «Omitir» y «Quitar».
        ExerciseOptionsGrid(listOf(
            ExerciseOption("Configurar serie", KpknLineIcons.Sliders, { onEdit(); onDismiss() }),
            ExerciseOption("Avanzado", KpknLineIcons.Flame, { onAdvanced?.invoke(); onDismiss() }, enabled = onAdvanced != null),
            ExerciseOption("Parciales y ROM", KpknLineIcons.Plus, { onDetails?.invoke(); onDismiss() }, enabled = onDetails != null),
            ExerciseOption("Nota", KpknLineIcons.Edit, { onDetails?.invoke(); onDismiss() }, enabled = onDetails != null),
        ))
        LiveSheetMenuRow("Añadir serie", { onAddSet(); onDismiss() }, icon = KpknLineIcons.Plus, chevron = false,
            enabled = availability.canAdd)
        LiveSheetMenuRow("Omitir serie", { onSkipSet(focusIdx); onDismiss() }, icon = LiveOptionIcons.Skip, chevron = false,
            enabled = availability.canSkip)
        LiveSheetMenuRow("Quitar serie pendiente", { onDeleteSet(focusIdx); onDismiss() }, icon = KpknLineIcons.Trash, danger = true,
            chevron = false, enabled = availability.canDelete)

        LiveSheetSectionLabel("Técnica de la serie")
        LiveSheetChoiceRow(
            listOf(SeriesTechnique.NORMAL to "Normal", SeriesTechnique.DROPSET to "Drop-set", SeriesTechnique.REST_PAUSE to "Rest-pause"),
            selected = { it == selectedTechnique && !(onlyFirstSetSelected && it != SeriesTechnique.NORMAL) },
            onSelect = { technique -> if (!(onlyFirstSetSelected && technique != SeriesTechnique.NORMAL)) selectedTechnique = technique },
            tagPrefix = "series-technique",
        )
        LiveSheetShortCopy(when {
            onlyFirstSetSelected -> "La serie 1 no puede ser drop-set ni rest-pause: necesita el descanso completo previo."
            selectedTechnique == SeriesTechnique.NORMAL -> "Serie estándar. Quita drop-set o rest-pause y el descanso especial."
            selectedTechnique == SeriesTechnique.DROPSET -> "Entre las series marcadas: sin descanso y −5 kg en la siguiente."
            else -> "Entre las series marcadas: 15 s de descanso y el mismo peso."
        }, error = onlyFirstSetSelected)

        LiveSheetSectionLabel("Aplicar a")
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.sets.forEachIndexed { idx, set ->
                val isCompleted = idx in completedSetIndices
                val isSelected = idx in selectedIndices
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected && !isCompleted) WizardColors.text else LiveSheetTokens.ChipFill)
                        .border(1.dp, when {
                            isSelected && !isCompleted -> WizardColors.text
                            set.isDropSet || set.isRestPause -> WizardColors.text.copy(alpha = .40f)
                            else -> LiveSheetTokens.Line
                        }, RoundedCornerShape(12.dp))
                        .combinedClickable(
                            enabled = !isCompleted,
                            role = Role.Checkbox,
                            onClick = {
                                if (multiMarkStarted) toggleIndex(idx)
                                else { selectedIndices = setOf(idx); anchorIdx = idx; includeAllFollowing = false }
                            },
                            onLongClick = { toggleIndex(idx) },
                        )
                        .semantics {
                            contentDescription = "Serie ${idx + 1}"
                            stateDescription = when { isCompleted -> "Completada"; isSelected -> "Marcada"; else -> "Sin marcar" }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) Icon(KpknLineIcons.Check, "Completada", Modifier.size(14.dp), tint = WizardColors.done)
                    else Text("S${idx + 1}", style = KpknType.chip.copy(fontSize = 12.sp),
                        color = if (isSelected) Color(0xFF111111) else WizardColors.text)
                }
            }
        }
        LiveSheetChoiceRow(
            listOf(false to "Solo S${anchorIdx + 1}", true to "Desde S${anchorIdx + 1} hasta el final"),
            selected = { all -> if (all) rangeChipSelected else soloChipSelected },
            onSelect = { all ->
                if (all) { includeAllFollowing = true; multiMarkStarted = true; selectedIndices = fromEndSet }
                else { includeAllFollowing = false; multiMarkStarted = false; selectedIndices = setOf(anchorIdx) }
            },
            tagPrefix = "series-scope",
        )
        LiveSheetShortCopy("Las series completadas no se tocan. Mantén pulsada una serie para marcar varias.")
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
