package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.definitions.NativeArchetypeSlot
import com.example.kpkn.data.protocols.definitions.NativeCandidateTable
import com.example.kpkn.data.protocols.definitions.NativeCardioDefaults
import com.example.kpkn.data.protocols.definitions.NativeCardioRole
import com.example.kpkn.data.protocols.definitions.NativeDayArchetype
import com.example.kpkn.data.protocols.definitions.NativeDose
import com.example.kpkn.data.protocols.definitions.NativeDoseLevel
import com.example.kpkn.data.protocols.definitions.NativeDoseTable
import com.example.kpkn.data.protocols.definitions.NativeMinimumDose
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.data.protocols.definitions.NativeWeekBuilder
import com.example.kpkn.data.programs.*
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
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
    /**
     * Motivo tipado §15.2 cuando el programa no se pudo publicar (`TIME_BUDGET`,
     * `COMPOSITION`…); null cuando todo encajó o el motivo es de otra capa.
     * Los planes propios lo rellenan en la ruta nativa.
     */
    val reasonCode: String? = null,
    /** Máximo de minutos por sesión estimado con el estimador común (§12.2). */
    val maxSessionMinutes: Int? = null,
    /**
     * B-02: músculos de un plan propio entregado cuyo volumen semanal pasa su límite pero cabe en la
     * banda de «volumen alto» (hoy solo glúteos, entre 16 y 17,5 series). Vacío = ninguno; cada aviso
     * lleva las series máximas de la semana, el límite recomendado y el techo.
     */
    val highVolume: List<HighVolumeNotice> = emptyList(),
    /**
     * B-03: notas del plan en lenguaje llano para enseñar tal cual en la revisión del wizard
     * (p. ej. por qué el puente de glúteo se hace una vez por semana). Subconjunto de
     * [limitations]; vacío si el plan no trae ninguna.
     */
    val planNotes: List<String> = emptyList(),
)

/** B-03 (antes DEV-r2-01, con jerga): Músculo corporal de 5–6 días hace el puente de glúteo una vez por semana. */
const val GLUTE_BRIDGE_ONCE_NOTE: String =
    "Hacemos el puente de glúteo 1 vez por semana para no pasar de 16 series de glúteo; " +
        "los otros días lo cambiamos por flexión de isquiotibiales."

/** B-03: Atleta corporal de 6 días cambia el puente de glúteo por una segunda flexión de isquiotibiales. */
const val ATHLETE_SIX_DAY_BRIDGE_NOTE: String =
    "Con 6 días hacemos una segunda flexión de isquiotibiales en lugar del puente de glúteo, " +
        "para no pasar de 16 series de glúteo; las flexiones equivalentes se juntan hasta 4 series. " +
        "Se mantienen potencia, fuerza relativa, hipertrofia y cardio; no se presenta como plan de tirón " +
        "ni de sentadilla, banca y peso muerto."
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

