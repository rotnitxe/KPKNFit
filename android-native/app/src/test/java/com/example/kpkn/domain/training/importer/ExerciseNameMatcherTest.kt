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

    @Test
    fun `barbell squat va a la trasera con barra alta con barra y es confiable`() {
        val match = matcher.match("Barbell Squat")

        assertEquals("high_bar_back_squat__barbell", match.best?.configurationId)
        assertTrue(match.isConfident)
    }

    @Test
    fun `barbell row va al remo convencional con barra y es confiable`() {
        val match = matcher.match("Barbell Row")

        assertEquals("conventional_row__barbell", match.best?.configurationId)
        assertTrue(match.isConfident)
    }

    @Test
    fun `incline dumbbell bench press va a la banca inclinada con mancuernas`() {
        assertEquals("incline_bench_press__dumbbells", matcher.match("Incline Dumbbell Bench Press").best?.configurationId)
    }

    @Test
    fun `romanian deadlift va al rumano basico y no al de deficit`() {
        val best = matcher.match("Romanian Deadlift").best

        assertEquals("romanian_deadlift__bilateral__barbell", best?.configurationId)
        assertEquals("Peso Muerto Rumano", best?.displayName)
    }

    @Test
    fun `squat no da la sentadilla en V`() {
        val best = matcher.match("Squat").best

        assertTrue(best?.configurationId.orEmpty().startsWith("high_bar_back_squat"))
        assertFalse(best?.displayName.orEmpty().contains("V-Squat"))
    }

    @Test
    fun `barbell bench press no da la banca con pausa`() {
        assertEquals("bench_press__barbell", matcher.match("Barbell Bench Press").best?.configurationId)
    }

    @Test
    fun `pause squat da la sentadilla con pausa`() {
        assertEquals("paused_back_squat__barbell", matcher.match("Pause Squat").best?.configurationId)
    }

    @Test
    fun `seated calf raise va a la elevacion de talones sentada`() {
        assertEquals("calf_raise__bilateral__seated_machine", matcher.match("Seated Calf Raise").best?.configurationId)
    }

    @Test
    fun `un material que el ejercicio no tiene no sale confiable`() {
        assertFalse(matcher.match("Dumbbell Hip Thrust").isConfident)
    }

    @Test
    fun `nombres basura no salen confiables`() {
        assertFalse(matcher.match("Warm-up 10 reps").isConfident)
        assertFalse(matcher.match("Set").isConfident)
        assertFalse(matcher.match("11 2").isConfident)
    }

    @Test
    fun `hyperextension e hypers van a la hiperextension de espalda baja y son confiables`() {
        val match = matcher.match("Hyperextension")

        assertEquals("back_extension_lumbar__default", match.best?.configurationId)
        assertTrue(match.isConfident)
        assertEquals("back_extension_lumbar__default", matcher.match("Hypers").best?.configurationId)
    }

    @Test
    fun `goblet squat va a la sentadilla copa y es confiable`() {
        val match = matcher.match("Goblet Squat")

        assertEquals("quads_sentadilla_copa__default", match.best?.configurationId)
        assertTrue(match.isConfident)
    }

    @Test
    fun `press con mancuernas sin alias no sale confiable`() {
        assertFalse(matcher.match("Dumbbell Press").isConfident)
        assertFalse(matcher.match("DB Press").isConfident)
        assertFalse(matcher.match("Press con mancuernas").isConfident)
    }

    private fun loadCatalog(): ExerciseCatalogV2 {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        return ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
}
