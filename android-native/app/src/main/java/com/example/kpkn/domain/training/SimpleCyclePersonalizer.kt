package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.*
import com.example.kpkn.data.programs.*
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

    fun personalize(programId: String, input: PersonalizerInput): PersonalizationResult {
        val entry = PersonalizedPlanCatalog.find(input.catalogEntryId)
        val provenance = CatalogProvenance(input.catalogEntryId, PersonalizedPlanCatalog.REVISION, entry?.source ?: CatalogSource.NATIVE, entry?.sourceId ?: input.catalogEntryId)
        fun unavailable(message: String) = PersonalizationResult(null, PersonalizationReport(false, CatalogClassification.SIMPLE, listOf(message), emptyList(), provenance))
        if (programId.isBlank() || entry == null) return unavailable("Selecciona un plan disponible.")
        if (entry.adaptation != AdaptationPolicy.CURATED_WEEKLY) return unavailable("Este método conserva su receta original; aplícalo sin personalización libre.")
        if (input.frequency !in entry.supportedFrequencies) return unavailable("Este plan no está curado para esa frecuencia.")
        if (input.availableMinutes !in 20..100) return unavailable("Elige entre 20 y 100 minutos por sesión.")
        if (input.weekdays.isNotEmpty() && (input.weekdays.size != input.frequency || input.weekdays.distinct().size != input.frequency || input.weekdays.any { it !in 1..7 })) return unavailable("Selecciona exactamente los días que quieres entrenar.")
        val ready = catalog?.state?.value as? ExerciseCatalogStateV2.Ready ?: return unavailable("El catálogo todavía no está disponible. Vuelve a intentarlo.")
        val equipment = normalizeEquipment(input.equipment)
        val cardio = input.cardio ?: if (entry.sourceId == "strength-cardio") CardioPreference(CardioType.WALK, 15) else null
        if (cardio != null && (cardio.minutes !in 5..60 || cardio.minutes >= input.availableMinutes)) return unavailable("Reserva tiempo tanto para fuerza como para cardio.")
        if (cardio != null && cardio.type !in setOf(CardioType.WALK, CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR) && "general_gym" !in equipment && "cardio" !in equipment) return unavailable("El cardio seleccionado necesita un aparato que no has indicado.")
        val lookup = ready.catalog.toLegacyConfigurationLookup()
        val pools = curatedPools()
        val allowedIds = pools.values.flatten().toSet()
        val candidates = ready.catalog.families.flatMap { it.definitions }.flatMap { definition ->
            definition.configurations.mapNotNull { configuration ->
                if (configuration.id !in allowedIds || configuration.evidence.reviewStatus != CatalogReviewStatusV2.APPROVED) return@mapNotNull null
                if (!equipmentAllows(configuration, equipment, entry.sourceId)) return@mapNotNull null
                if (input.level == CatalogLevel.BEGINNER && (configuration.profile.technicalDifficulty > 5.2 || configuration.id in hardBodyweight)) return@mapNotNull null
                val info = lookup[configuration.id.lowercase()] ?: return@mapNotNull null
                val exercise = exercise(info, "probe", 1, input.level)
                val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(listOf(Session("probe", "probe", exercises = listOf(exercise))), listOf(info))
                Candidate(info, configuration, volume)
            }
        }.associateBy { it.id }
        if (candidates.isEmpty()) return unavailable("No hay una variante curada compatible con tu equipo y experiencia.")
        val focused = focusMuscles(input.focus)
        val focusCandidates = candidates.values.filter { candidate -> candidate.primary.any { it in focused } }
        if (focused.isNotEmpty() && focusCandidates.isEmpty()) return unavailable("Tu equipo no permite trabajar ese enfoque con una variante curada. Cambia de enfoque o añade material.")
        val days = input.weekdays.sorted().ifEmpty { defaultDays.getValue(input.frequency) }
        val budgets = budgets(input, focused)
        val slots = List(days.size) { mutableListOf<Slot>() }
        val totals = mutableMapOf<String, Double>()
        val notes = mutableListOf<String>()
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
        fun options(muscle: String, dayIndex: Int): List<Candidate> {
            val curated = pools[muscle].orEmpty().mapNotNull(candidates::get)
            val rotated = if (curated.isEmpty()) curated else curated.drop(dayIndex % curated.size) + curated.take(dayIndex % curated.size)
            return rotated.filter { muscle in it.primary }.sortedBy { candidate ->
                candidate.volume.filterKeys { it != muscle }.values.sumOf { it.directSets + it.indirectSets }
            }
        }
        slots.indices.forEach { index ->
            val base = if (days.size >= 4) {
                if (index % 2 == 0) listOf("Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps")
                else listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen")
            } else listOf("Pectorales", "Dorsales", "Cuádriceps", "Isquiosurales")
            val ordered = ((if (index in priorityDays) focused.toList() else emptyList()) + base).distinct()
            ordered.forEach { muscle ->
                options(muscle, index).firstOrNull { canAdd(index, it) }?.let { add(index, it) }
            }
        }
        repeat(180) {
            val choice = slots.indices.flatMap { index ->
                val permitted = slots[index].flatMap { it.candidate.primary }.toSet()
                candidates.values.filter { candidate -> candidate.primary.any { it in permitted } && canAdd(index, candidate) }.map { candidate ->
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
        if (slots.any { it.size < 3 }) return unavailable("No se puede completar una sesión equilibrada con ese equipo, enfoque y tiempo. Amplía el tiempo o el material disponible.")
        slots.indices.forEach { index ->
            val total = slots[index].sumOf { it.sets }
            if (total < 10) notes += "Día ${index + 1}: $total series para respetar tu presupuesto de recuperación."
        }
        if (days.size > 1 && days.indices.any { i -> (days[(i + 1) % days.size] - days[i] + 7) % 7 == 1 }) {
            notes += "Hay días consecutivos, también al repetir la semana. Revisa tu recuperación antes de entrenar."
        }
        val sessions = slots.mapIndexed { index, daySlots ->
            val ordered = daySlots.sortedWith(compareBy<Slot> { slot -> if (slot.candidate.primary.any { it in focused }) 0 else 1 }
                .thenBy { if (it.candidate.configuration.profile.articulationType?.name == "MULTIARTICULAR") 0 else 1 })
            val exercises = ordered.mapIndexed { exerciseIndex, slot -> exercise(slot.candidate.info, "$programId-s$index-e$exerciseIndex", slot.sets, input.level) }
            val cardioExercise = cardio?.let {
                Exercise(id = "$programId-s$index-cardio", name = it.type.name.lowercase().replace('_', ' '),
                    cardioDetails = CardioDetails(type = it.type, intensity = it.intensity, targetDurationSeconds = it.minutes * 60))
            }
            Session(
                id = "$programId-session-$index", name = "Día ${index + 1}", dayOfWeek = days[index], assignedDays = listOf(days[index]),
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
            autoregulationMode = AutoregulationMode.PROPOSE,
            tags = listOf("KPKN_NATIVE", input.focus.name),
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
            val target = if (calibrated) mev + (minOf(mav, mrv) - mev) * if (muscle in focused) 0.875 else 0.4
                else minOf(mav, mrv) * if (muscle in focused) 0.65 else 0.5
            Budget(mev, mav, mrv, target, personal?.frequencyCap?.coerceIn(1, 6) ?: 3)
        }
    }

    private fun normalizeEquipment(source: Set<String>): Set<String> = source.map {
        when (it) { "bands" -> "band"; "smith" -> "smith_machine"; else -> it }
    }.toSet()

    private fun equipmentAllows(configuration: ExerciseConfigurationV2, equipment: Set<String>, family: String): Boolean {
        val actual = configuration.profile.equipmentId
        if (family == "machine-muscle" && actual != "machine") return false
        if (family == "bodyweight" && actual != "bodyweight") return false
        if (family == "home-training" && actual !in setOf("bodyweight", "band", "dumbbells")) return false
        if ("general_gym" !in equipment && actual !in equipment) return false
        val extra = when (configuration.id) {
            "pull_up__pronated__medium", "pull_up__supinated__medium" -> "pull_up_bar"
            "back_remo_invertido__default", "hams_curl_nordic_peso_corporal__default" -> "support"
            "curl_isquios_con_balon__default" -> "ball"
            else -> null
        }
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
        private val hardBodyweight = setOf("pull_up__pronated__medium", "back_remo_invertido__default", "hams_curl_nordic_peso_corporal__default", "quads_sentadilla_cosaca__default")
        private val defaultDays = mapOf(1 to listOf(1), 2 to listOf(1, 4), 3 to listOf(1, 3, 5), 4 to listOf(1, 2, 4, 5), 5 to listOf(1, 2, 3, 5, 6), 6 to listOf(1, 2, 3, 4, 5, 6))
    }
}
