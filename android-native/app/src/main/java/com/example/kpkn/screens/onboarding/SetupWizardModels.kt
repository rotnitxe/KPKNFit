package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.programs.toTrainingReference
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.TrainingValidation
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupDependencyRules
import com.example.kpkn.domain.onboarding.SetupFieldCheck
import com.example.kpkn.domain.onboarding.SetupInputFootprint
import com.example.kpkn.domain.onboarding.SetupInventoryGroup
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatValidation
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class SetupWizardMode { FULL, TRAINING_ONLY, NUTRITION_ONLY, RINGS_ONLY, RESUME }

@Serializable
enum class SetupWizardChapter { PROFILE, VOLUME, TRAINING, WEEK, NUTRITION, RINGS, REVIEW }

@Serializable
enum class SetupModuleChoice { TRAINING, TRAINING_AND_NUTRITION }

@Serializable
enum class SetupProgramRoute { CUSTOMIZABLE, PROTOCOL, LATER }

@Serializable
enum class SetupRecentTrainingState { NOT_ANSWERED, NO, YES, UNKNOWN }

@Serializable
enum class SetupDiscomfortState { NOT_ANSWERED, NONE, DECLARED, OMITTED }

/**
 * Procedencia de la grasa corporal ACTUAL: una medición real, una estimación
 * visual con la figura o una omisión explícita. El percentil y la fuente son
 * estado actual, nunca una meta; la figura (hombre/mujer) no escribe
 * `equationSex` ni plantea identidad.
 */
@Serializable
enum class SetupBodyFatSource { MEASURED, VISUAL_ESTIMATE, UNKNOWN }

/**
 * Estado visible del paso de grasa corporal. Solo [PENDING] bloquea Continuar:
 * la figura de arranque («≈25 %») no es una respuesta, así que el paso exige una
 * acción explícita (mover o tocar la figura, escribir una medición u omitir)
 * antes de avanzar. Se deriva siempre del borrador; no se guarda aparte.
 */
enum class SetupBodyFatState {
    /** Nada declarado todavía: ni figura, ni medición, ni omisión, ni dato previo. */
    PENDING,

    /** Sin acción en este alta, pero ya hay un porcentaje previo (Ajustes o borrador anterior): se conserva y no bloquea. */
    ON_FILE,

    /** Estimación visual con la figura. */
    VISUAL,

    /** Medición escrita por la persona. */
    MEASURED,

    /** «Omitir este paso»: sin dato, a propósito. */
    SKIPPED,
}

/** Mensaje de la validación y del texto de ayuda del paso mientras está [SetupBodyFatState.PENDING]. */
internal const val BODY_FAT_PENDING_MESSAGE =
    "Mueve la figura, usa el valor mostrado, escribe una medición u omite este paso."

private fun Double?.isUsableBodyFat(): Boolean {
    val value = this ?: return false
    return value.isFinite() && value in 3.0..60.0
}

/**
 * Estado de la grasa corporal del borrador. Un porcentaje previo en Ajustes
 * ([SetupWizardDraft.importedBodyFatPercent], solo si es creíble) o ya presente
 * en el borrador cuenta como [SetupBodyFatState.ON_FILE]: a quien vuelve se le
 * trata como a altura y peso sembrados desde Ajustes (no se le bloquea).
 */
fun SetupWizardDraft.bodyFatState(): SetupBodyFatState {
    val typed = !inputTexts[SetupStepId.BODY_FAT.name].isNullOrBlank()
    return when (bodyFatSource) {
        SetupBodyFatSource.UNKNOWN -> SetupBodyFatState.SKIPPED
        SetupBodyFatSource.MEASURED ->
            if (bodyFatPercent != null || typed) SetupBodyFatState.MEASURED else SetupBodyFatState.PENDING
        SetupBodyFatSource.VISUAL_ESTIMATE ->
            if (bodyFatPercent != null) SetupBodyFatState.VISUAL else SetupBodyFatState.PENDING
        null -> when {
            typed -> SetupBodyFatState.MEASURED
            bodyFatPercent != null || importedBodyFatPercent.isUsableBodyFat() -> SetupBodyFatState.ON_FILE
            else -> SetupBodyFatState.PENDING
        }
    }
}

/**
 * Pesaje real declarado por el usuario: id estable + fecha + peso en kg.
 * El wizard nunca fabrica pesajes ni fechas; la tendencia y el máximo anterior
 * viajan en campos propios del contexto, separados de estos registros.
 */
@Serializable
data class SetupWeighIn(
    val id: String,
    val dateIso: String,
    val weightKg: Double,
)

/**
 * Estado de edición de una fila editorial de paso (M4 lo persiste vía
 * `updateStep`). Mientras `editing` está activo la validación del paso no
 * deja avanzar, para no perder la fila a medias; guardar o salir la cierra.
 */
