package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures.Gear
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ConfigurationAvailability
import com.example.kpkn.domain.training.PlanAdaptationRequest
import com.example.kpkn.domain.training.PlanAdaptationResolver
import com.example.kpkn.domain.training.PlanAdaptationResult
import com.example.kpkn.domain.training.ProgramRecipeValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

/**
 * Q5-F2 / §10.1, §13.4, §15.2 — preparación de los CUATRO planes de autor
 * (`original:phul-ms-2021-r1`, `original:phat-biolayne-2016-r1`,
 * `adapted:phul-kpkn-r1`, `adapted:phat-kpkn-r1`) con el catálogo real y el
 * equipo efectivo REAL, por la misma función que usa el wizard
 * ([AuthoredPlanMaterializer.prepare]).
 *
 * Contratos que fija:
 *  - el original se materializa tal cual (tablas 18/16/21/18 y 21/17/24/28/28) y
 *    NUNCA se modifica; sin material se rechaza con APPARATUS_ABSENT/UNKNOWN;
 *  - la adaptación se apoya en [PlanAdaptationResolver.adapt]: sin cambios
 *    conserva la tabla, con cambios registra cada `slotChange` UNA vez y limpia
 *    las referencias de carga del slot sustituido; sin sustituto devuelve un
 *    motivo tipado y no publica nada;
 *  - la procedencia (ORIGINAL/ADAPTED, edición, slotChanges) viaja en el
 *    `Program` serializado.
 */
class AuthoredPlanMaterializerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private const val PHUL_ORIGINAL = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
        private const val PHAT_ORIGINAL = AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID
        private const val PHUL_ADAPTED = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID
        private const val PHAT_ADAPTED = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID

        private val PHUL_SET_COUNTS = listOf(18, 16, 21, 18)
        private val PHAT_SET_COUNTS = listOf(21, 17, 24, 28, 28)
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private fun prepare(planId: String, gear: Gear): Program = AuthoredPlanFixtures.prepare(planId, gear)

    private fun rejection(planId: String, gear: Gear): PlanMaterializationException {
        try {
            val program = prepare(planId, gear)
            fail("$planId con ${gear.label} no debe publicar programa; produjo ${program.sourceRecipe?.id}")
        } catch (typed: PlanMaterializationException) {
            return typed
        }
        error("inalcanzable")
    }

    private fun recipeJson(recipe: TrainingPlanRecipe): String =
        Json.encodeToString(TrainingPlanRecipe.serializer(), recipe)

    private fun tokensOf(failure: PlanMaterializationException): List<String> =
        failure.message.orEmpty().substringAfter(":", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Hallazgos H9 (descanso mínimo por rol, HARD) de una receta, con la MISMA
     * llamada que usa `requireComposition`: un H9 en una receta adaptada es un
     * defecto del adaptador, no «material insuficiente».
     */
    private fun h9Findings(recipe: TrainingPlanRecipe) =
        ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata).filter { it.rule == "H9" }

    // ─── Originales con gimnasio completo ────────────────────────────────────

    @Test
    fun fullGymPreparesPhulOriginalExactlyAsTheTableWithOriginalProvenance() {
        val program = prepare(PHUL_ORIGINAL, AuthoredPlanFixtures.fullGym)

        assertEquals(PHUL_ORIGINAL, program.sourceRecipe?.id)
        assertEquals(
            "el original conserva la receta del catálogo sin una sola sustitución",
            AuthoredPhulPhatRecipes.phulOriginal,
            program.sourceRecipe,
        )
        assertEquals("id estable del plan publicado, como los nativos", PHUL_ORIGINAL, program.structureTemplateId)
        assertEquals(12, AuthoredPlanFixtures.weeksOf(program).size)
        assertEquals(setOf(1, 2, 4, 5), AuthoredPlanFixtures.firstWeekDays(program))
        assertEquals("oráculos de series 18/16/21/18", PHUL_SET_COUNTS, AuthoredPlanFixtures.firstWeekSetCounts(program))
        assertEquals(setOf(1, 2, 4, 5), program.schedulePlan?.trainingDays)
        assertEquals(1, program.schedulePlan?.weekStartDay)

        val provenance = requireNotNull(program.planProvenance) { "el programa debe llevar su procedencia" }
        assertEquals(program.sourceRecipe?.provenance, provenance)
        assertEquals(PlanProvenanceClass.ORIGINAL, provenance.category)
        assertEquals(PHUL_ORIGINAL, provenance.planId)
        assertEquals("Brandon Campbell", provenance.sourceAuthor)
        assertTrue(provenance.sourceEdition.orEmpty().contains("2021-05-26"))
        assertTrue(provenance.sourceEdition.orEmpty().contains("2026-09-28"))
        assertTrue("un original no registra sustituciones", provenance.slotChanges.isEmpty())

        AuthoredPlanFixtures.exercisesOf(program).forEach { exercise ->
            assertEquals("PHUL no materializa modo RM: ${exercise.name}", TrainingMode.REPS, exercise.trainingMode)
            assertNull("sin 1RM heredado: ${exercise.name}", exercise.reference1RM)
            exercise.sets.forEach { set ->
                assertNull("PHUL no lleva porcentajes", set.targetPercentageRM)
                assertNull("carga pendiente, nunca 0 kg", set.weight)
            }
        }
    }

    @Test
    fun fullGymPreparesPhatOriginalWithSpeedBlocksAndDeferredHonestLoads() {
        val program = prepare(PHAT_ORIGINAL, AuthoredPlanFixtures.fullGym)

        assertEquals(PHAT_ORIGINAL, program.sourceRecipe?.id)
        assertEquals(AuthoredPhulPhatRecipes.phatOriginal, program.sourceRecipe)
        assertEquals("ventana de 6 semanas sin semana 7 fabricada", 6, AuthoredPlanFixtures.weeksOf(program).size)
        assertEquals(setOf(1, 2, 4, 5, 6), AuthoredPlanFixtures.firstWeekDays(program))
        assertEquals("oráculos de series 21/17/24/28/28", PHAT_SET_COUNTS, AuthoredPlanFixtures.firstWeekSetCounts(program))

        val provenance = requireNotNull(program.planProvenance)
        assertEquals(PlanProvenanceClass.ORIGINAL, provenance.category)
        assertEquals("Layne Norton", provenance.sourceAuthor)
        assertTrue(provenance.sourceEdition.orEmpty().contains("2016-05-30"))
        assertTrue(provenance.slotChanges.isEmpty())

        val speed = AuthoredPlanFixtures.weeksOf(program).first().sessions
            .flatMap { it.allExercises() }.filter { it.slotRole == SlotRole.SPEED }
        assertEquals("tres bloques SPEED en los días de hipertrofia", 3, speed.size)
        speed.forEach { exercise ->
            assertEquals(6, exercise.sets.size)
            assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, exercise.loadReference?.kind)
            assertNull("jamás un TM/1RM estimado para la velocidad", exercise.reference1RM)
            exercise.sets.forEach { set ->
                assertEquals(3, set.targetReps)
                assertEquals(65.0, set.targetPercentageRM ?: Double.NaN, 0.0001)
                assertNull("sin registro del día pesado la carga queda pendiente", set.weight)
            }
        }
    }

    // ─── Adaptaciones con gimnasio completo ──────────────────────────────────

    @Test
    fun fullGymKeepsBothAdaptedTablesAndRecordsNoSilentChange() {
        listOf(
            Triple(PHUL_ADAPTED, PHUL_SET_COUNTS, PHUL_ORIGINAL),
            Triple(PHAT_ADAPTED, PHAT_SET_COUNTS, PHAT_ORIGINAL),
        ).forEach { (planId, expectedSets, originalId) ->
            val program = prepare(planId, AuthoredPlanFixtures.fullGym)
            assertEquals("sin cambios la adaptación conserva su receta del catálogo", planId, program.sourceRecipe?.id)
            assertEquals(expectedSets, AuthoredPlanFixtures.firstWeekSetCounts(program))
            val provenance = requireNotNull(program.planProvenance)
            assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
            assertEquals(originalId, provenance.parentId)
            assertTrue("$planId: sin sustituciones no hay slotChanges", provenance.slotChanges.isEmpty())
            assertEquals(planId, program.structureTemplateId)
        }
    }

    @Test
    fun missingRackSubstitutesTheBarbellBenchAndSquatSlotsAndLeavesTheOriginalUntouched() {
        val originalBefore = recipeJson(AuthoredPhulPhatRecipes.phulOriginal)
        val adaptedBefore = recipeJson(AuthoredPhulPhatRecipes.phulAdapted)

        val program = prepare(PHUL_ADAPTED, AuthoredPlanFixtures.gymWithoutRack)

        val recipe = requireNotNull(program.sourceRecipe)
        assertTrue("receta derivada del resolver: ${recipe.id}", recipe.id.startsWith("$PHUL_ADAPTED~adapted-"))
        assertEquals(PHUL_ADAPTED, program.structureTemplateId)
        val provenance = requireNotNull(program.planProvenance)
        assertEquals("el programa y su receta citan la MISMA procedencia", recipe.provenance, provenance)
        assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
        assertEquals("la cadena sigue citando al original M&S", PHUL_ORIGINAL, provenance.parentId)
        assertTrue(provenance.sourceEdition.orEmpty().contains("2021-05-26"))
        // Paquete A · B4: el rack lo exigen la banca Y la sentadilla de barra, así que sin rack las dos bancas pasan a
        // mancuernas y las dos sentadillas (`sq`, `sq-front`) a la misma definición en Smith (el gimnasio completo la trae).
        assertEquals(
            "banca y banca inclinada de barra pasan a mancuernas y las sentadillas de barra a Smith; cada sustitución se registra UNA vez aunque la receta tenga 12 semanas",
            setOf(
                "bp" to (CatalogIds.BP to CatalogIds.BP_DB),
                "bp-inc" to (CatalogIds.BP_INC to CatalogIds.BP_INC_DB),
                "sq" to (CatalogIds.SQ_HIGH to "high_bar_back_squat__smith_machine"),
                "sq-front" to (CatalogIds.SQ_FRONT to "front_squat__smith_machine"),
            ),
            provenance.slotChanges.map { it.slotId to (it.fromConfigurationId to it.toConfigurationId) }.toSet(),
        )
        assertEquals(4, provenance.slotChanges.size)
        provenance.slotChanges.forEach { change ->
            assertTrue("cambio de la misma definición (mismo patrón): ${change.slotId}", change.samePattern)
            assertNull("PHUL no declara referencia de carga: nada que conservar ni descartar", change.loadReferenceKept)
            assertTrue(
                "la prescripción anterior y la nueva quedan registradas para auditar el cambio",
                change.prescriptionBefore != null && change.prescriptionAfter != null,
            )
        }

        val exercises = AuthoredPlanFixtures.exercisesOf(program)
        assertTrue(
            "no queda banca de barra en ninguna semana",
            exercises.none { it.catalogConfigurationId == CatalogIds.BP || it.catalogConfigurationId == CatalogIds.BP_INC },
        )
        assertTrue(
            "sin rack tampoco queda sentadilla de barra en ninguna semana (B4)",
            exercises.none { it.catalogConfigurationId == CatalogIds.SQ_HIGH || it.catalogConfigurationId == CatalogIds.SQ_FRONT },
        )
        assertEquals(
            "la sentadilla del autor pasa a la misma definición en Smith en todas las semanas",
            setOf("high_bar_back_squat__smith_machine"),
            exercises.filter { it.recipeSlotId == "sq" }.mapNotNull { it.catalogConfigurationId }.toSet(),
        )
        val benchSlot = exercises.filter { it.recipeSlotId == "bp" }
        assertEquals("un ejercicio 'bp' por semana", 12, benchSlot.size)
        benchSlot.forEach { exercise ->
            assertEquals(CatalogIds.BP_DB, exercise.catalogConfigurationId)
            assertNull("la mancuerna no hereda 1RM/TM de barra", exercise.reference1RM)
            assertNull(exercise.loadReference)
            assertEquals("el descanso del autor se conserva en la misma definición", 180, exercise.restTime)
            exercise.sets.forEach { set ->
                assertNull("sin porcentaje heredado", set.targetPercentageRM)
                assertNull("carga pendiente, nunca 0 kg", set.weight)
                assertEquals("esfuerzo inicial del autor", 2, set.targetRIR)
            }
        }
        assertEquals("sustituir no cambia las series del autor", PHUL_SET_COUNTS, AuthoredPlanFixtures.firstWeekSetCounts(program))

        // La receta del catálogo no se muta jamás.
        assertEquals(originalBefore, recipeJson(AuthoredPhulPhatRecipes.phulOriginal))
        assertEquals(adaptedBefore, recipeJson(AuthoredPhulPhatRecipes.phulAdapted))
    }

    @Test
    fun adaptedProgramSurvivesTheRoomJsonRoundTripWithItsProvenance() {
        val program = prepare(PHUL_ADAPTED, AuthoredPlanFixtures.gymWithoutRack)

        val reopened = program.toEntity().toProgram()

        assertEquals("lo que se activa es exactamente lo que se previsualizó", program, reopened)
        val provenance = requireNotNull(reopened.planProvenance)
        assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
        assertEquals("banca, inclinada y las dos sentadillas de barra (B4)", 4, provenance.slotChanges.size)
        assertTrue(provenance.sourceEdition.orEmpty().contains("2021-05-26"))
        assertEquals(provenance, reopened.sourceRecipe?.provenance)
    }

    // ─── Material insuficiente: motivo tipado y nada publicado ───────────────

    @Test
    fun originalsNeverSubstituteMissingMaterialAndNameWhatIsMissing() {
        listOf(PHUL_ORIGINAL, PHAT_ORIGINAL).forEach { planId ->
            val failure = rejection(planId, AuthoredPlanFixtures.dumbbellsAndBench)
            assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
            assertEquals(PlanRejectionReason.APPARATUS_ABSENT, failure.reason)
            assertTrue(failure.message.orEmpty().startsWith(AuthoredPlanMaterializer.MATERIAL_PREFIX))
            val tokens = tokensOf(failure)
            assertTrue("$planId exige barra: $tokens", "barbell" in tokens)
            assertTrue("$planId exige máquinas: $tokens", "machine" in tokens)
            assertTrue("diagnóstico de configuraciones afectadas", failure.affectedSlots.isNotEmpty())
        }
    }

    @Test
    fun unknownMachinesAskToConfirmInsteadOfBeingDenied() {
        val gear = AuthoredPlanFixtures.gymWithUnknownMachines
        listOf(PHUL_ORIGINAL, PHAT_ORIGINAL).forEach { planId ->
            val failure = rejection(planId, gear)
            assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
            assertEquals("$planId: solo faltan máquinas sin confirmar", PlanRejectionReason.APPARATUS_UNKNOWN, failure.reason)
            assertEquals(listOf("machine"), tokensOf(failure))
        }
        listOf(PHUL_ADAPTED, PHAT_ADAPTED).forEach { planId ->
            val failure = rejection(planId, gear)
            assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
            assertEquals("$planId: adaptar no inventa una máquina", PlanRejectionReason.APPARATUS_UNKNOWN, failure.reason)
            assertEquals(listOf("machine"), tokensOf(failure))
        }
    }

    @Test
    fun bodyweightOnlyRejectsEveryAuthoredPlanWithATypedReason() {
        val gear = AuthoredPlanFixtures.bodyweightOnly
        listOf(PHUL_ORIGINAL, PHAT_ORIGINAL).forEach { planId ->
            val failure = rejection(planId, gear)
            assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
            assertEquals(PlanRejectionReason.APPARATUS_ABSENT, failure.reason)
        }
        // PHAT adaptado: el remo pendlay (T1) no tiene ningún tirón alcanzable sin material.
        val phat = rejection(PHAT_ADAPTED, gear)
        assertEquals(PlanEvaluationStage.MATERIAL, phat.stage)
        assertEquals(PlanRejectionReason.APPARATUS_ABSENT, phat.reason)
        assertEquals(listOf("row-pendlay=${CatalogIds.PENDLAY}"), phat.affectedSlots)
        // PHUL adaptado: la banca cae a flexiones pero el press inclinado de mancuernas (T2) no tiene reserva.
        val phul = rejection(PHUL_ADAPTED, gear)
        assertEquals(PlanEvaluationStage.MATERIAL, phul.stage)
        assertEquals(PlanRejectionReason.NO_VALID_SUBSTITUTION, phul.reason)
        assertEquals(listOf("inc-db=${CatalogIds.BP_INC_DB}"), phul.affectedSlots)
    }

    @Test
    fun speedPowerHasNoSubstituteAndTheAdaptationIsNotViable() {
        val failure = rejection(PHAT_ADAPTED, AuthoredPlanFixtures.gymWithoutBarbell)
        assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
        assertEquals(PlanRejectionReason.NO_VALID_SUBSTITUTION, failure.reason)
        assertTrue(
            "la potencia ligada a un ejercicio de barra no se reescribe como series de RIR: ${failure.affectedSlots}",
            failure.affectedSlots.any { it.startsWith("speed-") },
        )
    }

    @Test
    fun dumbbellsAndBenchReachTheSameVerdictAsTheResolverAndNeverMutateTheCatalog() {
        val gear = AuthoredPlanFixtures.dumbbellsAndBench
        listOf(PHUL_ADAPTED, PHAT_ADAPTED).forEach { planId ->
            val entry = AuthoredPlanFixtures.entry(planId)
            val recipeBefore = recipeJson(requireNotNull(entry.recipe))
            val verdict = PlanAdaptationResolver.adapt(adaptationRequest(entry, gear))
            when (verdict) {
                is PlanAdaptationResult.NotViable -> {
                    val failure = rejection(planId, gear)
                    assertEquals("$planId: el wizard no puede ser más permisivo que el resolver", PlanEvaluationStage.MATERIAL, failure.stage)
                    assertNotNull(failure.reason)
                    assertTrue(failure.reason != PlanRejectionReason.INTERNAL_MATERIALIZATION)
                }
                is PlanAdaptationResult.Adapted -> {
                    // H9 no es una razón legítima de rechazo: es el piso de descanso de un rol que el
                    // adaptador debe respetar al sustituir. El rechazo por composición de abajo sigue
                    // permitido para las demás reglas, pero H9 se comprueba aquí sobre la receta
                    // derivada (el mensaje del materializador solo cita los primeros hallazgos).
                    assertTrue("$planId: la receta adaptada viola H9: ${h9Findings(verdict.recipe)}", h9Findings(verdict.recipe).isEmpty())
                    // Con cambios: o se publica el programa adaptado, o se rechaza por composición tipada.
                    val outcome = runCatching { prepare(planId, gear) }
                    val failure = outcome.exceptionOrNull()
                    if (failure != null) {
                        assertTrue(
                            "$planId: solo la composición puede rechazar una adaptación viable (${failure.message})",
                            failure is PlanMaterializationException && failure.reason == PlanRejectionReason.COMPOSITION,
                        )
                    } else {
                        assertAdaptedProgramRespectsTheVerdict(planId, outcome.getOrThrow(), verdict, gear)
                    }
                }
            }
            assertEquals("la receta del catálogo no se muta", recipeBefore, recipeJson(requireNotNull(entry.recipe)))
        }
    }

    @Test
    fun substitutingTheT1SquatForATierTwoAlternativeIsNeverRejectedByTheH9RestFloor() {
        // PHUL adaptado con solo mancuernas y banco: la sentadilla alta (T1, 180 s) cae a la sentadilla
        // copa (tier 2). El adaptador le ponía 120 s, por debajo del piso H9 de T1 (≥ 180 s, §12.2/§14.3),
        // y el wizard lo rechazaba con COMPOSITION como si fuera un problema del plan o del material.
        val gear = AuthoredPlanFixtures.dumbbellsAndBench
        val entry = AuthoredPlanFixtures.entry(PHUL_ADAPTED)
        val verdict = PlanAdaptationResolver.adapt(adaptationRequest(entry, gear))
        assertTrue("El resolver adapta PHUL con mancuernas y banco: $verdict", verdict is PlanAdaptationResult.Adapted)
        val recipe = (verdict as PlanAdaptationResult.Adapted).recipe

        val squats = recipe.weeks.flatMap { it.days }.flatMap { it.slots }.filter { it.id == "sq" }
        assertEquals(12, squats.size)
        squats.forEach { squat ->
            assertEquals("quads_sentadilla_copa__default", squat.lift.configurationId)
            assertEquals("el T1 sustituido conserva su piso H9", 180, squat.restSeconds)
        }
        assertTrue("sin hallazgos H9: ${h9Findings(recipe)}", h9Findings(recipe).isEmpty())

        // El wizard: o publica el programa, o la composición rechaza por OTRA regla; nunca por H9.
        val outcome = runCatching { prepare(PHUL_ADAPTED, gear) }
        outcome.exceptionOrNull()?.let { failure ->
            val typed = failure as? PlanMaterializationException
            assertTrue(
                "solo la composición por otra regla puede rechazarla (${failure.message})",
                typed != null && typed.reason == PlanRejectionReason.COMPOSITION,
            )
            assertTrue(
                "H9 es un defecto del adaptador y no puede ser la causa: ${typed?.affectedSlots}",
                typed!!.affectedSlots.none { it.startsWith("H9@") },
            )
        }
        outcome.getOrNull()?.let { program ->
            val published = AuthoredPlanFixtures.exercisesOf(program).filter { it.recipeSlotId == "sq" }
            assertEquals(12, published.size)
            published.forEach { assertEquals(180, it.restTime) }
        }
    }

    private fun adaptationRequest(entry: CatalogEntry, gear: Gear) = PlanAdaptationRequest(
        recipe = requireNotNull(entry.recipe),
        equipment = gear.equipment,
        availability = gear.availability,
        catalog = catalog,
    )

    private fun assertAdaptedProgramRespectsTheVerdict(
        planId: String,
        program: Program,
        verdict: PlanAdaptationResult.Adapted,
        gear: Gear,
    ) {
        val provenance = requireNotNull(program.planProvenance)
        assertEquals("$planId: slotChanges del resolver en el programa", verdict.changes, provenance.slotChanges)
        assertEquals(provenance, program.sourceRecipe?.provenance)
        val exercises = AuthoredPlanFixtures.exercisesOf(program)
        val configurations = exercises.mapNotNull { it.catalogConfigurationId }.distinct()
        val verdicts = PlanAdaptationResolver.missingMaterialOf(configurations, gear.equipment, gear.availability, catalog)
        assertTrue(
            "$planId: el programa adaptado solo usa material acreditado: ${verdicts.filterValues { it !is ConfigurationAvailability.Available }.keys}",
            verdicts.values.all { it is ConfigurationAvailability.Available },
        )
        verdict.changes.filter { it.toConfigurationId != null }.forEach { change ->
            exercises.filter { it.recipeSlotId == change.slotId && it.catalogConfigurationId == change.toConfigurationId }
                .forEach { substituted ->
                    assertNull("$planId/${change.slotId}: sin 1RM heredado", substituted.reference1RM)
                    assertNull("$planId/${change.slotId}: sin referencia de carga heredada", substituted.loadReference)
                    assertTrue(
                        "$planId/${change.slotId}: sin porcentajes",
                        substituted.sets.all { it.targetPercentageRM == null && it.weight == null },
                    )
                }
        }
    }

    // ─── Frecuencia y composición: rechazo tipado ────────────────────────────

    @Test
    fun aFixedRecipeThatProducesAnotherFrequencyIsRejectedWithATypedReason() {
        val entry = AuthoredPlanFixtures.entry(PHUL_ORIGINAL)
        try {
            AuthoredPlanMaterializer.prepare(
                AuthoredPlanFixtures.request(entry, AuthoredPlanFixtures.fullGym, expectedDaysPerWeek = 5),
            )
            fail("PHUL son 4 días: pedir 5 no puede publicar programa")
        } catch (typed: PlanMaterializationException) {
            assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, typed.stage)
            assertEquals(PlanRejectionReason.FREQUENCY, typed.reason)
            assertTrue(typed.message.orEmpty().contains("4"))
        }
    }

    @Test
    fun anUnresolvableConfigurationIsACatalogRejectionNotAnInternalError() {
        val broken = replaceFirstConfiguration(AuthoredPhulPhatRecipes.phulOriginal, "no_existe__config")
        val entry = AuthoredPlanFixtures.entry(PHUL_ORIGINAL).copy(recipe = broken)
        try {
            AuthoredPlanMaterializer.prepare(AuthoredPlanFixtures.request(entry, AuthoredPlanFixtures.fullGym))
            fail("una configuración inexistente nunca se afirma compatible")
        } catch (typed: PlanMaterializationException) {
            assertEquals(PlanEvaluationStage.CATALOG, typed.stage)
            assertEquals(PlanRejectionReason.UNRESOLVED_CONFIGURATION, typed.reason)
            assertEquals(listOf("no_existe__config"), typed.affectedSlots)
        }
    }

    @Test
    fun aHardCompositionFindingIsTypedAndNothingIsMaterialized() {
        val phul = AuthoredPhulPhatRecipes.phulOriginal
        // «Inferior fuerza» con un único ejercicio: H6 (3–9 ejercicios, ≥10 series) no tiene exención en el original.
        val broken = phul.copy(
            weeks = phul.weeks.map { week ->
                week.copy(
                    days = week.days.map { day ->
                        if (day.label == "Inferior fuerza") day.copy(slots = day.slots.take(1)) else day
                    },
                )
            },
        )
        val entry = AuthoredPlanFixtures.entry(PHUL_ORIGINAL).copy(recipe = broken)
        try {
            AuthoredPlanMaterializer.prepare(AuthoredPlanFixtures.request(entry, AuthoredPlanFixtures.fullGym))
            fail("una receta que incumple H6 no se publica")
        } catch (typed: PlanMaterializationException) {
            assertEquals(PlanEvaluationStage.COMPOSITION, typed.stage)
            assertEquals(PlanRejectionReason.COMPOSITION, typed.reason)
            assertTrue(typed.affectedSlots.any { it.startsWith("H6") })
        }
    }

    private fun replaceFirstConfiguration(recipe: TrainingPlanRecipe, configurationId: String): TrainingPlanRecipe =
        recipe.copy(
            weeks = recipe.weeks.map { week ->
                week.copy(
                    days = week.days.mapIndexed { dayIndex, day ->
                        if (dayIndex != 0) day else day.copy(
                            slots = day.slots.mapIndexed { slotIndex, slot ->
                                if (slotIndex == 0) slot.copy(lift = slot.lift.copy(configurationId = configurationId)) else slot
                            },
                        )
                    },
                )
            },
        )

    // ─── Identidad estable: la misma entrada da el mismo programa ────────────

    @Test
    fun theSameInputsGiveTheSameProgramAndStableIdsEvenWithRandomContainerIds() {
        val first = AuthoredPlanFixtures.prepare(PHUL_ORIGINAL, AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.SeqIds("a"))
        val second = AuthoredPlanFixtures.prepare(PHUL_ORIGINAL, AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.SeqIds("a"))
        assertEquals("misma entrada e idProvider determinista → mismo programa", first, second)

        // Con UUID reales cambian solo los contenedores (bloque/semana); sesiones y ejercicios son estables.
        val third = prepare(PHUL_ORIGINAL, AuthoredPlanFixtures.fullGym)
        val fourth = prepare(PHUL_ORIGINAL, AuthoredPlanFixtures.fullGym)
        assertEquals(AuthoredPlanFixtures.sessionsOf(third).map { it.id }, AuthoredPlanFixtures.sessionsOf(fourth).map { it.id })
        assertEquals(
            AuthoredPlanFixtures.exercisesOf(third).map { it.id },
            AuthoredPlanFixtures.exercisesOf(fourth).map { it.id },
        )
        assertEquals(third.sourceRecipe, fourth.sourceRecipe)
        assertEquals(third.planProvenance, fourth.planProvenance)
        assertNotEquals("los contenedores sí llevan UUID distintos", third, fourth)
        assertFalse(AuthoredPlanFixtures.exercisesOf(third).isEmpty())
    }
}
