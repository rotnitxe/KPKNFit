package com.example.kpkn.debug

import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioChoice
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.GeneratedPlans
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
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import com.example.kpkn.screens.onboarding.SetupModuleChoice
import com.example.kpkn.screens.onboarding.SetupProgramRoute
import com.example.kpkn.screens.onboarding.SetupTrainingPath
import com.example.kpkn.screens.onboarding.SetupWeighIn
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.capabilitySkills
import com.example.kpkn.screens.onboarding.confirmCurrentStep
import com.example.kpkn.screens.onboarding.marksLifts
import com.example.kpkn.screens.onboarding.selectedEquipmentSymbols
import com.example.kpkn.screens.onboarding.stepContext
import com.example.kpkn.screens.onboarding.touchStep
import com.example.kpkn.screens.onboarding.withCapability
import com.example.kpkn.screens.onboarding.withCompetition
import com.example.kpkn.screens.onboarding.withDayPlace
import com.example.kpkn.screens.onboarding.withFeelings
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withGroupSets
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withMainLiftToggled
import com.example.kpkn.screens.onboarding.withMarksUnit
import com.example.kpkn.screens.onboarding.withMaterial
import com.example.kpkn.screens.onboarding.withMuscles
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withStepChoice
import com.example.kpkn.screens.onboarding.withStepChoices
import com.example.kpkn.screens.onboarding.withStepNumber
import com.example.kpkn.screens.onboarding.withStepText
import com.example.kpkn.screens.onboarding.withTrainedDayToggled
import com.example.kpkn.screens.onboarding.withWeekStart
import com.example.kpkn.screens.onboarding.withWeekdays
import java.time.LocalDate
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
    /** Material EXACTO (en lugar de lo habitual de los lugares más `addMaterial` menos `dropMaterial`); vacío = solo peso corporal. */
    val exactMaterial: Set<EquipmentSymbolId>? = null,
    /** Los músculos que quiere mejorar (vacío = «Omitir»; null = acepta lo que el perfil sugiere). */
    val muscles: Set<MuscleSymbol>? = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK),
    /** Nivel de cada ejercicio por el que se pregunta; los que falten, «Algunas». */
    val capabilities: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
    /** Marcas (kg) de los levantamientos que se preguntan; las que falten usan [markFor]. */
    val marks: Map<LiftMark, Double> = emptyMap(),
    /** Unidad en que declara las marcas (`kg` o `lb`). */
    val marksUnit: String = "kg",
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

    // ── Los seis recorridos de la auditoría Q («declarado → efecto»): cada uno declara solo lo que dice su nombre. ──

    /** (a) Gimnasio, «Fuerza y masa muscular», 4 días × 60 min (lunes, martes, jueves y viernes; el jueves es el día fuerte). */
    QA(
        places = setOf(TrainingPlace.GYM), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.STRENGTH_MUSCLE, freshDay = 4, weekdays = setOf(1, 2, 4, 5), dayPlaces = emptyMap(),
        minutes = 60, experience = "intermediate", muscles = null,
        marks = mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 80.0, LiftMark.DEADLIFT to 150.0),
    ),

    /** (b) Casa con mancuernas y banco, Culturismo, 3 días × 75 min, con dos músculos prioritarios (espalda y hombros). */
    QB(
        places = setOf(TrainingPlace.HOME), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.BODYBUILDING, freshDay = 1, weekdays = setOf(1, 3, 5), dayPlaces = emptyMap(),
        minutes = 75, experience = "intermediate",
        exactMaterial = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH),
        muscles = setOf(MuscleSymbol.BACK, MuscleSymbol.SHOULDERS),
    ),

    /** (c) Espacios públicos, Calistenia, «pocas dominadas», 5 días × 45 min. */
    QC(
        places = setOf(TrainingPlace.PUBLIC), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.CALISTHENICS, freshDay = 2, weekdays = setOf(1, 2, 3, 5, 6), dayPlaces = emptyMap(),
        minutes = 45, experience = "intermediate", muscles = null,
        capabilities = mapOf(
            CapabilitySkill.PULL_UP to CapabilityLevel.SOME, CapabilitySkill.PUSH_UP to CapabilityLevel.MANY,
            CapabilitySkill.DIP to CapabilityLevel.SOME, CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.NONE,
        ),
    ),

    /** (d) Gimnasio y casa, Powerlifting con marcas en kg, 3 días × 90 min (lunes y viernes en el gimnasio, miércoles en casa). */
    QD(
        places = setOf(TrainingPlace.GYM, TrainingPlace.HOME), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.POWERLIFTING, freshDay = 1, weekdays = setOf(1, 3, 5),
        dayPlaces = mapOf(3 to TrainingPlace.HOME), minutes = 90, experience = "intermediate", muscles = null,
        marks = mapOf(LiftMark.SQUAT to 160.0, LiftMark.BENCH to 110.0, LiftMark.DEADLIFT to 200.0), marksUnit = "kg",
    ),

    /** (e) Solo peso corporal, «Funcional y saludable», 7 días × 180 min. */
    QE(
        places = setOf(TrainingPlace.HOME), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.FUNCTIONAL_HEALTH, freshDay = 3, weekdays = (1..7).toSet(), dayPlaces = emptyMap(),
        minutes = 180, experience = "returning", exactMaterial = emptySet(), muscles = null,
    ),

    /** (f) Halterofilia en gimnasio, 4 días × 90 min (con sus tres marcas: sentadilla, arranque y dos tiempos). */
    QF(
        places = setOf(TrainingPlace.GYM), addMaterial = emptySet(), dropMaterial = emptySet(),
        goal = TrainingGoalProfile.WEIGHTLIFTING, freshDay = 2, weekdays = setOf(1, 2, 4, 5), dayPlaces = emptyMap(),
        minutes = 90, experience = "intermediate", muscles = null,
        marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 100.0),
    ),
    ;

    companion object {
        /** `gym`, `home`, `park`, `multi`, `all` o uno de los seis casos de la auditoría (`qa`…`qf`, también `q-a`). */
        fun of(name: String?): HarnessPersona {
            val key = name?.replace("-", "")
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: GYM
        }
    }
}