@Serializable
data class SetupStepEditorState(
    val editing: Boolean = false,
    val itemIndex: Int? = null,
    val phase: Int = 0,
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class SetupVolumeAnswers(
    val style: TrainingStyle? = null,
    val technique: Int? = null,
    val consistency: Int? = null,
    val strength: Int? = null,
    val mobility: Int? = null,
    val responseState: CalibrationResponseState = CalibrationResponseState.UNKNOWN,
)

@Serializable
enum class SetupExperience(val label: String) { NEW("Estoy empezando"), RETURNING("Estoy volviendo"), INTERMEDIATE("Ya entreno con constancia"), ADVANCED("Tengo experiencia") }

@Serializable
enum class SetupTrainingPath(val label: String) { PERSONALIZE("Personaliza un plan"), FROM_SCRATCH("Crea desde cero") }

/**
 * Exactly four visible profiles (§15.1 / P-104): Fuerza, Músculo, Fuerza y
 * músculo and Atleta completo. `HEALTH` and `MIXED` survive ONLY so legacy
 * drafts keep reading; they are never auto-converted (the repair marks GOAL for
 * review and suggests Atleta completo without selecting it).
 */
@Serializable
enum class SetupGoal(val label: String) {
    STRENGTH("Fuerza"),
    MUSCLE("Músculo"),
    STRENGTH_MUSCLE("Fuerza y músculo"),
    COMPLETE_ATHLETE("Atleta completo"),
    HEALTH("Salud y condición"),
    MIXED("Fuerza + cardio");

    /** Legacy values preserved for read compatibility; never offered anew. */
    val isLegacyOnly: Boolean
        get() = this == HEALTH || this == MIXED
}

/** Fuerza → powerlifting, Músculo → hipertrofia, Fuerza y músculo → powerbuilding. */
val SetupGoal.inferredTrainingStyle: com.example.kpkn.data.models.TrainingStyle?
    get() = when (this) {
        SetupGoal.STRENGTH -> com.example.kpkn.data.models.TrainingStyle.POWERLIFTER
        SetupGoal.MUSCLE -> com.example.kpkn.data.models.TrainingStyle.BODYBUILDER
        SetupGoal.STRENGTH_MUSCLE -> com.example.kpkn.data.models.TrainingStyle.POWERBUILDER
        // Atleta completo es un perfil combinado (§15.1): no se disfraza de una
        // disciplina de tres; HEALTH/MIXED legacy conservan su lectura.
        SetupGoal.COMPLETE_ATHLETE, SetupGoal.HEALTH, SetupGoal.MIXED -> null
    }

/** True when the goal asks for the cardio branch of the wizard. */
val SetupGoal.requiresCardio: Boolean
    get() = this == SetupGoal.COMPLETE_ATHLETE || this == SetupGoal.MIXED

/** Reference used to pick candidates: inferred from the goal or asked in the brief focus question. */
fun SetupWizardDraft.trainingReference(): com.example.kpkn.data.programs.TrainingReference? {
    // Atleta completo es una combinación de capacidades, no una de las
    // disciplinas legacy. No heredar la elección de estilo/calibración como
    // un filtro oculto de catálogo.
    if (goal == SetupGoal.COMPLETE_ATHLETE) return null
    val style = goal?.inferredTrainingStyle ?: volumeAnswers.style
    return style?.toTrainingReference()
}

@Serializable
enum class SetupFocus(val label: String) { FULL_BODY("Todo el cuerpo"), GLUTES("Glúteos"), LEGS("Piernas"), BACK("Espalda"), CHEST("Pecho"), SHOULDERS("Hombros"), ARMS("Brazos") }

@Serializable
enum class SetupEquipment(val label: String) { NONE("Sin material"), BODYWEIGHT("Peso corporal"), BANDS("Bandas"), DUMBBELLS("Mancuernas"), MACHINE("Máquinas"), CABLE("Polea"), BARBELL("Barra"), PULL_UP("Barra de dominadas"), GYM("Gimnasio completo"), SUPPORT("Apoyo estable"), BALL("Balón"), SMITH("Máquina Smith") }

@Serializable
data class SetupExerciseDraft(
    val id: String,
    val exercise: Exercise,
    val info: ExerciseMuscleInfo? = null,
) {
    val name: String get() = exercise.name
    val sets: Int? get() = exercise.sets.size.takeIf { it > 0 }
    val reps: Int? get() = exercise.sets.firstOrNull()?.targetReps
}

@Serializable
data class SetupSessionDraft(
    val weekday: Int,
    val title: String,
    val exercises: List<SetupExerciseDraft> = emptyList(),
) {
    fun toSession(commitId: String): Session = Session(
        id = "$commitId-session-$weekday",
        name = title,
        exercises = exercises.map { it.exercise },
        dayOfWeek = weekday,
        assignedDays = listOf(weekday),
    )
}

@Serializable
data class SetupRingsAnswers(
    val startAction: String? = null,
    val recentTraining: Boolean? = null,
    val recencyDays: Int? = null,
    val sessionsLastSevenDays: Int? = null,
    val activityType: InitialRecoveryActivityType? = null,
    val intensity: Int? = null,
    val zones: List<String> = emptyList(),
    val muscleFeeling: Int? = null,
    val energy: Int? = null,
    val structureFeeling: Int? = null,
    val capturedAtMs: Long? = null,
    val recentTrainingState: SetupRecentTrainingState = SetupRecentTrainingState.NOT_ANSWERED,
    val lastSessionRecencyDays: Int? = null,
    val intensityLevel: InitialRecoveryIntensity? = null,
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val recentMuscles: Set<String> = emptySet(),
    val axialExposure: InitialRecoveryAxialExposure = InitialRecoveryAxialExposure(),
    val discomfortIds: List<String> = emptyList(),
    val discomfortState: SetupDiscomfortState = SetupDiscomfortState.NOT_ANSWERED,
    val activityTypeState: com.example.kpkn.data.models.InitialRecoveryResponseState = com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN,
)

typealias InitialRingsAnswers = SetupRingsAnswers

@Serializable
data class SetupWizardDraft(
    val draftId: String = "",
    val commitId: String = "",
    /** Canonical persisted scope; RESUME is only a navigation intent. */
    val draftScope: String = "full",
    val revision: Int = 1,
    val chapter: SetupWizardChapter = SetupWizardChapter.PROFILE,
    val name: String = "",
    val profileGender: Gender? = null,
    val moduleChoice: SetupModuleChoice = SetupModuleChoice.TRAINING_AND_NUTRITION,
    val weightKg: Double? = null,
    val importedWeightKg: Double? = null,
    /**
     * Grasa corporal (%) que ya constaba en Ajustes al crear el borrador (usuario
     * que vuelve). Igual que [importedWeightKg] no es una respuesta de este alta:
     * nunca se declara ni crea una observación; solo evita bloquear el paso de
     * grasa corporal y se muestra como dato previo.
     */
    val importedBodyFatPercent: Double? = null,
    val weightUnit: String = "kg",
    val weightUnitChanged: Boolean = false,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val birthDateIso: String? = null,
    val experience: SetupExperience? = null,
    val trainingPath: SetupTrainingPath? = null,
    val programRoute: SetupProgramRoute = SetupProgramRoute.CUSTOMIZABLE,
    val goal: SetupGoal? = null,
    val focus: SetupFocus = SetupFocus.FULL_BODY,
    val daysPerWeek: Int? = null,
    val selectedWeekdays: Set<Int> = emptySet(),
    val minutesPerSession: Int? = null,
    val cardioType: CardioType? = null,
    val cardioMinutes: Int? = null,
    val equipment: Set<SetupEquipment> = emptySet(),
    val trainingEnvironment: String? = null,
    val priorityMuscles: Set<String> = emptySet(),
    val lowerEmphasisMuscles: Set<String> = emptySet(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val selectedCatalogId: String? = null,
    val sessions: List<SetupSessionDraft> = emptyList(),
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val activateProgram: Boolean = true,
    val activateNutrition: Boolean = true,
    val confirmActivation: Boolean = false,
    val acceptFixedRecipeDifference: Boolean = false,
    val ringsAnswers: SetupRingsAnswers? = null,
    val nutritionMode: String = "create",
    val nutritionPlanId: String? = null,
    val nutritionDraft: NutritionWizardDraft? = null,
    val nutritionStepIndex: Int = 0,
    val nutritionDraftJson: String? = null,
    val catalogRevision: String? = null,
    val volumeRecommendations: List<VolumeRecommendation> = emptyList(),
    val athleteProfileScore: AthleteProfileScore? = null,
    val powerliftingProfile: PowerliftingProfile? = null,
    val knowsTrainingMarks: Boolean = false,
    val volumeAnswers: SetupVolumeAnswers = SetupVolumeAnswers(),
    val volumeCalibrationProfile: VolumeCalibrationProfile? = null,
    val manualMuscleOverrides: Map<String, Int> = emptyMap(),
    val manualEnergyOverride: Int? = null,
    val manualStructureOverride: Int? = null,
    /**
     * Canonical wizard progress for the traditional step flow: stable step id,
     * block, step index and completed blocks. Legacy WizChat drafts are migrated
     * into this field by [SetupDraftCompatibility.repair].
     */
    val stepProgress: SetupStepProgress = SetupStepProgress(),
    /**
     * Legacy conversational progress. Since the step migration it is only a
     * compatibility mirror (question cursor, answer log and sound/branch flags)
     * kept so older drafts and screens keep loading; it never drives navigation.
     */
    val wizChat: WizChatProgress = WizChatProgress(),
    /**
     * Steps the user actively touched (typed or selected) in this or any
     * previous session. Additive and serialization-safe: values prefilled from
     * settings are never silently upgraded to DECLARED; only an explicit user
     * interaction or a legacy DECLARED mirror answer counts.
     */
    val declaredSteps: Set<SetupStepId> = emptySet(),
    // ── Contrato del wizard tradicional (API exacta UI/VM) ──────────────────
    /** Contrato real de entrenamiento: bolsa de orden, autorregulación, calentamientos e inventario. */
    val trainingOptions: SetupTrainingOptions = SetupTrainingOptions(),
    /** Unidad visible de la estatura (`cm`/`ft`); el valor canónico sigue en [heightCm]. */
    val heightUnit: String = "cm",
    /** Grasa corporal ACTUAL (%): composición declarada, nunca una meta. */
    val bodyFatPercent: Double? = null,
    /** Cómo se obtuvo [bodyFatPercent]; exigido para persistir un percentil real. */
    val bodyFatSource: SetupBodyFatSource? = null,
    /** Figura del selector de complexión; independiente del sexo de cálculo. */
    val physiqueModel: String = "male",
    /** Posición del slider de complexión (estado de UI persistido con el borrador). */
    val physiqueSliderPosition: Float = 4f,
    /** Tendencia de peso declarada como contexto: rising | stable | falling. */
    val weightTrend: String? = null,
    /** Máximo de peso anterior (kg) como contexto del plan; no es un pesaje. */
    val previousMaximumWeightKg: Double? = null,
    /** Pesajes reales añadidos por el usuario; nunca derivados ni inventados. */
    val historicalWeighIns: List<SetupWeighIn> = emptyList(),
    /** Cuándo se midió realmente el peso actual; sobrevive a la rehidratación sin regenerarse. */
    val currentWeightMeasuredAtEpochMs: Long? = null,
    /** Cuándo se capturó realmente la grasa corporal actual; no se regenera al rehidratar. */
    val bodyFatCapturedAtEpochMs: Long? = null,
    /** Selecciones canónicas por paso (valores estables de las opciones del catálogo). */
    val stepSelections: Map<SetupStepId, List<String>> = emptyMap(),
    /**
     * Texto crudo por paso, siempre con clave [SetupStepId.name]: conserva el
     * intermedio de tecleo («1» de «19») y el texto inválido mientras el
     * usuario corrige. Nunca se usa [SetupStepId] como clave.
     */
    val inputTexts: Map<String, String> = emptyMap(),
    /** Fila editorial abierta por paso (M4): mientras `editing` no se cierre, el paso no avanza. */
    val stepEditors: Map<SetupStepId, SetupStepEditorState> = emptyMap(),
    /**
     * Paso al que volver cuando una edición arrancada desde la revisión final
     * se confirma (o se guarda y sale). El VM lo persiste con el borrador para
     * no perder la intención al reanudar; null = avance normal.
     */
    val reviewReturnStep: SetupStepId? = null,
)

/** Whether a step value was declared by the user: touched here or recorded as DECLARED in the legacy mirror. */
fun SetupWizardDraft.isStepDeclared(step: SetupStepId): Boolean {
    if (step in declaredSteps) return true
    val question = SetupStepGraph.questionForStep(step) ?: return false
    return wizChat.acceptedAnswers.any { it.questionId == question && it.source == WizChatAnswerSource.DECLARED }
}

/** Marks a step as explicitly touched by the user; never removes existing marks. */
fun SetupWizardDraft.touchStep(step: SetupStepId): SetupWizardDraft =
    copy(declaredSteps = declaredSteps + step)

/**
 * Monotonic revision bump for a write. The draft from models navigation
 * ([goNext]/[goBack]/[confirmCurrentStep]) carries its own revisions for the
 * mirrors but never for the Room row, so the persistence boundary must always
 * beat the previous revision by one. Never produces a revision below [previous].
 */
fun SetupWizardDraft.withNextDraftRevision(previous: SetupWizardDraft): SetupWizardDraft =
    copy(revision = maxOf(revision, previous.revision + 1))

/** Routing context derived from the draft; mirrors the legacy WizChat context. */
fun SetupWizardDraft.stepContext(): SetupStepContext = SetupStepContext(
    includeTraining = includeTraining,
    includeNutrition = includeNutrition,
    includeRings = draftScope in setOf("full", "resume", "rings_only"),
    programRouteLater = programRoute == SetupProgramRoute.LATER,
    homeEquipmentSelected = trainingEnvironment == "home" || trainingEnvironment == "Entreno en casa",
    mixedTraining = goal == SetupGoal.MIXED,
    completeAthleteGoal = goal == SetupGoal.COMPLETE_ATHLETE,
    hasTrainingMarks = knowsTrainingMarks,
    goalStyleInferred = goal?.inferredTrainingStyle != null || goal == SetupGoal.COMPLETE_ATHLETE,
    nutritionProfessional = nutritionDraft?.mode == "professional",
    nutritionStarted = includeNutrition && nutritionMode == "create",
    ringsAction = ringsAnswers?.startAction,
    recentTraining = ringsAnswers?.recentTraining,
    // Ramas nuevas: derivadas SOLO de datos del borrador, nunca de `answered`
    // (así la ruta es estable mientras el cursor está dentro de una rama).
    inventoryGroups = inventoryGroups(),
    asksAvailability = asksEquipmentCategories(),
    wantsCardio = cardioType != null || cardioMinutes != null,
    nutritionStartChoice = stepSelections[SetupStepId.NUTRITION_START]?.firstOrNull()
        ?: nutritionDraft?.configurationMode?.name?.lowercase(),
    nutritionDirection = stepSelections[SetupStepId.NUTRITION_DIRECTION]?.firstOrNull()
        ?: nutritionDraft?.direction?.name?.lowercase(),
    // AUTO inserta el paso de confirmación; el paso se mantiene en la ruta
    // mientras el modo siga siendo AUTO, nunca por estado de respuesta.
    autoregulationOn = trainingOptions.autoregulationMode == AutoregulationMode.AUTO,
)

/**
 * El alta ya no pregunta stock (kilos, discos, rangos). El material se declara
 * por categorías en [SetupStepId.AVAILABILITY]. Los pasos de inventario quedan
 * fuera de la ruta productiva; se conservan solo para borradores antiguos.
 */
internal fun SetupWizardDraft.inventoryGroups(): Set<SetupInventoryGroup> = emptySet()

/** Gimnasio, casa o máquinas: una sola pantalla de categorías. Sin material, no. */
internal fun SetupWizardDraft.asksEquipmentCategories(): Boolean = when (trainingEnvironment) {
    "gym", "Gimnasio completo", "home", "Entreno en casa", "machines", "Principalmente máquinas" -> true
    else -> false
}

/** Fingerprint of the inputs that feed previews; pure navigation never changes it. */
fun SetupWizardDraft.inputFootprint(): SetupInputFootprint = SetupInputFootprint(
    weightKg = weightKg,
    heightCm = heightCm,
    ageYears = ageYears,
    gender = profileGender?.name,
    equationSex = nutritionDraft?.equationSex?.name,
    bodyFatPercent = bodyFatPercent,
    equipment = equipment.map { it.name }.toSet(),
    trainingEnvironment = trainingEnvironment,
    inventory = trainingOptions.inventory?.toString()?.let { setOf(it) }.orEmpty(),
    equipmentAvailability = trainingOptions.availability?.categories?.mapTo(linkedSetOf()) { it.name },
    equipmentApparatus = trainingOptions.availability?.apparatus
        ?.mapValues { (_, presence) -> presence.name }?.toSortedMap().orEmpty(),
    equipmentSupports = trainingOptions.availability?.supports
        ?.mapValues { (_, presence) -> presence.name }?.toSortedMap().orEmpty(),
    daysPerWeek = daysPerWeek,
    selectedWeekdays = selectedWeekdays,
    minutesPerSession = minutesPerSession,
    programRoute = programRoute.name,
    trainingPath = trainingPath?.name,
    goal = goal?.name,
    focus = focus.name,
    experience = experience?.name,
    volumeStyle = volumeAnswers.style?.name,
    volumeResponses = listOf(volumeAnswers.technique, volumeAnswers.consistency, volumeAnswers.strength, volumeAnswers.mobility),
    cardioType = cardioType?.name,
    cardioMinutes = cardioMinutes,
    knowsTrainingMarks = knowsTrainingMarks,
    marks = listOf(powerliftingProfile?.squat1RM, powerliftingProfile?.bench1RM, powerliftingProfile?.deadlift1RM),
    selectedCatalogId = selectedCatalogId,
    priorityMuscles = priorityMuscles,
    lowerEmphasisMuscles = lowerEmphasisMuscles,
    priorityPoints = trainingOptions.orderPriorities,
    selectedSplitId = selectedSplitId,
    customSplitPattern = customSplitPattern,
    customSplitName = customSplitName,
    autoregulationMode = trainingOptions.autoregulationMode.name,
    warmupsPreference = trainingOptions.warmup?.joinToString(";") { step -> "${step.percent ?: ""}:${step.reps ?: ""}" },
    sessionsSignature = sessions.map { session ->
        "${session.weekday}:${session.title}:" + session.exercises.joinToString("|") { item ->
            "${item.id}/${item.sets ?: 0}/${item.reps}"
        }
    },
    ringsSignature = listOf(
        ringsAnswers?.toString().orEmpty(),
        manualMuscleOverrides.toString(),
        manualEnergyOverride?.toString().orEmpty(),
        manualStructureOverride?.toString().orEmpty(),
    ),
    nutritionSignature = listOf(
        nutritionDraft?.toString().orEmpty(),
        nutritionMode,
        nutritionPlanId.orEmpty(),
    ),
    nutritionStartChoice = stepContext().nutritionStartChoice,
    nutritionDirection = stepContext().nutritionDirection,
    nutritionDistribution = nutritionDraft?.weeklyDistribution?.name,
    nutritionTargetKg = nutritionDraft?.targetWeightText?.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) },
    nutritionHistorySignature = listOf(
        weightTrend.orEmpty(),
        previousMaximumWeightKg?.toString().orEmpty(),
        historicalWeighIns.joinToString(";") { "${it.id}|${it.dateIso}|${it.weightKg}" },
    ).joinToString("|"),
)

