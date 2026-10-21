package com.example.kpkn.screens.sessioneditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechnicalAspect
import com.example.kpkn.data.preferences.PreferenceStores
import com.example.kpkn.domain.exercises.TechnicalAspectEngine
import com.example.kpkn.domain.exercises.VariantGroup
import com.example.kpkn.domain.exercises.VariantGroupIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.components.formatSeriesEquivalent
import com.example.kpkn.screens.sessioneditor.components.formatVolumePercent
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/** Pasos del asistente (SE6b): 1 la variante, 2 los aspectos técnicos y 3 los músculos que resultan. */
private const val WIZARD_STEPS = 3

/**
 * Asistente de variantes y aspectos técnicos (SE6b): una hoja KPKN con el eyebrow «Paso 1 de 3», el título en `title`, las opciones en
 * filas de radio y «Atrás» y «Siguiente» como píldoras. Un ejercicio sin grupo de variantes se confirma solo, como antes.
 */
@Composable
fun CatalogSelectionWizard(
    initialExercise: ExerciseMuscleInfo,
    onConfirm: (selectedVariant: ExerciseMuscleInfo, selectedAspects: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val group = remember(initialExercise) {
        initialExercise.variantGroupId?.let { VariantGroupIndex.getGroup(it) }
    }

    if (group == null) {
        LaunchedEffect(initialExercise.id) {
            onConfirm(initialExercise, emptyMap())
        }
        return
    }

    KpknSheet(onDismissRequest = onDismiss) {
        CatalogSelectionWizardContent(
            group = group,
            initialExercise = initialExercise,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * El cuerpo del asistente, suelto para los tests y la hoja de render. Guarda el paso, la variante y los aspectos; las preferencias de la
 * variante y de los aspectos se guardan al cambiarlas, como antes.
 */
@Composable
internal fun CatalogSelectionWizardContent(
    group: VariantGroup,
    initialExercise: ExerciseMuscleInfo,
    onConfirm: (selectedVariant: ExerciseMuscleInfo, selectedAspects: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefStore = remember { PreferenceStores.variantPreferences(context) }

    var step by remember { mutableStateOf(0) }
    var selectedVariant by remember { mutableStateOf(initialExercise) }
    val savedAspects = remember(group, selectedVariant) {
        prefStore.loadAspectDefaults(group.id)
    }
    var selectedAspects by remember { mutableStateOf<Map<String, String>>(savedAspects) }

    val defaults = remember(selectedVariant) { computeDefaults(selectedVariant) }
    if (selectedAspects.isEmpty()) {
        selectedAspects = defaults
    }

    val effectiveResult = remember(selectedVariant, selectedAspects) {
        computeEffective(selectedVariant, selectedAspects)
    }

    val stepTitle = when (step) {
        0 -> "Elige la variante"
        1 -> "Ajusta los aspectos técnicos"
        else -> "Así queda el enfoque muscular"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        KpknTopRow(
            onBack = null,
            actions = {
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Cerrar",
                    onClick = onDismiss,
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Paso ${step + 1} de $WIZARD_STEPS",
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = stepTitle,
                    style = KpknType.title,
                    color = WizardColors.text,
                )
                Text(
                    text = selectedVariant.name,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            when (step) {
                0 -> VariantSelectorStep(
                    group = group,
                    selectedVariant = selectedVariant,
                    onSelect = { variant ->
                        selectedVariant = variant
                        prefStore.saveLastVariant(group.id, variant.id)
                        val variantDefaults = computeDefaults(variant)
                        if (variantDefaults.isNotEmpty()) {
                            selectedAspects = variantDefaults
                        }
                    },
                )
                1 -> TechnicalAspectsStep(
                    variant = selectedVariant,
                    selectedAspects = selectedAspects,
                    onAspectChange = { id, optId ->
                        val updated = selectedAspects + (id to optId)
                        selectedAspects = updated
                        prefStore.saveAspectDefaults(group.id, updated)
                    },
                )
                else -> MuscleActivationPreview(
                    baseMuscles = selectedVariant.involvedMuscles,
                    effectiveMuscles = effectiveResult.effectiveMuscles,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                KpknTextAction(
                    text = "Atrás",
                    onClick = { step -= 1 },
                )
            }
            if (step < WIZARD_STEPS - 1) {
                KpknPrimaryPill(
                    text = "Siguiente",
                    onClick = { step += 1 },
                    modifier = Modifier.weight(1f),
                    enabled = step != 0 || selectedVariant.variantGroupId != null,
                )
            } else {
                KpknPrimaryPill(
                    text = "Aplicar ejercicio",
                    onClick = { onConfirm(selectedVariant, selectedAspects) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun computeDefaults(variant: ExerciseMuscleInfo): Map<String, String> {
    val aspects = variant.catalogOptionAxes.orEmpty()
    return aspects.mapNotNull { aspect ->
        val defaultId = aspect.defaultOptionId
            ?: aspect.options.firstOrNull()?.id
        defaultId?.let { aspect.id to it }
    }.toMap()
}

private fun computeEffective(
    variant: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
): TechnicalAspectEngine.EffectiveMuscleResult {
    val selectedOptions = selectedAspects.mapNotNull { (aspectId, optId) ->
        variant.catalogOptionAxes
            ?.firstOrNull { it.id == aspectId }
            ?.options
            ?.firstOrNull { it.id == optId }
    }
    return TechnicalAspectEngine.computeEffectiveMuscles(
        baseMuscles = variant.involvedMuscles,
        selectedOptions = selectedOptions,
    )
}

/** Paso 1 (SE6b): las variantes del grupo, una fila de radio cada una, con sus músculos principales. */
@Composable
private fun VariantSelectorStep(
    group: VariantGroup,
    selectedVariant: ExerciseMuscleInfo,
    onSelect: (ExerciseMuscleInfo) -> Unit,
) {
    KpknModule {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            group.variants.forEach { variant ->
                val primaryMuscles = variant.involvedMuscles
                    .filter { it.role == MuscleRole.PRIMARY }
                    .map { it.muscle }
                KpknRadioRow(
                    selected = variant.id == selectedVariant.id,
                    title = variant.variantName ?: variant.name,
                    subtitle = primaryMuscles.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    onClick = { onSelect(variant) },
                )
            }
        }
    }
}

/** Paso 2 (SE6b): cada aspecto técnico en su módulo, con sus opciones como filas de radio y su explicación debajo. */
@Composable
private fun TechnicalAspectsStep(
    variant: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
    onAspectChange: (aspectId: String, optionId: String) -> Unit,
) {
    val aspects = variant.catalogOptionAxes.orEmpty()
    if (aspects.isEmpty()) {
        Text(
            text = "Este ejercicio no tiene aspectos técnicos que ajustar.",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        aspects.forEach { aspect ->
            AspectSelectorModule(
                aspect = aspect,
                selectedOptionId = selectedAspects[aspect.id],
                onSelect = { optId -> onAspectChange(aspect.id, optId) },
            )
        }
    }
}

@Composable
private fun AspectSelectorModule(
    aspect: TechnicalAspect,
    selectedOptionId: String?,
    onSelect: (String) -> Unit,
) {
    val selectedId = selectedOptionId ?: aspect.defaultOptionId
        ?: aspect.options.firstOrNull()?.id

    KpknModule {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = aspect.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            aspect.options.forEach { option ->
                KpknRadioRow(
                    selected = option.id == selectedId,
                    title = option.name,
                    subtitle = option.description?.takeIf { it.isNotBlank() },
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

/**
 * Paso 3 (SE6b): el enfoque muscular resultante. Cada músculo con su papel, su aporte por una serie y una barra en su color; los que
 * tienen definición canónica se abren al tocarlos.
 */
@Composable
internal fun MuscleActivationPreview(
    baseMuscles: List<InvolvedMuscle>,
    effectiveMuscles: List<InvolvedMuscle>,
) {
    var selectedKnowledge by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    KpknModule {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            effectiveMuscles.forEach { muscle ->
                val baseEntry = baseMuscles.firstOrNull { it.muscle == muscle.muscle }
                val effVC = muscle.volumeContribution ?: 0.0
                val roleChanged = baseEntry?.role != muscle.role
                val knowledge = canonicalMuscleKnowledgeForVolumeLabel(muscle.muscle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (knowledge != null) {
                                Modifier.clickable(role = Role.Button, onClick = { selectedKnowledge = knowledge })
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = muscle.muscle,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (roleChanged) {
                            Text(
                                text = "Cambio de rol",
                                style = KpknType.caption,
                                color = SymbolPalette.energia,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = formatRoleLabel(muscle.role),
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    Text(
                        text = "${formatSeriesEquivalent(effVC)} · ${formatVolumePercent(effVC)}",
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                    )
                    KpknProgressBar(
                        progress = effVC.toFloat(),
                        color = SymbolPalette.musculo,
                        height = 6.dp,
                    )
                }
            }
        }
    }
    selectedKnowledge?.let { knowledge ->
        CanonicalKnowledgeOverlay(
            knowledge = knowledge,
            onDismiss = { selectedKnowledge = null },
        )
    }
}

private fun formatRoleLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Principal"
    MuscleRole.SECONDARY -> "Secundario"
    MuscleRole.STABILIZER -> "Estabilizador"
    MuscleRole.NEUTRALIZER -> "Guiado"
}

object CatalogSelectionDraftBridge {
    private data class VariantResult(
        val variantName: String?,
        val variantGroupId: String?,
        val variantGroupName: String?,
        val selectedAspects: Map<String, String>,
    )

    private val results = mutableMapOf<String, VariantResult>()

    fun store(
        exerciseDbId: String,
        variantName: String?,
        variantGroupId: String?,
        variantGroupName: String?,
        selectedAspects: Map<String, String>,
    ) {
        results[exerciseDbId.lowercase()] = VariantResult(
            variantName = variantName,
            variantGroupId = variantGroupId,
            variantGroupName = variantGroupName,
            selectedAspects = selectedAspects,
        )
    }

    fun consume(exerciseDbId: String?): VariantSelection? {
        if (exerciseDbId == null) return null
        val result = results.remove(exerciseDbId.lowercase()) ?: return null
        return VariantSelection(
            variantName = result.variantName,
            variantGroupId = result.variantGroupId,
            variantGroupName = result.variantGroupName,
            selectedAspects = result.selectedAspects,
        )
    }

    data class VariantSelection(
        val variantName: String?,
        val variantGroupId: String?,
        val variantGroupName: String?,
        val selectedAspects: Map<String, String>,
    )

    fun clear() {
        results.clear()
    }
}
