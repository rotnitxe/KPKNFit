package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExerciseAliasesTest {

    private val catalog: ExerciseCatalogV2 by lazy {
        ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText())
    }

    @Test
    fun `cada id de la tabla existe en el catalogo real y tiene configuraciones validas`() {
        val definitions = catalog.families.flatMap { it.definitions }.associateBy { it.id }

        for ((phrase, alias) in ImportExerciseAliases.entries) {
            val definition = requireNotNull(definitions[alias.definitionId]) {
                "«$phrase» apunta a un id que no existe: ${alias.definitionId}"
            }
            assertTrue("«$phrase»: ${definition.id} sin configuraciones", definition.configurations.isNotEmpty())
            alias.configurationId?.let { id ->
                assertTrue("«$phrase»: no existe la configuración $id", definition.configurations.any { it.id == id })
            }
            alias.equipmentId?.let { equipment ->
                assertTrue(
                    "«$phrase»: ${definition.id} no tiene material $equipment",
                    definition.configurations.any { it.profile.equipmentId == equipment },
                )
            }
        }
    }

    @Test
    fun `no hay frases duplicadas tras normalizar y son al menos 200`() {
        val keys = ImportExerciseAliases.entries.map { ImportExerciseAliases.fullKey(it.first) }
        val repeated = keys.groupBy { it }.filterValues { it.size > 1 }.keys

        assertTrue("frases duplicadas: $repeated", repeated.isEmpty())
        assertTrue("solo ${keys.size} frases", keys.size >= 200)
    }

    @Test
    fun `una muestra de frases conocidas da la definicion esperada`() {
        val expected = listOf(
            "Sentadilla" to "high_bar_back_squat",
            "Squat" to "high_bar_back_squat",
            "Barbell Row" to "conventional_row",
            "Peso muerto rumano" to "romanian_deadlift",
            "RDL" to "romanian_deadlift",
            "SLDL" to "stiff_leg_deadlift",
            "OHP" to "military_press",
            "Press militar" to "military_press",
            "Press banca" to "bench_press",
            "Bench press" to "bench_press",
            "Incline bench press" to "incline_bench_press",
            "Press inclinado" to "incline_bench_press",
            "Decline bench press" to "decline_bench_press",
            "Close grip bench press" to "close_grip_bench_press",
            "CGBP" to "close_grip_bench_press",
            "Pull-up" to "pull_up",
            "Chin-ups" to "pull_up",
            "Face pulls" to "deltoides_face_pull",
            "Facepulls" to "deltoides_face_pull",
            "Flyes" to "flat_chest_fly",
            "Flys" to "flat_chest_fly",
            "Hammer Curls" to "hammer_curl",
            "Seated Calf Raise" to "calf_raise",
            "EZ Bar Curl" to "standing_biceps_curl",
            "Lat Pulldown" to "lat_pulldown",
            "Leg Curl" to "lying_leg_curl",
            "Lateral raise" to "standing_lateral_raise",
            "Hip thrust" to "hip_thrust",
            "Pause squat" to "paused_back_squat",
            "Deficit deadlift" to "hams_peso_muerto_convencional_deficit",
            "Dips" to "tren_superior_fondos",
        )
        for ((phrase, definitionId) in expected) {
            assertEquals("«$phrase»", definitionId, ImportExerciseAliases.lookup(phrase)?.definitionId)
        }
    }

    @Test
    fun `las abreviaturas de material solas no eligen ejercicio`() {
        assertNull(ImportExerciseAliases.lookup("BB"))
        assertNull(ImportExerciseAliases.lookup("DB"))
        assertNull(ImportExerciseAliases.lookup("EZ"))
    }
}