/** Keeps valid answers, marks incompatible selections pending and stales previews. */
fun SetupWizardDraft.applyChangeImpact(source: SetupChangeSource): SetupWizardDraft {
    val impact = SetupDependencyRules.impactOf(source)
    return copy(
        stepProgress = stepProgress
            .withPendingReview(impact.pendingSteps)
            .withStalePreviews(impact.stalePreviews),
    )
}

/** Applies every physiological change detected between [previous] and this draft. */
fun SetupWizardDraft.withChangeImpacts(previous: SetupWizardDraft): SetupWizardDraft {
    val sources = SetupChangeDetector.sourcesFor(previous.inputFootprint(), inputFootprint())
    return sources.fold(this) { draft, source -> draft.applyChangeImpact(source) }
}

/** Selections that must be reviewed again once earlier data changed. */
fun SetupWizardDraft.markPendingQuestions(questions: Collection<WizChatQuestionId>): SetupWizardDraft {
    val steps = questions.mapNotNull(SetupStepGraph::stepForQuestion).toSet()
    return copy(stepProgress = stepProgress.withPendingReview(steps))
}

/**
 * Rewinds to one step to change an earlier answer. Every answer is kept as data
 * and only the selections that may no longer be compatible are marked pending.
 */
fun SetupWizardDraft.editStep(
    step: SetupStepId,
    pendingQuestions: Collection<WizChatQuestionId> = emptyList(),
): SetupWizardDraft {
    val question = SetupStepGraph.questionForStep(step)
    return markPendingQuestions(pendingQuestions).copy(
        stepProgress = stepProgress.at(step, stepContext()),
        wizChat = wizChat.copy(
            currentQuestionId = question ?: wizChat.currentQuestionId,
            stage = question?.let(WizChatGraph::stageFor) ?: wizChat.stage,
            terminal = false,
            revision = wizChat.revision + 1,
        ),
    )
}

/** Advances or rewinds one step without deleting any answer. */
fun SetupWizardDraft.goNext(): SetupWizardDraft =
    moveCursorTo(SetupStepGraph.next(stepProgress.currentStepId, stepContext()))

fun SetupWizardDraft.goBack(): SetupWizardDraft =
    moveCursorTo(SetupStepGraph.previous(stepProgress.currentStepId, stepContext(), stepProgress.visited))

private fun SetupWizardDraft.moveCursorTo(step: SetupStepId?): SetupWizardDraft {
    if (step == null) return this
    val question = SetupStepGraph.questionForStep(step)
    return copy(
        stepProgress = stepProgress.at(step, stepContext()),
        wizChat = wizChat.copy(
            currentQuestionId = question ?: wizChat.currentQuestionId,
            stage = question?.let(WizChatGraph::stageFor) ?: wizChat.stage,
            terminal = step == SetupStepId.REVIEW_ACTIVATE,
            revision = wizChat.revision + 1,
        ),
    )
}

/** Records where a value came from; engine results can never be declared answers. */
fun SetupWizardDraft.recordStepAnswer(
    step: SetupStepId,
    provenance: SetupAnswerProvenance,
    valueState: SetupValueState,
): SetupWizardDraft = copy(stepProgress = stepProgress.recordAnswer(step, provenance, valueState))

