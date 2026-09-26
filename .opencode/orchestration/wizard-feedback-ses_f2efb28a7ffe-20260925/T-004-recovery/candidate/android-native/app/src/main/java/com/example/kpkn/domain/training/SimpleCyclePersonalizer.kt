package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.programs.*
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
enum class Calibration { UNCALIBRATED, CONSERVATIVE, CALIBRATED }

@Serializable
data class CardioPreference(
    val type: CardioType,
    val minutes: Int,
    val intensity: CardioIntensity = CardioIntensity.MEDIA,
)

@Serializable
data class PersonalizerInput(
    val catalogEntryId: String,
    val focus: TrainingFocus,
    val frequency: Int,
    val weekdays: List<Int> = emptyList(),
    val equipment: Set<String> = setOf("general_gym"),
    val level: CatalogLevel = CatalogLevel.BEGINNER,
    val availableMinutes: Int = 60,
    val calibration: Calibration = Calibration.UNCALIBRATED,
    val cardio: CardioPreference? = null,
    val volumeRecommendations: List<VolumeRecommendation> = emptyList(),
    /**
     * Prioridades heredadas: 1 punto de orden por músculo. Solo cambian el
     * orden de los ejercicios; nunca el volumen ni la frecuencia. Si hay bolsa
     * nueva ([exerciseOrderPriorities]) este campo se ignora.
     */
    val priorityMuscles: Set<String> = emptySet(),
    /**
     * Sin efecto desde que las prioridades solo ordenan ejercicios. Se
     * conserva por compatibilidad de serialización y no participa en ningún
     * cálculo.
     */
    val lowerEmphasisMuscles: Set<String> = emptySet(),
    /**
     * Bolsa de puntos de orden de ejercicios (músculo → puntos). Máximo
     * 2 puntos por músculo, 5 en total y ningún punto negativo; no es
     * obligatorio gastarlos todos. Solo altera el orden de los ejercicios:
     * jamás ejercicios prescritos, series, repeticiones, intensidades,
     * frecuencia muscular ni volumen directo/indirecto.
     */
    val exerciseOrderPriorities: Map<String, Int> = emptyMap(),
    val splitId: String? = null,
    val splitPattern: List<String> = emptyList(),
    val splitName: String? = null,
)

data class PersonalizationResult(val program: Program?, val report: PersonalizationReport)
data class PersonalizationReport(
    val executable: Boolean,
    val classification: CatalogClassification,
    val limitations: List<String>,
    val muscles: List<MuscleBudgetReport>,
    val provenance: CatalogProvenance,
)
data class MuscleBudgetReport(
    val muscle: String,
    val directSets: Double,
    val indirectSets: Double,
    val targetSets: Double,
    val mev: Int,
    val mav: Int,
    val mrv: Int,
    val frequency: Int,
    val deficitSets: Double,
)
data class CatalogProvenance(
    val catalogEntryId: String,
    val catalogRevision: String,
    val source: CatalogSource,
    val sourceId: String,
)

class SimpleCyclePersonalizer(private val catalog: ExerciseCatalogRepositoryV2? = null) {
    private data class Candidate(
        val info: ExerciseMuscleInfo,
        val configuration: ExerciseConfigurationV2,
        val volume: Map<String, RoleSeparatedMuscleVolume>,
    ) {
        val id get() = configuration.id
        val primary get() = volume.filterValues { it.directSets > 0.0 }.keys
    }

    private data class Budget(val mev: Int, val mav: Int, val mrv: Int, val target: Double, val frequencyCap: Int)
    private data class Slot(val candidate: Candidate, var sets: Int)

