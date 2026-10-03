package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.domain.text.SpanishPlurals
import java.util.Locale
import kotlin.math.abs

/** Medida del porcentaje de una serie: el valor crudo de la receta o el %1RM efectivo. */
private typealias PercentMeasure = (SetRecipe, SlotRecipe, WeekRecipe) -> Double?

/**
 * Contrato de receta válida (B.S2 del plan de curaduría de programas).
 *
 * Diez reglas puras sobre la forma de una [TrainingPlanRecipe] que ninguna regla H/W/S de
 * [SessionCompositionPolicy] mira: rango de series por slot, configuración repetida, series
 * vacías, días declarados, descarga, consumidor de la progresión, base del porcentaje, enlaces
 * `supplementalOf`, técnica redundante y semanas idénticas.
 *
 * Estado actual: **modo inventario**. [evaluate] emite todo como [CompositionSeverity.SOFT] y
 * `SessionCompositionPolicy.evaluateRecipeRaw` lo suma al final, así que `hardFindings` no
 * cambia. B.S6 corrige los datos, declara exenciones justificadas (de 25 caracteres o más) y
 * pasa el contrato a HARD llamando con `CompositionSeverity.HARD`; C10 queda siempre como aviso.
 *
 * Los ámbitos usan el formato de la política (`w{n}/{día}`, `w{n}`, `block{i}/{nombre}`) más
 * `recipe` para lo que cuelga de la receta entera y una forma por slot, `w{n}/{día}/{slot}`, para
 * los hallazgos de un slot concreto. Por regla: C1, C3, C8 y C9 por slot; C2 por día; C4 y C10 por
 * semana; C7 por día, semana o bloque; C5 y C6 `recipe`. Así
 * [SessionCompositionPolicy.applyExemptions] los filtra igual que al resto de reglas y B.S6 puede
 * declarar exenciones por slot (p. ej. el slot `t1` del día «Banca/OHP» en cualquier semana: `w*`
 * seguido de la barra, `Banca/OHP`, la barra y `t1`). El glob está anclado: el glob de día (`w*`,
 * la barra y `Banca/OHP`) NO casa los hallazgos por slot; hay que añadirle la barra y un asterisco
 * final para cubrir todos los slots del día, o escribir el slot concreto. (Aquí no se puede
 * escribir el glob literal: un asterisco seguido de una barra cierra el comentario.)
 *
 * Esta ruta la recorre también `hardFindings` en la generación de planes, así que cada regla
 * evita trabajo y asignaciones mientras no encuentra nada: los textos solo se construyen para los
 * hallazgos que se emiten.
 */
object RecipeContractPolicy {
    const val C1_SET_RANGE = "C1_SET_RANGE"
    const val C2_DUP_CONFIG = "C2_DUP_CONFIG"
    const val C3_EMPTY_SET = "C3_EMPTY_SET"
    const val C4_CLAIMED_DAYS = "C4_CLAIMED_DAYS"
    const val C5_DELOAD_REQUIRED = "C5_DELOAD_REQUIRED"
    const val C6_PROGRESSION_CONSUMER = "C6_PROGRESSION_CONSUMER"
    const val C7_PERCENT_BASIS = "C7_PERCENT_BASIS"
    const val C8_SUPPLEMENTAL_LINK = "C8_SUPPLEMENTAL_LINK"
    const val C9_REDUNDANT_TECHNIQUE = "C9_REDUNDANT_TECHNIQUE"
    const val C10_IDENTICAL_WEEKS = "C10_IDENTICAL_WEEKS"

    /** Ámbito de los hallazgos que cuelgan de la receta entera. */
    const val RECIPE_SCOPE = "recipe"

    /** Todas las reglas del contrato, en orden. */
    val RULES: List<String> = listOf(
        C1_SET_RANGE,
        C2_DUP_CONFIG,
        C3_EMPTY_SET,
        C4_CLAIMED_DAYS,
        C5_DELOAD_REQUIRED,
        C6_PROGRESSION_CONSUMER,
        C7_PERCENT_BASIS,
        C8_SUPPLEMENTAL_LINK,
        C9_REDUNDANT_TECHNIQUE,
        C10_IDENTICAL_WEEKS,
    )

    /** Reglas que el plan deja siempre como aviso (SOFT) aunque el resto pase a HARD. */
    private val ADVISORY_RULES: Set<String> = setOf(C10_IDENTICAL_WEEKS)

    private const val MIN_SETS = 2
    private const val MAX_SETS = 8
    private const val MAX_SPEED_SETS = 12
    private const val DELOAD_REQUIRED_FROM_WEEKS = 8
    private const val IDENTICAL_WEEKS_RUN = 3

    // Umbrales de porcentaje que la política mide hoy sobre el valor crudo (los espeja C7).
    private const val ACCUMULATION_CEILING = 80.0
    private const val INTENSIFICATION_FLOOR = 77.0
    private const val PEAK_FLOOR = 85.0
    private const val DELOAD_CEILING = 70.0
    private const val TAPER_MIN_DAYS = 7
    private const val TAPER_MAX_DAYS = 10
    private const val PERCENT_EPSILON = 1e-9

    /** Bloques en los que H8 exige 1-6 repeticiones en las series pesadas del T1. */
    private val STRENGTH_GOALS: Set<BlockGoal> = setOf(
        BlockGoal.INTENSIFICATION,
        BlockGoal.PEAK,
        BlockGoal.REALIZATION,
        BlockGoal.SPECIFICITY,
    )

