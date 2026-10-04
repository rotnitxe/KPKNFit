package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de material real de recetas fijas
 * ([missingFixedRecipeEquipment], la firma exacta que consume el wizard/M2
 * antes del preview):
 * - Legacy `general_gym` (inventario null) → comportamiento anterior, sin faltantes.
 * - Equipo finito (sin `general_gym`) → devuelve el material que la receta exige
 *   y no está declarado; NUNCA afirma compatibilidad sin verificar.
 * - Verificación pura: no muta el programa ni la receta de autor.
 * - Receta real materializada (protocolo): material compatible ⇒ vacío;
 *   inventario finito incompleto ⇒ incompatibilidad explícita.
 */
class FixedRecipeEquipmentCompatibilityTest {
    private val catalog: ExerciseCatalogV2 get() = CatalogCompositionTestSupport.catalog

    private fun programWith(
        configurationIds: List<String>,
        sourceRecipe: com.example.kpkn.data.protocols.TrainingPlanRecipe? = null,
    ): Program {
        val session = Session(
            id = "session-1",
            name = "Día",
            exercises = configurationIds.mapIndexed { index, configurationId ->
                Exercise(
                    id = "exercise-$index",
                    name = configurationId,
                    catalogConfigurationId = configurationId,
                    canonicalExerciseId = configurationId,
                    exerciseId = configurationId,
                    exerciseDbId = configurationId,
                    sets = listOf(ExerciseSet(id = "set-$index", targetReps = 5)),
                )
            },
        )
        return Program(
            id = "guard-program",
            name = "Guardia",
            sourceRecipe = sourceRecipe,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro-1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block-1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(id = "meso-1", name = "Meso", weeks = listOf(ProgramWeek(id = "week-1", name = "Semana", sessions = listOf(session)))),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * Requisito de material que la receta exige (sin `bodyweight`: no necesita
     * material). Con `exact` una máquina se declara por su CONFIGURACIÓN (token
     * `machine_config:<id>`), que es como se acredita hoy; sin `exact` devuelve
     * el kind genérico (`machine`), que es lo que aparece como faltante.
     */
    private fun requiredMaterialOf(program: Program, exact: Boolean): Set<String> {
        val byId = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
        return program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }.flatMap { it.allExercises() }
            .mapNotNull { exercise -> exercise.catalogConfigurationId }
            .flatMap { configurationId ->
                val configuration = byId[configurationId] ?: return@flatMap emptyList<String>()
                buildList {
                    val primary = configuration.profile.equipmentId
                    when {
                        primary.isBlank() -> Unit
                        primary == "machine" && exact -> add(machineConfigToken(configurationId))
                        else -> add(primary)
                    }
                    configuration.profile.richMetadata?.programming?.requiredEquipment?.forEach { entry ->
                        if (entry.isNotBlank() && !(entry == "machine" && exact)) add(entry)
                    }
                    // Requisitos de soporte CONJUNTO (banco, rack, barra de
                    // dominadas, paralelas, barra baja, balón, anclaje…).
                    addAll(supportRequirementsFor(configurationId))
                }
            }
            .filter { it != "bodyweight" }
            .toSet()
    }

    // ─── Contrato puro ────────────────────────────────────────────────────────

    @Test
    fun legacy_general_gym_keeps_the_previous_behaviour_without_missing_material() {
        // Perfil legacy (inventario null): `general_gym` autoriza como antes.
        val program = programWith(listOf("bench_press__barbell", "seated_leg_curl__bilateral__machine"))
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(program, setOf("general_gym"), catalog))
        assertTrue(fixedRecipeEquipmentAvailability(program, setOf("general_gym"), catalog).available)
    }

