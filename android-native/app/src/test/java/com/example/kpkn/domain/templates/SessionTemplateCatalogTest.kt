package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.domain.training.VolumeCalculator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SessionTemplateCatalogTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var exerciseDatabase: List<ExerciseMuscleInfo>
        private lateinit var exerciseDatabaseById: Map<String, ExerciseMuscleInfo>
        private lateinit var exerciseAliases: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val dbFile = findDbFile("exercise_database.json")
            val aliasesFile = findDbFile("exercise_id_aliases.json")

            exerciseDatabase = json.decodeFromString<List<ExerciseMuscleInfo>>(dbFile.readText())
            exerciseDatabaseById = exerciseDatabase.associateBy { it.id.lowercase() }
            exerciseAliases = json.decodeFromString<Map<String, String>>(aliasesFile.readText())
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.lowercase() }
        }

        private fun findDbFile(fileName: String): File {
            val resource = SessionTemplateCatalogTest::class.java.classLoader?.getResource(fileName)
            if (resource != null) return File(resource.toURI())

            val candidates = listOf(
                "src/main/assets/$fileName",
                "../app/src/main/assets/$fileName",
                "app/src/main/assets/$fileName",
                "android-native/app/src/main/assets/$fileName",
                "../android-native/app/src/main/assets/$fileName"
            )
            for (path in candidates) {
                val f = File(path)
                if (f.exists()) return f
            }
            error("No se encontró $fileName.")
        }

        fun resolveExerciseId(rawId: String?): String? {
            val normalized = rawId?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) return null
            if (exerciseDatabaseById.containsKey(normalized)) return normalized
            val canonical = exerciseAliases[normalized] ?: return null
            return canonical.takeIf { exerciseDatabaseById.containsKey(it) }
        }

        fun resolveExercise(rawId: String?): ExerciseMuscleInfo? =
            resolveExerciseId(rawId)?.let { exerciseDatabaseById[it] }
    }

    @Test
    fun systemTemplatesUseOnlyExerciseDatabaseIds() {
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val exercises = template.session.exercises + template.session.parts.flatMap { it.exercises }
            exercises.forEach { exercise ->
                val dbId = exercise.exerciseDbId
                assertNotNull("La plantilla '${template.name}' contiene un ejercicio '${exercise.name}' con dbId nulo", dbId)
                val resolvedId = resolveExerciseId(dbId)
                assertNotNull("El ejercicio '${exercise.name}' con dbId '$dbId' en la plantilla '${template.name}' no existe en exercise_database.json ni en aliases", resolvedId)
            }
        }
    }

    @Test
    fun systemTemplateNamesMatchDatabase() {
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val exercises = template.session.exercises + template.session.parts.flatMap { it.exercises }
            exercises.forEach { exercise ->
                val resolved = resolveExercise(exercise.exerciseDbId)
                assertNotNull("Ejercicio con dbId '${exercise.exerciseDbId}' no pudo ser resuelto", resolved)
                val matchesName = exercise.name.equals(resolved!!.name, ignoreCase = true)
                val matchesAlias = resolved.alias?.let { exercise.name.equals(it, ignoreCase = true) } ?: false

                assertTrue(
                    "El nombre '${exercise.name}' en la plantilla '${template.name}' no coincide con el nombre oficial '${resolved.name}' ni con su alias '${resolved.alias}'",
                    matchesName || matchesAlias
                )
            }
        }
    }

    @Test
    fun customSplitIsExcludedFromTemplateCategories() {
        val groups = SPLIT_TEMPLATES.flatMap { split ->
            SessionTemplateCatalogPolicy.templatesForSplit(split, SESSION_TEMPLATES_SYSTEM, exerciseDatabaseById)
        }
        assertTrue("No debe haber grupos para split 'custom'", groups.none { it.splitId == "custom" })
    }

    @Test
    fun splitTemplatesRespectWeeklyVolumePolicy() {
        val failures = mutableListOf<String>()
        val nonCustomSplits = SPLIT_TEMPLATES.filterNot { 
            it.id == "custom" ||
            it.tags.contains(SplitTag.POWERLIFTING)
        }
        nonCustomSplits.forEach { split ->
            val dayGroups = SessionTemplateCatalogPolicy.templatesForSplit(split, SESSION_TEMPLATES_SYSTEM, exerciseDatabaseById)
            val sessions = dayGroups.mapNotNull { group ->
                group.templates.firstOrNull()?.session
            }
            if (sessions.isEmpty()) return@forEach

            val weeklyVol = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(
                sessions, exerciseDatabase, 1.0
            )

            weeklyVol.forEach { entry ->
                val range = SessionTemplateCatalogPolicy.WEEKLY_VOLUME_RANGES[entry.muscleName]
                if (range != null) {
                    if (entry.weeklySets > range.endInclusive + 2.0) {
                        failures += "El split '${split.name}' excede el volumen semanal óptimo para '${entry.muscleName}': ${entry.weeklySets} series (máx: ${range.endInclusive})"
                    }
                }
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun nonCustomSplitsHaveTemplatesForEveryTrainingDay() {
        SPLIT_TEMPLATES.filterNot { it.id == "custom" }.forEach { split ->
            val dayGroups = SessionTemplateCatalogPolicy.templatesForSplit(split, SESSION_TEMPLATES_SYSTEM, exerciseDatabaseById)
            val trainingDays = split.pattern.filterNot { it.equals("Descanso", ignoreCase = true) }

            assertEquals(
                "El split '${split.name}' debe exponer un grupo por cada día de entrenamiento",
                trainingDays.size,
                dayGroups.size,
            )
            dayGroups.forEach { group ->
                assertTrue(
                    "El día '${group.dayLabel}' del split '${split.name}' no tiene plantillas compatibles",
                    group.templates.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun singleTemplatesRespectSessionVolumeCap() {
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val volMap = SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseDatabaseById)
            volMap.forEach { (muscle, sets) ->
                assertTrue(
                    "La plantilla '${template.name}' excede el límite de volumen por sesión para '$muscle': $sets series",
                    sets <= 12.0
                )
            }
        }
    }

    @Test
    fun templatesRespectRingsDrainCaps() {
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val drain = SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseDatabaseById)
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
            val maxCns = if (isPl) 45 else 35
            val maxMuscular = if (isPl) 50 else 45
            val maxSpinal = if (isPl) 40 else 30

            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga SNC: ${drain.cns}% > $maxCns%",
                drain.cns <= maxCns
            )
            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga Muscular: ${drain.muscular}% > $maxMuscular%",
                drain.muscular <= maxMuscular
            )
            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga Espinal: ${drain.spinal}% > $maxSpinal%",
                drain.spinal <= maxSpinal
            )
        }
    }

    @Test
    fun independentTemplatesAreNotLinkedToSplits() {
        val groups = SessionTemplateCatalogPolicy.independentTemplateGroups(SESSION_TEMPLATES_SYSTEM)
        val categories = groups.map { it.category }.toSet()
        assertEquals(
            "Debe existir al menos una plantilla independiente por cada enfoque del catálogo",
            SessionTemplateFocusCategory.entries.toSet(),
            categories,
        )
        groups.forEach { group ->
            group.templates.forEach { template ->
                assertTrue(
                    "La plantilla independiente '${template.name}' de enfoque '${group.category}' no debe estar asociada a ningún split",
                    template.splitIds.isEmpty()
                )
            }
        }
    }

    @Test
    fun userTemplatesAreGroupedSeparatelyFromFocusTemplates() {
        val userTemplate = SESSION_TEMPLATES_SYSTEM.first().copy(
            id = "user-template-test",
            sourceType = SessionTemplateSourceType.USER,
            splitIds = emptyList(),
            splitDayLabels = emptyList(),
            focusCategory = null,
        )
        val archivedUserTemplate = userTemplate.copy(
            id = "user-template-archived-test",
            isArchived = true,
        )
        val templates = SESSION_TEMPLATES_SYSTEM + userTemplate + archivedUserTemplate

        val userGroup = SessionTemplateCatalogPolicy.userTemplateGroup(templates)
        val independentIds = SessionTemplateCatalogPolicy.independentTemplateGroups(templates)
            .flatMap { it.templates }
            .map { it.id }

        assertEquals(listOf(userTemplate.id), userGroup.templates.map { it.id })
        assertFalse("Las plantillas de usuario no deben mezclarse con enfoques independientes", independentIds.contains(userTemplate.id))
    }

    @Test
    fun splitTemplatesHaveSplitMetadata() {
        val splitTemplates = SESSION_TEMPLATES_SYSTEM.filter { it.splitIds.isNotEmpty() }
        splitTemplates.forEach { template ->
            assertTrue(
                "La plantilla de split '${template.name}' debe tener splitDayLabels definidos",
                template.splitDayLabels.isNotEmpty()
            )
            template.splitIds.forEach { splitId ->
                val split = SPLIT_TEMPLATES.find { it.id == splitId }
                assertNotNull("El splitId '$splitId' en la plantilla '${template.name}' no existe en SPLIT_TEMPLATES", split)
            }
        }
    }
}
