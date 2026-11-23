package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Editor de un ciclo, con el patrón de e-block (el lienzo no lo dibuja): «Nombre», «Tipo» con chips y «Repetir cada (ciclos)», con el
 * choque de cadencia debajo del campo. Con un ciclo existente, además el grupo «Este ciclo» en filas `.mi`: posponer la próxima vez,
 * cancelar o reactivar, y «Eliminar ciclo» en `--danger`, que confirma la hoja de Ciclos. Guardar exige nombre y una cadencia de al menos
 * 1 que no choque con otro ciclo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCycleEditorSheet(
    loop: ProgramCycleLoop?,
    existing: List<ProgramCycleLoop>,
    onSave: (ProgramCycleDraft) -> Unit,
    onAction: (ProgramCycleAction) -> Unit,
    onDismiss: () -> Unit,
    apreExerciseNames: List<String> = emptyList(),
) {
    val types = LoopType.entries.filterNot { it == LoopType.COMPETITION }
    val hasApre = apreExerciseNames.isNotEmpty()
    val wavePresets = remember(hasApre) {
        com.example.kpkn.domain.training.LoopPresetEngine.wavePresets().filter { preset ->
            hasApre || preset.id != "onda-apre-10-6-3"
        }
    }
    var title by remember(loop?.id) { mutableStateOf(loop?.title.orEmpty()) }
    var type by remember(loop?.id) { mutableStateOf(loop?.type ?: LoopType.CUSTOM) }
    var mode by remember(loop?.id) {
        mutableStateOf(loop?.mode ?: com.example.kpkn.data.models.LoopMode.RECURRING_WEEK)
    }
    var waveSteps by remember(loop?.id) {
        mutableStateOf(loop?.waveSteps.orEmpty())
    }
    val defaultScope = if (hasApre) {
        com.example.kpkn.data.models.LoopModulationScope.APRE_EXERCISES
    } else {
        com.example.kpkn.data.models.LoopModulationScope.MAIN_COMPOUNDS
    }
    var modulationScope by remember(loop?.id, hasApre) {
        mutableStateOf(
            loop?.modulationScope?.takeIf { hasApre || it != com.example.kpkn.data.models.LoopModulationScope.APRE_EXERCISES }
                ?: defaultScope,
        )
    }
    var selectedPresetId by remember(loop?.id) {
        mutableStateOf(
            loop?.let { existingLoop ->
                wavePresets.firstOrNull { it.loop.waveSteps == existingLoop.waveSteps }?.id
            },
        )
    }
    var cadenceText by remember(loop?.id) { mutableStateOf((loop?.cadence ?: DEFAULT_CADENCE).toString()) }
    val cadence = if (mode == com.example.kpkn.data.models.LoopMode.PROGRESSIVE_WAVE) 1 else (cadenceText.toIntOrNull()?.coerceAtLeast(1) ?: 0)
    val conflict = if (mode == com.example.kpkn.data.models.LoopMode.PROGRESSIVE_WAVE) {
        null
    } else {
        cycleCadenceConflict(existing, loop?.id, cadence)
    }
    val canSave = title.isNotBlank() && cadence > 0 && conflict == null && type != LoopType.COMPETITION &&
        (mode == com.example.kpkn.data.models.LoopMode.RECURRING_WEEK || waveSteps.isNotEmpty())

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(title = if (loop == null) "Nuevo loop" else loop.title.ifBlank { "Loop" })
            StructureSheetGroup(text = "Modo del loop")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StructureChip(
                    text = "Evento periódico",
                    selected = mode == com.example.kpkn.data.models.LoopMode.RECURRING_WEEK,
                    onClick = { mode = com.example.kpkn.data.models.LoopMode.RECURRING_WEEK },
                )
                StructureChip(
                    text = "Evolución semanal / Onda",
                    selected = mode == com.example.kpkn.data.models.LoopMode.PROGRESSIVE_WAVE,
                    onClick = {
                        mode = com.example.kpkn.data.models.LoopMode.PROGRESSIVE_WAVE
                        if (waveSteps.isEmpty() && wavePresets.isNotEmpty()) {
                            val first = wavePresets.first()
                            selectedPresetId = first.id
                            if (title.isBlank()) title = first.title
                            waveSteps = first.loop.waveSteps
                            modulationScope = first.loop.modulationScope
                        }
                    },
                )
            }
            if (mode == com.example.kpkn.data.models.LoopMode.PROGRESSIVE_WAVE) {
                StructureSheetGroup(text = "Plantilla de onda")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    wavePresets.forEach { preset ->
                        StructureChip(
                            text = preset.title,
                            selected = selectedPresetId == preset.id,
                            onClick = {
                                selectedPresetId = preset.id
                                title = preset.title
                                waveSteps = preset.loop.waveSteps
                                modulationScope = preset.loop.modulationScope
                            },
                        )
                    }
                }
                StructureSheetGroup(text = "A qué ejercicios aplica")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    buildList {
                        if (hasApre) {
                            add(com.example.kpkn.data.models.LoopModulationScope.APRE_EXERCISES to "Solo APRE")
                        }
                        add(com.example.kpkn.data.models.LoopModulationScope.MAIN_COMPOUNDS to "Básicos")
                        add(com.example.kpkn.data.models.LoopModulationScope.ALL_EXERCISES to "Todos")
                    }.forEach { (scopeOption, scopeLabel) ->
                        StructureChip(
                            text = scopeLabel,
                            selected = modulationScope == scopeOption,
                            onClick = { modulationScope = scopeOption },
                        )
                    }
                }
                StructureSheetGroup(text = "Semanas de la onda")
                waveSteps.forEachIndexed { index, step ->
                    fun autoStepLabel(candidate: com.example.kpkn.data.models.LoopWaveStep): String {
                        val parts = buildList {
                            candidate.apreVariant?.let {
                                add(
                                    when (it) {
                                        com.example.kpkn.data.models.LoopApreVariant.APRE_10 -> "APRE 10"
                                        com.example.kpkn.data.models.LoopApreVariant.APRE_6 -> "APRE 6"
                                        com.example.kpkn.data.models.LoopApreVariant.APRE_3 -> "APRE 3"
                                    },
                                )
                            }
                            candidate.targetRirOverride?.let { add("RIR $it") }
                            candidate.setsDelta?.takeIf { it != 0 }?.let { add(if (it > 0) "+$it series" else "$it series") }
                            candidate.repRangeOverride?.let { (lo, hi) -> add("$lo-$hi reps") }
                            if (candidate.isDeload) add("Descarga")
                        }
                        return if (parts.isEmpty()) "Semana ${candidate.weekNumber}" else "Semana ${candidate.weekNumber} · ${parts.joinToString(" · ")}"
                    }
                    val updateStep: (com.example.kpkn.data.models.LoopWaveStep) -> Unit = { updated ->
                        selectedPresetId = null
                        val withSyncedLabel = updated.copy(label = autoStepLabel(updated))
                        waveSteps = waveSteps.mapIndexed { i, current -> if (i == index) withSyncedLabel else current }
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            StructureCaption(
                                text = "Semana ${step.weekNumber}: ${step.label.ifBlank { step.apreVariant?.name ?: "Paso ${step.weekNumber}" }}",
                                color = WizardColors.text,
                            )
                            if (waveSteps.size > 1) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        selectedPresetId = null
                                        waveSteps = waveSteps.filterIndexed { i, _ -> i != index }
                                            .mapIndexed { i, item -> item.copy(weekNumber = i + 1) }
                                    },
                                ) {
                                    androidx.compose.material3.Text("Quitar", color = WizardColors.danger)
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                null to "Base",
                                com.example.kpkn.data.models.LoopApreVariant.APRE_10 to "APRE 10",
                                com.example.kpkn.data.models.LoopApreVariant.APRE_6 to "APRE 6",
                                com.example.kpkn.data.models.LoopApreVariant.APRE_3 to "APRE 3",
                            ).forEach { (variantOpt, variantLabel) ->
                                StructureChip(
                                    text = variantLabel,
                                    selected = step.apreVariant == variantOpt,
                                    onClick = { updateStep(step.copy(apreVariant = variantOpt)) },
                                )
                            }
                            listOf(3, 2, 1, 0).forEach { rirOpt ->
                                StructureChip(
                                    text = "RIR $rirOpt",
                                    selected = step.targetRirOverride == rirOpt,
                                    onClick = {
                                        updateStep(
                                            step.copy(targetRirOverride = if (step.targetRirOverride == rirOpt) null else rirOpt),
                                        )
                                    },
                                )
                            }
                            StructureChip(
                                text = "Descarga",
                                selected = step.isDeload,
                                onClick = { updateStep(step.copy(isDeload = !step.isDeload)) },
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                StructureChip(
                    text = "+ Añadir semana",
                    selected = false,
                    onClick = {
                        selectedPresetId = null
                        val nextNum = (waveSteps.maxOfOrNull { it.weekNumber } ?: waveSteps.size) + 1
                        waveSteps = waveSteps + com.example.kpkn.data.models.LoopWaveStep(
                            weekNumber = nextNum,
                            label = "Semana $nextNum",
                        )
                    },
                )
            }
            StructureTextField(label = "Nombre", value = title, onValueChange = { title = it }, placeholder = "Por ejemplo, descarga de verano")
            if (mode == com.example.kpkn.data.models.LoopMode.RECURRING_WEEK) {
                StructureSheetGroup(text = "Tipo")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { option ->
                        StructureChip(text = cycleTypeLabel(option), selected = type == option, onClick = { type = option })
                    }
                }
                StructureTextField(
                    label = "Repetir cada (loops)",
                    value = cadenceText,
                    onValueChange = { cadenceText = it.filter(Char::isDigit) },
                    placeholder = DEFAULT_CADENCE.toString(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            conflict?.let {
                StructureCaption(text = cycleConflictLine(it, cadence), color = WizardColors.danger, modifier = Modifier.padding(top = 8.dp))
            }
            if (loop != null) {
                StructureSheetGroup(text = "Este loop")
                if (mode == com.example.kpkn.data.models.LoopMode.RECURRING_WEEK) {
                    StructureMenuRow(
                        icon = KpknLineIcons.Clock,
                        title = "Posponer la próxima semana",
                        divider = false,
                        onClick = { onAction(ProgramCycleAction.POSTPONE_NEXT) },
                    )
                }
                if (loop.cancelled) {
                    StructureMenuRow(icon = KpknLineIcons.Play, title = "Reactivar loop", onClick = { onAction(ProgramCycleAction.REACTIVATE) })
                } else {
                    StructureMenuRow(icon = KpknLineIcons.Pause, title = "Cancelar loop", onClick = { onAction(ProgramCycleAction.CANCEL) })
                }
            }
            StructurePrimaryPill(
                text = "Guardar",
                onClick = {
                    onSave(
                        ProgramCycleDraft(
                            loopId = loop?.id,
                            title = title.trim(),
                            type = type,
                            cadence = cadence,
                            mode = mode,
                            waveSteps = waveSteps,
                            modulationScope = modulationScope,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            if (loop != null) {
                StructureDangerAction(text = "Eliminar loop", onClick = { onAction(ProgramCycleAction.DELETE) })
            }
        }
    }
}

/** La cadencia que se propone a un ciclo nuevo: cada 4 ciclos, como antes. */
private const val DEFAULT_CADENCE = 4
