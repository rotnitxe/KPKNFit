package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
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
                    supportDependencyFor(configurationId)?.let { add(it) }
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
    fun cable_and_smith_stations_attest_only_their_station() {
        val cableConfig = "triceps_pushdown__bilateral__cable"
        val smithConfig = "bench_press__smith_machine"
        val legCurl = "seated_leg_curl__bilateral__machine"

        val cableStation = setOf("bodyweight", "cable")
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(programWith(listOf(cableConfig)), cableStation, catalog))
        assertEquals(setOf("machine"), missingFixedRecipeEquipment(programWith(listOf(legCurl)), cableStation, catalog))
        assertEquals(setOf("smith_machine"), missingFixedRecipeEquipment(programWith(listOf(smithConfig)), cableStation, catalog))

        val smithStation = setOf("bodyweight", "smith_machine")
        assertEquals(emptySet<String>(), missingFixedRecipeEquipment(programWith(listOf(smithConfig)), smithStation, catalog))
        assertEquals(setOf("machine"), missingFixedRecipeEquipment(programWith(listOf(legCurl)), smithStation, catalog))
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
