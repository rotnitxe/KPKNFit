package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceMetadata
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.templates.SessionTemplateEngine
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A movement eligible for a 1RM test, keyed by its exact catalog configuration. */
data class LoopTestMovement(
    val id: String,
    val configurationId: String,
    val exerciseId: String,
    val sourceSessionId: String,
    val sessionName: String,
    val dayOfWeek: Int?,
    val scheduleLabel: String?,
    val placeId: String?,
    val exerciseName: String,
    val referenceKg: Double?,
    val quantityConvention: LoadQuantityConvention,
    val exercise: Exercise,
)

/** Read-only preview data shown before a loop preset is saved. */
data class LoopPresetPreview(
    val presetId: String,
    val sourceWeekId: String?,
    val sourceWeekName: String?,
    val sourceSessions: List<Session>,
    val deloadSessions: List<Session>,
    val sourceWorkingSets: Int,
    val deloadWorkingSets: Int,
    val testMovements: List<LoopTestMovement>,
    val cadenceConflicts: List<String>,
    val collisionNotes: List<String> = emptyList(),
)

/** Pure preparation of real deload and 1RM test sessions for the simple-program loop presets. */
object LoopPresetEngine {
    private const val DELOAD_CADENCE = 4
    private const val TEST_CADENCE = 8
    private const val DEFAULT_WEIGHT_STEP_KG = AuthoredProgressionEngine.DEFAULT_ROUNDING_STEP_KG

    fun preview(
        program: Program,
        presetId: String,
        equipmentInventory: EquipmentInventory = EquipmentInventory(),
    ): LoopPresetPreview {
        val source = lastTrainableBaseWeek(program)
        val deload = source?.sessions.orEmpty().map { deloadSession(it, equipmentInventory) }
        val cadence = cadencesFor(presetId)
        val cadenceConflicts = cadence.mapNotNull { requested ->
            program.loops.firstOrNull { it.repeatEveryXLoops.coerceAtLeast(1) == requested }?.let { existing ->
                "Cada $requested ciclos ya lo usa «${existing.title.ifBlank { LoopEngine.getLoopTypeLabel(existing.type) }}». Edítalo o elimínalo antes de aplicar el preset."
            }
        }
        val existingIds = program.loops.mapTo(mutableSetOf()) { it.id }
        fun previewId(base: String): String {
            var candidate = base
            while (candidate in existingIds) candidate += "_"
            return candidate
        }
        val requestedLoops = buildList {
            if (presetId == "deload-4" || presetId == "deload-1rm") {
                add(Loop(previewId("__preview_deload__"), "Descarga", LoopType.DELOAD, DELOAD_CADENCE, priority = 100))
            }
            if (presetId == "1rm-8" || presetId == "deload-1rm") {
                add(Loop(previewId("__preview_1rm__"), "Test 1RM", LoopType.ONE_RM_TEST, TEST_CADENCE, priority = 50))
            }
        }
        val collisions = LoopEngine.detectLoopCollisions(
            LoopEngine.projectLoops(
                program.copy(loops = program.loops + requestedLoops),
                fromCycle = LoopEngine.getCurrentCycle(program).coerceAtLeast(0),
                lookAheadCycles = 24,
            ),
        )
        val externalLoopConflicts = collisions.entries
            .filter { (_, projections) -> projections.any { it.loop.id in existingIds } }
            .map { (cycle, projections) ->
                "C$cycle: coincide ${projections.joinToString(" + ") { it.loop.title.ifBlank { LoopEngine.getLoopTypeLabel(it.loop.type) } }} con un loop existente. Ajusta o elimina ese loop antes de aplicar el preset."
            }
        val collisionNotes = collisions.entries
            .filter { (_, projections) -> projections.none { it.loop.id in existingIds } }
            .map { (cycle, projections) ->
                "C$cycle: coinciden ${projections.joinToString(" y ") { it.loop.title.ifBlank { LoopEngine.getLoopTypeLabel(it.loop.type) } }}; se ordenan por prioridad, con Descarga antes del Test 1RM."
            }
        return LoopPresetPreview(
            presetId = presetId,
            sourceWeekId = source?.id,
            sourceWeekName = source?.name,
            sourceSessions = source?.sessions.orEmpty(),
            deloadSessions = deload,
            sourceWorkingSets = source?.sessions.orEmpty().sumOf(::workingSetCount),
            deloadWorkingSets = deload.sumOf(::workingSetCount),
            testMovements = source?.let { movementsOf(program, it) }.orEmpty(),
            cadenceConflicts = (cadenceConflicts + externalLoopConflicts).distinct(),
            collisionNotes = collisionNotes,
        )
    }

