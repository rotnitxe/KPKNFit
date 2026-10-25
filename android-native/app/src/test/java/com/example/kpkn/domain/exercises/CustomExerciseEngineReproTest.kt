package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.testsupport.MotCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · E1/E2/E3 (AUD1 §3): reproducciones del motor de «Crear ejercicio» con el catálogo real, usando la API existente
 * (`createAutomatic`/`preview` sobre la instantánea por defecto). Sin el motor unificado, el primer parecido acierta menos que la
 * búsqueda, el material por defecto pisa al escrito, un parecido débil genera anatomía y texto, y la ausencia de carga axial se
 * guarda como cero. Con el arreglo, todas pasan sin cambiar su firma.
 */
class CustomExerciseEngineReproTest {
    private val catalog = MotCatalog.legacyDefaultCatalog

    private fun referenceTop(name: String): String? {
        val initial = SmartExerciseCreator.preview(SmartCreateRequest(name = name, implementoId = ""), catalog)
        val ref = initial.reference
        val request = SmartCreateRequest(
            name = name,
            implementoId = ref?.equipment?.let { SmartExerciseCreator.implementoIdFromLabel(it) } ?: "",
            estacionId = ref?.catalogVariantChips?.getOrNull(1)?.let { SmartExerciseCreator.estacionIdFromLabel(it) },
            lateralidadId = ref?.catalogVariantChips?.getOrNull(2)?.let { SmartExerciseCreator.lateralidadIdFromLabel(it) },
        )
        val top = SmartExerciseCreator.preview(request, catalog).reference
        return top?.let { it.catalogDefinitionId ?: it.id }
    }

    @Test
    fun firstReference_isAtLeastAsGoodAsTheCatalogSearch_on36Names() {
        val failures = mutableListOf<String>()
        val hits = AUD1_CORPUS.count { case ->
            val top = referenceTop(case.name)
            val ok = top != null && top in case.accepted
            if (!ok) failures += "${case.number}. ${case.name} → $top"
            ok
        }
        // Línea base de la búsqueda V2 en AUD1 §3.2: 26/36 = 72,2 % de primer resultado relevante.
        assertTrue("P@1 del creador = $hits/36 (la búsqueda del catálogo: 26/36). Fallos:\n${failures.joinToString("\n")}", hits >= 26)
    }

    @Test
    fun explicitMaterialInTheName_winsOverTheDumbbellDefault() {
        val created = SmartExerciseCreator.createAutomatic("sentadilla barra baja", catalog)
        assertEquals("Barra", created.equipment)
        val front = SmartExerciseCreator.createAutomatic("front squat barra", catalog)
        assertEquals("Barra", front.equipment)
    }

    @Test
    fun description_neverAddsMusclesOfOtherMovements() {
        val squat = SmartExerciseCreator.createAutomatic("sentadila trasera barra alta", catalog)
        val squatText = squat.description.orEmpty()
        listOf("Pectorales", "Dorsales", "Trapecio").forEach {
            assertFalse("La sentadilla no es un ejercicio de $it: $squatText", squatText.contains(it))
        }
        val rdl = SmartExerciseCreator.createAutomatic("peso mueto rumano con mancuernas", catalog)
        val rdlText = rdl.description.orEmpty()
        listOf("Bíceps", "Tríceps", "Pectorales").forEach {
            assertFalse("El rumano no es un ejercicio de $it: $rdlText", rdlText.contains(it))
        }
        assertTrue(
            "Los primarios del rumano salen de una sola referencia, sin flexión de codo: ${rdl.involvedMuscles}",
            rdl.involvedMuscles.none { it.role == MuscleRole.PRIMARY && it.muscle in setOf("Bíceps", "Tríceps", "Pectorales") },
        )
    }

    @Test
    fun ambiguousNames_abstain_withoutAnatomyAxialOrDescription() {
        listOf("dragón lunar mancuernas", "mi ejercicio inventado", "XQZ 123").forEach { name ->
            val created = SmartExerciseCreator.createAutomatic(name, catalog)
            assertTrue("«$name» no debe heredar músculos de un parecido débil: ${created.involvedMuscles}", created.involvedMuscles.isEmpty())
            assertNull("«$name» no genera texto sin una configuración confirmada: ${created.description}", created.description)
        }
    }

    @Test
    fun unknownAxialIsNeverStoredAsZero() {
        val created = SmartExerciseCreator.createAutomatic("press raro de una variante inventada", catalog)
        assertNotNull(created.id)
        assertTrue(created.id.startsWith("custom:"))
        val serialized=kotlinx.serialization.json.Json.encodeToJsonElement(
            com.example.kpkn.data.models.ExerciseMuscleInfo.serializer(),created) as kotlinx.serialization.json.JsonObject
        assertFalse(serialized.containsKey("axialLoadFactor"))
    }
}
