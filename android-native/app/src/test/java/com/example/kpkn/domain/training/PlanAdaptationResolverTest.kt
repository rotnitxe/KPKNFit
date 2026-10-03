package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.protocols.DayMinimumDose
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.KpknOperationalDefaultScope
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotLoadReferenceMetadata
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.warmupPercentSets
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §13.4/§15.2: adaptación de UNA receta al equipo, perfil, nivel y tiempo
 * reales. Cada caso obligatorio del plan se afirma con su motivo tipado y
 * comprobando que la receta original queda intacta (nunca una adaptación
 * silenciosa) y que la derivada lleva procedencia ADAPTED con sus cambios.
 */
class PlanAdaptationResolverTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    // ─── Escenarios de equipo (§13.1/§13.2) ──────────────────────────────────

    private class Scenario(
        val equipment: EffectiveEquipmentResult,
        val availability: EquipmentAvailability?,
    ) {
        fun request(
            recipe: TrainingPlanRecipe,
            targetProfile: PlanTargetProfile? = null,
            level: CatalogLevel? = CatalogLevel.INTERMEDIATE,
            sessionBudgetMinutes: Int? = null,
        ): PlanAdaptationRequest = PlanAdaptationRequest(
            recipe = recipe,
            equipment = equipment,
            availability = availability,
            catalog = CatalogCompositionTestSupport.catalog,
            targetProfile = targetProfile,
            level = level,
            sessionBudgetMinutes = sessionBudgetMinutes,
        )
    }

    /** Ruta de disponibilidad (§13.1) con las categorías/keys declaradas. */
    private fun confirmed(
        categories: Set<EquipmentCategory>,
        supports: Map<String, ApparatusPresence> = emptyMap(),
        apparatus: Map<String, ApparatusPresence> = emptyMap(),
    ): Scenario {
        val availability = EquipmentAvailability(categories = categories, apparatus = apparatus, supports = supports)
        return Scenario(
            equipment = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet()),
            availability = availability,
        )
    }

    /** Ruta legacy (chips) para LEER programas previos: sin disponibilidad nueva. */
    private fun legacy(vararg chips: String): Scenario = Scenario(
        equipment = TrainingOptions().resolveEffectiveEquipment(chips.toSet()),
        availability = null,
    )

    /**
     * Banca con banca y mancuernas acreditadas, rack negado y sin barra:
     * `bench_press__barbell` está ausente mientras `bench_press__dumbbells`
     * es alcanzable (§13.4 tier 1).
     */
    private fun benchWithoutBarbell(): Scenario = confirmed(
        categories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS),
        supports = mapOf(
            EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
            EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT,
        ),
    )

    // ─── Constructores de receta ─────────────────────────────────────────────

    private fun benchRecipe(
        isCompetitionLift: Boolean = false,
        claimedLevel: String? = null,
        sets: List<SetRecipe> = warmupPercentSets() +
            percentSets(180, 5 to 80.0, 5 to 80.0, amrapLast = true),
        dayId: String? = "d1",
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "recipe-bench",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Empuje",
                        id = dayId,
                        slots = listOf(
                            SlotRecipe(
                                id = "s-bench",
                                role = SlotRole.T1_MAIN,
                                lift = LiftRef("bench_press__barbell", LiftSlot.BENCH),
                                sets = sets,
                                restSeconds = 180,
                                technique = TechniqueModifier.PAUSE_2S,
                                isCompetitionLift = isCompetitionLift,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        claimedLevel = claimedLevel,
        liftSlots = mapOf(LiftSlot.BENCH to "bench_press__barbell"),
        provenance = PlanProvenance(
            category = PlanProvenanceClass.ORIGINAL,
            recipeId = "recipe-bench",
            revision = 7,
        ),
    )

    /** Receta de UN slot (siempre T2, salvo que se pida lo contrario). */
    private fun accessoryRecipe(
        configurationId: String,
        slotId: String,
        role: SlotRole = SlotRole.T2_SUPPLEMENTAL,
        essentialSlotIds: List<String> = emptyList(),
        sets: List<SetRecipe> = List(3) { SetRecipe(reps = 10, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
        restSeconds: Int = 120,
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "recipe-$slotId",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Accesorio",
                        id = "d1",
                        slots = listOf(
                            SlotRecipe(
                                id = slotId,
                                role = role,
                                lift = LiftRef(configurationId, null),
                                sets = sets,
                                restSeconds = restSeconds,
                            ),
                        ),
                        minimumDose = if (essentialSlotIds.isEmpty()) {
                            null
                        } else {
                            DayMinimumDose(essentialSlotIds = essentialSlotIds)
                        },
                    ),
                ),
            ),
        ),
    )

    private fun slotOf(recipe: TrainingPlanRecipe): SlotRecipe = recipe.weeks[0].days[0].slots[0]

    private fun sessionsOf(program: Program) = program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.sessions }

    private fun exercisesOf(program: Program) = sessionsOf(program).flatMap { it.allExercises() }

    /**
     * H9 (descanso mínimo por rol, HARD) evaluado con la política REAL y los
     * metadatos del catálogo publicado: lo que `AuthoredPlanMaterializer`
     * convertiría en un rechazo COMPOSITION si el adaptador dejara un slot
     * sustituido por debajo del piso de su rol.
     */
    private fun assertNoH9Findings(recipe: TrainingPlanRecipe) {
        val findings = recipe.weeks.flatMap { week ->
            week.days.flatMap { day ->
                SessionCompositionPolicy.evaluateDay(
                    day,
                    week,
                    CatalogCompositionTestSupport.metadata,
                    recipe.trainingMaxPercent,
                    recipe.compositionProfile,
                )
            }
        }.filter { it.rule == "H9" }
        assertTrue("La adaptación no puede violar H9: $findings", findings.isEmpty())
    }

    // ─── §13.4: sustituciones obligatorias ───────────────────────────────────

    @Test
    fun barbell_bench_substitutes_to_dumbbells_keeping_push_and_dropping_bar_load() {
        val scenario = benchWithoutBarbell()
        val original = benchRecipe()
        val result = PlanAdaptationResolver.adapt(scenario.request(original))
        assertTrue("Se espera adaptación tipada: $result", result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted

        assertEquals(1, adapted.changes.size)
        val change = adapted.changes.single()
        assertEquals("bench_press__barbell", change.fromConfigurationId)
        assertEquals("bench_press__dumbbells", change.toConfigurationId)
        assertTrue("Tier 1 = misma definición / mismo patrón", change.samePattern)
        assertTrue("Antes: ${change.prescriptionBefore}", change.prescriptionBefore!!.contains("@80%"))
        assertTrue("Después: ${change.prescriptionAfter}", change.prescriptionAfter!!.contains("RIR 2"))

        val slot = slotOf(adapted.recipe)
        assertEquals("bench_press__dumbbells", slot.lift.configurationId)
        assertNull("La base RM/TM de barra no viaja a la mancuerna", slot.lift.liftSlot)
        assertFalse(slot.isCompetitionLift)
        assertNull(slot.explicitReference)
        assertEquals(180, slot.restSeconds)
        assertEquals(TechniqueModifier.PAUSE_2S, slot.technique)
        assertEquals(2, slot.sets.size)
        assertTrue("Los calentamientos de % se descartan con la base", slot.sets.none { it.isWarmup })
        slot.sets.forEach { set ->
            assertNull(set.percent)
            assertNull(set.reference)
            assertEquals(LoadBasis.RPE, set.loadBasis)
            assertFalse(set.isTopSet)
        }
        assertEquals(2, slot.sets[0].rir ?: -1)
        assertEquals(8.0, slot.sets[0].rpe!!, 0.0001)
        assertTrue(slot.sets[1].amrap)
        assertNull(slot.sets[1].rir)

        // La receta original queda intacta.
        val originalSlot = slotOf(original)
        assertEquals("bench_press__barbell", originalSlot.lift.configurationId)
        assertEquals(80.0, originalSlot.sets[3].percent!!, 0.0001)
        assertTrue(originalSlot.sets.any { it.isWarmup })
        assertEquals(PlanProvenanceClass.ORIGINAL, original.provenance!!.category)

        // Principiante: 3 RIR (rpe 7) en la sustitución.
        val beginner = PlanAdaptationResolver.adapt(scenario.request(benchRecipe(), level = CatalogLevel.BEGINNER))
        assertTrue(beginner is PlanAdaptationResult.Adapted)
        val beginnerSlot = slotOf((beginner as PlanAdaptationResult.Adapted).recipe)
        assertEquals(3, beginnerSlot.sets[0].rir ?: -1)
        assertEquals(7.0, beginnerSlot.sets[0].rpe!!, 0.0001)
    }

    @Test
    fun bench_falls_back_to_push_up_with_movement_change_and_rir_prescription() {
        // Solo banca confirmada: ni barra, ni mancuernas, ni prensa en suelo.
        val scenario = confirmed(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf(
                EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
                EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT,
            ),
        )
        val result = PlanAdaptationResolver.adapt(scenario.request(benchRecipe()))
        assertTrue(result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        // `from` es SIEMPRE la configuración original del slot que se reemplaza
        // (la misma regla que `calf_raise__bilateral__machine` en el drop); el
        // candidato tier-3 (push-up) es el `to`.
        assertEquals("bench_press__barbell", change.fromConfigurationId)
        assertEquals("push_up__flat", change.toConfigurationId)
        assertFalse("Cambio de patrón documentado", change.samePattern)
        assertTrue(change.reason.contains("cambio de patrón"))

        val slot = slotOf(adapted.recipe)
        // Oráculo corregido (antes 90 s): el slot sigue siendo T1_MAIN y H9 (§12.2/§14.3,
        // «T1 ≥ 180 s») es un piso por ROL, no por ejercicio. Bajarlo a 90 s hacía que la
        // receta adaptada se rechazara con COMPOSITION por un defecto del adaptador; el
        // descanso prescrito por la receta (180 s) se conserva y el cambio queda registrado.
        assertEquals(180, slot.restSeconds)
        assertNoH9Findings(adapted.recipe)
        assertTrue("Prescripción registrada: ${change.prescriptionAfter}", change.prescriptionAfter!!.contains("descanso 180s"))
        assertTrue(
            "El descanso y su piso H9 quedan registrados en el cambio: ${change.differences}",
            change.differences.orEmpty().contains("Descanso conservado en 180 s") &&
                change.differences.orEmpty().contains("H9 del rol T1_MAIN"),
        )
        assertNull(slot.technique)
        assertEquals(2, slot.sets.size)
        slot.sets.forEach { set ->
            assertEquals(8, set.reps ?: -1)
            assertEquals(8, set.repsMin ?: -1)
            assertEquals(15, set.repsMax ?: -1)
            assertNull(set.percent)
            assertEquals(LoadBasis.RPE, set.loadBasis)
        }
        assertEquals(2, slot.sets[0].rir ?: -1)
        assertTrue(slot.sets[1].amrap)
        assertTrue(
            "Prescripción legible tras el cambio: ${change.prescriptionAfter}",
            change.prescriptionAfter!!.contains("8-15"),
        )
    }

    @Test
    fun leg_press_substitutes_to_goblet_and_hack_to_bodyweight_squat_by_achievable_range() {
        // Sin maquinaria pero con mancuernas → sentadilla copa (tier 2, 8-12).
        val withDumbbells = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS))
        val goblet = PlanAdaptationResolver.adapt(
            withDumbbells.request(accessoryRecipe("quads_prensa_piernas__bilateral", "s-press")),
        )
        assertTrue(goblet is PlanAdaptationResult.Adapted)
        val gobletAdapted = goblet as PlanAdaptationResult.Adapted
        val gobletChange = gobletAdapted.changes.single()
        assertEquals("quads_sentadilla_copa__default", gobletChange.toConfigurationId)
        assertTrue(gobletChange.samePattern)
        assertTrue(gobletChange.reason.contains("Sentadilla copa"))
        val gobletSlot = slotOf(gobletAdapted.recipe)
        assertEquals(120, gobletSlot.restSeconds)
        assertEquals(8, gobletSlot.sets[0].repsMin ?: -1)
        assertEquals(12, gobletSlot.sets[0].repsMax ?: -1)
        assertEquals(2, gobletSlot.sets[0].rir ?: -1)

        // Sin material externo → reserva de peso corporal (tier 3, 8-15).
        val bodyweightOnly = confirmed(setOf(EquipmentCategory.SUPPORT))
        val squat = PlanAdaptationResolver.adapt(
            bodyweightOnly.request(accessoryRecipe("quads_sentadilla_hack__machine", "s-hack")),
        )
        assertTrue(squat is PlanAdaptationResult.Adapted)
        val squatAdapted = squat as PlanAdaptationResult.Adapted
        val squatChange = squatAdapted.changes.single()
        assertEquals("quads_sentadilla_sin_carga__default", squatChange.toConfigurationId)
        assertTrue(squatChange.samePattern)
        val squatSlot = slotOf(squatAdapted.recipe)
        // Oráculo corregido (antes 90 s): el slot es T2 (accessoryRecipe) y el piso H9 de T2 es
        // 120 s (§12.2/§14.3); la receta ya prescribía 120 s y la reserva corporal no lo baja.
        assertEquals(120, squatSlot.restSeconds)
        assertNoH9Findings(squatAdapted.recipe)
        assertEquals(8, squatSlot.sets[0].repsMin ?: -1)
        assertEquals(15, squatSlot.sets[0].repsMax ?: -1)
        assertEquals(2, squatSlot.sets[0].rir ?: -1)
        assertNotEquals(gobletChange.toConfigurationId, squatChange.toConfigurationId)
    }

    @Test
    fun hack_squat_on_unconfirmed_machines_is_apparatus_unknown() {
        // MACHINES confirmada pero SIN la clave exacta de la hack: falta confirmar.
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.MACHINES))
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("quads_sentadilla_hack__machine", "s-hack")),
        )
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.APPARATUS_UNKNOWN, failure.reason)
        assertEquals("s-hack", failure.slotId)
        assertEquals("quads_sentadilla_hack__machine", failure.configurationId)
        assertTrue("Falta confirmar material: ${failure.missingRequirements}", "machine" in failure.missingRequirements)
        assertTrue(failure.detail.contains("Falta confirmar"))
    }

    @Test
    fun lying_leg_curl_without_pattern_equivalent_is_no_valid_substitution() {
        // Ni máquina, ni polea, ni mancuernas: el curl no tiene equivalencia de
        // patrón (un puente/frog pump es extensión de cadera) y no se propone.
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT))
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("lying_leg_curl__bilateral__machine", "s-curl")),
        )
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.NO_VALID_SUBSTITUTION, failure.reason)
        assertEquals("s-curl", failure.slotId)
        assertEquals("lying_leg_curl__bilateral__machine", failure.configurationId)
        assertTrue("Gap documentado: ${failure.detail}", failure.detail.contains("extensión de cadera"))
        assertTrue(failure.detail.contains("Sin alternativa viable"))
    }

    @Test
    fun essential_slot_without_alternative_fails_typed_instead_of_dropping() {
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT))
        val essential = accessoryRecipe(
            configurationId = "lying_leg_curl__bilateral__machine",
            slotId = "s-curl",
            role = SlotRole.T3_ACCESSORY,
            essentialSlotIds = listOf("s-curl"),
        )
        val result = PlanAdaptationResolver.adapt(scenario.request(essential))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals("Un slot esencial nunca se descarta en silencio", PlanAdaptationReason.NO_VALID_SUBSTITUTION, failure.reason)
        assertEquals("s-curl", failure.slotId)
    }

    @Test
    fun bodyweight_calf_closes_the_catalog_gap_and_substitutes_instead_of_dropping() {
        // AC-E4 / §13.5: `calf_raise__bilateral__bodyweight` YA está publicada,
        // así que el T3 de gemelo sin máquina se sustituye a la reserva corporal
        // en lugar de descartarse: la nota de gap de CATALOG_GAP_NOTES ya no se
        // emite para esta definición.
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT))
        val original = accessoryRecipe("calf_raise__bilateral__machine", "s-calf", role = SlotRole.T3_ACCESSORY)
        val result = PlanAdaptationResolver.adapt(scenario.request(original))
        assertTrue("Esperaba sustitución, no descarte: $result", result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        assertEquals("s-calf", change.slotId)
        assertEquals("calf_raise__bilateral__machine", change.fromConfigurationId)
        assertEquals("calf_raise__bilateral__bodyweight", change.toConfigurationId)
        assertTrue("Tier 1 = misma definición", change.samePattern)
        assertEquals("calf_raise__bilateral__bodyweight", slotOf(adapted.recipe).lift.configurationId)
        assertTrue(
            "El gap documentado ya no se usa: ${change.reason}",
            !change.reason.contains("Sin variante de gemelo") && !change.reason.contains("a redistribuir"),
        )
        // La receta original queda intacta: nunca se muta para adaptar (§13.4).
        assertEquals("calf_raise__bilateral__machine", slotOf(original).lift.configurationId)
        assertTrue(original.weeks[0].days[0].slots[0].sets.isNotEmpty())
    }

    @Test
    fun droppable_t3_without_alternative_is_recorded_as_drop_not_silent_weakening() {
        // Sigue cubierto el camino de descarte para una definición SIN
        // alternativa viable: curl femoral SENTADO sin máquina ni polea (§13.4
        // no propone extensión de cadera como equivalencia de flexión).
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT))
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("seated_leg_curl__bilateral__machine", "s-curl", role = SlotRole.T3_ACCESSORY)),
        )
        assertTrue(result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        assertNull(change.toConfigurationId)
        assertEquals("s-curl", change.slotId)
        assertEquals("seated_leg_curl__bilateral__machine", change.fromConfigurationId)
        assertTrue("Serie visible para el fitter: ${change.reason}", change.reason.contains("a redistribuir"))
        assertEquals("sin series (a redistribuir por el fitter)", change.prescriptionAfter)
        assertTrue("El día queda sin el slot, nunca con una versión debilitada", adapted.recipe.weeks[0].days[0].slots.isEmpty())
    }

    /**
     * AC-E4: las cuatro altas corporales de §13.5/§13.6 existen y son las que
     * el resolver mira como reserva de la MISMA definición (tier 1); las notas
     * de `CATALOG_GAP_NOTES` correspondientes ya no se emiten.
     */
    @Test
    fun the_four_bodyweight_catalog_gap_cases_resolve_to_the_published_reserves() {
        val bodyweightOnly = confirmed(emptySet())

        // 1–3: las reservas corporales publicadas sustituyen a sus variantes con
        // material, en lugar de entrar en el camino de gap/descarte.
        mapOf(
            "calf_raise__bilateral__machine" to "calf_raise__bilateral__bodyweight",
            "reverse_lunge__barbell" to "reverse_lunge__bodyweight",
            "glutes_puente_gluteos__bilateral__barbell" to "glutes_puente_gluteos__bilateral__bodyweight",
        ).forEach { (original, reserve) ->
            val result = PlanAdaptationResolver.adapt(
                bodyweightOnly.request(accessoryRecipe(original, "s-${original.substringBefore("__")}")),
            )
            assertTrue("$original debe sustituirse: $result", result is PlanAdaptationResult.Adapted)
            val adapted = result as PlanAdaptationResult.Adapted
            assertEquals("$original → reserva corporal publicada", reserve, slotOf(adapted.recipe).lift.configurationId)
            val change = adapted.changes.single()
            assertTrue(
                "El gap de ${original.substringBefore("__")} ya no se emite: ${change.reason}",
                !change.reason.contains("gap") && !change.reason.contains("Sin "),
            )
        }

        // 4: `push_up__hands_elevated` es la variante publicada que el resolver
        // considera para la misma definición y es utilizable con solo cuerpo
        // (sin requisito de soporte), mientras que la de pies elevados sigue
        // exigiendo su apoyo real.
        val material = PlanAdaptationResolver.missingMaterialOf(
            listOf("push_up__flat", "push_up__feet_elevated", "push_up__hands_elevated"),
            bodyweightOnly.equipment,
            bodyweightOnly.availability,
            CatalogCompositionTestSupport.catalog,
        )
        assertEquals(
            "La alta de §13.5 se resuelve con solo cuerpo",
            ConfigurationAvailability.Available,
            material["push_up__hands_elevated"],
        )
        assertTrue(
            "Pies elevados sigue exigiendo apoyo estable",
            material["push_up__feet_elevated"] is ConfigurationAvailability.Missing,
        )
        val handsElevated = PlanAdaptationResolver.adapt(
            bodyweightOnly.request(accessoryRecipe("push_up__hands_elevated", "s-push-hands")),
        )
        assertTrue(handsElevated is PlanAdaptationResult.Adapted)
        assertTrue(
            "La variante publicada no genera cambios ni nota de gap",
            (handsElevated as PlanAdaptationResult.Adapted).changes.isEmpty(),
        )
    }

    @Test
    fun row_without_rowing_apparatus_fails_typed_and_never_becomes_superman() {
        // Sin banda, sin mancuernas, sin remo y sin barra baja: el remo no se
        // convierte en superman (extensores de columna ≠ tracción horizontal).
        val scenario = confirmed(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf(EquipmentKeys.LOW_BAR_SUPPORT to ApparatusPresence.ABSENT),
        )
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("conventional_row__cable", "s-row")),
        )
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.APPARATUS_ABSENT, failure.reason)
        assertEquals("s-row", failure.slotId)
        assertEquals("conventional_row__cable", failure.configurationId)
        assertTrue(failure.detail.contains("Sin alternativa viable"))
        assertFalse("El superman jamás se propone como remo", failure.detail.contains("superman"))
    }

    @Test
    fun band_pull_apart_substitutes_to_superman_with_documented_pattern_change() {
        // El superman SOLO es candidato de la apertura con banda, y siempre con
        // el cambio de patrón documentado (no cuenta como remo).
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT))
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("back_band_pull_apart__default", "s-pull-apart")),
        )
        assertTrue(result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        assertEquals("back_superman_suelo__default", change.toConfigurationId)
        assertFalse(change.samePattern)
        assertTrue("Nota §13.4: ${change.reason}", change.reason.contains("NO cuenta como remo"))
    }

    @Test
    fun pull_up_substitutes_to_band_row_with_documented_vertical_to_horizontal_change() {
        // Sin barra de dominadas ni polea/máquina: la reserva es tracción
        // horizontal con banda, documentada como cambio de patrón.
        val scenario = confirmed(
            categories = setOf(EquipmentCategory.BAND),
            supports = mapOf(EquipmentKeys.PULLUP_BAR to ApparatusPresence.ABSENT),
        )
        val result = PlanAdaptationResolver.adapt(
            scenario.request(accessoryRecipe("pull_up__pronated__medium", "s-pullup")),
        )
        assertTrue(result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        assertEquals("back_remo_banda__default", change.toConfigurationId)
        assertFalse("Tracción vertical → horizontal", change.samePattern)
        assertTrue("Nota §13.4: ${change.reason}", change.reason.contains("vertical → horizontal"))
        val slot = slotOf(adapted.recipe)
        // Oráculo corregido (antes 90 s): T2 (accessoryRecipe) ≥ 120 s por H9, y la receta prescribía 120 s.
        assertEquals(120, slot.restSeconds)
        assertNoH9Findings(adapted.recipe)
        assertEquals(2, slot.sets[0].rir ?: -1)
    }

    // ─── H9: el descanso de un slot sustituido respeta el piso de su rol ─────

    @Test
    fun substituted_slot_is_raised_to_the_h9_floor_of_its_role_and_the_change_records_the_rest() {
        // El rol no cambia al sustituir el ejercicio: la prensa de cualquier rol cae a sentadilla
        // copa (tier 2) y su descanso nunca queda bajo el piso H9 de ese rol (§12.2/§14.3: T1 ≥ 180 s,
        // T2 y técnica ≥ 120 s, T3 compuesto ≥ 90 s). Aquí la receta prescribe 45 s (por debajo de
        // cualquier piso) para ejercitar la rama «elevar» y comprobar que queda registrada.
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS))
        val floors = mapOf(
            SlotRole.T1_MAIN to 180,
            SlotRole.T2_SUPPLEMENTAL to 120,
            SlotRole.TECHNIQUE to 120,
            SlotRole.T3_ACCESSORY to 90,
        )
        floors.forEach { (role, floor) ->
            val original = accessoryRecipe("quads_prensa_piernas__bilateral", "s-press", role = role, restSeconds = 45)
            val result = PlanAdaptationResolver.adapt(scenario.request(original))
            assertTrue("$role: se esperaba adaptación: $result", result is PlanAdaptationResult.Adapted)
            val adapted = result as PlanAdaptationResult.Adapted
            val change = adapted.changes.single()
            assertEquals("$role: tier 2", "quads_sentadilla_copa__default", change.toConfigurationId)
            assertEquals("$role: el rol no cambia al sustituir", role, slotOf(adapted.recipe).role)
            assertEquals("$role: piso H9", floor, slotOf(adapted.recipe).restSeconds)
            assertNoH9Findings(adapted.recipe)
            assertTrue("$role: ${change.prescriptionBefore}", change.prescriptionBefore!!.contains("descanso 45s"))
            assertTrue("$role: ${change.prescriptionAfter}", change.prescriptionAfter!!.contains("descanso ${floor}s"))
            assertTrue(
                "$role: el descanso elevado queda registrado: ${change.differences}",
                change.differences.orEmpty().contains("Descanso elevado de 45 s a $floor s"),
            )
            assertEquals("$role: la receta original no se muta", 45, slotOf(original).restSeconds)
        }
    }

    @Test
    fun a_longer_prescribed_rest_is_kept_and_slots_that_do_not_change_keep_theirs() {
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS))
        val substituted = SlotRecipe(
            id = "s-press",
            role = SlotRole.T2_SUPPLEMENTAL,
            lift = LiftRef("quads_prensa_piernas__bilateral", null),
            sets = List(3) { SetRecipe(reps = 8, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
            restSeconds = 240,
        )
        // Material de cuerpo: siempre disponible, así que el slot no cambia y su descanso (60 s,
        // por debajo del piso de T3 compuesto) NO se toca: el piso solo rige para lo que se sustituye.
        val untouched = SlotRecipe(
            id = "s-pushup",
            role = SlotRole.T3_ACCESSORY,
            lift = LiftRef("push_up__flat", null),
            sets = List(2) { SetRecipe(reps = 12, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
            restSeconds = 60,
        )
        val recipe = TrainingPlanRecipe(
            id = "recipe-rest",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(DayRecipe(label = "Piernas", id = "d1", slots = listOf(substituted, untouched))),
                ),
            ),
        )

        val result = PlanAdaptationResolver.adapt(scenario.request(recipe))

        assertTrue("Se esperaba adaptación: $result", result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val slots = adapted.recipe.weeks[0].days[0].slots
        assertEquals("quads_sentadilla_copa__default", slots[0].lift.configurationId)
        assertEquals("un descanso mayor que el piso se conserva: una sustitución cambia lo mínimo", 240, slots[0].restSeconds)
        assertEquals("un slot que no cambia queda idéntico, también su descanso", untouched, slots[1])
        assertEquals(listOf("s-press"), adapted.changes.map { it.slotId })
        assertTrue(
            "El descanso conservado y su piso quedan registrados: ${adapted.changes.single().differences}",
            adapted.changes.single().differences.orEmpty().contains("Descanso conservado en 240 s (piso H9 del rol T2_SUPPLEMENTAL: 120 s)"),
        )
    }

    @Test
    fun the_same_slot_in_two_days_is_recorded_once_with_the_detail_of_each_day() {
        // PlanSlotChange no tiene coordenada de día: dos entradas con el mismo slotId y las mismas
        // configuraciones serían indistinguibles. Se registra UNA vez por (slot, desde, hacia) y el
        // detalle de cada día viaja en `differences`.
        val scenario = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS))
        fun hackDay(label: String, id: String, sets: Int) = DayRecipe(
            label = label,
            id = id,
            slots = listOf(
                SlotRecipe(
                    id = "s-hack",
                    role = SlotRole.T2_SUPPLEMENTAL,
                    lift = LiftRef("quads_sentadilla_hack__machine", null),
                    sets = List(sets) { SetRecipe(reps = 10, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
                    restSeconds = 120,
                ),
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "recipe-two-days",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(hackDay("Inferior fuerza", "dA", 2), hackDay("Inferior hipertrofia", "dB", 3)),
                ),
            ),
        )

        val result = PlanAdaptationResolver.adapt(scenario.request(recipe))

        assertTrue("Se esperaba adaptación: $result", result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        val change = adapted.changes.single()
        assertEquals("s-hack", change.slotId)
        assertEquals("quads_sentadilla_copa__default", change.toConfigurationId)
        val differences = change.differences.orEmpty()
        assertTrue("Nombra los dos días: $differences", differences.contains("Aparece en 2 días"))
        assertTrue(differences, differences.contains("«Inferior fuerza» 2×10 RIR 2, descanso 120s → 2×8-12 RIR 2, descanso 120s"))
        assertTrue(differences, differences.contains("«Inferior hipertrofia» 3×10 RIR 2, descanso 120s → 3×8-12 RIR 2, descanso 120s"))
        // Ambos días se sustituyen, cada uno con sus series.
        assertEquals(listOf(2, 3), adapted.recipe.weeks[0].days.map { it.slots.single().sets.size })
        adapted.recipe.weeks[0].days.forEach { day ->
            assertEquals("quads_sentadilla_copa__default", day.slots.single().lift.configurationId)
        }
        assertEquals(adapted.changes, adapted.recipe.provenance?.slotChanges)
    }

    // ─── §15.2: motivos tipados ──────────────────────────────────────────────

    @Test
    fun competition_lift_missing_material_never_substituted_silently() {
        // Con mancuernas disponibles: un lift de competición sigue sin sustituir.
        val result = PlanAdaptationResolver.adapt(benchWithoutBarbell().request(benchRecipe(isCompetitionLift = true)))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.NO_VALID_SUBSTITUTION, failure.reason)
        assertEquals("s-bench", failure.slotId)
        assertEquals("bench_press__barbell", failure.configurationId)
        assertTrue(failure.detail.contains("Lift de competición"))
        assertTrue("barbell" in failure.missingRequirements)
    }

    @Test
    fun unconfirmed_support_is_apparatus_unknown_even_when_substitute_exists() {
        // Barra y banca acreditadas, rack sin confirmar: falta confirmar (no se
        // sustituye material de una respuesta sin cerrar).
        val scenario = confirmed(
            categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT),
            supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT),
        )
        val result = PlanAdaptationResolver.adapt(scenario.request(benchRecipe()))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.APPARATUS_UNKNOWN, failure.reason)
        assertEquals("rack", failure.missingRequirements.first())
        assertTrue(failure.detail.contains("Falta confirmar"))
    }

    @Test
    fun legacy_route_without_answers_keeps_material_unknown() {
        // Sin disponibilidad declarada no se niega nada: queda «falta confirmar».
        val scenario = legacy("bodyweight")
        val result = PlanAdaptationResolver.adapt(scenario.request(benchRecipe()))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.APPARATUS_UNKNOWN, failure.reason)
        assertTrue("barbell" in failure.missingRequirements)
        assertTrue("bench" in failure.missingRequirements)
    }

    @Test
    fun unknown_configuration_is_unresolved() {
        val scenario = legacy("general_gym")
        val result = PlanAdaptationResolver.adapt(scenario.request(accessoryRecipe("no_existe__config", "s-x")))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.UNRESOLVED_CONFIGURATION, failure.reason)
        assertEquals("no_existe__config", failure.configurationId)
        assertTrue(failure.detail.contains("no existe en el catálogo publicado"))
    }

    @Test
    fun load_reference_for_another_configuration_is_unrepresentable() {
        val scenario = benchWithoutBarbell()
        val slotRecipe = SlotRecipe(
            id = "s-db",
            role = SlotRole.T2_SUPPLEMENTAL,
            lift = LiftRef("bench_press__dumbbells", null),
            sets = List(2) { SetRecipe(reps = 8, rir = 2, rpe = 8.0, loadBasis = LoadBasis.RPE) },
            restSeconds = 120,
            explicitReference = SlotLoadReferenceMetadata(
                configurationId = "bench_press__barbell",
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "recipe-load",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(DayRecipe(label = "Día", id = "d1", slots = listOf(slotRecipe))),
                ),
            ),
        )
        val result = PlanAdaptationResolver.adapt(scenario.request(recipe))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.LOAD_BASIS_UNREPRESENTABLE, failure.reason)
        assertEquals("s-db", failure.slotId)
        assertTrue("Detalle: ${failure.detail}", failure.detail.contains("bench_press__barbell"))
    }

    @Test
    fun lastre_and_assistance_are_never_interchanged_in_adaptation() {
        val configurationId = "quads_sentadilla_sin_carga__default"
        val slotRecipe = SlotRecipe(
            id = "s-assisted",
            role = SlotRole.T2_SUPPLEMENTAL,
            lift = LiftRef(configurationId, null),
            sets = listOf(
                SetRecipe(
                    reps = 8,
                    rir = 2,
                    rpe = 8.0,
                    loadBasis = LoadBasis.RPE,
                    reference = PlanLoadReference(
                        kind = PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
                        configurationId = configurationId,
                        quantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
                        state = PlanLoadReferenceState.CAPTURED,
                        capturedLoadKg = 20.0,
                    ),
                ),
            ),
            restSeconds = 90,
            explicitReference = SlotLoadReferenceMetadata(
                configurationId = configurationId,
                quantityConvention = LoadQuantityConvention.ASSISTANCE,
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "recipe-convention",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(DayRecipe(label = "Día", id = "d1", slots = listOf(slotRecipe))),
                ),
            ),
        )
        val result = PlanAdaptationResolver.adapt(confirmed(emptySet()).request(recipe))
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.LOAD_BASIS_UNREPRESENTABLE, failure.reason)
        assertTrue("Detalle: ${failure.detail}", failure.detail.contains("lastre y asistencia no se intercambian"))
    }

    @Test
    fun powerlifting_profile_requires_barbell_bench_and_rack() {
        val scenario = legacy("bodyweight")
        val result = PlanAdaptationResolver.adapt(
            scenario.request(benchRecipe(), targetProfile = PlanTargetProfile.POWERLIFTING),
        )
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.PROFILE_MISMATCH, failure.reason)
        assertTrue("barbell" in failure.missingRequirements)
        assertTrue("bench" in failure.missingRequirements)
        assertTrue("rack" in failure.missingRequirements)
    }

    @Test
    fun complete_athlete_profile_requires_real_cardio_blocks() {
        val scenario = confirmed(emptySet())
        val strengthOnly = accessoryRecipe("quads_sentadilla_sin_carga__default", "s-squat")
        val rejected = PlanAdaptationResolver.adapt(
            scenario.request(strengthOnly, targetProfile = PlanTargetProfile.COMPLETE_ATHLETE),
        )
        val failure = rejected as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.PROFILE_MISMATCH, failure.reason)

        val withCardio = strengthOnly.copy(
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(
                        DayRecipe(
                            label = "Día mixto",
                            id = "d1",
                            slots = strengthOnly.weeks[0].days[0].slots,
                            cardioBlocks = listOf(
                                RecipeCardioBlock(
                                    id = "cb-1",
                                    details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 600),
                                    position = RecipeCardioPosition.AFTER_STRENGTH,
                                ),
                            ),
                            sessionKind = RecipeSessionKind.STRENGTH_CARDIO,
                        ),
                    ),
                ),
            ),
        )
        val accepted = PlanAdaptationResolver.adapt(
            scenario.request(withCardio, targetProfile = PlanTargetProfile.COMPLETE_ATHLETE),
        )
        assertTrue("Con cardio real el perfil es viable: $accepted", accepted is PlanAdaptationResult.Adapted)
    }

    @Test
    fun recipe_level_above_user_is_level_unsuitable() {
        val scenario = benchWithoutBarbell()
        val tooHard = PlanAdaptationResolver.adapt(
            scenario.request(benchRecipe(claimedLevel = "avanzado"), level = CatalogLevel.INTERMEDIATE),
        )
        val failure = tooHard as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.LEVEL_UNSUITABLE, failure.reason)
        assertTrue(failure.detail.contains("avanzado"))

        val suitable = PlanAdaptationResolver.adapt(
            scenario.request(benchRecipe(claimedLevel = "intermedio"), level = CatalogLevel.ADVANCED),
        )
        assertTrue("Nivel adecuado no bloquea: $suitable", suitable is PlanAdaptationResult.Adapted)
    }

    @Test
    fun session_time_budget_reports_required_minutes() {
        // 4 series × (180 s descanso + 45 s de serie) = 900 s → 15 min.
        val recipe = accessoryRecipe(
            configurationId = "bench_press__barbell",
            slotId = "s-bench",
            sets = percentSets(180, 5 to 80.0, 5 to 80.0, 5 to 80.0, 5 to 80.0),
            restSeconds = 180,
        )
        val result = PlanAdaptationResolver.adapt(
            legacy("general_gym").request(recipe, sessionBudgetMinutes = 10),
        )
        val failure = result as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.TIME_BUDGET, failure.reason)
        assertEquals(15, failure.requiredMinutes ?: -1)
        assertTrue(failure.detail.contains("10 min"))
    }

    @Test
    fun fully_available_recipe_passes_through_unchanged() {
        // `general_gym` (legacy) acredita todo: sin cambios se devuelve MISMA receta.
        val recipe = benchRecipe()
        val result = PlanAdaptationResolver.adapt(
            legacy("general_gym").request(recipe, targetProfile = PlanTargetProfile.POWERLIFTING),
        )
        assertTrue(result is PlanAdaptationResult.Adapted)
        val adapted = result as PlanAdaptationResult.Adapted
        assertTrue(adapted.changes.isEmpty())
        assertSame(recipe, adapted.recipe)
        assertEquals(PlanProvenanceClass.ORIGINAL, adapted.recipe.provenance?.category)
    }

    // ─── Receta derivada: identidad y procedencia (§14.1/§14.4) ─────────────

    @Test
    fun adapted_recipe_gets_derived_id_and_adapted_provenance_without_mutating_original() {
        val scenario = benchWithoutBarbell()
        val original = benchRecipe()
        val first = PlanAdaptationResolver.adapt(scenario.request(original)) as PlanAdaptationResult.Adapted
        val adapted = first.recipe
        val provenance = adapted.provenance!!

        assertTrue(adapted.id.startsWith("${original.id}~adapted-"))
        assertNotEquals(original.id, adapted.id)
        assertEquals(original.contentVersion, adapted.contentVersion)
        assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
        assertEquals(original.id, provenance.parentId)
        assertEquals(original.provenance!!.revision, provenance.parentRevision)
        assertEquals(original.provenance!!.recipeId, provenance.recipeId)
        assertEquals(first.changes, provenance.slotChanges)
        assertTrue(provenance.operationalDefaults.any { it.scope == KpknOperationalDefaultScope.INITIAL_CHOICE })
        assertTrue("Los lifts de la configuración sustituida se retiran: ${adapted.liftSlots}", adapted.liftSlots.isEmpty())

        // Determinismo: misma entrada → misma identidad derivada.
        val second = PlanAdaptationResolver.adapt(scenario.request(original)) as PlanAdaptationResult.Adapted
        assertEquals(adapted.id, second.recipe.id)
        assertEquals(first.changes, second.changes)

        // La receta original sigue intacta.
        assertEquals("recipe-bench", original.id)
        assertEquals(PlanProvenanceClass.ORIGINAL, original.provenance!!.category)
        assertEquals("bench_press__barbell", slotOf(original).lift.configurationId)
        assertEquals(80.0, slotOf(original).sets[3].percent!!, 0.0001)
    }

    @Test
    fun missing_material_api_exposes_typed_verdicts() {
        val scenario = benchWithoutBarbell()
        val verdicts = PlanAdaptationResolver.missingMaterialOf(
            listOf(
                "bench_press__dumbbells",
                "bench_press__dumbbells",
                "lying_leg_curl__bilateral__machine",
                "no_existe__config",
            ),
            scenario.equipment,
            scenario.availability,
            CatalogCompositionTestSupport.catalog,
        )
        assertEquals("Entradas duplicadas se colapsan", 3, verdicts.size)
        assertTrue(verdicts["bench_press__dumbbells"] is ConfigurationAvailability.Available)
        assertTrue(verdicts["no_existe__config"] is ConfigurationAvailability.Unresolved)
        val curl = verdicts["lying_leg_curl__bilateral__machine"] as ConfigurationAvailability.Missing
        assertEquals(RequirementEvidence.ABSENT, curl.evidence)
        assertTrue("machine" in curl.missing)

        val machines = confirmed(setOf(EquipmentCategory.SUPPORT, EquipmentCategory.MACHINES))
        val hack = configurationAvailability(
            configurationId = "quads_sentadilla_hack__machine",
            equipment = machines.equipment,
            availability = machines.availability,
            catalog = CatalogCompositionTestSupport.catalog,
        )
        assertTrue(
            "Sin la clave exacta se queda en falta confirmar: $hack",
            hack is ConfigurationAvailability.Missing && hack.evidence == RequirementEvidence.UNKNOWN,
        )
    }

    // ─── Materialización de la receta adaptada (§14.4) ──────────────────────

    @Test
    fun adapted_recipe_materializes_with_derived_identity_and_without_bar_load() {
        val scenario = benchWithoutBarbell()
        val original = benchRecipe()
        val adapted = (PlanAdaptationResolver.adapt(scenario.request(original)) as PlanAdaptationResult.Adapted).recipe
        val profile = PowerliftingProfile(bench1RM = 100.0)

        val originalProgram = PlanMaterializer.materialize(
            program = Program(id = "p-orig", name = "Original"),
            recipe = original,
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SeqIds(),
            profile = profile,
            strict = false,
        )
        val barExercise = exercisesOf(originalProgram).single()
        assertEquals("bench_press__barbell", barExercise.catalogConfigurationId)
        assertEquals("TM de barra sobre 100 kg de 1RM", 90.0, barExercise.reference1RM!!, 0.0001)

        val program = PlanMaterializer.materialize(
            program = Program(id = "p-adapted", name = "Adaptado"),
            recipe = adapted,
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SeqIds(),
            profile = profile,
            strict = false,
        )
        val exercise = exercisesOf(program).single()
        assertEquals("bench_press__dumbbells", exercise.catalogConfigurationId)
        assertNull("La mancuerna nunca hereda la base de barra (90) ni el 65 % (65 kg)", exercise.reference1RM)
        assertTrue("Carga pendiente, nunca 0 kg: ${exercise.sets.map { it.weight }}", exercise.sets.all { it.weight == null })
        assertEquals(adapted.id, program.sourceRecipe?.id)
        assertEquals(PlanProvenanceClass.ADAPTED, program.sourceRecipe?.provenance?.category)
        assertEquals(
            1,
            program.sourceRecipe?.provenance?.slotChanges?.size ?: 0,
        )

        // Reconstruir la misma receta derivada no remapea la identidad.
        val rebuilt = PlanMaterializer.materialize(
            program = Program(id = "p-adapted", name = "Adaptado"),
            recipe = adapted,
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SeqIds(),
            profile = profile,
            strict = false,
        )
        assertEquals(sessionsOf(program).map { it.id }, sessionsOf(rebuilt).map { it.id })
        assertEquals(exercisesOf(program).map { it.id }, exercisesOf(rebuilt).map { it.id })
    }
}
