package com.example.kpkn.debug

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatCopyCatalog
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.domain.onboarding.WizChatGraphContext
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.screens.onboarding.SetupModuleChoice
import com.example.kpkn.screens.onboarding.SetupProgramRoute
import com.example.kpkn.screens.onboarding.SetupTrainingPath
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.capabilitySkills
import com.example.kpkn.screens.onboarding.confirmCurrentStep
import com.example.kpkn.screens.onboarding.marksLifts
import com.example.kpkn.screens.onboarding.selectedEquipmentSymbols
import com.example.kpkn.screens.onboarding.stepContext
import com.example.kpkn.screens.onboarding.touchStep
import com.example.kpkn.screens.onboarding.withCapability
import com.example.kpkn.screens.onboarding.withDayPlace
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withMarksUnit
import com.example.kpkn.screens.onboarding.withMaterial
import com.example.kpkn.screens.onboarding.withMuscles
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withStepChoice
import com.example.kpkn.screens.onboarding.withStepNumber
import com.example.kpkn.screens.onboarding.withStepText
import com.example.kpkn.screens.onboarding.withWeekStart
import com.example.kpkn.screens.onboarding.withWeekdays
import java.util.UUID

/*
 * SOLO DEPURACIÓN (no se integra): los borradores de prueba del arnés `WizardHarnessActivity`.
 *
 * Un borrador se construye CAMINANDO la ruta real del asistente: cada paso anterior al que se pide se responde con los datos
 * de una persona de prueba, se marca como declarado y se confirma con `confirmCurrentStep`, la misma función que usa el
 * ViewModel. Así el cursor, las respuestas y la procedencia son los de una persona que de verdad llegó hasta ahí, y las filas
 * plegadas llevan sus resúmenes reales.
 */

/** Una persona de prueba: lo que contestó en los pasos que el arnés da por confirmados. */
internal enum class HarnessPersona(
    val places: Set<TrainingPlace>,
    val addMaterial: Set<EquipmentSymbolId>,
    val dropMaterial: Set<EquipmentSymbolId>,
    val goal: TrainingGoalProfile,
    val freshDay: Int,
    val weekdays: Set<Int>,
    val dayPlaces: Map<Int, TrainingPlace>,
    val minutes: Int,
    val experience: String,
) {
    /** Gimnasio: lo habitual ya viene marcado; Powerlifting; martes, jueves y sábado, con el jueves como día fuerte y de arranque. */
    GYM(
        places = setOf(TrainingPlace.GYM), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.POWERLIFTING, freshDay = 4, weekdays = setOf(2, 4, 6), dayPlaces = emptyMap(),
        minutes = 75, experience = "intermediate",
    ),

    /** Casa con mancuernas y bandas: la barra, el rack y la barra de dominadas no están, así que varias disciplinas se bloquean. */
    HOME(
        places = setOf(TrainingPlace.HOME), addMaterial = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS),
        dropMaterial = emptySet(), goal = TrainingGoalProfile.BODYBUILDING, freshDay = 2, weekdays = setOf(2, 4, 6),
        dayPlaces = emptyMap(), minutes = 45, experience = "returning",
    ),

    /** Parque: barra de dominadas, paralelas y barra baja de serie; Calistenia; novato (pregunta capacidades, no marcas). */
    PARK(
        places = setOf(TrainingPlace.PUBLIC), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.CALISTHENICS, freshDay = 6, weekdays = setOf(2, 4, 6, 7), dayPlaces = emptyMap(),
        minutes = 60, experience = "new",
    ),

    /** Gimnasio y casa: con dos lugares aparece el lugar de cada día. */
    MULTI(
        places = setOf(TrainingPlace.GYM, TrainingPlace.HOME), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.STRENGTH_MUSCLE, freshDay = 1, weekdays = setOf(1, 3, 5, 6),
        dayPlaces = mapOf(3 to TrainingPlace.HOME, 6 to TrainingPlace.HOME), minutes = 90, experience = "intermediate",
    ),

    /** Los tres lugares y los siete días: el caso más ancho del calendario. */
    ALL(
        places = TrainingPlace.entries.toSet(), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.FUNCTIONAL_HEALTH, freshDay = 3, weekdays = (1..7).toSet(), dayPlaces = emptyMap(),
        minutes = 120, experience = "advanced",
    ),
    ;

    companion object {
        fun of(name: String?): HarnessPersona =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GYM
    }
}

