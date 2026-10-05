package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Contrato de receta válida (B.S2): una receta sintética por regla, con su caso positivo y su
 * caso negativo, más los falsos positivos controlados de C1.
 */
class RecipeContractPolicyTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    // ─── Constructores mínimos ────────────────────────────────────────────────────

    private fun slotOf(
        id: String,
        role: SlotRole,
        configurationId: String,
        sets: List<SetRecipe>,
        liftSlot: LiftSlot? = null,
        technique: TechniqueModifier? = null,
        supplementalOf: String? = null,
        rest: Int = 180,
    ): SlotRecipe = slot(
        id = id,
        role = role,
        configurationId = configurationId,
        sets = sets,
        restSeconds = rest,
        liftSlot = liftSlot,
        technique = technique,
        supplementalOf = supplementalOf,
    )

    /** [count] series de trabajo de [reps] repeticiones al [percent] % del TM. */
    private fun sets(count: Int, reps: Int = 5, percent: Double = 70.0): List<SetRecipe> =
        List(count) { SetRecipe(reps = reps, percent = percent) }

    private fun dayOf(slots: List<SlotRecipe>, label: String = "Dia", weekday: Int? = null): DayRecipe =
        day(label, slots, weekday = weekday)

    private fun weekOf(
        days: List<DayRecipe>,
        number: Int = 1,
        goal: BlockGoal = BlockGoal.ACCUMULATION,
        kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
        block: Int = 0,
    ): WeekRecipe = weekRecipe(number, block, "Bloque", goal, days, kind)

    private fun recipeOf(
        weeks: List<WeekRecipe>,
        progression: ProgressionRule = ProgressionRule.None,
        repeats: Boolean = false,
        claimedDays: Int? = null,
        trainingMaxPercent: Double = 0.90,
        profile: RecipeCompositionProfile = RecipeCompositionProfile.LEGACY_STANDARD,
        hooks: List<AutoregulationHook> = emptyList(),
        exemptions: List<RecipeCompositionExemption> = emptyList(),
        nativeProgression: NativeProgressionSpec? = null,
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "synthetic",
        weeks = weeks,
        trainingMaxPercent = trainingMaxPercent,
        progression = progression,
        exemptions = exemptions,
        autoregulationHooks = hooks,
        claimedDaysPerWeek = claimedDays,
        repeats = repeats,
        nativeProgression = nativeProgression,
        compositionProfile = profile,
    )

    /** Receta de una semana con un único día. */
    private fun singleDay(slots: List<SlotRecipe>, goal: BlockGoal = BlockGoal.ACCUMULATION): TrainingPlanRecipe =
        recipeOf(listOf(weekOf(listOf(dayOf(slots)), goal = goal)))

    /** Día con un T1 de sentadilla, banca o peso muerto, para los casos de base de porcentaje. */
    private fun t1Day(
        configurationId: String,
        liftSlot: LiftSlot,
        sets: List<SetRecipe>,
        weekday: Int?,
        label: String = "Dia",
        rest: Int = 240,
    ): DayRecipe = dayOf(
        listOf(slotOf("t1", SlotRole.T1_MAIN, configurationId, sets, liftSlot, rest = rest)),
        label = label,
        weekday = weekday,
    )

    private fun contract(recipe: TrainingPlanRecipe, rule: String): List<CompositionFinding> =
        RecipeContractPolicy.evaluate(recipe, metadata).filter { it.rule == rule }

    private fun assertNone(label: String, findings: List<CompositionFinding>) {
        assertTrue("$label: se esperaba ningún hallazgo y salió ${findings.map { it.message }}", findings.isEmpty())
    }

    /** Los ids de slot de los hallazgos de C1, C2... cuyos mensajes empiezan por el id del slot. */
    private fun slotIdsOf(findings: List<CompositionFinding>): Set<String> =
        findings.map { it.message.substringBefore(' ') }.toSet()

    // ─── C1 · series por slot ─────────────────────────────────────────────────────

    @Test
    fun c1_flags_slots_outside_two_to_eight_working_sets_and_ignores_warmups() {
        val warmups = List(3) { SetRecipe(reps = 5, percent = 40.0, isWarmup = true) }
        val recipe = singleDay(
            listOf(
                slotOf("ok", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets(5), LiftSlot.SQUAT),
                slotOf("one", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(1), LiftSlot.BENCH),
                slotOf("nine", SlotRole.T3_ACCESSORY, CatalogIds.LAT, sets(9, 10)),
                // Tres calentamientos más una serie de trabajo: los calentamientos no suman.
                slotOf("warmedUp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, warmups + sets(1), LiftSlot.DEADLIFT),
            ),
        )
        val flagged = contract(recipe, RecipeContractPolicy.C1_SET_RANGE)
        assertEquals(setOf("one", "nine", "warmedUp"), slotIdsOf(flagged))
        // El ámbito es el del slot (w{n}/{día}/{slot}) para poder exentar un slot sin silenciar el día.
        assertEquals(
            mapOf("one" to "w1/Dia/one", "nine" to "w1/Dia/nine", "warmedUp" to "w1/Dia/warmedUp"),
            flagged.associate { it.message.substringBefore(' ') to it.scope },
        )
        assertTrue(flagged.all { it.severity == CompositionSeverity.SOFT })
        // Concordancia de número: «1 serie», no «1 series».
        assertTrue(flagged.single { it.message.startsWith("one ") }.message.contains("tiene 1 serie de trabajo;"))
        assertTrue(flagged.single { it.message.startsWith("nine ") }.message.contains("tiene 9 series de trabajo;"))
    }

    @Test
    fun c1_one_set_is_valid_for_top_sets_amrap_single_reps_and_desired_max() {
        val recipe = singleDay(
            listOf(
                slotOf("speed", SlotRole.SPEED, CatalogIds.SQ_BOX, sets(10, 2, 60.0), LiftSlot.SQUAT, rest = 60),
                slotOf(
                    "top", SlotRole.T1_MAIN, CatalogIds.SQ_LOW,
                    listOf(SetRecipe(reps = 3, percent = 90.0, isTopSet = true)), LiftSlot.SQUAT,
                ),
                slotOf(
                    "amrap", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP,
                    listOf(SetRecipe(reps = 5, percent = 80.0, amrap = true)), LiftSlot.BENCH,
                ),
                slotOf(
                    "single", SlotRole.T1_MAIN, CatalogIds.DL,
                    listOf(SetRecipe(reps = 1, percent = 95.0)), LiftSlot.DEADLIFT,
                ),
                slotOf(
                    "desired", SlotRole.T1_MAIN, CatalogIds.DL_SUMO,
                    listOf(SetRecipe(reps = 2, percent = 90.0, loadBasis = LoadBasis.PERCENT_DESIRED_MAX)),
                    LiftSlot.DEADLIFT,
                ),
            ),
        )
        assertNone("controlados de C1", contract(recipe, RecipeContractPolicy.C1_SET_RANGE))
    }

    @Test
    fun c1_speed_slots_allow_up_to_twelve_sets() {
        fun speed(count: Int) = singleDay(
            listOf(slotOf("speed", SlotRole.SPEED, CatalogIds.SQ_BOX, sets(count, 2, 60.0), LiftSlot.SQUAT, rest = 60)),
        )
        assertNone("SPEED con 12 series", contract(speed(12), RecipeContractPolicy.C1_SET_RANGE))
        assertEquals(1, contract(speed(13), RecipeContractPolicy.C1_SET_RANGE).size)
        assertEquals("una serie SPEED que no es top set sigue fuera", 1, contract(speed(1), RecipeContractPolicy.C1_SET_RANGE).size)
    }

    @Test
    fun c1_t3_with_one_set_is_valid_only_in_peak_realization_taper_or_deload_weeks() {
        fun t3Week(goal: BlockGoal, kind: WeekExecutionKind = WeekExecutionKind.TRAINING) = recipeOf(
            listOf(
                weekOf(
                    listOf(dayOf(listOf(slotOf("shrug", SlotRole.T3_ACCESSORY, CatalogIds.SHRUG, sets(1, 8, 70.0))))),
                    goal = goal,
                    kind = kind,
                ),
            ),
        )
        // REALIZATION cuenta como pico, igual que en SessionCompositionPolicy (H6, W2 y BLOCK pico/realización).
        listOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER, BlockGoal.DELOAD).forEach { goal ->
            assertNone("T3 de 1 serie en $goal", contract(t3Week(goal), RecipeContractPolicy.C1_SET_RANGE))
        }
        assertNone(
            "T3 de 1 serie en una semana con kind DELOAD",
            contract(t3Week(BlockGoal.ACCUMULATION, WeekExecutionKind.DELOAD), RecipeContractPolicy.C1_SET_RANGE),
        )
        listOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION).forEach { goal ->
            assertEquals("T3 de 1 serie en $goal", 1, contract(t3Week(goal), RecipeContractPolicy.C1_SET_RANGE).size)
        }
    }

    @Test
    fun c1_authored_exact_accepts_the_declared_set_range_and_native_profiles_are_exempt() {
        fun withRange(profile: RecipeCompositionProfile, count: Int, range: AuthoredSetRange?) = recipeOf(
            listOf(
                weekOf(
                    listOf(
                        dayOf(
                            listOf(
                                slotOf("a", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(count), LiftSlot.BENCH)
                                    .copy(authoredSetRange = range),
                            ),
                        ),
                    ),
                ),
            ),
            profile = profile,
        )
        val c1 = RecipeContractPolicy.C1_SET_RANGE
        // Una serie dentro del rango publicado (1-1): válida solo si la receta es AUTHORED_EXACT.
        assertNone("1 serie con rango 1-1 en AUTHORED_EXACT", contract(withRange(RecipeCompositionProfile.AUTHORED_EXACT, 1, AuthoredSetRange(1, 1)), c1))
        assertEquals(1, contract(withRange(RecipeCompositionProfile.LEGACY_STANDARD, 1, AuthoredSetRange(1, 1)), c1).size)
        // Diez series dentro de un rango publicado 10-10.
        assertNone("10 series con rango 10-10 en AUTHORED_EXACT", contract(withRange(RecipeCompositionProfile.AUTHORED_EXACT, 10, AuthoredSetRange(10, 10)), c1))
        // Diez series fuera del rango publicado 3-4 y fuera de 2..8.
        assertEquals(1, contract(withRange(RecipeCompositionProfile.AUTHORED_EXACT, 10, AuthoredSetRange(3, 4)), c1).size)
        // Perfiles propios: exentos.
        assertNone("NATIVE_COMPACT", contract(withRange(RecipeCompositionProfile.NATIVE_COMPACT, 1, null), c1))
        assertNone("MIXED_CARDIO", contract(withRange(RecipeCompositionProfile.MIXED_CARDIO, 9, null), c1))
    }

    // ─── C2 · configuración repetida en un día ────────────────────────────────────

    @Test
    fun c2_flags_the_same_configuration_twice_in_a_day_without_an_anchor() {
        val unlinked = singleDay(
            listOf(
                slotOf("a", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH),
                slotOf("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(3), LiftSlot.BENCH),
            ),
        )
        val findings = contract(unlinked, RecipeContractPolicy.C2_DUP_CONFIG)
        assertEquals(1, findings.size)
        assertEquals("w1/Dia", findings.single().scope)
    }

    @Test
    fun c2_accepts_pairs_linked_by_supplemental_of_and_reports_only_the_loose_slot() {
        val paired = singleDay(
            listOf(
                slotOf("a", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH),
                slotOf("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(3), LiftSlot.BENCH, supplementalOf = "a"),
            ),
        )
        assertNone("par ancla + suplementario", contract(paired, RecipeContractPolicy.C2_DUP_CONFIG))

        val withLoose = singleDay(
            listOf(
                slotOf("a", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH),
                slotOf("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(3), LiftSlot.BENCH, supplementalOf = "a"),
                slotOf("c", SlotRole.T3_ACCESSORY, CatalogIds.BP, sets(3, 10)),
            ),
        )
        val findings = contract(withLoose, RecipeContractPolicy.C2_DUP_CONFIG)
        assertEquals(1, findings.size)
        assertTrue(findings.single().message, findings.single().message.contains("[c] no cuelgan"))
    }

    @Test
    fun c2_ignores_the_same_configuration_on_different_days() {
        val recipe = recipeOf(
            listOf(
                weekOf(
                    listOf(
                        dayOf(listOf(slotOf("a", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH)), "Lunes", 1),
                        dayOf(listOf(slotOf("a", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH)), "Jueves", 4),
                    ),
                ),
            ),
        )
        assertNone("misma configuración en días distintos", contract(recipe, RecipeContractPolicy.C2_DUP_CONFIG))
    }

    // ─── C3 · series vacías ───────────────────────────────────────────────────────

    @Test
    fun c3_flags_work_sets_without_reps_or_without_intensity_and_ignores_warmups() {
        val recipe = singleDay(
            listOf(
                slotOf("empty", SlotRole.T3_ACCESSORY, CatalogIds.LAT, listOf(SetRecipe(), SetRecipe(reps = 8, rpe = 8.0))),
                slotOf("noIntensity", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, listOf(SetRecipe(reps = 8))),
                slotOf("warm", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, listOf(SetRecipe(isWarmup = true)) + sets(3), LiftSlot.DEADLIFT),
                slotOf("range", SlotRole.T3_ACCESSORY, CatalogIds.FACE, listOf(SetRecipe(repsMin = 8, repsMax = 12, rir = 2))),
                slotOf(
                    "reference", SlotRole.T3_ACCESSORY, CatalogIds.ROW,
                    listOf(
                        SetRecipe(
                            reps = 5,
                            reference = PlanLoadReference(
                                kind = PlanLoadReferenceKind.EXERCISE_1RM,
                                configurationId = CatalogIds.ROW,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val findings = contract(recipe, RecipeContractPolicy.C3_EMPTY_SET)
        assertEquals(
            "empty da dos hallazgos (reps e intensidad) y noIntensity uno: ${findings.map { it.message }}",
            3,
            findings.size,
        )
        assertEquals(setOf("empty", "noIntensity"), slotIdsOf(findings))
        assertEquals(2, findings.count { it.message.startsWith("empty ") })
        assertTrue(findings.any { it.message.contains("sin repeticiones") })
        assertTrue(findings.count { it.message.contains("sin intensidad") } == 2)
        // El ámbito es el del slot (w{n}/{día}/{slot}).
        assertEquals(setOf("w1/Dia/empty", "w1/Dia/noIntensity"), findings.map { it.scope }.toSet())
        // Concordancia de número: «1 de 1 serie», «1 de 2 series».
        assertTrue(findings.any { it.message.contains("1 de 1 serie de trabajo sin intensidad") })
        assertTrue(findings.any { it.message.contains("1 de 2 series de trabajo sin repeticiones") })
    }

    // ─── C4 · días declarados ─────────────────────────────────────────────────────

    @Test
    fun c4_flags_claimed_days_that_do_not_match_the_week_and_repeated_weekdays() {
        fun days(vararg weekdays: Int): List<DayRecipe> =
            weekdays.mapIndexed { index, weekday ->
                dayOf(listOf(slotOf("a", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(3), LiftSlot.BENCH)), "D$index", weekday)
            }
        val c4 = RecipeContractPolicy.C4_CLAIMED_DAYS

        val wrongClaim = contract(recipeOf(listOf(weekOf(days(1, 3, 5))), claimedDays = 4), c4)
        assertEquals(1, wrongClaim.size)
        assertEquals("w1", wrongClaim.single().scope)
        assertTrue(wrongClaim.single().message.contains("claimedDaysPerWeek=4"))
        assertTrue(wrongClaim.single().message, wrongClaim.single().message.endsWith("la semana tiene 3 días"))

        // Concordancia de número: «1 día», no «1 días».
        val oneDay = contract(recipeOf(listOf(weekOf(days(1))), claimedDays = 3), c4)
        assertEquals(1, oneDay.size)
        assertTrue(oneDay.single().message, oneDay.single().message.endsWith("la semana tiene 1 día"))

        assertNone("claim correcto", contract(recipeOf(listOf(weekOf(days(1, 3, 5))), claimedDays = 3), c4))
        assertNone("sin claim", contract(recipeOf(listOf(weekOf(days(1, 3, 5)))), c4))

        val repeated = contract(recipeOf(listOf(weekOf(days(1, 1, 3))), claimedDays = 3), c4)
        assertEquals(1, repeated.size)
        assertTrue(repeated.single().message, repeated.single().message.contains("weekday repetido"))
    }

    // ─── C5 · descarga obligatoria ────────────────────────────────────────────────

    private fun weeksOf(
        count: Int,
        goalOf: (Int) -> BlockGoal = { BlockGoal.ACCUMULATION },
        kindOf: (Int) -> WeekExecutionKind = { WeekExecutionKind.TRAINING },
    ): List<WeekRecipe> = (1..count).map { number ->
        weekOf(
            listOf(dayOf(listOf(slotOf("a", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(3), LiftSlot.BENCH)), weekday = 1)),
            number = number,
            goal = goalOf(number),
            kind = kindOf(number),
        )
    }

    @Test
    fun c5_requires_a_deload_or_taper_week_in_long_recipes_that_do_not_repeat() {
        val c5 = RecipeContractPolicy.C5_DELOAD_REQUIRED
        val noDeload = contract(recipeOf(weeksOf(8)), c5)
        assertEquals(1, noDeload.size)
        assertEquals(RecipeContractPolicy.RECIPE_SCOPE, noDeload.single().scope)
        assertTrue(noDeload.single().message, noDeload.single().message.startsWith("8 semanas sin repetirse"))

        assertNone("7 semanas", contract(recipeOf(weeksOf(7)), c5))
        assertNone("se repite", contract(recipeOf(weeksOf(8), repeats = true), c5))
        assertNone(
            "DELOAD por blockGoal",
            contract(recipeOf(weeksOf(8, goalOf = { if (it == 8) BlockGoal.DELOAD else BlockGoal.ACCUMULATION })), c5),
        )
        assertNone(
            "TAPER por blockGoal",
            contract(recipeOf(weeksOf(8, goalOf = { if (it == 8) BlockGoal.TAPER else BlockGoal.ACCUMULATION })), c5),
        )
        assertNone(
            "DELOAD por kind",
            contract(recipeOf(weeksOf(8, kindOf = { if (it == 4) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING })), c5),
        )
    }

    // ─── C6 · consumidor de la progresión ─────────────────────────────────────────

    private fun recipeWithT1(
        sets: List<SetRecipe>,
        liftSlot: LiftSlot?,
        progression: ProgressionRule,
        hooks: List<AutoregulationHook> = emptyList(),
    ): TrainingPlanRecipe = recipeOf(
        listOf(weekOf(listOf(dayOf(listOf(slotOf("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets, liftSlot)))))),
        progression = progression,
        hooks = hooks,
    )

    @Test
    fun c6_flags_only_the_progression_rules_without_a_consumer() {
        val c6 = RecipeContractPolicy.C6_PROGRESSION_CONSUMER
        val noConsumer = "sin consumidor registrado en ejecución (ProgressionConsumers.executable)"
        assertNone("progression None", contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.None), c6))

        // B.S3: una regla con consumidor real en ejecución no da hallazgo (motor de progresión de autor o
        // autorregulación). Las de autorregulación llevan serie AMRAP para no activar el segundo aviso.
        val amrap = listOf(SetRecipe(reps = 5, percent = 85.0, amrap = true))
        assertNone(
            "CycleIncrement por ciclo",
            contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.CycleIncrement(2.5, 5.0)), c6),
        )
        assertNone(
            "CycleIncrement por bloque",
            contract(
                recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK)),
                c6,
            ),
        )
        assertNone(
            "WeeklyKg",
            contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.WeeklyKg(mapOf(2 to 5.0))), c6),
        )
        assertNone("AmrapDrivenTm", contract(recipeWithT1(amrap, LiftSlot.SQUAT, ProgressionRule.AmrapDrivenTm()), c6))
        assertNone(
            "RepTargetDrivenTm",
            contract(recipeWithT1(amrap, LiftSlot.SQUAT, ProgressionRule.RepTargetDrivenTm()), c6),
        )

        // B.S4: el top set y la serie al máximo salen como propuestas ADJUST_TM de ProgramAutoregulationEngine.
        // El top set evita el segundo aviso de TopSetPr (sin top set en un slot con liftSlot).
        val topSet = listOf(SetRecipe(reps = 5, percent = 85.0, isTopSet = true))
        assertNone("TopSetPr", contract(recipeWithT1(topSet, LiftSlot.SQUAT, ProgressionRule.TopSetPr()), c6))
        assertNone(
            "RepMaxAutoregulated",
            contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.RepMaxAutoregulated), c6),
        )

        // Sin consumidor en ejecución (B.S6): un hallazgo por regla, con el ámbito de la receta.
        mapOf(
            "WeeklyPercent" to ProgressionRule.WeeklyPercent(2.5),
        ).forEach { (name, rule) ->
            val findings = contract(recipeWithT1(topSet, LiftSlot.SQUAT, rule), c6)
            assertEquals(name, 1, findings.size)
            assertEquals(name, RecipeContractPolicy.RECIPE_SCOPE, findings.single().scope)
            assertEquals(name, "progression=$name: $noConsumer", findings.single().message)
        }
    }

    @Test
    fun c6_requires_marked_amrap_or_top_sets_in_slots_with_a_lift_slot() {
        val c6 = RecipeContractPolicy.C6_PROGRESSION_CONSUMER
        val topSet = listOf(SetRecipe(reps = 5, percent = 85.0, isTopSet = true))
        val amrap = listOf(SetRecipe(reps = 5, percent = 85.0, amrap = true))

        // TopSetPr tiene consumidor (B.S4): solo queda la exigencia de un top set en un slot con liftSlot.
        assertEquals(1, contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.TopSetPr()), c6).size)
        assertEquals(1, contract(recipeWithT1(topSet, null, ProgressionRule.TopSetPr()), c6).size)
        assertEquals(0, contract(recipeWithT1(topSet, LiftSlot.SQUAT, ProgressionRule.TopSetPr()), c6).size)
        // Con valores propios la regla sigue siendo TopSetPr: la exigencia de top set no depende del default.
        assertEquals(1, contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.TopSetPr(2.5, 5.0)), c6).size)

        // AmrapDrivenTm y RepTargetDrivenTm tienen consumidor, así que solo queda la exigencia de AMRAP.
        listOf(ProgressionRule.AmrapDrivenTm(), ProgressionRule.RepTargetDrivenTm()).forEach { rule ->
            val name = rule::class.simpleName
            assertEquals(name, 1, contract(recipeWithT1(sets(3), LiftSlot.SQUAT, rule), c6).size)
            assertEquals(name, 1, contract(recipeWithT1(amrap, null, rule), c6).size)
            assertEquals(name, 0, contract(recipeWithT1(amrap, LiftSlot.SQUAT, rule), c6).size)
        }

        // El gancho AMRAP_TM también exige AMRAP, aunque la progresión sea None.
        val hooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM))
        assertEquals(1, contract(recipeWithT1(sets(3), LiftSlot.SQUAT, ProgressionRule.None, hooks), c6).size)
        assertNone("gancho con AMRAP", contract(recipeWithT1(amrap, LiftSlot.SQUAT, ProgressionRule.None, hooks), c6))
    }

    // ─── C7 · una sola base de intensidad en la política ──────────────────────────

    private val percentRules = setOf("H5a", "H8", "H9", "W3", "W4", "BLOCK")

    private fun intensityFindings(recipe: TrainingPlanRecipe): List<CompositionFinding> =
        SessionCompositionPolicy.evaluateRecipeRaw(recipe, metadata).filter { it.rule in percentRules }

    /** Misma dosis escrita en %1RM: oráculo diferencial para la ruta real de validación. */
    private fun inOneRm(recipe: TrainingPlanRecipe): TrainingPlanRecipe = recipe.copy(
        trainingMaxPercent = 1.0,
        weeks = recipe.weeks.map { week ->
            week.copy(days = week.days.map { day ->
                day.copy(slots = day.slots.map { slot ->
                    slot.copy(sets = slot.sets.map { set ->
                        PercentBasis.effective1RmPercent(set, slot, week, recipe.trainingMaxPercent)?.let {
                            set.copy(percent = it, loadBasis = LoadBasis.PERCENT_1RM)
                        } ?: set
                    })
                })
            })
        },
    )

    private fun assertIntensityEquivalent(recipe: TrainingPlanRecipe) {
        assertEquals("%TM y la misma dosis en %1RM deben dar el mismo contrato", intensityFindings(inOneRm(recipe)), intensityFindings(recipe))
        assertNone("C7 no mantiene un espejo de los chequeos", contract(recipe, RecipeContractPolicy.C7_PERCENT_BASIS))
    }

    @Test
    fun c7_block_thresholds_use_effective_one_rm_for_peak_intensification_accumulation_and_deload() {
        val cases = listOf(
            Triple(BlockGoal.PEAK, 93.0, true), // 83,7 % 1RM: el pico no alcanza 85 %.
            Triple(BlockGoal.PEAK, 95.0, false),
            Triple(BlockGoal.INTENSIFICATION, 85.0, true), // 76,5 % 1RM: bajo 78 %.
            Triple(BlockGoal.ACCUMULATION, 88.0, false), // 79,2 % 1RM: no sobrepasa 80 %.
            Triple(BlockGoal.DELOAD, 75.0, false), // 67,5 % 1RM: no sobrepasa 70 %.
            Triple(BlockGoal.DELOAD, 80.0, true),
        )
        cases.forEach { (goal, percent, expected) ->
            val set = SetRecipe(reps = if (goal == BlockGoal.PEAK) 2 else 5, percent = percent)
            val recipe = recipeOf(listOf(weekOf(listOf(t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, listOf(set), 1)), goal = goal)))
            assertIntensityEquivalent(recipe)
            assertEquals("$goal / $percent %TM", expected, intensityFindings(recipe).any { it.rule == "BLOCK" })
        }
    }

    @Test
    fun c7_h8_uses_effective_one_rm_and_retains_the_top_set_exception() {
        val recipe = recipeOf(listOf(weekOf(listOf(t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(4, 8, 88.0), 1)), goal = BlockGoal.INTENSIFICATION)))
        assertIntensityEquivalent(recipe)
        assertFalse("88 %TM son 79,2 %1RM y no disparan H8 pesado", intensityFindings(recipe).any { it.rule == "H8" })
        val top = recipe.copy(weeks = recipe.weeks.map { week -> week.copy(days = week.days.map { day -> day.copy(slots = day.slots.map { slot -> slot.copy(sets = slot.sets.map { it.copy(isTopSet = true) }) }) }) })
        assertIntensityEquivalent(top)
        assertTrue("el top set sigue siendo pesado por diseño", intensityFindings(top).any { it.rule == "H8" })
    }

    @Test
    fun c7_h5a_h9_w3_and_w4_use_effective_one_rm_at_the_heavy_boundary() {
        val h5a = recipeOf(listOf(weekOf(listOf(dayOf(listOf(
            slotOf("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets(3, 3, 88.0), LiftSlot.SQUAT, rest = 240),
            slotOf("dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, sets(3, 3, 88.0), LiftSlot.DEADLIFT, rest = 240),
        ), weekday = 1)))))
        val h9 = recipeOf(listOf(weekOf(listOf(t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(3, 3, 90.0), 1, rest = 180)))))
        val consecutive = recipeOf(listOf(weekOf(listOf(
            t1Day(CatalogIds.DL, LiftSlot.DEADLIFT, sets(3, 3, 90.0), 1, "PM"),
            t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(3, 3, 90.0), 2, "Sentadilla"),
        ))))
        listOf(h5a, h9, consecutive).forEach { assertIntensityEquivalent(it) }
        assertFalse(intensityFindings(h5a).any { it.rule == "H5a" })
        assertFalse(intensityFindings(h9).any { it.rule == "H9" })
        assertFalse(intensityFindings(consecutive).any { it.rule in setOf("W3", "W4") })
        listOf(h5a, h9, consecutive).forEach { below ->
            val heavy = below.copy(weeks = below.weeks.map { week -> week.copy(days = week.days.map { day -> day.copy(slots = day.slots.map { slot -> slot.copy(sets = slot.sets.map { it.copy(percent = 95.0) }) }) }) })
            assertIntensityEquivalent(heavy)
            val expected = when (below) { h5a -> setOf("H5a"); h9 -> setOf("H9"); else -> setOf("W3", "W4") }
            expected.forEach { rule -> assertTrue("$rule a 95 %TM (85,5 %1RM)", intensityFindings(heavy).any { it.rule == rule }) }
        }
    }

    @Test
    fun c7_taper_last_heavy_uses_effective_one_rm() {
        val weeks = listOf(
            weekOf(listOf(
                t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(2, 2, 97.0), 1, "Pesado A"),
                t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(2, 2, 90.0), 5, "Pesado B"),
                t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(2, 5, 60.0), 2, "Ligero", rest = 180),
            ), number = 1, goal = BlockGoal.TAPER),
            weekOf(listOf(
                t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(2, 1, 100.0), 5, "Test"),
                t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, sets(2, 5, 60.0), 2, "Ligero 2", rest = 180),
            ), number = 2, goal = BlockGoal.TAPER),
        )
        val recipe = recipeOf(weeks)
        assertIntensityEquivalent(recipe)
        assertTrue("solo 97 %TM (87,3 %1RM) es pesado, a 11 días del test", intensityFindings(recipe).any { it.rule == "BLOCK" && it.message.contains("11 días") })
    }

    @Test
    fun c7_resolves_top_set_percent_and_ignores_observed_load_references_in_heavy_checks() {
        val volume = slotOf("volume", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets(5, 5, 95.0).map { it.copy(loadBasis = LoadBasis.PERCENT_OF_TOP_SET) }, LiftSlot.SQUAT, rest = 180)
        val anchor = slotOf("top", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, listOf(SetRecipe(reps = 1, percent = 80.0, isTopSet = true)), LiftSlot.SQUAT, rest = 240)
        val recipe = recipeOf(listOf(weekOf(listOf(dayOf(listOf(volume), "Volumen", 1), dayOf(listOf(anchor), "Top", 5)))))
        assertIntensityEquivalent(recipe)
        assertFalse("95 % del top de 80 %TM no exige descanso pesado", intensityFindings(recipe).any { it.rule == "H9" })
        val observed = recipeOf(listOf(weekOf(listOf(t1Day(CatalogIds.SQ_LOW, LiftSlot.SQUAT, listOf(SetRecipe(
            reps = 5, percent = 100.0, reference = PlanLoadReference(PlanLoadReferenceKind.OBSERVED_WORKING_SET, CatalogIds.SQ_LOW),
        )), 1, rest = 180)))))
        assertFalse("100 % de la carga observada no es 100 %1RM", intensityFindings(observed).any { it.rule == "H9" })
    }

    // ─── C8 · enlaces supplementalOf ──────────────────────────────────────────────

    @Test
    fun c8_flags_links_to_missing_slots_to_itself_and_to_another_pattern_group() {
        val recipe = singleDay(
            listOf(
                slotOf("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets(4), LiftSlot.SQUAT),
                // Empuje colgado de una sentadilla (Madcow): otro grupo.
                slotOf("inc", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP_INC, sets(3), LiftSlot.BENCH, supplementalOf = "sq"),
                // Bisagra colgada de una sentadilla (nSuns, Westside): otro grupo.
                slotOf("sumo", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL_SUMO, sets(3), LiftSlot.DEADLIFT, supplementalOf = "sq"),
                slotOf("ghost", SlotRole.T3_ACCESSORY, CatalogIds.LAT, sets(3, 10), supplementalOf = "nope"),
                slotOf("self", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, sets(3, 10), supplementalOf = "self"),
                // Mismo grupo: press militar colgado de la banca (empuje con empuje) y peso muerto rumano de un peso muerto.
                slotOf("bp", SlotRole.T1_MAIN, CatalogIds.BP, sets(3), LiftSlot.BENCH),
                slotOf("ohp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.OHP, sets(3), LiftSlot.OVERHEAD, supplementalOf = "bp"),
                slotOf("dl", SlotRole.T1_MAIN, CatalogIds.DL, sets(3), LiftSlot.DEADLIFT),
                slotOf("rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, sets(3), LiftSlot.DEADLIFT, supplementalOf = "dl"),
            ),
        )
        val findings = contract(recipe, RecipeContractPolicy.C8_SUPPLEMENTAL_LINK)
        assertEquals(setOf("inc", "sumo", "ghost", "self"), slotIdsOf(findings))
        // El ámbito es el del slot que cuelga (w{n}/{día}/{slot}).
        assertEquals(
            mapOf("inc" to "w1/Dia/inc", "sumo" to "w1/Dia/sumo", "ghost" to "w1/Dia/ghost", "self" to "w1/Dia/self"),
            findings.associate { it.message.substringBefore(' ') to it.scope },
        )
        assertTrue(findings.single { it.message.startsWith("inc ") }.message.contains("otro grupo de patrón"))
        assertTrue(findings.single { it.message.startsWith("ghost ") }.message.contains("no existe"))
        assertTrue(findings.single { it.message.startsWith("self ") }.message.contains("sí mismo"))
    }

    // ─── C9 · técnica redundante o parche ─────────────────────────────────────────

    @Test
    fun c9_flags_techniques_already_in_the_configuration_and_patches_whose_own_configuration_exists() {
        fun withTechnique(configurationId: String, technique: TechniqueModifier): List<CompositionFinding> =
            contract(
                singleDay(listOf(slotOf("s", SlotRole.T2_SUPPLEMENTAL, configurationId, sets(3), technique = technique))),
                RecipeContractPolicy.C9_REDUNDANT_TECHNIQUE,
            )

        // Técnica ya implícita en el id de la configuración.
        listOf(
            CatalogIds.SQ_BOX to TechniqueModifier.BOX,
            CatalogIds.DL_DEF to TechniqueModifier.DEFICIT,
            CatalogIds.SQ_PIN to TechniqueModifier.PIN,
            CatalogIds.BP_PAUSE to TechniqueModifier.PAUSE_2S,
            CatalogIds.BP_CHAINS to TechniqueModifier.CHAINS_BANDS,
            CatalogIds.BP_CLOSE_GRIP to TechniqueModifier.CLOSE_GRIP,
            CatalogIds.SQ_PAUSED to TechniqueModifier.PAUSE_2S,
            CatalogIds.DL_TO_KNEES to TechniqueModifier.TO_KNEES,
        ).forEach { (configuration, technique) ->
            val findings = withTechnique(configuration, technique)
            assertEquals("$configuration + $technique", 1, findings.size)
            assertTrue(findings.single().message, findings.single().message.contains("ya está en la configuración"))
            // El ámbito es el del slot (w{n}/{día}/{slot}).
            assertEquals("w1/Dia/s", findings.single().scope)
        }

        // Parches cuya configuración propia ya existe: el mensaje dice a cuál migrar la receta.
        listOf(
            Triple(CatalogIds.BP, TechniqueModifier.CLOSE_GRIP, CatalogIds.BP_CLOSE_GRIP),
            Triple(CatalogIds.LAT, TechniqueModifier.CLOSE_GRIP, CatalogIds.LAT_CLOSE_GRIP),
            Triple(CatalogIds.SQ_LOW, TechniqueModifier.PAUSE_2S, CatalogIds.SQ_PAUSED),
            Triple(CatalogIds.SQ_HIGH, TechniqueModifier.PAUSE_2S, CatalogIds.SQ_PAUSED),
            Triple(CatalogIds.DL, TechniqueModifier.TO_KNEES, CatalogIds.DL_TO_KNEES),
            Triple(CatalogIds.DL, TechniqueModifier.DEFICIT, CatalogIds.DL_DEF),
            Triple(CatalogIds.BP_FLOOR, TechniqueModifier.CHAINS_BANDS, CatalogIds.BP_CHAINS),
        ).forEach { (configuration, technique, suggested) ->
            val findings = withTechnique(configuration, technique)
            assertEquals("$configuration + $technique", 1, findings.size)
            val message = findings.single().message
            assertTrue(message, message.contains("parche"))
            assertTrue(message, message.contains("existe $suggested: migrar la receta a esa configuración"))
            assertFalse(message, message.contains("sin configuración propia"))
            assertEquals("w1/Dia/s", findings.single().scope)
            // «existe» es verdad: el destino está en el catálogo v2 que carga la prueba.
            assertNotNull("el destino $suggested debe existir en el catálogo", metadata.metadata(suggested))
        }

        // Técnicas que no son parche ni están en el id: sin hallazgo.
        assertNone("SPEED sobre BP", withTechnique(CatalogIds.BP, TechniqueModifier.SPEED))
        assertNone("SPEED sobre SQ_BOX", withTechnique(CatalogIds.SQ_BOX, TechniqueModifier.SPEED))
        assertNone("PRE_EXHAUST sobre LAT", withTechnique(CatalogIds.LAT, TechniqueModifier.PRE_EXHAUST))
    }

    // ─── C10 · semanas idénticas ──────────────────────────────────────────────────

    @Test
    fun c10_flags_three_or_more_identical_training_weeks_in_a_row() {
        val c10 = RecipeContractPolicy.C10_IDENTICAL_WEEKS
        val three = contract(recipeOf(weeksOf(3)), c10)
        assertEquals(1, three.size)
        assertEquals("w1", three.single().scope)
        assertTrue(three.single().message, three.single().message.contains("1-3"))
        assertTrue(three.single().message, three.single().message.contains("(3 semanas seguidas)"))

        assertTrue(contract(recipeOf(weeksOf(5)), c10).single().message.contains("1-5"))
        assertNone("dos semanas", contract(recipeOf(weeksOf(2)), c10))

        // Una semana de descarga corta la racha: 2 + descarga + 2.
        val interrupted = weeksOf(5, kindOf = { if (it == 3) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING })
        assertNone("racha cortada por una descarga", contract(recipeOf(interrupted), c10))

        // Semanas de descarga por blockGoal tampoco cuentan como entrenamiento.
        assertNone("todas DELOAD", contract(recipeOf(weeksOf(4, goalOf = { BlockGoal.DELOAD })), c10))

        // Si la tercera semana cambia, no hay racha de tres.
        val changed = weeksOf(3).mapIndexed { index, week ->
            if (index == 2) {
                week.copy(days = listOf(dayOf(listOf(slotOf("a", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(4), LiftSlot.BENCH)), weekday = 1)))
            } else {
                week
            }
        }
        assertNone("tercera semana distinta", contract(recipeOf(changed), c10))
    }

    @Test
    fun c10_excludes_recipes_whose_progression_changes_the_load_between_weeks() {
        val c10 = RecipeContractPolicy.C10_IDENTICAL_WEEKS
        assertNone("WeeklyKg", contract(recipeOf(weeksOf(4), progression = ProgressionRule.WeeklyKg(mapOf(2 to 5.0))), c10))
        assertNone("CycleIncrement", contract(recipeOf(weeksOf(4), progression = ProgressionRule.CycleIncrement(2.5, 5.0)), c10))
        assertNone("nativeProgression", contract(recipeOf(weeksOf(4), nativeProgression = NativeProgressionSpec()), c10))
        // TopSetPr o ninguna regla no cambian las semanas: sí salen.
        assertEquals(1, contract(recipeOf(weeksOf(4), progression = ProgressionRule.TopSetPr()), c10).size)
    }

    // ─── Conjunto: receta sana, severidad, resumen y cableado ─────────────────────

    @Test
    fun a_clean_recipe_has_no_contract_findings() {
        val monday = dayOf(
            listOf(
                slotOf("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets(4, 5, 70.0), LiftSlot.SQUAT, rest = 240),
                slotOf("rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, rpeSets(3, 8, 7.5), LiftSlot.DEADLIFT),
                slotOf("lat", SlotRole.T3_ACCESSORY, CatalogIds.LAT, rpeSets(3, 10, 8.0)),
            ),
            label = "Lunes",
            weekday = 1,
        )
        val recipe = recipeOf(weeksOf(2).map { it.copy(days = listOf(monday)) }, claimedDays = 1)
        val findings = RecipeContractPolicy.evaluate(recipe, metadata)
        assertTrue(findings.joinToString("\n") { "${it.rule} ${it.scope}: ${it.message}" }, findings.isEmpty())
    }

    @Test
    fun evaluate_does_not_fail_on_degenerate_recipes() {
        assertNone("sin semanas", RecipeContractPolicy.evaluate(recipeOf(emptyList()), metadata))
        assertNone("semana sin días", RecipeContractPolicy.evaluate(recipeOf(listOf(weekOf(emptyList()))), metadata))
        assertNone("día sin slots", RecipeContractPolicy.evaluate(recipeOf(listOf(weekOf(listOf(dayOf(emptyList()))))), metadata))
        // Un slot sin ninguna serie: C1 lo cuenta como 0 series y nada más falla.
        val noSets = singleDay(listOf(slotOf("empty", SlotRole.T3_ACCESSORY, CatalogIds.LAT, emptyList())))
        assertEquals(1, contract(noSets, RecipeContractPolicy.C1_SET_RANGE).size)
        // Una configuración que el catálogo no conoce no rompe C7 ni C8.
        val unknown = singleDay(
            listOf(
                slotOf("x", SlotRole.T1_MAIN, "no_existe__default", sets(3, 5, 90.0)),
                slotOf("y", SlotRole.T2_SUPPLEMENTAL, "tampoco__default", sets(3, 5, 90.0), supplementalOf = "x"),
            ),
            goal = BlockGoal.PEAK,
        )
        assertNone("C8 sin metadatos del catálogo", contract(unknown, RecipeContractPolicy.C8_SUPPLEMENTAL_LINK))
    }

    @Test
    fun severity_defaults_to_soft_and_hard_keeps_c10_as_an_advisory() {
        val recipe = recipeOf(
            weeksOf(3).map { week ->
                week.copy(days = listOf(dayOf(listOf(slotOf("one", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(1), LiftSlot.BENCH)), weekday = 1)))
            },
        )
        val soft = RecipeContractPolicy.evaluate(recipe, metadata)
        assertTrue(soft.any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertTrue(soft.any { it.rule == RecipeContractPolicy.C10_IDENTICAL_WEEKS })
        assertTrue("todo SOFT por defecto", soft.all { it.severity == CompositionSeverity.SOFT })

        val hard = RecipeContractPolicy.evaluate(recipe, metadata, CompositionSeverity.HARD)
        assertTrue(hard.filter { it.rule == RecipeContractPolicy.C1_SET_RANGE }.all { it.severity == CompositionSeverity.HARD })
        assertTrue(hard.filter { it.rule == RecipeContractPolicy.C10_IDENTICAL_WEEKS }.all { it.severity == CompositionSeverity.SOFT })
    }

    @Test
    fun summarize_counts_findings_per_contract_rule() {
        val recipe = singleDay(
            listOf(
                slotOf("one", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(1), LiftSlot.BENCH),
                slotOf("nine", SlotRole.T3_ACCESSORY, CatalogIds.LAT, sets(9, 10)),
            ),
        )
        val summary = RecipeContractPolicy.summarize(RecipeContractPolicy.evaluate(recipe, metadata))
        assertEquals(RecipeContractPolicy.RULES, summary.keys.toList())
        assertEquals(2, summary.getValue(RecipeContractPolicy.C1_SET_RANGE))
        assertEquals(0, summary.getValue(RecipeContractPolicy.C2_DUP_CONFIG))
    }

    @Test
    fun the_contract_is_wired_as_soft_findings_that_exemptions_silence_but_never_reach_hard() {
        // C1 emite por slot (w{n}/{día}/{slot}): la exención de todos los slots del día acaba en barra y asterisco.
        val exemption = RecipeCompositionExemption(
            rule = RecipeContractPolicy.C1_SET_RANGE,
            scope = "w1/Dia/*",
            justification = "Una sola serie de potencia por diseño del autor del método",
        )
        val slots = listOf(
            slotOf("one", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(1), LiftSlot.BENCH),
            slotOf("nine", SlotRole.T3_ACCESSORY, CatalogIds.LAT, sets(9, 10)),
        )
        val plain = singleDay(slots)
        val exempt = plain.copy(exemptions = listOf(exemption))

        // En bruto el contrato está y es HARD; solo la exención por slot lo silencia.
        assertTrue(SessionCompositionPolicy.evaluateRecipeRaw(plain, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertTrue(SessionCompositionPolicy.evaluateRecipe(plain, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertFalse(SessionCompositionPolicy.evaluateRecipe(exempt, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertTrue(SessionCompositionPolicy.evaluateRecipeRaw(exempt, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertTrue(ProgramRecipeValidator.hardFindings(plain, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
        assertFalse(ProgramRecipeValidator.hardFindings(exempt, metadata).any { it.rule == RecipeContractPolicy.C1_SET_RANGE })
    }

    @Test
    fun slot_scoped_findings_are_silenced_by_slot_globs_but_not_by_a_day_glob() {
        val plain = singleDay(
            listOf(
                slotOf("one", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, sets(1), LiftSlot.BENCH),
                slotOf("nine", SlotRole.T3_ACCESSORY, CatalogIds.LAT, sets(9, 10)),
            ),
        )
        fun c1With(scope: String): List<String> {
            val exemption = RecipeCompositionExemption(
                rule = RecipeContractPolicy.C1_SET_RANGE,
                scope = scope,
                justification = "Exención de prueba por slot para el contrato de receta válida",
            )
            return SessionCompositionPolicy.evaluateRecipe(plain.copy(exemptions = listOf(exemption)), metadata)
                .filter { it.rule == RecipeContractPolicy.C1_SET_RANGE }
                .map { it.scope }
        }
        assertEquals(listOf("w1/Dia/one", "w1/Dia/nine"), c1With("w9/Otro"))
        // Un glob de día, anclado, no casa los hallazgos por slot: hay que añadir barra y asterisco, o el slot concreto.
        assertEquals(listOf("w1/Dia/one", "w1/Dia/nine"), c1With("w1/Dia"))
        assertEquals(listOf("w1/Dia/one", "w1/Dia/nine"), c1With("w*/Dia"))
        assertEquals(emptyList<String>(), c1With("w1/Dia/*"))
        assertEquals(emptyList<String>(), c1With("w*/Dia/*"))
        assertEquals(emptyList<String>(), c1With("*"))
        assertEquals(listOf("w1/Dia/nine"), c1With("w1/Dia/one"))
        assertEquals(listOf("w1/Dia/one"), c1With("w*/Dia/nine"))
    }
}