/**
 * Result of a single confirmation of the current step. Exactly one advance is
 * produced per accepted call, so repeated callbacks are dropped by the caller.
 */
enum class SetupSubmitOutcome { ACCEPTED, REJECTED, DROPPED, FAILED }

data class SetupSubmitResult(
    val outcome: SetupSubmitOutcome,
    val movedToStep: SetupStepId? = null,
) {
    val accepted: Boolean get() = outcome == SetupSubmitOutcome.ACCEPTED
    val advanced: Boolean get() = outcome == SetupSubmitOutcome.ACCEPTED && movedToStep != null
}

/** Operations that can fail and be retried explicitly from the UI. */
enum class SetupRetryOperation { LOAD, SAVE, PREVIEW, CANDIDATES, RINGS_PREVIEW, COMMIT }

/**
 * Records provenance for [step] and moves the cursor exactly one step forward.
 * Basics values the user never touched (prefilled from settings) are recorded
 * as ESTIMATED; touched or legacy-DECLARED values are DECLARED. The legacy
 * wizChat mirror keeps a coherent answer log for older readers and the commit
 * review gate.
 */
fun SetupWizardDraft.confirmCurrentStep(step: SetupStepId): SetupWizardDraft {
    val atReview = step == SetupStepId.REVIEW_ACTIVATE
    val nextStep = SetupStepGraph.next(step, stepContext())
    val landedOnReview = atReview || nextStep == SetupStepId.REVIEW_ACTIVATE
    val question = SetupStepGraph.questionForStep(step)
    val declared = isStepDeclared(step)
    val recorded = stepProgress.recordAnswer(
        step,
        if (declared) SetupAnswerProvenance.USER_DECLARED else SetupAnswerProvenance.SUGGESTED,
        if (declared) SetupValueState.DECLARED else SetupValueState.ESTIMATED,
    ).at(nextStep ?: step, stepContext())
    val nextQuestion = if (landedOnReview) WizChatQuestionId.REVIEW
    else SetupStepGraph.questionForStep(nextStep ?: step) ?: wizChat.currentQuestionId
    val mirror = question?.let { listOf(confirmationMirrorRecord(step, it)) }
    // The mirror must stay coherent even for steps without a legacy question
    // (milestones, the review terminal): landing on the review always opens the
    // REVIEW question and marks the draft terminal so commit's gate opens.
    return copy(
        stepProgress = recorded,
        wizChat = wizChat.copy(
            acceptedAnswers = if (question == null) wizChat.acceptedAnswers
            else wizChat.acceptedAnswers.filterNot { it.questionId == question } + mirror.orEmpty(),
            currentQuestionId = nextQuestion,
            stage = WizChatGraph.stageFor(nextQuestion),
            terminal = wizChat.terminal || landedOnReview,
            revision = wizChat.revision + 1,
        ),
    )
}

/** Legacy mirror of a confirmed step: the envelope the old conversational flow would have kept. */
private fun SetupWizardDraft.confirmationMirrorRecord(step: SetupStepId, question: WizChatQuestionId): WizChatAnswerRecord {
    val declared = isStepDeclared(step)
    return WizChatAnswerRecord(
        questionId = question,
        kind = WizChatGraph.question(question)?.kind ?: WizChatAnswerKind.ACTION,
        textValue = when (question) {
            WizChatQuestionId.P_NAME -> name.takeIf { it.isNotBlank() }
            WizChatQuestionId.P_GENDER -> when (profileGender) {
                Gender.FEMALE -> "Mujer"
                Gender.MALE -> "Hombre"
                Gender.OTHER -> "Otro"
                null -> null
            }
            WizChatQuestionId.P_AGE -> ageYears?.toString()
            WizChatQuestionId.P_HEIGHT -> heightCm?.toString()
            WizChatQuestionId.P_WEIGHT -> weightKg?.toString()
            WizChatQuestionId.P_EXPERIENCE -> experience?.label
            else -> null
        },
        numberValue = when (question) {
            WizChatQuestionId.P_AGE -> ageYears?.toDouble()
            WizChatQuestionId.P_HEIGHT -> heightCm
            WizChatQuestionId.P_WEIGHT -> weightKg
            else -> null
        },
        source = if (declared) WizChatAnswerSource.DECLARED else WizChatAnswerSource.SUGGESTED_ACCEPTED,
        revision = wizChat.revision + 1,
    )
}

/** Pending modal decisions; discard is never triggered by the back button. */
@Serializable
enum class SetupWizardDialog { NONE, EXIT, DISCARD }

/** Terminal plans produced only by an explicit user intention. */
sealed interface SetupWizardExitPlan {
    data class SaveAndExit(val draft: SetupWizardDraft) : SetupWizardExitPlan
    data class Discard(val draftId: String) : SetupWizardExitPlan
}

/**
 * Pure wizard session: step navigation, change impacts and the exit/discard
 * intentions. [saveAndExit] and [confirmDiscard] only return a plan after the
 * matching dialog was requested, so discarding always needs explicit
 * confirmation and is never tied to the back button.
 */
data class SetupWizardSession(
    val draft: SetupWizardDraft,
    val dialog: SetupWizardDialog = SetupWizardDialog.NONE,
    val isSavingAndExiting: Boolean = false,
    val exitCompleted: Boolean = false,
) {
    fun requestExit(): SetupWizardSession = copy(dialog = SetupWizardDialog.EXIT)

    fun requestDiscard(): SetupWizardSession = copy(dialog = SetupWizardDialog.DISCARD)

    fun keepConfiguring(): SetupWizardSession = copy(dialog = SetupWizardDialog.NONE)

    fun saveAndExit(): SetupWizardExitPlan? =
        if (dialog != SetupWizardDialog.EXIT || isSavingAndExiting) null
        else SetupWizardExitPlan.SaveAndExit(draft)

    fun onSavedAndExited(): SetupWizardSession =
        copy(dialog = SetupWizardDialog.NONE, isSavingAndExiting = false, exitCompleted = true)

    fun onDiscarded(): SetupWizardSession =
        copy(dialog = SetupWizardDialog.NONE, isSavingAndExiting = false, exitCompleted = true)

    /** Discarding needs the explicit discard dialog; anything else keeps the draft. */
    fun confirmDiscard(): SetupWizardExitPlan? =
        if (dialog != SetupWizardDialog.DISCARD) null
        else SetupWizardExitPlan.Discard(draft.draftId)

    fun goBack(): SetupWizardSession = copy(draft = draft.goBack())

    fun goNext(): SetupWizardSession = copy(draft = draft.goNext())

    fun change(source: SetupChangeSource): SetupWizardSession = copy(draft = draft.applyChangeImpact(source))
}

data class SetupWizardState(
    val draft: SetupWizardDraft,
    val mode: SetupWizardMode = SetupWizardMode.FULL,
    val dirty: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isCommitting: Boolean = false,
    val receiptId: String? = null,
    val programPreview: Program? = null,
    val previewReport: PersonalizationReport? = null,
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val fixedSessionEstimateMinutes: Int? = null,
    val fixedTrainingDays: Set<Int>? = null,
    val requiresActivationConfirmation: Boolean = false,
    val machineState: WizChatMachineState = WizChatMachineState.Loading,
    val isSubmittingAnswer: Boolean = false,
    val messages: List<com.example.kpkn.domain.onboarding.WizChatMessage> = emptyList(),
    val planCandidates: List<SetupPlanCandidate> = emptyList(),
    val availablePlanCandidates: List<SetupPlanCandidate> = emptyList(),
    /**
     * T-001 / AC-T001-02: rechazo estructurado POR CANDIDATO del último
     * cálculo (etapa + causa concreta con la clase/mensaje útil). Se limpia en
     * cada cálculo nuevo y sólo se publica si las entradas siguen vigentes
     * (AC-T001-03), para que la UI distinga «error de catálogo» de «falta
     * material» o «no cabe en tu tiempo» en lugar de un aviso genérico.
     */
    val candidateRejections: List<SetupCandidateRejection> = emptyList(),
    /** §15.2: evaluados / viables / no viables del último barrido. */
    val candidateCounts: SetupCandidateCounts = SetupCandidateCounts(),
    /**
     * §15.2: la selección conserva plan_id + fingerprint + resultado preparado;
     * si cambian respuestas que la afectan, el preview deja de estar vigente y
     * la activación espera re-preparar (nunca se cambia de plan en silencio).
     */
    val selectionStale: Boolean = false,
    /**
     * Paquete A · D2 (B-01): el plan que la persona tenía elegido cuando llegó una lista de candidatos nueva
     * en la que ya no está entre los viables. El ViewModel NO relanza el preview de ese plan (antes un
     * error de su preview escondía toda la lista) y deja aquí el motivo, tomado del rechazo del barrido,
     * para que la lista lo explique. Se limpia al empezar una búsqueda nueva y al elegir otro plan.
     */
    val droppedSelection: SetupDroppedSelection? = null,
    val exerciseSuggestions: List<ExerciseMuscleInfo> = emptyList(),
    val isExerciseSearching: Boolean = false,
    val exerciseSearchError: String? = null,
    val isCandidateLoading: Boolean = false,
    /**
     * Los candidatos visibles salieron del segundo pase a peso corporal:
     * la vista previa y el alta usan ese mismo borrador adaptado.
     */
    val planAdaptedToBodyweight: Boolean = false,
    val nutritionPlanPreview: NutritionPlan? = null,
    val nutritionErrors: Map<String, String> = emptyMap(),
    val nutritionPacePercentPerWeek: Double? = null,
    val ringsBatteriesPreview: GlobalBatteries? = null,
    val ringsPreviewLoading: Boolean = false,
    val ringsPreviewError: String? = null,
    val dialog: SetupWizardDialog = SetupWizardDialog.NONE,
    val isSavingAndExiting: Boolean = false,
    val exitCompleted: Boolean = false,
    /** Message of the last failed operation; cleared on the next successful publish. */
    val lastFailure: String? = null,
    /**
     * Cobertura publicada por el preview de RINGS (`SetupRingsPreview.coverage`).
     * Es la única fuente que consumen Home y Revisión: nunca se deriva una
     * etiqueta global mezclando canales parciales (un `INCOMPLETE` histórico
     * convive con un `PARTIAL_CHECK_IN` válido y con «Sin calibrar» explícito).
     */
    val ringsCoveragePreview: RingsCoverage? = null,
    /** Preparación nutricional REAL del alta (días, gastos y errores), cuando existe. */
    val nutritionPreparation: SetupNutritionPreparationResult? = null,
) {
    val showNutritionPreview: Boolean get() = nutritionDraft != null
    val nutritionDraft: NutritionWizardDraft? get() = draft.nutritionDraft
    val currentStep: SetupStepId get() = draft.stepProgress.currentStepId
    val currentBlock get() = draft.stepProgress.block
    val completedBlocks get() = draft.stepProgress.completedBlocks
    val stepValidation: List<SetupFieldCheck>
        get() = SetupWizardValidation.validateStep(draft, draft.stepProgress.currentStepId)
    val globalValidation: List<SetupFieldCheck>
        get() = SetupWizardValidation.validateAll(draft)
    /** Continuar is usable when the step is not blocking and no write is in flight. */
    val canConfirmStep: Boolean
        get() = !isSubmittingAnswer && !isSavingAndExiting && stepValidation.none { it.isBlocking }
    /** The step is valid once its values are present; it never blocks on already-answered steps. */
    val hasBlockingError: Boolean
        get() = stepValidation.any { it.isBlocking }
}

