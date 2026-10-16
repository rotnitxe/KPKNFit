package com.example.kpkn.domain.discomfort

import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.DiscomfortSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscomfortSearchTest {

    private fun ids(query: String) = DiscomfortSearch.search(query).map { it.entry.id }
    private fun kind(query: String, id: String) =
        DiscomfortSearch.search(query).first { it.entry.id == id }.kind

    @Test
    fun `rodila con una falta encuentra las dos rodillas por busqueda aproximada`() {
        val hits = DiscomfortSearch.search("rodila")
        assertEquals(listOf("knee_patellar", "knee_medial"), hits.map { it.entry.id })
        assertTrue(hits.all { it.kind == DiscomfortMatchKind.APPROXIMATE })
    }

    @Test
    fun `espalda baja encuentra lumbar por alias`() {
        val hits = DiscomfortSearch.search("espalda baja")
        assertEquals("lumbar", hits.first().entry.id)
        assertEquals(DiscomfortMatchKind.ALIAS, hits.first().kind)
        assertFalse("upper_back no es espalda baja", hits.any { it.entry.id == "upper_back" })
    }

    @Test
    fun `lumbago y codo de tenista llegan a su molestia`() {
        assertEquals("lumbar", ids("Lumbago").first())
        assertEquals("elbow_lateral", ids("codo de tenista").first())
    }

    @Test
    fun `sin tildes ni mayusculas`() {
        assertEquals(ids("muneca"), ids("MUÑECA"))
        assertEquals("wrist_hand", ids("muñeca").first())
        assertEquals("achilles", ids("tendon de aquiles").first())
        assertEquals("achilles", ids("Tendón de Aquiles").first())
    }

    @Test
    fun `empieza por la etiqueta y luego palabra completa`() {
        // «rodilla»: las dos empiezan por la palabra.
        assertEquals(listOf("knee_patellar", "knee_medial"), ids("rodilla"))
        assertEquals(DiscomfortMatchKind.LABEL_START, kind("rodilla", "knee_patellar"))
        // «anterior» está dentro de la etiqueta, no al inicio.
        val anterior = DiscomfortSearch.search("anterior")
        assertTrue(anterior.isNotEmpty())
        // Las de la etiqueta van antes que las aproximadas («interior del muslo» se parece a «anterior»).
        val wordHits = anterior.filter { it.kind == DiscomfortMatchKind.LABEL_WORD }
        assertEquals(anterior.take(wordHits.size), wordHits)
        assertTrue(anterior.drop(wordHits.size).all { it.kind == DiscomfortMatchKind.APPROXIMATE })
        assertTrue(wordHits.map { it.entry.id }.containsAll(listOf("shoulder_anterior", "hip_front", "knee_patellar")))
    }

    @Test
    fun `el orden es inicio, palabra, alias y aproximada`() {
        fun entry(id: String, label: String) = DiscomfortCatalogEntry(
            id = id, label = label, description = "", section = DiscomfortSection.GENERAL,
        )
        // El catálogo está puesto al revés del orden esperado a propósito.
        val catalog = listOf(
            entry("aprox", "Zona izquierdo"),
            entry("alias", "Otra cosa"),
            entry("palabra", "La mano izquierda"),
            entry("inicio", "Izquierda total"),
        )
        val aliases = mapOf("alias" to listOf("izquierda lejana"))
        val hits = DiscomfortSearch.search("izquierda", catalog) { aliases[it].orEmpty() }
        assertEquals(listOf("inicio", "palabra", "alias", "aprox"), hits.map { it.entry.id })
        assertEquals(
            listOf(
                DiscomfortMatchKind.LABEL_START, DiscomfortMatchKind.LABEL_WORD,
                DiscomfortMatchKind.ALIAS, DiscomfortMatchKind.APPROXIMATE,
            ),
            hits.map { it.kind },
        )
    }

    @Test
    fun `a igual tipo manda el orden del catalogo`() {
        fun entry(id: String, label: String) = DiscomfortCatalogEntry(
            id = id, label = label, description = "", section = DiscomfortSection.GENERAL,
        )
        val catalog = listOf(entry("b", "Brazo uno"), entry("a", "Brazo dos"), entry("c", "Brazo tres"))
        assertEquals(listOf("b", "a", "c"), DiscomfortSearch.search("brazo", catalog) { emptyList() }.map { it.entry.id })
    }

    @Test
    fun `una falta solo se tolera en palabras de cinco letras o mas`() {
        assertTrue("cuatro letras no se corrigen", ids("codp").isEmpty())
        assertEquals("neck_cervical", ids("cuelo").first()) // 5 letras, una de menos
        assertEquals("neck_cervical", ids("cuelllo").first()) // una de más
        assertTrue("dos faltas ya no valen", ids("cuxxllo").isEmpty())
        assertTrue(ids("rdlla").isEmpty())
    }

    @Test
    fun `distancia de edicion de uno`() {
        assertTrue(DiscomfortSearch.editDistanceAtMostOne("rodilla", "rodilla"))
        assertTrue(DiscomfortSearch.editDistanceAtMostOne("rodila", "rodilla")) // una de menos
        assertTrue(DiscomfortSearch.editDistanceAtMostOne("rodillla", "rodilla")) // una de más
        assertTrue(DiscomfortSearch.editDistanceAtMostOne("rodalla", "rodilla")) // una cambiada
        assertTrue(DiscomfortSearch.editDistanceAtMostOne("rodilal", "rodilla")) // dos contiguas intercambiadas
        assertFalse(DiscomfortSearch.editDistanceAtMostOne("rodxxla", "rodilla")) // dos cambiadas
        assertFalse(DiscomfortSearch.editDistanceAtMostOne("rdlla", "rodilla")) // dos de menos
    }

    @Test
    fun `consulta vacia o sin sentido no devuelve nada ni none`() {
        assertTrue(DiscomfortSearch.search("").isEmpty())
        assertTrue(DiscomfortSearch.search("   ").isEmpty())
        assertTrue(DiscomfortSearch.search("¿?").isEmpty())
        assertTrue(DiscomfortSearch.search("zzzzz").isEmpty())
        assertFalse(ids("sin molestias").contains("none"))
        assertFalse(ids("molestias").contains("none"))
    }

    @Test
    fun `normalize quita tildes signos y espacios dobles`() {
        assertEquals("muneca mano", DiscomfortSearch.normalize("Muñeca / mano"))
        assertEquals("rodilla interna externa", DiscomfortSearch.normalize("  Rodilla   interna/externa "))
    }

    @Test
    fun `determinista`() {
        assertEquals(DiscomfortSearch.search("cadera"), DiscomfortSearch.search("cadera"))
        assertEquals(listOf("hip_front", "hip_lateral"), ids("cadera").take(2))
    }
}
