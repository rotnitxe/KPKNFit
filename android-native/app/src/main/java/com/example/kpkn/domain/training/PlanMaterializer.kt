package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.displayName
import com.example.kpkn.data.protocols.executionCue
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.calculations.PlateCalculator
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.workout.BaseLoadPolicy
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.domain.workout.warmupValidationMessages
import kotlin.math.abs

enum class WarmupLoadStatus {
    /** Carga real disponible respetando el inventario. */
    READY,
    /** Sin referencia de carga: porcentaje pendiente, nunca kg inventados. */
    PENDING_PERCENT,
    /** Peso corporal / asistido: sin % de carga externa ficticia. */
    NOT_APPLICABLE_LOAD_MODE,
    /** Aproximación ya reportada al calibrador. */
    COMPLETED,
    /** Carga repetida por redondeo/inventario: se evita duplicarla. */
    DEDUPED,
}

data class WarmupLoadEntry(
    val definition: WarmupSetDefinition,
    val status: WarmupLoadStatus,
    val requestedKg: Double?,
    val realizedKg: Double?,
    val isExact: Boolean,
)

data class WarmupLoadPlan(
    val entries: List<WarmupLoadEntry>,
    /** Avisos de WarmupRules: son umbrales de advertencia, nunca límites duros. */
    val validationMessages: List<String>,
    /** Nota de WarmupCalibrationEngine (±2,5 % por reporte, tope 5 %). */
    val calibrationNote: String?,
    /**
     * Viabilidad honesta de estas aproximaciones contra el inventario finito
     * (viaja con el plan real ya resuelto, así el consumidor nunca tiene que
     * asumir material ilimitado): UNKNOWN = sin carga de trabajo o sin
     * inventario → porcentaje pendiente, jamás 0 kg.
     * Mide el inventario (alcanzan los discos el objetivo), sin pisar el piso
     * de [BaseLoadPolicy] ni el dedupe de [entries], que ya resuelven la carga
     * efectiva.
     */
    val feasibility: WarmupFeasibility = WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, null, null),
)

