package com.example.kpkn.data.models

import com.example.kpkn.domain.discomfort.DiscomfortSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscomfortKnowledgeTest {

    private val withKnowledge = DISCOMFORT_CATALOG.filter { it.id != "none" }

    @Test
    fun `todas las entradas del catalogo menos none tienen conocimiento y ninguna sobra`() {
        withKnowledge.forEach { entry ->
            assertNotNull("Falta el conocimiento de ${entry.id}", discomfortKnowledge(entry.id))
        }
        assertNull(discomfortKnowledge("none"))
        assertEquals(
            withKnowledge.map { it.id }.toSet(),
            DISCOMFORT_KNOWLEDGE.map { it.id }.toSet(),
        )
        assertEquals("ids duplicados", DISCOMFORT_KNOWLEDGE.size, DISCOMFORT_KNOWLEDGE_BY_ID.size)
    }

    @Test
    fun `limites de longitud del contenido`() {
        DISCOMFORT_KNOWLEDGE.forEach { k ->
            assertTrue("${k.id}: título vacío", k.title.isNotBlank())
            assertTrue("${k.id}: zona vacía", k.zoneLabel.isNotBlank() && k.zonePhrase.isNotBlank())
            assertTrue("${k.id}: explicación de ${k.explanation.length}", k.explanation.length in 1..DISCOMFORT_EXPLANATION_MAX_CHARS)
            assertTrue("${k.id}: ${k.commonCauses.size} causas", k.commonCauses.size in 3..4)
            k.commonCauses.forEach {
                assertTrue("${k.id}: causa de ${it.length}: $it", it.length in 1..DISCOMFORT_CAUSE_MAX_CHARS)
            }
            assertTrue("${k.id}: consejo de ${k.whatYouCanDo.length}", k.whatYouCanDo.length in 1..DISCOMFORT_ADVICE_MAX_CHARS)
        }
    }

    @Test
    fun `sin exclamaciones, sin debes y sin repetir el cierre profesional`() {
        DISCOMFORT_KNOWLEDGE.forEach { k ->
            val texts = listOf(k.title, k.explanation, k.whatYouCanDo) + k.commonCauses
            texts.forEach { text ->
                assertFalse("${k.id}: «!» en «$text»", text.contains('!') || text.contains('¡'))
                assertFalse("${k.id}: «debes» en «$text»", text.lowercase().contains("debes"))
                assertFalse("${k.id}: repite el cierre en «$text»", text.contains("consulta a un profesional"))
            }
        }
        assertEquals(
            "Si el dolor es fuerte o no mejora en una semana, consulta a un profesional.",
            PROFESSIONAL_ADVICE,
        )
    }

    @Test
    fun `sin diagnosticos, medicamentos ni genero gramatical`() {
        val forbidden = listOf(
            "ibuprofeno", "paracetamol", "antiinflamatorio", "medicamento", "pastilla", "analgesic", "pomada",
            "tendinitis", "desgarro", "rotura", "esguince", "hernia", "fractura", "lesion", "lesión",
            "cansado", "cansada", "sentado", "sentada",
        )
        DISCOMFORT_KNOWLEDGE.forEach { k ->
            val text = (listOf(k.title, k.explanation, k.whatYouCanDo) + k.commonCauses).joinToString(" ").lowercase()
            forbidden.forEach { word -> assertFalse("${k.id}: contiene «$word»", text.contains(word)) }
        }
    }

    @Test
    fun `los alias estan sin tildes ni mayusculas y la animacion es la seccion del catalogo`() {
        DISCOMFORT_KNOWLEDGE.forEach { k ->
            assertTrue("${k.id}: sin alias", k.searchAliases.isNotEmpty())
            k.searchAliases.forEach { alias ->
                assertEquals("${k.id}: alias sin normalizar «$alias»", DiscomfortSearch.normalize(alias), alias)
            }
            assertEquals("${k.id}: animación", DISCOMFORT_CATALOG_BY_ID.getValue(k.id).section, k.animation)
        }
    }

    @Test
    fun `la ficha de rodilla delante sigue el modelo F7`() {
        val k = discomfortKnowledge("knee_patellar")!!
        assertEquals("RODILLA", k.zoneLabel)
        assertEquals("Molestia delante de la rodilla", k.title)
        assertEquals("la rodilla", k.zonePhrase)
        assertTrue(k.commonCauses.contains("Subir el volumen de pierna demasiado rápido."))
    }
}
