package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseNameMatcherTest {

    private val matcher by lazy { ExerciseNameMatcher(loadCatalog()) }

    @Test
    fun `sentadilla trasera encuentra una sentadilla con barra`() {
        val match = matcher.match("Sentadilla trasera")

        val best = requireNotNull(match.best)
        assertTrue(best.configurationId.endsWith("__barbell"))
        assertTrue(best.displayName.contains("Sentadilla"))
        assertTrue(best.confidence >= 0.8)
    }

    @Test
    fun `press banca va a la configuracion con barra y es confiable`() {
        val match = matcher.match("Press banca")

        assertEquals("bench_press__barbell", match.best?.configurationId)
        assertTrue(match.isConfident)
    }

    @Test
    fun `jalon al pecho encuentra el jalon`() {
        val match = matcher.match("Jalón al pecho")

        assertTrue(match.best?.configurationId.orEmpty().startsWith("lat_pulldown__"))
    }

    @Test
    fun `una errata sigue encontrando la sentadilla`() {
        val match = matcher.match("sentadila")

        assertTrue(match.candidates.isNotEmpty())
        assertTrue(match.best?.displayName.orEmpty().contains("sentadilla", ignoreCase = true))
    }

    @Test
    fun `un nombre inventado no es confiable y el lote tiene como mucho tres candidatos`() {
        assertFalse(matcher.match("ejercicio inventado xyz").isConfident)

        val batch = matcher.matchAll(listOf("Press banca", "Press banca", " Jalón al pecho ", ""))

        assertEquals(setOf("Press banca", "Jalón al pecho"), batch.keys)
        assertTrue(batch.values.all { it.candidates.size <= 3 })
    }

    @Test
    fun `el material del nombre elige la configuracion de ese equipo`() {
        assertEquals("bench_press__dumbbells", matcher.match("Press banca con mancuernas").best?.configurationId)
    }

    @Test
    fun `jalon en polea va a una configuracion de cable`() {
        val best = matcher.match("Jalón en polea").best

        assertTrue(best?.configurationId.orEmpty().startsWith("lat_pulldown__"))
        assertEquals("cable", best?.equipmentId)
    }

    @Test
    fun `peso muerto hexagonal va a la barra hexagonal`() {
        assertEquals(
            "conventional_deadlift__bilateral__hex_bar",
            matcher.match("Peso muerto hexagonal").best?.configurationId,
        )
    }

    @Test
    fun `press militar sin material sigue en la barra y ofrece otros materiales`() {
        val match = matcher.match("Press militar")

        assertEquals("military_press__barbell", match.best?.configurationId)
        assertTrue(match.candidates.map { it.equipmentId }.distinct().size > 1)
    }

    private fun loadCatalog(): ExerciseCatalogV2 {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        return ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
}
