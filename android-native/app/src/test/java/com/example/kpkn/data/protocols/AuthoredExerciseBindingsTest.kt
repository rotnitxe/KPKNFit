package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.protocols.definitions.AuthoredExerciseBindings
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.AuthoredSourceTable
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.ConfigurationAvailability
import com.example.kpkn.domain.training.configurationAvailability
import com.example.kpkn.domain.training.resolveEffectiveEquipment
import com.example.kpkn.domain.training.PlanAdaptationResolver
import com.example.kpkn.domain.training.TrainingOptions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §13.5/AC-E2: cada slot autorado está ligado a su configuración canónica
 * VERIFICADA en **ambos** assets distribuidos. Un binding sin coincidencia es
 * error de publicación (nunca un fallback por nombre) y una máquina genérica
 * jamás acredita una configuración exacta.
 */
class AuthoredExerciseBindingsTest {
    private fun asset(path: String): File = listOf(File(path), File("app/$path")).first { it.exists() }

    private val androidAsset: File get() = asset("src/main/assets/exercise_catalog_v2.json")
    private val resourcesAsset: File get() = asset("src/main/resources/exercise_catalog_v2.json")

    private fun decode(file: File): ExerciseCatalogV2 = ExerciseCatalogV2Loader.decodeApproved(file.readText())

    private fun configurationsOf(catalog: ExerciseCatalogV2) = catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .associateBy { it.id }

    private val authoredRecipes
        get() = AuthoredPhulPhatRecipes.all

    @Test
    fun every_authored_slot_is_bound_to_its_canonical_configuration() {
        val failures = mutableListOf<String>()
        authoredRecipes.forEach { recipe ->
            AuthoredExerciseBindings.coverageGapsOf(recipe).forEach { gap ->
                failures += "${recipe.id}: ${gap.dayId}/${gap.slotId} (${gap.configurationId}) → ${gap.detail}"
            }
        }
        assertTrue("Slots autorados sin binding válido:\n$failures", failures.isEmpty())
        // Ambas tablas cubren todos los días de sus recetas.
        AuthoredExerciseBindings.recipeTables.forEach { (recipeId, table) ->
            val recipe = requireNotNull(AuthoredPhulPhatRecipes.recipeFor(recipeId)) { recipeId }
            val dayIds = recipe.weeks.first().days.mapNotNull { it.id }.toSet()
            val boundDays = AuthoredExerciseBindings.of(table).map { it.dayId }.toSet()
            assertTrue("$recipeId sin bindings para ${dayIds - boundDays}", dayIds.all { it in boundDays })
        }
        // Cobertura completa: 25 slots PHUL + 42 slots PHAT = 67 bindings.
        assertEquals(67, AuthoredExerciseBindings.all.size)
        assertEquals(25, AuthoredExerciseBindings.of(AuthoredSourceTable.PHUL_MS_2021).size)
        assertEquals(42, AuthoredExerciseBindings.of(AuthoredSourceTable.PHAT_BIOLAYNE_2016).size)
        assertEquals(67, AuthoredExerciseBindings.all.map { Triple(it.table, it.dayId, it.slotId) }.distinct().size)
    }

    @Test
    fun every_bound_configuration_exists_and_is_approved_in_both_distributed_assets() {
        // Los dos assets distribuidos deben ser el mismo documento byte a byte.
        assertEquals(
            "assets y resources divergen",
            androidAsset.readText().replace("\r\n", "\n"),
            resourcesAsset.readText().replace("\r\n", "\n"),
        )
        val ids = AuthoredExerciseBindings.all.map { it.configurationId }.toSet()
        listOf("assets" to androidAsset, "resources" to resourcesAsset).forEach { (label, file) ->
            val configurations = configurationsOf(decode(file))
            ids.forEach { id ->
                val configuration = configurations[id]
                assertTrue("$label sin la configuración bindingeada: $id", configuration != null)
                assertEquals("$label: $id no está APPROVED", "APPROVED", configuration!!.evidence.reviewStatus.name)
            }
        }
        // Las cuatro reservas corporales de §13.5/§13.6 también están publicadas
        // en ambos assets (AC-E4), aunque no sean slots de la tabla de autor.
        val bodyweightReserves = setOf(
            "calf_raise__bilateral__bodyweight",
            "push_up__hands_elevated",
            "glutes_puente_gluteos__bilateral__bodyweight",
            "reverse_lunge__bodyweight",
        )
        listOf(androidAsset, resourcesAsset).forEach { file ->
            val configurations = configurationsOf(decode(file))
            bodyweightReserves.forEach { id ->
                val configuration = configurations[id]
                assertTrue("${file.name} sin reserva corporal: $id", configuration != null)
                assertEquals("${file.name}: $id no APPROVED", "APPROVED", configuration!!.evidence.reviewStatus.name)
            }
        }
    }

    @Test
    fun generic_machine_presence_never_proves_an_exact_machine_binding() {
        val catalog = decode(androidAsset)
        val configurations = configurationsOf(catalog)
        val machineBindings = AuthoredExerciseBindings.all.filter { binding ->
            configurations[binding.configurationId]?.profile?.equipmentId == "machine"
        }
        assertTrue("Debe haber bindings exactos de máquina que proteger", machineBindings.isNotEmpty())
        // Marcar solo la categoría MACHINES («máquinas») sin ninguna clave
        // concreta: ninguna configuración exacta queda acreditada.
        val availability = EquipmentAvailability(categories = setOf(EquipmentCategory.MACHINES))
        val equipment = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet())
        assertTrue("El kind genérico no emite configuraciones", equipment.tokens.none { it.startsWith("machine_config:") })
        machineBindings.forEach { binding ->
            val result = configurationAvailability(
                configurationId = binding.configurationId,
                equipment = equipment,
                availability = availability,
                catalog = catalog,
            )
            assertTrue(
                "${binding.configurationId} NO puede acreditarse con máquina genérica",
                result is ConfigurationAvailability.Missing,
            )
        }
    }

    @Test
    fun bindings_never_resolve_by_name_only() {
        // Toda resolución es literal contra el catálogo: ids con estructura de
        // configuración v2 (`definición__variante`), presentes en el asset.
        val configurations = configurationsOf(decode(androidAsset))
        AuthoredExerciseBindings.all.forEach { binding ->
            assertTrue(
                "${binding.slotId}: id no canónico '${binding.configurationId}'",
                binding.configurationId.contains("__"),
            )
            val definitionId = binding.configurationId.substringBefore("__")
            assertTrue(
                "${binding.slotId}: la definición de '$definitionId' no existe",
                configurations.values.any { it.id.startsWith("${definitionId}__") },
            )
            assertFalse(
                "${binding.slotId}: binding sin razón editorial",
                binding.reason.isBlank(),
            )
        }
    }
}
