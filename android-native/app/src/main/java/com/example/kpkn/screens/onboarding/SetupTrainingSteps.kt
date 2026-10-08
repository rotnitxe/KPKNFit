package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.selected
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingReference
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
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardSpacing
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
        SetupStepId.VOLUME_TECHNIQUE,
        SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH,
        SetupStepId.VOLUME_MOBILITY,
        SetupStepId.CARDIO_TYPE,
        SetupStepId.CARDIO_TIME,
        -> TrainingChoiceStep(step = step, state = state, vm = vm)

        // La raíz corta los hitos antes de delegar (StepQuestion + hitos), así
        // que este resumen solo pinta si el hito llega aquí: nunca duplica.
        SetupStepId.MILESTONE_TRAINING -> TrainingMilestoneSummary(state = state)

        // Pasos de otros bloques (datos básicos, nutrición, rings y revisión
        // final) y pasos retirados de la ruta de Entreno: sin control propio aquí.
        else -> Unit
    }
}

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

// ─── Avisos de rechazo (Paquete A · C3/C4 y Paquete C · C.P11) ─────────────────────────────────────────────────
//
// Un SOLO camino de texto para todo rechazo que la persona ve: hoy, el aviso de la selección caída del paso PLAN
// (`entreno/EntrenoPlanNotices.kt`). El texto y los botones salen de `PlanRejectionPresenter` (el presentador único de
// dominio); aquí solo se hace lo que el presentador no sabe: ofrecer la reparación de un toque con la etiqueta de D5 y
// traducir cada botón a la API del asistente. El texto crudo del motor (`SetupCandidateRejection.reason`) no se pinta
// nunca: solo va al registro. La lista antigua de planes (sin planes viables, plan propio encima de la lista) salió con
// el paso antiguo: la ruta nueva siempre trae el programa «a medida» y un fallo del barrido se resuelve con «Reintentar».

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

/** Lo que dicen el hito y la revisión cuando no se crea un programa ahora. */
internal const val DEFER_PROGRAM_REVIEW_VALUE = "Lo armarás manualmente más adelante"

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
