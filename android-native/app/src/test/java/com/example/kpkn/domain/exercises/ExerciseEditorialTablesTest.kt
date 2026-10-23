package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.OPEN_QUERY_PRIORITY_TABLE
import com.example.kpkn.testsupport.MotCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · E1 (AUD1 §3.3/§3.6): todos los ids de las tablas editoriales (orden de las consultas de familia y alias de identidad)
 * existen en el catálogo aprobado. Antes, la prioridad de «remo»/«row» apuntaba a seis ids `back_*` retirados.
 */
class ExerciseEditorialTablesTest {
    private val definitionIds: Set<String> =
        MotCatalog.catalog.families.flatMap { it.definitions }.map { it.id }.toSet()

    @Test
    fun openQueryPriorityTable_pointsOnlyAtExistingDefinitions() {
        val missing = OPEN_QUERY_PRIORITY_TABLE.flatMap { (query, ids) -> ids.filter { it !in definitionIds }.map { "$query → $it" } }
        assertTrue("ids retirados en la tabla de prioridad: $missing", missing.isEmpty())
    }

    @Test
    fun nameAliases_pointAtExistingDefinitions() {
        val missing = NameLexicon.ALIASES.flatMap { alias -> alias.definitionIds.filter { it !in definitionIds }.map { "${alias.phrase} → $it" } }
        assertTrue("alias a definiciones inexistentes: $missing", missing.isEmpty())
    }

    @Test
    fun rowQueries_nowReachTheCurrentRowDefinitions() {
        val resolver = ExerciseCatalogV2Resolver(MotCatalog.catalog)
        assertEquals("conventional_row", resolver.search("remo").first().definitionId)
        assertEquals("conventional_row", resolver.search("row").first().definitionId)
    }

    @Test
    fun corpusReferences_existInTheCatalog() {
        val all = (AUD1_CORPUS + HELD_OUT_CORPUS + FRESH_CORPUS).flatMap { it.accepted }.filter { it !in definitionIds }
        assertTrue("referencias del corpus que no existen: $all", all.isEmpty())
    }
}
