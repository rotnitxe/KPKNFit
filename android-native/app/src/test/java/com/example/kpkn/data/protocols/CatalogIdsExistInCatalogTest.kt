package com.example.kpkn.data.protocols

import com.example.kpkn.data.protocols.definitions.AuthoredExerciseBindings
import com.example.kpkn.data.protocols.definitions.NativeCandidateTable
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regla «nunca inventar ids de catálogo» (plan de curaduría de programas, paquete D; paso A.B5):
 * todo id que el código cita por su cuenta existe, APPROVED, en los DOS assets distribuidos
 * (`src/main/assets` y `src/main/resources`, que `decodeApproved` solo acepta si todo el catálogo está aprobado).
 *
 * Tres fuentes, todas por reflexión o por la API pública para que una constante nueva entre sola en la
 * comprobación sin tocar esta prueba:
 *  1. cada `const val` de texto de [CatalogIds];
 *  2. cada id que [NativeCandidateTable.candidatesFor] devuelve para toda clave de slot e intención, más cada
 *     `const val` de texto del propio objeto (también las privadas: una reserva declarada y aún sin usar no puede
 *     ser un id inventado);
 *  3. la configuración de cada binding de [AuthoredExerciseBindings.all].
 *
 * Si falta alguno, el mensaje del fallo (y la salida estándar) lista cada id con su origen y el asset donde falta.
 */
class CatalogIdsExistInCatalogTest {
    private fun asset(path: String): File = listOf(File(path), File("app/$path")).first { it.exists() }

    /** Ids de configuración APPROVED de cada asset, por nombre de asset. */
    private val approvedIdsByAsset: Map<String, Set<String>> by lazy {
        mapOf(
            "assets" to "src/main/assets/exercise_catalog_v2.json",
            "resources" to "src/main/resources/exercise_catalog_v2.json",
        ).mapValues { (_, path) ->
            ExerciseCatalogV2Loader.decodeApproved(asset(path).readText()).families
                .flatMap { it.definitions }
                .flatMap { it.configurations }
                .map { it.id }
                .toSet()
        }
    }

    /** `const val` de texto de un objeto Kotlin: campos estáticos `String`, públicos o privados. */
    private fun stringConstantsOf(type: Class<*>): Map<String, String> =
        type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(null) as String)
            }

    /** Una línea por (origen, id, asset) que falta. */
    private fun missingLines(origin: String, ids: Collection<Pair<String, String>>): List<String> =
        ids.flatMap { (label, id) ->
            approvedIdsByAsset.mapNotNull { (assetName, approved) ->
                if (id in approved) null else "$origin · $label = $id: no existe APPROVED en el asset «$assetName»"
            }
        }

    private fun assertNoMissing(lines: List<String>) {
        lines.forEach(::println)
        assertTrue("ids citados por el código que no existen en el catálogo:\n${lines.joinToString("\n")}", lines.isEmpty())
    }

    @Test
    fun every_catalog_ids_constant_exists_in_both_assets() {
        val constants = stringConstantsOf(CatalogIds::class.java)
        assertTrue("la reflexión debe encontrar las constantes de CatalogIds (${constants.size})", constants.size > 80)
        assertEquals(CatalogIds.SQ_LOW, constants["SQ_LOW"])
        assertEquals(CatalogIds.CURL_INCLINE, constants["CURL_INCLINE"])
        assertNoMissing(missingLines("CatalogIds", constants.map { (name, id) -> name to id }))
    }

    @Test
    fun every_native_candidate_table_id_exists_in_both_assets() {
        val fromCandidates = NativeSlotKey.entries.flatMap { key ->
            SlotIntent.entries.flatMap { intent ->
                NativeCandidateTable.candidatesFor(key, intent).map { id -> "candidatesFor($key, $intent)" to id }
            }
        }
        assertTrue("la tabla de candidatos no puede estar vacía", fromCandidates.isNotEmpty())
        val constants = stringConstantsOf(NativeCandidateTable::class.java)
        assertTrue("la reflexión debe encontrar las constantes de NativeCandidateTable (${constants.size})", constants.size >= 20)
        assertEquals(NativeCandidateTable.LOW_BAR_ROW, constants["LOW_BAR_ROW"])
        val fromConstants = constants.map { (name, id) -> "const $name" to id }
        assertNoMissing(missingLines("NativeCandidateTable", (fromCandidates + fromConstants).distinct()))
    }

    @Test
    fun every_authored_binding_configuration_exists_in_both_assets() {
        val bindings = AuthoredExerciseBindings.all
        assertTrue("los bindings autorados no pueden estar vacíos", bindings.isNotEmpty())
        val ids = bindings.map { binding -> "${binding.dayId}/${binding.slotId}" to binding.configurationId }
        assertNoMissing(missingLines("AuthoredExerciseBindings.all", ids.distinct()))
    }
}
