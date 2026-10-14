package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ExercisePatternDetector
import com.example.kpkn.domain.exercises.SmartCreateRequest
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.components.MuscleInvolvementSection
import com.example.kpkn.screens.sessioneditor.components.formatVolumePercent
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Página de edición de un ejercicio propio dentro del catálogo (SE6b): «Atrás», el título «Editar ejercicio», el nombre debajo y el
 * formulario. Sustituye al catálogo entero mientras dura la edición.
 */
@Composable
internal fun SmartExerciseEditorPage(
    exercise: ExerciseMuscleInfo,
    onSave: (ExerciseMuscleInfo) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknTopRow(onBack = onClose)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Editar ejercicio",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Text(
                text = exercise.name,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        SmartExerciseForm(initial = exercise, onSave = onSave, onDismiss = onClose)
    }
}

/**
 * Formulario del creador inteligente (SE6b). Deriva nombre → patrón → coincidencia de catálogo → músculos, permite descripción propia y
 * ajuste manual del involucramiento cuando la detección es incierta. «Guardar ejercicio» solo se activa con nombre, implemento y una
 * vista previa válida. Sin estado externo: lo que guarda sale por [onSave].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmartExerciseForm(
    initial: ExerciseMuscleInfo?,
    onSave: (ExerciseMuscleInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val catalog = remember { exerciseCatalogSnapshot() }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var implementoId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(0)
                ?.let { SmartExerciseCreator.implementoIdFromLabel(it) }
                ?: initial?.equipment?.let { SmartExerciseCreator.implementoIdFromLabel(it) },
        )
    }
    var estacionId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(1)
                ?.let { SmartExerciseCreator.estacionIdFromLabel(it) },
        )
    }
    var lateralidadId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(2)
                ?.let { SmartExerciseCreator.lateralidadIdFromLabel(it) },
        )
    }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var musclesOverride by remember(initial) {
        mutableStateOf(initial?.involvedMuscles?.takeIf { it.isNotEmpty() })
    }
    var manualExpanded by remember(initial) { mutableStateOf(initial != null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preview = remember(name, implementoId, estacionId, lateralidadId, description, musclesOverride) {
        if (name.isNotBlank()) {
            runCatching {
                SmartExerciseCreator.preview(
                    SmartCreateRequest(
                        name = name,
                        implementoId = implementoId ?: "",
                        estacionId = estacionId,
                        lateralidadId = lateralidadId,
                        description = description,
                        musclesOverride = musclesOverride,
                        existingId = initial?.id,
                    ),
                    catalog,
                )
            }.getOrNull()
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KpknTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre del ejercicio",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OptionChipRow(
            title = "Implemento",
            required = true,
            value = implementoId,
            options = SmartExerciseCreator.IMPLEMENTO_IDS,
            label = SmartExerciseCreator::implementoLabel,
            onSelect = { implementoId = it },
        )
        OptionChipRow(
            title = "Estación",
            value = estacionId,
            options = SmartExerciseCreator.ESTACION_IDS,
            label = SmartExerciseCreator::estacionLabel,
            onSelect = { estacionId = it },
        )
        OptionChipRow(
            title = "Lateralidad",
            value = lateralidadId,
            options = SmartExerciseCreator.LATERALIDAD_IDS,
            label = SmartExerciseCreator::lateralidadLabel,
            onSelect = { lateralidadId = it },
        )

        KpknTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            placeholder = if (preview?.manualRecommended == true) {
                "El nombre no permite inferirla; describe brevemente el ejercicio"
            } else {
                "Se genera automáticamente si la dejas vacía"
            },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        preview?.let { p ->
            p.detectedPattern?.let { PatternBanner(it) }
            KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Derivado automáticamente por similitud",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    if (p.manualRecommended) {
                        Text(
                            text = "No pudimos detectar el patrón con certeza: revisa el involucramiento muscular antes de guardar.",
                            style = KpknType.caption,
                            color = SymbolPalette.energia,
                        )
                    }
                    if (p.exercise.catalogVariantChips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            p.exercise.catalogVariantChips.forEach { value -> VariantTag(value) }
                        }
                    }
                    if (p.exercise.involvedMuscles.isNotEmpty()) {
                        Text(
                            text = "Músculos · aporte por 1 serie",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        MuscleInvolvementSection(p.exercise)
                    }
                }
            }
        }

        val editableMuscles = musclesOverride ?: preview?.exercise?.involvedMuscles.orEmpty()
        if (editableMuscles.isNotEmpty() && (manualExpanded || preview?.manualRecommended == true)) {
            ManualMuscleEditor(
                muscles = editableMuscles,
                onChange = { updated -> musclesOverride = updated },
            )
        }
        KpknTextAction(
            text = if (manualExpanded) "Ocultar ajuste manual" else "Ajustar involucramiento manualmente",
            onClick = { manualExpanded = !manualExpanded },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknTextAction(
                text = "Cancelar",
                onClick = onDismiss,
            )
            KpknPrimaryPill(
                text = "Guardar ejercicio",
                onClick = {
                    val result = preview?.exercise
                    if (result != null) {
                        creating = true
                        scope.launch {
                            withContext(Dispatchers.IO) { CustomExerciseRepository.upsert(result) }
                            creating = false
                            onSave(result)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank() && implementoId != null && !creating && preview != null,
            )
        }
    }
}

/** Aviso del patrón detectado: un módulo teñido y compacto con el nombre del patrón (SE6b). */
@Composable
private fun PatternBanner(pattern: ExercisePatternDetector.DetectedMovementPattern) {
    KpknModule(
        accent = SymbolPalette.ok,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Patrón detectado",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pattern.label,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
        }
    }
}

