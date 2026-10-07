package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanRejectionPresenter
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.PlanRepairAdvisor
import com.example.kpkn.domain.onboarding.PresentationContext
import com.example.kpkn.domain.onboarding.RejectionAction
import com.example.kpkn.domain.onboarding.RejectionPresentation
import com.example.kpkn.domain.onboarding.RejectionView
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.entreno.EntrenoFreshDayStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoGoalStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoMarksStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoMaterialStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoMusclesStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlacesStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoSessionTimeStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoCapabilitiesStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoWeekLayoutStep
import com.example.kpkn.screens.onboarding.entreno.EntrenoWeekdaysStep
import com.example.kpkn.screens.programs.PlanInfoMode
import com.example.kpkn.screens.programs.PlanInfoSheet

/**
 * Contenido de cada paso del bloque **Entreno** del wizard de configuración.
 *
 * Contrato de la capa raíz (`SetupStepScreen`): la raíz pinta la pregunta, el subtítulo y el CTA del scaffold;
 * aquí solo vive el control del paso, y **solo delega**: cada paso nuevo de Entreno v2 tiene su propio archivo
 * `entreno/Entreno<Paso>Step.kt` con la firma `(state, vm)`. Es el punto de enganche de los controles de símbolos
 * animados (cambiar UN solo archivo sustituye el control de un paso). Los controles provisionales solo escriben por
 * la API del ViewModel (`togglePlace`, `setGoalProfile`, `toggleWeekday`, …; ningún setter navega: el avance es el
 * CTA del host, `submitCurrentStep`).
 *
 * Los pasos de opción simples (experiencia, calibración, cardio) siguen siendo tarjetas del catálogo con
 * `setStepChoice`; los pasos retirados de la ruta (reparto, autorregulación, calentamientos, revisión del plan…) ya
 * no tienen control.
 */
@Composable
fun SetupTrainingStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.EQUIPMENT -> EntrenoPlacesStep(state = state, vm = vm)
        SetupStepId.AVAILABILITY -> EntrenoMaterialStep(state = state, vm = vm)
        SetupStepId.GOAL -> EntrenoGoalStep(state = state, vm = vm)
        SetupStepId.FRESH_DAY -> EntrenoFreshDayStep(state = state, vm = vm)
        SetupStepId.WEEKDAYS -> EntrenoWeekdaysStep(state = state, vm = vm)
        SetupStepId.SESSION_TIME -> EntrenoSessionTimeStep(state = state, vm = vm)
        SetupStepId.CAPABILITIES -> EntrenoCapabilitiesStep(state = state, vm = vm)
        SetupStepId.PRIORITIES -> EntrenoMusclesStep(state = state, vm = vm)
        SetupStepId.TRAINING_MAX -> EntrenoMarksStep(state = state, vm = vm)
        SetupStepId.PLAN -> EntrenoPlanStep(state = state, vm = vm)
        SetupStepId.WEEK_LAYOUT -> EntrenoWeekLayoutStep(state = state, vm = vm)

        SetupStepId.EXPERIENCE,
        SetupStepId.ROUTE,
        SetupStepId.VOLUME_TECHNIQUE,
        SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH,
        SetupStepId.VOLUME_MOBILITY,
        SetupStepId.CARDIO_TYPE,
        SetupStepId.CARDIO_TIME,
        SetupStepId.HOME_EQUIPMENT,
        -> TrainingChoiceStep(step = step, state = state, vm = vm)

        // La raíz corta los hitos antes de delegar (StepQuestion + hitos), así
        // que este resumen solo pinta si el hito llega aquí: nunca duplica.
        SetupStepId.MILESTONE_TRAINING -> TrainingMilestoneSummary(state = state)

        // Pasos de otros bloques (datos básicos, nutrición, rings y revisión
        // final) y pasos retirados de la ruta de Entreno: sin control propio aquí.
        else -> Unit
    }
}

// ─── Lógica pura (testeable) ────────────────────────────────────────────────
//
// La selección canónica la posee M1: `draft.selectedValues(step)` devuelve lo
// guardado con `setStepChoice(s)` y, si todavía no hay selección, proyecta la
// reserva tipada de un borrador rehidratado. Aquí solo se lee; nunca se
// duplica esa lógica (un duplicado propio preseleccionaba «recomendado» en una
// ruta sin responder).

/**
 * D6 (A.E2): un reparto de powerlifting (etiqueta `POWERLIFTING` del catálogo de repartos) solo se ofrece en Fuerza;
 * Músculo, Fuerza y músculo y Atleta completo no lo listan. Sin objetivo ([goal] null) todo se ofrece. Lo usa el reductor
 * de GOAL para retirar un reparto que el objetivo nuevo ya no ofrece; la lista de repartos del tablero de la semana y sus
 * nombres viven en el dominio (`domain/training/split/SplitCatalogRules`).
 */
internal fun isSplitOfferedForGoal(split: SplitTemplate, goal: SetupGoal?): Boolean =
    goal == null || goal == SetupGoal.STRENGTH || SplitTag.POWERLIFTING !in split.tags

// ─── Pasos de opción (defs actuales, valores estables) ──────────────────────

/**
 * Opción simple o múltiple según el control declarado en la definición: el
 * valor estable de cada tarjeta es el que viaja a `setStepChoice(s)`. La
 * respuesta se lee con `draft.selectedValues(step)`; aquí no se fabrica ninguna.
 */