/**
 * Tarjeta de un plan viable en el paso PLAN (C.P5).
 *
 * - [title] es el `displayName` de la ficha editorial y [subtitle] su `PlanLabels.subtitle`
 *   («Ciclo de 4 semanas que se repite · Intermedio»); la tarjeta pinta ambos más los [reasons].
 * - La hoja «Cómo funciona» no sale de aquí: la tarjeta guarda el [id] y la hoja se arma con la entrada del
 *   catálogo y la semana real que entrega el asistente (`readyWeekSnapshotFor`).
 * - [description] (el resumen editorial) y [details] (la línea de atribución) NO los pinta ninguna pantalla.
 *   Se conservan como contrato de datos porque dos pruebas los leen: `SetupExecutableAvailabilityMatrixTest`
 *   exige que un plan de autor publique su atribución en [details] y `SetupWizardOneDayCopyTest` revisa el
 *   texto de [description]. Pueden retirarse junto con esas dos aserciones.
 */
data class SetupPlanCandidate(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val source: String,
    val reasons: List<String> = emptyList(),
    val details: String? = null,
)

/**
 * Paquete A · D2 (B-01): la selección del paso PLAN que dejó de ser viable (ver
 * [SetupWizardState.droppedSelection]).
 *
 * - [planId] es el plan elegido y [title] su nombre visible (nunca un id crudo).
 * - [rejection] es el rechazo de ESE plan en el barrido que lo dejó fuera; es null cuando el plan ni
 *   siquiera se evaluó (el planificador ya lo descartó por objetivo, nivel o días).
 */
data class SetupDroppedSelection(
    val planId: String,
    val title: String,
    val rejection: SetupCandidateRejection?,
)

/**
 * Motivo «encaja con tu semana» de una tarjeta de plan. Con un solo día la
 * frase entera cambia de forma («tu semana de 1 día»), no solo el sustantivo;
 * por eso se elige la frase completa y no se concatena «días» a un número.
 */
internal fun weekFitReason(days: Int): String = SpanishPlurals.choose(
    days,
    "Encaja con tu semana de 1 día",
    "Encaja con tus $days días por semana",
)

/**
 * C4 · Línea de registro (logcat, etiqueta `SetupPlanSweep`) del barrido de
 * candidatos del paso PLAN. Solo medición para decidir si hace falta optimizar:
 * milisegundos y contadores, sin datos personales.
 *
 * - `catalogMs`: carga del catálogo de ejercicios (≈0 si ya estaba cargado).
 * - `sweepMs`: evaluación de los candidatos; `totalMs` = ambos.
 * - `evaluated`: candidatos materializados de verdad; `cacheHits`: reutilizados.
 * - `firstSweep`: primer barrido de este ViewModel; `catalogWasLoaded=false`
 *   indica arranque en frío del catálogo.
 * - `passes` > 1 indica el segundo pase adaptado a peso corporal.
 */
internal fun candidateSweepLogLine(
    totalMs: Long,
    catalogMs: Long,
    sweepMs: Long,
    catalogWasLoaded: Boolean,
    firstSweep: Boolean,
    published: Int,
    evaluated: Int,
    cacheHits: Int,
    passes: Int,
    viable: Int,
    useAdapted: Boolean,
): String =
    "barrido de candidatos: totalMs=$totalMs catalogMs=$catalogMs sweepMs=$sweepMs " +
        "catalogoYaCargado=$catalogWasLoaded primerBarrido=$firstSweep publicados=$published " +
        "evaluados=$evaluated aciertosCache=$cacheHits pases=$passes viables=$viable adaptado=$useAdapted"

/**
 * T-001 / AC-T001-02 — Etapa de descarte de UN candidato publicado.
 *
 * La taxonomía es la que la UI necesita distinguir: `CATALOG` (no se pudo
 * leer el catálogo), `MATERIAL` (la receta exige material no declarado),
 * `DURATION` (la sesión estimada supera el tiempo pedido), `PROFILE`/`FREQUENCY`
 * (nivel o frecuencia no curados), `COMPOSITION` (composición/volumen) y
 * `MATERIALIZATION` (fallo del motor sin causa tipada). Nunca un único cubo
 * genérico de «material».
 */
enum class SetupCandidateRejectionStage {
    CATALOG,
    PROFILE,
    FREQUENCY,
    MATERIAL,
    MATERIALIZATION,
    DURATION,
    COMPOSITION,
}

/**
 * Rechazo ESTRUCTURADO de un candidato (T-001 / AC-T001-02).
 *
 * - [planId] es el ID del candidato evaluado; `null` sólo en etapas globales
 *   (`CATALOG`, cuando falló la carga y no hubo candidato que evaluar).
 * - [reason] es conciso y conserva la clase + mensaje útiles de la excepción
 *   cuando la hubo; sin datos personales y sin volcar el borrador entero.
 */
data class SetupCandidateRejection(
    val planId: String?,
    val stage: SetupCandidateRejectionStage,
    val reason: String,
    /** §15.2 closed reason code; null only for legacy rejections without code. */
    val reasonCode: PlanRejectionReason? = null,
    /** Slots/configuraciones afectadas del rechazo (diagnóstico, sin datos personales). */
    val affectedSlots: List<String> = emptyList(),
    /** Capacidades que faltan (p. ej. `strength`,`power`,`cardio`). */
    val missingCapabilities: List<String> = emptyList(),
    /** Minutos que este plan necesita cuando la causa es de duración. */
    val requiredMinutes: Int? = null,
    /** true cuando hace falta confirmar un aparato/soporte en el panel. */
    val needsApparatusConfirmation: Boolean = false,
    /** Clave curada a confirmar cuando [needsApparatusConfirmation]. */
    val apparatusKey: String? = null,
    /**
     * Paquete A · B1: tokens de material (`rack`, `bench`, `barbell`…) que el motor negó o no pudo
     * confirmar. Vacío si el rechazo no es de aparatos o el motor no los informó; con ellos la
     * [apparatusKey] sale de `SetupApparatusPanel.keyForToken` y ya no se lee el texto de [reason].
     */
    val missingRequirements: List<String> = emptyList(),
    /**
     * Paquete A · C3: reparaciones de UN toque que el asesor ([com.example.kpkn.domain.onboarding.PlanRepairAdvisor])
     * probó y dejan LISTO el plan propio del objetivo, en el orden en que se aplican (vacía = ninguna). Solo el
     * rechazo del plan PROPIO las lleva; la UI las ofrece como botón («Sí, tengo rack y banco», «Cambiar a Músculo»,
     * «Ajustar a N min»…) y las aplica con `applyRepairs`, que es lo que el asesor probó.
     */
    val repairs: List<PlanRepair> = emptyList(),
)

/**
 * T-005 / AC-T005-06 (§15.2): conteos reales del último barrido. Se muestran
 * «evaluados / viables / no viables», nunca «publicados» para un subconjunto
 * que ya pasó filtros.
 */