    /**
     * Hallazgos del contrato de [recipe], sin filtrar por exenciones. Con [severity] distinto de
     * SOFT, todas las reglas salen con esa severidad salvo C10, que es siempre aviso.
     */
    fun evaluate(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        severity: CompositionSeverity = CompositionSeverity.SOFT,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        findings += checkSetRange(recipe)
        findings += checkDuplicateConfigurations(recipe)
        findings += checkEmptySets(recipe)
        findings += checkClaimedDays(recipe)
        findings += checkDeloadRequired(recipe)
        findings += checkProgressionConsumer(recipe)
        findings += checkPercentBasis(recipe, metadata)
        findings += checkSupplementalLinks(recipe, metadata)
        findings += checkRedundantTechnique(recipe)
        findings += checkIdenticalWeeks(recipe)
        return findings.map { it.copy(severity = severityOf(it.rule, severity)) }
    }

    /** Número de hallazgos por regla del contrato (todas las reglas, también las que dan 0). */
    fun summarize(findings: List<CompositionFinding>): Map<String, Int> =
        RULES.associateWith { rule -> findings.count { it.rule == rule } }

    private fun severityOf(rule: String, requested: CompositionSeverity): CompositionSeverity =
        if (rule in ADVISORY_RULES) CompositionSeverity.SOFT else requested

    private fun finding(rule: String, scope: String, message: String): CompositionFinding =
        CompositionFinding(CompositionSeverity.SOFT, rule, scope, message)

    private fun dayScope(week: WeekRecipe, day: DayRecipe): String = "w${week.weekNumber}/${day.label}"

    /** Ámbito de un slot concreto (`w{n}/{día}/{slot}`): C1, C3, C8 y C9. */
    private fun slotScope(week: WeekRecipe, day: DayRecipe, slot: SlotRecipe): String =
        "${dayScope(week, day)}/${slot.id}"

    private fun weekScope(week: WeekRecipe): String = "w${week.weekNumber}"

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun fmtOrDash(value: Double?): String = if (value == null) "—" else "${fmt(value)} %"

    // ─── C1 · series de trabajo por slot ──────────────────────────────────────────