@Composable
private fun TrainingChoiceStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    if (SetupStepDefinitions.options(step).isEmpty()) {
        TrainingNotice("Este paso no tiene opciones disponibles.", TrainingNoticeTone.ERROR)
        return
    }
    val selected = state.draft.selectedValues(step)
    BikePresenceConfirmation(step = step, state = state, vm = vm)
    if (SetupStepDefinitions.control(step) == SetupControlKind.MULTI_CHOICE) {
        SetupBodyMultiChoiceCards(
            step = step,
            selected = selected,
            onSelect = { value -> vm.toggleStepChoice(step, value) },
        )
    } else {
        SetupBodyChoiceCards(
            step = step,
            selected = selected,
            // Re-pulsar la tarjeta elegida no la deselecciona (selección única).
            onSelect = { value -> if (value !in selected) vm.setStepChoice(step, value) },
        )
    }
    SetupBodySkipAction(step = step, vm = vm)
}

/**
 * T-005 / §15.1 — BIKE_OUTDOOR exige confirmar acceso a bicicleta con
 * PRESENCIA si no consta: no se hereda de la categoría «Cardio» ni del resto
 * del material de gimnasio. Solo presencia, sin kilos ni cantidades.
 */
@Composable
private fun BikePresenceConfirmation(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    if (step != SetupStepId.CARDIO_TYPE) return
    if ("BIKE_OUTDOOR" !in state.draft.selectedValues(step)) return
    val presence = SetupApparatusPanel.presenceOf(
        state.draft.trainingOptions.availability,
        SetupApparatusPanel.OUTDOOR_BIKE_KEY,
    )
    if (presence == ApparatusPresence.PRESENT) return

    fun write(value: ApparatusPresence) {
        vm.updateStep(SetupStepId.CARDIO_TYPE) { draft ->
            draft.withApparatusPresence(SetupApparatusPanel.OUTDOOR_BIKE_KEY, value, isSupport = false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        TrainingNotice(
            text = if (presence == ApparatusPresence.ABSENT) {
                "Confirmaste que no tienes bicicleta: elige otro tipo de cardio o corrige aquí."
            } else {
                "¿Tienes acceso a una bicicleta? Confírmalo para continuar con bicicleta al aire libre."
            },
            tone = TrainingNoticeTone.INFO,
        )
        WizardChoiceCard(
            title = "Sí, tengo bicicleta",
            selected = presence == ApparatusPresence.PRESENT,
            onClick = { write(ApparatusPresence.PRESENT) },
        )
        WizardChoiceCard(
            title = "No tengo bicicleta",
            selected = presence == ApparatusPresence.ABSENT,
            onClick = { write(ApparatusPresence.ABSENT) },
        )
    }
}

// ─── PLAN: candidatos reales ────────────────────────────────────────────────

/**
 * Qué enseña la lista de planes (Paquete A · D2, B-01). Es una decisión pura sobre el estado, separada del
 * composable para poder probarla sin montar la pantalla:
 *
 *  - [Loading]: el barrido de candidatos sigue en curso.
 *  - [SearchFailed]: la BÚSQUEDA falló (el catálogo no quedó listo o hubo un error global): no hay lista, solo
 *    el motivo (`errors["candidates"]`) y «Reintentar». Se distingue del «ningún plan es viable» porque el
 *    fallo de búsqueda deja un rechazo global (`planId == null`) y el otro, rechazos por plan.
 *  - [NoneViable]: la búsqueda terminó y ningún plan es viable: la explicación con acciones
 *    ([CandidateIncompatibility]).
 *  - [Candidates]: hay lista y se enseña SIEMPRE. El error del preview de la SELECCIÓN ya no la esconde: va
 *    como aviso encima de las tarjetas, igual que la selección caída (el plan elegido que ya no encaja).
 */
internal sealed interface CandidateListGate {
    data object Loading : CandidateListGate

    data class SearchFailed(val message: String) : CandidateListGate

    data object NoneViable : CandidateListGate

    data class Candidates(
        val cards: List<SetupPlanCandidate>,
        /** Error del preview del plan elegido (`previewError`); null si no hay. */
        val previewError: String?,
        /** Selección caída que sigue pendiente de explicar; null si ya no corresponde a la selección actual. */
        val dropped: SetupDroppedSelection?,
        /**
         * Paquete A · C4: aviso del plan PROPIO del objetivo cuando quedó rechazado y tiene reparación de un toque,
         * aunque haya otros planes viables debajo («No pudimos armar tu plan de fuerza… [Sí, tengo rack y banco]»);
         * null cuando el propio es viable, no tiene reparación o ya lo explica el aviso de la selección caída.
         */
        val ownPlanNotice: RejectionNotice? = null,
    ) : CandidateListGate
}

internal fun candidateListGate(state: SetupWizardState): CandidateListGate {
    if (state.isCandidateLoading) return CandidateListGate.Loading
    val cards = state.planCandidates.ifEmpty { state.availablePlanCandidates }
    if (cards.isEmpty()) {
        val searchError = state.errors["candidates"]
        return if (searchError != null && state.candidateRejections.any { it.planId == null }) {
            CandidateListGate.SearchFailed(searchError)
        } else {
            CandidateListGate.NoneViable
        }
    }
    val selected = state.draft.selectedCatalogId
    // Elegir otro plan descarta el aviso (el ViewModel ya lo limpia); esta comprobación evita enseñarlo
    // si, por la vía que sea, la selección actual ya no es la que cayó.
    val dropped = state.droppedSelection?.takeIf { selected == null || selected == it.planId }
    return CandidateListGate.Candidates(
        cards = cards,
        previewError = state.previewError,
        dropped = dropped,
        ownPlanNotice = ownPlanNotice(state, droppedPlanId = dropped?.planId),
    )
}

// ─── Avisos de rechazo (Paquete A · C3/C4 y Paquete C · C.P11) ─────────────────────────────────────────────────
//
// Un SOLO camino de texto para todo rechazo que la persona ve: la lista sin planes viables, el aviso de la selección
// caída y el aviso del plan propio encima de la lista. El texto y los botones salen de `PlanRejectionPresenter` (el
// presentador único de dominio); aquí solo se hace lo que el presentador no sabe: elegir el rechazo, ofrecer la
// reparación de un toque del plan propio con la etiqueta de D5 y traducir cada botón a la API del asistente. El
// texto crudo del motor (`SetupCandidateRejection.reason`) no se pinta nunca: solo va al registro.

/** Qué hace un botón de un aviso de rechazo con la API del asistente. */
internal sealed interface NoticeEffect {
    /** Aplica reparaciones de un toque (`applyRepairs`): justo lo que el asesor probó y deja el plan propio listo. */
    data class Apply(val repairs: List<PlanRepair>) : NoticeEffect

    /** Una acción del presentador: ir a un paso, reintentar o ver las alternativas. */
    data class Act(val action: RejectionAction) : NoticeEffect

    /** Elegir el programa [planId] (el «a medida» que sustituye a un plan de la biblioteca): `selectPlan`. */
    data class Choose(val planId: String) : NoticeEffect
}

/** Un botón del aviso: su texto y su efecto. */
internal data class NoticeButton(val label: String, val effect: NoticeEffect)

/**
 * Un aviso listo para pintar: un texto llano y hasta dos botones, el principal primero. Con [informational] no es un
 * fallo (no va en el rojo de aviso): dice qué pasó y deja la salida a un toque.
 */
internal data class RejectionNotice(
    val text: String,
    val primary: NoticeButton?,
    val secondary: NoticeButton? = null,
    val informational: Boolean = false,
) {
    val buttons: List<NoticeButton> get() = listOfNotNull(primary, secondary)
}

/** Arranque común del aviso de la selección caída. */
internal const val DROPPED_SELECTION_LEAD = "Tu plan elegido ya no encaja con tus respuestas."

/**
 * El aviso de un plan propio de la biblioteca que un perfil general ya no ofrece (COPY · Revelado del programa): no es una
 * alarma, es lo que pasó. El asistente arma ese programa a medida y lo deja a un toque.
 */
internal const val LIBRARY_PLAN_NOW_TAILORED = "Este programa de la biblioteca ahora se arma a medida en el asistente."

/** El botón de ese aviso: elige el programa «a medida». */
internal const val CHOOSE_TAILORED_LABEL = "Elegir el programa a medida"

/**
 * Frase que se añade al aviso de «Cambiar a Fuerza y músculo» (recomendación 2): con mancuernas, los ejercicios
 * principales de ese plan serán versiones con mancuernas, no con barra. El asesor solo propone ese destino cuando la
 * categoría de mancuernas está marcada.
 */
internal const val STRENGTH_MUSCLE_DUMBBELLS_HINT =
    " Con tus mancuernas, los ejercicios principales serán versiones con mancuernas."

/** Como mucho dos botones por aviso (el principal y uno secundario). */
private const val MAX_NOTICE_BUTTONS = 2

/** Minúscula inicial para insertar una etiqueta en una frase; deja intactas las siglas («EZ»). */
private fun String.lowercaseFirst(): String =
    if (isEmpty() || (length > 1 && this[1].isUpperCase())) this else replaceFirstChar { it.lowercaseChar() }

private fun joinSpanish(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.first()
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

/** Nombre corto de una llave para el botón de un toque: el MISMO que usa el texto del presentador (H14). */
private fun shortApparatusLabel(key: String): String = PlanRejectionPresenter.shortLabelOf(key) ?: "material"

/**
 * La proyección mínima de un rechazo del asistente para el presentador. NO lleva `reason` (el texto crudo del motor),
 * así que ni ids ni tokens pueden llegar a la pantalla.
 */
internal fun SetupCandidateRejection.toRejectionView(): RejectionView = RejectionView(
    planId = planId,
    reasonCode = reasonCode,
    requiredMinutes = requiredMinutes,
    apparatusKey = apparatusKey,
    needsApparatusConfirmation = needsApparatusConfirmation,
    missingRequirements = missingRequirements,
    stage = when (stage) {
        SetupCandidateRejectionStage.CATALOG -> PlanEvaluationStage.CATALOG
        SetupCandidateRejectionStage.PROFILE -> PlanEvaluationStage.PROFILE
        SetupCandidateRejectionStage.FREQUENCY -> PlanEvaluationStage.FREQUENCY_SPLIT
        SetupCandidateRejectionStage.MATERIAL -> PlanEvaluationStage.MATERIAL
        SetupCandidateRejectionStage.MATERIALIZATION -> PlanEvaluationStage.MATERIALIZATION
        SetupCandidateRejectionStage.DURATION -> PlanEvaluationStage.SESSION_DURATION
        SetupCandidateRejectionStage.COMPOSITION -> PlanEvaluationStage.COMPOSITION
    },
)

/**
 * La disciplina de un plan con las palabras del asistente (Fuerza, Fuerza y músculo, Músculo), nunca «powerlifting»:
 * los nombres deportivos internos no se ofrecen como etiquetas de la interfaz.
 */
internal fun disciplineLabelOf(planId: String?): String? {
    val references = planId?.let(PersonalizedPlanCatalog::find)?.references ?: return null
    return when {
        TrainingReference.POWERLIFTING in references -> SetupGoal.STRENGTH.label
        TrainingReference.POWERBUILDING in references -> SetupGoal.STRENGTH_MUSCLE.label
        TrainingReference.HYPERTROPHY in references -> SetupGoal.MUSCLE.label
        else -> null
    }
}

/**
 * Lo que el presentador necesita saber de las respuestas de la persona para escribir el texto.
 *
 *  - H14: [PresentationContext.apparatusKeyOf] resuelve cada requisito de material con la llave que sigue SIN
 *    responder ([PlanRepairAdvisor.confirmableKeyFor]), la misma que confirma el botón de un toque; si todas están
 *    respondidas (un rechazo por material ausente) conserva la primera del panel, como antes.
 *  - H12: [PresentationContext.ownPlanId] y [PresentationContext.goalProfile] dejan que el presentador diga, con su
 *    único texto, el requisito de resistencia del plan propio de Fuerza y músculo.
 */
internal fun presentationContextOf(draft: SetupWizardDraft): PresentationContext {
    val availability = draft.trainingOptions.availability
    val goalProfile = planGoalProfileOf(draft.goal)
    return PresentationContext(
        userMinutes = draft.minutesPerSession,
        goalLabel = draft.goal?.label,
        daysChosen = draft.daysPerWeek,
        disciplineLabelOf = ::disciplineLabelOf,
        planDaysOf = { planId -> planId?.let(PersonalizedPlanCatalog::find)?.supportedFrequencies },
        apparatusKeyOf = { token ->
            PlanRepairAdvisor.confirmableKeyFor(token, availability) ?: SetupApparatusPanel.keyForToken(token)
        },
        ownPlanId = ownPlanIdOf(goalProfile),
        goalProfile = goalProfile,
    )
}

/** La presentación del presentador único, con el contexto de las respuestas de la persona (sin excepciones locales). */
private fun presentationFor(rejection: SetupCandidateRejection, draft: SetupWizardDraft): RejectionPresentation =
    PlanRejectionPresenter.present(rejection.toRejectionView(), presentationContextOf(draft))

/**
 * Etiqueta del botón de las reparaciones (D5): «Sí, tengo rack y banco», «Cambiar a Músculo», «Ajustar a N min»,
 * «Cardio de N min», «Quitar el reparto». Con una reparación encadenada (confirmar material y, con él, más minutos)
 * o un cambio de objetivo que además pide minutos, la etiqueta lo dice: «Sí, tengo rack y banco · ajustar a 75 min».
 */
internal fun repairLabelOf(repairs: List<PlanRepair>): String? {
    val first = repairs.firstOrNull() ?: return null
    val chainedMinutes = repairs.drop(1).filterIsInstance<PlanRepair.SetMinutes>().firstOrNull()?.minutes
    return when (first) {
        is PlanRepair.ConfirmApparatus -> {
            val items = first.keys.map(::shortApparatusLabel).distinct()
            val have = if (items.isEmpty()) "Sí, tengo el material" else "Sí, tengo ${joinSpanish(items)}"
            if (chainedMinutes != null) "$have · ajustar a $chainedMinutes min" else have
        }
        is PlanRepair.SwitchGoal -> {
            val label = "Cambiar a ${first.goal.toSetupGoal()?.label ?: "otro objetivo"}"
            first.alsoMinutes?.let { minutes -> "$label · ajustar a $minutes min" } ?: label
        }
        is PlanRepair.SetMinutes -> "Ajustar a ${first.minutes} min"
        is PlanRepair.SetCardioMinutes -> "Cardio de ${first.minutes} min"
        PlanRepair.ClearSplit -> "Quitar el reparto"
    }
}

/**
 * El aviso de UN rechazo: el texto del presentador y hasta dos botones. Si el rechazo trae reparaciones de un toque
 * (solo el del plan propio), la reparación es el botón principal y el primero del presentador (la acción de navegación
 * equivalente: «Confirmar material», «Cambiar objetivo»…) pasa a secundario; «Ajustar a N min» no se duplica.
 *
 * [keepSeeAlternatives] decide si «Ver alternativas» sobrevive: solo tiene sentido donde las alternativas están a la
 * vista (el aviso de la selección caída, encima de la lista). Sin lista (ningún plan viable) no hay nada que ver y, si
 * el botón era el único, queda «Reintentar».
 */
internal fun rejectionNotice(
    rejection: SetupCandidateRejection,
    draft: SetupWizardDraft,
    keepSeeAlternatives: Boolean,
): RejectionNotice {
    val presentation = presentationFor(rejection, draft)
    val repairs = rejection.repairs
    val repairButton = repairLabelOf(repairs)?.let { label -> NoticeButton(label, NoticeEffect.Apply(repairs)) }
    val fromPresenter = presentation.actions
        .filter { action -> keepSeeAlternatives || action != RejectionAction.SeeAlternatives }
        .filterNot { action -> action is RejectionAction.SetMinutes && repairs.any { it is PlanRepair.SetMinutes } }
        .map { action -> NoticeButton(action.label, NoticeEffect.Act(action)) }
    val buttons = (listOfNotNull(repairButton) + fromPresenter)
        .take(MAX_NOTICE_BUTTONS)
        .ifEmpty { listOf(NoticeButton(RejectionAction.Retry.label, NoticeEffect.Act(RejectionAction.Retry))) }
    // Cuando solo cabe bajando el cardio, el botón lo nombra y el texto dice por qué es la salida.
    val cardioHint = repairs.filterIsInstance<PlanRepair.SetCardioMinutes>().firstOrNull()
        ?.let { repair -> " Con ${repair.minutes} min de cardio sí cabe." }
        .orEmpty()
    // «Cambiar a Fuerza y músculo» solo se propone con mancuernas: el texto avisa de que los principales las usarán.
    val dumbbellHint = if (repairs.any { it is PlanRepair.SwitchGoal && it.goal == PlanGoalProfile.STRENGTH_MUSCLE }) {
        STRENGTH_MUSCLE_DUMBBELLS_HINT
    } else {
        ""
    }
    return RejectionNotice(
        text = presentation.text + cardioHint + dumbbellHint,
        primary = buttons.first(),
        secondary = buttons.getOrNull(1),
    )
}

/**
 * Aviso de la selección caída: [DROPPED_SELECTION_LEAD] y el motivo del presentador, con sus botones (y «Ver
 * alternativas», que solo cierra el aviso). Sin rechazo —el plan ni se evaluó, el planificador ya lo descartó por
 * objetivo, nivel o días— explica eso y ofrece «Ver alternativas» y «Cambiar objetivo». El `reason` crudo no se pinta.
 */
internal fun droppedSelectionNotice(dropped: SetupDroppedSelection, draft: SetupWizardDraft): RejectionNotice {
    // Un plan propio que un perfil general ya no ofrece: el «a medida» lo sustituye. Sin alarma y a un toque.
    dropped.tailoredId?.let { tailoredId ->
        return RejectionNotice(
            text = LIBRARY_PLAN_NOW_TAILORED,
            primary = NoticeButton(CHOOSE_TAILORED_LABEL, NoticeEffect.Choose(tailoredId)),
            informational = true,
        )
    }
    val rejection = dropped.rejection
        ?: return RejectionNotice(
            text = "$DROPPED_SELECTION_LEAD Ya no está entre los planes que corresponden a tus respuestas.",
            primary = NoticeButton(RejectionAction.SeeAlternatives.label, NoticeEffect.Act(RejectionAction.SeeAlternatives)),
            secondary = NoticeButton(RejectionAction.ChangeGoal.label, NoticeEffect.Act(RejectionAction.ChangeGoal)),
        )
    val notice = rejectionNotice(rejection, draft, keepSeeAlternatives = true)
    return notice.copy(text = "$DROPPED_SELECTION_LEAD ${notice.text}")
}

/**
 * Aviso del plan propio del objetivo encima de la lista (C4): solo cuando su rechazo trae reparaciones, no está entre
 * los viables y no es ya la selección caída (que tiene su propio aviso, con las mismas reparaciones). Es lo que ve la
 * persona que pidió Fuerza con el gimnasio sin confirmar y tiene otros planes debajo: el plan que esperaba, por qué no
 * está y el botón que lo arregla.
 */
internal fun ownPlanNotice(state: SetupWizardState, droppedPlanId: String?): RejectionNotice? {
    val draft = state.draft
    val goalLabel = draft.goal?.label ?: return null
    val ownId = ownPlanIdOf(planGoalProfileOf(draft.goal)) ?: return null
    if (droppedPlanId == ownId) return null
    if (state.availablePlanCandidates.any { candidate -> candidate.id == ownId }) return null
    val rejection = state.candidateRejections.firstOrNull { it.planId == ownId && it.repairs.isNotEmpty() } ?: return null
    val notice = rejectionNotice(rejection, draft, keepSeeAlternatives = false)
    return notice.copy(text = "No pudimos armar tu plan de ${goalLabel.lowercaseFirst()} con tus respuestas. ${notice.text}")
}

/**
 * El aviso de la lista SIN planes viables: el rechazo más accionable (el del plan propio; luego material por confirmar
 * con llave; luego el de menos minutos; luego perfil o material ausente; si no, el primero) y su reparación. Sin
 * ningún rechazo por plan (el planificador no encontró nada) queda el resumen de la búsqueda y «Reintentar».
 */
internal fun incompatibilityNotice(state: SetupWizardState): RejectionNotice {
    val draft = state.draft
    val rejections = state.candidateRejections
    val views = rejections.map { it.toRejectionView() }
    val primary = PlanRejectionPresenter.primary(views, ownPlanIdOf(planGoalProfileOf(draft.goal)))
        ?.let { view -> views.indexOf(view) }
        ?.let { index -> rejections.getOrNull(index) }
        ?: return RejectionNotice(
            text = state.errors["candidates"] ?: "Ahora mismo no hay un plan compatible con tus respuestas.",
            primary = NoticeButton(RejectionAction.Retry.label, NoticeEffect.Act(RejectionAction.Retry)),
        )
    return rejectionNotice(primary, draft, keepSeeAlternatives = false)
}

/** Lo que hace un botón con la API del asistente. [onSeeAlternatives] cierra el aviso donde las alternativas están a la vista. */
internal fun performNoticeEffect(effect: NoticeEffect, vm: SetupWizardViewModel, onSeeAlternatives: () -> Unit = {}) {
    when (effect) {
        is NoticeEffect.Apply -> vm.applyRepairs(effect.repairs)
        is NoticeEffect.Act -> performRejectionAction(effect.action, vm, onSeeAlternatives)
        is NoticeEffect.Choose -> vm.selectPlan(effect.planId)
    }
}

/** Mapa de las acciones del presentador a la API del asistente (A.C3): navegar con `editStep`, reintentar o ajustar minutos. */
internal fun performRejectionAction(action: RejectionAction, vm: SetupWizardViewModel, onSeeAlternatives: () -> Unit = {}) {
    when (action) {
        RejectionAction.Retry -> vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
        RejectionAction.ChangeGoal -> vm.editStep(SetupStepId.GOAL)
        RejectionAction.SeeAlternatives -> onSeeAlternatives()
        RejectionAction.ChangeDays -> vm.editStep(SetupStepId.WEEKDAYS)
        // El reparto ya no es un paso: se cambia en la semana armada.
        RejectionAction.ChangeSplit -> vm.editStep(SetupStepId.WEEK_LAYOUT)
        RejectionAction.ConfirmApparatus -> vm.editStep(SetupStepId.AVAILABILITY)
        is RejectionAction.SetMinutes -> vm.applyRepair(PlanRepair.SetMinutes(action.minutes))
    }
}

/** Pinta un aviso de rechazo con sus dos botones; cada botón ejecuta su efecto con la API del asistente. */
@Composable
private fun RejectionNoticeView(
    notice: RejectionNotice,
    vm: SetupWizardViewModel,
    onSeeAlternatives: () -> Unit = {},
) {
    TrainingNotice(
        text = notice.text,
        tone = TrainingNoticeTone.ERROR,
        actionLabel = notice.primary?.label,
        onAction = notice.primary?.let { button -> { performNoticeEffect(button.effect, vm, onSeeAlternatives) } },
        secondaryActionLabel = notice.secondary?.label,
        onSecondaryAction = notice.secondary?.let { button -> { performNoticeEffect(button.effect, vm, onSeeAlternatives) } },
    )
}

/**
 * Candidatos reales con sus metadatos; se eligen antes de las marcas (el grafo
 * coloca PLAN antes de TRAINING_MAX/TRAINING_MARKS). Carga, error y vacío son
 * estados explícitos con reintento, no carrusel infinito.
 *
 * Paquete A · D2 (B-01): la puerta de la lista es [candidateListGate]. Solo el fallo de la BÚSQUEDA
 * esconde la lista; el error del preview del plan elegido y la selección caída salen como avisos encima de
 * las tarjetas, que siguen visibles para poder elegir otro plan.
 */
@Composable
internal fun TrainingPlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.PLAN
    val draft = state.draft
    // C.P5: el plan cuya hoja «Cómo funciona» está abierta (su id). Sobrevive a girar la pantalla.
    var infoPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
        FromScratchSessions(state = state)
        return
    }

    when (val gate = candidateListGate(state)) {
        CandidateListGate.Loading -> TrainingLoading("Buscando planes compatibles con tus respuestas…")
        is CandidateListGate.SearchFailed -> TrainingNotice(
            text = gate.message,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.CANDIDATES) },
        )

        CandidateListGate.NoneViable -> CandidateIncompatibility(state = state, vm = vm)

        is CandidateListGate.Candidates -> {
            gate.dropped?.let { dropped -> DroppedSelectionBanner(dropped = dropped, state = state, vm = vm) }
            // C4: el plan propio del objetivo rechazado con reparación, aunque debajo haya otros planes viables.
            gate.ownPlanNotice?.let { notice -> RejectionNoticeView(notice = notice, vm = vm) }
            gate.previewError?.let { previewError ->
                TrainingNotice(
                    text = previewError,
                    tone = TrainingNoticeTone.ERROR,
                    actionLabel = "Reintentar",
                    onAction = { vm.retryFailedOperation(SetupRetryOperation.PREVIEW) },
                )
            }
            val selected = draft.selectedValues(step)
            gate.cards.forEach { candidate ->
                WizardChoiceCard(
                    title = candidate.title,
                    subtitle = planCandidateSubtitle(candidate),
                    selected = draft.programRoute != SetupProgramRoute.LATER && candidate.id in selected,
                    onClick = { vm.selectPlan(candidate.id) },
                    footer = { PlanInfoLink(candidate = candidate, onClick = { infoPlanId = candidate.id }) },
                )
            }
            val hidden = state.availablePlanCandidates.size - state.planCandidates.size
            if (hidden > 0) {
                // §15.2: el límite de tarjetas es visual; paginar NO rematerializa
                // (los Ready viven en `availablePlanCandidates` y en la caché).
                TextButton(onClick = { vm.showMoreCandidates() }) {
                    Text("Ver más opciones ($hidden)", color = WizardColors.text, style = WizardTypography.cardTitle)
                }
            }
            CandidateCountsLine(state = state)
        }
    }

    DeferProgramChoice(selected = draft.programRoute == SetupProgramRoute.LATER, onClick = vm::deferProgramUntilLater)

    infoPlanId?.let { planId ->
        CandidatePlanInfo(planId = planId, vm = vm, onDismiss = { infoPlanId = null })
    }
}

