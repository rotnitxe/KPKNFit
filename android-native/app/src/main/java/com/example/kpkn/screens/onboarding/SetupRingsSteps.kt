package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Cuerpo de los pasos del bloque RINGS.
 *
 * La cabecera (pregunta, subtítulo, progreso y CTA) la pinta la raíz del
 * wizard; aquí solo se dibuja el control, siempre con las opciones del catálogo
 * `SetupStepDefinitions`.
 *
 * Reglas de honestidad del bloque:
 * - «Sí / No / No lo sé» son estados distintos: desconocer el historial nunca
 *   equivale a no haber entrenado ni fabrica sesiones.
 * - Las sensaciones (músculos, energía, columna) se preguntan siempre y su
 *   «No lo sé» no inventa un nivel 0.
 * - El resultado expone la cobertura por canal; sin datos dice «Sin calibrar»
 *   y jamás un 100 % afirmativo.
 *
 * Cada interacción escribe el dato de dominio con `updateStep` (limpieza de la
 * evidencia al responder «No»/«No lo sé» y la exposición axial completa) y
 * después la respuesta del paso con el setter, cuya proyección
 * (`SetupStepAnswers.withStep*`) va al final y por eso manda sobre los campos
 * que comparte. Ninguna función mueve el cursor: solo el CTA confirma.
 */
@Composable
fun SetupRingsStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.RINGS_RECENT -> RingsRecentContent(state, vm)
        SetupStepId.RINGS_SESSIONS -> RingsSessionsContent(state, vm)
        SetupStepId.RINGS_RECENCY -> RingsRecencyContent(state, vm)
        SetupStepId.RINGS_ACTIVITY -> RingsActivityContent(state, vm)
        SetupStepId.RINGS_INTENSITY -> RingsIntensityContent(state, vm)
        SetupStepId.RINGS_AXIAL -> RingsAxialContent(state, vm)
        SetupStepId.RINGS_MUSCLE_FEELING -> RingsFeelingContent(state, vm, SetupStepId.RINGS_MUSCLE_FEELING)
        SetupStepId.RINGS_ENERGY_FEELING -> RingsFeelingContent(state, vm, SetupStepId.RINGS_ENERGY_FEELING)
        SetupStepId.RINGS_STRUCTURE_FEELING -> RingsFeelingContent(state, vm, SetupStepId.RINGS_STRUCTURE_FEELING)
        SetupStepId.RINGS_DISCOMFORT -> RingsDiscomfortContent(state, vm)
        SetupStepId.RINGS_RESULT -> RingsResultContent(state, vm)
        // MILESTONE_RINGS lo pinta la raíz y RINGS_START es legacy-only (fuera
        // de la ruta productiva): aquí no se dibuja nada.
        else -> Unit
    }
}

// ─── Escritura sobre el borrador ────────────────────────────────────────────

private fun SetupWizardDraft.withRingsAnswers(
    change: (SetupRingsAnswers) -> SetupRingsAnswers,
): SetupWizardDraft = copy(ringsAnswers = change(ringsAnswers ?: SetupRingsAnswers()))

/** La respuesta deja de estar respaldada por evidencia declarada. */
private fun SetupRingsAnswers.withoutEvidence(): SetupRingsAnswers = copy(
    sessionsLastSevenDays = null,
    lastSessionRecencyDays = null,
    recencyDays = null,
    activityType = null,
    activityTypeState = InitialRecoveryResponseState.UNKNOWN,
    intensityLevel = null,
    axialExposure = InitialRecoveryAxialExposure(),
    capturedAtMs = null,
)

// ─── RINGS_RECENT ───────────────────────────────────────────────────────────

