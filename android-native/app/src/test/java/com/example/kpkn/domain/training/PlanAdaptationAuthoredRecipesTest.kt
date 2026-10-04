package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotLoadReferenceMetadata
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Consolidación 2026-10-01 (wizvm): el resolver ahora tiene llamador de
 * producción ([com.example.kpkn.domain.onboarding.AuthoredPlanMaterializer]) y
 * recibe las recetas REALES de PHUL/PHAT (12 y 6 semanas). Estas pruebas fijan
 * lo que `PlanAdaptationResolverTest` (recetas sintéticas de una semana) no
 * podía ver:
 *
 *  - un cambio se registra UNA vez por (slot, desde, hacia) aunque la receta
 *    repita el día en todas sus semanas, también cuando el RIR cambia por semana;
 *  - un slot repetido en varios días (PHAT: hack, ext, curl-lying) se registra UNA
 *    vez por (slot, desde, hacia) y el detalle de cada día viaja en `differences`
 *    (PlanSlotChange no tiene coordenada de día);
 *  - un T1/T2 sustituido por una alternativa de tier ≥ 2 conserva el piso de
 *    descanso H9 de su rol (T1 ≥ 180 s, T2 ≥ 120 s), con la política REAL;
 *  - una adaptación de una adaptación sigue citando al ORIGINAL como padre;
 *  - un slot SPEED (potencia ligada a su ejercicio pesado de 3-5 reps) nunca se
 *    reescribe como series de RIR: si su configuración falta, la adaptación no
 *    es viable;
 *  - la receta del catálogo no se muta.
 */
class PlanAdaptationAuthoredRecipesTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun adapt(recipe: TrainingPlanRecipe, gear: Gear): PlanAdaptationResult = PlanAdaptationResolver.adapt(
        PlanAdaptationRequest(
            recipe = recipe,
            equipment = gear.equipment,
            availability = gear.availability,
            catalog = CatalogCompositionTestSupport.catalog,
        ),
    )

    private fun adapted(result: PlanAdaptationResult): PlanAdaptationResult.Adapted {
        assertTrue("Se esperaba una adaptación viable y llegó $result", result is PlanAdaptationResult.Adapted)
        return result as PlanAdaptationResult.Adapted
    }

    private fun notViable(result: PlanAdaptationResult): PlanAdaptationResult.NotViable {
        assertTrue("Se esperaba una adaptación NO viable y llegó $result", result is PlanAdaptationResult.NotViable)
        return result as PlanAdaptationResult.NotViable
    }

    @Test
    fun fullGymPassesTheRealAdaptedRecipesThroughWithoutCopyingThem() {
        listOf(AuthoredPhulPhatRecipes.phulAdapted, AuthoredPhulPhatRecipes.phatAdapted).forEach { recipe ->
            val result = adapted(adapt(recipe, AuthoredPlanFixtures.fullGym))
            assertTrue("${recipe.id}: sin faltantes no hay cambios", result.changes.isEmpty())
            assertSame(recipe, result.recipe)
        }
    }

    @Test
    fun aSubstitutionIsRecordedOncePerSlotAcrossTheTwelveWeeksAndKeepsTheOriginalAsParent() {
        val catalogRecipe = AuthoredPhulPhatRecipes.phulAdapted
        val result = adapted(adapt(catalogRecipe, AuthoredPlanFixtures.gymWithoutRack))

        assertEquals(12, result.recipe.weeks.size)
        // Paquete A · B4: sin rack tampoco se puede hacer la sentadilla de barra. Las dos sentadillas de PHUL (`sq` y
        // `sq-front`) pasan a la MISMA definición en Smith (tier 1) y las dos bancas a mancuernas.
        assertEquals(
            "banca e inclinada de barra → mancuernas y sentadillas de barra → Smith; una entrada por slot, no 12",
            listOf("bp", "bp-inc", "sq", "sq-front"),
            result.changes.map { it.slotId }.sorted(),
        )
        assertEquals(
            setOf(CatalogIds.BP_DB, CatalogIds.BP_INC_DB, "high_bar_back_squat__smith_machine", "front_squat__smith_machine"),
            result.changes.mapNotNull { it.toConfigurationId }.toSet(),
        )

        val provenance = requireNotNull(result.recipe.provenance)
        assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
        assertEquals(result.changes, provenance.slotChanges)
        assertEquals(
            "adaptar una adaptación NO la convierte en hija de la adaptación",
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            provenance.parentId,
        )
        assertEquals(AuthoredPhulPhatRecipes.phulOriginal.provenance?.revision, provenance.parentRevision)
        assertEquals(catalogRecipe.provenance?.sourceEdition, provenance.sourceEdition)
        assertTrue(result.recipe.id.startsWith("${catalogRecipe.id}~adapted-"))

        // La sustitución llega a TODAS las semanas y la receta del catálogo queda intacta.
        result.recipe.weeks.forEach { week ->
            val bench = week.days.flatMap { it.slots }.single { it.id == "bp" }
            assertEquals(CatalogIds.BP_DB, bench.lift.configurationId)
            assertTrue(bench.sets.none { it.percent != null || it.reference != null })
        }
        assertEquals(
            "la receta del catálogo conserva la banca de barra",
            CatalogIds.BP,
            catalogRecipe.weeks.first().days.flatMap { it.slots }.single { it.id == "bp" }.lift.configurationId,
        )
    }

    @Test
    fun theSameSubstitutionIsNotDuplicatedWhenTheRirChangesByWeek() {
        // PHAT usa RIR 2 en las semanas 1-4 y RIR 1 en 5-6: dos días DISTINTOS para el resolver.
        val availability = AuthoredPlanFixtures.fullGym.availability
        val noPullUpBar = Gear(
            "E6-sin-barra-de-dominadas",
            availability.copy(supports = availability.supports + (EquipmentKeys.PULLUP_BAR to com.example.kpkn.data.models.ApparatusPresence.ABSENT)),
        )
        val result = adapted(adapt(AuthoredPhulPhatRecipes.phatAdapted, noPullUpBar))

        val change = result.changes.single()
        assertEquals("pullup", change.slotId)
        assertEquals(CatalogIds.PULLUP, change.fromConfigurationId)
        assertEquals("jalón en polea: mismo patrón vertical", CatalogIds.LAT, change.toConfigurationId)
        assertTrue(change.samePattern)
        assertTrue(
            "se conserva la prescripción de la primera semana (RIR 2): ${change.prescriptionBefore}",
            change.prescriptionBefore.orEmpty().contains("RIR 2"),
        )
        assertEquals(6, result.recipe.weeks.size)
        result.recipe.weeks.forEach { week ->
            val slot = week.days.flatMap { it.slots }.first { it.id == "pullup" }
            assertEquals("semana ${week.weekNumber}", CatalogIds.LAT, slot.lift.configurationId)
        }
    }

    /** Hallazgos H9 HARD de la receta con la política y los metadatos reales (lo que el materializador convierte en COMPOSITION). */
    private fun h9Findings(recipe: TrainingPlanRecipe): List<CompositionFinding> =
        ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata).filter { it.rule == "H9" }

    @Test
    fun aT1SubstitutedByATierTwoAlternativeKeepsItsH9FloorInEveryWeek() {
        // PHUL adaptado con solo mancuernas y banco: la sentadilla alta de barra (T1, «sq») no tiene
        // variante de su definición alcanzable y cae a la sentadilla copa (tier 2). Antes el tier 2
        // le ponía 120 s (< 180 s de H9) y AuthoredPlanMaterializer rechazaba el plan con COMPOSITION.
        val catalogRecipe = AuthoredPhulPhatRecipes.phulAdapted
        val result = adapted(adapt(catalogRecipe, AuthoredPlanFixtures.dumbbellsAndBench))

        val change = result.changes.single { it.slotId == "sq" }
        assertEquals(CatalogIds.SQ_HIGH, change.fromConfigurationId)
        assertEquals("quads_sentadilla_copa__default", change.toConfigurationId)
        assertTrue("alternativa curada del mismo patrón", change.samePattern)
        assertTrue(
            "el descanso y su piso H9 quedan registrados: ${change.differences}",
            change.differences.orEmpty().contains("Descanso conservado en 180 s (piso H9 del rol T1_MAIN: 180 s)"),
        )
        assertTrue(change.prescriptionBefore.orEmpty().contains("descanso 180s"))
        assertTrue(change.prescriptionAfter.orEmpty().contains("descanso 180s"))
        assertEquals(12, result.recipe.weeks.size)
        result.recipe.weeks.forEach { week ->
            val squat = week.days.flatMap { it.slots }.single { it.id == "sq" }
            assertEquals("semana ${week.weekNumber}", "quads_sentadilla_copa__default", squat.lift.configurationId)
            assertEquals("el rol no cambia al sustituir", SlotRole.T1_MAIN, squat.role)
            assertEquals("semana ${week.weekNumber}: el T1 conserva su piso H9", 180, squat.restSeconds)
        }
        assertEquals("ningún slot de la receta adaptada viola H9: ${h9Findings(result.recipe)}", emptyList<CompositionFinding>(), h9Findings(result.recipe))
        // La receta del catálogo no se muta.
        val catalogSquat = catalogRecipe.weeks.first().days.flatMap { it.slots }.single { it.id == "sq" }
        assertEquals(CatalogIds.SQ_HIGH, catalogSquat.lift.configurationId)
        assertEquals(180, catalogSquat.restSeconds)
    }

    @Test
    fun aT2SubstitutedByTheTierThreeReserveKeepsItsH9Floor() {
        // PHAT adaptado sin barra de dominadas ni poleas: la dominada (T2) pasa a la reserva de remo con
        // mancuernas (tier 3, cambio vertical → horizontal). Antes el tier 3 le ponía 90 s (< 120 s de H9).
        val full = AuthoredPlanFixtures.fullGym.availability
        val cableKeys = EFFECTIVE_EQUIPMENT_KEYS.filter { it.category == EquipmentCategory.CABLE }.map { it.key }.toSet()
        val noCableNoPullUpBar = Gear(
            "E6-sin-polea-ni-barra-de-dominadas",
            full.copy(
                categories = full.categories - EquipmentCategory.CABLE,
                apparatus = full.apparatus.filterKeys { it !in cableKeys },
                supports = full.supports + (EquipmentKeys.PULLUP_BAR to ApparatusPresence.ABSENT),
            ),
        )
        val result = adapted(adapt(AuthoredPhulPhatRecipes.phatAdapted, noCableNoPullUpBar))

        val change = result.changes.single { it.slotId == "pullup" }
        assertEquals(CatalogIds.PULLUP, change.fromConfigurationId)
        assertEquals("conventional_row__dumbbells", change.toConfigurationId)
        assertFalse("tirón vertical → horizontal: cambio de patrón documentado", change.samePattern)
        assertTrue(
            "el descanso y su piso H9 quedan registrados: ${change.differences}",
            change.differences.orEmpty().contains("Descanso conservado en 120 s (piso H9 del rol T2_SUPPLEMENTAL: 120 s)"),
        )
        assertEquals(6, result.recipe.weeks.size)
        result.recipe.weeks.forEach { week ->
            val pullUp = week.days.flatMap { it.slots }.single { it.id == "pullup" }
            assertEquals("conventional_row__dumbbells", pullUp.lift.configurationId)
            assertEquals(SlotRole.T2_SUPPLEMENTAL, pullUp.role)
            assertEquals("semana ${week.weekNumber}: el T2 conserva su piso H9", 120, pullUp.restSeconds)
        }
        assertEquals("ningún slot de la receta adaptada viola H9: ${h9Findings(result.recipe)}", emptyList<CompositionFinding>(), h9Findings(result.recipe))
    }

    @Test
    fun aSlotRepeatedInTwoPhatDaysIsRecordedOnceAndNamesBothDays() {
        // PHAT repite hack, ext y curl-lying en «Inferior fuerza» e «Inferior hipertrofia». El contrato
        // de procedencia no tiene día: una entrada por (slot, desde, hacia) con el detalle en `differences`.
        val full = AuthoredPlanFixtures.fullGym.availability
        val noLegMachines = Gear(
            "E6-sin-hack-extension-ni-curl-tumbado",
            full.copy(
                apparatus = full.apparatus + mapOf(
                    EquipmentKeys.HACK_SQUAT to ApparatusPresence.ABSENT,
                    EquipmentKeys.LEG_EXTENSION to ApparatusPresence.ABSENT,
                    EquipmentKeys.LEG_CURL_LYING to ApparatusPresence.ABSENT,
                ),
            ),
        )
        val result = adapted(adapt(AuthoredPhulPhatRecipes.phatAdapted, noLegMachines))

        assertEquals(
            "una entrada por slot aunque aparezca en dos días",
            setOf("hack", "ext", "curl-lying"),
            result.changes.map { it.slotId }.toSet(),
        )
        assertEquals(3, result.changes.size)
        result.changes.forEach { change ->
            val differences = change.differences.orEmpty()
            assertTrue("${change.slotId}: $differences", differences.contains("Aparece en 2 días"))
            assertTrue("${change.slotId}: $differences", differences.contains("«Inferior fuerza»"))
            assertTrue("${change.slotId}: $differences", differences.contains("«Inferior hipertrofia»"))
        }
        // `ext` no tiene alternativa curada: se retira en los dos días y en las seis semanas.
        assertNull(result.changes.single { it.slotId == "ext" }.toConfigurationId)
        result.recipe.weeks.forEach { week ->
            assertTrue("semana ${week.weekNumber}", week.days.flatMap { it.slots }.none { it.id == "ext" })
            assertEquals(
                "hack sustituido en los dos días de piernas",
                2,
                week.days.flatMap { it.slots }.count { it.id == "hack" && it.lift.configurationId != CatalogIds.SQ_HACK },
            )
        }
        assertEquals(result.changes, result.recipe.provenance?.slotChanges)
    }

    @Test
    fun speedSlotsKeepTheirPrescriptionWhenOtherSlotsAreSubstituted() {
        val original = AuthoredPhulPhatRecipes.phatAdapted
        val availability = AuthoredPlanFixtures.fullGym.availability
        val noPullUpBar = Gear(
            "E6-sin-barra-de-dominadas",
            availability.copy(supports = availability.supports + (EquipmentKeys.PULLUP_BAR to com.example.kpkn.data.models.ApparatusPresence.ABSENT)),
        )
        val result = adapted(adapt(original, noPullUpBar))

        fun speedSlots(recipe: TrainingPlanRecipe) =
            recipe.weeks.flatMap { it.days }.flatMap { it.slots }.filter { it.role == SlotRole.SPEED }
        assertEquals("tres SPEED por semana × 6 semanas", 18, speedSlots(result.recipe).size)
        assertEquals("la potencia del autor no se toca", speedSlots(original), speedSlots(result.recipe))
    }

    @Test
    fun aSpeedSlotWhoseExerciseIsMissingHasNoSubstituteAndTheAdaptationIsNotViable() {
        val recipe = rowWithSpeedRecipe()
        // Solo mancuernas: el remo pendlay pesado SÍ tiene reserva (remo gorila con mancuernas)...
        val dumbbellsOnly = Gear("solo-mancuernas", EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS)))

        val failure = notViable(adapt(recipe, dumbbellsOnly))

        // ...pero su SPEED va ligado a la carga de 3-5 reps del ejercicio de barra: sin sustituto.
        assertEquals(PlanAdaptationReason.NO_VALID_SUBSTITUTION, failure.reason)
        assertEquals("speed-row", failure.slotId)
        assertEquals(CatalogIds.PENDLAY, failure.configurationId)
        assertTrue("falta la barra: ${failure.missingRequirements}", "barbell" in failure.missingRequirements)
        assertTrue(failure.detail.contains("SPEED"))
    }

    @Test
    fun theSyntheticSpeedRecipeIsViableAsIsWithABarbell() {
        val recipe = rowWithSpeedRecipe()
        val barbell = Gear("barra", EquipmentAvailability(categories = setOf(EquipmentCategory.BARBELL)))

        val result = adapted(adapt(recipe, barbell))

        assertTrue(result.changes.isEmpty())
        assertSame(recipe, result.recipe)
        assertNotNull(result.recipe.weeks.first().days.first().slots.first { it.role == SlotRole.SPEED }.explicitReference)
    }

    /** Remo pesado de 3-5 reps + SPEED 6×3 al 65 % de su carga observada (forma exacta de PHAT §10.3). */
    private fun rowWithSpeedRecipe(): TrainingPlanRecipe {
        val heavy = SlotRecipe(
            id = "row-pendlay",
            role = SlotRole.T1_MAIN,
            lift = LiftRef(configurationId = CatalogIds.PENDLAY, liftSlot = null),
            sets = List(3) { SetRecipe(repsMin = 3, repsMax = 5, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
            restSeconds = 240,
            intent = SlotIntent.F,
        )
        val speed = SlotRecipe(
            id = "speed-row",
            role = SlotRole.SPEED,
            lift = LiftRef(configurationId = CatalogIds.PENDLAY, liftSlot = null),
            sets = List(6) {
                SetRecipe(
                    reps = 3,
                    percent = 65.0,
                    loadBasis = LoadBasis.RPE,
                    reference = PlanLoadReference(
                        kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                        configurationId = CatalogIds.PENDLAY,
                        sourceSlotId = "row-pendlay",
                        repMin = 3,
                        repMax = 5,
                    ),
                )
            },
            restSeconds = 90,
            technique = TechniqueModifier.SPEED,
            priority = SlotPriority.SPEED,
            intent = SlotIntent.P,
            explicitReference = SlotLoadReferenceMetadata(configurationId = CatalogIds.PENDLAY, repMin = 3, repMax = 5),
        )
        return TrainingPlanRecipe(
            id = "recipe-row-speed",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(DayRecipe(label = "Espalda", id = "d1", slots = listOf(heavy, speed))),
                ),
            ),
            provenance = PlanProvenance(
                category = PlanProvenanceClass.ADAPTED,
                recipeId = "recipe-row-speed",
                parentId = "recipe-row-speed-original",
            ),
        )
    }
}
