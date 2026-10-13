package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nombres en inglés, con notación, plurales y abreviaturas (propuesta v6, H6b). Frases sintéticas, sin corpus. */
class ExerciseNameMatcherEnglishTest {

    private val matcher by lazy { ExerciseNameMatcher(loadCatalog()) }

    @Test
    fun `los plurales ingleses encuentran su ejercicio`() {
        assertConfident("Seated Cable Rows", "gironda_row__medium")
        assertConfident("Leg Raises", "core_elevacion_piernas__default")
        assertConfident("Incline Dumbbell Flyes", "incline_chest_fly__dumbbells")
    }

    @Test
    fun `la notacion pegada de cargas, repeticiones y pausas no cambia el ejercicio`() {
        assertConfident("Barbell Curl 25", "standing_biceps_curl__barbell")
        assertConfident("Bent Over Row[1-4]", "conventional_row__barbell")
        assertConfident("Chinups (30)", "pull_up__supinated__medium")
        assertConfident("2ct Pause Squat", "paused_back_squat__barbell")
    }

    @Test
    fun `las abreviaturas del gimnasio se reconocen`() {
        assertConfident("Leg Ext", "quads_extension_cuadriceps__machine__bilateral")
        assertConfident("SSB Squat", "high_bar_back_squat__safety_bar")
        assertConfident("CG Bench Press", "close_grip_bench_press__barbell")
        assertConfident("Incline DB", "incline_bench_press__dumbbells")
    }

    @Test
    fun `los codigos de bloque y de hoja se quitan antes de buscar`() {
        assertConfident("ACC2 ACC Lat Pulldown", "lat_pulldown__bilateral__cable")
        assertConfident("PD DEADLIFT Deadlift", "conventional_deadlift__bilateral__barbell")
        assertConfident("A1: Bodyweight Glute Bridge", "glutes_puente_gluteos__bilateral__bodyweight")
        assertConfident("#REF! SQUAT Squat", "high_bar_back_squat__barbell")
    }

    @Test
    fun `las cabeceras y la prosa no dan candidatos`() {
        for (header in listOf("1 Fri Heavy", "3 day (Wednesday)", "Bench Warm-up", "t3 used: none", "- CHOOSE -")) {
            val match = matcher.match(header)
            assertFalse(header, match.isConfident)
            assertTrue(header, match.candidates.isEmpty())
        }
    }

    @Test
    fun `los negativos no se reconocen solos`() {
        val negatives = listOf(
            "Pin Squat",
            "Pause Deadlift",
            "BTN Push-Press",
            "Seated Leg Press",
            "Biceps",
            "Chest",
            "Back",
            "Pulldowns / Pullups / Chinups",
            "Weighted Chin-ups",
            "Bench Press / Overhead Press / Squat",
        )
        for (name in negatives) assertFalse(name, matcher.match(name).isConfident)
    }

    @Test
    fun `pause deadlift no sugiere el monster walk ni nada por debajo de 0,3`() {
        assertTrue(matcher.match("Pause Deadlift").candidates.isEmpty())
    }

    @Test
    fun `standing tricep extensions no sugiere la version tumbada`() {
        val match = matcher.match("Standing Tricep Extensions with Barbell")

        assertFalse(match.isConfident)
        assertTrue(match.candidates.none { it.displayName.contains("Tumbada") })
        assertEquals("overhead_triceps__barbell", match.best?.configurationId)
    }

    @Test
    fun `la version tumbada sigue sugiriendose si el nombre dice tumbado`() {
        val match = matcher.match("Standing Lying Triceps Extension")

        assertFalse(match.isConfident)
        assertEquals("Extensión de Tríceps Tumbada", match.best?.displayName)
    }

    @Test
    fun `calves sugiere la elevacion de talones sin reconocerla sola`() {
        val match = matcher.match("Calves")

        assertFalse(match.isConfident)
        assertEquals("Elevación de Talones", match.best?.displayName)
    }