/**
 * Alcance del borrador del arnés: `training` (por defecto) es el alta de solo entreno de siempre; `full` es el alta completa
 * (Entreno, Nutrición y RINGS), con los mismos valores que `newDraft` del ViewModel para `SetupWizardMode.FULL`.
 */
internal enum class HarnessScope {
    TRAINING,
    FULL;

    companion object {
        /** `full` (sin distinguir mayúsculas) es el alta completa; cualquier otro valor, o ninguno, es solo entreno. */
        fun of(name: String?): HarnessScope = if (name?.trim()?.equals("full", ignoreCase = true) == true) FULL else TRAINING
    }
}

/**
 * El borrador del arnés: parte de uno nuevo (como `newDraft` del ViewModel), responde y confirma todos los pasos de la ruta ANTES
 * de [start] con la persona [persona] y deja el cursor en [start]. [answers] (ver [withAnswers]) escribe además datos del paso
 * activo sin confirmarlo, para abrirlo ya con elecciones hechas.
 *
 * Con [HarnessScope.TRAINING] (por defecto) es el alta de solo entreno: los hitos entre bloques (el de datos básicos) se confirman
 * por el camino, como al pulsar «Continuar» en su overlay. Si [start] no está en la ruta de esa persona, el cursor se queda en el
 * hito del bloque de Entreno (el overlay de «bloque completado»), sin confirmarlo, y nunca llega a la revisión ni a «Activar».
 *
 * Con [HarnessScope.FULL] la ruta pasa también por Nutrición y RINGS, y la persona elige su programa «a medida» al pasar por PLAN
 * (como al tocar «Elegir»). El cursor se para en [start] o, si no está en la ruta, en la revisión final.
 */
