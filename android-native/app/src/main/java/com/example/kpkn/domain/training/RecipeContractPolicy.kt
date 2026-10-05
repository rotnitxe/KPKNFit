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

/**
 * Contrato de receta válida (B.S2 del plan de curaduría de programas).
 *
 * Diez reglas puras sobre la forma de una [TrainingPlanRecipe] que ninguna regla H/W/S de
 * [SessionCompositionPolicy] mira: rango de series por slot, configuración repetida, series
 * vacías, días declarados, descarga, consumidor de la progresión, base del porcentaje, enlaces
 * `supplementalOf`, técnica redundante y semanas idénticas.
 *
 * B.S6: la ruta de validación pide [CompositionSeverity.HARD] para C1–C9; [evaluate]
 * conserva el modo SOFT explícito para el inventario. C10 queda siempre como aviso.
 * C7 se cumple en [SessionCompositionPolicy] usando [PercentBasis] en H/W/BLOCK/taper,
 * sin mantener una segunda implementación de esos chequeos.
 *
 * Los ámbitos usan el formato de la política (`w{n}/{día}`, `w{n}`, `block{i}/{nombre}`) más
 * `recipe` para lo que cuelga de la receta entera y una forma por slot, `w{n}/{día}/{slot}`, para
 * los hallazgos de un slot concreto. Por regla: C1, C3, C8 y C9 por slot; C2 por día; C4 y C10 por
 * semana; C5 y C6 `recipe`. C7 se cumple en la política de intensidad compartida. Así
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
     * C6: `progression` distinta de `None` sin consumidor registrado en ejecución, o una regla o
     * gancho que exige AMRAP o top set sin ninguna serie marcada en un slot con `liftSlot`.
     * [ProgressionConsumers.executable] dice qué reglas tienen consumidor: `CycleIncrement` y
     * `WeeklyKg` las consume el motor de progresión de autor (B.S3) y `AmrapDrivenTm`,
     * `RepTargetDrivenTm`, `TopSetPr` y `RepMaxAutoregulated` `ProgramAutoregulationEngine` como
     * propuestas `ADJUST_TM` (B.S4). Una regla con consumidor no da hallazgo; la única sin consumidor
     * es `WeeklyPercent`, que B.S6 retiró de las recetas publicadas (la clase se conserva para
     * decodificar el JSON de programas ya guardados).
     */
    private fun checkProgressionConsumer(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val rule = recipe.progression
        val ruleName = rule::class.simpleName ?: rule.toString()
        if (rule != ProgressionRule.None && !ProgressionConsumers.isExecutable(rule)) {
            findings += finding(
                C6_PROGRESSION_CONSUMER,
                RECIPE_SCOPE,
                "progression=$ruleName: sin consumidor registrado en ejecución (ProgressionConsumers.executable)",
            )
        }
        val needsAmrap = rule is ProgressionRule.AmrapDrivenTm ||
            rule is ProgressionRule.RepTargetDrivenTm ||
            recipe.autoregulationHooks.any { it.kind == AutoregulationHookKind.AMRAP_TM }
        val needsTopSet = rule is ProgressionRule.TopSetPr
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

    // C7 no repite H/W/BLOCK: SessionCompositionPolicy mide siempre mediante PercentBasis.
    // Su contrato diferencial (%TM ↔ %1RM) se verifica sobre esa ruta compartida.

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