object PlanMaterializer {
    fun materialize(
        program: Program,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        profile: PowerliftingProfile? = null,
        strict: Boolean = true,
        extraExemptions: List<com.example.kpkn.data.protocols.RecipeCompositionExemption> = emptyList(),
        sourceProtocolId: String? = program.sourceProtocolId,
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): Program {
        require(options.autoregulationMode != AutoregulationMode.AUTO || options.automaticConfirmed) {
            "La autorregulación AUTO requiere confirmación explícita del usuario (SetupTrainingOptions.automaticConfirmed)."
        }
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata, extraExemptions)
        if (hard.isNotEmpty()) {
            val message = hard.joinToString("\n") { "${it.rule} ${it.scope}: ${it.message}" }
            if (strict) error("Receta '${recipe.id}' no pasa composición:\n$message")
        }
        val resolvedProfile = hydrateProfile(program, profile, recipe.trainingMaxPercent)
        val startDay = program.resolvedSchedulePlan().weekStartDay ?: program.startDay ?: 1
        // El plan es nativo SOLO si la receta que se va a materializar es la suya
        // (nunca una receta de autor aplicada encima): la base del autor se preserva.
        val nativeCurate = program.isNativeCuratedRecipe(recipe)
        // Manda la elección persistida por el usuario (Program.planWarmupConfig);
        // options solo aporta configuración cuando el programa todavía no guarda nada.
        val planWarmupSteps = effectivePlanWarmupSteps(program, options)
        val splitId = program.selectedSplitId
        val splitPattern = splitId?.let { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id }?.pattern }
        val trainingDays = splitPattern?.let { SplitApplicationEngine.patternToTrainingDays(it, startDay) }
            ?.map { it.dayOfWeek }
        val byBlock = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
        val blocks = byBlock.map { (_, weeks) ->
            val head = weeks.first()
            val mesoGoal = when (head.blockGoal) {
                com.example.kpkn.data.models.BlockGoal.ACCUMULATION, com.example.kpkn.data.models.BlockGoal.DENSITY -> MesocycleGoal.ACCUMULATION
                com.example.kpkn.data.models.BlockGoal.INTENSIFICATION, com.example.kpkn.data.models.BlockGoal.SPECIFICITY -> MesocycleGoal.INTENSIFICATION
                com.example.kpkn.data.models.BlockGoal.REALIZATION, com.example.kpkn.data.models.BlockGoal.PEAK -> MesocycleGoal.REALIZATION
                com.example.kpkn.data.models.BlockGoal.DELOAD, com.example.kpkn.data.models.BlockGoal.TAPER -> MesocycleGoal.DELOAD
                com.example.kpkn.data.models.BlockGoal.CUSTOM -> MesocycleGoal.CUSTOM
            }
            val scheme = when (head.blockGoal) {
                com.example.kpkn.data.models.BlockGoal.TAPER, com.example.kpkn.data.models.BlockGoal.DELOAD -> BlockProgressionScheme.PERCENT_RM
                com.example.kpkn.data.models.BlockGoal.PEAK, com.example.kpkn.data.models.BlockGoal.REALIZATION -> BlockProgressionScheme.RPE_CAP
                else -> BlockProgressionScheme.PERCENT_RM
            }
            Block(
                id = idProvider.newId(),
                name = head.blockName.ifBlank { "Bloque ${head.blockIndex + 1}" },
                goal = head.blockGoal,
                progressionScheme = scheme,
                sourceDefinitionId = recipe.id,
                // Origen real del contenido: si se materializa la receta propia de un
                // plan nativo, el bloque sigue curado por el motor nativo (así una
                // rematerialización posterior vuelve a reconocerlo); cualquier receta
                // ajena se atribuye a ella misma, nunca como nativa.
                prescriptionOrigin = if (nativeCurate) KPKN_NATIVE_CURATED_ORIGIN else recipe.id,
                mesocycles = listOf(
                    Mesocycle(
                        id = idProvider.newId(),
                        name = head.blockName.ifBlank { "Mesociclo" },
                        goal = mesoGoal,
                        weeks = weeks.sortedBy { it.weekNumber }.map { week ->
                            materializeWeek(
                                week,
                                recipe,
                                metadata,
                                idProvider,
                                resolvedProfile,
                                trainingDays,
                                startDay,
                                planWarmupSteps,
                                nativeCurate,
                            )
                        },
                    ),
                ),
            )
        }
        val structure = if (blocks.size > 1) ProgramStructure.COMPLEX else ProgramStructure.SIMPLE
        val simpleKind = when {
            structure != ProgramStructure.SIMPLE -> program.simpleProgramKind
            recipe.repeats -> SimpleProgramKind.CYCLIC
            else -> SimpleProgramKind.LINEAR
        }
        val trainingDaySet = trainingDays?.toSet().orEmpty()
        return program.copy(
            structure = structure,
            simpleProgramKind = simpleKind,
            structureTemplateId = recipe.id,
            powerliftingProfile = resolvedProfile ?: program.powerliftingProfile,
            sourceRecipe = recipe,
            sourceProtocolId = sourceProtocolId,
            // El modo elegido por el usuario (OFF/PROPOSE/AUTO) sobrevive a la
            // materialización: nunca se fuerza PROPOSE aquí.
            autoregulationMode = program.autoregulationMode,
            // La elección de calentamientos se guarda en el JSON del programa para
            // que la rematerialización no dependa de la configuración del llamante:
            // lo ya persistido manda; si es la primera vez que llega una elección
            // explícita, queda registrada aquí (null = preset del plan).
            planWarmupConfig = program.planWarmupConfig ?: options.warmup,
            runState = null,
            loops = emptyList(),
            loopState = null,
            loopOccurrences = emptyList(),
            events = emptyList(),
            calendarBreaks = emptyList(),
            pausedCyclicSnapshot = if (structure == ProgramStructure.SIMPLE) null else program.pausedCyclicSnapshot,
            schedulePlan = if (trainingDaySet.isEmpty()) program.schedulePlan else program.resolvedSchedulePlan().copy(
                trainingDays = trainingDaySet,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = idProvider.newId(),
                    name = program.name,
                    blocks = blocks,
                ),
            ),
        ).alignTemporalMetadata()
    }

    fun hydrateProfile(
        program: Program,
        profile: PowerliftingProfile?,
        trainingMaxPercent: Double,
    ): PowerliftingProfile? {
        val goals = program.goals
        val base = profile ?: program.powerliftingProfile
        if (base == null && goals == null) return null
        val merged = (base ?: PowerliftingProfile()).copy(
            squat1RM = base?.squat1RM ?: goals?.squat1RM,
            bench1RM = base?.bench1RM ?: goals?.bench1RM,
            deadlift1RM = base?.deadlift1RM ?: goals?.deadlift1RM,
        )
        return TrainingMaxResolver.hydrateProfile(merged, trainingMaxPercent)
    }

    fun rematerializeWeek(
        program: Program,
        weekId: String,
        recipe: TrainingPlanRecipe = program.sourceRecipe ?: error("El programa no tiene receta fuente"),
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        intensityScale: Double = 1.0,
        volumeFactor: Double = 1.0,
        executedWeekIds: Set<String> = emptySet(),
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): Program {
        require(options.autoregulationMode != AutoregulationMode.AUTO || options.automaticConfirmed) {
            "La autorregulación AUTO requiere confirmación explícita del usuario (SetupTrainingOptions.automaticConfirmed)."
        }
        if (weekId in executedWeekIds) return program
        val profile = program.powerliftingProfile
        // La receta propia del programa nativo es la única que recibe la política
        // de aproximaciones de la ruta nativa; cualquier otra receta conserva su
        // base (y la de autor, intacta).
        val nativeCurate = program.isNativeCuratedRecipe(recipe)
        // Nunca se reintroduce el preset sobre la elección persistida del usuario:
        // vacío = sin aproximaciones, lista = pasos propios, null = preset.
        val planWarmupSteps = effectivePlanWarmupSteps(program, options)
        return program.copy(
            // La elección de calentamientos persiste en el JSON del programa (la
            // rematerialización no depende de la configuración del llamante).
            planWarmupConfig = program.planWarmupConfig ?: options.warmup,
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else {
                                            val source = recipe.weeks.firstOrNull { it.weekNumber == week.progressionIndex }
                                                ?: recipe.weeks.getOrNull(meso.weeks.indexOf(week))
                                                ?: return@map week
                                            val scaled = source.copy(
                                                days = source.days.map { day ->
                                                    day.copy(
                                                        slots = day.slots.map { slot ->
                                                            val dropped = if (volumeFactor < 1.0 && slot.sets.size > 1) {
                                                                slot.sets.dropLast((slot.sets.size * (1.0 - volumeFactor)).toInt().coerceAtLeast(0))
                                                            } else slot.sets
                                                            slot.copy(
                                                                sets = dropped.map { set ->
                                                                    set.copy(
                                                                        percent = set.percent?.times(intensityScale),
                                                                    )
                                                                }.ifEmpty { slot.sets.take(1) },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                            materializeWeek(scaled, recipe, metadata, idProvider, profile, null, 1, planWarmupSteps, nativeCurate).copy(
                                                id = week.id,
                                                name = week.name,
                                                progressionIndex = week.progressionIndex,
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private fun materializeWeek(
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        trainingDays: List<Int>?,
        startDay: Int,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
    ): ProgramWeek {
        val sessions = week.days.mapIndexed { index, day ->
            val dayOfWeek = rotateWeekday(day.weekday, startDay)
                ?: trainingDays?.getOrNull(index)
                ?: ((startDay - 1 + index).mod(7) + 1)
            materializeDay(day, dayOfWeek, week, recipe, metadata, idProvider, profile, planWarmupSteps, nativeCurate)
        }
        return ProgramWeek(
            id = idProvider.newId(),
            name = week.weekName.ifBlank { "Semana ${week.weekNumber}" },
            sessions = sessions,
            progressionIndex = week.weekNumber,
            executionKind = week.kind,
        )
    }

    private fun materializeDay(
        day: DayRecipe,
        dayOfWeek: Int,
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
    ): Session {
        val assignedWarmups = assignWarmups(day, metadata, idProvider, planWarmupSteps, nativeCurate)
        val exercises = day.slots.mapIndexed { index, slot ->
            materializeSlot(slot, week, recipe, metadata, idProvider, profile, assignedWarmups[index])
        }
        val parts = groupParts(day, exercises, idProvider)
        return Session(
            id = idProvider.newId(),
            name = day.label,
            scheduleLabel = day.label,
            dayOfWeek = dayOfWeek,
            assignedDays = listOf(dayOfWeek),
            exercises = emptyList(),
            parts = parts,
            isMainSession = day.slots.any { it.role == SlotRole.T1_MAIN },
            origin = SessionOrigin.USER_DRAFT,
            requirement = SessionRequirement.REQUIRED,
        )
    }

    private fun groupParts(day: DayRecipe, exercises: List<Exercise>, idProvider: IdProvider): List<SessionPart> {
        val parts = mutableListOf<SessionPart>()
        day.slots.zip(exercises).forEach { (slot, exercise) ->
            val name = when (slot.role) {
                SlotRole.SPEED -> "Velocidad"
                SlotRole.T1_MAIN -> "Principal"
                SlotRole.TECHNIQUE -> "Técnica"
                SlotRole.T2_SUPPLEMENTAL -> "Suplementario"
                SlotRole.T3_ACCESSORY -> "Accesorios"
            }
            val previous = parts.lastOrNull()
            if (previous?.name == name) {
                parts[parts.lastIndex] = previous.copy(exercises = previous.exercises + exercise)
            } else {
                parts += SessionPart(id = idProvider.newId(), name = name, exercises = listOf(exercise))
            }
        }
        return parts
    }

    private fun materializeSlot(
        slot: SlotRecipe,
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        warmupSets: List<WarmupSetDefinition>,
    ): Exercise {
        val meta = metadata.metadata(slot.lift.configurationId)
            ?: error("Configuración '${slot.lift.configurationId}' sin metadata del catálogo")
        // El nombre es el canonicalName verbatim del catálogo; la técnica del
        // slot viaja en variantName/techniqueModifier/relationshipNotes y se
        // muestra como chip en exerciseDisplayParts, nunca como texto del nombre.
        val display = meta.displayName
        require(display.isNotBlank()) { "Nombre vacío para '${slot.lift.configurationId}'" }
        val tm = slot.lift.liftSlot?.let { TrainingMaxResolver.trainingMax(profile, it, recipe.trainingMaxPercent) }
        val oneRm = slot.lift.liftSlot?.let { TrainingMaxResolver.oneRm(profile, it) }
        val working = slot.sets.filter { !it.isWarmup }
        val warmups = slot.sets.filter { it.isWarmup }
        val usesPercent = working.any { it.percent != null } || warmups.any { it.percent != null }
        val reference = when (working.firstOrNull()?.loadBasis ?: slot.sets.firstOrNull()?.loadBasis) {
            LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> oneRm
            else -> tm
        }
        val sets = working.map { set -> materializeSet(set, slot, week, reference, idProvider) }
        val cues = buildList {
            addAll(meta?.let { emptyList() } ?: emptyList())
            slot.technique?.let { add(it.executionCue()) }
        }
        return Exercise(
            id = idProvider.newId(),
            name = display,
            exerciseDbId = slot.lift.configurationId,
            exerciseId = slot.lift.configurationId,
            canonicalExerciseId = slot.lift.configurationId,
            exerciseFamilyId = slot.lift.configurationId.substringBefore("__"),
            relativeToCanonicalExerciseId = slot.lift.configurationId.takeIf { slot.technique != null },
            relationshipType = ExerciseRelationshipType.TECHNIQUE.takeIf { slot.technique != null },
            relationshipNotes = slot.technique?.displayName(),
            sets = sets,
            restTime = slot.restSeconds,
            trainingMode = if (usesPercent) TrainingMode.RM else TrainingMode.REPS,
            reference1RM = reference,
            variantName = slot.technique?.displayName(),
            isUnilateral = slot.isUnilateral,
            isCompetitionLift = slot.isCompetitionLift,
            executionCues = cues,
            catalogRevision = "v2-approved-2026-08-12-a",
            catalogDefinitionId = slot.lift.configurationId.substringBefore("__"),
            catalogConfigurationId = slot.lift.configurationId,
            performanceProfileId = meta.performanceProfileId,
            occurrenceId = idProvider.newId(),
            slotRole = slot.role,
            techniqueModifier = slot.technique,
            warmupSets = warmupSets,
        )
    }

    private fun materializeSet(
        set: SetRecipe,
        slot: SlotRecipe,
        week: WeekRecipe,
        tm: Double?,
        idProvider: IdProvider,
    ): ExerciseSet {
        val percent = resolvePercent(set, slot, week)
        val weight = percent?.let { TrainingMaxResolver.loadKg(it, tm) }
        val range = if (set.repsMin != null && set.repsMax != null) RepRange(set.repsMin, set.repsMax) else null
        val mode = when {
            set.amrap -> IntensityMode.AMRAP
            percent != null -> IntensityMode.SOLO_RM
            set.rir != null -> IntensityMode.RIR
            set.rpe != null -> IntensityMode.RPE
            else -> IntensityMode.RPE
        }
        return ExerciseSet(
            id = idProvider.newId(),
            targetReps = set.reps,
            targetRepsRange = range,
            targetRPE = set.rpe,
            targetRIR = set.rir,
            intensityMode = mode,
            targetPercentageRM = percent,
            weight = weight,
            isAmrap = set.amrap,
            restAfterSeconds = slot.restSeconds,
            isTopSet = set.isTopSet,
            loadBasis = set.loadBasis,
        )
    }

    private fun resolvePercent(set: SetRecipe, slot: SlotRecipe, week: WeekRecipe): Double? {
        val raw = set.percent ?: return null
        if (set.loadBasis != LoadBasis.PERCENT_OF_TOP_SET) return raw
        if (set.isTopSet) return raw
        val sameLift = week.days.flatMap { day ->
            day.slots.filter { it.lift.liftSlot != null && it.lift.liftSlot == slot.lift.liftSlot }
        }
        val top = sameLift.flatMap { it.sets }.firstOrNull { it.isTopSet }?.percent ?: 100.0
        val isVolume = slot.sets.none { it.isTopSet } && slot.sets.count { !it.isWarmup } >= 5
        if (isVolume) return raw / 100.0 * top
        val volumeSlot = sameLift.firstOrNull { candidate ->
            candidate.sets.none { it.isTopSet } && candidate.sets.count { !it.isWarmup } >= 5
        }
        val volumeFactor = volumeSlot?.sets?.firstOrNull { !it.isWarmup }?.percent ?: 90.0
        val volumeResolved = volumeFactor / 100.0 * top
        return raw / 100.0 * volumeResolved
    }

    /** Receta weekday 1-7 relativa al lunes; [startDay] rota el microciclo sin reordenar días. */
    private fun rotateWeekday(recipeDay: Int?, startDay: Int): Int? {
        if (recipeDay == null) return null
        val safeRecipe = recipeDay.coerceIn(1, 7)
        val safeStart = startDay.coerceIn(1, 7)
        return ((safeStart - 1) + (safeRecipe - 1)).mod(7) + 1
    }

    private const val WARMUP_EQUIVALENT_POINTS = 5.0
    private const val UNKNOWN_PATTERN_BUCKET = "__unknown_pattern__"
    /** Tolerancia de la revalidación piso↔material (kg). */
    private const val LOAD_EPSILON = 0.01

    /**
     * Aproximaciones por slot: las que trae la receta (autor, intactas) más el
     * preset del plan (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de trabajo)
     * solo en el primer compuesto de cada patrón de movimiento del día. El
     * patrón se identifica con la composición real del catálogo
     * ([CompositionTaxonomy]), no solo por [SlotRole.T1_MAIN]: los programas
     * nativos no siempre etiquetan así.
     *
     * El preset exige series con porcentaje ([usesPercent]) salvo en semanas
     * curadas nativas ([nativeCurate], serie RIR de la ruta nativa) y sus
     * rematerializaciones, que aplican la misma política de aproximaciones para
     * no divergir del motor.
     *
     * Anti-redundancia: un paso del preset equivalente (±5 puntos porcentuales)
     * a una aproximación ya presente no se duplica. El resultado queda ordenado
     * por porcentaje ascendente; el usuario puede editar reps/% o quitar
     * cualquier aproximación después.
     */
    private fun assignWarmups(
        day: DayRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
    ): List<List<WarmupSetDefinition>> {
        val claimedPatterns = mutableSetOf<String>()
        val out = mutableListOf<List<WarmupSetDefinition>>()
        day.slots.forEach { slot ->
            val meta = metadata.metadata(slot.lift.configurationId)
            val family = CompositionTaxonomy.familyOf(meta?.movementPatternId)
            val compound = meta != null &&
                !CompositionTaxonomy.isIsolation(family, meta.articulationType, meta.configurationId)
            val patternBucket = family?.name ?: UNKNOWN_PATTERN_BUCKET
            val isFirstCompoundOfPattern = when {
                family != null && compound -> claimedPatterns.add(patternBucket)
                // Sin patrón en el catálogo se conserva el fallback legacy por rol.
                family == null && slot.role == SlotRole.T1_MAIN -> claimedPatterns.add(patternBucket)
                else -> false
            }
            val recipeWarmups = slot.sets.filter { it.isWarmup }.map { set ->
                WarmupSetDefinition(idProvider.newId(), set.percent ?: 40.0, set.reps ?: 5, restBetween = 60)
            }
            val usesPercent = slot.sets.any { it.percent != null }
            // Los calentamientos específicos de protocolo se conservan íntegros salvo
            // edición explícita del usuario: el preset del plan NO se suma sobre ellos.
            // El preset se aplica solo al primer compuesto de cada patrón cuando la
            // receta no trae aproximaciones propias.
            val presetWarmups = if (recipeWarmups.isEmpty() && isFirstCompoundOfPattern && (usesPercent || nativeCurate)) {
                planWarmupSteps.mapNotNull { step ->
                    val percent = step.percent ?: return@mapNotNull null
                    WarmupSetDefinition(
                        id = idProvider.newId(),
                        percentageOfWorkingWeight = percent,
                        targetReps = step.reps ?: 5,
                        restBetween = when {
                            percent >= 70.0 -> 120
                            percent >= 50.0 -> 90
                            else -> 60
                        },
                    )
                }
            } else {
                emptyList()
            }
            out.add(
                (recipeWarmups + presetWarmups)
                    .sortedBy { WarmupCalibrationEngine.normalizePercentage(it.percentageOfWorkingWeight) },
            )
        }
        return out
    }

    /**
     * Pasos de calentamiento efectivos de una materialización/rematerialización:
     * manda la elección persistida en el programa ([Program.planWarmupConfig],
     * la decisión real del usuario: null = preset del plan, vacío = sin
     * aproximaciones, lista = pasos propios) para que una rematerialización nunca
     * reintroduzca el preset sobre una elección ya guardada. Solo cuando el
     * programa todavía no guarda nada se usa la configuración de esta llamada
     * ([options]), que por defecto es el preset 40 % × 8 / 60 % × 5 / 80 % × 3
     * sobre la carga de trabajo. Los pasos siempre salen normalizados (orden
     * ascendente, sin duplicados ±5 puntos porcentuales, 0 < % ≤ 100).
     */
    private fun effectivePlanWarmupSteps(program: Program, options: SetupTrainingOptions): List<SetRecipe> {
        val persisted = program.planWarmupConfig
        return if (persisted != null) normalizedWarmupSteps(persisted) else options.resolvedWarmupSteps()
    }

    /**
     * Contrato público de la bolsa de prioridades de orden en la ruta de
     * materialización: esta ruta NUNCA reordena recetas de autor ni altera su
     * estructura, así que [OrderPrioritiesCapabilities.applied] solo puede ser
     * true cuando la bolsa pedida coincide con la que el generador nativo aplicó
     * al persistir el programa. La UI debe consultar este resultado antes de
     * afirmar que la bolsa quedó aplicada; ver [OrderPrioritiesContract].
     */
    fun orderPrioritiesCapabilities(
        program: Program,
        recipe: TrainingPlanRecipe? = program.sourceRecipe,
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): OrderPrioritiesCapabilities = OrderPrioritiesContract.capabilitiesOf(program, recipe, options)

    /**
     * Realiza las cargas de aproximación contra el inventario real:
     * - Los porcentajes son sobre la carga de trabajo ([workingLoadKg]), nunca
     *   sobre el 1RM; con calibración de WarmupCalibrationEngine (±2,5 %, tope 5 %).
     * - Sin referencia de carga → porcentajes pendientes
     *   ([WarmupLoadStatus.PENDING_PERCENT]), nunca kilogramos inventados.
     * - Peso corporal / asistido → sin % de carga externa ficticia
     *   ([WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE]).
     * - La carga se ajusta con [PlateCalculator] (discos con cantidades) o al
     *   rango real de la máquina; no se asumen incrementos universales de 0,5 kg.
     * - [configurationId] verifica que la máquina declarada ES la de este
     *   ejercicio (leg curl ≠ leg press): si no coincide, la carga queda
     *   [WarmupLoadStatus.PENDING_PERCENT], nunca kilogramos de otra máquina.
     * - [equipmentKind] elige el motor de material del ejercicio: discos
     *   (barra/por defecto), mancuerna por pareja, kettlebell; sin rango
     *   exacto/estación para máquina, cable o Smith →
     *   [WarmupLoadStatus.PENDING_PERCENT]. Nunca se usa el resolvedor de
     *   discos para todo tipo de material.
     * - [BaseLoadPolicy] impone el piso de carga base por etiqueta activa.
     * - Las cargas repetidas por redondeo se colapsan ([WarmupLoadStatus.DEDUPED]).
     * - [WarmupLoadPlan.feasibility] resume la viabilidad honesta contra el
     *   inventario declarado (o la máquina): sin carga de trabajo o sin
     *   inventario → [WarmupFeasibilityStatus.UNKNOWN], nunca ilimitado.
     */
    fun realizeWarmupLoads(
        warmups: List<WarmupSetDefinition>,
        workingLoadKg: Double?,
        inventory: EquipmentInventory,
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
        taggedProfile: WorkoutContextProfile? = null,
        activeTagId: String? = null,
        machine: MachineLoadRange? = null,
        configurationId: String? = null,
        equipmentKind: String? = null,
        effortReports: List<WarmupEffortReport> = emptyList(),
        effectiveSetCount: Int = 3,
        /**
         * Colapso de cargas repetidas (prescripción). La vista de
         * calentamientos lo desactiva: cada paso muestra su propio kg alcanzable
         * aunque coincida con otro, en vez de quedar «pendiente».
         */
        deduplicate: Boolean = true,
    ): WarmupLoadPlan {
        // Matching configurationId ↔ ejercicio: si la máquina declarada no es la
        // de esta configuración (leg curl ≠ leg press) no se aplica y las cargas
        // quedan pendientes, nunca kg inventados con la máquina equivocada.
        val matchedMachine = machineRangeFor(machine, configurationId)
        val machineMismatch = machine != null && matchedMachine == null
        val validationMessages = warmupValidationMessages(warmups, effectiveSetCount)
        if (loadMode == LoadModeV2.BODYWEIGHT || loadMode == LoadModeV2.ASSISTED) {
            return WarmupLoadPlan(
                entries = warmups.map {
                    WarmupLoadEntry(it, WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE, null, null, isExact = false)
                },
                validationMessages = validationMessages,
                calibrationNote = null,
                // Sin carga externa no se proclama viabilidad de cargas: se queda en
                // «pendiente de porcentaje» (UNKNOWN), nunca kilogramos inventados.
                feasibility = WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, workingLoadKg, inventory),
            )
        }
        val calibration = WarmupCalibrationEngine.calibrateWorkingLoad(
            programmedPercentages = warmups.map { it.percentageOfWorkingWeight },
            workingLoadKg = workingLoadKg,
            reports = effortReports,
        )
        val lastReportedIndex = effortReports.maxOfOrNull { it.warmupIndex } ?: -1
        val resolved = warmups.mapIndexed { index, definition ->
            when {
                index <= lastReportedIndex ->
                    WarmupLoadEntry(definition, WarmupLoadStatus.COMPLETED, null, null, isExact = false)
                // Máquina declarada que no corresponde a esta configuración:
                // porcentaje pendiente, nunca kilogramos de la máquina equivocada.
                machineMismatch ->
                    WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                else -> {
                    val requested = calibration.remainingWarmupLoadsKg.getOrNull(index)
                    if (requested == null || requested <= 0.0) {
                        WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                    } else {
                        // Carga alcanzable según el material REAL del ejercicio
                        // (discos, mancuerna por pareja, kettlebell, máquina
                        // exacta o estación): si no hay material acreditado →
                        // pendiente, nunca 0 ni kilogramos inventados.
                        val achieved = reachableWarmupLoad(
                            requestedKg = requested,
                            equipmentKind = equipmentKind,
                            inventory = inventory,
                            machine = matchedMachine,
                        )
                        if (achieved == null || achieved <= 0.0) {
                            WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                        } else {
                            val floor = BaseLoadPolicy.floorForLoadSuggestion(
                                loadMode = loadMode,
                                activeTagId = activeTagId,
                                engineSuggestedKg = achieved,
                                taggedProfileBaseLoadKg = BaseLoadPolicy.resolvedFromProfile(taggedProfile),
                            )
                            val candidate = floor?.suggestedWeight ?: achieved
                            // Revalidación piso + material: si no pueden
                            // satisfacerse a la vez → pendiente explícito, nunca
                            // un kg imposible como READY.
                            val finalizedKg = if (candidate == achieved) {
                                achieved
                            } else {
                                reachableWarmupLoad(candidate, equipmentKind, inventory, matchedMachine)
                                    ?.takeIf { it >= candidate - LOAD_EPSILON }
                            }
                            if (finalizedKg == null || finalizedKg <= 0.0) {
                                WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                            } else {
                                WarmupLoadEntry(
                                    definition = definition,
                                    status = WarmupLoadStatus.READY,
                                    requestedKg = requested,
                                    realizedKg = finalizedKg,
                                    isExact = abs(finalizedKg - requested) < LOAD_EPSILON,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Evitar cargas repetidas por redondeo: gana la aproximación más liviana.
        // Solo para prescripción; la ruta de visualización pide cada kg alcanzable.
        val entries = if (!deduplicate) resolved else {
            val seen = mutableListOf<Double>()
            resolved.map { entry ->
                val realized = entry.realizedKg
                if (entry.status != WarmupLoadStatus.READY || realized == null) {
                    entry
                } else if (seen.any { abs(it - realized) < 0.001 }) {
                    entry.copy(status = WarmupLoadStatus.DEDUPED, realizedKg = null, isExact = false)
                } else {
                    seen += realized
                    entry
                }
            }
        }
        return WarmupLoadPlan(
            entries = entries,
            validationMessages = validationMessages,
            calibrationNote = calibration.note,
            // Viabilidad real contra el inventario hardware (o la máquina) con la
            // misma carga de trabajo ya usada aquí: si no hay carga de trabajo la
            // respuesta es UNKNOWN y las entries quedan en PENDING_PERCENT, nunca
            // un 0 kg ni un stock ilimitado inventado.
            feasibility = WarmupFeasibilityChecker.of(
                workingLoadKg = workingLoadKg,
                inventory = inventory,
                warmups = warmups,
                machine = machine,
                configurationId = configurationId,
                equipmentKind = equipmentKind,
            ),
        )
    }
}
