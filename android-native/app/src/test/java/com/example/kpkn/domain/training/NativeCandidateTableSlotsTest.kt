package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.definitions.NativeCandidateTable
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Paquete A · B5 (curaduría de programas, 2026-10-03; DEC-w2-04 parte 1): la tabla de candidatos de los
 * planes propios ([NativeCandidateTable]) ya no deja a la kettlebell, la Smith, las mancuernas sin banco
 * ni la banda con barra de dominadas en el peso corporal.
 *
 * Fija cuatro cosas: (1) cada id de cada (slot, intención) existe y está APPROVED en el catálogo
 * compilado; (2) un id con sufijo de material (`__kettlebell`, `__smith_machine`, `__band`…) coincide con el
 * `equipmentId` del catálogo; (3) el orden de las altas (después de las variantes con barra, mancuerna,
 * polea y máquina, y antes de la banda y de la reserva corporal; la excepción de V está en el KDoc de
 * [NativeCandidateTable]); y (4) con los fixtures de material del contrato de cobertura (E10 kettlebell,
 * E11 Smith y banco, E12 banda y barra de dominadas) el generador real las usa, con sus soportes (B4).
 */
class NativeCandidateTableSlotsTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        /** Sufijos de id que declaran el implemento y el `equipmentId` que el catálogo debe publicar para ellos. */
        private val SUFFIX_EQUIPMENT: Map<String, String> = mapOf(
            "__kettlebell" to "kettlebell",
            "__smith_machine" to "smith_machine",
            "__band" to "band",
            "__dumbbells" to "dumbbells",
            "__barbell" to "barbell",
            "__cable" to "cable",
            "__machine" to "machine",
            "__bodyweight" to "bodyweight",
        )
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private val configurations by lazy {
        catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
    }

    private fun allCandidates(): List<Triple<NativeSlotKey, SlotIntent, String>> =
        NativeSlotKey.entries.flatMap { key ->
            SlotIntent.entries.flatMap { intent ->
                NativeCandidateTable.candidatesFor(key, intent).map { id -> Triple(key, intent, id) }
            }
        }

    // ─── (1) y (2): ids reales y coherentes con su material ──────────────────────────────

    @Test
    fun every_candidate_of_every_slot_and_intent_exists_and_is_approved_in_the_compiled_catalog() {
        val candidates = allCandidates()
        assertTrue("la tabla no puede estar vacía", candidates.isNotEmpty())
        val problems = candidates.mapNotNull { (key, intent, id) ->
            val configuration = configurations[id]
            when {
                configuration == null -> "$key/$intent: $id no existe en el catálogo compilado"
                configuration.evidence.reviewStatus != CatalogReviewStatusV2.APPROVED ->
                    "$key/$intent: $id no está APPROVED (${configuration.evidence.reviewStatus})"
                else -> null
            }
        }.distinct()
        assertTrue("candidatos sin respaldo en el catálogo:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun a_candidate_with_a_material_suffix_matches_the_catalog_equipment_id() {
        val problems = allCandidates().mapNotNull { (key, intent, id) ->
            val expected = SUFFIX_EQUIPMENT.entries.firstOrNull { (suffix, _) -> id.endsWith(suffix) }?.value
                ?: return@mapNotNull null
            val actual = configurations.getValue(id).profile.equipmentId
            if (actual == expected) null else "$key/$intent: $id declara «$expected» y el catálogo publica «$actual»"
        }.distinct()
        assertTrue("sufijo de material incoherente con el catálogo:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    // ─── (3): las altas de B5 y su sitio en cada lista ───────────────────────────────────

    /** Un alta de B5: debe aparecer en TODAS las intenciones del slot, después de [after] y antes de [before]. */
    private data class Addition(
        val key: NativeSlotKey,
        val id: String,
        val after: String,
        val before: String? = null,
    )

    private val additions = listOf(
        // S: sentadilla en Smith y frontal con kettlebell, tras la copa con mancuerna y antes de la reserva corporal.
        Addition(NativeSlotKey.S, "high_bar_back_squat__smith_machine", "quads_sentadilla_copa__default", "front_squat__kettlebell"),
        Addition(NativeSlotKey.S, "front_squat__kettlebell", "high_bar_back_squat__smith_machine", "quads_sentadilla_sin_carga__default"),
        // B y PB: floor press con mancuernas (sin banco) y banca Smith (con banco, B4), antes de la flexión de rodillas.
        Addition(NativeSlotKey.B, "floor_press__dumbbells", "floor_press__barbell", "bench_press__smith_machine"),
        Addition(NativeSlotKey.B, "bench_press__smith_machine", "floor_press__dumbbells", "knee_push_up__default"),
        Addition(NativeSlotKey.PB, "floor_press__dumbbells", "floor_press__barbell", "bench_press__smith_machine"),
        Addition(NativeSlotKey.PB, "bench_press__smith_machine", "floor_press__dumbbells", "knee_push_up__default"),
        // D: rumano en Smith y hip thrust con banda (con banco, B4), antes del puente corporal.
        Addition(NativeSlotKey.D, "romanian_deadlift__bilateral__smith_machine", "romanian_deadlift__bilateral__dumbbells", "hip_thrust__bilateral__band"),
        Addition(NativeSlotKey.D, "hip_thrust__bilateral__band", "romanian_deadlift__bilateral__smith_machine", "glutes_puente_gluteos__bilateral__bodyweight"),
        // R: remo en Smith y con kettlebell, tras el remo en máquina y antes del remo con banda.
        Addition(NativeSlotKey.R, "conventional_row__smith_machine", "chest_supported_row__machine__medium", "back_remo_banda__default"),
        Addition(NativeSlotKey.R, "conventional_row__kettlebell", "conventional_row__smith_machine", "back_remo_banda__default"),
        // V: el jalón con banda (barra de dominadas, B4) es tirón vertical: detrás de las dominadas y delante de los remos
        // de respaldo; los remos Smith y kettlebell, detrás del remo con barra y delante del remo con banda.
        Addition(NativeSlotKey.V, "lat_pulldown__bilateral__band", "pull_up__pronated__medium", "back_remo_invertido__default"),
        Addition(NativeSlotKey.V, "conventional_row__smith_machine", "conventional_row__barbell", "back_remo_banda__default"),
        Addition(NativeSlotKey.V, "conventional_row__kettlebell", "conventional_row__smith_machine", "back_remo_banda__default"),
        // O: press militar en Smith y con kettlebell, tras la máquina y antes de la flexión.
        Addition(NativeSlotKey.O, "military_press__smith_machine", "military_press__machine", "military_press__kettlebell"),
        Addition(NativeSlotKey.O, "military_press__kettlebell", "military_press__smith_machine", "knee_push_up__default"),
        // U, L, A: la kettlebell va la última de las variantes con material.
        Addition(NativeSlotKey.U, "walking_lunge__kettlebell", "walking_lunge__dumbbells", "reverse_lunge__bodyweight"),
        Addition(NativeSlotKey.L, "standing_lateral_raise__kettlebell", "standing_lateral_raise__cable"),
        Addition(NativeSlotKey.A, "hammer_curl__kettlebell", "preacher_curl__machine"),
        // T: extensión sobre la cabeza con mancuerna y press francés con kettlebell, tras la máquina y antes de la reserva corporal.
        Addition(NativeSlotKey.T, "overhead_triceps__dumbbells", "triceps_pushdown__bilateral__machine", "triceps_press_frances__kettlebell"),
        Addition(NativeSlotKey.T, "triceps_press_frances__kettlebell", "overhead_triceps__dumbbells", "triceps_flexiones_esfinge__default"),
    )

    @Test
    fun the_b5_additions_are_in_every_intent_after_the_existing_material_variants_and_before_the_bodyweight_reserve() {
        val problems = additions.flatMap { addition ->
            SlotIntent.entries.mapNotNull { intent ->
                val list = NativeCandidateTable.candidatesFor(addition.key, intent)
                val index = list.indexOf(addition.id)
                val afterIndex = list.indexOf(addition.after)
                val beforeIndex = addition.before?.let(list::indexOf)
                when {
                    index < 0 -> "${addition.key}/$intent: falta ${addition.id}"
                    afterIndex < 0 || afterIndex >= index ->
                        "${addition.key}/$intent: ${addition.id} debe ir después de ${addition.after} (lista $list)"
                    beforeIndex != null && (beforeIndex < 0 || beforeIndex <= index) ->
                        "${addition.key}/$intent: ${addition.id} debe ir antes de ${addition.before} (lista $list)"
                    else -> null
                }
            }
        }
        assertTrue("altas de B5 mal colocadas:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun the_b5_additions_do_not_add_a_second_copy_of_any_candidate() {
        allCandidates().groupBy { (key, intent, _) -> key to intent }.forEach { (slot, rows) ->
            val ids = rows.map { it.third }
            assertEquals("ids repetidos en $slot", ids.size, ids.toSet().size)
        }
    }

    // ─── (4): el generador real las usa con el material del contrato de cobertura ─────────

    private val levels = listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED)

    /** Fixtures E10, E11 y E12 del contrato de cobertura (`CoverageFixtures`), por id. */
    private fun fixture(id: String): EquipmentAvailability =
        CoverageFixtures.extendedFixtures().first { it.id == id }.availability

    private fun generateMuscle(
        availability: EquipmentAvailability,
        level: CatalogLevel,
        days: Int = 3,
        minutes: Int = 60,
    ): PersonalizationResult = CoverageFixtures.personalizer().personalize(
        programId = "slots-${level.name.lowercase()}-$days-$minutes-${availability.hashCode().toUInt().toString(16)}",
        input = PersonalizerInput(
            catalogEntryId = NativeProfileKind.MUSCLE.entryId,
            focus = TrainingFocus.FULL_BODY,
            frequency = days,
            weekdays = CoverageFixtures.weekdays(days),
            equipment = emptySet(),
            level = level,
            availableMinutes = minutes,
        ),
        options = TrainingOptions(availability = availability),
    )

    private fun requireProgram(result: PersonalizationResult, context: String): Program =
        requireNotNull(result.program) {
            "$context: ${result.report.reasonCode} ${result.report.limitations}"
        }

    private fun configurationsOf(program: Program): Set<String> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }.flatMap { it.allExercises() }
            .mapNotNull { it.catalogConfigurationId }
            .toSet()

    private fun equipmentIdsOf(configurationIds: Set<String>): Set<String> =
        configurationIds.map { configurations.getValue(it).profile.equipmentId }.toSet()

    @Test
    fun kettlebell_only_muscle_plan_uses_kettlebell_in_squat_row_or_press_at_every_level() {
        val kettlebellLifts = setOf("front_squat__kettlebell", "conventional_row__kettlebell", "military_press__kettlebell")
        levels.forEach { level ->
            val result = generateMuscle(fixture("E10"), level)
            val program = requireProgram(result, "E10 Músculo 3d/60 $level")
            val configurationIds = configurationsOf(program)
            assertTrue(
                "E10 $level: ninguna sentadilla, remo ni press de kettlebell en $configurationIds",
                configurationIds.any { it in kettlebellLifts },
            )
            assertTrue(
                "E10 $level: solo kettlebell y peso corporal, no hay fuga de material: ${equipmentIdsOf(configurationIds)}",
                equipmentIdsOf(configurationIds).all { it == "kettlebell" || it == "bodyweight" },
            )
            assertTrue(
                "E10 $level: tirón vertical y horizontal comparten el remo con kettlebell y el plan lo avisa",
                SHARED_ROW_FOR_PULL_NOTE in result.report.planNotes,
            )
        }
    }

    @Test
    fun smith_and_bench_muscle_plan_uses_the_smith_in_squat_bench_hinge_row_or_press_at_every_level() {
        val smithLifts = setOf(
            "high_bar_back_squat__smith_machine",
            "bench_press__smith_machine",
            "romanian_deadlift__bilateral__smith_machine",
            "conventional_row__smith_machine",
            "military_press__smith_machine",
        )
        levels.forEach { level ->
            val result = generateMuscle(fixture("E11"), level)
            val program = requireProgram(result, "E11 Músculo 3d/60 $level")
            val configurationIds = configurationsOf(program)
            assertTrue(
                "E11 $level: ninguna configuración Smith en $configurationIds",
                configurationIds.any { it in smithLifts },
            )
            assertTrue(
                "E11 $level: la banca Smith se hace con el banco plano confirmado: $configurationIds",
                "bench_press__smith_machine" in configurationIds,
            )
            assertTrue(
                "E11 $level: solo Smith y peso corporal, no hay fuga de material: ${equipmentIdsOf(configurationIds)}",
                equipmentIdsOf(configurationIds).all { it == "smith_machine" || it == "bodyweight" },
            )
            assertTrue("E11 $level: el remo compartido se avisa", SHARED_ROW_FOR_PULL_NOTE in result.report.planNotes)
        }
    }

    @Test
    fun the_smith_bench_press_needs_the_bench_and_falls_back_to_the_knee_push_up_without_it() {
        val smithWithoutBench = EquipmentAvailability(categories = setOf(EquipmentCategory.SMITH_MACHINE))
        levels.forEach { level ->
            val program = requireProgram(generateMuscle(smithWithoutBench, level), "Smith sin banco $level")
            val configurationIds = configurationsOf(program)
            assertFalse(
                "Smith sin banco no puede hacer banca Smith (B4: bench_press__* exige banco): $configurationIds",
                "bench_press__smith_machine" in configurationIds,
            )
            assertTrue("sin banco, el empuje horizontal cae a la flexión de rodillas: $configurationIds", "knee_push_up__default" in configurationIds)
        }
    }

    @Test
    fun dumbbells_without_a_bench_get_the_floor_press_instead_of_the_knee_push_up() {
        // E2 (solo mancuernas): sin banco no hay press de banca con mancuernas, y hasta B5 el empuje horizontal caía a la
        // flexión de rodillas. r2 §13.3: «`floor_press__dumbbells` sin banco».
        val dumbbellsOnly = EquipmentAvailability(setOf(EquipmentCategory.DUMBBELLS))
        levels.forEach { level ->
            val program = requireProgram(generateMuscle(dumbbellsOnly, level), "solo mancuernas Músculo 3d/60 $level")
            val configurationIds = configurationsOf(program)
            assertTrue(
                "solo mancuernas $level: el empuje horizontal es el floor press con mancuernas: $configurationIds",
                "floor_press__dumbbells" in configurationIds,
            )
            assertFalse(
                "solo mancuernas $level: el floor press sustituye a la flexión de rodillas: $configurationIds",
                "knee_push_up__default" in configurationIds,
            )
        }
    }

    @Test
    fun band_and_pull_up_bar_muscle_plan_gets_the_band_pulldown_or_pull_ups_as_vertical_pull() {
        levels.forEach { level ->
            val result = generateMuscle(fixture("E12"), level)
            val program = requireProgram(result, "E12 Músculo 3d/60 $level")
            val configurationIds = configurationsOf(program)
            val vertical = if (level == CatalogLevel.BEGINNER) "lat_pulldown__bilateral__band" else "pull_up__pronated__medium"
            assertTrue("E12 $level: falta el tirón vertical $vertical en $configurationIds", vertical in configurationIds)
            assertFalse(
                "E12 $level: con jalón o dominadas el tirón vertical y el horizontal no comparten remo",
                SHARED_ROW_FOR_PULL_NOTE in result.report.planNotes,
            )
        }
    }

    @Test
    fun band_only_never_gets_the_anchored_band_variants_and_keeps_its_row() {
        val bandOnly = EquipmentAvailability(setOf(EquipmentCategory.BAND))
        levels.forEach { level ->
            val result = generateMuscle(bandOnly, level)
            val program = requireProgram(result, "solo banda Músculo 3d/60 $level")
            val configurationIds = configurationsOf(program)
            assertFalse(
                "sin barra de dominadas el jalón con banda no tiene dónde anclarse (B4): $configurationIds",
                "lat_pulldown__bilateral__band" in configurationIds,
            )
            assertFalse(
                "sin banco el hip thrust con banda no se puede hacer (B4): $configurationIds",
                "hip_thrust__bilateral__band" in configurationIds,
            )
            assertTrue("solo banda conserva el remo con banda: $configurationIds", "back_remo_banda__default" in configurationIds)
            assertTrue(
                "solo banda: tirón vertical y horizontal comparten el remo con banda y el plan lo avisa",
                SHARED_ROW_FOR_PULL_NOTE in result.report.planNotes,
            )
        }
    }
}