/**
 * Enlace «Ver cómo funciona» al pie de la tarjeta de un plan (C.P5). Es un botón aparte de la tarjeta, que solo
 * elige: abre la hoja del plan sin cambiar la selección. Cada enlace dice de qué plan es para TalkBack.
 */
@Composable
private fun PlanInfoLink(candidate: SetupPlanCandidate, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .testTag("plan-info-${candidate.id}")
            .semantics { contentDescription = "$PLAN_INFO_LINK ${candidate.title}" },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    ) {
        Text(text = PLAN_INFO_LINK, color = WizardColors.text, style = WizardTypography.cardSubtitle)
    }
}

private const val PLAN_INFO_LINK = "Ver cómo funciona"

/** Texto exacto de la tarjeta que aplaza el programa en el paso PLAN. */
internal const val DEFER_PROGRAM_CHOICE = "Yo haré mi programa de entreno manualmente más adelante"

/** Lo que dicen el hito y la revisión cuando no se crea un programa ahora. */
internal const val DEFER_PROGRAM_REVIEW_VALUE = "Lo armarás manualmente más adelante"

/**
 * Escape del catálogo: la persona no elige un plan y armará el suyo después.
 * Visible también mientras la lista carga, falla o no tiene planes compatibles.
 */
@Composable
private fun DeferProgramChoice(selected: Boolean, onClick: () -> Unit) {
    WizardChoiceCard(
        title = DEFER_PROGRAM_CHOICE,
        subtitle = "No creamos un programa ahora. Podrás armarlo desde cero cuando quieras.",
        selected = selected,
        onClick = onClick,
    )
}