    fun personalize(
        programId: String,
        input: PersonalizerInput,
        options: TrainingOptions = TrainingOptions(),
    ): PersonalizationResult {
        val entry = PersonalizedPlanCatalog.find(input.catalogEntryId)
        val provenance = CatalogProvenance(input.catalogEntryId, PersonalizedPlanCatalog.REVISION, entry?.source ?: CatalogSource.NATIVE, entry?.sourceId ?: input.catalogEntryId)
        fun unavailable(message: String) = PersonalizationResult(null, PersonalizationReport(false, CatalogClassification.SIMPLE, listOf(message), emptyList(), provenance))
        if (programId.isBlank() || entry == null) return unavailable("Selecciona un plan disponible.")
        if (entry.adaptation != AdaptationPolicy.CURATED_WEEKLY) return unavailable("Este método conserva su receta original; aplícalo sin personalización libre. Las prioridades de orden y el split elegido no alteran su receta ni su orden de ejercicios.")
        if (input.frequency !in entry.supportedFrequencies) return unavailable("Este plan no está curado para esa frecuencia.")
        if (input.availableMinutes !in 20..100) return unavailable("Elige entre 20 y 100 minutos por sesión.")
        if (input.weekdays.isNotEmpty() && (input.weekdays.size != input.frequency || input.weekdays.distinct().size != input.frequency || input.weekdays.any { it !in 1..7 })) return unavailable("Selecciona exactamente los días que quieres entrenar.")
        // Las opciones que intervienen en la selección/personalización deben
        // cumplir su contrato antes de tocar el motor.
        when (val config = options.validateForSelection()) {
            is TrainingValidation.Invalid -> return unavailable(config.reasons.joinToString("\n"))
            TrainingValidation.Valid -> Unit
        }
        val ready = catalog?.state?.value as? ExerciseCatalogStateV2.Ready ?: return unavailable("El catálogo todavía no está disponible. Vuelve a intentarlo.")
        // Equipo efectivo con el contrato COMPARTIDO (TrainingOptions.effectiveEquipment):
        // availability explícita manda sobre chips e inventario; en su ausencia
        // se conserva el comportamiento de inventario/legacy. El mismo resultado
        // es el que el wizard usa para readiness y candidatos.
        val equipment = options.effectiveEquipment(input.equipment)
        val cardio = input.cardio ?: if (entry.sourceId == "strength-cardio") CardioPreference(CardioType.WALK, 15) else null
        if (cardio != null && (cardio.minutes !in 5..60 || cardio.minutes >= input.availableMinutes)) return unavailable("Reserva tiempo tanto para fuerza como para cardio.")
        if (cardio != null && cardio.type !in setOf(CardioType.WALK, CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR) && "general_gym" !in equipment && "cardio" !in equipment) return unavailable("El cardio seleccionado necesita un aparato que no has indicado.")
        val lookup = ready.catalog.toLegacyConfigurationLookup()
        val compositionMetadata = CatalogCompositionMetadataProvider.fromCatalog(ready.catalog)
        val pools = curatedPools()
        val allowedIds = pools.values.flatten().toSet()
        val requireExactMachineConfiguration = options.availability == null && options.inventory != null
        val candidates = ready.catalog.families.flatMap { it.definitions }.flatMap { definition ->
            definition.configurations.mapNotNull { configuration ->
                if (configuration.id !in allowedIds || configuration.evidence.reviewStatus != CatalogReviewStatusV2.APPROVED) return@mapNotNull null
                if (!equipmentAllows(configuration, equipment, entry.sourceId, requireExactMachineConfiguration)) return@mapNotNull null
                if (input.level == CatalogLevel.BEGINNER && (configuration.profile.technicalDifficulty > 5.2 || configuration.id in hardBodyweight)) return@mapNotNull null
                val info = lookup[configuration.id.lowercase()] ?: return@mapNotNull null
                val exercise = exercise(info, "probe", 1, input.level)
                val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(listOf(Session("probe", "probe", exercises = listOf(exercise))), listOf(info))
                Candidate(info, configuration, volume)
            }
        }.associateBy { it.id }
        if (candidates.isEmpty()) return unavailable("No hay una variante curada compatible con tu equipo y experiencia.")
        // Las prioridades (nuevas o heredadas) son SOLO orden: una bolsa de
        // puntos que nunca toca volumen, series, repeticiones ni frecuencia.
        // La bolsa de [TrainingOptions] gana sobre la heredada del input
        // cuando viene con puntos; si está vacía se conserva el input intacto.
        val exerciseOrderPoints = orderPoints(options.applyTo(input)) ?: return unavailable(
            "La bolsa de prioridades de orden no es válida: máximo 2 puntos por músculo, 5 puntos en total y ningún punto negativo. Ajusta tus prioridades."
        )
        val focused = focusMuscles(input.focus)
        val focusCandidates = candidates.values.filter { candidate -> candidate.primary.any { it in focused } }
        if (focused.isNotEmpty() && focusCandidates.isEmpty()) return unavailable("Tu equipo no permite trabajar ese enfoque con una variante curada. Cambia de enfoque o añade material.")
        val selectedDays = input.weekdays.sorted().ifEmpty { defaultDays.getValue(input.frequency) }
        // El split es una elección real: restringe qué grupos entran cada día
        // antes de rellenar las sesiones, no solo sus etiquetas.
        val splitResolution = resolveSplitPlan(input, selectedDays, candidates)
        val splitPlan = when (splitResolution) {
            is SplitResolution.Invalid -> return unavailable(splitResolution.reason)
            SplitResolution.None -> null
            is SplitResolution.Ready -> splitResolution.plan
        }
        val customLabelsByDay = splitPlan?.labelsByDay.orEmpty()
        val dayGroupsByDay = splitPlan?.groupsByDay.orEmpty()
        val days = selectedDays
        val budgets = budgets(input, focused)
        val slots = List(days.size) { mutableListOf<Slot>() }
        val totals = mutableMapOf<String, Double>()
        val notes = mutableListOf<String>()
        splitPlan?.notes?.let { notes.addAll(it) }
        val priorityDays = spacedIndices(days, minOf(3, days.size))
        fun minutes(daySlots: List<Slot>, extraSets: Int = 0, extraExercise: Boolean = false): Int =
            6 + ceil(daySlots.sumOf { it.sets * 2.25 + 1.5 } + extraSets * 2.25 + if (extraExercise) 1.5 else 0.0).toInt() + (cardio?.minutes ?: 0)
        fun canAdd(dayIndex: Int, candidate: Candidate): Boolean {
            val daySlots = slots[dayIndex]
            val existing = daySlots.firstOrNull { it.candidate.id == candidate.id }
            if (existing == null && daySlots.size >= 7) return false
            if ((existing?.sets ?: 0) >= 4 || daySlots.sumOf { it.sets } >= 30) return false
            if (minutes(daySlots, 1, existing == null) > input.availableMinutes) return false
            return candidate.volume.all { (muscle, contribution) ->
                val budget = budgets[muscle] ?: return@all false
                val total = contribution.directSets + contribution.indirectSets
                val directInDay = daySlots.sumOf { it.sets * (it.candidate.volume[muscle]?.directSets ?: 0.0) }
                val hasDirect = daySlots.any { muscle in it.candidate.primary }
                val frequency = slots.count { day -> day.any { muscle in it.candidate.primary } }
                (totals[muscle] ?: 0.0) + total <= minOf(budget.mav, budget.mrv) + 0.0001 &&
                    directInDay + contribution.directSets <= 12.0 &&
                    (contribution.directSets == 0.0 || hasDirect || frequency < budget.frequencyCap)
            }
        }
        fun add(dayIndex: Int, candidate: Candidate): Boolean {
            if (!canAdd(dayIndex, candidate)) return false
            val existing = slots[dayIndex].firstOrNull { it.candidate.id == candidate.id }
            if (existing == null) slots[dayIndex] += Slot(candidate, 1) else existing.sets++
            candidate.volume.forEach { (muscle, value) -> totals[muscle] = (totals[muscle] ?: 0.0) + value.directSets + value.indirectSets }
            return true
        }
        fun muscleCandidates(muscle: String, dayIndex: Int): List<Candidate> {
            val curated = pools[muscle].orEmpty().mapNotNull(candidates::get)
            val rotated = if (curated.isEmpty()) curated else curated.drop(dayIndex % curated.size) + curated.take(dayIndex % curated.size)
            return rotated.filter { muscle in it.primary }.sortedBy { candidate ->
                candidate.volume.filterKeys { it != muscle }.values.sumOf { it.directSets + it.indirectSets }
            }
        }
        slots.indices.forEach { index ->
            val allowedMuscles = dayGroupsByDay[days[index]]
            val base = if (days.size >= 4) {
                if (index % 2 == 0) listOf("Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps")
                else listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen")
            } else listOf("Pectorales", "Dorsales", "Cuádriceps", "Isquiosurales")
            // Un split aplicado restringe los grupos del día ANTES de rellenar.
            val ordered = if (allowedMuscles == null) {
                ((if (index in priorityDays) focused.toList() else emptyList()) + base).distinct()
            } else {
                ((if (index in priorityDays) focused.filter { it in allowedMuscles } else emptyList()) + allowedMuscles).distinct()
            }
            ordered.forEach { muscle ->
                muscleCandidates(muscle, index).firstOrNull { canAdd(index, it) }?.let { add(index, it) }
            }
        }
        repeat(180) {
            val choice = slots.indices.flatMap { index ->
                val permitted = slots[index].flatMap { it.candidate.primary }.toSet()
                val allowedMuscles = dayGroupsByDay[days[index]]
                candidates.values.filter { candidate ->
                    candidate.primary.any { it in permitted } &&
                        (allowedMuscles == null || candidate.primary.any { it in allowedMuscles }) &&
                        canAdd(index, candidate)
                }.map { candidate ->
                    val gain = candidate.volume.entries.sumOf { (muscle, contribution) ->
                        val deficit = (budgets.getValue(muscle).target - (totals[muscle] ?: 0.0)).coerceAtLeast(0.0)
                        minOf(deficit, contribution.directSets + contribution.indirectSets) * if (muscle in focused) 3.0 else 1.0
                    }
                    val existing = slots[index].any { it.candidate.id == candidate.id }
                    Triple(index, candidate, gain + if (existing && gain > 0.0) 0.05 else 0.0)
                }
            }.maxByOrNull { it.third }
            if (choice != null && choice.third > 0.0) add(choice.first, choice.second)
        }
        // Una sesión equilibrada exige variedad; un día restringido por el split
        // se centra en su patrón y basta con un ejercicio prescrito.
        if (slots.indices.any { index -> slots[index].size < if (dayGroupsByDay[days[index]] != null) 1 else 3 }) {
            return unavailable(
                if (splitPlan != null) "El split '${splitPlan.splitName}' no permite completar las sesiones con tu tiempo, equipo y presupuesto de recuperación. Amplía el tiempo o el material disponible."
                else "No se puede completar una sesión equilibrada con ese equipo, enfoque y tiempo. Amplía el tiempo o el material disponible."
            )
        }
        slots.indices.forEach { index ->
            val total = slots[index].sumOf { it.sets }
            if (total < 10) notes += "Día ${index + 1}: $total series para respetar tu presupuesto de recuperación."
            val label = customLabelsByDay[days[index]]
            if (dayGroupsByDay[days[index]] != null && label != null && slots[index].size < 3) {
                notes += "Día ${index + 1} ($label): ${slots[index].size} ejercicios para centrarse en el patrón del split sin superar tu presupuesto de recuperación."
            }
        }
        if (days.size > 1 && days.indices.any { i -> (days[(i + 1) % days.size] - days[i] + 7) % 7 == 1 }) {
            notes += "Hay días consecutivos, también al repetir la semana. Revisa tu recuperación antes de entrenar."
        }
        val sessions = slots.mapIndexed { index, daySlots ->
            val baseOrder = daySlots.sortedWith(compareBy<Slot> { slot -> if (slot.candidate.primary.any { it in focused }) 0 else 1 }
                .thenBy { if (it.candidate.configuration.profile.articulationType?.name == "MULTIARTICULAR") 0 else 1 })
            val ordered = prioritizeExerciseOrder(baseOrder, exerciseOrderPoints)
            // Preset del plan (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de
            // trabajo) en el primer compuesto de cada patrón, tal y como queda
            // ordenado: la misma política de PlanMaterializer para la ruta nativa
            // RIR. Vacío = sin aproximaciones automáticas y sin mezclarse con
            // calentamientos que traiga la receta de autor (aquí no llegan).
            val warmupSteps = options.resolvedWarmupSteps()
            val firstCompounds = if (warmupSteps.isEmpty()) {
                emptySet()
            } else {
                firstCompoundConfigurationIds(ordered, compositionMetadata)
            }
            val exercises = ordered.mapIndexed { exerciseIndex, slot ->
                val base = exercise(slot.candidate.info, "$programId-s$index-e$exerciseIndex", slot.sets, input.level)
                if (slot.candidate.configuration.id in firstCompounds) {
                    base.copy(warmupSets = presetWarmupDefinitions(warmupSteps, base.id))
                } else {
                    base
                }
            }
            val cardioExercise = cardio?.let {
                Exercise(id = "$programId-s$index-cardio", name = it.type.name.lowercase().replace('_', ' '),
                    cardioDetails = CardioDetails(type = it.type, intensity = it.intensity, targetDurationSeconds = it.minutes * 60))
            }
            Session(
                id = "$programId-session-$index", name = customLabelsByDay[days[index]] ?: "Día ${index + 1}", dayOfWeek = days[index], assignedDays = listOf(days[index]),
                exercises = exercises,
                parts = cardioExercise?.let { listOf(SessionPart("$programId-cardio-$index", "Cardio", exercises = listOf(it), isCardioGroup = true)) }.orEmpty(),
                focus = input.focus.name, targetDurationMinutes = minutes(daySlots), origin = SessionOrigin.USER_DRAFT,
            )
        }
        val actual = VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessions, lookup.values.toList())
        val reports = budgets.map { (muscle, budget) ->
            val value = actual[muscle]
            val direct = value?.directSets ?: 0.0
            val indirect = value?.indirectSets ?: 0.0
            val frequency = slots.count { day -> day.any { muscle in it.candidate.primary } }
            val deficit = (budget.target - direct - indirect).coerceAtLeast(0.0)
            if (muscle in focused && deficit > 1.0) notes += "$muscle: el tiempo, equipo o volumen indirecto limitan el objetivo. No se excedió MAV."
            MuscleBudgetReport(muscle, direct, indirect, budget.target, budget.mev, budget.mav, budget.mrv, frequency, deficit)
        }
        if (reports.any { it.directSets + it.indirectSets > minOf(it.mav, it.mrv) + 0.001 }) return unavailable("La combinación excede el volumen permitido. Elige otra distribución.")
        val week = ProgramWeek("$programId-week", "Semana repetible", sessions = sessions)
        val sourceRecipe = TrainingPlanRecipe(
            id = "$programId-onboarding-recipe",
            weeks = listOf(
                WeekRecipe(
                    weekNumber = 1,
                    blockIndex = 0,
                    blockName = "KPKN personalizado",
                    weekName = "Semana repetible",
                    days = sessions.map { session ->
                        DayRecipe(
                            label = session.name,
                            weekday = session.dayOfWeek,
                            slots = session.exercises.mapIndexed { exerciseIndex, exercise ->
                                SlotRecipe(
                                    id = "${session.id}-slot-$exerciseIndex",
                                    role = SlotRole.T3_ACCESSORY,
                                    lift = LiftRef(exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: exercise.id),
                                    sets = exercise.sets.map { set ->
                                        SetRecipe(
                                            reps = set.targetReps,
                                            repsMin = set.targetRepsRange?.min,
                                            repsMax = set.targetRepsRange?.max,
                                            rir = set.targetRIR,
                                            loadBasis = LoadBasis.REP_MAX,
                                        )
                                    },
                                    restSeconds = exercise.restTime ?: 90,
                                    source = SlotSource.KPKN_DEFAULT,
                                )
                            },
                        )
                    },
                ),
            ),
            progression = ProgressionRule.None,
            claimedDaysPerWeek = days.size,
            claimedLevel = input.level.name.lowercase(),
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.WEEKLY_REVIEW)),
            repeats = true,
        )
        val program = Program(
            id = programId, name = entry.title,
            description = "${entry.description}\n${notes.distinct().joinToString("\n")}",
            mode = ProgramMode.HYPERTROPHY, structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC, structureTemplateId = entry.id,
            macrocycles = listOf(Macrocycle(id = "$programId-macro", name = "Mi plan", blocks = listOf(
                Block(id = "$programId-block", name = "Semana cíclica", mesocycles = listOf(
                    Mesocycle(id = "$programId-meso", name = "Base", weeks = listOf(week)),
                ), sourceDefinitionId = entry.id, sourceRevision = PersonalizedPlanCatalog.REVISION, prescriptionOrigin = "KPKN_NATIVE_CURATED"),
            ))),
            startDay = days.first(), volumeRecommendations = input.volumeRecommendations,
            schedulePlan = ProgramSchedulePlan(
                weekStartDay = days.first(),
                trainingDays = days.toSet(),
            ),
            // El modo de autorregulación lo trae la configuración real
            // (PROPOSE por defecto; AUTO solo tras confirmación explícita) y debe
            // sobrevivir a la materialización.
            autoregulationMode = options.autoregulationMode,
            // La elección de calentamientos del usuario se persiste en el JSON del
            // programa (null = preset; vacío = sin aproximaciones; lista = pasos
            // propios) para que la rematerialización no dependa del onboarding.
            planWarmupConfig = options.warmup,
            // La bolsa de orden realmente aplicada se persiste igual: es el dato
            // con el que OrderPrioritiesContract puede responder con un hecho
            // («aplicada») en vez de suposiciones. Solo ordena ejercicios.
            planOrderPriorities = exerciseOrderPoints.takeIf { it.isNotEmpty() },
            tags = listOf("KPKN_NATIVE", input.focus.name),
            selectedSplitId = splitPlan?.splitId,
            customSplitPattern = if (splitPlan?.splitId == "custom") input.splitPattern else emptyList(),
            customSplitName = if (splitPlan?.splitId == "custom") input.splitName else null,
            sourceRecipe = sourceRecipe,
        )
        ProgramExecutionContract.requireExecutable(program)
        return PersonalizationResult(program, PersonalizationReport(true, CatalogClassification.SIMPLE, notes.distinct(), reports, provenance))
    }

    private fun exercise(info: ExerciseMuscleInfo, id: String, sets: Int, level: CatalogLevel): Exercise = Exercise(
        id = id, name = info.name, exerciseDbId = info.id, exerciseId = info.id, canonicalExerciseId = info.id,
        catalogConfigurationId = info.catalogConfigurationId, catalogDefinitionId = info.catalogDefinitionId,
        catalogRevision = info.catalogRevision, performanceProfileId = info.performanceProfileId, occurrenceId = id,
        effectiveMuscles = info.involvedMuscles,
        sets = List(sets) { index -> ExerciseSet("$id-set-$index", targetReps = 10, targetRepsRange = RepRange(8, 12), targetRIR = if (level == CatalogLevel.BEGINNER) 3 else 2, intensityMode = IntensityMode.RIR) },
        restTime = 90,
    )

    /**
     * Primer compuesto de cada patrón de movimiento de la sesión, identificado
     * con la composición real del catálogo (igual que en [PlanMaterializer]):
     * gana el primer compuesto del orden ya priorizado, tal y como el usuario
     * lo ve. Los ejercicios sin patrón conocido simplemente no reciben preset.
     */
    private fun firstCompoundConfigurationIds(
        slots: List<Slot>,
        metadata: ExerciseCompositionMetadataProvider,
    ): Set<String> {
        val claimed = mutableSetOf<String>()
        val first = mutableSetOf<String>()
        slots.forEach { slot ->
            val id = slot.candidate.configuration.id
            val meta = metadata.metadata(id)
            val family = CompositionTaxonomy.familyOf(meta?.movementPatternId)
            val compound = meta != null &&
                !CompositionTaxonomy.isIsolation(family, meta.articulationType, meta.configurationId)
            if (compound) {
                val bucket = family?.name ?: "_compound_$id"
                if (claimed.add(bucket)) first += id
            }
        }
        return first
    }

    /**
     * Pasos declarados o preset (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga
     * de trabajo) convertidos en aproximaciones nativas editables, con descanso
     * según la cercanía a la serie de trabajo (misma decisión que
     * [PlanMaterializer.assignWarmups]).
     */
    private fun presetWarmupDefinitions(
        steps: List<SetRecipe>,
        exerciseId: String,
    ): List<WarmupSetDefinition> = steps.mapIndexedNotNull { index, step ->
        val percent = step.percent ?: return@mapIndexedNotNull null
        WarmupSetDefinition(
            id = "$exerciseId-warmup-$index",
            percentageOfWorkingWeight = percent,
            targetReps = step.reps ?: 5,
            restBetween = when {
                percent >= 70.0 -> 120
                percent >= 50.0 -> 90
                else -> 60
            },
        )
    }

    /** El presupuesto depende del enfoque del plan; las prioridades de orden no lo alteran. */
    private fun budgets(input: PersonalizerInput, focused: Set<String>): Map<String, Budget> {
        val groups = linkedMapOf(
            "Pectorales" to KpknMuscleGroup.CHEST, "Dorsales" to KpknMuscleGroup.BACK_LATS,
            "Trapecio" to KpknMuscleGroup.BACK_UPPER, "Romboides" to KpknMuscleGroup.BACK_UPPER,
            "Cuádriceps" to KpknMuscleGroup.QUADS, "Isquiosurales" to KpknMuscleGroup.HAMS,
            "Glúteos" to KpknMuscleGroup.GLUTES, "Glúteo Medio" to KpknMuscleGroup.GLUTES,
            "Deltoides" to KpknMuscleGroup.DELT_LATERAL, "Bíceps" to KpknMuscleGroup.BICEPS,
            "Tríceps" to KpknMuscleGroup.TRICEPS, "Pantorrillas" to KpknMuscleGroup.CALVES,
            "Core" to KpknMuscleGroup.CORE, "Abdomen" to KpknMuscleGroup.CORE,
            "Erectores Espinales" to KpknMuscleGroup.ERECTORS, "Antebrazo" to KpknMuscleGroup.FOREARMS,
            "Aductores" to KpknMuscleGroup.ADDUCTORS, "Cuello" to KpknMuscleGroup.NECK,
        )
        return groups.mapValues { (muscle, group) ->
            val global = VolumeLandmarks.byGroup.getValue(group)
            val personal = input.volumeRecommendations.firstOrNull { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) == muscle }
            val mav = (personal?.maxAdaptiveVolume ?: global.mav).coerceAtLeast(1)
            val mrv = minOf(personal?.maxRecoverableVolume ?: global.mrv, global.mrv).coerceAtLeast(1)
            val mev = (personal?.minEffectiveVolume ?: global.mev).coerceIn(0, minOf(mav, mrv))
            val calibrated = input.calibration == Calibration.CALIBRATED && personal != null && input.level != CatalogLevel.BEGINNER && input.catalogEntryId != "native:return-training"
            val target = if (calibrated) mev + (minOf(mav, mrv) - mev) * (if (muscle in focused) 0.875 else 0.4)
            else minOf(mav, mrv) * (if (muscle in focused) 0.65 else 0.5)
            Budget(mev, mav, mrv, target, personal?.frequencyCap?.coerceIn(1, 6) ?: 3)
        }
    }

    /**
     * Filtro real de material por configuración:
     * - En **modo inventario legacy**, una máquina concreta solo se admite si su
     *   token `machine_config:<id>` está en el set (leg curl ≠ chest press ≠
     *   prensa); la presencia genérica `machine` no basta. `cable` y
     *   `smith_machine` sí valen como estación multi-ejercicio declarada.
     * - La disponibilidad categórica `machine` habilita variantes nativas
     *   aprobadas sin afirmar una configuración concreta.
     * - Con **inventario null** (perfil legacy) se conserva el comportamiento
     *   anterior: `machine`/`general_gym` del perfil siguen valiendo.
     * - Las dependencias de soporte usan la MISMA API que la guardia de recetas
     *   fijas ([supportDependencyFor]): un único punto, sin duplicado divergente.
     */
    private fun equipmentAllows(
        configuration: ExerciseConfigurationV2,
        equipment: Set<String>,
        family: String,
        requireExactMachineConfiguration: Boolean,
    ): Boolean {
        val actual = configuration.profile.equipmentId
        if (family == "machine-muscle" && actual != "machine") return false
        if (family == "bodyweight" && actual != "bodyweight") return false
        if (family == "home-training" && actual !in setOf("bodyweight", "band", "dumbbells")) return false
        val machineConfigDeclared = machineConfigToken(configuration.id) in equipment
        if (requireExactMachineConfiguration && actual == "machine" && !machineConfigDeclared) return false
        if (!machineConfigDeclared && "general_gym" !in equipment && actual !in equipment) return false
        val extra = supportDependencyFor(configuration.id)
        return extra == null || "general_gym" in equipment || extra in equipment
    }

    private fun spacedIndices(days: List<Int>, count: Int): Set<Int> {
        if (count >= days.size) return days.indices.toSet()
        return (0 until count).map { it * days.size / count }.toSet()
    }

    private fun focusMuscles(focus: TrainingFocus): Set<String> = when (focus) {
        TrainingFocus.FULL_BODY -> emptySet()
        TrainingFocus.GLUTES -> setOf("Glúteos")
        TrainingFocus.LEGS -> setOf("Cuádriceps", "Isquiosurales")
        TrainingFocus.BACK -> setOf("Dorsales")
        TrainingFocus.CHEST -> setOf("Pectorales")
        TrainingFocus.SHOULDERS -> setOf("Deltoides")
        TrainingFocus.ARMS -> setOf("Bíceps", "Tríceps")
    }

    /**
     * Bolsa de puntos de orden: máximo 2 por músculo, 5 en total y ningún
     * punto negativo (la despriorización no existe). No es obligatorio gastar
     * los cinco. Devuelve null cuando la bolsa no cumple el contrato.
     *
     * La compatibilidad heredada ([PersonalizerInput.priorityMuscles]) aporta
     * 1 punto por músculo solo si no hay bolsa nueva;
     * [PersonalizerInput.lowerEmphasisMuscles] no tiene ningún efecto.
     */
    internal fun orderPoints(input: PersonalizerInput): Map<String, Int>? {
        val source: Map<String, Int> = if (input.exerciseOrderPriorities.isNotEmpty()) {
            input.exerciseOrderPriorities
        } else {
            input.priorityMuscles.associate { canonicalSelection(it) to 1 }
        }
        return orderPointsFromBag(source)
    }

    /**
     * Único efecto de los puntos: el orden de los ejercicios dentro de la
     * sesión. Puntuación 2 → 1 → 0; a igual puntuación se conserva el orden
     * previo (orden estable). No toca ejercicios prescritos, series,
     * repeticiones, intensidades ni frecuencia.
     *
     * Las sesiones generadas no contienen superseries ni bloques técnicos (cada
     * slot es su propio bloque), los calentamientos viven dentro de cada
     * ejercicio y el cardio vive en `parts`: nada de eso se reordena, y las
     * recetas de autor nunca llegan aquí porque se rechazan antes con explicación.
     */
    private fun prioritizeExerciseOrder(slots: List<Slot>, points: Map<String, Int>): List<Slot> {
        if (points.isEmpty()) return slots
        fun score(slot: Slot): Int = slot.candidate.primary.maxOfOrNull { points[it] ?: 0 } ?: 0
        return slots.sortedByDescending { score(it) }
    }

    private sealed class SplitResolution {
        object None : SplitResolution()
        data class Invalid(val reason: String) : SplitResolution()
        data class Ready(val plan: SplitPlan) : SplitResolution()
    }

    /**
     * Restricción real del split: etiqueta de cada día y grupos musculares que
     * pueden entrar en él. Un día con grupos null conserva la composición base
     * (la etiqueta no describe un patrón conocido y se explica en las notas).
     */
    private data class SplitPlan(
        val splitId: String,
        val splitName: String,
        val labelsByDay: Map<Int, String>,
        val groupsByDay: Map<Int, Set<String>?>,
        val notes: List<String> = emptyList(),
    )

    /**
     * Resuelve el split elegido y valida que concuerda con los días, el equipo
     * y el nivel. Solo entra en juego un split visible con receta KPKN
     * (`isVisibleForApplication`); los protocolos de autor no llegan hasta aquí
     * porque conservan su receta y su orden. Si un patrón no puede cumplirse,
     * se rechaza con motivo y no se reescribe ninguna receta.
     */
    private fun resolveSplitPlan(
        input: PersonalizerInput,
        selectedDays: List<Int>,
        candidates: Map<String, Candidate>,
    ): SplitResolution {
        val rawSplitId = input.splitId ?: return SplitResolution.None
        val pattern: List<String>
        val splitName: String
        if (rawSplitId == "custom") {
            pattern = input.splitPattern
            splitName = input.splitName?.takeIf { it.isNotBlank() } ?: "Mi split"
            if (pattern.size != 7) return SplitResolution.Invalid("Un split personalizado debe tener siete posiciones.")
        } else {
            val template = SPLIT_TEMPLATES.firstOrNull { it.id == rawSplitId }
                ?: return SplitResolution.Invalid("El split '$rawSplitId' no existe en el catálogo de splits.")
            if (!template.isVisibleForApplication) {
                return SplitResolution.Invalid("El split '${template.name}' está oculto porque falta una receta verificable día por día.")
            }
            pattern = template.pattern
            splitName = template.name
        }
        // Mismo convenio que SplitApplicationEngine: solo "Descanso" descansa.
        val trainingLabels = if (rawSplitId == "custom") {
            pattern.mapNotNull { label ->
                label.trim().takeIf { it.isNotBlank() && !it.equals("Descanso", ignoreCase = true) }
            }
        } else {
            SplitApplicationEngine.patternToTrainingDays(pattern, startDay = selectedDays.first()).map { it.label }
        }
        if (trainingLabels.size != input.frequency || trainingLabels.size != selectedDays.size) {
            return SplitResolution.Invalid("El split '$splitName' define ${trainingLabels.size} días de entrenamiento y has elegido ${selectedDays.size}. Ajusta los días o el split.")
        }
        val labelsByDay = mutableMapOf<Int, String>()
        val groupsByDay = mutableMapOf<Int, Set<String>?>()
        val notes = mutableListOf<String>()
        for ((index, label) in trainingLabels.withIndex()) {
            val day = selectedDays[index]
            labelsByDay[day] = label
            val groups = dayMuscleGroups(label)
            if (groups == null) {
                notes += "El split '$splitName' no define grupos para el día '$label'; ese día conserva la composición base."
                groupsByDay[day] = null
                continue
            }
            if (candidates.values.none { candidate -> candidate.primary.any { it in groups } }) {
                return SplitResolution.Invalid("El split '$splitName' necesita material para el día '$label' que no has indicado.")
            }
            groupsByDay[day] = groups
        }
        return SplitResolution.Ready(SplitPlan(rawSplitId, splitName, labelsByDay, groupsByDay, notes))
    }

    /**
     * Grupos curados que un día de split puede recibir según su etiqueta.
     * Null = la etiqueta no describe un patrón conocido; ese día no se
     * restringe y se explica en las notas. La semántica de las etiquetas sigue
     * la de los splits (`SplitApplicationEngine`), ampliada con las cadenas
     * anterior/posterior y los patrones de los splits KPKN nativos.
     */
    private fun dayMuscleGroups(label: String): Set<String>? {
        val lower = label.trim().lowercase()
        if (lower.isBlank()) return null
        val push = setOf("Pectorales", "Deltoides", "Tríceps")
        val pull = setOf("Dorsales", "Trapecio", "Bíceps", "Deltoides")
        val legs = setOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas")
        val torso = setOf("Pectorales", "Dorsales", "Trapecio", "Bíceps", "Tríceps", "Deltoides")
        val whole = torso + legs + setOf("Abdomen", "Erectores Espinales")
        val anterior = setOf("Pectorales", "Cuádriceps", "Deltoides", "Abdomen")
        val posterior = setOf("Dorsales", "Isquiosurales", "Glúteos", "Erectores Espinales")
        val groups = linkedSetOf<String>()
        if ("cuerpo completo" in lower || "full" in lower || "sbd" in lower) groups += whole
        if ("empuje" in lower || "push" in lower) groups += push
        if ("tirón" in lower || "tiron" in lower || "pull" in lower || "tracción" in lower || "traccion" in lower) groups += pull
        if ("pierna" in lower || "lower" in lower) groups += legs
        if ("torso" in lower || "upper" in lower) groups += torso
        if ("cadena anterior" in lower || lower == "anterior" || lower.startsWith("anterior ")) groups += anterior
        if ("cadena posterior" in lower || lower == "posterior" || lower.startsWith("posterior ")) groups += posterior
        if ("pecho" in lower || "banca" in lower || "bench" in lower) groups += "Pectorales"
        if ("espalda" in lower) groups += setOf("Dorsales", "Trapecio", "Erectores Espinales")
        if ("peso muerto" in lower || "deadlift" in lower) groups += setOf("Isquiosurales", "Glúteos", "Dorsales", "Erectores Espinales")
        if ("sentadilla" in lower || "squat" in lower) groups += setOf("Cuádriceps", "Glúteos")
        if ("hombro" in lower || "press militar" in lower) groups += "Deltoides"
        if ("brazo" in lower) groups += setOf("Bíceps", "Tríceps")
        if ("cuádriceps" in lower || "cuadriceps" in lower) groups += "Cuádriceps"
        if ("isquios" in lower || "femoral" in lower) groups += "Isquiosurales"
        if ("glúteo" in lower || "gluteo" in lower) groups += "Glúteos"
        if ("pantorrilla" in lower || "gemelo" in lower) groups += "Pantorrillas"
        if ("abdomen" in lower || "core" in lower || "abs" in lower) groups += "Abdomen"
        return groups.takeIf { it.isNotEmpty() }
    }

    private fun curatedPools(): Map<String, List<String>> = linkedMapOf(
        "Pectorales" to listOf("tren_superior_press_pecho_maquina_convergente__default", "bench_press__dumbbells", "bench_press__barbell", "flat_chest_fly__machine", "push_up__flat", "tren_superior_press_banda_resistencia__default"),
        "Dorsales" to listOf("chest_supported_row__machine__medium", "lat_pulldown__bilateral__machine", "back_remo_banda__default", "conventional_row__dumbbells", "lat_pulldown__bilateral__cable", "pull_up__pronated__medium", "back_remo_invertido__default"),
        "Cuádriceps" to listOf("quads_extension_cuadriceps__machine__bilateral", "quads_sentadilla_hack__machine", "quads_prensa_piernas__bilateral", "walking_lunge__dumbbells", "quads_sentadilla_cosaca__default"),
        "Isquiosurales" to listOf("seated_leg_curl__bilateral__machine", "lying_leg_curl__bilateral__machine", "romanian_deadlift__bilateral__barbell", "romanian_deadlift__bilateral__dumbbells", "curl_isquios_con_balon__default", "hams_curl_nordic_peso_corporal__default"),
        "Glúteos" to listOf("hip_thrust__bilateral__machine", "glutes_frog_pumps__default", "hip_thrust__bilateral__barbell", "glutes_patada_gluteo__band"),
        "Deltoides" to listOf("seated_lateral_raise__machine", "standing_lateral_raise__dumbbells", "standing_lateral_raise__cable", "military_press__machine"),
        "Bíceps" to listOf("preacher_curl__machine", "hammer_curl__band", "hammer_curl__dumbbells", "standing_biceps_curl__barbell"),
        "Tríceps" to listOf("triceps_pushdown__bilateral__machine", "triceps_pushdown__bilateral__band", "triceps_pushdown__bilateral__cable", "triceps_flexiones_esfinge__default"),
        "Pantorrillas" to listOf("calf_raise__bilateral__machine"),
        "Abdomen" to listOf("core_crunch_suelo_peso_corporal__default", "core_crunch_maquina__default"),
    )

    companion object {
        /** Contrato de la bolsa de puntos de orden: máximo 2 por músculo y 5 en total. */
        internal const val MAX_ORDER_POINTS_PER_MUSCLE = 2
        internal const val MAX_ORDER_POINTS_TOTAL = 5
        private val hardBodyweight = setOf("pull_up__pronated__medium", "back_remo_invertido__default", "hams_curl_nordic_peso_corporal__default", "quads_sentadilla_cosaca__default")
        private val defaultDays = mapOf(1 to listOf(1), 2 to listOf(1, 4), 3 to listOf(1, 3, 5), 4 to listOf(1, 2, 4, 5), 5 to listOf(1, 2, 3, 5, 6), 6 to listOf(1, 2, 3, 4, 5, 6))
    }
}