/**
 * El borrador del arnés: parte de uno nuevo de solo entreno (como `newDraft` del ViewModel), responde y confirma todos los
 * pasos de la ruta ANTES de [start] con la persona [persona] y deja el cursor en [start]. [answers] (ver [withAnswers]) escribe
 * además datos del paso activo sin confirmarlo, para abrirlo ya con elecciones hechas.
 *
 * Los hitos entre bloques (el de datos básicos) se confirman por el camino, como al pulsar «Continuar» en su overlay. Si [start]
 * no está en la ruta de esa persona, el cursor se queda en el hito del bloque de Entreno (el overlay de «bloque completado»),
 * sin confirmarlo, y nunca llega a la revisión ni a «Activar».
 */
internal fun buildHarnessDraft(
    draftId: String,
    persona: HarnessPersona,
    start: SetupStepId,
    answers: String,
    revision: Int,
): SetupWizardDraft {
    val scope = "training_only"
    val first = WizChatGraph.firstFor(WizChatGraphContext(includeTraining = true, includeNutrition = false))
    val base = SetupWizardDraft(
        draftId = draftId,
        commitId = UUID.randomUUID().toString(),
        draftScope = scope,
        revision = revision,
        moduleChoice = SetupModuleChoice.TRAINING,
        includeTraining = true,
        includeNutrition = false,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        catalogRevision = PersonalizedPlanCatalog.REVISION,
        wizChat = WizChatProgress(
            scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scope,
            currentQuestionId = first,
            stage = WizChatGraph.stageFor(first),
        ),
    )
    var draft = base.copy(stepProgress = SetupStepProgress.initial(base.stepContext()))
    var guard = 0
    while (guard++ < MAX_STEPS) {
        val step = draft.stepProgress.currentStepId
        if (step == start || step == SetupStepId.MILESTONE_TRAINING || step == SetupStepId.REVIEW_ACTIVATE) break
        draft = draft.answeredAs(step, persona).touchStep(step).confirmCurrentStep(step)
    }
    return draft.withAnswers(answers)
}

private const val MAX_STEPS = 80

/** Los datos que contesta [persona] en [step] (los pasos sin datos propios, como el plan, no cambian nada). */
internal fun SetupWizardDraft.answeredAs(step: SetupStepId, persona: HarnessPersona): SetupWizardDraft = when (step) {
    SetupStepId.NAME -> withStepText(step, "Valentina").withStepNumber(SetupStepId.AGE, 29.0)
    SetupStepId.AGE -> withStepNumber(step, 29.0)
    SetupStepId.HEIGHT -> withStepNumber(step, 168.0)
    SetupStepId.WEIGHT -> withStepNumber(step, 64.0)
    SetupStepId.EQUATION_SEX -> withStepChoice(step, "female")
    SetupStepId.BODY_FAT -> withStepChoice(step, "VISUAL_ESTIMATE").withStepNumber(step, 24.0)
    SetupStepId.EXPERIENCE -> withStepChoice(step, persona.experience)
    SetupStepId.EQUIPMENT -> withPlaces(persona.places)
    // Una casa sin material se lee como «solo peso corporal» (exclusivo): se suelta antes de sumar implementos.
    SetupStepId.AVAILABILITY -> withMaterial(
        (selectedEquipmentSymbols() - EquipmentSymbolId.BODYWEIGHT_ONLY + persona.addMaterial) - persona.dropMaterial,
    )
    SetupStepId.GOAL -> withGoalProfile(persona.goal)
    SetupStepId.FRESH_DAY -> withFreshestDay(persona.freshDay)
    SetupStepId.WEEKDAYS -> persona.dayPlaces.entries.fold(withWeekdays(persona.weekdays)) { draft, (day, place) ->
        draft.withDayPlace(day, place)
    }
    SetupStepId.SESSION_TIME -> withSessionMinutes(persona.minutes)
    SetupStepId.CARDIO_TYPE,
    SetupStepId.CARDIO_TIME,
    SetupStepId.VOLUME_TECHNIQUE,
    SetupStepId.VOLUME_CONSISTENCY,
    SetupStepId.VOLUME_STRENGTH,
    SetupStepId.VOLUME_MOBILITY,
    -> middleOption(step)
    SetupStepId.CAPABILITIES -> capabilitySkills().fold(this) { draft, skill -> draft.withCapability(skill, CapabilityLevel.SOME) }
    SetupStepId.PRIORITIES -> withMuscles(setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK))
    SetupStepId.TRAINING_MAX -> marksLifts().fold(this) { draft, lift -> draft.withLiftMark(lift, markFor(lift)) }
    else -> this
}

/** La opción de en medio de un paso de elección (una respuesta cualquiera pero válida). */
private fun SetupWizardDraft.middleOption(step: SetupStepId): SetupWizardDraft {
    val options = SetupStepDefinitions.options(step)
    val value = options.getOrNull(options.size / 2)?.value ?: return this
    return withStepChoice(step, value)
}

