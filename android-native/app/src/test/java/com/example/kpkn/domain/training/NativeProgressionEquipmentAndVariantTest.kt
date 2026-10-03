package com.example.kpkn.domain.training

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.domain.training.NativeProgressionTestSupport.completedLog
import com.example.kpkn.domain.training.NativeProgressionTestSupport.managedOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.sessionOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.4 con recetas sintéticas MATERIALIZADAS por `PlanMaterializer` (no sesiones a mano):
 * la convención de carga sale del equipo del catálogo (F-03), el siguiente paso respeta el
 * material declarado (F-11) y la variante corporal aceptada sobrevive a re-materializar y
 * al ciclo siguiente (F-10).
 */
class NativeProgressionEquipmentAndVariantTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private const val GOBLET = "quads_sentadilla_copa__default"
        private const val DB_BENCH = "bench_press__dumbbells"
        private const val KB_CURL = "hammer_curl__kettlebell"
        private const val CABLE_LAT = "lat_pulldown__bilateral__cable"
        private const val BARBELL_BENCH = "bench_press__barbell"
        private const val EZ_CURL = "standing_biceps_curl__ez_bar"
        private const val KNEE_PUSH_UP = "knee_push_up__default"
        private const val PUSH_UP = "push_up__flat"

        /** Todos los pasos de 1,25 kg por lado hasta 25 kg: 60 → 62,5 y 22 → 20 son formables. */
        private val FULL_PLATES = listOf(
            PlateStock(25.0, 2),
            PlateStock(10.0, 2),
            PlateStock(5.0, 2),
            PlateStock(2.5, 2),
            PlateStock(1.25, 2),
        )
    }

    private data class SlotSpec(val slotId: String, val configurationId: String, val intent: SlotIntent = SlotIntent.H)

    private fun recipeOf(
        id: String,
        weeks: Int,
        slots: List<SlotSpec>,
        strategy: NativeProgressionStrategy? = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = (1..weeks).map { number ->
            WeekRecipe(
                weekNumber = number,
                blockIndex = 0,
                weekName = "Semana $number",
                days = listOf(
                    DayRecipe(
                        id = "day-1",
                        label = "Día 1",
                        slots = slots.map { spec ->
                            SlotRecipe(
                                id = spec.slotId,
                                role = SlotRole.T3_ACCESSORY,
                                lift = LiftRef(configurationId = spec.configurationId),
                                sets = List(2) { SetRecipe(repsMin = 8, repsMax = 12, rir = 2, loadBasis = LoadBasis.RPE) },
                                restSeconds = 90,
                                intent = spec.intent,
                            )
                        },
                    ),
                ),
            )
        },
        nativeProgression = strategy?.let { NativeProgressionSpec(strategy = it, exposuresBeforeProposal = 2) },
    )

    private fun materialized(recipe: TrainingPlanRecipe): Program = PlanMaterializer.materialize(
        program = Program(id = "syn-${recipe.id}", name = "Sintético ${recipe.id}"),
        recipe = recipe,
        metadata = CatalogCompositionTestSupport.metadata,
        strict = false,
    )

    private fun observe(
        program: Program,
        logs: List<WorkoutLog>,
        inventory: EquipmentInventory? = null,
        curated: Set<String> = emptySet(),
    ): Program = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
        program = program,
        logs = logs,
        inventory = inventory,
        curatedConfigurations = curated,
        nowMs = 500L,
    )

    /** Dos exposiciones completas en el tope, a la carga que indique [loadFor] por configuración. */
    private fun twoExposures(
        program: Program,
        loadFor: (String?) -> Double?,
        reps: Int? = null,
        prefix: String = "syn",
    ): List<WorkoutLog> = (1..2).map { week ->
        completedLog(
            program = program,
            sessionId = sessionOf(program, week).id,
            logId = "$prefix-$week",
            dayOffset = week,
            loadKg = { exercise -> loadFor(exercise.catalogConfigurationId) },
            repsOf = { set -> reps ?: (set.targetRepsRange?.max ?: set.targetReps ?: 0) },
        )
    }

    // ─── F-03: la convención sale del equipo del catálogo ────────────────────

    @Test
    fun equipmentDecidesTheConvention_notASubstringOfTheConfigurationId() {
        val program = materialized(
            recipeOf(
                "conv",
                weeks = 1,
                slots = listOf(
                    SlotSpec("s-goblet", GOBLET),
                    SlotSpec("s-db-bench", DB_BENCH),
                    SlotSpec("s-kb-curl", KB_CURL),
                    SlotSpec("s-cable", CABLE_LAT),
                    SlotSpec("s-barbell", BARBELL_BENCH),
                    SlotSpec("s-ez", EZ_CURL, SlotIntent.I),
                    SlotSpec("s-pushup", KNEE_PUSH_UP),
                ),
            ),
        )
        val byConfiguration: Map<String?, Exercise> =
            managedOf(program, sessionOf(program, 1).id).associateBy { it.catalogConfigurationId }

        assertEquals(
            "la sentadilla copa es de mancuerna aunque su id no lo diga",
            LoadQuantityConvention.PER_IMPLEMENT,
            byConfiguration.getValue(GOBLET).loadQuantityConvention,
        )
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, byConfiguration.getValue(DB_BENCH).loadQuantityConvention)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, byConfiguration.getValue(KB_CURL).loadQuantityConvention)
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, byConfiguration.getValue(CABLE_LAT).loadQuantityConvention)
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, byConfiguration.getValue(BARBELL_BENCH).loadQuantityConvention)
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, byConfiguration.getValue(EZ_CURL).loadQuantityConvention)
        assertEquals(
            "el peso corporal no tiene carga externa inequívoca",
            LoadQuantityConvention.UNSPECIFIED,
            byConfiguration.getValue(KNEE_PUSH_UP).loadQuantityConvention,
        )
    }

    @Test
    fun recipeWithoutNativeProgression_keepsEveryConventionUnspecified() {
        val program = materialized(
            recipeOf("legacy-conv", weeks = 1, slots = listOf(SlotSpec("s-goblet", GOBLET), SlotSpec("s-bench", BARBELL_BENCH)), strategy = null),
        )
        val exercises = weeksOf(program).flatMap { it.sessions }.flatMap { it.allExercises() }
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.none { it.nativeProgressionManaged })
        assertTrue(exercises.all { it.loadQuantityConvention == LoadQuantityConvention.UNSPECIFIED })
    }

    @Test
    fun conventionPolicyMapsEquipmentFamilies() {
        listOf("dumbbells", "kettlebell").forEach {
            assertEquals(it, LoadQuantityConvention.PER_IMPLEMENT, NativeLoadConventions.forEquipment(it))
        }
        listOf("barbell", "ez_bar", "hex_bar", "safety_bar", "t_bar", "machine", "cable", "smith_machine", "plate").forEach {
            assertEquals(it, LoadQuantityConvention.TOTAL_EXTERNAL, NativeLoadConventions.forEquipment(it))
        }
        listOf("bodyweight", "band", "trx", "", null).forEach {
            assertEquals("$it", LoadQuantityConvention.UNSPECIFIED, NativeLoadConventions.forEquipment(it))
        }
        // El catálogo manda sobre el id; el id solo es último recurso cuando el catálogo no conoce la configuración.
        assertEquals(NativeLoadConventions.StockKind.DUMBBELL, NativeLoadConventions.stockKindFor("dumbbells", GOBLET))
        assertEquals(NativeLoadConventions.StockKind.NONE, NativeLoadConventions.stockKindFor("bodyweight", "algo__dumbbells"))
        assertEquals(NativeLoadConventions.StockKind.DUMBBELL, NativeLoadConventions.stockKindFor(null, "bench_press__dumbbells"))
        assertEquals(NativeLoadConventions.StockKind.NONE, NativeLoadConventions.stockKindFor(null, GOBLET))
        // Solo la barra recta tiene tara declarada en el inventario: EZ/hex/safety/T/H no calculan pasos de disco.
        assertEquals(NativeLoadConventions.StockKind.BARBELL, NativeLoadConventions.stockKindFor("barbell", "algo__barbell"))
        listOf("ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar").forEach {
            assertEquals(it, NativeLoadConventions.StockKind.NONE, NativeLoadConventions.stockKindFor(it, "algo__barbell"))
        }
    }

    // ─── F-03 + F-11: el menor incremento conocido del equipo real ───────────

    @Test
    fun knownIncrement_usesTheCatalogEquipment_forDumbbellBarbellAndCable() {
        val program = materialized(
            recipeOf(
                "inc",
                weeks = 4,
                slots = listOf(SlotSpec("s-goblet", GOBLET), SlotSpec("s-bench", BARBELL_BENCH), SlotSpec("s-lat", CABLE_LAT)),
            ),
        )
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = FULL_PLATES,
            dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(14.0), DumbbellPairStock(16.0, pairAvailable = false)),
            machines = listOf(
                MachineLoadRange(
                    configurationId = CABLE_LAT,
                    minLoadKg = 5.0,
                    maxLoadKg = 100.0,
                    incrementKg = 5.0,
                    baseLoadKg = 0.0,
                ),
            ),
        )
        val logs = twoExposures(
            program,
            loadFor = { configurationId ->
                when (configurationId) {
                    GOBLET -> 12.0
                    BARBELL_BENCH -> 60.0
                    else -> 50.0
                }
            },
        )

        val proposals = observe(program, logs, inventory).nativeProgressionProposals
        val targets = proposals.associate { it.identity.configurationId to it.targetLoadKg }

        assertEquals(3, proposals.size)
        assertTrue(proposals.all { it.kind == NativeProgressionProposalKind.INCREASE_LOAD })
        assertEquals("el siguiente PAR de mancuernas (16 no tiene pareja)", 14.0, targets.getValue(GOBLET)!!, 0.0001)
        assertEquals("21,25 kg por lado es la menor carga formable sobre 60 kg", 62.5, targets.getValue(BARBELL_BENCH)!!, 0.0001)
        assertEquals("el siguiente paso de la máquina declarada", 55.0, targets.getValue(CABLE_LAT)!!, 0.0001)
        val conventions = proposals.associate { it.identity.configurationId to it.identity.quantityConvention }
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, conventions.getValue(GOBLET))
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, conventions.getValue(BARBELL_BENCH))
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, conventions.getValue(CABLE_LAT))
    }

    @Test
    fun proposalText_usesTheRealUnitOfEachEquipmentAndHidesInternalIds() {
        val program = materialized(
            recipeOf(
                "plain-text",
                weeks = 4,
                slots = listOf(
                    SlotSpec("s-goblet", GOBLET),
                    SlotSpec("s-bench", BARBELL_BENCH),
                    SlotSpec("s-cable", CABLE_LAT),
                ),
            ),
        )
        val inventory = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = FULL_PLATES,
            dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(14.0)),
            machines = listOf(
                MachineLoadRange(
                    configurationId = CABLE_LAT,
                    minLoadKg = 5.0,
                    maxLoadKg = 100.0,
                    incrementKg = 5.0,
                    baseLoadKg = 0.0,
                ),
            ),
        )
        val logs = twoExposures(
            program,
            loadFor = { configurationId ->
                when (configurationId) {
                    GOBLET -> 12.0
                    BARBELL_BENCH -> 60.0
                    else -> 50.0
                }
            },
        )

        val text = observe(program, logs, inventory).nativeProgressionProposals
            .associate { it.identity.configurationId to it.explanation }

        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 12 a 14 kg por mancuerna.",
            text.getValue(GOBLET),
        )
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 60 a 62,5 kg en total.",
            text.getValue(BARBELL_BENCH),
        )
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 50 a 55 kg.",
            text.getValue(CABLE_LAT),
        )
        assertTrue(text.values.none { it.contains("__") || it.contains("RIR") || it.contains("exposicion", ignoreCase = true) })
    }

    @Test
    fun noDeclaredStock_leavesTheNextLoadForTheAthlete_butDeclaredLimitsAreReported() {
        val program = materialized(recipeOf("limit", weeks = 4, slots = listOf(SlotSpec("s-goblet", GOBLET))))
        val logs = twoExposures(program, loadFor = { 16.0 })

        val unknown = observe(program, logs, inventory = null).nativeProgressionProposals.single()
        assertNull("sin stock declarado la carga queda por elegir", unknown.targetLoadKg)
        assertTrue(unknown.explanation.contains("elige una carga"))

        // Con stock declarado pero sin un par más pesado se informa el tope, no «sube ligeramente».
        val limited = observe(
            program,
            logs,
            EquipmentInventory(dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(16.0))),
        )
        assertTrue(limited.nativeProgressionProposals.isEmpty())
        val notice = limited.nativeProgressionAudit.single()
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, notice.status)
        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, notice.kind)
        assertTrue(notice.userFacingNotice)
        assertTrue(notice.reason, notice.reason.contains("no tiene un par de mancuernas más pesado"))
    }

    @Test
    fun barbellIncrement_requiresAchievablePlateCombination() {
        val program = materialized(recipeOf("plates", weeks = 4, slots = listOf(SlotSpec("s-bench", BARBELL_BENCH))))
        val logs = twoExposures(program, loadFor = { 60.0 })

        // Con solo discos de 1,25 kg no se puede formar ninguna carga mayor que 60 kg: se informa el tope.
        val tooLight = observe(
            program,
            logs,
            EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(1.25, 2))),
        )
        assertTrue(tooLight.nativeProgressionProposals.isEmpty())
        assertTrue(tooLight.nativeProgressionAudit.single().reason.contains("no se puede formar una carga de barra mayor"))

        val enough = observe(program, logs, EquipmentInventory(barbellWeightKg = 20.0, plates = FULL_PLATES))
        assertEquals(62.5, enough.nativeProgressionProposals.single().targetLoadKg!!, 0.0001)

        // La barra declarada manda: con una barra de 15 kg, 60 kg son 22,5 por lado y el menor paso sigue siendo 1,25 por lado.
        val lighterBar = observe(program, logs, EquipmentInventory(barbellWeightKg = 15.0, plates = FULL_PLATES))
        assertEquals(62.5, lighterBar.nativeProgressionProposals.single().targetLoadKg!!, 0.0001)
    }

    @Test
    fun barbellReduction_respectsTheEmptyBarFloor() {
        val program = materialized(recipeOf("floor", weeks = 4, slots = listOf(SlotSpec("s-bench", BARBELL_BENCH))))
        val inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = FULL_PLATES)

        // 22 kg con reps bajo el mínimo: reducir nunca baja de la barra vacía (20 kg).
        val belowMinimumAt22 = twoExposures(program, loadFor = { 22.0 }, reps = 5, prefix = "floor22")
        val reduced = observe(program, belowMinimumAt22, inventory).nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, reduced.kind)
        assertEquals(20.0, reduced.targetLoadKg!!, 0.0001)

        // Ya en la barra vacía no hay carga menor: aviso, no una propuesta imposible.
        val belowMinimumAtBar = twoExposures(program, loadFor = { 20.0 }, reps = 5, prefix = "floor20")
        val atFloor = observe(program, belowMinimumAtBar, inventory)
        assertTrue(atFloor.nativeProgressionProposals.isEmpty())
        val notice = atFloor.nativeProgressionAudit.single()
        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, notice.kind)
        assertTrue(notice.reason, notice.reason.contains("barra vacía"))
    }

    // ─── F-10: la variante corporal sobrevive a re-materializar y al ciclo 2 ──

    private fun pushUpInfo() = ExerciseMuscleInfo(
        id = "push_up",
        name = "Flexión",
        equipment = "peso corporal",
        catalogDefinitionId = "push_up",
        catalogConfigurationId = PUSH_UP,
        catalogRevision = "catalog-test",
        performanceProfileId = "profile-test",
    )

    /** Programa de tres semanas con una flexión de rodillas; dos exposiciones y variante aceptada. */
    private fun acceptedHarderVariant(): Triple<Program, List<WorkoutLog>, TrainingPlanRecipe> {
        val recipe = recipeOf(
            "variant",
            weeks = 3,
            slots = listOf(SlotSpec("s-push", KNEE_PUSH_UP)),
            strategy = NativeProgressionStrategy.BODYWEIGHT_VARIANT_ESCALATION,
        )
        val program = materialized(recipe)
        val logs = twoExposures(program, loadFor = { null }, prefix = "variant")
        val proposed = observe(program, logs, curated = setOf(PUSH_UP))
        val proposal = proposed.nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT, proposal.kind)
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = proposed,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = mapOf(PUSH_UP to pushUpInfo()),
            nowMs = 600L,
        )
        return Triple(accepted, logs, recipe)
    }

    @Test
    fun bodyweightVariantReplacement_survivesRematerializeWeek() {
        val (accepted, _, recipe) = acceptedHarderVariant()
        val weeks = weeksOf(accepted)
        fun configurationOf(week: Int, program: Program = accepted) =
            managedOf(program, sessionOf(program, week).id).single().catalogConfigurationId

        assertEquals("semanas entrenadas intactas", KNEE_PUSH_UP, configurationOf(1))
        assertEquals(KNEE_PUSH_UP, configurationOf(2))
        assertEquals("la sesión futura recibe la variante", PUSH_UP, configurationOf(3))

        val effective = accepted.effectiveWeekRecipes.single()
        assertEquals(3, effective.weekOccurrence)
        assertEquals(1, effective.cycleNumber)
        assertEquals(PUSH_UP, effective.weekRecipe!!.days.single().slots.single().lift.configurationId)
        val change = effective.changes.single()
        assertEquals("s-push", change.slotId)
        assertEquals(KNEE_PUSH_UP, change.fromConfigurationId)
        assertEquals(PUSH_UP, change.toConfigurationId)
        assertEquals(false, change.loadReferenceKept)
        assertEquals(NativeWorkoutProgressionRuntime.NATIVE_VARIANT_APPLIED_KIND, effective.appliedProposals.single().kind)
        assertEquals("la receta global no se muta", KNEE_PUSH_UP, recipe.weeks[2].days.single().slots.single().lift.configurationId)
        assertEquals(KNEE_PUSH_UP, accepted.sourceRecipe!!.weeks[2].days.single().slots.single().lift.configurationId)

        // Re-materializar la semana 3 (autorregulación AUGE o «Re-materializar») ya no pierde la sustitución.
        val rebuilt = PlanMaterializer.rematerializeWeek(
            program = accepted,
            weekId = weeks[2].id,
            recipe = accepted.sourceRecipe!!,
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertEquals(PUSH_UP, configurationOf(3, rebuilt))
        assertEquals("el id del ejercicio se conserva al reconstruir", managedOf(accepted, sessionOf(accepted, 3).id).single().id, managedOf(rebuilt, sessionOf(rebuilt, 3).id).single().id)
        assertEquals(KNEE_PUSH_UP, configurationOf(2, rebuilt))
    }

    @Test
    fun bodyweightVariant_carriesToCycleTwoSessionsAndRecipes_idempotently() {
        val (accepted, logs, _) = acceptedHarderVariant()
        val idsBefore = weeksOf(accepted).flatMap { it.sessions }.flatMap { it.allExercises() }.map { it.id }

        val carried = NativeWorkoutProgressionRuntime.carryForwardToNextCycle(accepted, logs, closedCycle = 1, nowMs = 700L)
        val again = NativeWorkoutProgressionRuntime.carryForwardToNextCycle(carried, logs, closedCycle = 1, nowMs = 800L)

        assertEquals("repetir el arrastre no cambia nada", carried, again)
        (1..3).forEach { week ->
            val exercise = managedOf(carried, sessionOf(carried, week).id).single()
            assertEquals("semana $week", PUSH_UP, exercise.catalogConfigurationId)
            assertNull("una variante nueva no hereda referencias", exercise.loadReference)
        }
        assertEquals(idsBefore, weeksOf(carried).flatMap { it.sessions }.flatMap { it.allExercises() }.map { it.id })
        val cycleTwoRecipes = carried.effectiveWeekRecipes.filter { it.cycleNumber == 2 }
        assertEquals(listOf(1, 2, 3), cycleTwoRecipes.map { it.weekOccurrence }.sorted())
        assertTrue(cycleTwoRecipes.all { it.weekRecipe!!.days.single().slots.single().lift.configurationId == PUSH_UP })
    }
}
