package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los grupos de «ejercicios a evitar» apuntan a definiciones REALES del catálogo: si un id se renombra o se retira, esta prueba
 * lo dice antes que el motor (que filtraría en silencio un id que nadie usa).
 */
class AvoidGroupsTest {

    private val catalogDefinitionIds: Set<String> by lazy {
        CatalogCompositionTestSupport.catalog.families.flatMapTo(mutableSetOf()) { family ->
            family.definitions.map { it.id }
        }
    }

    @Test
    fun everyDefinitionOfEveryGroupExistsInTheCatalog() {
        for (group in AvoidGroup.entries) {
            val missing = AvoidGroups.definitionIdsOf(group).filterNot { it in catalogDefinitionIds }
            assertTrue("$group: ids que no existen en el catálogo $missing", missing.isEmpty())
        }
    }

    @Test
    fun onlyJumpsAndRunningHaveNoCatalogDefinitionsYet() {
        // El catálogo no tiene definiciones de saltos ni de carrera: su control del paso no cambia ejercicios hasta un lote nuevo.
        val empty = AvoidGroup.entries.filter { AvoidGroups.definitionIdsOf(it).isEmpty() }
        assertEquals(listOf(AvoidGroup.JUMPS, AvoidGroup.RUNNING), empty)
    }

    @Test
    fun aDefinitionBelongsToAtMostOneGroup() {
        val all = AvoidGroup.entries.flatMap { AvoidGroups.definitionIdsOf(it) }
        assertEquals("un mismo ejercicio no puede estar en dos grupos", all.size, all.toSet().size)
    }

    @Test
    fun theBarbellSquatGroupHasTheBarbellSquatsAndNoLungeOrBulgarian() {
        val squats = AvoidGroups.definitionIdsOf(AvoidGroup.BARBELL_SQUAT)
        assertTrue("high_bar_back_squat" in squats && "front_squat" in squats && "quads_sentadilla_zercher_barra_recta" in squats)
        assertTrue("bulgarian_split_squat" !in squats && "forward_lunge" !in squats)
    }
}