/**
 * La hoja «Cómo funciona» de un candidato del asistente (C.P5): la entrada del catálogo con la semana real que
 * ya calculó la evaluación (solo la pintan los planes sin receta) y «Elegir este plan» como botón principal, que
 * elige el plan y cierra la hoja. El glosario enlaza a Conceptos clave (C.P6) cuando la pantalla recibe el
 * callback de navegación ([LocalOpenConcept]); al volver, el asistente sigue donde estaba.
 */
@Composable
private fun CandidatePlanInfo(planId: String, vm: SetupWizardViewModel, onDismiss: () -> Unit) {
    val entry = remember(planId) { PersonalizedPlanCatalog.find(planId) } ?: return
    val readyWeek = remember(planId) { vm.readyWeekSnapshotFor(planId) }
    PlanInfoSheet(
        entry = entry,
        mode = PlanInfoMode.WIZARD,
        readyWeek = readyWeek,
        onDismiss = onDismiss,
        onPrimaryAction = {
            vm.selectPlan(planId)
            onDismiss()
        },
        onOpenConcept = LocalOpenConcept.current,
    )
}

/**
 * Aviso encima de la lista: el plan que la persona tenía elegido ya no encaja con sus respuestas. La acción
 * lleva al paso que lo arregla; «Ver alternativas» solo cierra el aviso (el estado del ViewModel se
 * conserva: sigue sin lanzar el preview de ese plan). Elegir otro plan lo retira del estado.
 */
