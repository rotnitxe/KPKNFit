package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.onboarding.NativePlanFailureMapper
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.training.CoverageFixtures.EquipmentFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Paquete A · B1 + B2 + B3 + B7 (curaduría de programas, 2026-10-03). JVM puro, con los fixtures E0–E18 de
 * [CoverageFixtures] y el motor real.
 *
 *  - B1: el rechazo de material de un plan propio viaja con `missingRequirements` (tokens) y
 *    [SetupApparatusPanel.keyForToken] los convierte en la llave confirmable del panel.
 *  - B2: los gates de Fuerza y de Fuerza y músculo distinguen `APPARATUS_ABSENT` (negado de forma explícita)
 *    de `APPARATUS_UNKNOWN` (falta confirmar), con el mismo criterio que las recetas fijas. Un gimnasio sin
 *    confirmar, la barra sola y «barra + mancuernas + banco sin rack» ya no se reportan como «declaraste ausente».
 *    Los dos gates se prueban con los 19 fixtures E0–E18, cada uno en exactamente un grupo.
 *  - B3: `hasExplicitMachinePresence` (solo máquinas y poleas activan el modo «configuración exacta»), el tirón
 *    disponible resuelto con las configuraciones reales y el único cambio posible de DEV-r2-06 en E5 y E15: el gemelo
 *    (donde el fitter lo conserva).
 *  - B7: un soporte es `ABSENT` solo si TODAS las llaves que lo acreditan están `ABSENT`.
 *
 * Las filas usan 90 min (el presupuesto de tiempo nunca es la causa), 3 días y nivel intermedio, salvo dos pruebas
 * que varían el dato que juzgan: la fila de principiante con solo barra de dominadas (E13, B3) y la rejilla del
 * gemelo (2 a 4 días, principiante e intermedio; DEV-r2-06).
 */
class NativeMaterialEvidenceTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val generator by lazy { CoverageFixtures.personalizer() }
    private val fixtures: Map<String, EquipmentFixture> = CoverageFixtures.allFixtures().associateBy { it.id }

    private fun fixture(id: String): EquipmentAvailability =
        requireNotNull(fixtures[id]) { "fixture inexistente $id" }.availability

    // ─── Clasificación de la rejilla E0–E18 por gate ───────────────────────────────────────────
    // Cada fixture está en exactamente un grupo por gate (lo verifica
    // `the_gate_groups_classify_every_fixture_of_the_grid_exactly_once`): añadir un fixture a [CoverageFixtures]
    // obliga a decidir qué hace con él cada gate.

    /** Fuerza: sin barra declarada (categoría sin marcar con la disponibilidad confirmada) → ABSENT [barbell]. */
    private val strengthWithoutBarbell = listOf("E0", "E1", "E2", "E3", "E7", "E9", "E10", "E11", "E12", "E13", "E14")

    /** Fuerza: barra, rack y banco acreditados → programa. */
    private val strengthWithBarbellRackAndBench = listOf("E4", "E5", "E6", "E18")

    /** Fuerza: barra presente, rack y banco sin responder → UNKNOWN [rack, bench]. */
    private val strengthRackAndBenchUnconfirmed = listOf("E8", "E16")

    /** Fuerza: barra y bancos confirmados, rack sin declarar → UNKNOWN [rack]. */
    private val strengthRackUnconfirmed = listOf("E17")

    /** Fuerza: gimnasio completo con rack y bancos negados → ABSENT [rack, bench]. */
    private val strengthRackAndBenchDenied = listOf("E15")

    /** Fuerza y músculo: ni barra con rack y banco, ni mancuernas, ni barra sola → PROFILE_MISMATCH. */
    private val powerbuildingWithoutExternalResistance =
        listOf("E0", "E1", "E7", "E9", "E10", "E11", "E12", "E13", "E14")

    /** Fuerza y músculo: ruta de mancuernas, con o sin bancos → programa. */
    private val powerbuildingWithDumbbells = listOf("E2", "E3")

    /** Fuerza y músculo: barra con rack y banco acreditados → programa. */
    private val powerbuildingWithBarbellRackAndBench = listOf("E4", "E5", "E6", "E18")

    /** Fuerza y músculo: mancuernas con rack y banco sin confirmar o negados → programa, nunca bloqueado (T-001). */
    private val powerbuildingDumbbellsNeverBlocked = listOf("E8", "E17", "E15")

    /** Fuerza y músculo: barra sola, rack y banco sin responder → UNKNOWN [rack, bench]. */
    private val powerbuildingBarbellAlone = listOf("E16")

    private fun generate(
        kind: NativeProfileKind,
        availability: EquipmentAvailability?,
        legacyEquipment: Set<String> = emptySet(),
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
        days: Int = 3,
        minutes: Int = 90,
    ): PersonalizationResult = generator.personalize(
        programId = "b-evidence-${kind.name.lowercase()}-$days-$minutes",
        input = PersonalizerInput(
            catalogEntryId = kind.entryId,
            focus = TrainingFocus.FULL_BODY,
            frequency = days,
            weekdays = CoverageFixtures.weekdays(days),
            equipment = legacyEquipment,
            level = level,
            availableMinutes = minutes,
        ),
        options = if (availability != null) TrainingOptions(availability = availability) else TrainingOptions(),
    )

    private fun typedFailureOf(result: PersonalizationResult, context: String): PlanMaterializationException {
        assertNull("$context: no debe haber programa", result.program)
        return requireNotNull(NativePlanFailureMapper.typedFailure(result.report)) {
            "$context: el informe no trae un motivo cerrado (${result.report.reasonCode}): ${result.report.limitations}"
        }
    }

    private fun assertProgram(result: PersonalizationResult, context: String) {
        assertNotNull(
            "$context: debe haber programa (${result.report.reasonCode}): ${result.report.limitations}",
            result.program,
        )
        assertNull("$context: un programa no lleva motivo de rechazo", result.report.reasonCode)
        assertTrue("$context: un programa no lleva requisitos pendientes", result.report.missingRequirements.isEmpty())
    }

    private fun assertRejected(
        result: PersonalizationResult,
        context: String,
        reasonCode: String,
        missing: List<String>?,
    ) {
        val failure = typedFailureOf(result, context)
        assertEquals("$context: reasonCode", reasonCode, result.report.reasonCode)
        val (stage, reason) = when (reasonCode) {
            "APPARATUS_ABSENT" -> PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_ABSENT
            "APPARATUS_UNKNOWN" -> PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_UNKNOWN
            "PROFILE_MISMATCH" -> PlanEvaluationStage.PROFILE to PlanRejectionReason.PROFILE_MISMATCH
            else -> error("motivo no contemplado en la prueba: $reasonCode")
        }
        assertEquals("$context: etapa tipada", stage, failure.stage)
        assertEquals("$context: motivo tipado", reason, failure.reason)
        if (missing != null) {
            assertEquals("$context: missingRequirements del informe", missing, result.report.missingRequirements)
            assertEquals("$context: missingRequirements de la excepción tipada", missing, failure.missingRequirements)
        }
        val message = result.report.limitations.joinToString(" ")
        assertFalse("$context: el texto de usuario no lleva tokens internos: $message", '_' in message || "machine_config" in message)
        if (reasonCode == "APPARATUS_ABSENT" || reasonCode == "APPARATUS_UNKNOWN") {
            // La ruta heredada de la UI (`SetupWizardViewModel.missingEquipmentTokens`) toma lo que sigue al primer «:»
            // como una lista de tokens separados por comas: un mensaje de aparatos con «:» se leería como aparatos inventados.
            assertFalse("$context: el mensaje de aparatos no lleva «:» (la ruta heredada lo lee como tokens): $message", ':' in message)
        }
    }

    private fun exercisesOf(program: Program) = program.macrocycles
        .flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
        .flatMap { it.allExercises() }

    // ─── B2 · Fuerza ──────────────────────────────────────────────────────────────────────

    @Test
    fun strength_without_a_declared_barbell_is_an_explicit_absence_and_lists_only_what_was_denied() {
        strengthWithoutBarbell.forEach { id ->
            val result = generate(NativeProfileKind.STRENGTH, fixture(id))
            // El rack y el banco sin responder (o ya confirmados) NO se cuentan como ausentes: solo la barra,
            // que es una categoría sin marcar con la disponibilidad confirmada.
            assertRejected(result, "STRENGTH/$id", "APPARATUS_ABSENT", missing = listOf("barbell"))
            assertTrue(
                "STRENGTH/$id: el texto nombra la barra: ${result.report.limitations}",
                result.report.limitations.any { it.contains("barra") },
            )
        }
    }

    @Test
    fun strength_with_barbell_rack_and_bench_confirmed_gets_a_program() {
        strengthWithBarbellRackAndBench.forEach { id ->
            assertProgram(generate(NativeProfileKind.STRENGTH, fixture(id)), "STRENGTH/$id")
        }
    }

    @Test
    fun strength_with_supports_still_unconfirmed_asks_to_confirm_instead_of_blaming_the_user() {
        // E8: gimnasio completo sin confirmar soportes. E16: barra sola.
        strengthRackAndBenchUnconfirmed.forEach { id ->
            val result = generate(NativeProfileKind.STRENGTH, fixture(id))
            assertRejected(result, "STRENGTH/$id", "APPARATUS_UNKNOWN", missing = listOf("rack", "bench"))
            assertTrue(
                "STRENGTH/$id: el texto pide confirmar rack y banco: ${result.report.limitations}",
                result.report.limitations.any { it.contains("Falta confirmar") && it.contains("rack") && it.contains("banco") },
            )
        }
        // E17: barra, mancuernas y bancos confirmados; solo falta el rack.
        strengthRackUnconfirmed.forEach { id ->
            val result = generate(NativeProfileKind.STRENGTH, fixture(id))
            assertRejected(result, "STRENGTH/$id", "APPARATUS_UNKNOWN", missing = listOf("rack"))
            assertTrue(
                "STRENGTH/$id: el texto pide solo el rack: ${result.report.limitations}",
                result.report.limitations.any { it.contains("rack") && !it.contains("banco") },
            )
        }
    }

    @Test
    fun strength_with_rack_and_bench_explicitly_denied_is_an_absence_with_evidence() {
        // E15: gimnasio completo, pero rack y bancos negados.
        strengthRackAndBenchDenied.forEach { id ->
            val result = generate(NativeProfileKind.STRENGTH, fixture(id))
            assertRejected(result, "STRENGTH/$id", "APPARATUS_ABSENT", missing = listOf("rack", "bench"))
            assertTrue(
                "STRENGTH/$id: el texto nombra rack y banco: ${result.report.limitations}",
                result.report.limitations.any { it.contains("Falta rack, banco") },
            )
        }
    }

    @Test
    fun a_missing_barbell_is_reported_as_absent_even_when_rack_and_bench_are_also_unknown() {
        // Una ausencia declarada manda sobre lo desconocido; la lista solo trae lo negado.
        val availability = EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT))
        val result = generate(NativeProfileKind.STRENGTH, availability)
        assertRejected(result, "STRENGTH/sin barra", "APPARATUS_ABSENT", missing = listOf("barbell"))
    }

    // ─── B2 · Fuerza y músculo ──────────────────────────────────────────────────────────────

    @Test
    fun powerbuilding_with_neither_barbell_nor_dumbbells_stays_a_profile_mismatch() {
        // E0 nada, E1 banda, E7 soportes y barra de dominadas, E9 máquinas y poleas, E10 kettlebell, E11 Smith y banco,
        // E12 banda y barra de dominadas, E13 barra de dominadas, E14 máquinas, poleas y bici: sin resistencia externa.
        powerbuildingWithoutExternalResistance.forEach { id ->
            val result = generate(NativeProfileKind.POWERBUILDING, fixture(id))
            assertRejected(result, "POWERBUILDING/$id", "PROFILE_MISMATCH", missing = null)
            assertTrue(
                "POWERBUILDING/$id no lleva requisitos de aparato: ${result.report.missingRequirements}",
                result.report.missingRequirements.isEmpty(),
            )
        }
    }

    @Test
    fun powerbuilding_with_a_barbell_alone_asks_to_confirm_rack_and_bench() {
        powerbuildingBarbellAlone.forEach { id ->
            val result = generate(NativeProfileKind.POWERBUILDING, fixture(id))
            assertRejected(result, "POWERBUILDING/$id", "APPARATUS_UNKNOWN", missing = listOf("rack", "bench"))
            assertTrue(
                "POWERBUILDING/$id: el texto reconoce la barra y pide rack y banco: ${result.report.limitations}",
                result.report.limitations.any { it.contains("Tienes barra, pero falta confirmar") && it.contains("rack y banco") },
            )
        }
    }

    @Test
    fun powerbuilding_with_a_barbell_and_rack_but_the_bench_unanswered_asks_only_for_the_bench() {
        // Barra y rack acreditados, banco sin responder y sin mancuernas: un solo requisito por confirmar.
        val availability = EquipmentAvailability(
            categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT),
            supports = mapOf(EquipmentKeys.SQUAT_RACK to ApparatusPresence.PRESENT),
        )
        val result = generate(NativeProfileKind.POWERBUILDING, availability)
        assertRejected(
            result,
            "POWERBUILDING/barra y rack, banco sin responder",
            "APPARATUS_UNKNOWN",
            missing = listOf("bench"),
        )
        assertTrue(
            "el texto reconoce la barra y pide solo el banco: ${result.report.limitations}",
            result.report.limitations.any {
                it.contains("Tienes barra, pero falta confirmar") && it.contains("banco") && !it.contains("rack")
            },
        )
        // El requisito resuelve una llave del panel que la persona puede confirmar (el banco plano).
        assertEquals(EquipmentKeys.BENCH_FLAT, SetupApparatusPanel.keyForToken(result.report.missingRequirements.single()))
    }

    @Test
    fun powerbuilding_with_a_barbell_but_rack_or_bench_denied_and_no_dumbbells_is_a_profile_mismatch() {
        val rackDenied = EquipmentAvailability(
            categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT),
            supports = mapOf(
                EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
                EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT,
            ),
        )
        assertRejected(
            generate(NativeProfileKind.POWERBUILDING, rackDenied),
            "POWERBUILDING/barra sin rack",
            "PROFILE_MISMATCH",
            missing = null,
        )
    }

    @Test
    fun powerbuilding_with_dumbbells_or_the_full_barbell_triple_gets_a_program() {
        // E2/E3: mancuernas (con o sin bancos). E4/E5/E6/E18: barra con rack y banco confirmados.
        (powerbuildingWithDumbbells + powerbuildingWithBarbellRackAndBench).forEach { id ->
            assertProgram(generate(NativeProfileKind.POWERBUILDING, fixture(id)), "POWERBUILDING/$id")
        }
    }

    /**
     * Guardia de T-001 (gimnasio completo + «Fuerza y músculo» no puede volver a quedar bloqueado): con mancuernas
     * el plan se entrega por la ruta de mancuernas aunque el rack y el banco sigan sin confirmar. Es el único
     * candidato viable de esa fila de la matriz (los PHAT quedan rechazados por «bench»), así que preguntar aquí
     * por rack y banco la dejaría sin ningún plan. DESVIACIÓN del brief A.B2, que pedía APPARATUS_UNKNOWN para E17.
     */
    @Test
    fun powerbuilding_with_dumbbells_is_never_blocked_by_unconfirmed_rack_and_bench() {
        // E8: gimnasio completo sin confirmar. E17: barra + mancuernas + bancos, sin rack declarado.
        // E15: gimnasio completo con rack y bancos negados.
        powerbuildingDumbbellsNeverBlocked.forEach { id ->
            assertProgram(generate(NativeProfileKind.POWERBUILDING, fixture(id)), "POWERBUILDING/$id")
        }
    }

    /**
     * Guardia de cobertura de los dos gates: los grupos de arriba reparten E0–E18 sin dejar ningún fixture fuera ni
     * repetirlo, así que un fixture nuevo en [CoverageFixtures] rompe esta prueba hasta que se clasifique.
     */
    @Test
    fun the_gate_groups_classify_every_fixture_of_the_grid_exactly_once() {
        val strength = strengthWithoutBarbell + strengthWithBarbellRackAndBench + strengthRackAndBenchUnconfirmed +
            strengthRackUnconfirmed + strengthRackAndBenchDenied
        val powerbuilding = powerbuildingWithoutExternalResistance + powerbuildingWithDumbbells +
            powerbuildingWithBarbellRackAndBench + powerbuildingDumbbellsNeverBlocked + powerbuildingBarbellAlone
        mapOf("STRENGTH" to strength, "POWERBUILDING" to powerbuilding).forEach { (gate, ids) ->
            assertEquals("$gate: ningún fixture repetido en dos grupos: $ids", ids.size, ids.toSet().size)
            assertEquals("$gate: los grupos cubren E0–E18 completo", fixtures.keys, ids.toSet())
        }
        assertEquals("la rejilla son 19 fixtures, E0–E18", 19, fixtures.size)
    }

    // ─── Ruta legacy (sin disponibilidad confirmada): se conserva lo que había ──────────────────────

    @Test
    fun legacy_profile_keeps_its_old_gate_without_inventing_unknowns() {
        // `general_gym` a secas no acredita barra, rack ni banco, y sin disponibilidad no existe «sin confirmar».
        val gym = generate(NativeProfileKind.STRENGTH, null, legacyEquipment = setOf("general_gym"))
        assertRejected(gym, "legacy general_gym", "APPARATUS_ABSENT", missing = listOf("barbell", "rack", "bench"))
        assertTrue(
            gym.report.limitations.joinToString(" "),
            gym.report.limitations.any { it.contains("barra") && it.contains("rack") && it.contains("banco") },
        )

        assertProgram(
            generate(NativeProfileKind.STRENGTH, null, legacyEquipment = setOf("bodyweight", "barbell", "rack", "bench")),
            "legacy barra + rack + banco",
        )
        // El chip legacy `support` atestigua rack y banco (paraguas de §13.1 regla 4).
        assertProgram(
            generate(NativeProfileKind.STRENGTH, null, legacyEquipment = setOf("bodyweight", "barbell", "support")),
            "legacy barra + soportes",
        )
        // Barra sin soportes y sin mancuernas: sin respuestas «sin confirmar» en esta ruta, es un desajuste de perfil.
        assertRejected(
            generate(NativeProfileKind.POWERBUILDING, null, legacyEquipment = setOf("bodyweight", "barbell")),
            "legacy POWERBUILDING barra sola",
            "PROFILE_MISMATCH",
            missing = null,
        )
        assertProgram(
            generate(NativeProfileKind.POWERBUILDING, null, legacyEquipment = setOf("bodyweight", "dumbbells")),
            "legacy POWERBUILDING mancuernas",
        )
    }

    // ─── B1 · del token a la llave confirmable ─────────────────────────────────────────────

    @Test
    fun every_rejection_token_of_the_own_plans_resolves_to_a_confirmable_key_or_to_a_category() {
        val unknownCases = listOf("E8", "E16", "E17").map { it to generate(NativeProfileKind.STRENGTH, fixture(it)) } +
            listOf("E16").map { it to generate(NativeProfileKind.POWERBUILDING, fixture(it)) }
        unknownCases.forEach { (id, result) ->
            assertEquals("$id", "APPARATUS_UNKNOWN", result.report.reasonCode)
            assertTrue("$id: lista no vacía", result.report.missingRequirements.isNotEmpty())
            result.report.missingRequirements.forEach { token ->
                assertNotNull("$id: el token «$token» debe resolver una llave del panel", SetupApparatusPanel.keyForToken(token))
            }
        }
        // El token de la barra es una categoría: no tiene una llave que confirmar.
        assertNull(SetupApparatusPanel.keyForToken("barbell"))
    }

    @Test
    fun key_for_token_maps_the_vocabulary_to_the_curated_panel_keys() {
        assertEquals(EquipmentKeys.SQUAT_RACK, SetupApparatusPanel.keyForToken("rack"))
        // `bench` lo acreditan el banco plano y el regulable; se pide el primero del panel.
        assertEquals(EquipmentKeys.BENCH_FLAT, SetupApparatusPanel.keyForToken("bench"))
        assertEquals(EquipmentKeys.BENCH_ADJUSTABLE, SetupApparatusPanel.keyForToken("bench_incline"))
        assertEquals(EquipmentKeys.PULLUP_BAR, SetupApparatusPanel.keyForToken("pull_up_bar"))
        assertEquals(EquipmentKeys.DIP_BARS, SetupApparatusPanel.keyForToken("dip_bars"))
        assertEquals(EquipmentKeys.LOW_BAR_SUPPORT, SetupApparatusPanel.keyForToken("low_bar_support"))
        assertEquals(EquipmentKeys.EZ_BAR, SetupApparatusPanel.keyForToken("ez_bar"))
        // Máquinas: por token `machine_config:<id>` o por el id suelto de la configuración.
        assertEquals(EquipmentKeys.LEG_PRESS, SetupApparatusPanel.keyForToken("machine_config:quads_prensa_piernas__bilateral"))
        assertEquals(EquipmentKeys.LEG_PRESS, SetupApparatusPanel.keyForToken("quads_prensa_piernas__bilateral"))
        assertEquals(EquipmentKeys.CABLE_HIGH_LOW, SetupApparatusPanel.keyForToken("machine_config:lat_pulldown__bilateral__cable"))
        // Sin llave: `machine`, categorías, requisitos que el panel no puede acreditar y tokens desconocidos.
        listOf("machine", "barbell", "dumbbells", "kettlebell", "band", "support", "ball", "nordic_anchor", "token_inventado")
            .forEach { token -> assertNull("«$token» no tiene llave que confirmar", SetupApparatusPanel.keyForToken(token)) }
    }

    @Test
    fun categories_for_keys_returns_the_categories_that_make_them_visible() {
        assertEquals(setOf(EquipmentCategory.SUPPORT), SetupApparatusPanel.categoriesFor(listOf(EquipmentKeys.SQUAT_RACK, EquipmentKeys.BENCH_FLAT)))
        assertEquals(
            setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR),
            SetupApparatusPanel.categoriesFor(listOf(EquipmentKeys.SQUAT_RACK, EquipmentKeys.PULLUP_BAR)),
        )
        assertEquals(
            setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE),
            SetupApparatusPanel.categoriesFor(listOf(EquipmentKeys.LEG_PRESS, EquipmentKeys.CABLE_HIGH_LOW)),
        )
        // La barra EZ no tiene categoría propia: cuenta como soporte.
        assertEquals(setOf(EquipmentCategory.SUPPORT), SetupApparatusPanel.categoriesFor(listOf(EquipmentKeys.EZ_BAR)))
        // Llaves que no existen se ignoran.
        assertEquals(emptySet<EquipmentCategory>(), SetupApparatusPanel.categoriesFor(listOf("llave_inventada")))
        assertEquals(emptySet<EquipmentCategory>(), SetupApparatusPanel.categoriesFor(emptyList()))
    }

    // ─── B7 · evidencia ANY → ALL ───────────────────────────────────────────────────────────

    private fun requirementsOf(availability: EquipmentAvailability) =
        TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet())

    @Test
    fun a_flat_bench_denied_with_the_adjustable_one_unanswered_is_unknown_not_absent() {
        val result = requirementsOf(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT),
            ),
        )
        assertEquals(RequirementEvidence.UNKNOWN, result.requirements["bench"])
        assertTrue("bench" in result.unknownRequirements)
        assertFalse("bench" in result.missingRequirements)
        // `bench_incline` solo lo acredita el regulable, que sigue sin responder.
        assertEquals(RequirementEvidence.UNKNOWN, result.requirements["bench_incline"])
    }

    @Test
    fun a_bench_is_absent_only_when_every_key_that_attests_it_is_denied() {
        val both = requirementsOf(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                supports = mapOf(
                    EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
                    EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.ABSENT,
                ),
            ),
        )
        assertEquals(RequirementEvidence.ABSENT, both.requirements["bench"])
        assertEquals(RequirementEvidence.ABSENT, both.requirements["bench_incline"])
        assertTrue("bench" in both.missingRequirements)

        // Un rack tiene una sola llave: negarla basta.
        val rack = requirementsOf(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                supports = mapOf(EquipmentKeys.SQUAT_RACK to ApparatusPresence.ABSENT),
            ),
        )
        assertEquals(RequirementEvidence.ABSENT, rack.requirements["rack"])

        // Con el regulable presente el banco está acreditado aunque el plano se haya negado.
        val adjustable = requirementsOf(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT),
                supports = mapOf(
                    EquipmentKeys.BENCH_FLAT to ApparatusPresence.ABSENT,
                    EquipmentKeys.BENCH_ADJUSTABLE to ApparatusPresence.PRESENT,
                ),
            ),
        )
        assertEquals(RequirementEvidence.PRESENT, adjustable.requirements["bench"])
    }

    // ─── B3 · presencia explícita de máquinas y tirón resuelto ───────────────────────────────

    @Test
    fun explicit_machine_presence_counts_only_machine_and_cable_keys() {
        assertFalse("E5 solo confirma soportes", fixture("E5").hasExplicitMachinePresence())
        listOf("E6", "E9", "E14", "E18").forEach { id ->
            assertTrue("$id declara máquinas o poleas", fixture(id).hasExplicitMachinePresence())
        }
        listOf("E0", "E1", "E2", "E3", "E4", "E7", "E8", "E10", "E11", "E12", "E13", "E15", "E16", "E17").forEach { id ->
            assertFalse("$id no declara ninguna máquina ni polea", fixture(id).hasExplicitMachinePresence())
        }

        // La bici exterior no es una llave del panel: activa `hasExplicitPresence` pero no el modo exacto.
        val bikeOnly = EquipmentAvailability(
            categories = setOf(EquipmentCategory.CARDIO),
            apparatus = mapOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY to ApparatusPresence.PRESENT),
        )
        assertTrue(bikeOnly.hasExplicitPresence)
        assertFalse(bikeOnly.hasExplicitMachinePresence())

        // Los soportes y la barra de dominadas tampoco cuentan.
        assertFalse(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR),
                supports = mapOf(
                    EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT,
                    EquipmentKeys.PULLUP_BAR to ApparatusPresence.ABSENT,
                ),
            ).hasExplicitMachinePresence(),
        )
        // Un «No» a una máquina o a una polea sí es una declaración explícita; un UNKNOWN no.
        assertTrue(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.CABLE),
                apparatus = mapOf(EquipmentKeys.ROPE_ATTACHMENT to ApparatusPresence.ABSENT),
            ).hasExplicitMachinePresence(),
        )
        assertFalse(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.MACHINES),
                apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.UNKNOWN),
            ).hasExplicitMachinePresence(),
        )
        assertFalse(EquipmentAvailability().hasExplicitMachinePresence())
    }

    /**
     * DEV-r2-06: con las máquinas solo por categoría y los soportes confirmados, el plan propio ya no entra en el
     * modo «configuración exacta» y puede usar una máquina aprobada (remo o jalón en máquina) para el tirón. Con una
     * máquina concreta declarada sí se exige la configuración exacta, y esa máquina no habilita el remo ni el jalón.
     */
    @Test
    fun machines_by_category_feed_the_pull_of_own_plans_unless_a_concrete_machine_is_declared() {
        val machinesAndBench = EquipmentAvailability(
            categories = setOf(EquipmentCategory.MACHINES, EquipmentCategory.SUPPORT),
            supports = mapOf(EquipmentKeys.BENCH_FLAT to ApparatusPresence.PRESENT),
        )
        val pullNote = "Sin banda o barra de apoyo"
        val pullConfigurations = setOf("chest_supported_row__machine__medium", "lat_pulldown__bilateral__machine")

        val byCategory = generate(NativeProfileKind.MUSCLE, machinesAndBench)
        assertProgram(byCategory, "MUSCLE/máquinas por categoría")
        val byCategoryProgram = requireNotNull(byCategory.program)
        assertFalse(
            "con una máquina de tirón por categoría hay tirón: ${byCategoryProgram.description}",
            byCategoryProgram.description.orEmpty().contains(pullNote),
        )
        assertTrue(
            "el plan usa el remo o el jalón en máquina: ${exercisesOf(byCategoryProgram).mapNotNull { it.catalogConfigurationId }.toSet()}",
            exercisesOf(byCategoryProgram).any { it.catalogConfigurationId in pullConfigurations },
        )

        // Control: una máquina concreta declarada (la prensa) exige configuración exacta; la prensa no da tirón.
        val exact = generate(
            NativeProfileKind.MUSCLE,
            machinesAndBench.copy(apparatus = mapOf(EquipmentKeys.LEG_PRESS to ApparatusPresence.PRESENT)),
        )
        assertProgram(exact, "MUSCLE/máquina concreta declarada")
        val exactProgram = requireNotNull(exact.program)
        assertTrue(
            "con configuración exacta y solo la prensa no hay tirón: ${exactProgram.description}",
            exactProgram.description.orEmpty().contains(pullNote),
        )
        assertTrue(
            "ninguna máquina de tirón sin su llave",
            exercisesOf(exactProgram).none { it.catalogConfigurationId in pullConfigurations },
        )
    }

    /** Lo que lleva el plan Músculo de una celda (días y nivel): sus gemelos y los accesorios que el fitter retiró. */
    private data class CalfCell(val calves: Set<String>, val retiredByFitter: List<String>)

    /**
     * DEV-r2-06, efecto en E5 y E15 (todas las categorías con soportes confirmados o negados y ninguna máquina
     * declarada): el único cambio posible es el gemelo. El slot G lista `calf_raise__bilateral__machine` ANTES que la
     * reserva corporal (`NativeCandidateTable.candidatesFor(G)`), así que con las máquinas por categoría el gemelo pasa
     * de la variante corporal a la máquina. Con una máquina concreta declarada que NO acredita el gemelo de pie (E18:
     * solo la prensa) rige el modo exacto y el gemelo se queda en la reserva corporal.
     *
     * El gemelo es un accesorio opcional (`G:I`) y el fitter MRV no distingue por músculo: cuando algún músculo pasa
     * su MRV retira los accesorios opcionales. Con 3 días, 90 min e intermedio el plan de E5 no lleva gemelo (medido
     * el 2026-10-03: la primera versión de esta prueba lo exigía en esa celda y falló con la lista vacía). Por eso
     * la prueba recorre una rejilla pequeña de días y niveles y exige (a) que ninguna celda use la variante
     * equivocada y (b) que en alguna celda el gemelo sobreviva, para que (a) no sea vacua.
     */
    @Test
    fun calf_moves_to_the_machine_by_category_when_only_supports_were_confirmed() {
        val machineCalf = "calf_raise__bilateral__machine"
        val bodyCalf = "calf_raise__bilateral__bodyweight"

        // Las celdas sin programa se saltan: este test no juzga la viabilidad, solo qué gemelo lleva el plan.
        fun calfCells(fixtureId: String): Map<String, CalfCell> {
            val cells = linkedMapOf<String, CalfCell>()
            for (level in listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE)) {
                for (days in 2..4) {
                    val program = generate(NativeProfileKind.MUSCLE, fixture(fixtureId), level = level, days = days).program
                        ?: continue
                    val calves = exercisesOf(program)
                        .mapNotNull { it.catalogConfigurationId }
                        .filter { it == machineCalf || it == bodyCalf }
                        .toSet()
                    val retired = program.description.orEmpty().lines().filter { "retirado por presupuesto" in it }
                    cells["${days}d/${level.name}"] = CalfCell(calves, retired)
                }
            }
            return cells
        }

        fun calvesSeen(cells: Map<String, CalfCell>): Set<String> = cells.values.flatMap { it.calves }.toSet()

        // E5: soportes confirmados y máquinas sin responder. E15: lo mismo con el rack y los bancos negados.
        listOf("E5", "E15").forEach { id ->
            val cells = calfCells(id)
            assertTrue(
                "MUSCLE/$id: el gemelo sobrevive en alguna celda y es la máquina por categoría: $cells",
                machineCalf in calvesSeen(cells),
            )
            assertFalse(
                "MUSCLE/$id: ninguna celda deja el gemelo en la reserva corporal: $cells",
                bodyCalf in calvesSeen(cells),
            )
        }

        // Control: E18 declara solo la prensa (modo exacto). La prensa acredita el gemelo EN la prensa, no el de pie.
        val exact = calfCells("E18")
        assertTrue(
            "MUSCLE/E18: el gemelo sobrevive en alguna celda y es la reserva corporal (si no, la ausencia de la máquina no probaría nada): $exact",
            bodyCalf in calvesSeen(exact),
        )
        assertFalse(
            "MUSCLE/E18: sin su llave, la máquina de gemelos de pie no entra en ninguna celda: $exact",
            machineCalf in calvesSeen(exact),
        )
    }

    @Test
    fun pull_is_resolved_from_the_real_configurations_of_each_material() {
        val pullNote = "Sin banda o barra de apoyo"
        // Sin ningún material no hay tirón (DEV-r2-01): el calendario «sin tirón» se conserva.
        val none = requireNotNull(generate(NativeProfileKind.MUSCLE, fixture("E0")).program)
        assertTrue(none.description.orEmpty().contains(pullNote))
        // Banda (E1), soportes con barra de dominadas y barra baja (E7) y barra de dominadas (E13, intermedio).
        listOf("E1", "E7", "E13").forEach { id ->
            val program = requireNotNull(generate(NativeProfileKind.MUSCLE, fixture(id)).program) { "MUSCLE/$id sin programa" }
            assertFalse(
                "MUSCLE/$id conserva el tirón: ${program.description}",
                program.description.orEmpty().contains(pullNote),
            )
        }
        // Un principiante con SOLO la barra de dominadas (E13) no tiene ningún tirón resoluble: r2 §13.3 no le da
        // dominadas por defecto y no hay barra baja. Antes `pull_up_bar` bastaba para anunciar tirón, el calendario
        // llevaba remos que no se podían resolver y el plan caía en COMPOSITION; ahora usa el calendario «sin tirón».
        val beginner = requireNotNull(generate(NativeProfileKind.MUSCLE, fixture("E13"), level = CatalogLevel.BEGINNER).program) {
            "MUSCLE/E13 principiante sin programa"
        }
        assertTrue(beginner.description.orEmpty().contains(pullNote))
    }
}