    /** Builds only after the person confirms the preview. All generated sessions already have new nested IDs. */
    fun materialize(
        program: Program,
        presetId: String,
        selectedMovementIds: Set<String>,
        attemptKgByMovement: Map<String, Double> = emptyMap(),
        equipmentInventory: EquipmentInventory = EquipmentInventory(),
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ): List<Loop> {
        require(LoopEngine.canAddNewLoop(program)) { "Esta rutina no permite añadir loops con la estructura actual." }
        val preview = preview(program, presetId, equipmentInventory)
        require(preview.sourceWeekId != null && preview.sourceSessions.isNotEmpty()) { "La rutina no tiene una semana base entrenable." }
        require(preview.cadenceConflicts.isEmpty()) { preview.cadenceConflicts.joinToString(" ") }
        val includeDeload = presetId == "deload-4" || presetId == "deload-1rm"
        val includeTest = presetId == "1rm-8" || presetId == "deload-1rm"
        val loops = mutableListOf<Loop>()
        if (includeDeload) {
            require(preview.deloadSessions.isNotEmpty() && preview.deloadSessions.any(::hasExecutableContent)) {
                "No se pudo preparar una descarga entrenable."
            }
            loops += Loop(
                id = idProvider(),
                title = "Descarga",
                type = LoopType.DELOAD,
                repeatEveryXLoops = DELOAD_CADENCE,
                priority = 100,
                sessions = preview.deloadSessions,
            )
        }
        if (includeTest) {
            val selected = preview.testMovements.filter { it.id in selectedMovementIds }
            require(selected.isNotEmpty()) { "Elige al menos un movimiento para el test 1RM." }
            val sessions = testSessionsOf(selected, attemptKgByMovement, equipmentInventory, idProvider)
            require(sessions.isNotEmpty() && sessions.any(::hasExecutableContent)) { "No se pudo preparar un test entrenable." }
            loops += Loop(
                id = idProvider(),
                title = "Test 1RM",
                type = LoopType.ONE_RM_TEST,
                repeatEveryXLoops = TEST_CADENCE,
                priority = 50,
                sessions = sessions,
            )
        }
        return loops
    }

    /** Last trainable base week; loop and rest weeks never become the source. */
    fun lastTrainableBaseWeek(program: Program): ProgramWeek? = program.macrocycles
        .firstOrNull()?.blocks?.firstOrNull()?.mesocycles.orEmpty()
        .flatMap { it.weeks }
        .lastOrNull { week ->
            !week.isLoopWeek && week.executionKind != WeekExecutionKind.REST &&
                week.sessions.any(::hasExecutableContent)
        }

    private fun cadencesFor(presetId: String): List<Int> = when (presetId) {
        "deload-4" -> listOf(DELOAD_CADENCE)
        "1rm-8" -> listOf(TEST_CADENCE)
        "deload-1rm" -> listOf(DELOAD_CADENCE, TEST_CADENCE)
        else -> emptyList()
    }