@Composable
private fun DroppedSelectionBanner(
    dropped: SetupDroppedSelection,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    var closed by rememberSaveable(dropped.planId) { mutableStateOf(false) }
    if (closed) return
    val notice = droppedSelectionNotice(dropped, state.draft)
    RejectionNoticeView(notice = notice, vm = vm, onSeeAlternatives = { closed = true })
}

/** §15.2: revisados / encajan (nunca «publicados» de un subconjunto). */
@Composable
private fun CandidateCountsLine(state: SetupWizardState) {
    val counts = state.candidateCounts
    if (counts.evaluated == 0) return
    Text(
        text = candidateCountsText(counts, adaptedToBodyweight = state.planAdaptedToBodyweight),
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
        modifier = Modifier.testTag("setup-candidate-counts"),
    )
}

/** Cierre del conteo cuando las tarjetas salen del pase a peso corporal (los conteos son los del pase pedido). */
internal const val ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX = "te mostramos planes de peso corporal"

/**
 * «12 planes revisados · 3 encajan con tus respuestas» (C.P5): cada cifra concuerda con su sustantivo y con su
 * verbo. Con uno solo («1 plan revisado · 1 encaja con tus respuestas», lo normal en Atleta completo) y con
 * ninguno («… · ninguno encaja con tus respuestas») el texto cambia de forma, no solo de número. Los que no
 * encajan no se cuentan aparte: son la resta.
 *
 * Con [adaptedToBodyweight] las tarjetas que se ven NO son las que se contaron (el conteo es del pase pedido y las
 * tarjetas, del segundo pase a peso corporal): el texto lo dice para que «ninguno encaja» no parezca un error al
 * lado de unas tarjetas («… · ninguno encaja con tus respuestas; te mostramos planes de peso corporal»).
 */
