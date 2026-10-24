package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ExerciseSwapSuggestionsTest {
    private val catalog get()=CatalogTestBuilders.real
    private val index get()=GeneratorCatalog.of(catalog)
    private fun target()=index.entries.values.first { it.definitionId=="bench_press" && it.equipmentId=="barbell" }.let {
        Exercise("bench",it.definition.canonicalName,catalogDefinitionId=it.definitionId,catalogConfigurationId=it.id)
    }
    @Test fun soloMancuernasYGruposEvitadosSeRespetanSinAlterarListaCompleta() {
        val definitions=catalog.families.flatMap { it.definitions }
        val anchor=catalogReplacementAnchorFor(definitions.associateBy { it.id },"bench_press",target().catalogConfigurationId)
        val before=orderDefinitionsForReplacement(definitions,anchor).map { it.id }
        val availability=EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS),setOf(TrainingPlace.HOME))
        val options=replacementSuggestions(target(),availability,emptySet(),catalog)
        assertTrue(options.isNotEmpty())
        val excluded=AvoidGroups.definitionIdsOf(AvoidGroup.BENCH_PRESS)
        options.forEach { assertEquals("dumbbells",index.entry(it.configId)!!.equipmentId) }
        replacementSuggestions(target(),null,setOf(AvoidGroup.BENCH_PRESS),catalog).forEach { assertFalse(index.entry(it.configId)!!.definitionId in excluded) }
        assertEquals(before,orderDefinitionsForReplacement(definitions,anchor).map { it.id })
        assertEquals(definitions.size,before.size)
        assertEquals(definitions.map { it.id }.toSet(),before.toSet())
    }
    @Test fun sinIdentidadOBusquedaNoHaySeccionYElOrdenEsDeterminista() {
        assertTrue(replacementSuggestions(target().copy(catalogConfigurationId=null),null,emptySet(),catalog).isEmpty())
        assertTrue(replacementSuggestions(target(),null,emptySet(),catalog,"remo").isEmpty())
        val one=replacementSuggestions(target(),null,emptySet(),catalog)
        assertTrue(one.isNotEmpty());assertTrue(one.size<=6)
        assertEquals(one,replacementSuggestions(target(),null,emptySet(),catalog))
    }
    @Test fun gruposDeclaradosSobrevivenJsonYLegadoNoInfiereGrupos() {
        val json=Json { ignoreUnknownKeys=true }
        assertTrue(persistedSwapAvoided(json.decodeFromString<Program>("""{"id":"p","name":"Plan"}""")).isEmpty())
        val program=Program("p","Plan",avoidedExerciseGroups=setOf("BENCH_PRESS","future-group"))
        assertEquals(setOf(AvoidGroup.BENCH_PRESS),persistedSwapAvoided(json.decodeFromString<Program>(json.encodeToString(program))))
    }
}