    @Test
    fun `bodyweight box squat sugiere la sentadilla a cajon sin reconocerla sola`() {
        val match = matcher.match("Bodyweight Box Squat")

        assertFalse(match.isConfident)
        assertEquals("quads_sentadilla_cajon__default", match.best?.configurationId)
    }

    @Test
    fun `una de dos opciones en ingles se reconoce solo si las dos se reconocen`() {
        val match = matcher.match("Press banca / press inclinado")

        assertEquals("bench_press__barbell", match.best?.configurationId)
        assertTrue(match.isConfident)
        assertEquals("press inclinado", match.alternative)
    }

    @Test
    fun `los alias nuevos de la propuesta v6 reconocen su ejercicio`() {
        assertConfident("Bent Over One Arm Row", "conventional_row__dumbbells")
        assertConfident("1 Arm DB Row", "conventional_row__dumbbells")
        assertConfident("Pull Down", "lat_pulldown__bilateral__cable")
        assertConfident("Stiff Legged Deadlift", "stiff_leg_deadlift__bilateral__barbell")
        assertConfident("Good Morning Sitting", "good_morning_seated__barbell")
        assertConfident("Calf Raise Smith Machine", "calf_raise__bilateral__smith_machine")
        assertConfident("Pause Bench", "paused_bench_press__barbell")
        assertConfident("Flat Bench", "bench_press__barbell")
        assertConfident("Triceps Dip", "triceps_fondos_entre_bancos__default")
        assertConfident("Bicep Curl", "standing_biceps_curl__barbell")
        assertConfident("Barbell Curl", "standing_biceps_curl__barbell")
        assertConfident("Back Ext", "back_extension_lumbar__default")
        assertConfident("Ab Wheel Rollout", "core_rueda_abdominal__default")
        assertConfident("Close Grip Bench", "close_grip_bench_press__barbell")
        assertConfident("Wide Grip Pull Up", "pull_up__pronated__wide")
        assertConfident("Cable Rope Pushdown", "triceps_pushdown__bilateral__cable")
        assertConfident("Band Pressdown", "triceps_pushdown__bilateral__band")
        assertConfident("Calf Press", "calf_raise__bilateral__leg_press_machine")
        assertConfident("Deadlift Up To Knees", "deadlift_to_knees__barbell")
        assertConfident("Dip Triceps Version", "triceps_fondos_entre_bancos__default")
        assertConfident("Safety Bar Squat", "high_bar_back_squat__safety_bar")
        assertConfident("Incline Bench", "incline_bench_press__barbell")
        assertConfident("Incline Dumbbell Fly", "incline_chest_fly__dumbbells")
        assertConfident("Bent Over Rear Delt Raise", "rear_delt_raise__dumbbells")
        assertConfident("Good Morning Standing", "good_morning__bilateral__barbell")
    }

    @Test
    fun `un alias nuevo no cambia un nombre que la primera pasada ya reconoce`() {
        assertEquals("bench_press__barbell", matcher.match("Press banca").best?.configurationId)
        assertNull(ImportExerciseAliases.lookup("Flat bench"))
    }

    @Test
    fun `hip abductor no sugiere la aduccion aunque las dos palabras se parezcan`() {
        for (name in listOf("Hip Abductor", "Hip Abductor, Band[1-4]")) {
            val match = matcher.match(name)

            assertFalse(name, match.isConfident)
            assertTrue(name, match.candidates.none { it.displayName.contains("Aducciones") })
        }
    }

    /** Reconocido con confianza y con esa configuración. */
    private fun assertConfident(name: String, configurationId: String) {
        val match = matcher.match(name)
        assertTrue("«$name» debe reconocerse: ${match.candidates}", match.isConfident)
        assertEquals("«$name»", configurationId, match.best?.configurationId)
    }

    private fun loadCatalog(): ExerciseCatalogV2 {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        return ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
}