internal fun candidateCountsText(counts: SetupCandidateCounts, adaptedToBodyweight: Boolean = false): String {
    val reviewed = SpanishPlurals.withNoun(counts.evaluated, "plan revisado", "planes revisados")
    val fitting = when (counts.viable) {
        0 -> "ninguno encaja con tus respuestas"
        else -> SpanishPlurals.choose(
            counts.viable,
            "1 encaja con tus respuestas",
            "${counts.viable} encajan con tus respuestas",
        )
    }
    val line = "$reviewed · $fitting"
    return if (adaptedToBodyweight) "$line; $ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX" else line
}

/**
 * T-005 / §15.2: incompatibilidad con ACCIONES concretas, antes de revisión y sin tocar las respuestas de la persona.
 * El texto y los botones los decide [incompatibilityNotice], que se apoya en el presentador único de rechazos (C.P11):
 * error de catálogo → «Reintentar»; material por confirmar → «Sí, tengo rack y banco» cuando hay reparación o
 * «Confirmar material»; tiempo insuficiente → «Ajustar a N min»; objetivo que no cabe → «Cambiar a Músculo»…
 * El texto crudo del motor ya no se pinta: solo va al registro.
 */
@Composable
private fun CandidateIncompatibility(state: SetupWizardState, vm: SetupWizardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        RejectionNoticeView(notice = incompatibilityNotice(state), vm = vm)
        CandidateCountsLine(state = state)
    }
}