@Composable
private fun RingsRecentContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_RECENT
    val answers = state.draft.ringsAnswers
    val fallback = when (answers?.recentTrainingState) {
        SetupRecentTrainingState.YES -> "yes"
        SetupRecentTrainingState.NO -> "no"
        SetupRecentTrainingState.UNKNOWN -> "unknown"
        else -> null
    }
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "yes" to "Hay sesiones registradas que contar como evidencia.",
            "no" to "Sin sesiones en esta semana.",
            "unknown" to "No lo recuerdas: pediremos las sensaciones, no las sesiones.",
        ),
        onSelect = { value ->
            vm.updateStep(step) { draft ->
                draft.withRingsAnswers { old ->
                    when (value) {
                        "yes" -> old.copy(
                            recentTraining = true,
                            recentTrainingState = SetupRecentTrainingState.YES,
                            capturedAtMs = null,
                        )

                        "no" -> old.copy(
                            recentTraining = false,
                            recentTrainingState = SetupRecentTrainingState.NO,
                        ).withoutEvidence()

                        else -> old.copy(
                            recentTraining = null,
                            recentTrainingState = SetupRecentTrainingState.UNKNOWN,
                        ).withoutEvidence()
                    }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── RINGS_SESSIONS ─────────────────────────────────────────────────────────

@Composable
private fun RingsSessionsContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_SESSIONS
    val fallback = state.draft.ringsAnswers?.sessionsLastSevenDays?.toString()
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            value.toIntOrNull()?.let { sessions ->
                vm.updateStep(step) { draft ->
                    draft.withRingsAnswers { it.copy(sessionsLastSevenDays = sessions, capturedAtMs = null) }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── RINGS_RECENCY ──────────────────────────────────────────────────────────

@Composable
private fun RingsRecencyContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_RECENCY
    val fallback = state.draft.ringsAnswers?.lastSessionRecencyDays?.toString()
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            value.toIntOrNull()?.let { days ->
                vm.updateStep(step) { draft ->
                    draft.withRingsAnswers {
                        it.copy(lastSessionRecencyDays = days, recencyDays = days, capturedAtMs = null)
                    }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── RINGS_ACTIVITY ─────────────────────────────────────────────────────────

@Composable
private fun RingsActivityContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_ACTIVITY
    val fallback = state.draft.ringsAnswers?.activityType?.name
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            setupEnumValueOrNull<InitialRecoveryActivityType>(value)?.let { activity ->
                vm.updateStep(step) { draft ->
                    draft.withRingsAnswers {
                        it.copy(activityType = activity, activityTypeState = InitialRecoveryResponseState.DECLARED, capturedAtMs = null)
                    }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── RINGS_INTENSITY ────────────────────────────────────────────────────────

@Composable
private fun RingsIntensityContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_INTENSITY
    val fallback = state.draft.ringsAnswers?.intensityLevel?.name
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            setupEnumValueOrNull<InitialRecoveryIntensity>(value)?.let { intensity ->
                vm.updateStep(step) { draft ->
                    draft.withRingsAnswers { it.copy(intensityLevel = intensity, capturedAtMs = null) }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── RINGS_AXIAL ────────────────────────────────────────────────────────────

@Composable
private fun RingsAxialContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_AXIAL
    val axial = state.draft.ringsAnswers?.axialExposure
    val fallback = if (axial != null && axial.state == InitialRecoveryResponseState.DECLARED) {
        if (axial.sessions == 1) "yes" else "no"
    } else {
        null
    }
    val selected = state.draft.setupSelectionOf(step, fallback)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            vm.updateStep(step) { draft ->
                draft.withRingsAnswers { answers ->
                    val stateValue = if (value == "unknown") InitialRecoveryResponseState.UNKNOWN
                    else InitialRecoveryResponseState.DECLARED
                    answers.copy(
                        axialExposure = InitialRecoveryAxialExposure(
                            state = stateValue,
                            sessions = if (value == "yes") 1 else 0,
                            intensity = answers.intensityLevel,
                            recencyDays = answers.lastSessionRecencyDays,
                        ),
                        capturedAtMs = null,
                    )
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── Sensaciones: músculos / energía / columna ──────────────────────────────

@Composable
private fun RingsFeelingContent(state: SetupWizardState, vm: SetupWizardViewModel, step: SetupStepId) {
    val answers = state.draft.ringsAnswers
    val feeling: Int? = when (step) {
        SetupStepId.RINGS_MUSCLE_FEELING -> answers?.muscleFeeling
        SetupStepId.RINGS_ENERGY_FEELING -> answers?.energy
        else -> answers?.structureFeeling
    }
    val selected = state.draft.setupSelectionOf(step, feeling?.toString())
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "unknown" to "No lo sabes: se queda sin nivel en lugar de inventar un 0.",
        ),
        onSelect = { value ->
            val level = value.toIntOrNull()
            vm.updateStep(step) { draft ->
                draft.withRingsAnswers { answersNow ->
                    when (step) {
                        SetupStepId.RINGS_MUSCLE_FEELING -> answersNow.copy(muscleFeeling = level, capturedAtMs = null)
                        SetupStepId.RINGS_ENERGY_FEELING -> answersNow.copy(energy = level, capturedAtMs = null)
                        else -> answersNow.copy(structureFeeling = level, capturedAtMs = null)
                    }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "Prefiero omitir esta sensación",
        visible = selected.isEmpty(),
    )
}

// ─── RINGS_DISCOMFORT ───────────────────────────────────────────────────────

@Composable
private fun RingsDiscomfortContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_DISCOMFORT
    val answers = state.draft.ringsAnswers
    val fallback = when (answers?.discomfortState) {
        SetupDiscomfortState.OMITTED -> setOf("omit")
        SetupDiscomfortState.NONE -> setOf("none")
        SetupDiscomfortState.DECLARED -> answers?.discomfortIds.orEmpty().toSet()
        else -> emptySet()
    }
    val selected = state.draft.setupSelectionOf(step, *fallback.toTypedArray())
    SetupBodyMultiChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "none" to "Ninguna molestia en este momento.",
            "omit" to "Prefieres no responder a esta pregunta.",
        ),
        // El toggle se resuelve dentro del mutex del VM sobre `selectedValues`
        // (que cae en `typedSelections` y por eso conserva molestias manuales
        // fuera del catálogo); `withStepChoices` proyecta `discomfortState` e
        // `discomfortIds` y `validateStep(DISCOMFORT)` lee ese estado.
        onSelect = { value -> vm.toggleStepChoice(step, value) },
    )
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "Prefiero omitir las molestias",
        visible = selected.isEmpty(),
    )
}

// ─── RINGS_RESULT ───────────────────────────────────────────────────────────

@Composable
private fun RingsResultContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    // Cobertura por canal publicada por la preview (SetupRingsPreview.coverage).
    // Sin preview todavía el canal entero está «Sin calibrar»: nunca un 100 %.
    val coverage = state.ringsCoveragePreview ?: RingsCoverage.NO_DATA
    val error = state.ringsPreviewError
    val loading = state.ringsPreviewLoading

    if (error != null) {
        Text(error, style = WizardTypography.bodySmall, color = WizardColors.danger)
        TextButton(onClick = { vm.retryFailedOperation(SetupRetryOperation.RINGS_PREVIEW) }) {
            Text("Reintentar", color = WizardColors.text)
        }
    } else if (loading) {
        SetupBodyHint(text = "Calculando tu punto de partida…")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        RingsChannelRow(label = "Músculos", coverage = coverage.muscular)
        RingsChannelRow(label = "Energía", coverage = coverage.system)
        RingsChannelRow(label = "Columna", coverage = coverage.structure)
    }

    SetupBodyCaption(
        text = "«Sin calibrar» significa que todavía no hay datos de ese canal: no afirma un porcentaje " +
            "ni dice que no hayas entrenado.",
        modifier = Modifier.padding(top = 4.dp),
    )

    RingsResultEditActions(state, vm)
}

@Composable
private fun RingsChannelRow(label: String, coverage: RingsChannelCoverage) {
    val value = coverage.score?.toString() ?: "Sin calibrar"
    val description = "$label: $value. ${coverage.label}."
    Surface(
        color = WizardColors.cardFill,
        shape = WizardShapes.card,
        border = BorderStroke(WizardColors.unselectedBorderWidth, WizardColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(label, style = WizardTypography.cardTitle, color = WizardColors.text)
                Text(coverage.label, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
            }
            if (coverage.hasData) {
                Text(
                    text = value,
                    style = WizardTypography.header,
                    color = WizardColors.text,
                )
            } else {
                Text(
                    text = value,
                    style = WizardTypography.cardSubtitle,
                    color = WizardColors.textFaint,
                )
            }
        }
    }
}

/** Acciones de edición del resultado: vuelven al paso sin borrar respuestas. */
@Composable
private fun RingsResultEditActions(state: SetupWizardState, vm: SetupWizardViewModel) {
    val route = SetupStepGraph.stepIds(state.draft.stepContext())
    val targets = listOf(
        SetupStepId.RINGS_RECENT to "Editar entrenamiento reciente",
        SetupStepId.RINGS_MUSCLE_FEELING to "Editar sensaciones",
        SetupStepId.RINGS_DISCOMFORT to "Editar molestias",
    ).filter { (step, _) -> step in route }
    if (targets.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        targets.forEach { (step, label) ->
            SetupBodyEditAction(label = label, onClick = { vm.editStep(step) })
        }
    }
}