    @Test
    fun finite_material_without_general_gym_reports_exactly_what_is_missing() {
        val program = programWith(listOf("bench_press__barbell", "seated_leg_curl__bilateral__machine"))
        // Equipo finito: `bodyweight` nunca es faltante, el resto sí.
        val missing = missingFixedRecipeEquipment(program, setOf("bodyweight"), catalog)
        assertTrue("Debe reportar la barra exigida", "barbell" in missing)
        assertTrue("Debe reportar la máquina exigida", "machine" in missing)
        assertFalse("El peso corporal no necesita material", "bodyweight" in missing)
        assertFalse("Sin verificar no se afirma compatibilidad", missing.isEmpty())

        // Con TODO el material declarado (sin truco `general_gym`; las máquinas
        // por su configuración exacta): compatible.
        val declared = requiredMaterialOf(program, exact = true) + "bodyweight"
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(program, declared, catalog))
        assertTrue(fixedRecipeEquipmentAvailability(program, declared, catalog).available)
    }

    @Test
    fun support_dependency_is_required_and_satisfied_only_explicitly() {
        // Configuración de dominas: `bodyweight` + dependencia de soporte.
        val program = programWith(listOf("pull_up__pronated__medium"))
        val missing = missingFixedRecipeEquipment(program, setOf("bodyweight"), catalog)
        assertTrue("El soporte de dominas se exige si no está declarado", "pull_up_bar" in missing)
        assertEquals(
            emptySet<String>(),
            missingFixedRecipeEquipment(program, setOf("bodyweight", "pull_up_bar"), catalog),
        )
    }

    @Test
    fun unverifiable_configuration_never_affirms_compatibility() {
        val program = programWith(listOf("config_que_no_existe__en_catalogo"))
        // Sin `general_gym` (inventario finito): nada se afirma sin verificar.
        val missing = missingFixedRecipeEquipment(program, setOf("bodyweight"), catalog)
        assertTrue(
            "Una configuración ausente del catálogo bloquea la afirmación de compatibilidad",
            FIXED_RECIPE_UNVERIFIABLE_MATERIAL in missing,
        )
        val availability = fixedRecipeEquipmentAvailability(program, setOf("bodyweight"), catalog)
        assertFalse(availability.available)
        assertEquals(setOf("config_que_no_existe__en_catalogo"), availability.unverifiableConfigurations)
    }

    @Test
    fun helper_is_pure_and_keeps_the_author_recipe_intact() {
        val authorRecipe = com.example.kpkn.data.protocols.TrainingPlanRecipe(
            id = "author-fixed-recipe",
            weeks = emptyList(),
        )
        val program = programWith(listOf("bench_press__barbell"), sourceRecipe = authorRecipe)
        val snapshot = program.copy()
        missingFixedRecipeEquipment(program, setOf("bodyweight"), catalog)
        fixedRecipeEquipmentAvailability(program, setOf("bodyweight"), catalog)
        assertEquals("La guardia no muta el programa ni su receta", snapshot, program)
        assertEquals(authorRecipe, program.sourceRecipe)
        assertEquals(
            "Ni el orden ni las sesiones cambian",
            snapshot.macrocycles,
            program.macrocycles,
        )
    }

    // ─── Fine-machine: configuración exacta, kinds de estación, nada por kind ──

    @Test
    fun declared_leg_curl_attests_only_itself_not_chest_press_or_leg_press() {
        val legCurl = "seated_leg_curl__bilateral__machine"
        val chestPress = "tren_superior_press_pecho_maquina_convergente__default"
        val legPress = "quads_extension_cuadriceps__machine__bilateral"
        val declared = setOf("bodyweight", machineConfigToken(legCurl))

        // Configuración acertada → compatible sin más material.
        assertEquals(
            "La máquina declarada SÍ acredita su configuración",
            emptySet<String>(),
            missingFixedRecipeEquipment(programWith(listOf(legCurl)), declared, catalog),
        )
        // …y NO abre otras máquinas: chest press y prensa siguen faltando.
        assertEquals(
            "Una máquina declarada no acredita las demás (leg curl ≠ chest press ≠ prensa)",
            setOf("machine"),
            missingFixedRecipeEquipment(programWith(listOf(chestPress, legPress)), declared, catalog),
        )
        // La presencia genérica `machine` (lo que expone effectiveEquipment)
        // tampoco aprueba ninguna máquina por sí sola.
        assertEquals(
            setOf("machine"),
            missingFixedRecipeEquipment(programWith(listOf(chestPress)), setOf("bodyweight", "machine"), catalog),
        )
    }

    @Test
    fun broad_machine_availability_does_not_satisfy_an_authored_exact_machine_requirement() {
        val authored = programWith(listOf("seated_leg_curl__bilateral__machine"))
        val snapshot = authored.copy()
        val categories = TrainingOptions(
            availability = EquipmentAvailability(setOf(EquipmentCategory.MACHINES)),
        ).effectiveEquipment(setOf("general_gym"))

        assertEquals(setOf("bodyweight", "machine"), categories)
        assertEquals(
            setOf("machine"),
            missingFixedRecipeEquipment(authored, categories, catalog),
        )
        assertEquals("La guardia no relaja ni reescribe la receta autora", snapshot, authored)
    }

    @Test
    fun support_requirements_are_a_set_of_dependencies_not_a_single_string() {
        // §13.2: «Actualizar supportDependencyFor: debe devolver conjunto … o
        // sustituirse por requirementsOf(configuration)» (AC-C1).
        assertEquals(setOf("bench", "rack"), supportRequirementsFor("bench_press__barbell"))
        assertEquals(setOf("bench"), supportRequirementsFor("bench_press__dumbbells"))
        assertEquals(setOf("bench"), supportRequirementsFor("bench_press__smith_machine"))
        assertEquals(setOf("bench", "bench_incline", "rack"), supportRequirementsFor("incline_bench_press__barbell"))
        assertEquals(setOf("bench", "bench_incline"), supportRequirementsFor("decline_bench_press__dumbbells"))
        // En suelo: sin banco, deliberadamente fuera (§13.2).
        assertEquals(emptySet<String>(), supportRequirementsFor("floor_press__dumbbells"))
        assertEquals(setOf("pull_up_bar"), supportRequirementsFor("pull_up__pronated__medium"))
        assertEquals(setOf("dip_bars"), supportRequirementsFor("tren_superior_fondos__default"))
        assertEquals(setOf("bench"), supportRequirementsFor("triceps_fondos_entre_bancos__default"))
        assertEquals(setOf("low_bar_support"), supportRequirementsFor("back_remo_invertido__default"))
        assertEquals(setOf("ball"), supportRequirementsFor("curl_isquios_con_balon__default"))
        assertEquals(setOf("nordic_anchor"), supportRequirementsFor("hams_curl_nordic_peso_corporal__default"))
        assertEquals(setOf("support"), supportRequirementsFor("push_up__feet_elevated"))
        // §13.2: nada de rack por coincidencia de IMPLEMENTO. Paquete A · B4 (N-05): el rack sigue a la lift, así que la
        // sentadilla trasera de barra lo exige (antes devolvía vacío) y el peso muerto rumano con barra no.
        assertEquals(setOf("rack"), supportRequirementsFor("high_bar_back_squat__barbell"))
        assertEquals(emptySet<String>(), supportRequirementsFor("romanian_deadlift__bilateral__barbell"))
        assertEquals(emptySet<String>(), supportRequirementsFor("unknown_configuration__default"))
    }

    // ─── Paquete A · B4 (DEC-w2-04 parte 1): soportes reales de sentadillas y press ─────────

    @Test
    fun the_rack_follows_the_barbell_squat_lifts_and_not_the_implement_or_the_smith() {
        listOf(
            "high_bar_back_squat__barbell",
            "low_bar_back_squat__barbell",
            "front_squat__barbell",
            "paused_back_squat__barbell",
            "high_bar_back_squat__safety_bar",
            "quads_sentadilla_cajon__default",
            "quads_sentadilla_anderson__default",
        ).forEach { id -> assertEquals("rack en $id", setOf("rack"), supportRequirementsFor(id)) }
        // La Smith trae su propio soporte; la kettlebell, la mancuerna y el peso corporal no se descargan de un rack;
        // el peso muerto, el press militar y la zancada de barra quedan fuera (N-05: «no Smith, no OHP»).
        listOf(
            "high_bar_back_squat__smith_machine",
            "low_bar_back_squat__smith_machine",
            "front_squat__smith_machine",
            "front_squat__kettlebell",
            "quads_sentadilla_copa__default",
            "quads_sentadilla_sin_carga__default",
            "conventional_deadlift__bilateral__barbell",
            "deadlift_to_knees__barbell",
            "military_press__barbell",
            "military_press__smith_machine",
            "walking_lunge__barbell",
        ).forEach { id -> assertEquals("sin rack en $id", emptySet<String>(), supportRequirementsFor(id)) }
    }

    @Test
    fun barbell_bench_variants_by_name_ask_for_bench_and_rack_like_the_barbell_bench_press() {
        // Antes el prefijo `bench_press__` no los cubría: no exigían banco ni rack.
        listOf(
            "paused_bench_press__barbell",
            "close_grip_bench_press__barbell",
            "tren_superior_press_spoto_barra__default",
            "tren_superior_press_banca_cadenas__default",
        ).forEach { id -> assertEquals("banco y rack en $id", setOf("bench", "rack"), supportRequirementsFor(id)) }
        // Lo que ya estaba no cambia: el suelo sigue sin banco y la inclinada sigue pidiendo el banco regulable.
        assertEquals(emptySet<String>(), supportRequirementsFor("floor_press__barbell"))
        assertEquals(setOf("bench", "bench_incline", "rack"), supportRequirementsFor("incline_bench_press__barbell"))
        assertEquals(setOf("bench"), supportRequirementsFor("bench_press__smith_machine"))
    }

    @Test
    fun support_gaps_are_closed_for_rack_chin_scapular_pull_ups_dead_hang_band_variants_and_the_incline_curl() {
        assertEquals(setOf("low_bar_support"), supportRequirementsFor("rack_chin__default"))
        assertEquals(setOf("pull_up_bar"), supportRequirementsFor("back_dominadas_escapulares__default"))
        assertEquals(setOf("pull_up_bar"), supportRequirementsFor("forearms_suspension_isometrica_barra_fija__default"))
        // El jalón con banda se ancla arriba; el de polea no necesita la barra.
        assertEquals(setOf("pull_up_bar"), supportRequirementsFor("lat_pulldown__bilateral__band"))
        assertEquals(setOf("pull_up_bar"), supportRequirementsFor("lat_pulldown__unilateral__band"))
        assertEquals(emptySet<String>(), supportRequirementsFor("lat_pulldown__bilateral__cable"))
        assertEquals(emptySet<String>(), supportRequirementsFor("close_grip_lat_pulldown__cable"))
        // El hip thrust con banda apoya la espalda alta en un banco.
        assertEquals(setOf("bench"), supportRequirementsFor("hip_thrust__bilateral__band"))
        assertEquals(setOf("bench"), supportRequirementsFor("hip_thrust__unilateral__band"))
        // El curl inclinado necesita el banco regulable (que acredita también el plano).
        assertEquals(setOf("bench", "bench_incline"), supportRequirementsFor("incline_biceps_curl__dumbbells"))
        assertEquals(emptySet<String>(), supportRequirementsFor("standing_biceps_curl__barbell"))
    }

    /** Programa de un protocolo del catálogo, materializado como lo hace el wizard (receta intacta). */
    private fun programOfProtocol(protocolId: String): Program = ProgramProtocolEngine.applyProtocol(
        program = Program(id = "guard-$protocolId", name = protocolId),
        protocol = PROTOCOL_LIBRARY.first { it.id == protocolId },
        metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
        exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
    )

    @Test
    fun squat_only_protocols_now_ask_for_the_rack_they_never_asked_for() {
        // Smolov y Smolov Jr entrenan SOLO la sentadilla con barra (más jalón y face pull en polea): sin banca, el rack
        // no salía por ningún otro camino. Con B4 exigen barra y rack; la polea ya figuraba.
        listOf("smolov", "smolov-jr").forEach { id ->
            val program = programOfProtocol(id)
            assertEquals(
                "$id con barra y polea declaradas pero sin rack",
                setOf("rack"),
                missingFixedRecipeEquipment(program, setOf("bodyweight", "barbell", "cable"), catalog),
            )
            assertEquals(
                "$id con barra, polea y rack declarados",
                emptySet<String>(),
                missingFixedRecipeEquipment(program, setOf("bodyweight", "barbell", "cable", "rack"), catalog),
            )
        }
    }

    @Test
    fun phat_now_asks_for_the_low_bar_support_that_its_rack_chin_always_needed() {
        // PHAT lleva el rack chin (`rack-chin`, T2) en dos días y se hace con una barra baja estable, pero hasta B4 no pedía
        // ningún soporte. Con el gimnasio completo y esa llave sin responder falta confirmarla (confirmable, no se infiere);
        // negada, el rack chin no tiene sustituto curado en `PlanAdaptationResolver` y la adaptación no es viable
        // (A.B6 decidirá si se añade uno).
        val full = AuthoredPlanFixtures.fullGym.availability
        fun adaptWith(lowBar: ApparatusPresence?): PlanAdaptationResult {
            val supports = if (lowBar == null) {
                full.supports - EquipmentKeys.LOW_BAR_SUPPORT
            } else {
                full.supports + (EquipmentKeys.LOW_BAR_SUPPORT to lowBar)
            }
            val gear = AuthoredPlanFixtures.Gear("phat-barra-baja-$lowBar", full.copy(supports = supports))
            return PlanAdaptationResolver.adapt(
                PlanAdaptationRequest(
                    recipe = AuthoredPhulPhatRecipes.phatAdapted,
                    equipment = gear.equipment,
                    availability = gear.availability,
                    catalog = catalog,
                ),
            )
        }

        assertTrue(
            "con la barra baja confirmada PHAT adaptado sigue viable",
            adaptWith(ApparatusPresence.PRESENT) is PlanAdaptationResult.Adapted,
        )
        val unknown = adaptWith(null)
        assertTrue("barra baja sin responder: $unknown", unknown is PlanAdaptationResult.NotViable)
        unknown as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.APPARATUS_UNKNOWN, unknown.reason)
        assertEquals("rack-chin", unknown.slotId)
        assertEquals(setOf("low_bar_support"), unknown.missingRequirements)

        val denied = adaptWith(ApparatusPresence.ABSENT)
        assertTrue("barra baja negada: $denied", denied is PlanAdaptationResult.NotViable)
        denied as PlanAdaptationResult.NotViable
        assertEquals(PlanAdaptationReason.NO_VALID_SUBSTITUTION, denied.reason)
        assertEquals("rack-chin", denied.slotId)
        assertEquals(setOf("low_bar_support"), denied.missingRequirements)
    }

    @Test
    fun every_visible_fixed_recipe_with_a_rack_squat_reports_the_rack_when_it_is_missing() {
        val rackSquats = setOf(
            "high_bar_back_squat__barbell",
            "low_bar_back_squat__barbell",
            "front_squat__barbell",
            "paused_back_squat__barbell",
            "high_bar_back_squat__safety_bar",
            "quads_sentadilla_cajon__default",
            "quads_sentadilla_anderson__default",
        )
        val everythingButTheRack = setOf(
            "bodyweight", "barbell", "dumbbells", "kettlebell", "band", "cable", "smith_machine", "bench",
            "bench_incline", "pull_up_bar", "low_bar_support", "dip_bars", "support", "ez_bar",
        )
        val withRackSquat = linkedMapOf<String, Boolean>()
        val notMaterialized = mutableListOf<String>()
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication && it.defaultSplit != null }.forEach { protocol ->
            // Materializar no es lo que se prueba aquí: una receta que no se pueda materializar con estos metadatos se
            // anota en la salida y se salta (el resto de pruebas de protocolos ya vigilan su materialización).
            val program = runCatching { programOfProtocol(protocol.id) }.getOrNull()
            if (program == null) {
                notMaterialized += protocol.id
                return@forEach
            }
            val configurationIds = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }.flatMap { it.allExercises() }
                .mapNotNull { it.catalogConfigurationId }.toSet()
            if (configurationIds.none { it in rackSquats }) return@forEach
            val missing = missingFixedRecipeEquipment(program, everythingButTheRack, catalog)
            assertTrue("${protocol.id} lleva una sentadilla de barra y debe pedir el rack: $missing", "rack" in missing)
            // Solo se pide el rack si esa receta no lo tenía ya por una banca de barra.
            val askedForRackAlready = configurationIds.any { id ->
                val needs = supportRequirementsFor(id)
                "bench" in needs && "rack" in needs
            }
            withRackSquat[protocol.id] = askedForRackAlready
        }
        println("[B4] recetas visibles que no se materializaron en esta prueba: $notMaterialized")
        println("[B4] recetas fijas visibles con sentadilla de barra (id → ya pedía rack por la banca): $withRackSquat")
        println("[B4] recetas fijas que piden rack SOLO por la sentadilla: ${withRackSquat.filterValues { !it }.keys}")
        assertTrue(
            "Smolov y Smolov Jr piden rack solo por la sentadilla: $withRackSquat",
            withRackSquat["smolov"] == false && withRackSquat["smolov-jr"] == false,
        )
    }

    @Test
    fun fixed_recipe_guard_requires_the_whole_support_set_at_once() {
        val benchPress = programWith(listOf("bench_press__barbell"))
        assertEquals(
            "Banca barra exige barra + banco + rack (§13.2)",
            setOf("bench", "rack"),
            missingFixedRecipeEquipment(benchPress, setOf("bodyweight", "barbell"), catalog),
        )
        assertEquals(
            emptySet<String>(),
            missingFixedRecipeEquipment(benchPress, setOf("bodyweight", "barbell", "bench", "rack"), catalog),
        )

        val inclinePress = programWith(listOf("incline_bench_press__barbell"))
        assertEquals(
            setOf("bench", "bench_incline", "rack"),
            missingFixedRecipeEquipment(inclinePress, setOf("bodyweight", "barbell"), catalog),
        )

        val pullUp = programWith(listOf("pull_up__pronated__medium"))
        assertEquals(
            "Dominadas exigen su barra (§13.2)",
            setOf("pull_up_bar"),
            missingFixedRecipeEquipment(pullUp, setOf("bodyweight"), catalog),
        )
        assertEquals(
            emptySet<String>(),
            missingFixedRecipeEquipment(pullUp, setOf("bodyweight", "pull_up_bar"), catalog),
        )
    }

    @Test
    fun cable_and_smith_stations_attest_only_their_station() {
        val cableConfig = "triceps_pushdown__bilateral__cable"
        val smithConfig = "bench_press__smith_machine"
        val legCurl = "seated_leg_curl__bilateral__machine"

        val cableStation = setOf("bodyweight", "cable")
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(programWith(listOf(cableConfig)), cableStation, catalog))
        assertEquals(setOf("machine"), missingFixedRecipeEquipment(programWith(listOf(legCurl)), cableStation, catalog))
        // §13.3: la banca Smith exige banco; la estación de cable no acredita ni Smith ni banco.
        assertEquals(
            setOf("smith_machine", "bench"),
            missingFixedRecipeEquipment(programWith(listOf(smithConfig)), cableStation, catalog),
        )

        val smithStation = setOf("bodyweight", "smith_machine")
        assertEquals(
            "Smith acredita su estación pero el press sigue exigiendo banco (§13.3)",
            setOf("bench"),
            missingFixedRecipeEquipment(programWith(listOf(smithConfig)), smithStation, catalog),
        )
        assertEquals(setOf("machine"), missingFixedRecipeEquipment(programWith(listOf(legCurl)), smithStation, catalog))

        val smithWithBench = setOf("bodyweight", "smith_machine", "bench")
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(programWith(listOf(smithConfig)), smithWithBench, catalog))
        assertEquals(setOf("machine"), missingFixedRecipeEquipment(programWith(listOf(legCurl)), smithWithBench, catalog))
    }

    @Test
    fun empty_inventory_even_with_gym_admits_no_machine_kind() {
        // Inventario vacío + chip GYM → equipo efectivo del wizard: solo bodyweight.
        val effective = TrainingOptions(inventory = EquipmentInventory()).effectiveEquipment(setOf("general_gym"))
        assertEquals(setOf("bodyweight"), effective)
        val missing = missingFixedRecipeEquipment(
            programWith(listOf("seated_leg_curl__bilateral__machine", "triceps_pushdown__bilateral__cable")),
            effective,
            catalog,
        )
        assertTrue("Sin maquinaria declarada no se admite máquina: $missing", "machine" in missing)
        assertTrue("Sin estación declarada no se admite cable: $missing", "cable" in missing)
    }

    // ─── Receta real (protocolo materializado) ────────────────────────────────

    @Test
    fun real_protocol_guard_separates_compatible_material_from_finite_empty() {
        val protocol = PROTOCOL_LIBRARY.first { entry ->
            entry.isVisibleForApplication && entry.defaultSplit != null && entry.recipe != null &&
                entry.recipe!!.liftSlots.isNotEmpty()
        }
        val program = ProgramProtocolEngine.applyProtocol(
            program = Program(id = "guard-protocol", name = "Protocolo real"),
            protocol = protocol,
            metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
            exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
        )

        // Receta intacta tras la materialización (la guardia no la reescribe).
        assertEquals(protocol.recipe?.id, program.sourceRecipe?.id)

        // Legacy: `general_gym` ⇒ comportamiento anterior, sin faltantes.
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(program, setOf("general_gym"), catalog))

        // Material real que la receta exige (una receta de fuerza exige barra).
        val requiredKinds = requiredMaterialOf(program, exact = false)
        assertTrue("La receta real exige material declarado", requiredKinds.isNotEmpty())
        assertTrue("La receta de fuerza exige barra", "barbell" in requiredKinds)

        // Con inventario finito incompleto → incompatibilidad explícita.
        val withNothing = missingFixedRecipeEquipment(program, setOf("bodyweight"), catalog)
        assertTrue("Falta material real declarado", withNothing.containsAll(requiredKinds))

        // Con TODO el material declarado (sin atajo `general_gym`; las máquinas
        // por su configuración exacta) → compatible.
        val declaredExact = requiredMaterialOf(program, exact = true)
        assertEquals(
            emptySet<String>(),
            missingFixedRecipeEquipment(program, declaredExact + "bodyweight", catalog),
        )
    }
}
