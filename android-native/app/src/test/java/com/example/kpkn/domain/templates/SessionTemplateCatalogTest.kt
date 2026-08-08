package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.protocols.ProtocolExerciseLibrary
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.VolumeCalculator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SessionTemplateCatalogTest {

    companion object {
        private lateinit var exerciseDatabase: List<ExerciseMuscleInfo>
        private lateinit var exerciseDatabaseById: Map<String, ExerciseMuscleInfo>
        /** Índice exacto de configuraciones v2; no contiene aliases ni nombres. */
        private lateinit var exerciseIndexWithAliases: Map<String, ExerciseMuscleInfo>
        private lateinit var catalogConfigurationIds: Set<String>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val catalog = ExerciseCatalogV2Loader.decodeApproved(findCatalogFile().readText())
            exerciseIndexWithAliases = catalog.toLegacyConfigurationLookup()
            exerciseDatabase = exerciseIndexWithAliases.values.toList()
            exerciseDatabaseById = exerciseIndexWithAliases
            catalogConfigurationIds = catalog.families
                .flatMap { family -> family.definitions }
                .flatMap { definition -> definition.configurations }
                .map { configuration -> configuration.id }
                .toSet()
        }

        private fun findCatalogFile(): File {
            val resource = SessionTemplateCatalogTest::class.java.classLoader?.getResource("exercise_catalog_v2.json")
            if (resource != null) return File(resource.toURI())

            val candidates = listOf(
                "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
                "../android-native/app/src/main/assets/exercise_catalog_v2.json",
                "android-native/app/src/main/assets/exercise_catalog_v2.json",
            )
            for (path in candidates) {
                val f = File(path)
                if (f.exists()) return f
            }
            error("No se encontró exercise_catalog_v2.json.")
        }

        fun resolveExerciseId(rawId: String?): String? {
            val normalized = rawId?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) return null
            return normalized.takeIf { exerciseDatabaseById.containsKey(it) }
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
                assertNotNull(
                    "La plantilla '${template.name}' contiene un ejercicio '${exercise.name}' con dbId nulo",
                    dbId,
                )
                assertTrue(
                    "El ejercicio '${exercise.name}' con dbId '$dbId' en la plantilla '${template.name}' " +
                        "debe resolverse mediante el catálogo o sus aliases",
                    resolveExerciseId(dbId) != null,
                )
            }
        }
    }

    @Test
    fun systemTemplateNamesMatchDatabase() {
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val exercises = template.session.exercises + template.session.parts.flatMap { it.exercises }
            exercises.forEach { exercise ->
                val dbId = exercise.exerciseDbId
                assertNotNull(dbId)
                val official = resolveExercise(dbId)
                assertNotNull(
                    "Ejercicio con dbId '$dbId' no existe como entrada canónica ni alias resoluble",
                    official,
                )
                assertTrue(
                    "El nombre '${exercise.name}' en la plantilla '${template.name}' no puede quedar vacío",
                    exercise.name.isNotBlank() && official!!.name.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun protocolExerciseLibraryUses_only_approved_catalog_configurations() {
        val referenced = setOf(
            ProtocolExerciseLibrary.SQUAT_MAIN,
            ProtocolExerciseLibrary.SQUAT_TECHNIQUE,
            ProtocolExerciseLibrary.BENCH_MAIN,
            ProtocolExerciseLibrary.BENCH_TECHNIQUE,
            ProtocolExerciseLibrary.DEADLIFT_MAIN,
            ProtocolExerciseLibrary.DEADLIFT_TECHNIQUE,
            ProtocolExerciseLibrary.OHP_MAIN,
        )
        // Include every private accessory pool through the public compiler so this
        // gate follows the same executable path as the generated protocol sessions.
        val generated = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
            .flatMap { protocol -> protocol.blocks }
            .flatMap { block ->
                listOf(
                    ProtocolExerciseLibrary.SQUAT_MAIN,
                    ProtocolExerciseLibrary.BENCH_MAIN,
                    ProtocolExerciseLibrary.DEADLIFT_MAIN,
                    ProtocolExerciseLibrary.OHP_MAIN,
                )
            }
            .flatMap { lift ->
                listOf(lift) + ProtocolExerciseLibrary.accessoriesFor(lift, weekNumber = 1, count = 3)
            }
        val missing = (referenced + generated.toSet())
            .map { lift -> lift.exerciseDbId }
            .filterNot { it in catalogConfigurationIds }
        assertTrue(
            "ProtocolExerciseLibrary contiene configurationId fuera del asset aprobado: $missing",
            missing.isEmpty(),
        )
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
            val caps = RingBudgetPolicy.sessionHardCaps(isPl)

            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga SNC: ${drain.cns}% > ${caps.cns}%",
                drain.cns <= caps.cns
            )
            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga Muscular: ${drain.muscular}% > ${caps.muscular}%",
                drain.muscular <= caps.muscular
            )
            assertTrue(
                "La plantilla '${template.name}' excede el límite de fatiga Espinal: ${drain.spinal}% > ${caps.spinal}%",
                drain.spinal <= caps.spinal
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

    @Test
    fun auditProducesCountsDurationAndMusclesForRealTemplate() {
        val template = SESSION_TEMPLATES_SYSTEM.first { it.id == "sys-push-ppl" }
        val audit = SessionTemplateAudit.audit(template, exerciseIndexWithAliases)

        val expectedExercises = template.session.exercises + template.session.parts.flatMap { it.exercises }
        assertEquals(expectedExercises.size, audit.exerciseCount)
        assertEquals(template.session.parts.size, audit.partCount)
        assertEquals(expectedExercises.sumOf { it.sets.size }, audit.totalSets)
        assertTrue("Debe haber series en Push Day", audit.totalSets > 0)
        assertTrue("Duración estimada debe ser positiva", audit.estimatedDurationMinutes >= SessionTemplateAudit.MIN_DURATION_MINUTES)
        assertTrue("Debe haber músculos primarios derivados de la DB", audit.primaryMuscleSets.isNotEmpty())
        assertTrue(
            "Push Day debe cargar pectorales como primario",
            audit.primaryMuscleSets.keys.any { it.contains("Pectoral", ignoreCase = true) },
        )
        assertNotNull("Debe poder promediar RPE/RIR de las series", audit.averageTargetRpe)
        assertTrue(
            "RPE medio coherente: ${audit.averageTargetRpe}",
            audit.averageTargetRpe!! in 5.0..10.0,
        )
        assertEquals(audit.exercises.size, audit.uniqueExerciseIds.size)
        assertTrue(
            "Todos los ejercicios de Push deben resolverse en el índice",
            audit.exercises.all { SessionTemplateAudit.resolveCatalogInfo(it, exerciseIndexWithAliases) != null },
        )
    }

    @Test
    fun catalogUsesAtLeast75UniqueExerciseIds() {
        val uniqueIds = linkedSetOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            template.session.allExercises().forEach { exercise ->
                val resolved = resolveExerciseId(
                    exercise.exerciseDbId ?: exercise.exerciseId ?: exercise.canonicalExerciseId,
                )
                assertNotNull("ID no resoluble en '${template.name}': ${exercise.exerciseDbId}", resolved)
                uniqueIds += resolved!!
            }
        }
        assertTrue(
            "Catálogo canónico consolidado debe usar >=70 IDs únicos (actual=${uniqueIds.size})",
            uniqueIds.size >= 70,
        )
    }

    @Test
    fun noSingleExerciseDominatesMoreThan35PercentOfTemplates() {
        val templateCount = SESSION_TEMPLATES_SYSTEM.size
        val presence = mutableMapOf<String, Int>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val idsInTemplate = template.session.allExercises()
                .mapNotNull { resolveExerciseId(it.exerciseDbId ?: it.exerciseId ?: it.canonicalExerciseId) }
                .toSet()
            idsInTemplate.forEach { id ->
                presence[id] = (presence[id] ?: 0) + 1
            }
        }
        val limit = templateCount * 0.35
        val offenders = presence.filter { it.value > limit }
            .entries
            .sortedByDescending { it.value }
            .map { (id, count) -> "$id en $count/${templateCount} plantillas (${"%.0f".format(100.0 * count / templateCount)}%)" }
        assertTrue(
            "Ningún ejercicio debe aparecer en >35% de plantillas:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun templateMetadataMatchesRealExerciseAndPartCounts() {
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val audit = SessionTemplateAudit.audit(template, exerciseIndexWithAliases)
            if (template.exerciseCount != audit.exerciseCount) {
                failures += "'${template.name}': exerciseCount=${template.exerciseCount} real=${audit.exerciseCount}"
            }
            if (template.partCount != audit.partCount) {
                failures += "'${template.name}': partCount=${template.partCount} real=${audit.partCount}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun templateTotalVolumeIsReasonable() {
        val lowVolumeIds = SESSION_TEMPLATES_SYSTEM
            .filter { it.id.endsWith("-low") || it.name.contains("Compacta", ignoreCase = true) }
            .map { it.id }
            .toSet()
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val totalSets = template.session.allExercises().sumOf { it.sets.size }
            val isLow = template.id in lowVolumeIds
            val range = if (isLow) 8..12 else 8..22
            if (totalSets !in range) {
                failures += "'${template.name}' (${template.id}): $totalSets series fuera de $range" +
                    if (isLow) " [low]" else ""
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun declaredDurationStaysWithinAuditDivergence() {
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val audit = SessionTemplateAudit.audit(template, exerciseIndexWithAliases)
            val declared = template.estimatedDurationMinutes
            if (declared == null || declared <= 0 || audit.estimatedDurationMinutes <= 0) {
                failures += "'${template.name}': duración declarada inválida ($declared)"
                return@forEach
            }
            val ratio = abs(declared - audit.estimatedDurationMinutes).toDouble() /
                audit.estimatedDurationMinutes.toDouble()
            if (ratio > SessionTemplateAudit.DURATION_DIVERGENCE_RATIO) {
                failures += "'${template.name}': declarada=$declared estimada=${audit.estimatedDurationMinutes} " +
                    "(desvío ${(ratio * 100).toInt()}%)"
            }
            val durationIssues = audit.issues.filter { it.kind == SessionTemplateAuditIssueKind.DURATION_DIVERGENT }
            if (durationIssues.isNotEmpty()) {
                failures += durationIssues.joinToString { it.message }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun primaryCoverageIncludesWeeklyVolumeMuscles() {
        val covered = mutableSetOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val audit = SessionTemplateAudit.audit(template, exerciseIndexWithAliases)
            covered += audit.primaryMuscleSets.keys
        }
        // Antebrazo no está en WEEKLY_VOLUME_RANGES; el resto debe tener cobertura primaria.
        val required = SessionTemplateCatalogPolicy.WEEKLY_VOLUME_RANGES.keys
        val missing = required.filter { muscle ->
            covered.none { it.equals(muscle, ignoreCase = true) }
        }
        assertTrue(
            "Falta cobertura primaria de músculos semanales: $missing\nCubiertos=$covered",
            missing.isEmpty(),
        )
    }

    @Test
    fun auditResolvesAllSystemTemplateExerciseIds() {
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val audit = SessionTemplateAudit.audit(template, exerciseIndexWithAliases)
            audit.exercises.forEach { exercise ->
                val viaAliasHelper = resolveExerciseId(
                    exercise.exerciseDbId ?: exercise.exerciseId ?: exercise.canonicalExerciseId,
                )
                val viaAudit = SessionTemplateAudit.resolveCatalogInfo(exercise, exerciseIndexWithAliases)
                if (viaAliasHelper == null) {
                    failures += "'${template.name}': '${exercise.name}' sin ID resoluble " +
                        "(dbId=${exercise.exerciseDbId})"
                }
                if (viaAudit == null) {
                    failures += "'${template.name}': '${exercise.name}' no resuelve en índice con aliases " +
                        "(dbId=${exercise.exerciseDbId})"
                }
            }
            audit.uniqueExerciseIds.forEach { id ->
                if (!exerciseDatabaseById.containsKey(id.lowercase())) {
                    failures += "'${template.name}': uniqueExerciseId '$id' no es canónico de la DB"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun auditConvertsRirToRpeWhenRpeMissing() {
        val catalogId = exerciseDatabase.first().id
        val session = Session(
            id = "audit-rir",
            name = "RIR probe",
            exercises = listOf(
                Exercise(
                    id = "ex1",
                    name = "Probe",
                    exerciseDbId = catalogId,
                    sets = listOf(
                        ExerciseSet(id = "s1", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR),
                        ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
                    ),
                ),
            ),
        )
        val audit = SessionTemplateAudit.audit(session, exerciseIndexWithAliases)
        // (10-2)=8 y 8.0 → media 8.0
        assertEquals(8.0, audit.averageTargetRpe!!, 0.01)
    }

    @Test
    fun issuesDetectInconsistentMetadataAndDivergentDuration() {
        val real = SESSION_TEMPLATES_SYSTEM.first()
        val firstExercise = real.session.allExercises().first()
        val info = SessionTemplateAudit.resolveCatalogInfo(firstExercise, exerciseIndexWithAliases)
        assertNotNull(info)

        val broken = SessionTemplate(
            id = "audit-broken-meta",
            sourceType = SessionTemplateSourceType.USER,
            name = "Broken Meta",
            description = "Plantilla sintética para issues",
            difficulty = Difficulty.INTERMEDIO,
            estimatedDurationMinutes = 120,
            exerciseCount = 99,
            partCount = 99,
            session = Session(
                id = "broken-session",
                name = "Broken Meta",
                parts = listOf(
                    SessionPart(
                        id = "p1",
                        name = "Única",
                        exercises = listOf(
                            firstExercise.copy(
                                id = "broken-ex",
                                sets = listOf(
                                    ExerciseSet(
                                        id = "s1",
                                        targetReps = 10,
                                        targetRPE = 12.0,
                                        intensityMode = IntensityMode.RPE,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val audit = SessionTemplateAudit.audit(broken, exerciseIndexWithAliases)
        val kinds = audit.issues.map { it.kind }.toSet()

        assertTrue(
            "Debe marcar metadata inconsistente: ${audit.issues}",
            SessionTemplateAuditIssueKind.INCONSISTENT_METADATA in kinds,
        )
        assertTrue(
            "Debe marcar intensidad fuera de rango: ${audit.issues}",
            SessionTemplateAuditIssueKind.INTENSITY_OUT_OF_RANGE in kinds,
        )
        assertTrue(
            "Debe marcar duración muy desviada: ${audit.issues}",
            SessionTemplateAuditIssueKind.DURATION_DIVERGENT in kinds,
        )
        assertEquals(1, audit.exerciseCount)
        assertEquals(1, audit.partCount)
        assertTrue(audit.estimatedDurationMinutes < 120)
        assertTrue(abs(120 - audit.estimatedDurationMinutes).toDouble() / audit.estimatedDurationMinutes > SessionTemplateAudit.DURATION_DIVERGENCE_RATIO)
    }
}