private fun planCandidateSubtitle(candidate: SetupPlanCandidate): String =
    (listOf(candidate.subtitle) + candidate.reasons)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** Ruta legacy «desde cero»: resumen real de las sesiones ya montadas. */
@Composable
private fun FromScratchSessions(state: SetupWizardState) {
    val sessions = state.draft.sessions.sortedBy { it.weekday }
    if (sessions.isEmpty()) {
        TrainingNotice("Todavía no has montado tus sesiones.", TrainingNoticeTone.ERROR)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        sessions.forEach { session ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WizardColors.cardFill, WizardShapes.card)
                    .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${weekdayLabel(session.weekday) ?: "Día ${session.weekday}"} · ${session.title}",
                    style = WizardTypography.cardTitle,
                    color = WizardColors.text,
                )
                if (session.exercises.isEmpty()) {
                    Text("Sin ejercicios todavía.", style = WizardTypography.cardSubtitle, color = WizardColors.danger)
                }
                session.exercises.forEach { item ->
                    Text(
                        text = scratchExerciseLine(name = item.name, sets = item.sets, reps = item.reps),
                        style = WizardTypography.cardSubtitle,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

/** Línea de un ejercicio montado a mano: «Sentadilla · 1 serie × 1 rep» o «· 3 series × 8 reps». */
internal fun scratchExerciseLine(name: String, sets: Int?, reps: Int?): String =
    "$name · ${sets?.let(SpanishPlurals::sets) ?: "— series"} × ${reps?.let(SpanishPlurals::reps) ?: "— reps"}"

// ─── Vista previa del programa ──────────────────────────────────────────────

/** «1 serie × 1 rep» / «3 series × 8 rep» de la vista previa del programa. */
internal fun exerciseSummary(exercise: Exercise): String = buildString {
    append(SpanishPlurals.sets(exercise.sets.size))
    val first = exercise.sets.firstOrNull()
    val reps = first?.targetReps
    val duration = first?.targetDuration
    when {
        reps != null -> append(" × $reps rep")
        duration != null -> append(" × $duration s")
        else -> Unit
    }
}

// ─── MILESTONE_TRAINING ─────────────────────────────────────────────────────

/** Resumen real de lo respondido en el bloque antes de cerrarlo. */
@Composable
private fun TrainingMilestoneSummary(state: SetupWizardState) {
    val rows = trainingMilestoneRows(state)
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        rows.forEach { (label, value) -> TrainingSummaryRow(label = label, value = value) }
    }
}

/**
 * Las filas del resumen del hito del bloque Entreno (etiqueta, valor), sin pintar: lo que la persona eligió, con los
 * mismos nombres de la revisión final (lugares, material, objetivo, días y tiempo, programa y, si las declaró, las
 * marcas). Un dato sin declarar no añade fila; nunca se pinta un id ni un valor por defecto.
 */
internal fun trainingMilestoneRows(state: SetupWizardState): List<Pair<String, String>> {
    val draft = state.draft
    return buildList {
        draft.experience?.let { add("Experiencia" to it.label) }
        placesSummaryText(draft.trainingPlaces)?.let { add("Lugares" to it) }
        materialReviewValue(draft)?.let { add("Material" to it) }
        (draft.goalProfile?.label ?: draft.goal?.label)?.let { add("Objetivo" to it) }
        daysAndTimeReviewValue(draft)?.let { add("Días y tiempo" to it) }
        if (draft.programRoute == SetupProgramRoute.LATER) {
            add("Programa" to DEFER_PROGRAM_REVIEW_VALUE)
        } else {
            draft.selectedCatalogId?.let { id ->
                // C.P6: el nombre de la ficha editorial; si el id ya no resuelve, nunca se pinta el id crudo.
                add("Programa" to (PersonalizedPlanCatalog.find(id)?.displayName ?: "Tu programa elegido"))
            }
        }
        if (draft.liftMarks.isNotEmpty()) {
            val unit = if (draft.marksUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG
            add(
                "Marcas" to draft.liftMarks.entries.sortedBy { it.key.ordinal }
                    .joinToString(" / ") { (_, kg) -> WizardWeightScale.formatWithUnit(kg, unit) },
            )
        }
    }
}

/** Etiqueta del día de la semana (1 = lunes … 7 = domingo) desde las defs. */
private fun weekdayLabel(day: Int?): String? {
    if (day == null) return null
    return SetupStepDefinitions.options(SetupStepId.WEEKDAYS)
        .firstOrNull { it.value == day.toString() }
        ?.label
        ?: "Día $day"
}