    /**
     * C1: un slot trae entre 2 y 8 series de trabajo (SPEED, entre 2 y 12). Controlados, no se
     * emiten: una sola serie si es top set, AMRAP, de 1 repetición o `PERCENT_DESIRED_MAX`; un
     * T3 de 1 serie vale en PEAK, REALIZATION, TAPER y DELOAD (por `blockGoal` o por `kind`); un
     * slot de receta `AUTHORED_EXACT` dentro de su `authoredSetRange`; y los perfiles propios,
     * cuyo ajustador decide las series. El ámbito es el del slot (`w{n}/{día}/{slot}`).
     */
    private fun checkSetRange(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val profile = recipe.compositionProfile
        if (profile == RecipeCompositionProfile.NATIVE_COMPACT || profile == RecipeCompositionProfile.MIXED_CARDIO) {
            return emptyList()
        }
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    val count = slot.sets.count { !it.isWarmup }
                    if (!isSetCountAllowed(slot, count, week, profile)) {
                        val maxSets = maxSetsOf(slot)
                        findings += finding(
                            C1_SET_RANGE,
                            slotScope(week, day, slot),
                            "${slot.id} (${slot.lift.configurationId}, ${slot.role}) tiene ${SpanishPlurals.sets(count)} " +
                                "de trabajo; el rango admitido es $MIN_SETS-$maxSets",
                        )
                    }
                }
            }
        }
        return findings
    }

    private fun maxSetsOf(slot: SlotRecipe): Int =
        if (slot.role == SlotRole.SPEED) MAX_SPEED_SETS else MAX_SETS

    private fun isSetCountAllowed(
        slot: SlotRecipe,
        count: Int,
        week: WeekRecipe,
        profile: RecipeCompositionProfile,
    ): Boolean {
        if (count in MIN_SETS..maxSetsOf(slot)) return true
        val authored = slot.authoredSetRange
        if (profile == RecipeCompositionProfile.AUTHORED_EXACT && authored != null && count in authored.min..authored.max) {
            return true
        }
        if (count != 1) return false
        val only = slot.sets.first { !it.isWarmup }
        val oneRep = (only.reps ?: only.repsMin) == 1
        if (only.isTopSet || only.amrap || oneRep || only.loadBasis == LoadBasis.PERCENT_DESIRED_MAX) return true
        return slot.role == SlotRole.T3_ACCESSORY && isPeakTaperOrDeload(week)
    }

    /** PEAK, REALIZATION, TAPER y DELOAD (por `blockGoal` o por `kind`); la política trata REALIZATION como pico. */
    private fun isPeakTaperOrDeload(week: WeekRecipe): Boolean =
        week.kind == WeekExecutionKind.DELOAD ||
            week.blockGoal == BlockGoal.PEAK ||
            week.blockGoal == BlockGoal.REALIZATION ||
            week.blockGoal == BlockGoal.TAPER ||
            week.blockGoal == BlockGoal.DELOAD

    // ─── C2 · misma configuración dos veces en un día ─────────────────────────────

    /**
     * C2: la misma `configurationId` en 2 o más slots de un día solo es válida si cada slot de
     * ese grupo cuelga con `supplementalOf` de otro del grupo, o es el ancla de alguno. Los pares
     * de autor (Sheiko a y a2, Wendler t1 y bbb, Coan SPEED y dl) cumplen la condición; si no la
     * cumplen, salen en el inventario.
     */
    private fun checkDuplicateConfigurations(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                if (day.slots.size < 2 || !hasRepeatedConfiguration(day)) return@forEach
                val byConfiguration = day.slots.groupBy { it.lift.configurationId.trim().lowercase() }
                byConfiguration.forEach { (configuration, slots) ->
                    if (configuration.isEmpty() || slots.size < 2) return@forEach
                    val ids = slots.map { it.id }
                    val unlinked = slots.filter { slot ->
                        val target = slot.supplementalOf
                        val pointsAtGroup = target != null && target != slot.id && target in ids
                        val isAnchor = slots.any { other -> other !== slot && other.supplementalOf == slot.id }
                        !pointsAtGroup && !isAnchor
                    }
                    if (unlinked.isNotEmpty()) {
                        findings += finding(
                            C2_DUP_CONFIG,
                            dayScope(week, day),
                            "$configuration aparece en ${slots.size} slots $ids y ${unlinked.map { it.id }} " +
                                "no cuelgan del ancla con supplementalOf",
                        )
                    }
                }
            }
        }
        return findings
    }

    private fun hasRepeatedConfiguration(day: DayRecipe): Boolean {
        val seen = HashSet<String>()
        return day.slots.any { slot -> !seen.add(slot.lift.configurationId.trim().lowercase()) }
    }

    // ─── C3 · series de trabajo vacías ────────────────────────────────────────────

    private fun SetRecipe.hasReps(): Boolean = reps != null || repsMin != null || repsMax != null

    private fun SetRecipe.hasIntensity(): Boolean =
        percent != null || rpe != null || rir != null || reference != null

    /**
     * C3: una serie de trabajo sin repeticiones (`reps`, `repsMin` y `repsMax` vacíos) o sin
     * intensidad (`percent`, `rpe`, `rir` y `reference` vacíos). Los calentamientos no cuentan. El
     * ámbito es el del slot (`w{n}/{día}/{slot}`).
     */
    private fun checkEmptySets(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    var workCount = 0
                    var withoutReps = 0
                    var withoutIntensity = 0
                    slot.sets.forEach { set ->
                        if (set.isWarmup) return@forEach
                        workCount += 1
                        if (!set.hasReps()) withoutReps += 1
                        if (!set.hasIntensity()) withoutIntensity += 1
                    }
                    if (withoutReps > 0) {
                        findings += finding(
                            C3_EMPTY_SET,
                            slotScope(week, day, slot),
                            "${slot.id} (${slot.lift.configurationId}): $withoutReps de ${SpanishPlurals.sets(workCount)} " +
                                "de trabajo sin repeticiones (reps, repsMin y repsMax vacíos)",
                        )
                    }
                    if (withoutIntensity > 0) {
                        findings += finding(
                            C3_EMPTY_SET,
                            slotScope(week, day, slot),
                            "${slot.id} (${slot.lift.configurationId}): $withoutIntensity de ${SpanishPlurals.sets(workCount)} " +
                                "de trabajo sin intensidad (porcentaje, RPE, RIR ni referencia)",
                        )
                    }
                }
            }
        }
        return findings
    }

    // ─── C4 · días declarados y weekday ───────────────────────────────────────────

    /**
     * C4: `claimedDaysPerWeek` distinto de los días reales de alguna semana, o un `weekday`
     * repetido dentro de una semana.
     */
    private fun checkClaimedDays(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val claimed = recipe.claimedDaysPerWeek
        recipe.weeks.forEach { week ->
            if (claimed != null && claimed != week.days.size) {
                findings += finding(
                    C4_CLAIMED_DAYS,
                    weekScope(week),
                    "claimedDaysPerWeek=$claimed pero la semana tiene ${SpanishPlurals.days(week.days.size)}",
                )
            }
            val repeated = week.days.mapNotNull { it.weekday }
                .groupingBy { it }.eachCount()
                .filterValues { it > 1 }
                .keys.sorted()
            if (repeated.isNotEmpty()) {
                findings += finding(
                    C4_CLAIMED_DAYS,
                    weekScope(week),
                    "weekday repetido dentro de la semana: $repeated",
                )
            }
        }
        return findings
    }

    // ─── C5 · descarga obligatoria ────────────────────────────────────────────────

    /**
     * C5: una receta que no se repite y dura 8 semanas o más trae al menos una semana de descarga
     * o de taper (por `kind` o por `blockGoal`). Un ciclo que se repite no la necesita al final.
     */
    private fun checkDeloadRequired(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        if (recipe.repeats || recipe.weeks.size < DELOAD_REQUIRED_FROM_WEEKS) return emptyList()
        if (recipe.weeks.any { isDeloadOrTaper(it) }) return emptyList()
        return listOf(
            finding(
                C5_DELOAD_REQUIRED,
                RECIPE_SCOPE,
                "${SpanishPlurals.weeks(recipe.weeks.size)} sin repetirse y ninguna es descarga ni taper " +
                    "(ni por kind ni por blockGoal)",
            ),
        )
    }

    private fun isDeloadOrTaper(week: WeekRecipe): Boolean =
        week.kind == WeekExecutionKind.DELOAD ||
            week.blockGoal == BlockGoal.DELOAD ||
            week.blockGoal == BlockGoal.TAPER

    // ─── C6 · consumidor de la progresión ─────────────────────────────────────────

    /**
     * C6: `progression` distinta de `None`, o una regla o gancho que exige AMRAP o top set sin
     * ninguna serie marcada en un slot con `liftSlot`. Hoy solo `AmrapDrivenTm` y
     * `RepTargetDrivenTm` tienen consumidor en ejecución (`ProgramAutoregulationEngine`, con los
     * defectos R-02 y L-04); el resto no tiene ninguno. El aviso sale siempre y su texto dice cuál
     * de los dos casos es, sin cambiar qué se emite; B.S3-S5 sustituye esta condición por el
     * registro de consumidores.
     */
    private fun checkProgressionConsumer(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val rule = recipe.progression
        val ruleName = rule::class.simpleName ?: rule.toString()
        if (rule != ProgressionRule.None) {
            val consumer = when (rule) {
                is ProgressionRule.AmrapDrivenTm, is ProgressionRule.RepTargetDrivenTm ->
                    "consumida por ProgramAutoregulationEngine con los defectos R-02/L-04 (B.S3–S5 la reescribe)"
                else -> "sin consumidor registrado en ejecución (B.S3–S5)"
            }
            findings += finding(C6_PROGRESSION_CONSUMER, RECIPE_SCOPE, "progression=$ruleName: $consumer")
        }
        val needsAmrap = rule is ProgressionRule.AmrapDrivenTm ||
            rule is ProgressionRule.RepTargetDrivenTm ||
            recipe.autoregulationHooks.any { it.kind == AutoregulationHookKind.AMRAP_TM }
        val needsTopSet = rule == ProgressionRule.TopSetPr
        if (needsAmrap && !hasMarkedSet(recipe) { set -> set.amrap }) {
            findings += finding(
                C6_PROGRESSION_CONSUMER,
                RECIPE_SCOPE,
                "$ruleName (o su gancho AMRAP_TM) exige series AMRAP, pero ningún slot con liftSlot tiene una",
            )
        }
        if (needsTopSet && !hasMarkedSet(recipe) { set -> set.isTopSet }) {
            findings += finding(
                C6_PROGRESSION_CONSUMER,
                RECIPE_SCOPE,
                "$ruleName exige un top set, pero ningún slot con liftSlot tiene uno",
            )
        }
        return findings
    }

    /** Hay una serie de trabajo marcada por [marked] en algún slot que declara su `liftSlot`. */
    private fun hasMarkedSet(recipe: TrainingPlanRecipe, marked: (SetRecipe) -> Boolean): Boolean =
        recipe.weeks.any { week ->
            week.days.any { day ->
                day.slots.any { slot ->
                    slot.lift.liftSlot != null && slot.sets.any { set -> !set.isWarmup && marked(set) }
                }
            }
        }

    // ─── C7 · base del porcentaje (TM frente a 1RM) ───────────────────────────────

    /**
     * C7: la política compara hoy el `percent` crudo en BLOCK, H5a, H8, H9, W3, W4 y la última
     * pesada del taper, aunque casi siempre sea un porcentaje del TM. Aquí cada chequeo se repite
     * con el %1RM efectivo ([PercentBasis.effective1RmPercent]) y se emite un hallazgo cuando el
     * veredicto cambiaría. Las series que no se pueden expresar sobre el 1RM (REP_MAX, RPE,
     * referencia de trabajo observado o lastre) se miden con su valor crudo en las dos pasadas, de
     * modo que solo cuenta la conversión TM→1RM y la resolución de `PERCENT_OF_TOP_SET`. No
     * cambia ninguna de esas reglas: solo inventaría. Sin ninguna conversión en la receta (TM del
     * 100 %, bases en 1RM o series sin porcentaje) no hay nada que comparar y sale vacío al instante.
     */
    private fun checkPercentBasis(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): List<CompositionFinding> {
        val trainingMax = recipe.trainingMaxPercent
        val raw: PercentMeasure = { set, _, _ -> set.percent }
        val effective: PercentMeasure = { set, slot, week ->
            PercentBasis.effective1RmPercent(set, slot, week, trainingMax) ?: set.percent
        }
        if (!conversionChangesAnyPercent(recipe, effective)) return emptyList()
        val cache = HashMap<String, ExerciseCompositionMetadata?>()
        val metaOf: (String) -> ExerciseCompositionMetadata? = { id ->
            if (cache.containsKey(id)) cache[id] else metadata.metadata(id).also { cache[id] = it }
        }
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day -> addDayBasisFindings(findings, day, week, metaOf, raw, effective) }
            addWeekBasisFindings(findings, week, metaOf, raw, effective)
        }
        addBlockBasisFindings(findings, recipe, raw, effective)
        return findings
    }

    private fun conversionChangesAnyPercent(recipe: TrainingPlanRecipe, effective: PercentMeasure): Boolean =
        recipe.weeks.any { week ->
            week.days.any { day ->
                day.slots.any { slot ->
                    slot.sets.any { set ->
                        val rawPercent = set.percent
                        rawPercent != null && abs((effective(set, slot, week) ?: rawPercent) - rawPercent) > PERCENT_EPSILON
                    }
                }
            }
        }

    private fun verdictLabel(fires: Boolean): String = if (fires) "hay hallazgo" else "no hay hallazgo"

    private fun heavySetsLabel(count: Int): String = SpanishPlurals.withNoun(count, "serie pesada", "series pesadas")

    private fun pairsLabel(count: Int): String = SpanishPlurals.withNoun(count, "par", "pares")

    private fun daysOrDash(days: Int?): String = if (days == null) "— días" else SpanishPlurals.days(days)

    /** Emite el hallazgo de C7 solo si el veredicto crudo y el efectivo difieren; los textos se construyen entonces. */
    private inline fun emitIfChanged(
        findings: MutableList<CompositionFinding>,
        check: String,
        item: String,
        scope: String,
        rawFires: Boolean,
        effectiveFires: Boolean,
        rawDetail: () -> String,
        effectiveDetail: () -> String,
    ) {
        if (rawFires == effectiveFires) return
        val label = if (item.isEmpty()) check else "$check $item"
        findings += finding(
            C7_PERCENT_BASIS,
            scope,
            "$label: con el % crudo ${verdictLabel(rawFires)} (${rawDetail()}) y con el %1RM efectivo " +
                "${verdictLabel(effectiveFires)} (${effectiveDetail()})",
        )
    }

    /** Serie pesada según la política: top set o al menos el 85 % medido con [percentOf]. */
    private fun isHeavy(set: SetRecipe, slot: SlotRecipe, week: WeekRecipe, percentOf: PercentMeasure): Boolean =
        set.isTopSet || (percentOf(set, slot, week) ?: 0.0) >= SessionCompositionPolicy.HEAVY_PERCENT

    private fun peakLabel(slot: SlotRecipe, week: WeekRecipe, percentOf: PercentMeasure): String =
        fmtOrDash(slot.sets.mapNotNull { percentOf(it, slot, week) }.maxOrNull())

    /** H5a cuenta el slot como axial pesado si no cuelga de otro y alguna serie es pesada (o REP_MAX). */
    private fun isHeavyAxial(slot: SlotRecipe, week: WeekRecipe, percentOf: PercentMeasure): Boolean =
        slot.supplementalOf == null &&
            slot.sets.any { set -> isHeavy(set, slot, week, percentOf) || set.loadBasis == LoadBasis.REP_MAX }

    /** Series del T1 «pesadas» con menos de 1 o más de 6 repeticiones (H8). */
    private fun heavyOutOfRepRange(slot: SlotRecipe, week: WeekRecipe, percentOf: PercentMeasure): Int =
        slot.sets.count { set ->
            if (set.isWarmup) return@count false
            val reps = set.reps ?: set.repsMax ?: 0
            isHeavy(set, slot, week, percentOf) && reps > 0 && reps !in 1..6
        }

    /** H5a, H8 y H9: chequeos de un día. */
    private fun addDayBasisFindings(
        findings: MutableList<CompositionFinding>,
        day: DayRecipe,
        week: WeekRecipe,
        metaOf: (String) -> ExerciseCompositionMetadata?,
        raw: PercentMeasure,
        effective: PercentMeasure,
    ) {
        val scope = dayScope(week, day)

        // H5a: más de un axial T1/T2 a ≥ 85 % o top set.
        val axialSlots = day.slots.filter { slot ->
            (slot.role == SlotRole.T1_MAIN || slot.role == SlotRole.T2_SUPPLEMENTAL) &&
                (metaOf(slot.lift.configurationId)?.axialLoadFactor ?: 0.0) >= SessionCompositionPolicy.AXIAL_SLOT_THRESHOLD
        }
        if (axialSlots.size > 1) {
            val rawHeavy = axialSlots.count { isHeavyAxial(it, week, raw) }
            val effectiveHeavy = axialSlots.count { isHeavyAxial(it, week, effective) }
            emitIfChanged(
                findings, "H5a", "", scope, rawHeavy > 1, effectiveHeavy > 1,
                { "axiales T1/T2: " + axialSlots.joinToString { "${it.id} ${peakLabel(it, week, raw)}" } },
                { "axiales T1/T2: " + axialSlots.joinToString { "${it.id} ${peakLabel(it, week, effective)}" } },
            )
        }

        // H8: en bloques de fuerza o pico, series pesadas del T1 fuera de 1-6 repeticiones.
        if (week.blockGoal in STRENGTH_GOALS) {
            day.slots.forEach { slot ->
                if (slot.role != SlotRole.T1_MAIN) return@forEach
                val rawOut = heavyOutOfRepRange(slot, week, raw)
                val effectiveOut = heavyOutOfRepRange(slot, week, effective)
                emitIfChanged(
                    findings, "H8", slot.id, scope, rawOut > 0, effectiveOut > 0,
                    { "T1 ${peakLabel(slot, week, raw)}, ${heavySetsLabel(rawOut)} fuera de 1-6 repeticiones" },
                    { "T1 ${peakLabel(slot, week, effective)}, ${heavySetsLabel(effectiveOut)} fuera de 1-6 repeticiones" },
                )
            }
        }

        // H9: solo el T1 sube su suelo de descanso (de 180 a 240 s) cuando alguna serie es pesada.
        day.slots.forEach { slot ->
            if (slot.role != SlotRole.T1_MAIN) return@forEach
            val rawHeavy = slot.sets.any { (raw(it, slot, week) ?: 0.0) >= SessionCompositionPolicy.HEAVY_PERCENT }
            val effectiveHeavy = slot.sets.any { (effective(it, slot, week) ?: 0.0) >= SessionCompositionPolicy.HEAVY_PERCENT }
            if (rawHeavy == effectiveHeavy) return@forEach
            val meta = metaOf(slot.lift.configurationId)
            val isolation = CompositionTaxonomy.isIsolation(
                CompositionTaxonomy.familyOf(meta?.movementPatternId),
                meta?.articulationType,
                slot.lift.configurationId,
            )
            val rawMinimum = SessionCompositionPolicy.minimumRestSeconds(slot.role, rawHeavy, isolation)
            val effectiveMinimum = SessionCompositionPolicy.minimumRestSeconds(slot.role, effectiveHeavy, isolation)
            emitIfChanged(
                findings, "H9", slot.id, scope, slot.restSeconds < rawMinimum, slot.restSeconds < effectiveMinimum,
                {
                    "descanso ${slot.restSeconds} s, mínimo $rawMinimum s con carga " +
                        "${if (rawHeavy) "pesada" else "no pesada"} (${peakLabel(slot, week, raw)})"
                },
                {
                    "descanso ${slot.restSeconds} s, mínimo $effectiveMinimum s con carga " +
                        "${if (effectiveHeavy) "pesada" else "no pesada"} (${peakLabel(slot, week, effective)})"
                },
            )
        }
    }

    /** Posiciones de los días con un T1 axial pesado (W3); sin weekday, un descanso entre días listados. */
    private fun heavyAxialDays(
        week: WeekRecipe,
        metaOf: (String) -> ExerciseCompositionMetadata?,
        percentOf: PercentMeasure,
    ): List<Int> = week.days.mapIndexedNotNull { index, day ->
        val heavyAxial = day.slots.any { slot ->
            slot.role == SlotRole.T1_MAIN &&
                (metaOf(slot.lift.configurationId)?.axialLoadFactor ?: 0.0) >= SessionCompositionPolicy.AXIAL_SLOT_THRESHOLD &&
                slot.sets.any { set -> isHeavy(set, slot, week, percentOf) }
        }
        if (heavyAxial) day.weekday ?: (index * 2) else null
    }

    private fun hasClosePair(positions: List<Int>): Boolean =
        positions.zipWithNext().any { (first, second) -> second - first < 2 }

    private fun hasHeavyT1(day: DayRecipe, token: String, week: WeekRecipe, percentOf: PercentMeasure): Boolean =
        day.slots.any { slot ->
            slot.lift.configurationId.contains(token) &&
                slot.role == SlotRole.T1_MAIN &&
                slot.sets.any { set -> isHeavy(set, slot, week, percentOf) }
        }

    /** Pares de días consecutivos con peso muerto pesado seguido de sentadilla pesada (W4). */
    private fun deadliftBeforeSquatPairs(week: WeekRecipe, percentOf: PercentMeasure): Int =
        week.days.sortedBy { it.weekday ?: Int.MAX_VALUE }.zipWithNext().count { (previous, next) ->
            val previousDay = previous.weekday
            val nextDay = next.weekday
            val consecutive = if (previousDay != null && nextDay != null) {
                nextDay - previousDay == 1 || (previousDay == 7 && nextDay == 1)
            } else {
                true
            }
            consecutive &&
                hasHeavyT1(previous, "deadlift", week, percentOf) &&
                hasHeavyT1(next, "squat", week, percentOf)
        }

    /** W3 y W4: chequeos de una semana. */
    private fun addWeekBasisFindings(
        findings: MutableList<CompositionFinding>,
        week: WeekRecipe,
        metaOf: (String) -> ExerciseCompositionMetadata?,
        raw: PercentMeasure,
        effective: PercentMeasure,
    ) {
        val scope = weekScope(week)

        // W3: T1 axiales pesados en días consecutivos.
        val rawDays = heavyAxialDays(week, metaOf, raw)
        val effectiveDays = heavyAxialDays(week, metaOf, effective)
        emitIfChanged(
            findings, "W3", "", scope, hasClosePair(rawDays), hasClosePair(effectiveDays),
            { "T1 axiales pesados en los días $rawDays" },
            { "T1 axiales pesados en los días $effectiveDays" },
        )

        // W4: peso muerto pesado el día anterior a una sentadilla pesada.
        val rawClashes = deadliftBeforeSquatPairs(week, raw)
        val effectiveClashes = deadliftBeforeSquatPairs(week, effective)
        emitIfChanged(
            findings, "W4", "", scope, rawClashes > 0, effectiveClashes > 0,
            { "${pairsLabel(rawClashes)} de días con peso muerto pesado seguido de sentadilla pesada" },
            { "${pairsLabel(effectiveClashes)} de días con peso muerto pesado seguido de sentadilla pesada" },
        )
    }

    /** Porcentajes de las series de trabajo de todos los T1 de las semanas de un bloque. */
    private fun blockT1Percents(weeks: List<WeekRecipe>, percentOf: PercentMeasure): List<Double> {
        val percents = ArrayList<Double>()
        weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    if (slot.role != SlotRole.T1_MAIN) return@forEach
                    slot.sets.forEach { set ->
                        if (set.isWarmup) return@forEach
                        val value = percentOf(set, slot, week)
                        if (value != null) percents.add(value)
                    }
                }
            }
        }
        return percents
    }

    /** BLOCK (acumulación, intensificación, pico, descarga y taper): chequeos de un bloque. */
    private fun addBlockBasisFindings(
        findings: MutableList<CompositionFinding>,
        recipe: TrainingPlanRecipe,
        raw: PercentMeasure,
        effective: PercentMeasure,
    ) {
        val byBlock = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
        byBlock.forEach { (index, weeks) ->
            val head = weeks.first()
            val scope = "block$index/${head.blockName}"
            when (head.blockGoal) {
                BlockGoal.ACCUMULATION -> {
                    val before = blockT1Percents(weeks, raw)
                    val after = blockT1Percents(weeks, effective)
                    emitIfChanged(
                        findings, "BLOCK acumulación (T1 > 80 %)", "", scope,
                        before.any { it > ACCUMULATION_CEILING }, after.any { it > ACCUMULATION_CEILING },
                        { "T1 máx ${fmtOrDash(before.maxOrNull())}" },
                        { "T1 máx ${fmtOrDash(after.maxOrNull())}" },
                    )
                }
                BlockGoal.INTENSIFICATION -> {
                    val before = blockT1Percents(weeks, raw)
                    val after = blockT1Percents(weeks, effective)
                    emitIfChanged(
                        findings, "BLOCK intensificación (T1 bajo 78 %)", "", scope,
                        before.any { it in 1.0..INTENSIFICATION_FLOOR }, after.any { it in 1.0..INTENSIFICATION_FLOOR },
                        { "T1 mín ${fmtOrDash(before.minOrNull())}" },
                        { "T1 mín ${fmtOrDash(after.minOrNull())}" },
                    )
                }
                BlockGoal.PEAK, BlockGoal.REALIZATION -> {
                    val before = blockT1Percents(weeks, raw)
                    val after = blockT1Percents(weeks, effective)
                    emitIfChanged(
                        findings, "BLOCK pico (T1 ≥ 85 %)", "", scope,
                        before.isNotEmpty() && before.none { it >= PEAK_FLOOR },
                        after.isNotEmpty() && after.none { it >= PEAK_FLOOR },
                        { "T1 máx ${fmtOrDash(before.maxOrNull())}" },
                        { "T1 máx ${fmtOrDash(after.maxOrNull())}" },
                    )
                }
                BlockGoal.DELOAD -> {
                    val before = blockT1Percents(weeks, raw)
                    val after = blockT1Percents(weeks, effective)
                    emitIfChanged(
                        findings, "BLOCK descarga (T1 > 70 %)", "", scope,
                        before.any { it > DELOAD_CEILING }, after.any { it > DELOAD_CEILING },
                        { "T1 máx ${fmtOrDash(before.maxOrNull())}" },
                        { "T1 máx ${fmtOrDash(after.maxOrNull())}" },
                    )
                }
                BlockGoal.TAPER -> {
                    val before = taperDays(weeks, raw)
                    val after = taperDays(weeks, effective)
                    emitIfChanged(
                        findings, "BLOCK taper (última pesada a 7-10 días del test)", "", scope,
                        before != null && before !in TAPER_MIN_DAYS..TAPER_MAX_DAYS,
                        after != null && after !in TAPER_MIN_DAYS..TAPER_MAX_DAYS,
                        { "última pesada a ${daysOrDash(before)} del test" },
                        { "última pesada a ${daysOrDash(after)} del test" },
                    )
                }
                else -> Unit
            }
        }
    }

    /**
     * Días entre la última pesada del taper y el día de test (el de weekday más alto de la última
     * semana), o null si no hay ni pesada ni test. Espeja `checkTaperLastHeavy` de la política.
     */
    private fun taperDays(weeks: List<WeekRecipe>, percentOf: PercentMeasure): Int? {
        val ordered = weeks.sortedBy { it.weekNumber }
        val lastWeek = ordered.lastOrNull() ?: return null
        val testDay = lastWeek.days.maxByOrNull { it.weekday ?: 0 } ?: return null
        val testWeekday = testDay.weekday ?: 7
        val lastHeavy = ordered.flatMap { week -> week.days.map { day -> week to day } }
            .lastOrNull { (week, day) ->
                val isTest = week.weekNumber == lastWeek.weekNumber && day.label == testDay.label
                !isTest && day.slots.any { slot ->
                    slot.role == SlotRole.T1_MAIN &&
                        slot.sets.any { set -> !set.isWarmup && isHeavy(set, slot, week, percentOf) }
                }
            } ?: return null
        val heavyWeek = lastHeavy.first.weekNumber
        val heavyDay = lastHeavy.second.weekday ?: 1
        return (lastWeek.weekNumber - heavyWeek) * 7 + (testWeekday - heavyDay)
    }

    // ─── C8 · enlaces supplementalOf ──────────────────────────────────────────────

    /**
     * C8: `supplementalOf` apunta a un slot que no existe en el día, a sí mismo, o a un slot de
     * otro grupo de patrón (sentadilla, bisagra, empuje, tirón, extensión de rodilla u otra
     * familia). La política exime de H2, H3, H4, H5 y H7 a los slots colgados, así que un enlace
     * incoherente silencia reglas de verdad. El ámbito es el del slot que cuelga
     * (`w{n}/{día}/{slot}`).
     */
    private fun checkSupplementalLinks(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                if (day.slots.none { it.supplementalOf != null }) return@forEach
                val byId = day.slots.associateBy { it.id }
                day.slots.forEach { slot ->
                    val targetId = slot.supplementalOf ?: return@forEach
                    val target = byId[targetId]
                    val message: String? = when {
                        targetId == slot.id -> "${slot.id} cuelga de sí mismo (supplementalOf=$targetId)"
                        target == null ->
                            "${slot.id} cuelga de '$targetId', que no existe en el día (slots: ${byId.keys.toList()})"
                        else -> {
                            val slotGroup = patternGroupOf(slot, metadata)
                            val targetGroup = patternGroupOf(target, metadata)
                            if (slotGroup != null && targetGroup != null && slotGroup != targetGroup) {
                                "${slot.id} (${slot.lift.configurationId}, $slotGroup) cuelga de $targetId " +
                                    "(${target.lift.configurationId}, $targetGroup): son de otro grupo de patrón"
                            } else {
                                null
                            }
                        }
                    }
                    if (message != null) {
                        findings += finding(C8_SUPPLEMENTAL_LINK, slotScope(week, day, slot), message)
                    }
                }
            }
        }
        return findings
    }

    /** Grupo de patrón de un slot, o null si el catálogo no lo conoce. */
    private fun patternGroupOf(slot: SlotRecipe, metadata: ExerciseCompositionMetadataProvider): String? {
        val meta = metadata.metadata(slot.lift.configurationId) ?: return null
        val family = CompositionTaxonomy.familyOf(meta.movementPatternId) ?: return null
        return when (family) {
            PatternFamily.SQUAT -> "SQUAT"
            PatternFamily.HINGE, PatternFamily.HIP_EXTENSION -> "HINGE"
            PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH -> "PUSH"
            PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL -> "PULL"
            PatternFamily.KNEE_EXTENSION -> "KNEE_EXT"
            else -> family.name
        }
    }

    // ─── C9 · técnica redundante o parche ─────────────────────────────────────────

    private data class TechniquePatch(
        val configurationId: String,
        val technique: TechniqueModifier,
        val suggested: String,
    )

    /**
     * Parches conocidos: una técnica sobre una configuración cuya variante ya existe como
     * configuración propia (`suggested` es siempre una constante de [CatalogIds]).
     */
    private val TECHNIQUE_PATCHES: List<TechniquePatch> = listOf(
        TechniquePatch(CatalogIds.BP, TechniqueModifier.CLOSE_GRIP, CatalogIds.BP_CLOSE_GRIP),
        TechniquePatch(CatalogIds.LAT, TechniqueModifier.CLOSE_GRIP, CatalogIds.LAT_CLOSE_GRIP),
        TechniquePatch(CatalogIds.SQ_LOW, TechniqueModifier.PAUSE_2S, CatalogIds.SQ_PAUSED),
        TechniquePatch(CatalogIds.SQ_HIGH, TechniqueModifier.PAUSE_2S, CatalogIds.SQ_PAUSED),
        TechniquePatch(CatalogIds.DL, TechniqueModifier.TO_KNEES, CatalogIds.DL_TO_KNEES),
        TechniquePatch(CatalogIds.DL, TechniqueModifier.DEFICIT, CatalogIds.DL_DEF),
        TechniquePatch(CatalogIds.BP_FLOOR, TechniqueModifier.CHAINS_BANDS, CatalogIds.BP_CHAINS),
    )

    /** Palabras del id de configuración que ya expresan la técnica (se comparan como palabra entera). */
    private val IMPLICIT_TECHNIQUE_MARKERS: Map<TechniqueModifier, List<String>> = mapOf(
        TechniqueModifier.BOX to listOf("cajon", "box"),
        TechniqueModifier.DEFICIT to listOf("deficit"),
        TechniqueModifier.PAUSE_2S to listOf("paused", "pausa"),
        TechniqueModifier.PIN to listOf("anderson", "pin", "pins"),
        TechniqueModifier.CHAINS_BANDS to listOf("cadenas", "chains", "bandas", "bands"),
        TechniqueModifier.TO_KNEES to listOf("to_knees"),
        TechniqueModifier.CLOSE_GRIP to listOf("close_grip"),
    )

    /**
     * C9: la `technique` del slot ya está implícita en el id de su configuración (cajón, déficit,
     * pausa, Anderson, pines...) o es un parche de una técnica que el catálogo ya tiene como
     * configuración propia (agarre cerrado sobre `BP` y `LAT`, pausa sobre las sentadillas, hasta
     * rodillas sobre `DL`, déficit sobre `DL`, cadenas sobre `BP_FLOOR`). En el parche el mensaje
     * dice a qué configuración migrar la receta; los destinos existen en `CatalogIds` y en el
     * catálogo v2 (lo comprueba `RecipeContractPolicyTest`). El ámbito es el del slot
     * (`w{n}/{día}/{slot}`).
     */
    private fun checkRedundantTechnique(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    val technique = slot.technique ?: return@forEach
                    val configuration = slot.lift.configurationId
                    val marker = IMPLICIT_TECHNIQUE_MARKERS[technique].orEmpty()
                        .firstOrNull { hasIdToken(configuration, it) }
                    if (marker != null) {
                        findings += finding(
                            C9_REDUNDANT_TECHNIQUE,
                            slotScope(week, day, slot),
                            "${slot.id}: la técnica $technique ya está en la configuración $configuration ('$marker'); sobra el modificador",
                        )
                    }
                    val patch = TECHNIQUE_PATCHES.firstOrNull { it.configurationId == configuration && it.technique == technique }
                    if (patch != null) {
                        findings += finding(
                            C9_REDUNDANT_TECHNIQUE,
                            slotScope(week, day, slot),
                            "${slot.id}: $configuration + $technique es un parche; " +
                                "existe ${patch.suggested}: migrar la receta a esa configuración",
                        )
                    }
                }
            }
        }
        return findings
    }

    private fun hasIdToken(configurationId: String, token: String): Boolean =
        "_${configurationId.lowercase().replace("__", "_")}_".contains("_${token}_")

    // ─── C10 · semanas idénticas ──────────────────────────────────────────────────

    /**
     * C10: 3 o más semanas de entrenamiento seguidas con exactamente los mismos días, slots y
     * series. Quedan fuera las recetas cuya progresión cambia la carga entre semanas
     * (`WeeklyKg`, `CycleIncrement`) y las que progresan en ejecución con `nativeProgression`.
     * Una semana de descarga o de taper corta la racha.
     */
    private fun checkIdenticalWeeks(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val progression = recipe.progression
        if (progression is ProgressionRule.WeeklyKg ||
            progression is ProgressionRule.CycleIncrement ||
            recipe.nativeProgression != null
        ) {
            return emptyList()
        }
        val ordered = recipe.weeks.sortedBy { it.weekNumber }
        val findings = mutableListOf<CompositionFinding>()
        var start = 0
        while (start < ordered.size) {
            val first = ordered[start]
            if (!isTrainingWeek(first)) {
                start += 1
                continue
            }
            var end = start + 1
            while (end < ordered.size && isTrainingWeek(ordered[end]) && ordered[end].days == first.days) {
                end += 1
            }
            val length = end - start
            if (length >= IDENTICAL_WEEKS_RUN) {
                findings += finding(
                    C10_IDENTICAL_WEEKS,
                    weekScope(first),
                    "las semanas ${first.weekNumber}-${ordered[end - 1].weekNumber} son idénticas " +
                        "(${SpanishPlurals.weeks(length)} seguidas) " +
                        "y la progresión (${progression::class.simpleName}) no las cambia",
                )
            }
            start = end
        }
        return findings
    }

    private fun isTrainingWeek(week: WeekRecipe): Boolean =
        week.kind == WeekExecutionKind.TRAINING && !isDeloadOrTaper(week)
}