/**
 * Contrato único de la bolsa de puntos de orden, compartido entre el motor
 * ([SimpleCyclePersonalizer.orderPoints], usado en la ruta nativa) y
 * [TrainingOptions.validate] (la configuración que el draft aplica).
 * Máximo 2 puntos por músculo, 5 en total y ningún punto negativo; no es
 * obligatorio gastarlos todos. Devuelve la bolsa con los músculos normalizados
 * o null cuando no cumple el contrato.
 */
internal fun orderPointsFromBag(source: Map<String, Int>): Map<String, Int>? {
    val totals = linkedMapOf<String, Int>()
    source.forEach { (raw, points) ->
        if (points < 0) return null
        if (points == 0) return@forEach
        if (points > SimpleCyclePersonalizer.MAX_ORDER_POINTS_PER_MUSCLE) return null
        val muscle = canonicalSelection(raw)
        if (muscle.isBlank()) return@forEach
        val merged = (totals[muscle] ?: 0) + points
        if (merged > SimpleCyclePersonalizer.MAX_ORDER_POINTS_PER_MUSCLE) return null
        totals[muscle] = merged
    }
    if (totals.values.sum() > SimpleCyclePersonalizer.MAX_ORDER_POINTS_TOTAL) return null
    return totals
}

/**
 * Normalización canónica de nombres de músculo usada por la bolsa de orden del
 * motor y de [TrainingOptions]; sinónimos y plurales coloquiales remiten
 * al mismo grupo curado que entiende el presupuesto de volumen.
 */
internal fun canonicalSelection(raw: String): String {
    val normalized = raw.trim().lowercase()
    return when (normalized) {
        "pecho", "pectorales" -> "Pectorales"
        "espalda", "dorsales", "lats" -> "Dorsales"
        "hombros", "deltoides" -> "Deltoides"
        "brazos", "bíceps", "biceps" -> "Bíceps"
        "tríceps", "triceps" -> "Tríceps"
        "piernas", "cuádriceps", "cuadriceps" -> "Cuádriceps"
        "isquios", "isquiosurales", "femorales" -> "Isquiosurales"
        "glúteos", "gluteos" -> "Glúteos"
        "pantorrillas", "gemelos" -> "Pantorrillas"
        "abdomen", "core" -> "Abdomen"
        "trapecio" -> "Trapecio"
        "erectores espinales", "columna" -> "Erectores Espinales"
        else -> VolumeCalculator.normalizeCanonicalMuscleGroup(raw)
    }
}