internal fun buildHarnessDraft(
    draftId: String,
    persona: HarnessPersona,
    start: SetupStepId,
    answers: String,
    revision: Int,
    scope: HarnessScope = HarnessScope.TRAINING,
): SetupWizardDraft {
    val full = scope == HarnessScope.FULL
    // Los valores de `newDraft` del ViewModel: `FULL` (todo el alta, nutrición en modo «create») o `TRAINING_ONLY`.
    val draftScope = if (full) "full" else "training_only"
    val first = WizChatGraph.firstFor(WizChatGraphContext(includeTraining = true, includeNutrition = full))
    val base = SetupWizardDraft(
        draftId = draftId,
        commitId = UUID.randomUUID().toString(),
        draftScope = draftScope,
        revision = revision,
        moduleChoice = if (full) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING,
        includeTraining = true,
        includeNutrition = full,
        nutritionDraft = if (full) NutritionWizardDraft(mode = "create", planId = null, weightUnit = "kg") else null,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        catalogRevision = PersonalizedPlanCatalog.REVISION,
        wizChat = WizChatProgress(
            scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = draftScope,
            currentQuestionId = first,
            stage = WizChatGraph.stageFor(first),
        ),
    )
    var draft = base.copy(stepProgress = SetupStepProgress.initial(base.stepContext()))
    // Solo entreno: también se para en el hito del bloque. Alta completa: el recorrido sigue hasta la revisión.
    val stops = if (full) {
        setOf(start, SetupStepId.REVIEW_ACTIVATE)
    } else {
        setOf(start, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
    }
    var guard = 0
    while (guard++ < MAX_STEPS) {
        val step = draft.stepProgress.currentStepId
        if (step in stops) break
        // Alta completa: la persona elige su programa «a medida» al pasar por PLAN; sin él, la revisión bloquea la activación.
        val chosen = if (full && step == SetupStepId.PLAN) {
            draft.copy(selectedCatalogId = GeneratedPlans.entryIdFor(persona.goal))
        } else {
            draft
        }
        draft = chosen.answeredAs(step, persona).touchStep(step).confirmCurrentStep(step)
    }
    return draft.withAnswers(answers)
}

private const val MAX_STEPS = 80

/** Base del identificador del borrador del arnés: propio, para no tocar el borrador canónico del asistente. */
internal const val HARNESS_DRAFT_BASE = "setup-harness-w"

/**
 * El identificador del borrador del arnés para un arranque. Con [reset] (lo normal) es UNO NUEVO (`setup-harness-w-<segundos>`): la
 * activación escribe observaciones corporales con id `<borrador>/weight/<fecha>` y una segunda alta del mismo día con el mismo id
 * falla con «La observación … ya existe y no fue creada por este alta», así que los seis recorridos de la auditoría Q (que activan
 * uno tras otro en el teléfono, sin poder limpiar su base de datos) solo caben con un id distinto cada vez. Sin `reset` (matar el
 * proceso, giro de pantalla) se reabre el último ([previous], que el arnés recuerda) y, si no hubiera ninguno, se crea uno nuevo.
 */
internal fun harnessDraftIdFor(reset: Boolean, previous: String?, nowMs: Long): String =
    if (!reset && !previous.isNullOrBlank()) previous else "$HARNESS_DRAFT_BASE-${nowMs / 1000L}"

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
        persona.exactMaterial
            ?: ((selectedEquipmentSymbols() - EquipmentSymbolId.BODYWEIGHT_ONLY + persona.addMaterial) - persona.dropMaterial),
    )
    SetupStepId.GOAL -> withGoalProfile(persona.goal)
    // C4a: la persona de powerlifting intermedio pasa por ¿Compites? y, sin esta respuesta, se queda sin responder.
    SetupStepId.COMPETITION -> withCompetition(CompetitionAnswer.NO)
    SetupStepId.FRESH_DAY -> withFreshestDay(persona.freshDay)
    SetupStepId.WEEKDAYS -> persona.dayPlaces.entries.fold(withWeekdays(persona.weekdays)) { draft, (day, place) ->
        draft.withDayPlace(day, place)
    }
    SetupStepId.SESSION_TIME -> withSessionMinutes(persona.minutes)
    // Caminar siempre está entre las opciones del cardio; las máquinas y la bicicleta dependen del material de cada persona.
    SetupStepId.CARDIO_TYPE -> withStepChoice(step, CardioChoice.WALK.name)
    SetupStepId.CARDIO_TIME,
    SetupStepId.VOLUME_TECHNIQUE,
    SetupStepId.VOLUME_CONSISTENCY,
    SetupStepId.VOLUME_STRENGTH,
    SetupStepId.VOLUME_MOBILITY,
    -> middleOption(step)
    SetupStepId.CAPABILITIES -> capabilitySkills().fold(this) { draft, skill ->
        draft.withCapability(skill, persona.capabilities[skill] ?: CapabilityLevel.SOME)
    }
    // null = acepta lo que el perfil sugiere (queda como declarado al confirmar el paso).
    SetupStepId.PRIORITIES -> persona.muscles?.let { withMuscles(it) } ?: this
    SetupStepId.TRAINING_MAX -> marksLifts()
        .fold(this) { draft, lift -> draft.withLiftMark(lift, persona.marks[lift] ?: markFor(lift)) }
        .withMarksUnit(persona.marksUnit)
    // Nutrición en automático, «Definir» de 64 a 60 kg: sin calorías manuales (ese modo no las pregunta) y con dos pesajes de los
    // últimos días, el de ayer con % de grasa. El motor calcula el plan con estos datos.
    SetupStepId.NUTRITION_START -> withStepChoice(step, "automatic")
    SetupStepId.NUTRITION_ELIGIBILITY -> withStepChoices(step, setOf("none"))
    SetupStepId.NUTRITION_DIRECTION -> withStepChoice(step, "deficit")
    SetupStepId.NUTRITION_RHYTHM -> withStepChoice(step, "medium")
    // Como la pantalla: el objetivo va en el borrador de nutrición, en el texto del paso y en su número.
    SetupStepId.NUTRITION_TARGET -> copy(
        nutritionDraft = (nutritionDraft ?: NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId))
            .copy(targetWeightText = "60", weightUnit = "kg"),
    ).withStepText(step, "60").withStepNumber(step, 60.0)
    SetupStepId.NUTRITION_HISTORY_CONTEXT -> withStepChoice(step, "falling")
    SetupStepId.NUTRITION_ACTIVITY -> withStepChoice(step, EerActivity.ACTIVE.name)
    SetupStepId.NUTRITION_DISTRIBUTION -> withStepChoice(step, "uniform")
    SetupStepId.NUTRITION_WEIGH_INS -> copy(historicalWeighIns = harnessWeighIns())
    // RINGS: ayer y hace tres días (uno cae en las 72 h, así que sale la pregunta de series), dos ejercicios principales, series
    // por grupo (piernas 12, pecho 9), intensidad «Dura», sensaciones medias (3 de 5) y sin molestias: ruta de RINGS hasta la revisión.
    SetupStepId.RINGS_SESSION_DAYS -> withTrainedDayToggled(1).withTrainedDayToggled(3)
    SetupStepId.RINGS_MAIN_LIFTS -> withMainLiftToggled(RingsLiftPattern.SQUAT).withMainLiftToggled(RingsLiftPattern.BENCH)
    SetupStepId.RINGS_GROUP_SETS -> withGroupSets(RingsMuscleGroup.LEGS, 12).withGroupSets(RingsMuscleGroup.CHEST, 9)
    SetupStepId.RINGS_INTENSITY -> withStepChoice(step, InitialRecoveryIntensity.HARD.name)
    SetupStepId.RINGS_FEELINGS -> withFeelings(muscular = 3, energy = 3, structure = 3)
    // La molestia se escribe con la selección múltiple: es la que proyecta «Sin molestias» (con `withStepChoice` no llega).
    SetupStepId.RINGS_DISCOMFORT -> withStepChoices(step, setOf("none"))
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
 * Dos pesajes de los últimos días, con fechas distintas (la activación rechaza dos en el mismo día): el de ayer con % de grasa.
 * Del más reciente al más viejo, como los ordena la pantalla de pesajes.
 */
private fun harnessWeighIns(today: LocalDate = LocalDate.now()): List<SetupWeighIn> = listOf(
    SetupWeighIn(id = UUID.randomUUID().toString(), dateIso = today.minusDays(1).toString(), weightKg = 64.2, bodyFatPercent = 23.8),
    SetupWeighIn(id = UUID.randomUUID().toString(), dateIso = today.minusDays(3).toString(), weightKg = 64.8),
)

/**
 * Escribe datos del paso activo (y los marca como declarados) SIN confirmarlo: `clave=valor/clave=valor` (la barra separa los
 * datos porque `;` lo interpretaría el intérprete de órdenes del teléfono al pasar por `adb shell`).
 *  - `places=GYM,HOME`; `material=BARBELL,RACK` (lista exacta) o `material=+RINGS,-BARBELL` (sobre lo que ya hay);
 *  - `goal=POWERLIFTING`; `fresh=4`; `days=1,3,5`; `startday=4`; `dayplaces=3:HOME,6:PUBLIC`; `minutes=75` (el reloj va de 30 a 180: menos se lee como 30);
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