class SimpleCyclePersonalizer(
    private val catalog: ExerciseCatalogRepositoryV2? = null,
    /**
     * Holgura de glúteos sobre su límite semanal (B-02, solo planes propios). Producción usa siempre el
     * valor por defecto ([GLUTES_SOFT_BAND]); 0,0 reproduce el comportamiento anterior (sin tolerancia) y
     * existe para que las pruebas comparen «antes» y «después» con el mismo generador.
     */
    private val glutesSoftBand: Double = GLUTES_SOFT_BAND,
) {
    init {
        // La política solo tolera hasta el techo blando: el ajustador nunca puede aceptar más que ella.
        require(glutesSoftBand in 0.0..GLUTES_SOFT_BAND) {
            "La holgura de glúteos debe estar entre 0 y $GLUTES_SOFT_BAND series"
        }
    }

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
    ): PersonalizationResult = personalizeAt(programId, input, options, probeMinimumMinutes = true)

    /**
     * Cuerpo de [personalize]. [probeMinimumMinutes] solo lo activa la llamada pública: el sondeo
     * de [minimumViableMinutes] vuelve aquí con `false`, así que nunca se anida un sondeo dentro
     * de otro.
     */
    private fun personalizeAt(
        programId: String,
        input: PersonalizerInput,
        options: TrainingOptions,
        probeMinimumMinutes: Boolean,
    ): PersonalizationResult {
        val entry = PersonalizedPlanCatalog.find(input.catalogEntryId)
        val provenance = CatalogProvenance(input.catalogEntryId, PersonalizedPlanCatalog.REVISION, entry?.source ?: CatalogSource.NATIVE, entry?.sourceId ?: input.catalogEntryId)
        fun unavailable(message: String, reasonCode: String? = null, maxMinutes: Int? = null) = PersonalizationResult(
            null,
            PersonalizationReport(
                false, CatalogClassification.SIMPLE, listOf(message), emptyList(), provenance,
                reasonCode = reasonCode, maxSessionMinutes = maxMinutes,
            ),
        )
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
        val nativeKind = NativeProfileKind.fromEntryId(entry.id)
        if (cardio != null && (
                cardio.minutes !in 5..60 ||
                    (cardio.minutes >= input.availableMinutes && nativeKind != NativeProfileKind.COMPLETE_ATHLETE)
            )
        ) return unavailable("Reserva tiempo tanto para fuerza como para cardio.")
        if (cardio != null && cardio.type !in setOf(CardioType.WALK, CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR) && "general_gym" !in equipment && "cardio" !in equipment) return unavailable("El cardio seleccionado necesita un aparato que no has indicado.")
        // ─── Los cuatro planes propios §11–§12 (paquete F, T-004a) ────────────
        // Su calendario, dosis y progresión salen de las tablas §11.1–§12.4; la
        // ruta de hipertrofia de abajo solo sigue para los nativos históricos.
        if (nativeKind != null) {
            return personalizeNative(
                programId = programId,
                input = input,
                options = options,
                entry = entry,
                kind = nativeKind,
                equipment = equipment,
                cardio = cardio,
                ready = ready,
                provenance = provenance,
                unavailable = { message -> unavailable(message) },
            )
        }
        val lookup = ready.catalog.toLegacyConfigurationLookup()
        val compositionMetadata = CatalogCompositionMetadataProvider.fromCatalog(ready.catalog)
        val pools = curatedPools()
        val allowedIds = pools.values.flatten().toSet()
        // Exigencia de configuración exacta: hay una declaración CONCRETA de
        // aparatos/soportes (presencia por clave) o inventario declarado sin
        // disponibilidad nueva. Una disponibilidad puramente categórica sigue
        // permitiendo variantes máquina nativas aprobadas.
        val requireExactMachineConfiguration = options.availability?.hasExplicitPresence ?: (options.inventory != null)
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
        // §12.2 (AC-T004-03): el tiempo que se promete es el del estimador común
        // sobre la sesión REAL —3 min generales, preparación por ejercicio, series,
        // descansos, aproximaciones del preset y cardio—, no solo la cota rápida
        // `minutes`, que ignora casi todo eso. Sin este segundo filtro un día lleno
        // según la cota medía 61 o 67 min con un presupuesto de 60 y el evaluador
        // lo rechazaba con TIME_BUDGET aunque quitar una serie lo resolvía. El
        // filtro solo restringe y el estimador crece al añadir series/ejercicios,
        // así que un plan que ya cabía se genera exactamente igual.
        val planWarmupSteps = options.resolvedWarmupSteps()
        fun buildDaySession(index: Int, daySlots: List<Slot>): Session {
            val baseOrder = daySlots.sortedWith(compareBy<Slot> { slot -> if (slot.candidate.primary.any { it in focused }) 0 else 1 }
                .thenBy { if (it.candidate.configuration.profile.articulationType?.name == "MULTIARTICULAR") 0 else 1 })
            val ordered = prioritizeExerciseOrder(baseOrder, exerciseOrderPoints)
            // Preset del plan (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de
            // trabajo) en el primer compuesto de cada patrón, tal y como queda
            // ordenado: la misma política de PlanMaterializer para la ruta nativa
            // RIR. Vacío = sin aproximaciones automáticas y sin mezclarse con
            // calentamientos que traiga la receta de autor (aquí no llegan).
            val firstCompounds = if (planWarmupSteps.isEmpty()) {
                emptySet()
            } else {
                firstCompoundConfigurationIds(ordered, compositionMetadata)
            }
            val exercises = ordered.mapIndexed { exerciseIndex, slot ->
                val base = exercise(slot.candidate.info, "$programId-s$index-e$exerciseIndex", slot.sets, input.level)
                if (slot.candidate.configuration.id in firstCompounds) {
                    base.copy(warmupSets = presetWarmupDefinitions(planWarmupSteps, base.id))
                } else {
                    base
                }
            }
            val cardioExercise = cardio?.let {
                Exercise(id = "$programId-s$index-cardio", name = it.type.name.lowercase().replace('_', ' '),
                    cardioDetails = CardioDetails(type = it.type, intensity = it.intensity, targetDurationSeconds = it.minutes * 60))
            }
            return Session(
                id = "$programId-session-$index", name = customLabelsByDay[days[index]] ?: "Día ${index + 1}", dayOfWeek = days[index], assignedDays = listOf(days[index]),
                exercises = exercises,
                parts = cardioExercise?.let { listOf(SessionPart("$programId-cardio-$index", "Cardio", exercises = listOf(it), isCardioGroup = true)) }.orEmpty(),
                focus = input.focus.name, origin = SessionOrigin.USER_DRAFT,
            )
        }
        // El estimador solo depende de qué ejercicios hay y de cuántas series
        // llevan (no de su orden ni de los ids), así que se memoriza por contenido.
        val estimatedMinutesCache = HashMap<List<Pair<String, Int>>, Int>()
        fun estimatedDayMinutes(index: Int, daySlots: List<Slot>): Int =
            estimatedMinutesCache.getOrPut(daySlots.map { it.candidate.id to it.sets }) {
                SessionDurationEstimator.estimate(buildDaySession(index, daySlots)).totalMinutes
            }
        fun canAdd(dayIndex: Int, candidate: Candidate): Boolean {
            val daySlots = slots[dayIndex]
            val existing = daySlots.firstOrNull { it.candidate.id == candidate.id }
            if (existing == null && daySlots.size >= 7) return false
            if ((existing?.sets ?: 0) >= 4 || daySlots.sumOf { it.sets } >= 30) return false
            if (minutes(daySlots, 1, existing == null) > input.availableMinutes) return false
            val withinBudgets = candidate.volume.all { (muscle, contribution) ->
                val budget = budgets[muscle] ?: return@all false
                val total = contribution.directSets + contribution.indirectSets
                val directInDay = daySlots.sumOf { it.sets * (it.candidate.volume[muscle]?.directSets ?: 0.0) }
                val hasDirect = daySlots.any { muscle in it.candidate.primary }
                val frequency = slots.count { day -> day.any { muscle in it.candidate.primary } }
                (totals[muscle] ?: 0.0) + total <= minOf(budget.mav, budget.mrv) + 0.0001 &&
                    directInDay + contribution.directSets <= 12.0 &&
                    (contribution.directSets == 0.0 || hasDirect || frequency < budget.frequencyCap)
            }
            if (!withinBudgets) return false
            // Medida real del día con el candidato añadido; va la última por ser el
            // filtro más caro (solo se mide lo que ya encaja en volumen).
            val trial = if (existing == null) {
                daySlots + Slot(candidate, 1)
            } else {
                daySlots.map { if (it === existing) Slot(it.candidate, it.sets + 1) else it }
            }
            return estimatedDayMinutes(dayIndex, trial) <= input.availableMinutes
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
        // Relleno honesto de variedad (T-004): cuando un día SIN split no alcanza
        // el mínimo de ejercicios con sus grupos base —p. ej. principiante sin
        // material, cuyos grupos Dorsales/Isquiosurales no tienen variante curada
        // viable sin apoyo— se completa desde las demás piscinas CURADAS, ANTES
        // de repartir series para que el mínimo de variedad no quede fuera del
        // presupuesto de minutos. Respeta [canAdd] (tiempo, presupuestos,
        // frecuencia) y no toca techo de dificultad, equipo declarado, recetas ni
        // la restricción de grupos de un split aplicado: solo amplía qué grupos
        // pueden rellenar una sesión.
        val paddingDiagnostics = StringBuilder()
        slots.indices.forEach { index ->
            if (dayGroupsByDay[days[index]] != null) return@forEach
            if (slots[index].size >= 3) return@forEach
            val paddingOrder = listOf(
                "Glúteos", "Abdomen", "Tríceps", "Pectorales", "Cuádriceps",
                "Deltoides", "Bíceps", "Pantorrillas", "Dorsales", "Isquiosurales",
                "Erectores Espinales",
            )
            for (muscle in paddingOrder) {
                if (slots[index].size >= 3) break
                val poolCandidates = muscleCandidates(muscle, index)
                val chosen = poolCandidates
                    .firstOrNull { candidate -> slots[index].none { it.candidate.id == candidate.id } && canAdd(index, candidate) }
                if (chosen == null) {
                    paddingDiagnostics.append(" dia${days[index]}:$muscle(cand=${poolCandidates.size})")
                }
                chosen?.let { add(index, it) }
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
            val detail = slots.indices.joinToString("; ") { index ->
                "dia${days[index]}=${slots[index].size}"
            } + paddingDiagnostics.toString().replace(Regex("\\(cand=\\d+\\)"), "")
            // Razón TIPADA del rechazo temporal: si TODA la combinación válida se queda sin
            // sesión equilibrada a estos minutos pero la misma entrada sí produce programa con
            // más minutos, el límite es el tiempo y no el material. El sondeo corre solo aquí
            // (resultado final «sin candidatos»), una vez por llamada pública, y devuelve el
            // primer presupuesto viable; si ni 100 min bastan, el rechazo no es de tiempo y el
            // mensaje conserva su clasificación por texto (material/enfoque).
            val minimumMinutes = if (probeMinimumMinutes) minimumViableMinutes(programId, input, options) else null
            if (minimumMinutes != null) {
                return unavailable(
                    if (splitPlan != null) "El split '${splitPlan.splitName}' no permite completar las sesiones con ${input.availableMinutes} min por sesión: el mínimo real con tus respuestas es de $minimumMinutes min. Amplía el tiempo o elige otro plan. $detail"
                    else "No se puede completar una sesión equilibrada con ${input.availableMinutes} min por sesión: el mínimo real de este plan con tus respuestas es de $minimumMinutes min. Amplía el tiempo o elige otro plan. $detail",
                    reasonCode = "TIME_BUDGET",
                    maxMinutes = minimumMinutes,
                )
            }
            return unavailable(
                if (splitPlan != null) "El split '${splitPlan.splitName}' no permite completar las sesiones con tu tiempo, equipo y presupuesto de recuperación. Amplía el tiempo o el material disponible. $detail"
                else "No se puede completar una sesión equilibrada con ese equipo, enfoque y tiempo. Amplía el tiempo o el material disponible. $detail"
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
        // La duración sellada es la medida con el estimador común (§12.2): la misma
        // que usan canAdd, el evaluador y la vista previa.
        val sessions = slots.mapIndexed { index, daySlots ->
            buildDaySession(index, daySlots).copy(targetDurationMinutes = estimatedDayMinutes(index, daySlots))
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
        val stored = ProgramPersistNormalizer.forRoomStorage(program)
        return PersonalizationResult(stored, PersonalizationReport(true, CatalogClassification.SIMPLE, notes.distinct(), reports, provenance))
    }

    /**
     * Mínimo de `availableMinutes` (por encima del pedido y hasta el techo de 100) con el que la
     * MISMA entrada —mismo plan, días, material, nivel, split y opciones— sí produce programa, o
     * null si ningún presupuesto lo consigue. Cada prueba es una llamada completa a
     * [personalizeAt], o sea el mismo generador y el mismo estimador de duración que el resultado
     * final: no hay otro estimador. Una prueba que lanza `IllegalArgumentException` o
     * `IllegalStateException` (p. ej. el contrato de ejecución) cuenta como NO viable, igual que
     * lo sería la llamada real; `CancellationException` se propaga siempre.
     */
    private fun minimumViableMinutes(programId: String, input: PersonalizerInput, options: TrainingOptions): Int? =
        firstViableMinutes(input.availableMinutes) { minutes ->
            try {
                personalizeAt(programId, input.copy(availableMinutes = minutes), options, probeMinimumMinutes = false)
                    .program != null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }

    // ─── Cuatro planes propios §11–§12 (paquete F, T-004a) ───────────────────

    /** Slot resuelto de un día propio (§11.2) antes de materializar semanas. */
    private data class NativeSlotPlan(
        val slotId: String,
        val key: NativeSlotKey,
        val intent: SlotIntent,
        val configurationId: String,
        val equipmentId: String,
        val replacementGroup: String?,
        val role: SlotRole,
        val liftSlot: LiftSlot?,
        val isCompetitionLift: Boolean,
        val technique: TechniqueModifier?,
        var supplementalOf: String?,
        var essential: Boolean,
        var sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        /** RIR editorial fijo para fuerza relativa corporal de Atleta (§11.4). */
        val rirOverride: Int? = null,
        var warmup: Boolean,
        val restSeconds: Int,
        var removed: Boolean = false,
    )
    /** Día resuelto: arquetipo §11.3/§11.4 + cardio §11.4 + sesión §14.3. */
    private data class NativeDayPlan(
        val archetype: NativeDayArchetype,
        val label: String,
        val weekday: Int,
        val slots: MutableList<NativeSlotPlan>,
        val sessionKind: RecipeSessionKind,
        val cardioBlock: RecipeCardioBlock?,
        val priority: SlotPriority,
    )

    /** Intento de fitter: receta + programa materializado + minutos por día. */
    private data class NativeFitAttempt(
        val recipe: TrainingPlanRecipe,
        val program: Program,
        /** Minutos de la semana 1, en orden de día (las semanas 1–5 comparten estructura). */
        val minutesByDay: List<Int>,
        /** Minutos de TODAS las sesiones materializadas (máximo real §12.2). */
        val allSessionMinutes: List<Int>,
        /** Exceso real sobre MRV, ponderado por el estimador compartido de volumen. */
        val overMrv: Map<String, Double>,
        /**
         * B-02: exceso sobre el TECHO BLANDO de cada músculo (el MRV, salvo glúteos, que admiten
         * +[GLUTES_SOFT_BAND]). Vacío = el plan cabe en el límite o en la banda de «volumen alto»;
         * solo un exceso aquí obliga a rechazar el plan. El recorte sigue guiándose por [overMrv].
         */
        val overSoftCeiling: Map<String, Double> = overMrv,
        /** B-02: músculos que quedaron en la banda de «volumen alto» (pasan su MRV sin superar el techo blando). */
        val highVolume: List<HighVolumeNotice> = emptyList(),
    ) {
        val maxMinutes: Int get() = allSessionMinutes.maxOrNull() ?: 1
        val totalMrvExcess: Double get() = overMrv.values.sum()
    }

    /**
     * Ruta de los cuatro planes propios (§11–§12): gating de material por
     * disciplina §11.1, calendarios §11.3/§11.4 por día 1..6, dosis §11.2 por
     * nivel, fitter determinista §12.3 y seis semanas con descarga §12.1.
     * Todo se materializa con [PlanMaterializer] y se valida con
     * `ProgramRecipeValidator` (perfil de composición §14.3) antes de publicar.
     */
    private fun personalizeNative(
        programId: String,
        input: PersonalizerInput,
        options: TrainingOptions,
        entry: CatalogEntry,
        kind: NativeProfileKind,
        equipment: Set<String>,
        cardio: CardioPreference?,
        ready: ExerciseCatalogStateV2.Ready,
        provenance: CatalogProvenance,
        unavailable: (String) -> PersonalizationResult,
    ): PersonalizationResult {
        fun fail(message: String, reasonCode: String? = null, maxMinutes: Int? = null) = PersonalizationResult(
            null,
            PersonalizationReport(
                executable = false,
                classification = CatalogClassification.SIMPLE,
                limitations = listOf(message),
                muscles = emptyList(),
                provenance = provenance,
                reasonCode = reasonCode,
                maxSessionMinutes = maxMinutes,
            ),
        )

        // §11.1: la disciplina decide el requisito mínimo de material ANTES de
        // construir nada. Un `general_gym` genérico sirve para leer/usar
        // programas legacy, pero no confirma el SBD competitivo requerido.
        val legacyGym = "general_gym" in equipment
        fun has(token: String) = token in equipment
        val barbellOk = has("barbell")
        val rackOk = has("rack")
        val benchOk = has("bench")
        val dumbbellsOk = has("dumbbells")
        when (kind) {
            NativeProfileKind.STRENGTH -> {
                val missing = buildList {
                    if (!barbellOk) add("barra y carga")
                    if (!rackOk) add("rack")
                    if (!benchOk) add("banco")
                }
                if (missing.isNotEmpty()) {
                    return fail(
                        "Este perfil trabaja sentadilla, banca y peso muerto con barra. Falta ${missing.joinToString(", ")}.",
                        reasonCode = "APPARATUS_ABSENT",
                    )
                }
            }
            NativeProfileKind.POWERBUILDING -> {
                if (!(barbellOk && rackOk && benchOk) && !dumbbellsOk) {
                    return fail(
                        "Fuerza y músculo necesita resistencia externa para sus principales: barra con rack y banco, o mancuernas. " +
                            "Con solo bandas o peso corporal te ofrecemos Músculo o Atleta completo.",
                        reasonCode = "PROFILE_MISMATCH",
                    )
                }
            }
            else -> Unit
        }
        val pullAvailable = legacyGym || barbellOk || dumbbellsOk || has("band") ||
            has("cable") || has("pull_up_bar") || equipment.any { it.startsWith("machine_config:") }
        // La bolsa de prioridades sigue siendo SOLO orden y sigue con su contrato.
        val exerciseOrderPoints = orderPoints(options.applyTo(input))
            ?: return unavailable(
                "La bolsa de prioridades de orden no es válida: máximo 2 puntos por músculo, 5 puntos en total y ningún punto negativo. Ajusta tus prioridades.",
            )

        val doseLevel = when (input.level) {
            CatalogLevel.BEGINNER -> NativeDoseLevel.BEGINNER
            CatalogLevel.INTERMEDIATE -> NativeDoseLevel.INTERMEDIATE
            CatalogLevel.ADVANCED -> NativeDoseLevel.ADVANCED
        }
        val selectedDays = input.weekdays.sorted()
            .ifEmpty { NativeProfileCalendars.DEFAULT_WEEKDAYS.getValue(input.frequency) }
        val archetypes = when (kind) {
            NativeProfileKind.STRENGTH -> NativeProfileCalendars.strength(selectedDays.size)
            NativeProfileKind.MUSCLE -> NativeProfileCalendars.muscle(selectedDays.size, pullAvailable)
            NativeProfileKind.POWERBUILDING -> NativeProfileCalendars.powerbuilding(selectedDays.size)
            NativeProfileKind.COMPLETE_ATHLETE -> NativeProfileCalendars.athlete(selectedDays.size, pullAvailable)
        }
        val startDay = selectedDays.first()
        // Identidad de calendario (§14.3): el día de receta rota con la misma
        // regla de `PlanMaterializer.rotateWeekday` para caer en el día elegido.
        fun recipeWeekday(day: Int) = ((day - startDay).mod(7)) + 1
        val requireExactMachineConfiguration = options.availability?.hasExplicitPresence ?: (options.inventory != null)
        // §13.1: el peso corporal no necesita aparato y siempre está disponible.
        val nativeEquipment = equipment + "bodyweight"
        val approvedConfigurations = ready.catalog.families.flatMap { it.definitions }
            .flatMap { it.configurations }
            .filter { it.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED }
            .associateBy { it.id }

        fun resolveConfiguration(key: NativeSlotKey, intent: SlotIntent): ExerciseConfigurationV2? {
            // §11.5: el principiante aprende la intención rápida con la variante
            // corporal (sentadilla sin salto / flexión de rodillas).
            val candidateIds = when {
                input.level == CatalogLevel.BEGINNER && key == NativeSlotKey.PS ->
                    listOf(NativeCandidateTable.PS_BODYWEIGHT)
                input.level == CatalogLevel.BEGINNER && key == NativeSlotKey.PB ->
                    listOf(NativeCandidateTable.PB_BODYWEIGHT)
                else -> NativeCandidateTable.candidatesFor(key, intent)
            }
            return candidateIds.firstNotNullOfOrNull { configurationId ->
                val configuration = approvedConfigurations[configurationId] ?: return@firstNotNullOfOrNull null
                if (input.level == CatalogLevel.BEGINNER &&
                    !NativeCandidateTable.beginnerDifficultyExempt(key) &&
                    (configuration.profile.technicalDifficulty > 5.2 || configuration.id in hardBodyweight)
                ) {
                    return@firstNotNullOfOrNull null
                }
                if (!equipmentAllows(configuration, nativeEquipment, entry.sourceId, requireExactMachineConfiguration)) {
                    return@firstNotNullOfOrNull null
                }
                configuration
            }
        }

        fun competitionLiftSlot(key: NativeSlotKey, configurationId: String): LiftSlot? = when {
            key == NativeSlotKey.S && configurationId == CatalogIds.SQ_LOW -> LiftSlot.SQUAT
            key == NativeSlotKey.B && configurationId == CatalogIds.BP -> LiftSlot.BENCH
            key == NativeSlotKey.D && configurationId == CatalogIds.DL -> LiftSlot.DEADLIFT
            else -> null
        }

        val compositionMetadata = CatalogCompositionMetadataProvider.fromCatalog(ready.catalog)
        val notes = mutableListOf<String>()
        // B-03: notas del plan escritas en lenguaje llano; la revisión del wizard las muestra tal cual.
        val plainNotes = mutableListOf<String>()
        val dayPlans = archetypes.mapIndexed { index, archetype ->
            val sessionKind = when (archetype.cardio) {
                NativeCardioRole.NONE -> RecipeSessionKind.STRENGTH
                NativeCardioRole.AFTER_STRENGTH, NativeCardioRole.AFTER_STRENGTH_BRIEF -> RecipeSessionKind.STRENGTH_CARDIO
                NativeCardioRole.DEDICATED_WITH_ACCESSORIES -> RecipeSessionKind.CARDIO_ACCESSORY
                NativeCardioRole.ONLY -> RecipeSessionKind.CARDIO
            }
            val claimedFamilies = mutableSetOf<String>()
            val slots = mutableListOf<NativeSlotPlan>()
            archetype.slots.forEach { spec ->
                val configuration = resolveConfiguration(spec.key, spec.intent) ?: return@forEach
                if (spec.key == NativeSlotKey.V && configuration.id == NativeCandidateTable.LOW_BAR_ROW) {
                    notes += "Día ${index + 1}: V se adapta a tracción horizontal con remo invertido; requiere barra baja confirmada (§13.3)."
                }
                val bodyweightRepRange = spec.intent == SlotIntent.H &&
                    configuration.profile.equipmentId in setOf("bodyweight", "band")
                val relativeBodyweightStrength = kind == NativeProfileKind.COMPLETE_ATHLETE &&
                    configuration.profile.equipmentId == "bodyweight" &&
                    spec.intent in setOf(SlotIntent.F, SlotIntent.FV)
                val dose = if (relativeBodyweightStrength) {
                    NativeDose(
                        role = if (spec.intent == SlotIntent.F) SlotRole.T1_MAIN else SlotRole.T2_SUPPLEMENTAL,
                        sets = 2,
                        repsMin = 5,
                        repsMax = 8,
                        rir = 3,
                        restSeconds = if (spec.intent == SlotIntent.F) {
                            NativeDoseTable.HEAVY_T1_REST_SECONDS
                        } else {
                            NativeDoseTable.T2_REST_SECONDS
                        },
                    )
                } else {
                    NativeDoseTable.doseFor(spec.intent, doseLevel, bodyweightRepRange)
                }
                val liftSlot = competitionLiftSlot(spec.key, configuration.id)
                val family = COMPOUND_FAMILY_BY_SLOT[spec.key]
                slots += NativeSlotPlan(
                    slotId = spec.key.name.lowercase(),
                    key = spec.key,
                    intent = spec.intent,
                    configurationId = configuration.id,
                    equipmentId = configuration.profile.equipmentId,
                    replacementGroup = configuration.profile.replacementGroup?.takeIf { it.isNotBlank() },
                    role = dose.role,
                    liftSlot = liftSlot,
                    isCompetitionLift = liftSlot != null && spec.intent == SlotIntent.F,
                    technique = if (spec.intent == SlotIntent.P) TechniqueModifier.SPEED else null,
                    supplementalOf = null,
                    essential = false,
                    sets = dose.sets,
                    repsMin = dose.repsMin,
                    repsMax = dose.repsMax,
                    rirOverride = null,
                    warmup = false,
                    restSeconds = dose.restSeconds,
                )
            }
            // Regla de los días de dos ejercicios de Fuerza (§11.3): el I/C
            // mantiene 2 series también en principiante (≥4 series efectivas).
            if (kind == NativeProfileKind.STRENGTH && slots.size == 2) {
                slots.filter { it.intent == SlotIntent.I || it.intent == SlotIntent.C }
                    .forEach { it.sets = maxOf(it.sets, 2) }
            }
            val label = when (sessionKind) {
                RecipeSessionKind.CARDIO -> "Cardio"
                RecipeSessionKind.CARDIO_ACCESSORY -> "Cardio y accesorios"
                else -> "Día ${index + 1}"
            }
            val dayPriority = if (archetype.slots.any { it.intent == SlotIntent.P }) SlotPriority.SPEED else SlotPriority.NORMAL
            mergeNativeDaySlots(slots, notes, label)
            // Orden §11.5/H1: el mismo rango de contratos de orden (SPEED →
            // principales → compuestos → aislamientos → core/gemelo) con orden
            // estable: el arquetipo decide dentro de cada rango.
            slots.sortBy { slot -> h1Rank(slot, dayPriority, compositionMetadata) }
            slots.forEach { slot ->
                val family = COMPOUND_FAMILY_BY_SLOT[slot.key]
                slot.warmup = family != null && claimedFamilies.add(family)
            }
            val twoExerciseDay = slots.size == 2
            slots.forEach { slot ->
                slot.essential = slot.intent in setOf(SlotIntent.F, SlotIntent.FV, SlotIntent.P) ||
                    twoExerciseDay || sessionKind == RecipeSessionKind.CARDIO_ACCESSORY
            }
            val explicitCardioMinutes = cardio?.minutes
            val cardioBlock = when (archetype.cardio) {
                NativeCardioRole.NONE -> null
                else -> {
                    val minutes = when (archetype.cardio) {
                        NativeCardioRole.AFTER_STRENGTH_BRIEF ->
                            NativeCardioDefaults.briefBlockMinutes(explicitCardioMinutes)
                        NativeCardioRole.DEDICATED_WITH_ACCESSORIES, NativeCardioRole.ONLY ->
                            explicitCardioMinutes ?: NativeCardioDefaults.defaultDedicatedMinutes(input.availableMinutes)
                        else ->
                            explicitCardioMinutes ?: NativeCardioDefaults.defaultBlockMinutes(input.availableMinutes)
                    }
                    RecipeCardioBlock(
                        id = "cardio-${index + 1}",
                        details = NativeCardioDefaults.details(cardio?.type ?: CardioType.WALK, minutes),
                        position = when (archetype.cardio) {
                            NativeCardioRole.DEDICATED_WITH_ACCESSORIES -> RecipeCardioPosition.BEFORE_STRENGTH
                            NativeCardioRole.ONLY -> RecipeCardioPosition.ONLY
                            else -> RecipeCardioPosition.AFTER_STRENGTH
                        },
                        purpose = when (archetype.cardio) {
                            NativeCardioRole.ONLY -> "Cardio continuo del día"
                            NativeCardioRole.DEDICATED_WITH_ACCESSORIES -> "Cardio continuo antes de los accesorios"
                            NativeCardioRole.AFTER_STRENGTH_BRIEF -> "Bloque breve tras el trabajo de fuerza"
                            else -> "Cardio continuo tras la resistencia"
                        },
                        progression = NativeCardioDefaults.progression(archetype.cardio, explicitCardioMinutes),
                    )
                }
            }
            NativeDayPlan(
                archetype = archetype,
                label = label,
                weekday = recipeWeekday(selectedDays[index]),
                slots = slots,
                sessionKind = sessionKind,
                cardioBlock = cardioBlock,
                priority = if (archetype.slots.any { it.intent == SlotIntent.P }) SlotPriority.SPEED else SlotPriority.NORMAL,
            )
        }.toMutableList()
        if (kind == NativeProfileKind.COMPLETE_ATHLETE && selectedDays.size == 1) {
            notes += "Un solo día: los cuatro componentes se ofrecen como exposición única de base, no como programación de alto rendimiento."
        }
        if (!pullAvailable) {
            notes += "Sin banda o barra de apoyo, el trabajo de tirón es limitado: la serie de dorsales puede ser cero."
        }
        if (kind == NativeProfileKind.MUSCLE && !pullAvailable && selectedDays.size in 5..6) {
            notes += GLUTE_BRIDGE_ONCE_NOTE
            plainNotes += GLUTE_BRIDGE_ONCE_NOTE
        }
        if (kind == NativeProfileKind.COMPLETE_ATHLETE && !pullAvailable && selectedDays.size == 6) {
            notes += ATHLETE_SIX_DAY_BRIDGE_NOTE
            plainNotes += ATHLETE_SIX_DAY_BRIDGE_NOTE
        }

        val metadata = compositionMetadata
        val budget = input.availableMinutes
        val adjustments = mutableListOf<String>()
        var compositionError: String? = null
        val nativeBudgets = budgets(input, emptySet())
        // Tabla de músculos del catálogo: se arma una sola vez (antes se rehacía en cada semana de cada intento).
        val legacyLookup = ready.catalog.toLegacyConfigurationLookup()
        val legacyLookupList = legacyLookup.values.toList()

        /** Series semanales (directas + indirectas) de cada músculo con presupuesto; 0 si el músculo no aparece. */
        fun weeklySetsBySession(sessions: List<Session>): Map<String, Double> {
            val actual = VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessions, legacyLookupList)
            return nativeBudgets.keys.associateWith { muscle ->
                val volume = actual[muscle]
                (volume?.directSets ?: 0.0) + (volume?.indirectSets ?: 0.0)
            }
        }

        /** B-02: techo blando del músculo (su MRV, salvo glúteos, que admiten la banda de [glutesSoftBand]). */
        fun softCeilingOf(muscle: String, mrv: Int): Double = VolumeSoftBand.softCeilingFor(muscle, mrv, glutesSoftBand)

        fun isFittable(candidate: NativeFitAttempt, current: NativeFitAttempt? = null): Boolean {
            if (candidate.maxMinutes <= budget && candidate.overMrv.isEmpty()) return true
            if (current == null) return false
            val volumeNoWorse = candidate.totalMrvExcess <= current.totalMrvExcess + 0.001
            val volumeImproved = candidate.totalMrvExcess < current.totalMrvExcess - 0.001
            // Compare every materialized session, not only the plan-wide maximum
            // nor the per-day-position maximum: a reduction on one over-budget
            // session is progress even when another session still ties the
            // maximum. Otherwise F/Fv adjustments are silently discarded. In the
            // alternating Músculo 3-day calendar one day position is fed by two
            // different day plans across weeks, so a per-position maximum hides
            // the progress of reducing only one of them (§12.3 step 2/3).
            val sameSessionCount = candidate.allSessionMinutes.size == current.allSessionMinutes.size
            val timeNoWorse = sameSessionCount && candidate.allSessionMinutes.indices.all { sessionIndex ->
                candidate.allSessionMinutes[sessionIndex] <= current.allSessionMinutes[sessionIndex]
            }
            val timeImproved = sameSessionCount && candidate.allSessionMinutes.indices.any { sessionIndex ->
                candidate.allSessionMinutes[sessionIndex] < current.allSessionMinutes[sessionIndex]
            }
            return (timeImproved && volumeNoWorse) || (volumeImproved && timeNoWorse)
        }

        fun attemptFit(): NativeFitAttempt? {
            val recipe = assembleNativeRecipe(kind, doseLevel, dayPlans, includeWarmups = options.resolvedWarmupSteps().isNotEmpty())
            // MRV es un límite real, pero se entrega al fitter como una condición
            // corregible (§12.3), no como un rechazo anterior al primer ajuste.
            val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
                .filterNot { finding -> finding.rule == "W2" && finding.message.contains("> MRV") }
            if (hard.isNotEmpty()) {
                compositionError = hard.joinToString("; ") { "${it.rule} ${it.scope}: ${it.message}" }
                return null
            }
            val program = PlanMaterializer.materialize(
                nativeSkeleton(programId, entry, selectedDays, recipe, options, input),
                recipe,
                metadata,
                DeterministicIdProvider(programId),
                // This is an intermediate fitter candidate: every hard rule
                // except an MRV excess was checked above, and MRV is reduced
                // against materialized weekly volume before publication.
                strict = false,
                options = options,
            )
            val weeks = program.macrocycles.flatMap { it.blocks }
                .flatMap { it.mesocycles }
                .flatMap { it.weeks }
            val sessionsByWeek = weeks.map { it.sessions }
            val sessions = sessionsByWeek.flatten()
            val perSession = sessions.map(::estimateNativeSessionMinutes)
            val perWeekMinutes = sessionsByWeek.map { weekSessions -> weekSessions.map(::estimateNativeSessionMinutes) }
            val weeklySetsByWeek = sessionsByWeek.map(::weeklySetsBySession)
            // B-02: los glúteos se miden TAMBIÉN con el contador de la política (W2) y se toma el mayor de los
            // dos, así que el ajustador solo entrega lo que la política y el volumen real aceptan por igual.
            val policyGluteSets = recipe.weeks.maxOfOrNull { week ->
                SessionCompositionPolicy.weeklyGroupSets(week, metadata)[KpknMuscleGroup.GLUTES] ?: 0.0
            } ?: 0.0
            val weeklyPeak = nativeBudgets.keys.associateWith { muscle ->
                val peak = weeklySetsByWeek.maxOfOrNull { it.getValue(muscle) } ?: 0.0
                if (muscle == VolumeSoftBand.GLUTES_MUSCLE) maxOf(peak, policyGluteSets) else peak
            }
            // El recorte (cascada de §12.3) sigue guiándose por el MRV: la banda solo se usa como último recurso.
            val overMrv = nativeBudgets.mapNotNull { (muscle, row) ->
                val excess = weeklyPeak.getValue(muscle) - row.mrv
                if (excess > 0.001) muscle to excess else null
            }.toMap()
            val overSoftCeiling = nativeBudgets.mapNotNull { (muscle, row) ->
                val excess = weeklyPeak.getValue(muscle) - softCeilingOf(muscle, row.mrv)
                if (excess > 0.001) muscle to excess else null
            }.toMap()
            val highVolume = nativeBudgets.mapNotNull { (muscle, row) ->
                VolumeSoftBand.noticeOrNull(muscle, weeklyPeak.getValue(muscle), row.mrv, glutesSoftBand)
            }
            return NativeFitAttempt(
                recipe = recipe,
                program = program,
                minutesByDay = dayPlans.indices.map { dayIndex ->
                    perWeekMinutes.mapNotNull { it.getOrNull(dayIndex) }.maxOrNull() ?: 0
                },
                allSessionMinutes = perSession,
                overMrv = overMrv,
                overSoftCeiling = overSoftCeiling,
                highVolume = highVolume,
            )
        }

        var fit = attemptFit() ?: return fail(
            compositionError ?: "No se pudo componer la semana de este plan.",
            reasonCode = "COMPOSITION",
        )
        fun dayIndexOf(slot: NativeSlotPlan) = dayPlans.indexOfFirst { day -> day.slots.any { it === slot } }
        fun overBudgetDayIndices(): List<Int> = fit.minutesByDay.indices
            .filter { fit.minutesByDay[it] > budget }
            .sortedByDescending { fit.minutesByDay[it] - budget }
        fun adjustmentDayIndices(): List<Int> {
            val overBudget = overBudgetDayIndices()
            if (fit.overMrv.isEmpty()) return overBudget.ifEmpty { dayPlans.indices.toList() }
            // Un MRV excedido es semanal: también hay que probar reducciones en
            // días que caben individualmente, priorizando primero los días que
            // además exceden tiempo (§12.3).
            return overBudget + dayPlans.indices.filterNot { it in overBudget }
        }
        fun isAdditionalPractice(slot: NativeSlotPlan): Boolean {
            if (slot.intent != SlotIntent.FV) return false
            val slotDay = dayIndexOf(slot)
            if (slotDay < 0) return false
            // Un Fv puede compartir sesión con un F de OTRO patrón. Solo cuenta
            // como práctica adicional si ese mismo slot/patrón aparece otro día.
            return dayPlans.indices.any { otherDay ->
                otherDay != slotDay && dayPlans[otherDay].slots.any { other ->
                    !other.removed && other.key == slot.key &&
                        other.intent in setOf(SlotIntent.F, SlotIntent.FV, SlotIntent.H)
                }
            }
        }
        fun proposeDrop(slot: NativeSlotPlan, label: String): Boolean {
            slot.removed = true
            val next = attemptFit()
            return if (next == null || !isFittable(next, fit)) {
                slot.removed = false
                false
            } else {
                fit = next
                adjustments += label
                true
            }
        }
        if (fit.maxMinutes > budget || fit.overMrv.isNotEmpty()) {
            // §12.3 paso 2: accesorios opcionales en orden inverso (I primero,
            // C después). Cada retirada se revalida contra DayMinimumDose y
            // los suelos semanales; si viola un mínimo, se descarta.
            //
            // Calendario Músculo 3 días: una misma posición de día la ocupan dos
            // planes de día según la semana (FA/FB/FA ↔ FB/FA/FB) y W5 exige que
            // los accesorios de la semana sean el mismo conjunto en todas las
            // semanas del bloque. Retirar un accesorio de la primera copia de un
            // arquetipo repetido solo es válido cuando la otra copia ya lo perdió,
            // así que la pasada se repite mientras avance (§12.3 paso 2 «por orden
            // inverso de la tabla»: el accesorio se retira de la tabla, no de un
            // solo día). En el resto de calendarios una segunda pasada no puede
            // aceptar nada nuevo, así que no se ejecuta.
            val repeatDropPasses = alternatesEvenWeeks(kind, dayPlans.size)
            do {
                var droppedInPass = false
                val droppable = listOf(SlotIntent.I, SlotIntent.C).flatMap { intent ->
                    adjustmentDayIndices().flatMap { dayIndex ->
                        dayPlans[dayIndex].slots.filter { !it.removed && !it.essential && it.intent == intent }
                    }
                }
                for (slot in droppable) {
                    if (fit.maxMinutes <= budget && fit.overMrv.isEmpty()) break
                    if (fit.minutesByDay.getOrElse(dayIndexOf(slot)) { 0 } <= budget && fit.overMrv.isEmpty()) continue
                    if (proposeDrop(slot, "Accesorio ${slot.configurationId} retirado por presupuesto (§12.3.2).")) {
                        droppedInPass = true
                    }
                }
            } while (repeatDropPasses && droppedInPass && (fit.maxMinutes > budget || fit.overMrv.isNotEmpty()))
        }
        if (fit.maxMinutes > budget || fit.overMrv.isNotEmpty()) {
            // §12.3 paso 3: H 3→2, después I 2→1 (excepto protegido), F 3→2 en
            // no principiantes y Fv 2→1 solo en días de práctica adicionales.
            data class Reduction(val slot: NativeSlotPlan, val newSets: Int, val label: String)
            val reductions = mutableListOf<Reduction>()
            fun slotsOrdered() = adjustmentDayIndices()
                .flatMap { dayPlans[it].slots }.filter { !it.removed }
            slotsOrdered().filter { it.intent == SlotIntent.H && it.sets > 2 }.forEach {
                reductions += Reduction(it, 2, "${it.configurationId}: H 3→2 series por presupuesto (§12.3.3).")
            }
            slotsOrdered().filter { it.intent == SlotIntent.I && it.sets > 1 && !it.essential }.forEach {
                reductions += Reduction(it, 1, "${it.configurationId}: I 2→1 series por presupuesto (§12.3.3).")
            }
            if (doseLevel != NativeDoseLevel.BEGINNER) {
                slotsOrdered().filter { it.intent == SlotIntent.F && it.sets > 2 }.forEach {
                    reductions += Reduction(it, 2, "${it.configurationId}: F 3→2 series por presupuesto (§12.3.3).")
                }
            }
            slotsOrdered().filter { slot -> isAdditionalPractice(slot) && slot.sets > 1 }.forEach {
                reductions += Reduction(it, 1, "${it.configurationId}: Fv 2→1 en día de práctica por presupuesto (§12.3.3).")
            }
            for (reduction in reductions) {
                if (fit.maxMinutes <= budget && fit.overMrv.isEmpty()) break
                val dayIndex = dayIndexOf(reduction.slot)
                if (dayIndex < 0 ||
                    (fit.minutesByDay.getOrElse(dayIndex) { 0 } <= budget && fit.overMrv.isEmpty())
                ) continue
                val previous = reduction.slot.sets
                reduction.slot.sets = reduction.newSets
                val next = attemptFit()
                val accepted = next != null && isFittable(next, fit)
                if (next == null || !accepted) {
                    reduction.slot.sets = previous
                } else {
                    fit = next
                    adjustments += reduction.label
                }
            }
        }
        if (fit.maxMinutes > budget) {
            // §12.3 paso 4: mover un accesorio a otra sesión del mismo split con
            // capacidad (menor tiempo primero, luego índice de día). Nunca
            // principales, potencia ni cardio.
            var moveGuard = 0
            while (fit.maxMinutes > budget && moveGuard < 8) {
                moveGuard++
                val sourceIndex = overBudgetDayIndices().firstOrNull() ?: break
                val source = dayPlans[sourceIndex]
                val movable = source.slots.filter { !it.removed && !it.essential }.reversed()
                var moved = false
                for (slot in movable) {
                    val originalIndex = source.slots.indexOf(slot)
                    val targets = dayPlans.indices.filter { it != sourceIndex }
                        .sortedBy { fit.minutesByDay.getOrElse(it) { 0 } }
                    for (targetIndex in targets) {
                        val target = dayPlans[targetIndex]
                        source.slots.removeAt(originalIndex)
                        target.slots.add(slot)
                        // A moved accessory must retain the real H1 order; the
                        // candidate validator remains strict and decides
                        // whether the move is otherwise valid.
                        target.slots.sortBy { moved -> h1Rank(moved, target.priority, metadata) }
                        val next = attemptFit()
                        val ok = next != null &&
                            next.totalMrvExcess <= fit.totalMrvExcess + 0.001 &&
                            next.minutesByDay.getOrElse(sourceIndex) { 0 } <= budget &&
                            next.minutesByDay.getOrElse(targetIndex) { 0 } <= budget
                        if (ok) {
                            fit = next
                            adjustments += "${slot.configurationId} movido a ${dayPlans[targetIndex].label} por presupuesto (§12.3.4)."
                            moved = true
                            break
                        }
                        target.slots.remove(slot)
                        source.slots.add(originalIndex, slot)
                        if (next == null) break
                    }
                    if (moved || fit.maxMinutes <= budget) break
                }
                if (!moved) break
            }
        }
        // Sin palancas fuera de §12.3: el cardio (default por SESSION_TIME, §11.4,
        // o los minutos explícitos del usuario) y los descansos no se recortan para
        // caber. §11.4: «no recalcular el default usando tiempo restante»; §12.4:
        // los minutos elegidos «se conservan, sin reducirlos»; §12.2: «las recetas
        // mínimas no se hacen caber bajando descansos». Ver DEC-w1-01 en
        // docs/WIZARD_PLAN_DEVIATIONS.md.
        if (fit.maxMinutes > budget) {
            // §12.3 paso 5: sin variante de split que quepa → TIME_BUDGET con
            // los minutos mínimos reales calculados; nunca éxito parcial.
            val compositionDiagnostic = compositionError?.let { "; diagnóstico de composición: $it" } ?: ""
            return fail(
                "Este plan necesita $budget min por sesión y no cabe con las dosis mínimas; el mínimo real es de " +
                    "${fit.maxMinutes} min. Amplía el tiempo o elige otro plan.$compositionDiagnostic",
                reasonCode = "TIME_BUDGET",
                maxMinutes = fit.maxMinutes,
            )
        }
        // B-02: el MRV sigue siendo el objetivo (la cascada de arriba ya agotó sus palancas), pero un
        // plan propio cuyos glúteos quedan entre el MRV y el techo blando se entrega con aviso de
        // «volumen alto». Solo un exceso sobre el techo blando (o en otro músculo) es COMPOSITION.
        if (fit.overSoftCeiling.isNotEmpty()) {
            return fail(
                "No se puede respetar el MRV semanal sin bajar de los mínimos diarios y de patrón: " +
                    fit.overSoftCeiling.keys.joinToString("; ") { muscle ->
                        "$muscle excede ${"%.1f".format(fit.overMrv.getValue(muscle))} series"
                    },
                reasonCode = "COMPOSITION",
            )
        }

        notes += adjustments
        fit.highVolume.forEach { notice -> notes += notice.message }
        val program = fit.program.copy(
            description = (entry.description + "\n" + notes.distinct().joinToString("\n")).trim(),
            sourceRecipe = fit.recipe,
        )
        val stamped = program.withSessionDurations(fit)
        val structuralIssues = ProgramExecutionContract.validate(stamped)
        if (structuralIssues.isNotEmpty()) {
            return fail(
                structuralIssues.joinToString("; ") { it.message },
                reasonCode = "COMPOSITION",
            )
        }
        val lookup = legacyLookup
        val budgets = nativeBudgets
        val firstWeekSessions = stamped.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .firstOrNull()?.sessions.orEmpty()
        val actual = VolumeCalculator.calculateRoleSeparatedMuscleVolume(firstWeekSessions, lookup.values.toList())
        val reports = budgets.map { (muscle, budgetRow) ->
            val value = actual[muscle]
            val direct = value?.directSets ?: 0.0
            val indirect = value?.indirectSets ?: 0.0
            val frequency = firstWeekSessions.count { session ->
                val contribution = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
                    listOf(session),
                    lookup.values.toList(),
                )[muscle]
                contribution != null && contribution.directSets + contribution.indirectSets > 0.0
            }
            MuscleBudgetReport(muscle, direct, indirect, budgetRow.target, budgetRow.mev, budgetRow.mav, budgetRow.mrv, frequency, (budgetRow.target - direct - indirect).coerceAtLeast(0.0))
        }
        // §14.3: para los planes propios el techo semanal es el MRV con
        // contabilidad real; MAV es un objetivo de programación, no un límite
        // duro (los suelos/techos de §§11–12 mandan). LEGACY conserva su
        // comprobación min(MAV, MRV) sin cambios en la ruta histórica. B-02: en glúteos el
        // techo es el blando (MRV + banda); en el resto de músculos sigue siendo el MRV.
        val overVolume = reports.filter {
            it.directSets + it.indirectSets > softCeilingOf(it.muscle, it.mrv) + 0.001
        }
        if (overVolume.isNotEmpty()) {
            return unavailable(
                "La combinación excede el volumen permitido. Elige otra distribución. " +
                    overVolume.joinToString("; ") {
                        "${it.muscle}: ${it.directSets}+${it.indirectSets} > mrv ${it.mrv}"
                    },
            )
        }
        ProgramExecutionContract.requireExecutable(stamped)
        val stored = ProgramPersistNormalizer.forRoomStorage(stamped)
        return PersonalizationResult(
            stored,
            PersonalizationReport(
                executable = true,
                classification = CatalogClassification.SIMPLE,
                limitations = notes.distinct(),
                muscles = reports,
                provenance = provenance,
                reasonCode = null,
                maxSessionMinutes = fit.maxMinutes,
                highVolume = fit.highVolume,
                planNotes = plainNotes.distinct(),
            ),
        )
    }

    /**
     * Rango de orden H1 (SPEED → T1 → T2 → T3 compuesto → aislamiento →
     * core/gemelo) con orden estable. El fitter/ensamblador ordena los slots
     * del día por este rango para que el arquetipo §11.3/§11.4 cumpla el
     * contrato de orden existente sin reescribir su contenido.
     */
    private fun h1Rank(
        slot: NativeSlotPlan,
        dayPriority: SlotPriority,
        metadata: ExerciseCompositionMetadataProvider,
    ): Int {
        val meta = metadata.metadata(slot.configurationId)
        val family = meta?.let { CompositionTaxonomy.familyOf(it.movementPatternId) }
        val isolation = CompositionTaxonomy.isIsolation(family, meta?.articulationType, slot.configurationId)
        val speedFirst = dayPriority == SlotPriority.SPEED && slot.role == SlotRole.SPEED
        return when {
            speedFirst -> 0
            slot.role == SlotRole.SPEED -> 1
            slot.role == SlotRole.T1_MAIN -> 2
            slot.role == SlotRole.TECHNIQUE -> 3
            slot.role == SlotRole.T2_SUPPLEMENTAL -> 4
            slot.role == SlotRole.T3_ACCESSORY && !isolation -> 5
            CompositionTaxonomy.isFinisherFamily(family) -> 7
            else -> 6
        }
    }

    /**
     * §13.3: si dos slots del día caen en la MISMA configuración no se
     * duplican ejercicios equivalentes. Potencia y fatiga conviven con
     * `supplementalOf` (§11.5); F+H de la misma configuración cuenta dos
     * propósitos y una sola configuración; H+H se fusiona hasta 4 series.
     */
    private fun mergeNativeDaySlots(slots: MutableList<NativeSlotPlan>, notes: MutableList<String>, dayLabel: String) {
        val byConfiguration = LinkedHashMap<String, MutableList<NativeSlotPlan>>()
        slots.forEach { byConfiguration.getOrPut(it.configurationId) { mutableListOf() } += it }
        byConfiguration.values.filter { it.size > 1 }.forEach { group ->
            val power = group.firstOrNull { it.intent == SlotIntent.P }
            val main = group.firstOrNull { it != power && it.intent in setOf(SlotIntent.F, SlotIntent.FV) }
            when {
                power != null && main != null -> power.supplementalOf = main.slotId
                power != null -> group.firstOrNull { it !== power }?.let { power.supplementalOf = it.slotId }
                main != null -> group.filter { it !== main }.forEach { it.supplementalOf = main.slotId }
                else -> {
                    val first = group.first()
                    first.sets = minOf(group.sumOf { it.sets }, 4)
                    group.drop(1).forEach { it.removed = true }
                    notes += "$dayLabel: ${group.size} slots equivalentes de ${first.configurationId} fusionados en ${first.sets} series (§13.3)."
                }
            }
        }
        // §14.3 (H4): dos slots distintos del MISMO replacementGroup no se
        // declaran equivalentes sin relación: se emparejan con `supplementalOf`
        // (p. ej. P(S) de velocidad y sentadilla de trabajo en XA).
        slots.filter { !it.removed && it.replacementGroup != null }
            .groupBy { it.replacementGroup }
            .values.filter { it.size > 1 }
            .forEach { group ->
                if (group.any { it.supplementalOf != null }) return@forEach
                val power = group.firstOrNull { it.intent == SlotIntent.P }
                val anchor = if (power != null) group.first { it !== power } else group.first()
                group.forEach { slot ->
                    if (slot !== anchor && slot.supplementalOf == null) slot.supplementalOf = anchor.slotId
                }
            }
    }

    /**
     * Único calendario propio que alterna planes de día en las semanas pares:
     * Músculo de 3 días (FA/FB/FA ↔ FB/FA/FB, §11.3). Lo comparten el ensamblado
     * de la receta y el fitter, que debe retirar accesorios respetando W5.
     */
    private fun alternatesEvenWeeks(kind: NativeProfileKind, dayCount: Int): Boolean =
        kind == NativeProfileKind.MUSCLE && dayCount == 3

    /** Seis semanas con descarga (§12.1) construidas desde los planes de día. */
    private fun assembleNativeRecipe(
        kind: NativeProfileKind,
        level: NativeDoseLevel,
        dayPlans: List<NativeDayPlan>,
        includeWarmups: Boolean,
    ): TrainingPlanRecipe {
        val weeks = (1..NativeWeekBuilder.WEEKS).map { weekNumber ->
            val deload = weekNumber == NativeWeekBuilder.DELOAD_WEEK
            // Músculo de 3 días alterna FA/FB/FA ↔ FB/FA/FB en semanas pares
            // (§11.3); las mismas variantes se resuelven una vez por bloque.
            val plansForWeek = if (
                alternatesEvenWeeks(kind, dayPlans.size) && weekNumber % 2 == 0
            ) {
                listOf(dayPlans[1], dayPlans[0], dayPlans[1]).mapIndexed { index, plan ->
                    plan.copy(label = dayPlans[index].label, weekday = dayPlans[index].weekday)
                }
            } else {
                dayPlans
            }
            WeekRecipe(
                weekNumber = weekNumber,
                blockIndex = if (deload) 1 else 0,
                blockName = if (deload) NativeWeekBuilder.DELOAD_BLOCK_NAME else NativeWeekBuilder.BASE_BLOCK_NAME,
                blockGoal = if (deload) BlockGoal.DELOAD else BlockGoal.ACCUMULATION,
                kind = if (deload) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
                weekName = "Semana $weekNumber",
                days = plansForWeek.mapIndexed { dayIndex, plan ->
                    assembleNativeDay(plan, dayIndex, weekNumber, level, deload, includeWarmups)
                },
            )
        }
        val competitionLifts = mutableMapOf<LiftSlot, String>()
        dayPlans.flatMap { it.slots }.filter { !it.removed }.forEach { slot ->
            slot.liftSlot?.let { competitionLifts.putIfAbsent(it, slot.configurationId) }
        }
        val liftSlots = if (competitionLifts.keys.containsAll(
                setOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT),
            )
        ) {
            competitionLifts
        } else {
            emptyMap()
        }
        val allBodyweight = dayPlans.flatMap { it.slots }.filter { !it.removed }
            .all { it.equipmentId == "bodyweight" }
        return TrainingPlanRecipe(
            id = kind.entryId,
            weeks = weeks,
            liftSlots = liftSlots,
            progression = ProgressionRule.None,
            claimedDaysPerWeek = dayPlans.size,
            claimedLevel = when (level) {
                NativeDoseLevel.BEGINNER -> "principiante"
                NativeDoseLevel.INTERMEDIATE -> "intermedio"
                NativeDoseLevel.ADVANCED -> "avanzado"
            },
            // §12.1: continuar inicia una ocurrencia nueva; no se clona la semana eternamente.
            repeats = false,
            contentVersion = 1,
            nativeProgression = NativeProgressionSpec(
                strategy = if (allBodyweight) {
                    NativeProgressionStrategy.BODYWEIGHT_VARIANT_ESCALATION
                } else {
                    NativeProgressionStrategy.REP_RANGE_THEN_LOAD
                },
                exposuresBeforeProposal = 2,
                note = "Doble progresión §12.4: sube reps hasta el tope del rango y, tras dos exposiciones completas, " +
                    "el menor incremento conocido del equipo; corporal: siguiente variante curada más exigente.",
            ),
            compositionProfile = kind.compositionProfile,
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.WEEKLY_REVIEW)),
        )
    }

    private fun assembleNativeDay(
        plan: NativeDayPlan,
        dayIndex: Int,
        weekNumber: Int,
        level: NativeDoseLevel,
        deload: Boolean,
        includeWarmups: Boolean,
    ): DayRecipe {
        val active = plan.slots.filter { !it.removed }
        val slots = active.map { slot ->
            val setsCount = when {
                slot.intent == SlotIntent.P && (deload || weekNumber <= 2) -> NativeDoseTable.SPEED_SETS_WEEKS_1_2
                deload -> NativeDoseTable.deloadSets(slot.sets)
                else -> slot.sets
            }
            val rir = when {
                slot.intent == SlotIntent.P -> NativeDoseTable.SPEED_RIR
                deload -> 4
                slot.rirOverride != null -> slot.rirOverride
                else -> NativeWeekBuilder.trainingRir(level, weekNumber)
            }
            val warmupSets = if (slot.warmup && includeWarmups) {
                listOf(SetRecipe(reps = 5, isWarmup = true, loadBasis = LoadBasis.RPE))
            } else {
                emptyList()
            }
            val workSets = List(setsCount) {
                SetRecipe(
                    reps = slot.repsMin,
                    repsMin = slot.repsMin,
                    repsMax = slot.repsMax,
                    rir = rir,
                    rpe = (10 - rir).toDouble(),
                    loadBasis = LoadBasis.RPE,
                )
            }
            SlotRecipe(
                id = slot.slotId,
                role = slot.role,
                lift = LiftRef(slot.configurationId, slot.liftSlot),
                sets = warmupSets + workSets,
                restSeconds = slot.restSeconds,
                technique = slot.technique,
                supplementalOf = slot.supplementalOf,
                isCompetitionLift = slot.isCompetitionLift,
                source = SlotSource.KPKN_DEFAULT,
                priority = if (slot.intent == SlotIntent.P) SlotPriority.SPEED else SlotPriority.NORMAL,
                intent = slot.intent,
            )
        }
        val minimumDose = when (plan.sessionKind) {
            RecipeSessionKind.CARDIO -> NativeMinimumDose.cardioOnly()
            RecipeSessionKind.CARDIO_ACCESSORY -> NativeMinimumDose.cardioAccessory(active.map { it.slotId })
            else -> NativeMinimumDose.strength(active.filter { it.essential }.map { it.slotId })
        }
        return DayRecipe(
            id = "d${dayIndex + 1}-${plan.archetype.name}",
            label = plan.label,
            slots = slots,
            weekday = plan.weekday,
            priority = plan.priority,
            cardioBlocks = plan.cardioBlock
                ?.let { block ->
                    if (deload) {
                        // §12.1: la descarga mantiene la duración a intensidad fácil.
                        listOf(block.copy(details = block.details.copy(intensity = CardioIntensity.BAJA)))
                    } else {
                        listOf(block)
                    }
                }
                .orEmpty(),
            sessionKind = plan.sessionKind,
            minimumDose = minimumDose,
        )
    }

    /**
     * Esqueleto de [Program] para materializar un plan propio: bloques con
     * origen nativo (§14.4) para que las rematerializaciones conserven la
     * política nativa, calendario por los días elegidos y configuración de
     * calentamientos/autorregulación persistida en el JSON del programa.
     */
    private fun nativeSkeleton(
        programId: String,
        entry: CatalogEntry,
        days: List<Int>,
        recipe: TrainingPlanRecipe,
        options: TrainingOptions,
        input: PersonalizerInput,
    ): Program = Program(
        id = programId,
        name = entry.title,
        description = entry.description,
        mode = when (entry.id) {
            NativeProfileKind.STRENGTH.entryId -> ProgramMode.POWERLIFTING
            NativeProfileKind.POWERBUILDING.entryId -> ProgramMode.POWERBUILDING
            else -> ProgramMode.HYPERTROPHY
        },
        structure = ProgramStructure.COMPLEX,
        simpleProgramKind = SimpleProgramKind.LINEAR,
        structureTemplateId = entry.id,
        macrocycles = listOf(
            Macrocycle(
                id = "$programId-macro",
                name = entry.title,
                blocks = listOf(
                    Block(
                        id = "$programId-block",
                        name = NativeWeekBuilder.BASE_BLOCK_NAME,
                        goal = BlockGoal.ACCUMULATION,
                        prescriptionOrigin = KPKN_NATIVE_CURATED_ORIGIN,
                        mesocycles = listOf(
                            Mesocycle(id = "$programId-meso", name = NativeWeekBuilder.BASE_BLOCK_NAME, weeks = emptyList()),
                        ),
                    ),
                ),
            ),
        ),
        startDay = days.first(),
        volumeRecommendations = input.volumeRecommendations,
        schedulePlan = ProgramSchedulePlan(weekStartDay = days.first(), trainingDays = days.toSet()),
        autoregulationMode = options.autoregulationMode,
        planWarmupConfig = options.warmup,
        tags = listOf("KPKN_NATIVE", input.focus.name),
        sourceRecipe = recipe,
    )

    /** Shared §12.2 estimate; fitter and persisted target use identical minutes. */
    private fun estimateNativeSessionMinutes(session: Session): Int =
        SessionDurationEstimator.estimate(session).totalMinutes

    /** Fija la duración estimada real de cada sesión materializada (§12.2). */
    private fun Program.withSessionDurations(fit: NativeFitAttempt): Program {
        var index = 0
        return copy(
            macrocycles = macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        week.copy(
                                            sessions = week.sessions.map { session ->
                                                session.copy(
                                                    targetDurationMinutes = fit.allSessionMinutes.getOrElse(index) { 1 },
                                                ).also { index++ }
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
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
     *   fijas ([supportRequirementsFor]): un conjunto, sin duplicado divergente.
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
        // Requisitos de soporte de ESTA configuración (banco, rack, barra de
        // dominadas, paralelas, barra baja, balón, anclaje de Nordic): todos
        // declarados o el perfil legacy `general_gym`.
        val extra = supportRequirementsFor(configuration.id)
        return extra.isEmpty() || "general_gym" in equipment || extra.all { requirement -> requirement in equipment }
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
        "Pectorales" to listOf("tren_superior_press_pecho_maquina_convergente__default", "bench_press__dumbbells", "bench_press__barbell", "flat_chest_fly__machine", "push_up__flat", "tren_superior_press_banda_resistencia__default", "knee_push_up__default"),
        "Dorsales" to listOf("chest_supported_row__machine__medium", "lat_pulldown__bilateral__machine", "back_remo_banda__default", "conventional_row__dumbbells", "lat_pulldown__bilateral__cable", "pull_up__pronated__medium", "back_remo_invertido__default"),
        "Cuádriceps" to listOf("quads_extension_cuadriceps__machine__bilateral", "quads_sentadilla_hack__machine", "quads_prensa_piernas__bilateral", "walking_lunge__dumbbells", "quads_sentadilla_cosaca__default", "quads_sentadilla_sin_carga__default"),
        "Isquiosurales" to listOf("seated_leg_curl__bilateral__machine", "lying_leg_curl__bilateral__machine", "romanian_deadlift__bilateral__barbell", "romanian_deadlift__bilateral__dumbbells", "curl_isquios_con_balon__default", "hams_curl_nordic_peso_corporal__default"),
        "Glúteos" to listOf("hip_thrust__bilateral__machine", "glutes_frog_pumps__default", "hip_thrust__bilateral__barbell", "glutes_patada_gluteo__band"),
        "Deltoides" to listOf("seated_lateral_raise__machine", "standing_lateral_raise__dumbbells", "standing_lateral_raise__cable", "military_press__machine"),
        "Bíceps" to listOf("preacher_curl__machine", "hammer_curl__band", "hammer_curl__dumbbells", "standing_biceps_curl__barbell"),
        "Tríceps" to listOf("triceps_pushdown__bilateral__machine", "triceps_pushdown__bilateral__band", "triceps_pushdown__bilateral__cable", "triceps_flexiones_esfinge__default"),
        "Pantorrillas" to listOf("calf_raise__bilateral__machine"),
        "Abdomen" to listOf("core_crunch_suelo_peso_corporal__default", "core_crunch_maquina__default"),
        "Erectores Espinales" to listOf("back_superman_suelo__default"),
    )

    companion object {
        /** Contrato de la bolsa de puntos de orden: máximo 2 por músculo y 5 en total. */
        internal const val MAX_ORDER_POINTS_PER_MUSCLE = 2
        internal const val MAX_ORDER_POINTS_TOTAL = 5

        /** Techo de `availableMinutes` que acepta [personalize] (rango 20..100). */
        private const val MAX_SESSION_MINUTES = 100

        /**
         * Primer presupuesto de minutos mayor que [availableMinutes] para el que [viableAt] es
         * verdadero, o null si no hay ninguno hasta [MAX_SESSION_MINUTES]. Sondea el techo UNA
         * vez antes de barrer: si ni el máximo basta, el rechazo no es de tiempo y se devuelve sin
         * más pruebas (la rama de material no paga ~80 generaciones). Si el techo basta, el barrido
         * va de menor a mayor y corta en el primer minuto viable, de modo que el resultado es el
         * mínimo exacto aunque la viabilidad no fuera monótona. [viableAt] puede lanzar: una
         * excepción (incluida `CancellationException`) sale tal cual, sin capturarse aquí.
         */
        internal fun firstViableMinutes(availableMinutes: Int, viableAt: (Int) -> Boolean): Int? {
            if (availableMinutes >= MAX_SESSION_MINUTES) return null
            if (!viableAt(MAX_SESSION_MINUTES)) return null
            for (minutes in availableMinutes + 1 until MAX_SESSION_MINUTES) {
                if (viableAt(minutes)) return minutes
            }
            return MAX_SESSION_MINUTES
        }

        /**
         * Familia de patrón por clave de arquetipo: el primer compuesto de cada
         * patrón del día recibe la aproximación técnica §12.2 (5 reps fáciles,
         * 60 s), lo que además desactiva el preset del plan en ese slot. Las
         * familias coinciden con [CompositionTaxonomy] para las variantes
         * resolubles de cada clave.
         */
        private val COMPOUND_FAMILY_BY_SLOT = mapOf(
            NativeSlotKey.S to "SQUAT",
            NativeSlotKey.U to "SQUAT",
            NativeSlotKey.PS to "SQUAT",
            NativeSlotKey.D to "HINGE",
            NativeSlotKey.SM to "HINGE",
            NativeSlotKey.B to "HORIZONTAL_PUSH",
            NativeSlotKey.PB to "HORIZONTAL_PUSH",
            NativeSlotKey.O to "VERTICAL_PUSH",
            NativeSlotKey.R to "HORIZONTAL_PULL",
            NativeSlotKey.V to "VERTICAL_PULL",
        )
        // El remo invertido con barra baja real es una regresión horizontal
        // adecuada para principiantes (difficulty 4.0, §13.3); la dificultad
        // del tirón vertical no debe excluirlo. Dominadas, Nordics y cossack
        // siguen excluidos por defecto.
        private val hardBodyweight = setOf("pull_up__pronated__medium", "hams_curl_nordic_peso_corporal__default", "quads_sentadilla_cosaca__default")
        private val defaultDays = mapOf(1 to listOf(1), 2 to listOf(1, 4), 3 to listOf(1, 3, 5), 4 to listOf(1, 2, 4, 5), 5 to listOf(1, 2, 3, 5, 6), 6 to listOf(1, 2, 3, 4, 5, 6))
    }
}

/**
 * Ids deterministas para la ruta nativa: dos personalizaciones con los mismos
 * inputs producen exactamente el mismo [Program] (contrato de igualdad del
 * catálogo), sin UUIDs aleatorios por materialización.
 */
private class DeterministicIdProvider(private val prefix: String) : IdProvider {
    private var counter = Int.MIN_VALUE
    override fun newId(): String {
        val value = counter
        counter++
        return "$prefix-id-$value"
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