/** Una variante de la vista previa, como etiqueta inset que no se pulsa. */
@Composable
private fun VariantTag(value: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = value,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}

private val MANUAL_MUSCLE_OPTIONS = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo",
    "Cuádriceps", "Isquiosurales", "Glúteos", "Core", "Abdomen", "Trapecio",
    "Romboides", "Erectores Espinales", "Pantorrillas", "Aductores",
)

/**
 * Ajuste manual del involucramiento (SE6b): cada músculo en su módulo, con sus papeles como `KpknChip` y su aporte en `KpknNumberCell`:
 * un arrastre o los gestos de accesibilidad suben o bajan 5 puntos. Abajo, los músculos que faltan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualMuscleEditor(
    muscles: List<InvolvedMuscle>,
    onChange: (List<InvolvedMuscle>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Involucramiento manual",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        muscles.forEachIndexed { index, muscle ->
            val volume = (muscle.volumeContribution ?: resolveMuscleVolumeContribution(muscle)).toFloat()
            val percent = formatVolumePercent(volume.toDouble())
            KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = muscle.muscle,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        KpknTextAction(
                            text = "Quitar",
                            onClick = { onChange(muscles.filterIndexed { i, _ -> i != index }) },
                            color = WizardColors.textMuted,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MuscleRole.PRIMARY to "Primario",
                            MuscleRole.SECONDARY to "Secundario",
                            MuscleRole.STABILIZER to "Estabilizador",
                        ).forEach { (role, label) ->
                            KpknChip(
                                text = label,
                                selected = muscle.role == role,
                                onClick = {
                                    onChange(muscles.toMutableList().also { it[index] = muscle.copy(role = role) })
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Aporte por 1 serie",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        KpknNumberCell(
                            value = percent,
                            input = KpknCellInput.ReadOnly,
                            contentDescription = "Aporte de ${muscle.muscle}: $percent",
                            onStep = { step ->
                                onChange(
                                    muscles.toMutableList().also {
                                        it[index] = muscle.copy(volumeContribution = stepVolume(volume, step))
                                    },
                                )
                            },
                            modifier = Modifier.width(88.dp),
                        )
                    }
                }
            }
        }
        val available = MANUAL_MUSCLE_OPTIONS.filter { option ->
            muscles.none { it.muscle.equals(option, ignoreCase = true) }
        }
        if (available.isNotEmpty()) {
            Text(
                text = "Agregar músculo",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                available.take(12).forEach { option ->
                    KpknChip(
                        text = option,
                        selected = false,
                        onClick = { onChange(muscles + InvolvedMuscle(option, MuscleRole.SECONDARY, 0.5)) },
                    )
                }
            }
        }
    }
}

/** Un paso de aporte: 5 puntos por cada paso, entre 0 y 1, redondeado a centésimas. */
private fun stepVolume(current: Float, direction: Int): Double =
    ((current + direction * 0.05f).coerceIn(0f, 1f) * 100f).roundToInt() / 100.0

/** Una fila de opciones (implemento, estación, lateralidad) como `KpknChip` en filas que saltan de línea. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChipRow(
    title: String,
    value: String?,
    options: List<String>,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    required: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (required) "$title *" else title,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { optionId ->
                KpknChip(
                    text = label(optionId),
                    selected = value == optionId,
                    onClick = { onSelect(optionId) },
                )
            }
        }
    }
}
