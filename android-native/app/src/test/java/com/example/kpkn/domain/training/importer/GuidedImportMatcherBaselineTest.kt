package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small synthetic label set; top suggestion and automatic precision are gates, and eligible coverage is reported separately. */
class GuidedImportMatcherBaselineTest {
    @Test
    fun `automatic catalog assignments meet the 95 percent precision gate`() {
        val matcher = ExerciseNameMatcher(
            ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText()),
        )
        val examples = listOf(
            "Sentadilla Libre con Barra Olímpica" to "high_bar_back_squat__barbell",
            "Sentadilla trasera barra baja" to "low_bar_back_squat__barbell",
            "Sentadilla frontal con barra" to "front_squat__barbell",
            "Barbell squat" to "high_bar_back_squat__barbell",
            "Press banca con mancuernas" to "bench_press__dumbbells",
            "Press banca con barra" to "bench_press__barbell",
            "Peso muerto con barra hexagonal" to "conventional_deadlift__bilateral__hex_bar",
            "Seated Triceps Press" to "triceps_press_maquina__default",
        )
        var automatic = 0
        var correct = 0
        var correctTopSuggestion = 0
        val manualReview = mutableListOf<String>()
        examples.forEach { (input, expected) ->
            val result = matcher.match(input)
            if (result.best?.configurationId == expected) correctTopSuggestion++
            val auto = matcher.canResolveAutomatically(result)
            if (auto) {
                automatic++
                if (result.best?.configurationId == expected) correct++
            } else {
                manualReview += input
            }
        }
        assertTrue("At least one labeled name should be eligible for automatic assignment", automatic > 0)
        assertEquals(
            "Generic movement names must remain in human review",
            setOf("Barbell squat", "Peso muerto con barra hexagonal"),
            manualReview.toSet(),
        )
        val precision = correct.toDouble() / automatic
        val suggestionAccuracy = correctTopSuggestion.toDouble() / examples.size
        println("Guided matcher corpus: top suggestions=$correctTopSuggestion/${examples.size}; automatic coverage=$automatic/${examples.size}; precision=$correct/$automatic; manual review=${manualReview.joinToString()}")
        assertTrue("Top catalog suggestion accuracy $correctTopSuggestion/${examples.size} must be at least 95%", suggestionAccuracy >= 0.95)
        assertTrue("Automatic match precision $correct/$automatic must be at least 95%", precision >= 0.95)
    }
}
