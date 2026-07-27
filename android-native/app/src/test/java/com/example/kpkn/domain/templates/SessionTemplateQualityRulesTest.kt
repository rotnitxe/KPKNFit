package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SessionTemplateQualityRulesTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var exerciseDatabaseById: Map<String, ExerciseMuscleInfo>
        private lateinit var exerciseAliases: Map<String, String>
        private lateinit var exerciseIndexWithAliases: Map<String, ExerciseMuscleInfo>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val dbFile = findDbFile("exercise_database.json")
            val aliasesFile = findDbFile("exercise_id_aliases.json")

            val exerciseDatabase = json.decodeFromString<List<ExerciseMuscleInfo>>(dbFile.readText())
            exerciseDatabaseById = exerciseDatabase.associateBy { it.id.lowercase() }
            exerciseAliases = json.decodeFromString<Map<String, String>>(aliasesFile.readText())
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.lowercase() }
            val merged = exerciseDatabaseById.toMutableMap()
            exerciseAliases.forEach { (alias, canonical) ->
                exerciseDatabaseById[canonical]?.let { merged[alias] = it }
            }
            exerciseIndexWithAliases = merged
        }

        private fun findDbFile(fileName: String): File {
            val resource = SessionTemplateQualityRulesTest::class.java.classLoader?.getResource(fileName)
            if (resource != null) return File(resource.toURI())

            val candidates = listOf(
                "src/main/assets/$fileName",
                "../app/src/main/assets/$fileName",
                "app/src/main/assets/$fileName",
                "android-native/app/src/main/assets/$fileName",
                "../android-native/app/src/main/assets/$fileName",
            )
            for (path in candidates) {
                val f = File(path)
                if (f.exists()) return f
            }
            error("No se encontró $fileName.")
        }
    }

    @Test
    fun systemTemplatesHaveNoP0QualityViolations() {
        val violations = SessionTemplateQualityRules.p0Violations(
            SESSION_TEMPLATES_SYSTEM,
            exerciseIndexWithAliases,
        )
        val details = violations.joinToString("\n") { report ->
            val codes = report.p0.joinToString { "${it.code}: ${it.message}" }
            "${report.templateId} → $codes"
        }
        assertTrue(
            "Plantillas sistema con P0 (esperado rojo hasta F1):\n$details",
            violations.isEmpty(),
        )
    }

    @Test
    fun syntheticTemplateTriggersSameMuscleStreakAndBeginnerHardBw() {
        val nordic = ExerciseMuscleInfo(
            id = "nordic_curl",
            name = "Nordic Curl",
            type = "Aislamiento",
            equipment = "Peso corporal",
            involvedMuscles = listOf(
                InvolvedMuscle(muscle = "Isquiosurales", role = MuscleRole.PRIMARY),
            ),
        )
        val legCurl = ExerciseMuscleInfo(
            id = "leg_curl_machine",
            name = "Curl Femoral Máquina",
            type = "Aislamiento",
            equipment = "Máquina",
            involvedMuscles = listOf(
                InvolvedMuscle(muscle = "Isquiosurales", role = MuscleRole.PRIMARY),
            ),
        )
        val index = mapOf(
            nordic.id to nordic,
            legCurl.id to legCurl,
        )

        fun set(id: String) = ExerciseSet(
            id = id,
            targetReps = 8,
            targetRPE = 7.0,
            intensityMode = IntensityMode.RPE,
        )

        val template = SessionTemplate(
            id = "synthetic-bad-beginner",
            sourceType = SessionTemplateSourceType.USER,
            name = "Sesión sintética mala",
            description = "Test",
            difficulty = Difficulty.PRINCIPIANTE,
            session = Session(
                id = "s1",
                name = "Bad",
                exercises = listOf(
                    Exercise(
                        id = "e1",
                        name = "Nordic Curl",
                        exerciseDbId = nordic.id,
                        sets = listOf(set("s1")),
                    ),
                    Exercise(
                        id = "e2",
                        name = "Curl Femoral Máquina",
                        exerciseDbId = legCurl.id,
                        sets = listOf(set("s2")),
                    ),
                    Exercise(
                        id = "e3",
                        name = "Curl Femoral Máquina B",
                        exerciseDbId = legCurl.id,
                        sets = listOf(set("s3")),
                    ),
                ),
            ),
        )

        val report = SessionTemplateQualityRules.audit(template, index)
        val codes = report.issues.map { it.code }.toSet()
        assertTrue(
            "Debe flaggear BEGINNER_HARD_BW, got: $codes",
            "BEGINNER_HARD_BW" in codes,
        )
        assertTrue(
            "Debe flaggear SAME_MUSCLE_STREAK, got: $codes",
            "SAME_MUSCLE_STREAK" in codes,
        )
        assertFalse("No debe haber códigos vacíos", codes.isEmpty())
        assertTrue(
            "Ambos deben ser P0",
            report.p0.any { it.code == "BEGINNER_HARD_BW" } &&
                report.p0.any { it.code == "SAME_MUSCLE_STREAK" },
        )
    }
}
