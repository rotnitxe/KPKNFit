package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EffectiveWeekRecipe
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseLoadReference
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.ManualSessionOverride
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanRecipeJsonCompatTest {
    private val codec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun legacy_protocol_json_decodes_with_null_recipe() {
        val payload = """
            {"id":"gzcl-base","name":"GZCL","emoji":"x","description":"d","author":"Cody Lefever",
             "blocks":[{"name":"A","weeks":4,"goal":"Acumulación"}]}
        """.trimIndent()
        val protocol = codec.decodeFromString(Protocol.serializer(), payload)
        assertNull(protocol.recipe)
        assertEquals(ProtocolPublicationStatus.HIDDEN_UNVERIFIED, protocol.publicationStatus)
        assertTrue(protocol.exemptions.isEmpty())
    }

    @Test
    fun legacy_program_json_decodes_without_source_recipe() {
        val program = codec.decodeFromString(Program.serializer(), """{"id":"p","name":"Base"}""")
        assertNull(program.sourceRecipe)
        assertEquals(com.example.kpkn.data.models.AutoregulationMode.OFF, program.autoregulationMode)
        assertNull(program.powerliftingProfile)
    }

    @Test
    fun pending_action_legacy_and_new_enum_decode() {
        val legacy = codec.decodeFromString(
            PendingProgramAction.serializer(),
            """{"type":"CONFIRM_DELOAD","message":"descarga"}""",
        )
        assertEquals(PendingProgramActionType.CONFIRM_DELOAD, legacy.type)
        assertTrue(legacy.proposals.isEmpty())
        val next = codec.decodeFromString(
            PendingProgramAction.serializer(),
            """{"type":"CONFIRM_AUTOREGULATION","message":"AUGE","proposals":[{"kind":"ADJUST_TM","explanation":"AMRAP corto"}]}""",
        )
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, next.type)
        assertEquals(1, next.proposals.size)
    }

    // ---------------------------------------------------------------------
    // AC-B1: JSON crudo legado (payloads previos a los contratos T-002a)
    // ---------------------------------------------------------------------

    private val legacyRecipePayload = """
        {"id":"phul-verified","trainingMaxPercent":0.9,
         "progression":{"type":"cycle_increment","upperKg":5.0,"lowerKg":2.5},
         "claimedDaysPerWeek":4,"claimedLevel":"INTERMEDIATE","repeats":true,
         "weeks":[{"weekNumber":1,"blockIndex":0,"blockName":"Semana 1",
           "days":[{"label":"Superior fuerza","weekday":1,
             "slots":[{"id":"bench","role":"T1_MAIN",
               "lift":{"configurationId":"bench_press__barbell","liftSlot":"BENCH"},
               "sets":[{"reps":5,"percent":82.0,"rpe":8.0,"loadBasis":"PERCENT_TM"}],
               "restSeconds":180,"source":"AUTHOR","isCompetitionLift":true}]}]}]}
    """.trimIndent()

    @Test
    fun legacy_recipe_json_decodes_with_defaulted_new_contracts() {
        val recipe = codec.decodeFromString(TrainingPlanRecipe.serializer(), legacyRecipePayload)

        // Campos nuevos de TrainingPlanRecipe en sus defaults.
        assertEquals(1, recipe.contentVersion)
        assertNull(recipe.provenance)
        assertNull(recipe.nativeProgression)
        assertEquals(RecipeCompositionProfile.LEGACY_STANDARD, recipe.compositionProfile)

        // Semántica existente intacta.
        assertEquals(4, recipe.claimedDaysPerWeek)
        assertTrue(recipe.repeats)
        assertTrue(recipe.progression is ProgressionRule.CycleIncrement)
        val cycle = recipe.progression as ProgressionRule.CycleIncrement
        assertEquals(5.0, cycle.upperKg, 0.0)
        assertEquals(2.5, cycle.lowerKg, 0.0)

        val day = recipe.weeks.single().days.single()
        assertNull(day.id)
        assertTrue(day.cardioBlocks.isEmpty())
        assertEquals(RecipeSessionKind.STRENGTH, day.sessionKind)
        assertNull(day.minimumDose)

        val slot = day.slots.single()
        assertNull(slot.intent)
        assertNull(slot.authoredSetRange)
        assertNull(slot.explicitReference)
        assertEquals("bench_press__barbell", slot.lift.configurationId)
        assertEquals(LiftSlot.BENCH, slot.lift.liftSlot)
        assertEquals(180, slot.restSeconds)
        assertTrue(slot.isCompetitionLift)

        val set = slot.sets.single()
        assertNull(set.reference)
        assertEquals(82.0, set.percent!!, 0.0)
        assertEquals(LoadBasis.PERCENT_TM, set.loadBasis)
        assertFalse(set.isWarmup)
    }

    @Test
    fun legacy_program_json_keeps_reference1rm_and_prescription_origin_semantics() {
        val payload = """
            {"id":"p-legacy","name":"Base","sourceProtocolId":"gzcl",
             "macrocycles":[{"id":"m1","name":"Macro","blocks":[
               {"id":"b1","name":"Bloque","prescriptionOrigin":"KPKN_NATIVE_CURATED",
                "sourceDefinitionId":"full-body","sourceRevision":"native-cycle-1",
                "mesocycles":[{"id":"me1","name":"Meso","weeks":[
                  {"id":"w1","name":"Semana 1","sessions":[
                    {"id":"s1","name":"Full","exercises":[
                      {"id":"ex1","name":"Sentadilla","trainingMode":"RM","reference1RM":220.0,
                       "sets":[{"id":"st1","targetReps":3,"targetPercentageRM":85.0,
                         "loadBasis":"PERCENT_1RM","weight":180.0}]}]}]}]}]}]}]}
        """.trimIndent()

        val program = codec.decodeFromString(Program.serializer(), payload)

        // prescriptionOrigin: identidad por igualdad, sin JSON/procedencia codificados en el string.
        val block = program.macrocycles.single().blocks.single()
        assertEquals("KPKN_NATIVE_CURATED", block.prescriptionOrigin)
        assertEquals("full-body", block.sourceDefinitionId)

        // reference1RM sigue significando 1RM y convive con los campos nuevos en default.
        val exercise = program.macrocycles.single().blocks.single().mesocycles.single()
            .weeks.single().sessions.single().exercises.single()
        assertEquals(220.0, exercise.reference1RM!!, 0.0)
        assertEquals(LoadQuantityConvention.UNSPECIFIED, exercise.loadQuantityConvention)
        assertNull(exercise.loadReference)
        assertNull(exercise.recipeDayId)
        assertNull(exercise.recipeSlotId)
        val set = exercise.sets.single()
        assertEquals(LoadBasis.PERCENT_1RM, set.loadBasis)
        assertEquals(LoadQuantityConvention.UNSPECIFIED, set.loadQuantityConvention)

        // Campos nuevos de Program en sus defaults.
        assertNull(program.planProvenance)
        assertTrue(program.exerciseLoadReferences.isEmpty())
        assertTrue(program.effectiveWeekRecipes.isEmpty())
        assertTrue(program.manualSessionOverrides.isEmpty())

        // Roundtrip: los valores existentes no cambian.
        val roundtrip = codec.decodeFromString(Program.serializer(), codec.encodeToString(Program.serializer(), program))
        assertEquals("KPKN_NATIVE_CURATED", roundtrip.macrocycles.single().blocks.single().prescriptionOrigin)
        assertEquals(
            220.0,
            roundtrip.macrocycles.single().blocks.single().mesocycles.single()
                .weeks.single().sessions.single().exercises.single().reference1RM!!,
            0.0,
        )
    }

    @Test
    fun legacy_session_json_decodes_with_defaulted_rebuild_identity_fields() {
        val payload = """
            {"id":"session-1","name":"Sesion legada","dayOfWeek":2,
             "exercises":[
               {"id":"exercise-1","name":"Back Squat","trainingMode":"RM","reference1RM":220.0,
                "sets":[{"id":"set-1","targetReps":5,"weight":180.0,"loadBasis":"PERCENT_1RM"}]}]}
        """.trimIndent()

        val session = codec.decodeFromString(Session.serializer(), payload)

        assertEquals("session-1", session.id)
        val exercise = session.exercises.single()
        assertEquals(220.0, exercise.reference1RM!!, 0.0)
        assertEquals(LoadQuantityConvention.UNSPECIFIED, exercise.loadQuantityConvention)
        assertNull(exercise.loadReference)
        assertNull(exercise.recipeDayId)
        assertNull(exercise.recipeSlotId)
        assertEquals(LoadQuantityConvention.UNSPECIFIED, exercise.sets.single().loadQuantityConvention)
    }

    @Test
    fun observed_working_set_reference_never_leaks_into_reference1rm() {
        // §14.2: 100 kg de trabajo 3–5 reps NO es un 1RM; no se guarda en reference1RM.
        val payload = """
            {"id":"ex-obs","name":"Bench",
             "loadReference":{"kind":"OBSERVED_WORKING_SET",
               "configurationId":"bench_press__barbell","quantityConvention":"TOTAL_EXTERNAL",
               "sourceSlotId":"day1-bench","repMin":3,"repMax":5,
               "sourceProgramId":"p1","sourceRunId":"run1","sourceWeekOccurrence":1,
               "state":"CAPTURED","capturedLoadKg":100.0,"capturedAtMs":123456}}
        """.trimIndent()

        val exercise = codec.decodeFromString(Exercise.serializer(), payload)

        assertNull(exercise.reference1RM)
        val reference = exercise.loadReference
        assertNotNull(reference)
        assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, reference!!.kind)
        assertEquals("bench_press__barbell", reference.configurationId)
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, reference.quantityConvention)
        assertEquals("day1-bench", reference.sourceSlotId)
        assertEquals(3, reference.repMin)
        assertEquals(5, reference.repMax)
        assertEquals("p1", reference.sourceProgramId)
        assertEquals("run1", reference.sourceRunId)
        assertEquals(1, reference.sourceWeekOccurrence)
        assertEquals(PlanLoadReferenceState.CAPTURED, reference.state)
        assertEquals(100.0, reference.capturedLoadKg!!, 0.0)
        assertEquals(123456L, reference.capturedAtMs)
    }

    // ---------------------------------------------------------------------
    // AC-B2: roundtrip de los campos nuevos, con y sin valores presentes
    // ---------------------------------------------------------------------

    private fun fullRecipe(): TrainingPlanRecipe {
        val reference = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = "bench_press__barbell",
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            sourceSlotId = "day1-bench",
            repMin = 3,
            repMax = 5,
            sourceProgramId = "p1",
            sourceRunId = "run1",
            sourceWeekOccurrence = 1,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 100.0,
            capturedAtMs = 99L,
        )
        val day = DayRecipe(
            label = "Día A",
            slots = listOf(
                SlotRecipe(
                    id = "s-bench",
                    role = SlotRole.T1_MAIN,
                    lift = LiftRef("bench_press__barbell", LiftSlot.BENCH),
                    sets = listOf(SetRecipe(reps = 3, percent = 65.0, loadBasis = LoadBasis.PERCENT_1RM, reference = reference)),
                    restSeconds = 180,
                    intent = SlotIntent.F,
                    authoredSetRange = AuthoredSetRange(3, 4),
                    explicitReference = SlotLoadReferenceMetadata(
                        configurationId = "bench_press__barbell",
                        quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                        repMin = 3,
                        repMax = 5,
                        note = "Enlaza día pesado de la misma ocurrencia",
                    ),
                ),
            ),
            weekday = 1,
            id = "day-a",
            cardioBlocks = listOf(
                RecipeCardioBlock(
                    id = "cardio-1",
                    details = CardioDetails(
                        type = CardioType.TREADMILL,
                        intensity = CardioIntensity.MEDIA,
                        targetDurationSeconds = 10 * 60,
                    ),
                    position = RecipeCardioPosition.AFTER_STRENGTH,
                    purpose = "Cardio continuo",
                    progression = RecipeCardioProgression(
                        offeredDurationsMinutes = listOf(10, 15, 20, 30),
                        stopAtMinutes = 15,
                        note = "Tras resistencia se detiene en 15",
                    ),
                ),
            ),
            sessionKind = RecipeSessionKind.STRENGTH_CARDIO,
            minimumDose = DayMinimumDose(minDistinctConfigurations = 2, minResistanceSets = 4, essentialSlotIds = listOf("s-bench")),
        )
        return TrainingPlanRecipe(
            id = "native:powerbuilding-foundation-v2",
            weeks = listOf(
                WeekRecipe(
                    weekNumber = 1,
                    blockIndex = 0,
                    blockName = "Bloque 1",
                    days = listOf(day),
                ),
            ),
            contentVersion = 2,
            provenance = PlanProvenance(
                planId = "adapted:phul-kpkn-r1",
                recipeId = "original:phul-ms-2021-r1",
                revision = 2,
                category = PlanProvenanceClass.ADAPTED,
                technicalOrigin = CatalogSource.NATIVE,
                sourceTitle = "PHUL Workout",
                sourceUrl = "https://www.muscleandstrength.com/workouts/phul-workout",
                sourceAuthor = "Brandon Campbell",
                sourceEdition = "M&S 2021-05-26",
                parentId = "original:phul-ms-2021-r1",
                parentRevision = 1,
                slotChanges = listOf(
                    PlanSlotChange(
                        slotId = "s-bench",
                        fromConfigurationId = "bench_press__barbell",
                        toConfigurationId = "bench_press__dumbbells",
                        reason = "Sin barra confirmada",
                        samePattern = true,
                        differences = "Misma intención de empuje; pierde RM/TM de barra",
                        prescriptionBefore = "3–4×3–5",
                        prescriptionAfter = "3–4×6–10",
                        loadReferenceKept = false,
                    ),
                ),
                operationalDefaults = listOf(
                    KpknOperationalDefault(KpknOperationalDefaultScope.REST, "180 s en compuestos pesados"),
                ),
            ),
            nativeProgression = NativeProgressionSpec(
                strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
                exposuresBeforeProposal = 2,
                note = "Configuración inicial KPKN",
            ),
            compositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
        )
    }

    @Test
    fun recipe_new_contract_fields_round_trip_with_values() {
        val original = fullRecipe()
        val encoded = codec.encodeToString(TrainingPlanRecipe.serializer(), original)
        val decoded = codec.decodeFromString(TrainingPlanRecipe.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals(2, decoded.contentVersion)
        assertNotNull(decoded.provenance)
        assertEquals(PlanProvenanceClass.ADAPTED, decoded.provenance!!.category)
        assertEquals(CatalogSource.NATIVE, decoded.provenance!!.technicalOrigin)
        assertEquals(1, decoded.provenance!!.slotChanges.size)
        assertEquals(false, decoded.provenance!!.slotChanges.single().loadReferenceKept)
        assertEquals(RecipeCompositionProfile.NATIVE_COMPACT, decoded.compositionProfile)
        assertNotNull(decoded.nativeProgression)
        assertEquals(RecipeSessionKind.STRENGTH_CARDIO, decoded.weeks.single().days.single().sessionKind)
        assertEquals(1, decoded.weeks.single().days.single().cardioBlocks.size)
        assertNotNull(decoded.weeks.single().days.single().minimumDose)
        assertEquals(SlotIntent.F, decoded.weeks.single().days.single().slots.single().intent)
        assertNotNull(decoded.weeks.single().days.single().slots.single().authoredSetRange)
        assertNotNull(decoded.weeks.single().days.single().slots.single().explicitReference)
        val reference = decoded.weeks.single().days.single().slots.single().sets.single().reference
        assertNotNull(reference)
        assertEquals(PlanLoadReferenceState.CAPTURED, reference!!.state)
        assertEquals(100.0, reference.capturedLoadKg!!, 0.0)
    }

    @Test
    fun recipe_without_new_fields_keeps_stable_defaults_and_null_vs_empty() {
        val legacy = codec.decodeFromString(TrainingPlanRecipe.serializer(), legacyRecipePayload)
        val reencoded = codec.decodeFromString(TrainingPlanRecipe.serializer(), codec.encodeToString(TrainingPlanRecipe.serializer(), legacy))

        // Defaults estables tras encode→decode con encodeDefaults=true.
        assertEquals(legacy, reencoded)
        assertEquals(1, reencoded.contentVersion)
        assertEquals(RecipeCompositionProfile.LEGACY_STANDARD, reencoded.compositionProfile)
        // null distinto de vacío donde el contrato lo exige.
        assertNull(reencoded.provenance)
        assertNull(reencoded.nativeProgression)
        assertNull(reencoded.weeks.single().days.single().minimumDose)
        assertTrue(reencoded.weeks.single().days.single().cardioBlocks.isEmpty())
        // Una instancia declarada NO es null tras el roundtrip.
        val withEmptyProvenance = legacy.copy(provenance = PlanProvenance())
        val decodedEmpty = codec.decodeFromString(TrainingPlanRecipe.serializer(), codec.encodeToString(TrainingPlanRecipe.serializer(), withEmptyProvenance))
        assertNotNull(decodedEmpty.provenance)
        assertEquals(PlanProvenanceClass.LEGACY, decodedEmpty.provenance!!.category)
    }

    @Test
    fun program_new_contract_fields_round_trip_with_and_without_values() {
        // Sin valores: defaults estables y null ≠ vacío.
        val bare = codec.decodeFromString(Program.serializer(), """{"id":"p","name":"Base"}""")
        assertNull(bare.planProvenance)
        assertTrue(bare.exerciseLoadReferences.isEmpty())
        assertTrue(bare.effectiveWeekRecipes.isEmpty())
        assertTrue(bare.manualSessionOverrides.isEmpty())
        assertEquals(bare, codec.decodeFromString(Program.serializer(), codec.encodeToString(Program.serializer(), bare)))

        // Con valores: roundtrip completo.
        val program = Program(
            id = "p-adapted",
            name = "Plan adaptado",
            planProvenance = PlanProvenance(
                planId = "adapted:phat-kpkn-r1",
                recipeId = "original:phat-biolayne-2016-r1",
                revision = 1,
                category = PlanProvenanceClass.ADAPTED,
                technicalOrigin = CatalogSource.PROTOCOL,
                sourceTitle = "PHAT",
                sourceEdition = "Biolayne 2016-05-30",
                parentId = "original:phat-biolayne-2016-r1",
                parentRevision = 1,
            ),
            exerciseLoadReferences = listOf(
                ExerciseLoadReference(
                    exerciseId = "ex1",
                    references = listOf(
                        PlanLoadReference(
                            kind = PlanLoadReferenceKind.EXERCISE_1RM,
                            configurationId = "high_bar_back_squat__barbell",
                            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                            state = PlanLoadReferenceState.CAPTURED,
                            capturedLoadKg = 140.0,
                            capturedAtMs = 42L,
                        ),
                        PlanLoadReference(
                            kind = PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
                            configurationId = "pull_up__pronated__medium",
                            quantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
                            state = PlanLoadReferenceState.PENDING,
                        ),
                    ),
                ),
            ),
            effectiveWeekRecipes = listOf(
                EffectiveWeekRecipe(
                    weekOccurrence = 1,
                    cycleNumber = 2,
                    version = 3,
                    weekRecipe = WeekRecipe(weekNumber = 1, blockIndex = 0, blockName = "Semana 1", days = emptyList()),
                    changes = listOf(
                        PlanSlotChange(slotId = "s-bench", reason = "Propuesta de volumen aceptada", samePattern = true),
                    ),
                    appliedProposals = listOf(
                        AppliedRecipeProposal(proposalId = "prop-1", kind = "ADJUST_TM", summary = "TM +5 %", acceptedAtMs = 7L),
                    ),
                ),
            ),
            manualSessionOverrides = listOf(
                ManualSessionOverride(
                    sessionId = "s1",
                    weekId = "w1",
                    weekOccurrence = 1,
                    cycleNumber = 1,
                    recipeDayId = "day-a",
                    scope = ManualOverrideScope.SESSION,
                    reason = "Sesión personalizada por el usuario",
                    createdAtMs = 9L,
                ),
            ),
        )
        val decoded = codec.decodeFromString(Program.serializer(), codec.encodeToString(Program.serializer(), program))
        assertEquals(program, decoded)
        assertEquals(PlanProvenanceClass.ADAPTED, decoded.planProvenance!!.category)
        assertEquals(3, decoded.effectiveWeekRecipes.single().version)
        assertEquals(ManualOverrideScope.SESSION, decoded.manualSessionOverrides.single().scope)
        assertEquals("day-a", decoded.manualSessionOverrides.single().recipeDayId)
        assertEquals(2, decoded.exerciseLoadReferences.single().references.size)
    }

    @Test
    fun session_exercise_and_set_new_contract_fields_round_trip() {
        val session = Session(
            id = "s1",
            name = "Día A",
            exercises = listOf(
                Exercise(
                    id = "ex1",
                    name = "Press con mancuernas",
                    loadQuantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
                    loadReference = PlanLoadReference(
                        kind = PlanLoadReferenceKind.EXERCISE_TM,
                        configurationId = "bench_press__dumbbells",
                        quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
                        state = PlanLoadReferenceState.PENDING,
                    ),
                    recipeDayId = "day-a",
                    recipeSlotId = "s-bench",
                    sets = listOf(
                        ExerciseSet(
                            id = "st1",
                            targetReps = 8,
                            loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                        ),
                    ),
                ),
            ),
        )

        val decoded = codec.decodeFromString(Session.serializer(), codec.encodeToString(Session.serializer(), session))
        assertEquals(session, decoded)

        val exercise = decoded.exercises.single()
        // Semántica preservada: reference1RM sigue null y convención/referencia son campos aparte.
        assertNull(exercise.reference1RM)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, exercise.loadQuantityConvention)
        assertEquals(PlanLoadReferenceState.PENDING, exercise.loadReference!!.state)
        assertEquals("day-a", exercise.recipeDayId)
        assertEquals("s-bench", exercise.recipeSlotId)
        assertEquals(LoadQuantityConvention.TOTAL_EXTERNAL, exercise.sets.single().loadQuantityConvention)
    }
}
