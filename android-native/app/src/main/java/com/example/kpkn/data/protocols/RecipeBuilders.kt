package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.DaySlotTemplate

fun warmupPercentSets(
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = listOf(
    SetRecipe(reps = 5, percent = 40.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 3, percent = 55.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 1, percent = 65.0, isWarmup = true, loadBasis = basis),
)

/**
 * Política de aproximación separada de [warmupPercentSets] (que conservan
 * intactas los protocolos de autor): preset del plan para el primer compuesto
 * de cada patrón de movimiento — 40 % × 8, 60 % × 5, 80 % × 3 — aplicado sobre
 * la carga de trabajo, nunca sobre el 1RM.
 */
fun firstCompoundWarmupPercentSets(
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = listOf(
    SetRecipe(reps = 8, percent = 40.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 5, percent = 60.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 3, percent = 80.0, isWarmup = true, loadBasis = basis),
)

fun copies(count: Int, reps: Int, percent: Double, basis: LoadBasis = LoadBasis.PERCENT_TM): List<SetRecipe> =
    List(count) { SetRecipe(reps = reps, percent = percent, loadBasis = basis) }

fun percentSets(
    rest: Int,
    vararg steps: Pair<Int, Double>,
    amrapLast: Boolean = false,
    topLast: Boolean = false,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = steps.mapIndexed { index, (reps, percent) ->
    val last = index == steps.lastIndex
    SetRecipe(
        reps = reps,
        percent = percent,
        amrap = last && amrapLast,
        isTopSet = last && topLast,
        loadBasis = basis,
    )
}

fun repeatPercentSets(
    count: Int,
    reps: Int,
    percent: Double,
    rest: Int,
    amrapLast: Boolean = false,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = List(count) { index ->
    SetRecipe(
        reps = reps,
        percent = percent,
        amrap = amrapLast && index == count - 1,
        loadBasis = basis,
    )
}

fun rpeSets(
    count: Int,
    reps: Int,
    rpe: Double,
    rest: Int = 90,
    rir: Int? = null,
    repsMax: Int? = null,
): List<SetRecipe> = List(count) {
    SetRecipe(
        reps = reps,
        repsMax = repsMax,
        rpe = rpe,
        rir = rir,
        loadBasis = if (rir != null) LoadBasis.RPE else LoadBasis.RPE,
    )
}

fun rirSets(count: Int, reps: Int, rir: Int, rest: Int = 90, repsMax: Int? = null): List<SetRecipe> =
    List(count) {
        SetRecipe(reps = reps, repsMax = repsMax, rir = rir, rpe = (10 - rir).toDouble(), loadBasis = LoadBasis.RPE)
    }

fun rangeRirSets(count: Int, repsMin: Int, repsMax: Int, rir: Int): List<SetRecipe> =
    List(count) {
        SetRecipe(
            reps = repsMin,
            repsMin = repsMin,
            repsMax = repsMax,
            rir = rir,
            rpe = (10 - rir).toDouble(),
            loadBasis = LoadBasis.RPE,
        )
    }

fun slot(
    id: String,
    role: SlotRole,
    configurationId: String,
    sets: List<SetRecipe>,
    restSeconds: Int,
    liftSlot: LiftSlot? = null,
    technique: TechniqueModifier? = null,
    supplementalOf: String? = null,
    isCompetitionLift: Boolean = false,
    isUnilateral: Boolean = false,
    source: SlotSource = SlotSource.AUTHOR,
    priority: SlotPriority = SlotPriority.NORMAL,
    occurrenceLabel: String? = null,
): SlotRecipe = SlotRecipe(
    id = id,
    role = role,
    lift = LiftRef(configurationId, liftSlot),
    sets = sets,
    restSeconds = restSeconds,
    technique = technique,
    supplementalOf = supplementalOf,
    isCompetitionLift = isCompetitionLift,
    isUnilateral = isUnilateral,
    source = source,
    priority = priority,
    occurrenceLabel = occurrenceLabel,
)

fun day(
    label: String,
    slots: List<SlotRecipe>,
    archetype: DaySlotTemplate? = null,
    priority: SlotPriority = SlotPriority.NORMAL,
    weekday: Int? = null,
): DayRecipe = DayRecipe(label = label, slots = slots, archetype = archetype, priority = priority, weekday = weekday)

fun weekRecipe(
    weekNumber: Int,
    blockIndex: Int,
    blockName: String,
    blockGoal: BlockGoal,
    days: List<DayRecipe>,
    kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
    weekName: String = "",
): WeekRecipe = WeekRecipe(
    weekNumber = weekNumber,
    blockIndex = blockIndex,
    blockName = blockName,
    blockGoal = blockGoal,
    kind = kind,
    days = days,
    weekName = weekName,
)

/**
 * Quita [n] series de trabajo a cada accesorio (T3) del día, sin pasar de [minSets] series por slot.
 *
 * H-03 (B.S6 parte 2b): el suelo ya no es de 1 serie en todas partes. El contrato de receta (C1) solo
 * admite un T3 de una serie en las semanas de PICO, REALIZACIÓN, TAPER o DESCARGA; en el resto (p. ej. la
 * intensificación) un accesorio de 2 series, como el encogimiento 2×8, no puede quedar en 1. Por eso el
 * suelo por defecto es 2 y solo baja a 1 cuando el recorte es de 2 series o más, que es lo que piden esas
 * semanas de pico, taper o descarga (`dropT3(2)`); la intensificación recorta una sola serie (`dropT3(1)`).
 * Una receta que necesite otro suelo lo pasa en [minSets]. Un slot con menos series que el suelo se queda
 * como está: nunca se añaden series.
 */
fun DayRecipe.dropT3(n: Int, minSets: Int = if (n >= 2) 1 else 2): DayRecipe {
    if (n <= 0) return this
    return copy(
        slots = slots.map { slot ->
            if (slot.role != SlotRole.T3_ACCESSORY) slot
            else slot.copy(sets = slot.sets.take((slot.sets.size - n).coerceAtLeast(minSets)))
        },
    )
}

/** Rampa de series de los accesorios (estilo RP): +[extra] series a cada slot T3, sin pasar de [cap] series de trabajo. */
fun DayRecipe.addT3Sets(extra: Int, cap: Int = 8): DayRecipe = copy(
    slots = slots.map { s ->
        if (s.role != SlotRole.T3_ACCESSORY || extra <= 0) s
        else {
            val work = s.sets.filter { !it.isWarmup }
            val warm = s.sets.filter { it.isWarmup }
            val room = (cap - work.size).coerceAtLeast(0)
            val added = List(minOf(extra, room)) { work.last() }
            s.copy(sets = warm + work + added)
        }
    },
)

fun topSetAndBackoff(
    topSets: Int,
    topReps: Int,
    topPct: Double,
    backSets: Int,
    backReps: Int,
    backPct: Double,
    rpe: Double? = null,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> {
    val top = List(topSets) { index ->
        SetRecipe(
            reps = topReps,
            percent = topPct,
            isTopSet = index == topSets - 1,
            rpe = rpe,
            loadBasis = basis,
        )
    }
    val back = List(backSets) {
        SetRecipe(reps = backReps, percent = backPct, loadBasis = basis)
    }
    return top + back
}

fun DayRecipe.replaceT1Work(
    work: List<SetRecipe>,
    configurationId: String? = null,
    technique: TechniqueModifier? = null,
    keepWarmup: Boolean = true,
): DayRecipe = copy(
    slots = slots.map { slot ->
        if (slot.role != SlotRole.T1_MAIN) slot
        else {
            val warmups = if (keepWarmup) slot.sets.filter { it.isWarmup } else emptyList()
            slot.copy(
                lift = configurationId?.let { slot.lift.copy(configurationId = it) } ?: slot.lift,
                technique = technique ?: slot.technique,
                sets = warmups + work,
            )
        }
    },
)

fun DayRecipe.replaceT2Lift(
    configurationId: String,
    technique: TechniqueModifier? = null,
): DayRecipe = copy(
    slots = slots.map { slot ->
        if (slot.role != SlotRole.T2_SUPPLEMENTAL) slot
        else slot.copy(
            lift = slot.lift.copy(configurationId = configurationId),
            technique = technique ?: slot.technique,
        )
    },
)

fun DayRecipe.asKpknSuggested(): DayRecipe = copy(
    slots = slots.map { it.copy(source = SlotSource.KPKN_DEFAULT) },
)

fun DayRecipe.withMinRir(minRir: Int): DayRecipe = copy(
    slots = slots.map { slot ->
        slot.copy(
            sets = slot.sets.map { set ->
                if (set.isWarmup) set
                else set.copy(
                    rir = maxOf(set.rir ?: minRir, minRir),
                    rpe = minOf(set.rpe ?: (10 - minRir).toDouble(), (10 - minRir).toDouble()),
                )
            },
        )
    },
)

/**
 * Fuente de un método de tercero. [author] lleva los autores completos, escritos como
 * «Nombre Apellido y Nombre Apellido» (sin barras ni abreviaturas) y debe coincidir con
 * `Protocol.author`: el disclaimer lo repite tal cual («No afiliado a {autores}»).
 */
fun attributed(
    primaryReference: String,
    primaryUrl: String,
    author: String,
    variant: String? = null,
    evidenceUrl: String? = null,
): ProtocolSource = ProtocolSource(
    primaryReference = primaryReference,
    primaryUrl = primaryUrl,
    evidenceUrl = evidenceUrl ?: primaryUrl,
    variant = variant,
    disclaimer = "No afiliado a $author",
    reviewedAt = "2026-09-13",
    catalogRevision = "v2-approved-2026-08-12-a",
    approvedBy = "KPKN Editorial",
)

/** Disclaimer de los planes propios de KPKN: no hay un tercero del que desafiliarse. */
const val KPKN_OWN_PLAN_DISCLAIMER = "Plan propio de KPKN."

/**
 * Fuente de un plan propio de KPKN (E-11): sin URL, porque no existe una página de fuente que
 * enlazar, y con [KPKN_OWN_PLAN_DISCLAIMER] en lugar de «No afiliado a KPKN».
 */
fun kpknOwnSource(
    primaryReference: String,
    variant: String? = null,
): ProtocolSource = ProtocolSource(
    primaryReference = primaryReference,
    primaryUrl = null,
    evidenceUrl = null,
    variant = variant,
    disclaimer = KPKN_OWN_PLAN_DISCLAIMER,
    reviewedAt = "2026-09-13",
    catalogRevision = "v2-approved-2026-08-12-a",
    approvedBy = "KPKN Editorial",
)

/**
 * L-05/B.S6: los porcentajes de trabajo T1 de pico y test están escritos sobre el 1RM.
 * Las etapas anteriores conservan su TM; el llamador aplica esto solo en las semanas
 * PEAK/REALIZATION/TAPER que declara sobre 1RM. Las series por esfuerzo o referencia
 * observada y las aproximaciones conservan su base de carga.
 */
fun DayRecipe.mainWorkPercentOfOneRm(): DayRecipe = copy(
    slots = slots.map { slot ->
        if (slot.role != SlotRole.T1_MAIN) slot else slot.copy(
            sets = slot.sets.map { set ->
                if (!set.isWarmup && set.percent != null && set.loadBasis == LoadBasis.PERCENT_TM && set.reference == null) {
                    set.copy(loadBasis = LoadBasis.PERCENT_1RM)
                } else set
            },
        )
    },
)

/**
 * CAL5: la serie AMRAP de un método que solo la REGISTRA (Cube, Lilliebridge, The Rippler: ninguna fuente le da una regla de
 * carga) queda como AMRAP de registro explícito: `purpose = AMRAP` con su mínimo (las repeticiones prescritas) y sin efecto
 * sobre la carga. Se guarda en el historial y en los récords; el programa no pretende que haga más.
 */
fun DayRecipe.amrapAsRecord(): DayRecipe = copy(
    slots = slots.map { slot ->
        slot.copy(
            sets = slot.sets.map { set ->
                if (set.isWarmup || !set.amrap) set
                else set.copy(
                    amrap = true,
                    purpose = com.example.kpkn.data.models.SetPurpose.AMRAP,
                    amrapMinimumReps = (set.repsMin ?: set.reps)?.takeIf { it > 0 },
                    calibrationScope = null,
                )
            },
        )
    },
)

/**
 * CAL5/CAL6: el AMRAP de las plantillas propias de KPKN (`power-12-3`, `power-16-4`, `power-20-5`) es un CALIBRADOR de
 * «próximas sesiones»: su capacidad se compara con el 1RM que implica el TM y el cambio sale por el canal de propuestas del
 * programa (OFF, PROPONER y AUTO), nunca por encima de la receta. Conserva `amrap = true` para los lectores antiguos.
 */
fun DayRecipe.amrapAsCalibrator(): DayRecipe = copy(
    slots = slots.map { slot ->
        slot.copy(
            sets = slot.sets.map { set ->
                if (set.isWarmup || !set.amrap) set
                else set.copy(
                    amrap = true,
                    purpose = com.example.kpkn.data.models.SetPurpose.CALIBRATOR,
                    amrapMinimumReps = (set.repsMin ?: set.reps)?.takeIf { it > 0 },
                    calibrationScope = com.example.kpkn.data.models.CalibrationScope.NEXT_SESSIONS,
                )
            },
        )
    },
)

/**
 * Alternativas de relleno por propósito (F-31, decisión 3 del 27-oct). Solo ids de [CatalogIds]. La primera alternativa que
 * sirve es la que usa [rotateKpknFillers]; el orden pone primero la más parecida en intención.
 */
val KPKN_FILLER_ALTERNATIVES: Map<String, List<String>> = mapOf(
    // Orden de cada lista: 1.º el MISMO material que el original, 2.º peso corporal sin material, 3.º las demás. Así la
    // rotación no hace que un plan pida material que no pedía (p. ej. Smolov: solo barra, rack y polea).
    CatalogIds.PALLOF to listOf(CatalogIds.PLANCHA, CatalogIds.HLR, CatalogIds.WHEEL), // cable → sin otra de polea; corporal; rueda
    CatalogIds.WHEEL to listOf(CatalogIds.PLANCHA, CatalogIds.HLR, CatalogIds.PALLOF), // rueda → sin otra de rueda; corporal; polea
    CatalogIds.GHR to listOf(CatalogIds.CURL_L, CatalogIds.CURL_H), // ghd → máquina (el curl nórdico pide una ancla que «todo el material» no acredita)
    CatalogIds.FACE to listOf(CatalogIds.REAR_DELT_CABLE, CatalogIds.BAND_PA, CatalogIds.REAR_DELT), // polea; banda; mancuernas
    CatalogIds.PENDLAY to listOf(CatalogIds.ROW, CatalogIds.CSR, CatalogIds.ROW_DB), // barra; mancuernas
    CatalogIds.LAT to listOf(CatalogIds.LAT_CLOSE_GRIP, CatalogIds.PULLOVER, CatalogIds.PULLUP), // polea; polea; barra de dominadas
    CatalogIds.JM to listOf(CatalogIds.SKULLCRUSHER, CatalogIds.OH_TRI, CatalogIds.PUSHDOWN), // barra; polea
    CatalogIds.OH_TRI to listOf(CatalogIds.PUSHDOWN, CatalogIds.KICKBACK_CABLE), // polea
    CatalogIds.CRUNCH to listOf(CatalogIds.HLR, CatalogIds.PLANCHA, CatalogIds.PALLOF), // corporal; polea
    CatalogIds.CALF to listOf(CatalogIds.CALF_SEATED), // máquina
)

/** Máximo de días distintos por semana en que el mismo relleno de KPKN puede aparecer (regla C12). */
private const val KPKN_FILLER_MAX_DAYS_PER_WEEK = 2

/** Definición aproximada de una configuración: lo que va antes del primer «__» de su id (como en la prueba F-16). */
private fun definitionOfConfiguration(configurationId: String): String = configurationId.substringBefore("__")

/**
 * En cada semana, la 3.ª y siguientes apariciones (en días distintos) de un relleno KPKN (`T3_ACCESSORY` con
 * `source == KPKN_DEFAULT`) pasan a la primera alternativa de [alternatives] que no esté ya en ese día (ni por configuración ni
 * por definición) ni haya llegado a 2 días en la semana. Mismas series, descanso e id de slot; se quita la técnica, que
 * era del ejercicio original. Si ninguna alternativa sirve, la aparición se queda como está (C12 la seguirá señalando).
 */
fun List<WeekRecipe>.rotateKpknFillers(
    alternatives: Map<String, List<String>> = KPKN_FILLER_ALTERNATIVES,
): List<WeekRecipe> = map { week -> week.rotateKpknFillers(alternatives) }

private fun WeekRecipe.rotateKpknFillers(alternatives: Map<String, List<String>>): WeekRecipe {
    fun SlotRecipe.isKpknFiller(): Boolean = role == SlotRole.T3_ACCESSORY && source == SlotSource.KPKN_DEFAULT
    // Días (por índice) en que cada configuración aparece como relleno KPKN; empieza con lo que trae la receta.
    val usage = HashMap<String, MutableSet<Int>>()
    days.forEachIndexed { dayIndex, day ->
        day.slots.filter { it.isKpknFiller() }.forEach { usage.getOrPut(it.lift.configurationId) { HashSet() }.add(dayIndex) }
    }
    if (usage.values.none { it.size > KPKN_FILLER_MAX_DAYS_PER_WEEK }) return this
    val kept = HashMap<String, MutableSet<Int>>()
    val rotatedDays = days.mapIndexed { dayIndex, day ->
        var slots = day.slots
        day.slots.forEachIndexed { slotIndex, original ->
            if (!original.isKpknFiller()) return@forEachIndexed
            val configuration = original.lift.configurationId
            val keptDays = kept.getOrPut(configuration) { HashSet() }
            if (dayIndex in keptDays || keptDays.size < KPKN_FILLER_MAX_DAYS_PER_WEEK) {
                keptDays += dayIndex
                return@forEachIndexed
            }
            val others = slots.filterIndexed { index, _ -> index != slotIndex }.map { it.lift.configurationId }
            val otherDefinitions = others.map(::definitionOfConfiguration).toSet()
            val replacement = alternatives[configuration].orEmpty().firstOrNull { candidate ->
                candidate != configuration &&
                    candidate !in others &&
                    definitionOfConfiguration(candidate) !in otherDefinitions &&
                    (usage[candidate]?.size ?: 0) < KPKN_FILLER_MAX_DAYS_PER_WEEK
            }
            if (replacement == null) {
                keptDays += dayIndex
                return@forEachIndexed
            }
            usage.getValue(configuration).remove(dayIndex)
            usage.getOrPut(replacement) { HashSet() }.add(dayIndex)
            kept.getOrPut(replacement) { HashSet() }.add(dayIndex)
            slots = slots.mapIndexed { index, slot ->
                if (index != slotIndex) slot else slot.copy(lift = slot.lift.copy(configurationId = replacement), technique = null)
            }
        }
        if (slots === day.slots) day else day.copy(slots = slots)
    }
    return copy(days = rotatedDays)
}