private fun markFor(lift: LiftMark): Double = when (lift) {
    LiftMark.SQUAT -> 120.0
    LiftMark.BENCH -> 80.0
    LiftMark.DEADLIFT -> 150.0
    LiftMark.OVERHEAD_PRESS -> 50.0
    LiftMark.SNATCH -> 60.0
    LiftMark.CLEAN_AND_JERK -> 80.0
}

/**
 * Escribe datos del paso activo (y los marca como declarados) SIN confirmarlo: `clave=valor/clave=valor` (la barra separa los
 * datos porque `;` lo interpretaría el intérprete de órdenes del teléfono al pasar por `adb shell`).
 *  - `places=GYM,HOME`; `material=BARBELL,RACK` (lista exacta) o `material=+RINGS,-BARBELL` (sobre lo que ya hay);
 *  - `goal=POWERLIFTING`; `fresh=4`; `days=1,3,5`; `startday=4`; `dayplaces=3:HOME,6:PUBLIC`; `minutes=75`;
 *  - `caps=PULL_UP:SOME,PUSH_UP:MANY`; `muscles=CHEST,BACK`; `marks=SQUAT:140,BENCH:100`; `unit=lb`.
 */
private fun SetupWizardDraft.withAnswers(spec: String): SetupWizardDraft =
    spec.split('/').filter { it.isNotBlank() }.fold(this) { draft, item ->
        val key = item.substringBefore('=').trim()
        val value = item.substringAfter('=', "").trim()
        val list = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        when (key) {
            "places" -> draft.withPlaces(list.mapNotNull(::placeNamed).toSet()).touchStep(SetupStepId.EQUIPMENT)
            "material" -> draft.withMaterial(materialFrom(draft.selectedEquipmentSymbols(), list)).touchStep(SetupStepId.AVAILABILITY)
            "goal" -> TrainingGoalProfile.entries.firstOrNull { it.name == value }
                ?.let { draft.withGoalProfile(it).touchStep(SetupStepId.GOAL) } ?: draft
            "fresh" -> draft.withFreshestDay(value.toIntOrNull()).touchStep(SetupStepId.FRESH_DAY)
            "days" -> draft.withWeekdays(list.mapNotNull { it.toIntOrNull() }.toSet()).touchStep(SetupStepId.WEEKDAYS)
            "startday" -> draft.withWeekStart(value.toIntOrNull()).touchStep(SetupStepId.WEEKDAYS)
            "dayplaces" -> list.fold(draft) { d, pair ->
                val day = pair.substringBefore(':').toIntOrNull()
                val place = placeNamed(pair.substringAfter(':', ""))
                if (day != null && place != null) d.withDayPlace(day, place) else d
            }.touchStep(SetupStepId.WEEKDAYS)
            "minutes" -> draft.withSessionMinutes(value.toIntOrNull()).touchStep(SetupStepId.SESSION_TIME)
            "caps" -> list.fold(draft) { d, pair ->
                val skill = CapabilitySkill.entries.firstOrNull { it.name == pair.substringBefore(':') }
                val level = CapabilityLevel.entries.firstOrNull { it.name == pair.substringAfter(':', "") }
                if (skill != null && level != null) d.withCapability(skill, level) else d
            }.touchStep(SetupStepId.CAPABILITIES)
            "muscles" -> draft.withMuscles(list.mapNotNull { name -> MuscleSymbol.entries.firstOrNull { it.name == name } }.toSet())
                .touchStep(SetupStepId.PRIORITIES)
            "marks" -> list.fold(draft) { d, pair ->
                val lift = LiftMark.entries.firstOrNull { it.name == pair.substringBefore(':') }
                val kg = pair.substringAfter(':', "").toDoubleOrNull()
                if (lift != null && kg != null) d.withLiftMark(lift, kg) else d
            }.touchStep(SetupStepId.TRAINING_MAX)
            "unit" -> draft.withMarksUnit(value)
            else -> draft
        }
    }

private fun placeNamed(name: String): TrainingPlace? = when (name.trim().lowercase()) {
    "gym", "gimnasio" -> TrainingPlace.GYM
    "home", "casa" -> TrainingPlace.HOME
    "public", "park", "parque" -> TrainingPlace.PUBLIC
    else -> null
}

/** `+X`/`-X` parten de lo que ya hay; sin signo la lista es la selección entera. */
private fun materialFrom(current: Set<EquipmentSymbolId>, items: List<String>): Set<EquipmentSymbolId> {
    if (items.none { it.startsWith('+') || it.startsWith('-') }) {
        return items.mapNotNull { name -> EquipmentSymbolId.entries.firstOrNull { it.name == name } }.toSet()
    }
    var result = current
    for (item in items) {
        val symbol = EquipmentSymbolId.entries.firstOrNull { it.name == item.drop(1) } ?: continue
        result = if (item.startsWith('-')) result - symbol else result + symbol
    }
    return result
}