    private fun movementsOf(program: Program, week: ProgramWeek): List<LoopTestMovement> {
        val seen = mutableSetOf<String>()
        return week.sessions.flatMap { session ->
            session.allExercises().mapNotNull { exercise ->
                val configurationId = (exercise.catalogConfigurationId ?: exercise.exerciseDbId)
                    ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val isMain = exercise.isCompetitionLift || exercise.slotRole == SlotRole.T1_MAIN || exercise.trainingMode == TrainingMode.RM
                if (!isMain || exercise.trainingMode == TrainingMode.TIME || exercise.trainingMode == TrainingMode.DISTANCE) return@mapNotNull null
                if (!seen.add(configurationId)) return@mapNotNull null
                val reference = ExerciseTrainingMax.capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_1RM)
                    ?: exercise.reference1RM?.takeIf { it.isFinite() && it > 0.0 }
                LoopTestMovement(
                    id = configurationId,
                    configurationId = configurationId,
                    exerciseId = exercise.id,
                    sourceSessionId = session.id,
                    sessionName = session.name,
                    dayOfWeek = session.dayOfWeek,
                    scheduleLabel = session.scheduleLabel,
                    placeId = session.placeId,
                    exerciseName = exercise.name,
                    referenceKg = reference,
                    quantityConvention = exercise.loadQuantityConvention,
                    exercise = exercise,
                )
            }
        }
    }

    private fun deloadSession(source: Session, inventory: EquipmentInventory): Session {
        val cloned = SessionTemplateEngine.cloneSessionContent(source)
        fun scaleSession(session: Session): Session = session.copy(
            name = if (session.id == cloned.id) "${source.name} · Descarga" else session.name,
            exercises = session.exercises.map { deloadExercise(it, inventory) },
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map { deloadExercise(it, inventory) }) },
            sessionB = session.sessionB?.let(::scaleSession),
            sessionC = session.sessionC?.let(::scaleSession),
            sessionD = session.sessionD?.let(::scaleSession),
        )
        return scaleSession(cloned)
    }

    private fun deloadExercise(exercise: Exercise, inventory: EquipmentInventory): Exercise {
        val weightStepKg = equipmentStepKg(exercise, inventory)
        val originalSets = exercise.sets
        val count = if (originalSets.isEmpty()) 0 else PeriodizationEngine.scaleSets(originalSets.size, MesocycleGoal.DELOAD, null)
        val lowVolume = originalSets.size <= 2
        val scaledSets = originalSets.take(count).map { set ->
            val mode = set.loadModeV2 ?: when (exercise.loadQuantityConvention) {
                LoadQuantityConvention.ADDITIONAL_BODYWEIGHT -> LoadModeV2.LASTRE
                LoadQuantityConvention.ASSISTANCE -> LoadModeV2.ASSISTED
                else -> LoadModeV2.LOAD
            }
            val factor = when (mode) {
                LoadModeV2.LOAD, LoadModeV2.LASTRE -> 0.8
                LoadModeV2.ASSISTED -> 1.2
                LoadModeV2.BODYWEIGHT -> 1.0
            }
            val weight = set.weight?.takeIf { it.isFinite() && it > 0.0 }?.let { kg ->
                when (mode) {
                    LoadModeV2.LOAD, LoadModeV2.LASTRE -> floorToStep(kg * factor, weightStepKg)
                    LoadModeV2.ASSISTED -> ceilToStep(kg * factor, weightStepKg)
                    LoadModeV2.BODYWEIGHT -> kg
                }
            } ?: set.weight
            val percentage = set.targetPercentageRM?.takeIf { it.isFinite() && it > 0.0 }?.let { pct ->
                if (mode == LoadModeV2.BODYWEIGHT) pct else (pct * factor).coerceAtMost(100.0)
            } ?: set.targetPercentageRM
            val reps = set.targetReps?.let { reps -> if (lowVolume) (reps - 1).coerceAtLeast(1) else reps }
            val repRange = set.targetRepsRange?.let { range ->
                if (!lowVolume) range else range.copy(min = (range.min - 1).coerceAtLeast(1), max = (range.max - 1).coerceAtLeast(1))
            }
            val duration = set.targetDuration?.let { seconds ->
                if (lowVolume && seconds > 1) (seconds * 0.8).roundToInt().coerceAtLeast(1) else seconds
            }
            val unit = set.unitModeV2 ?: when (exercise.trainingMode) {
                TrainingMode.TIME -> UnitModeV2.TIME
                TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
                else -> UnitModeV2.REPS
            }
            val plannedTarget = set.plannedTargetV2?.let { target ->
                if (lowVolume && unit == UnitModeV2.TIME) (target * 0.8).coerceAtLeast(1.0) else target
            }
            set.copy(
                targetReps = reps,
                targetRepsRange = repRange,
                targetDuration = duration,
                targetRPE = (set.targetRPE ?: 8.0).minus(2.0).coerceIn(5.0, 6.0),
                targetRIR = ((set.targetRIR ?: 2) + 2).coerceAtMost(4),
                weight = weight,
                targetPercentageRM = percentage,
                plannedTargetV2 = plannedTarget,
            )
        }
        val cardio = exercise.cardioDetails?.let { details ->
            details.copy(
                targetDurationSeconds = details.targetDurationSeconds?.let { (it * 0.8).roundToInt().coerceAtLeast(1) },
                targetDistanceKm = details.targetDistanceKm?.let { it * 0.8 },
                intensityLevel = (details.resolvedIntensityLevel() - 2).coerceAtLeast(1),
                intensity = CardioIntensity.fromLevel((details.resolvedIntensityLevel() - 2).coerceAtLeast(1)),
                intervalBlocks = details.intervalBlocks.map { it.copy(
                    durationSeconds = (it.durationSeconds * 0.8).roundToInt().coerceAtLeast(1),
                    targetDistanceMeters = it.targetDistanceMeters?.let { meters -> meters * 0.8 },
                    intensityLevel = it.intensityLevel?.minus(2)?.coerceAtLeast(1),
                ) },
            )
        }
        return exercise.copy(
            sets = scaledSets,
            targetDurationMinutes = exercise.targetDurationMinutes?.let { (it * 0.8).roundToInt().coerceAtLeast(1) },
            cardioDetails = cardio,
        )
    }

    private fun testSessionsOf(
        movements: List<LoopTestMovement>,
        attemptKgByMovement: Map<String, Double>,
        inventory: EquipmentInventory,
        idProvider: () -> String,
    ): List<Session> = movements.groupBy { it.sourceSessionId }.mapNotNull { (sessionId, selected) ->
        val sourceSession = movements.firstOrNull { it.sourceSessionId == sessionId } ?: return@mapNotNull null
        val originalSession = selected.first()
        val clonedSession = SessionTemplateEngine.cloneSessionContent(
            // The movement stores no session object intentionally; recover its identity via the selected exercise's session fields.
            Session(
                id = sourceSession.sourceSessionId,
                name = sourceSession.sessionName,
                dayOfWeek = sourceSession.dayOfWeek,
                exercises = selected.map { it.exercise },
            ),
        )
        val testExercises = clonedSession.exercises.mapIndexedNotNull { index, clone ->
            val selectedMovement = selected.getOrNull(index) ?: return@mapIndexedNotNull null
            val attempt = attemptKgByMovement[selectedMovement.id]
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: selectedMovement.referenceKg
            val config = selectedMovement.configurationId
            val weightStepKg = equipmentStepKg(selectedMovement.exercise, inventory)
            val loadReference = if (selectedMovement.referenceKg != null) {
                PlanLoadReference(
                    kind = PlanLoadReferenceKind.EXERCISE_1RM,
                    configurationId = config,
                    quantityConvention = selectedMovement.quantityConvention,
                    state = PlanLoadReferenceState.CAPTURED,
                    capturedLoadKg = selectedMovement.referenceKg,
                )
            } else {
                PlanLoadReference(
                    kind = PlanLoadReferenceKind.EXERCISE_1RM,
                    configurationId = config,
                    quantityConvention = selectedMovement.quantityConvention,
                    state = PlanLoadReferenceState.PENDING,
                    metadata = PlanLoadReferenceMetadata(origin = "LOOP_1RM_TEST", reason = "Carga pendiente para un test 1RM"),
                )
            }
            val warmupRows = listOf(0.4 to 5, 0.6 to 3, 0.8 to 1).map { (fraction, reps) ->
                ExerciseSet(
                    id = idProvider(),
                    targetReps = reps,
                    weight = attempt?.times(fraction)?.let { floorToStep(it, weightStepKg) },
                    targetPercentageRM = fraction * 100.0,
                    loadBasis = LoadBasis.PERCENT_1RM,
                    loadModeV2 = LoadModeV2.LOAD,
                    unitModeV2 = UnitModeV2.REPS,
                )
            }
            val attemptSet = ExerciseSet(
                id = idProvider(),
                targetReps = 1,
                weight = attempt,
                targetPercentageRM = 100.0,
                loadBasis = LoadBasis.PERCENT_1RM,
                loadModeV2 = LoadModeV2.LOAD,
                unitModeV2 = UnitModeV2.REPS,
                isTopSet = true,
            )
            clone.copy(
                trainingMode = TrainingMode.RM,
                sets = warmupRows + attemptSet,
                warmupSets = emptyList(),
                reference1RM = selectedMovement.referenceKg,
                loadReference = loadReference,
            )
        }
        if (testExercises.isEmpty()) return@mapNotNull null
        Session(
            id = clonedSession.id,
            name = "Test 1RM · ${originalSession.sessionName}",
            description = "Tres series de aproximación y un intento final por movimiento.",
            exercises = testExercises,
            warmup = emptyList(),
            dayOfWeek = originalSession.dayOfWeek,
            scheduleLabel = originalSession.scheduleLabel,
            isMainSession = true,
            placeId = originalSession.placeId,
        )
    }

    private fun hasExecutableContent(session: Session): Boolean = SessionTemplateEngine.sessionHasExecutableContent(session)

    private fun workingSetCount(session: Session): Int = session.allExercises().sumOf { exercise -> exercise.sets.count { !it.isEmptySlot } }

    private fun equipmentStepKg(exercise: Exercise, inventory: EquipmentInventory): Double {
        val configurationId = exercise.catalogConfigurationId
        val machineStep = inventory.machines.firstOrNull { it.configurationId == configurationId }
            ?.incrementKg?.takeIf { it.isFinite() && it > 0.0 }
        if (machineStep != null) return machineStep
        if (exercise.loadQuantityConvention == LoadQuantityConvention.PER_IMPLEMENT) {
            val weights = inventory.dumbbells.filter { it.pairAvailable }
                .map { it.weightPerUnitKg }.filter { it.isFinite() && it > 0.0 }.distinct().sorted()
            val dumbbellStep = weights.zipWithNext { lower, upper -> upper - lower }
                .filter { it.isFinite() && it > 0.0 }.minOrNull()
            if (dumbbellStep != null) return dumbbellStep
        }
        val barbellStep = AuthoredProgressionEngine.roundingStepKg(inventory)
        return barbellStep.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_WEIGHT_STEP_KG
    }

    private fun floorToStep(value: Double, stepKg: Double): Double = floor(value / stepKg) * stepKg
    private fun ceilToStep(value: Double, stepKg: Double): Double = ceil(value / stepKg) * stepKg
}