data class SetupCandidateCounts(
    val evaluated: Int = 0,
    val viable: Int = 0,
    val nonViable: Int = 0,
)

/**
 * Fallo de materialización CAUSADO en una etapa conocida del pipeline. Sigue
 * siendo `IllegalStateException`, así ningún caller existente que capture ese
 * tipo cambia de comportamiento; aporta la etapa para que el informe por
 * candidato no la pierda en un `catch` genérico.
 */
class SetupCandidateFailureException(
    val stage: SetupCandidateRejectionStage,
    message: String,
) : IllegalStateException(message)

typealias SetupWizardUiState = SetupWizardState

data class SetupPreview(val program: Program?, val report: PersonalizationReport?)

object SetupWizardValidation {
    fun validate(draft: SetupWizardDraft, chapter: SetupWizardChapter): Map<String, String> = buildMap {
        when (chapter) {
            SetupWizardChapter.PROFILE -> {
                if (draft.ageYears == null && draft.birthDateIso == null) put("age", "Añade tu edad o fecha de nacimiento")
                draft.ageYears?.let { if (it !in 13..100) put("age", "La edad debe estar entre 13 y 100 años") }
                draft.birthDateIso?.let { value ->
                    val date = runCatching { LocalDate.parse(value) }.getOrNull()
                    if (date == null || date.isAfter(LocalDate.now())) put("birthDate", "Usa una fecha válida")
                    else if (date.plusYears(13).isAfter(LocalDate.now())) put("birthDate", "Debes tener al menos 13 años")
                }
                if (draft.experience == null) put("experience", "Elige tu experiencia")
            }
            SetupWizardChapter.TRAINING -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.trainingPath == null && draft.programRoute == SetupProgramRoute.CUSTOMIZABLE) put("path", "Elige cómo quieres empezar")
                if (draft.goal == null) put("goal", "Elige un objetivo")
            }
            SetupWizardChapter.VOLUME -> {
                val answers = draft.volumeAnswers
                if (answers.style == null) put("volumeStyle", "Elige el estilo de referencia")
                if (answers.technique == null) put("volumeTechnique", "Indica tu técnica actual")
                if (answers.consistency == null) put("volumeConsistency", "Indica tu consistencia actual")
                if (answers.strength == null) put("volumeStrength", "Indica tu fuerza actual")
                if (answers.mobility == null) put("volumeMobility", "Indica tu movilidad actual")
            }
            SetupWizardChapter.WEEK -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.daysPerWeek == null) put("days", "Elige los días que quieres entrenar")
                if (draft.minutesPerSession == null) put("minutes", "Indica el tiempo disponible")
                if (draft.equipment.isEmpty()) put("equipment", "Elige al menos un perfil de equipo")
                if (draft.selectedWeekdays.size != draft.daysPerWeek || draft.selectedWeekdays.any { it !in 1..7 }) put("week", "Selecciona ${SpanishPlurals.days(draft.daysPerWeek ?: 0)} en tu semana")
                if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
                    val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                    if (selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() }) put("sessions", "Completa todas las sesiones que programaste")
                }
            }
            SetupWizardChapter.NUTRITION -> Unit
            SetupWizardChapter.RINGS, SetupWizardChapter.REVIEW -> Unit
        }
    }

    fun validateDate(value: String?, today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { value?.let(LocalDate::parse) }.getOrNull() ?: return false
        return !date.isAfter(today) && !date.plusYears(13).isAfter(today)
    }

    /**
     * Per-step validation distinguishing absent, invalid, declared and estimated
     * values. Ranges come from [WizChatValidation.validate]; nothing is defaulted
     * silently.
     */
    fun validateStep(draft: SetupWizardDraft, step: SetupStepId): List<SetupFieldCheck> {
        fun absent(key: String, message: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, SetupValueState.ABSENT, message))
        fun invalid(key: String, message: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, SetupValueState.INVALID, message))
        fun ok(key: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, valueStateOf(draft, step)))
        fun choice(key: String, value: String?, absentMessage: String): List<SetupFieldCheck> {
            if (value.isNullOrBlank()) return if (draft.isAnswered(step)) ok(key) else absent(key, absentMessage)
            val question = SetupStepGraph.questionForStep(step)?.let(WizChatGraph::question)
            val error = question?.let { WizChatValidation.validate(it, text = value) }
            return if (error != null) invalid(key, error) else ok(key)
        }
        fun number(key: String, value: Double?, absentMessage: String): List<SetupFieldCheck> {
            // El texto crudo manda: si existe, el número válido es el que el
            // usuario está escribiendo ahora, no el tipado anterior.
            val raw = draft.inputTexts[step.name]
            val parsedRaw = raw?.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) }
            if (raw != null && raw.isNotBlank() && parsedRaw == null) return invalid(key, "Escribe un número válido")
            val effective = parsedRaw ?: value
            if (effective == null) {
                // Pasos opcionales: omitir de forma explícita (registro de
                // respuesta en la confirmación) habilita Continuar sin fabricar dato.
                val skippable = SetupStepDefinitions.of(step)?.allowSkip == true
                return if (skippable && draft.isAnswered(step)) ok(key) else absent(key, absentMessage)
            }
            if (!effective.isFinite()) return invalid(key, "Escribe un número válido")
            // Enteros exactos donde el decimal no significa nada.
            if ((step == SetupStepId.AGE || step == SetupStepId.SESSION_TIME) && effective % 1.0 != 0.0) {
                return invalid(key, "Escribe un número entero sin decimales")
            }
            // Rangos: manda el catálogo ([SetupStepDefinitions]); la regla legacy
            // solo cubre pasos sin rango propio (compatibilidad de lectura).
            val definitionRange = SetupStepDefinitions.of(step)?.range
            if (definitionRange != null) {
                if (effective !in definitionRange.min..definitionRange.max) {
                    return invalid(
                        key,
                        "Usa un valor entre ${definitionRange.min.toInt()} y ${definitionRange.max.toInt()} ${definitionRange.unit.orEmpty()}.".trim(),
                    )
                }
            } else {
                val question = SetupStepGraph.questionForStep(step)?.let(WizChatGraph::question)
                val error = question?.let { WizChatValidation.validate(it, number = effective) }
                if (error != null) return invalid(key, error)
            }
            return ok(key)
        }
        return when (step) {
            SetupStepId.NAME -> when {
                draft.name.isBlank() && !draft.isAnswered(step) -> absent("name", "Escribe un nombre o usa omitir")
                draft.name.length > 32 -> invalid("name", "Usa hasta 32 caracteres")
                else -> ok("name")
            }
            SetupStepId.GENDER -> if (draft.isAnswered(step) || draft.profileGender != null) ok("profileGender")
                else absent("profileGender", "Elige con qué género te identificas o usa omitir")
            SetupStepId.AGE -> number("age", draft.ageYears?.toDouble(), "Añade tu edad o fecha de nacimiento")
            SetupStepId.HEIGHT -> number("height", draft.heightCm, "Indica tu estatura")
            SetupStepId.WEIGHT -> number("weight", draft.weightKg, "Indica tu peso")
            SetupStepId.EQUATION_SEX -> when {
                draft.nutritionDraft?.equationSex != null -> ok("equationSex")
                // «No lo sé» solo es válido con nutrición a mano: la omisión es
                // una elección explícita del usuario, nunca un default.
                "unknown" in draft.selectedValues(step) -> ok("equationSex")
                else -> absent("equationSex", "Elige tu sexo de cálculo o usa «No lo sé»")
            }
            SetupStepId.BODY_FAT -> {
                val raw = draft.inputTexts[step.name]
                val parsedRaw = raw?.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) }
                val percent = draft.bodyFatPercent
                val source = draft.bodyFatSource
                when {
                    raw != null && raw.isNotBlank() && parsedRaw == null -> invalid("bodyFat", "Escribe un porcentaje válido")
                    (parsedRaw ?: percent)?.let { it !in 3.0..60.0 } == true -> invalid("bodyFat", "Usa un porcentaje entre 3 y 60 %")
                    // Medido o visual SIN percentil no es válido nunca: ni con
                    // respuesta vieja ni con omisión previa registrada.
                    (source == SetupBodyFatSource.MEASURED || source == SetupBodyFatSource.VISUAL_ESTIMATE) &&
                        percent == null && parsedRaw == null -> absent("bodyFat", "Indica tu grasa corporal")
                    // Sin ninguna acción explícita (mover o tocar la figura, escribir una
                    // medición u omitir) y sin un dato previo en Ajustes no se avanza: la
                    // figura de arranque (≈25 %) no es una respuesta y nunca se guarda sola.
                    draft.bodyFatState() == SetupBodyFatState.PENDING -> absent("bodyFat", BODY_FAT_PENDING_MESSAGE)
                    // Fuente desconocida o omitida explícitamente: sin percentil fabricado.
                    else -> ok("bodyFat")
                }
            }
            SetupStepId.EXPERIENCE -> choice("experience", draft.experience?.label, "Elige tu experiencia")
            SetupStepId.MILESTONE_BASICS, SetupStepId.MILESTONE_TRAINING,
            SetupStepId.MILESTONE_NUTRITION, SetupStepId.MILESTONE_RINGS -> emptyList()
            SetupStepId.ROUTE -> if (draft.isAnswered(step)) ok("programRoute") else absent("programRoute", "Elige cómo quieres empezar")
            SetupStepId.GOAL -> if (draft.goal != null) ok("goal") else absent("goal", "Elige un objetivo")
            SetupStepId.STYLE -> if (draft.volumeAnswers.style != null) ok("volumeStyle")
                else absent("volumeStyle", "Elige el estilo de referencia")
            SetupStepId.VOLUME_TECHNIQUE -> if (draft.volumeAnswers.technique != null) ok("volumeTechnique")
                else absent("volumeTechnique", "Indica tu técnica actual")
            SetupStepId.VOLUME_CONSISTENCY -> if (draft.volumeAnswers.consistency != null) ok("volumeConsistency")
                else absent("volumeConsistency", "Indica tu consistencia actual")
            SetupStepId.VOLUME_STRENGTH -> if (draft.volumeAnswers.strength != null) ok("volumeStrength")
                else absent("volumeStrength", "Indica tu fuerza actual")
            SetupStepId.VOLUME_MOBILITY -> if (draft.volumeAnswers.mobility != null) ok("volumeMobility")
                else absent("volumeMobility", "Indica tu movilidad actual")
            SetupStepId.EQUIPMENT -> if (draft.trainingEnvironment.isNullOrBlank()) absent("equipment", "Elige dónde sueles entrenar")
                else ok("equipment")
            SetupStepId.AVAILABILITY -> if (draft.trainingOptions.availability == null) {
                absent("availability", "Elige el material que tienes o marca solo peso corporal")
            } else {
                ok("availability")
            }
            // Inventario con pesos: el asistente ya no lo pregunta (D2.5). Los pasos
            // INVENTORY_* siguen en el enum solo para leer borradores guardados; fuera
            // de la ruta no validan nada.
            SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_PLATES,
            SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_KETTLEBELLS,
            SetupStepId.INVENTORY_MACHINES -> emptyList()
            SetupStepId.HOME_EQUIPMENT -> if (draft.equipment.isEmpty()) absent("equipment", "Elige al menos un perfil de equipo")
                else ok("equipment")
            SetupStepId.DAYS -> when {
                draft.daysPerWeek == null -> absent("days", "Elige los días que quieres entrenar")
                draft.daysPerWeek !in 1..6 -> invalid("days", "Elige entre 1 y 6 días")
                else -> ok("days")
            }
            SetupStepId.WEEKDAYS -> when {
                draft.selectedWeekdays.isEmpty() -> absent("week", "Selecciona tus días de entrenamiento")
                draft.daysPerWeek == null || draft.selectedWeekdays.size != draft.daysPerWeek ||
                    draft.selectedWeekdays.any { it !in 1..7 } ->
                    invalid("week", "Selecciona ${SpanishPlurals.days(draft.daysPerWeek ?: 0)} en tu semana")
                else -> ok("week")
            }
            SetupStepId.SESSION_TIME -> number("minutes", draft.minutesPerSession?.toDouble(), "Indica el tiempo disponible")
            SetupStepId.CARDIO_TYPE -> when (draft.cardioType) {
                null -> absent("cardioType", "Elige el tipo de cardio")
                // §15.1: BIKE_OUTDOOR exige acceso a bicicleta confirmado con
                // presencia si no consta; no se hereda de «Cardio» ni del resto
                // del material de gimnasio.
                CardioType.BIKE_OUTDOOR -> when (SetupApparatusPanel.presenceOf(
                    draft.trainingOptions.availability,
                    SetupApparatusPanel.OUTDOOR_BIKE_KEY,
                )) {
                    ApparatusPresence.PRESENT -> ok("cardioType")
                    ApparatusPresence.ABSENT -> invalid(
                        "cardioType",
                        "Elegiste bicicleta pero confirmaste que no tienes; elige otro cardio.",
                    )
                    ApparatusPresence.UNKNOWN -> invalid(
                        "cardioType",
                        "Confirma que tienes acceso a una bicicleta para elegir bicicleta al aire libre.",
                    )
                }
                else -> ok("cardioType")
            }
            SetupStepId.CARDIO_TIME -> if (draft.cardioMinutes != null) ok("cardioMinutes") else absent("cardioMinutes", "Indica los minutos de cardio")
            // Bolsa de orden: ≤5 puntos en total, ≤2 por músculo y SIN límite
            // al número de músculos (5×1 es válido). Vacío es válido: no es
            // obligatorio gastar los puntos.
            SetupStepId.PRIORITIES -> {
                val bag = draft.trainingOptions.orderPriorities
                when {
                    bag.values.any { it < 0 || it > 2 } -> invalid("priorities", "Máximo 2 puntos por músculo")
                    bag.values.sum() > 5 -> invalid("priorities", "Reparte 5 puntos en total")
                    else -> ok("priorities")
                }
            }
            // La ruta de protocolo fija su propio reparto: no se bloquea un paso
            // que la UI solo puede mostrar como informativo.
            SetupStepId.SPLIT -> when {
                draft.programRoute == SetupProgramRoute.PROTOCOL -> ok("split")
                draft.isAnswered(step) -> ok("split")
                else -> absent("split", "Elige tu reparto semanal")
            }
            SetupStepId.TRAINING_MAX -> if (draft.isAnswered(step)) ok("trainingMax") else absent("trainingMax", "Indica si conoces tus marcas")
            SetupStepId.TRAINING_MARKS -> {
                val marks = listOf(draft.powerliftingProfile?.squat1RM, draft.powerliftingProfile?.bench1RM, draft.powerliftingProfile?.deadlift1RM)
                when {
                    marks.all { it == null } -> absent("marks", "Añade al menos una marca, o vuelve y elige Todavía no")
                    marks.any { it != null && it !in 1.0..1000.0 } -> invalid("marks", "Usa valores válidos entre 1 y 1000 kg")
                    else -> ok("marks")
                }
            }
            // PROPOSE (el default del contrato) es una decisión válida: se
            // acepta al confirmar sin ningún touch sintético y nunca bloquea.
            SetupStepId.AUTOREGULATION -> ok("autoregulation")
            // AUTO exige confirmación explícita: un registro previo de
            // respuesta (isAnswered) NO sustituye `automaticConfirmed`; solo
            // salir de AUTO («Solo revisar») desbloquea sin confirmación.
            SetupStepId.AUTOREGULATION_CONFIRM -> when {
                draft.trainingOptions.autoregulationMode != AutoregulationMode.AUTO -> ok("autoregulationConfirm")
                draft.trainingOptions.automaticConfirmed -> ok("autoregulationConfirm")
                else -> absent("autoregulationConfirm", "Confirma el ajuste automático o elige Solo revisar")
            }
            // null = preset del plan, vacío = sin calentamiento explícito.
            SetupStepId.WARMUPS -> {
                val rows = draft.trainingOptions.warmup
                when {
                    rows == null || rows.isEmpty() -> ok("warmups")
                    rows.any { recipe ->
                        val percent = recipe.percent
                        val reps = recipe.reps
                        percent == null || !percent.isFinite() || percent <= 0.0 || percent > 100.0 ||
                            reps == null || reps !in 1..60
                    } -> invalid("warmups", "Cada paso necesita un porcentaje entre 1 y 100 y entre 1 y 60 repeticiones")
                    else -> ok("warmups")
                }
            }
            SetupStepId.PLAN -> if (draft.selectedCatalogId != null || draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) ok("plan")
                else absent("plan", "Elige un plan")
            SetupStepId.TRAINING_REVIEW, SetupStepId.NUTRITION_RESULT,
            SetupStepId.RINGS_RESULT, SetupStepId.REVIEW_ACTIVATE -> emptyList()
            SetupStepId.NUTRITION_START -> if (draft.isAnswered(step)) ok("nutritionStart") else absent("nutritionStart", "Elige una opción de nutrición")
            SetupStepId.NUTRITION_SEX -> if (draft.nutritionDraft?.equationSex != null) ok("equationSex")
                else absent("equationSex", "Elige el sexo que usamos solo para calcular tu energía")
            SetupStepId.NUTRITION_ELIGIBILITY -> {
                val selected = draft.selectedValues(step)
                val exclusive = SetupStepDefinitions.of(step)?.exclusiveValues.orEmpty()
                when {
                    selected.any { it in exclusive } && selected.size > 1 ->
                        invalid("eligibility", "«Ninguna de estas» y «No lo sé» no se pueden combinar")
                    selected.isEmpty() && !draft.isAnswered(step) -> absent("eligibility", "Elige al menos una opción")
                    else -> ok("eligibility")
                }
            }
            SetupStepId.NUTRITION_DIRECTION -> if (draft.nutritionDraft?.direction != null || draft.isAnswered(step)) ok("direction")
                else absent("direction", "Elige hacia dónde quieres llevar tu alimentación")
            SetupStepId.NUTRITION_RHYTHM -> if (draft.isAnswered(step)) ok("rhythm")
                else absent("rhythm", "Elige el ritmo de cambio")
            SetupStepId.NUTRITION_TARGET -> {
                val raw = draft.inputTexts[step.name]
                    ?: draft.nutritionDraft?.targetWeightText.orEmpty().ifBlank { null }
                val parsed = raw?.let { parseLocalizedNumber(it) }
                when {
                    raw != null && parsed == null -> invalid("targetWeight", "Escribe un peso válido")
                    parsed != null && parsed !in 20.0..500.0 -> invalid("targetWeight", "Usa un peso entre 20 y 500 kg")
                    raw == null && !draft.isAnswered(step) ->
                        absent("targetWeight", "Indica tu peso objetivo o usa omitir")
                    else -> ok("targetWeight")
                }
            }
            // Contexto de historia OBLIGATORIO-PERO-OPCIONAL en sus dos filas:
            // tender o máximo ausentes requiere respuesta u omisión explícita;
            // nunca se inventan registros de pesaje aquí.
            SetupStepId.NUTRITION_HISTORY_CONTEXT -> {
                val rawMax = draft.inputTexts[step.name]
                val parsed = rawMax?.takeIf { it.isNotBlank() }?.let { parseLocalizedNumber(it) }
                val maximum = draft.previousMaximumWeightKg
                when {
                    rawMax != null && rawMax.isNotBlank() && parsed == null -> invalid("historyMax", "Escribe un peso válido")
                    (parsed ?: maximum)?.let { it !in 20.0..500.0 } == true -> invalid("historyMax", "Usa un peso entre 20 y 500 kg")
                    draft.weightTrend == null && maximum == null && !draft.isAnswered(step) ->
                        absent("historyContext", "Declara la tendencia, el máximo anterior o usa omitir")
                    else -> ok("historyContext")
                }
            }
            SetupStepId.NUTRITION_ACTIVITY -> if (draft.isAnswered(step)) ok("activity") else absent("activity", "Indica qué tan activo eres")
            // «Yo traigo mis números»: dejar un campo vacío es válido (así lo
            // declara la UI); solo un texto no numérico crudo bloquea.
            SetupStepId.NUTRITION_MANUAL_CALORIES -> {
                val raw = draft.inputTexts[step.name]
                if (!raw.isNullOrBlank() && parseLocalizedNumber(raw) == null) invalid("calories", "Escribe un número válido")
                else ok("calories")
            }
            SetupStepId.NUTRITION_MANUAL_CARBS_FAT -> {
                val raw = draft.inputTexts[step.name]
                if (!raw.isNullOrBlank() && parseLocalizedNumber(raw) == null) invalid("carbs", "Escribe un número válido")
                else ok("carbs")
            }
            SetupStepId.NUTRITION_DISTRIBUTION -> if (draft.isAnswered(step)) ok("distribution")
                else absent("distribution", "Elige cómo repartir tu semana")
            SetupStepId.NUTRITION_WEIGH_INS -> {
                val rows = draft.historicalWeighIns
                val today = LocalDate.now()
                val broken = rows.firstOrNull { row ->
                    val date = runCatching { LocalDate.parse(row.dateIso) }.getOrNull()
                    date == null || date.isAfter(today) ||
                        !row.weightKg.isFinite() || row.weightKg !in 20.0..500.0
                }
                when {
                    broken != null -> invalid("weighIns", "Revisa la fecha y el peso de tus pesajes")
                    rows.isEmpty() && !draft.isAnswered(step) -> absent("weighIns", "Añade tus pesajes o usa omitir")
                    else -> ok("weighIns")
                }
            }
            SetupStepId.RINGS_START -> if (draft.isAnswered(step)) ok("startAction") else absent("startAction", "Elige cómo situar tus RINGS")
            SetupStepId.RINGS_RECENT -> when (draft.ringsAnswers?.recentTrainingState) {
                null, SetupRecentTrainingState.NOT_ANSWERED ->
                    if (draft.isAnswered(step)) ok("recentTraining")
                    else absent("recentTraining", "Indica si entrenaste en los últimos siete días")
                // «No lo sé» explícito (UNKNOWN) permite resultado parcial:
                // desconocer el historial no es no haber entrenado.
                else -> ok("recentTraining")
            }
            SetupStepId.RINGS_SESSIONS -> if (draft.ringsAnswers?.sessionsLastSevenDays != null) ok("sessions")
                else absent("sessions", "Indica cuántas sesiones hiciste")
            SetupStepId.RINGS_RECENCY -> if (draft.ringsAnswers?.lastSessionRecencyDays != null) ok("recency")
                else absent("recency", "Indica cuándo fue tu última sesión")
            SetupStepId.RINGS_ACTIVITY -> if (draft.ringsAnswers?.activityType != null) ok("activityType")
                else absent("activityType", "Indica qué predominó en tus sesiones")
            SetupStepId.RINGS_INTENSITY -> if (draft.ringsAnswers?.intensityLevel != null) ok("intensity")
                else absent("intensity", "Indica cómo sentiste la intensidad")
            SetupStepId.RINGS_AXIAL -> if (draft.isAnswered(step)) ok("axial") else absent("axial", "Indica si hubo cargas pesadas para la espalda")
            // Sensaciones con `allowSkip`: «No lo sé» o una omisión explícita
            // habilita Continuar sin fabricar un nivel 0. Un check-in parcial
            // (historial incompleto) nunca se bloquea aquí: el mapper decide
            // INCOMPLETE/PARTIAL y este validador solo exige el dato del control.
            SetupStepId.RINGS_MUSCLE_FEELING -> if (draft.ringsAnswers?.muscleFeeling != null || draft.isAnswered(step)) ok("muscleFeeling")
                else absent("muscleFeeling", "Indica cómo se sienten tus músculos")
            SetupStepId.RINGS_ENERGY_FEELING -> if (draft.ringsAnswers?.energy != null || draft.isAnswered(step)) ok("energy")
                else absent("energy", "Indica cómo está tu energía")
            SetupStepId.RINGS_STRUCTURE_FEELING -> if (draft.ringsAnswers?.structureFeeling != null || draft.isAnswered(step)) ok("structureFeeling")
                else absent("structureFeeling", "Indica cómo está tu columna")
            SetupStepId.RINGS_DISCOMFORT -> when (draft.ringsAnswers?.discomfortState) {
                null, SetupDiscomfortState.NOT_ANSWERED ->
                    if (draft.isAnswered(step)) ok("discomfort")
                    else absent("discomfort", "Indica si hay alguna molestia o si prefieres omitirlo")
                // NONE/DECLARED/OMITTED son respuestas explícitas conservadas.
                else -> ok("discomfort")
            }
        }
    }

    /** Global validation: every step of the current route plus equation inputs. */
    fun validateAll(draft: SetupWizardDraft): List<SetupFieldCheck> =
        SetupStepGraph.stepIds(draft.stepContext()).flatMap { validateStep(draft, it) } + missingEquationInputs(draft)

    /**
     * Data an equation needs but the draft does not provide. These are never
     * replaced by defaults: the wizard must ask again or keep the value pending.
     */
    fun missingEquationInputs(draft: SetupWizardDraft): List<SetupFieldCheck> {
        if (!draft.includeNutrition || draft.nutritionMode != "create") return emptyList()
        val nutrition = draft.nutritionDraft
        // Objetivos propios o solo registro: no hay cadena EER que exigir.
        if (nutrition != null && nutrition.configurationMode != NutritionConfigurationMode.AUTOMATIC) return emptyList()
        return buildList {
            if (draft.weightKg == null && nutrition?.weightText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.WEIGHT, "equation.weight", SetupValueState.MISSING_EQUATION_INPUT,
                    "El peso es necesario para calcular el gasto energético"))
            if (draft.heightCm == null && nutrition?.heightText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.HEIGHT, "equation.height", SetupValueState.MISSING_EQUATION_INPUT,
                    "La estatura es necesaria para calcular el gasto energético"))
            if (draft.ageYears == null && nutrition?.ageText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.AGE, "equation.age", SetupValueState.MISSING_EQUATION_INPUT,
                    "La edad es necesaria para calcular el gasto energético"))
            if (nutrition?.equationSex == null) add(
                SetupFieldCheck(SetupStepId.NUTRITION_SEX, "equation.sex", SetupValueState.MISSING_EQUATION_INPUT,
                    "El sexo de cálculo es necesario para la ecuación de energía"))
        }
    }

    /**
     * Respuesta visible ANTES de la primera confirmación: registro confirmado,
     * selección actual o texto crudo escrito. Sin esto, cualquier rama que
     * valide `answered` se bloquearía a sí misma (deadlock) porque los
     * registros solo nacen en `confirmCurrentStep`/`skipStep`.
     */
    private fun SetupWizardDraft.isAnswered(step: SetupStepId): Boolean =
        step in stepProgress.answers ||
            !stepSelections[step].isNullOrEmpty() ||
            inputTexts[step.name]?.isNotBlank() == true ||
            (SetupStepGraph.questionForStep(step)?.let { question ->
                wizChat.acceptedAnswers.any { it.questionId == question }
            } == true)

    private fun valueStateOf(draft: SetupWizardDraft, step: SetupStepId): SetupValueState =
        when (draft.stepProgress.answers[step]) {
            SetupAnswerProvenance.USER_DECLARED -> SetupValueState.DECLARED
            SetupAnswerProvenance.SUGGESTED, SetupAnswerProvenance.DERIVED -> SetupValueState.ESTIMATED
            else -> if (draft.isAnswered(step)) SetupValueState.DECLARED else SetupValueState.ESTIMATED
        }
}
