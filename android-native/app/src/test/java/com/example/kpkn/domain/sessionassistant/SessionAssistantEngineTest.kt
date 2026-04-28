package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.sessions.SessionTemplateTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAssistantEngineTest {

    private val hamstringInfo = ExerciseMuscleInfo(
        id = "tren_inferior_curl_femoral_maquina",
        name = "Curl Femoral",
        force = "Flexion",
        involvedMuscles = listOf(InvolvedMuscle("isquiotibiales", MuscleRole.PRIMARY)),
        averageRestSeconds = 90,
        efc = 2.0,
        cnc = 1.5,
        ssc = 0.2,
        axialLoadFactor = 0.0,
    )

    private val rdlInfo = ExerciseMuscleInfo(
        id = "tren_inferior_peso_muerto_rumano",
        name = "Peso Muerto Rumano",
        force = "Bisagra",
        involvedMuscles = listOf(
            InvolvedMuscle("isquiotibiales", MuscleRole.PRIMARY),
            InvolvedMuscle("erector", MuscleRole.SECONDARY),
        ),
        averageRestSeconds = 120,
        efc = 3.5,
        cnc = 4.0,
        ssc = 1.8,
        axialLoadFactor = 3.5,
    )

    private val squatInfo = ExerciseMuscleInfo(
        id = "tren_inferior_sentadilla_barra",
        name = "Sentadilla con Barra",
        force = "Sentadilla",
        involvedMuscles = listOf(
            InvolvedMuscle("cuádriceps", MuscleRole.PRIMARY),
            InvolvedMuscle("glúteo", MuscleRole.SECONDARY),
        ),
        averageRestSeconds = 120,
        efc = 4.0,
        cnc = 4.5,
        ssc = 1.5,
        axialLoadFactor = 4.0,
    )

    private val benchInfo = ExerciseMuscleInfo(
        id = "tren_superior_press_banca_plano_barra",
        name = "Press Banca Plano",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("pectoral", MuscleRole.PRIMARY)),
        averageRestSeconds = 120,
        efc = 3.0,
        cnc = 3.0,
        ssc = 0.5,
        axialLoadFactor = 1.0,
    )

    private val tricepsInfo = ExerciseMuscleInfo(
        id = "tren_superior_extension_triceps_polea",
        name = "Extensión de Tríceps en Polea",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("tríceps", MuscleRole.PRIMARY)),
        averageRestSeconds = 60,
        efc = 1.5,
        cnc = 1.0,
        ssc = 0.1,
        axialLoadFactor = 0.0,
    )

    private val legExtensionInfo = ExerciseMuscleInfo(
        id = "tren_inferior_extension_cuadriceps_maquina",
        name = "Extensión de Cuádriceps",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("cuádriceps", MuscleRole.PRIMARY)),
        averageRestSeconds = 60,
        efc = 1.5,
        cnc = 1.0,
        ssc = 0.1,
        axialLoadFactor = 0.0,
    )

    private val latPulldownInfo = ExerciseMuscleInfo(
        id = "tren_superior_jalon_frontal_polea",
        name = "Jalón Frontal",
        force = "Tirón",
        involvedMuscles = listOf(InvolvedMuscle("dorsal", MuscleRole.PRIMARY)),
        averageRestSeconds = 90,
        efc = 2.5,
        cnc = 2.0,
        ssc = 0.3,
        axialLoadFactor = 0.5,
    )

    private val exerciseIndex = mapOf(
        hamstringInfo.id.lowercase() to hamstringInfo,
        rdlInfo.id.lowercase() to rdlInfo,
        squatInfo.id.lowercase() to squatInfo,
        benchInfo.id.lowercase() to benchInfo,
        tricepsInfo.id.lowercase() to tricepsInfo,
        legExtensionInfo.id.lowercase() to legExtensionInfo,
        latPulldownInfo.id.lowercase() to latPulldownInfo,
    )

    private fun makeSet(
        rpe: Double? = 8.0,
        rir: Int? = null,
        failure: Boolean = false,
        mode: IntensityMode = IntensityMode.RPE,
    ) = ExerciseSet(
        id = "set-${java.util.UUID.randomUUID()}",
        targetReps = 10,
        targetRPE = rpe,
        targetRIR = rir,
        intensityMode = mode,
        isFailure = failure,
    )

    private fun makeExercise(
        dbId: String,
        sets: List<ExerciseSet>,
        trainingMode: TrainingMode = TrainingMode.REPS,
        name: String = dbId,
    ) = Exercise(
        id = "ex-${java.util.UUID.randomUUID()}",
        name = name,
        exerciseDbId = dbId,
        sets = sets,
        trainingMode = trainingMode,
    )

    private fun makeSession(vararg exercises: Exercise) = Session(
        id = "session-test",
        name = "Sesión Test",
        exercises = exercises.toList(),
    )

    private fun makeInput(
        session: Session,
        weekSessions: List<Session> = listOf(session),
        ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
        settings: Settings = Settings(),
        customDrain: com.example.kpkn.data.models.PredictedDrain? = null,
        customTemplateDrains: Map<String, com.example.kpkn.data.models.PredictedDrain> = emptyMap(),
    ) = SessionAssistantInput(
        allExercisesInSession = session.exercises + session.parts.flatMap { it.exercises },
        weekSessions = weekSessions,
        currentSessionId = session.id,
        program = null,
        settings = settings,
        workoutLogs = emptyList(),
        exerciseIndex = exerciseIndex,
        ruleLimits = ruleLimits,
        mesoIndex = 0,
        programId = "test-program",
        customDrain = customDrain,
        customTemplateDrains = customTemplateDrains,
    )

    @Test
    fun `isquiosAtLimit_doesNotSuggestHamstringCurlSet`() {
        val sets = (1..5).map { makeSet(rpe = 8.0) }
        val session = makeSession(makeExercise("tren_inferior_curl_femoral_maquina", sets))
        val input = makeInput(
            session = session,
            ruleLimits = SessionEditorRuleLimits(
                maxVolumePerMuscleSession = 5.0,
                rigidLimits = true,
            ),
        )

        val report = SessionAssistantEngine.evaluate(input)

        val hasAddSeries = report.ajustes.any {
            it.type == AssistantActionType.ADD_GHOST_EXERCISE &&
                it.muscle == "Isquiosurales"
        }
        assertFalse("No debe recomendar añadir series a isquios en el límite", hasAddSeries)

        val hasBlockingRisk = report.riesgos.any {
            it.muscle == "Isquiosurales" && it.severity == RiskSeverity.BLOCKING
        }
        assertTrue("Debe detectar riesgo BLOCKING para isquios", hasBlockingRisk)
    }

    @Test
    fun `underMevWithSafeDrain_suggestsGhostExercise`() {
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", (1..2).map { makeSet(rpe = 7.0) }),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val ghostForPectoral = report.tarjetasFantasma.any {
            it.compatibleConSplit
        }
        assertTrue(
            "Debe generar al menos una tarjeta fantasma para músculos bajo MEV",
            report.oportunidades.isNotEmpty() || ghostForPectoral,
        )
    }

    @Test
    fun `highSpine_blocksAxialSuggestions`() {
        val heavySets = (1..4).map { makeSet(rpe = 9.5) }
        val session = makeSession(
            makeExercise("tren_inferior_peso_muerto_rumano", heavySets, name = "Peso Muerto Rumano"),
            makeExercise("tren_inferior_sentadilla_barra", heavySets, name = "Sentadilla con Barra"),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val spinalRisk = report.riesgos.find { it.type == RiskType.SPINE }
        assertNotNull("Debe detectar riesgo de columna", spinalRisk)
        assertTrue(
            "Riesgo de columna debe ser WARNING o BLOCKING",
            spinalRisk!!.severity == RiskSeverity.WARNING || spinalRisk.severity == RiskSeverity.BLOCKING,
        )
    }

    @Test
    fun `failureHeavy_setsRecommendRirBeforeVolume`() {
        val failureSets = (1..4).map { makeSet(failure = true, mode = IntensityMode.FAILURE) }
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", failureSets, name = "Press Banca"),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val failureAjuste = report.ajustes.find { it.type == AssistantActionType.REMOVE_FAILURE }
        assertNotNull("Debe recomendar reducir fallo primero", failureAjuste)
        assertTrue(
            "Fallo debe ser el primer ajuste (prioridad alta)",
            failureAjuste!!.priority <= 2,
        )
    }

    @Test
    fun `manualRigidLimitWinsOverAugeOpportunity`() {
        val sets = (1..5).map { makeSet(rpe = 8.0) }
        val session = makeSession(
            makeExercise("tren_inferior_curl_femoral_maquina", sets),
        )
        val input = makeInput(
            session = session,
            ruleLimits = SessionEditorRuleLimits(
                maxVolumePerMuscleSession = 4.0,
                rigidLimits = true,
            ),
        )

        val report = SessionAssistantEngine.evaluate(input)

        val hasBlockingVolume = report.riesgos.any {
            it.type == RiskType.VOLUME &&
                it.severity == RiskSeverity.BLOCKING &&
                it.muscle == "Isquiosurales"
        }
        assertTrue("Límite manual rígido debe generar BLOCKING", hasBlockingVolume)

        val hasGhostForHamstring = report.tarjetasFantasma.any {
            it.impactoVolumen.contains("Isquiosurales")
        }
        assertFalse("No debe haber tarjeta fantasma para isquios cuando está en BLOCKING", hasGhostForHamstring)
    }

    @Test
    fun `ghostExerciseAddsCatalogExerciseAndRecalculates`() {
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", (1..2).map { makeSet(rpe = 7.0) }, name = "Press Banca"),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val ghostCard = report.tarjetasFantasma.firstOrNull()
        assertNotNull("Debe haber al menos una tarjeta fantasma", ghostCard)
        if (ghostCard != null) {
            assertTrue(
                "exerciseDbId debe estar en el catálogo",
                exerciseIndex.containsKey(ghostCard.exerciseDbId.lowercase()),
            )
            assertTrue("nombre no debe estar vacío", ghostCard.name.isNotBlank())
            assertTrue("motivo no debe estar vacío", ghostCard.motivo.isNotBlank())
            assertTrue("sets debe ser > 0", ghostCard.sets > 0)
        }
    }

    @Test
    fun `templateApplyRegeneratesIds`() {
        val template = SessionTemplate(
            id = "test-template",
            sourceType = SessionTemplateSourceType.SYSTEM,
            name = "Push Test",
            description = "Test template",
            tags = listOf(SessionTemplateTag.EMPUJE),
            session = Session(
                id = "template-session",
                name = "Push",
                exercises = listOf(
                    makeExercise("tren_superior_press_banca_plano_barra", (1..3).map { makeSet() }, name = "Press Banca"),
                ),
            ),
        )

        val targetSession = makeSession()
        val applied = com.example.kpkn.domain.templates.SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = targetSession,
            mode = com.example.kpkn.data.sessions.SessionTemplateApplyMode.REPLACE,
        )

        val originalIds = template.session.exercises.map { it.id } +
            template.session.exercises.flatMap { it.sets.map { s -> s.id } }
        val newIds = applied.exercises.map { it.id } +
            applied.exercises.flatMap { it.sets.map { s -> s.id } }

        assertTrue("IDs deben ser diferentes después de aplicar plantilla", originalIds.intersect(newIds).isEmpty())
        assertTrue("Debe tener ejercicios", applied.exercises.isNotEmpty())
    }

    @Test
    fun `templateFiltersReturnExpectedSplits`() {
        val pushTemplate = SessionTemplate(
            id = "push-test",
            sourceType = SessionTemplateSourceType.SYSTEM,
            name = "Push",
            description = "Push day",
            tags = listOf(SessionTemplateTag.EMPUJE, SessionTemplateTag.PECHO, SessionTemplateTag.HOMBROS),
            session = Session(
                id = "push-session",
                name = "Push",
                exercises = listOf(
                    makeExercise("tren_superior_press_banca_plano_barra", (1..3).map { makeSet() }, name = "Press Banca"),
                ),
            ),
        )
        val pullTemplate = SessionTemplate(
            id = "pull-test",
            sourceType = SessionTemplateSourceType.SYSTEM,
            name = "Pull",
            description = "Pull day",
            tags = listOf(SessionTemplateTag.TIRON, SessionTemplateTag.ESPALDA),
            session = Session(
                id = "pull-session",
                name = "Pull",
                exercises = listOf(
                    makeExercise("tren_superior_jalon_frontal_polea", (1..3).map { makeSet() }, name = "Jalón"),
                ),
            ),
        )

        val pushTemplates = listOf(pushTemplate, pullTemplate).filter {
            SessionTemplateTag.EMPUJE in it.tags
        }
        val pullTemplates = listOf(pushTemplate, pullTemplate).filter {
            SessionTemplateTag.TIRON in it.tags
        }

        assertEquals("Filtro Push debe retornar 1 plantilla", 1, pushTemplates.size)
        assertEquals("Filtro Pull debe retornar 1 plantilla", 1, pullTemplates.size)
        assertEquals("Push debe ser EMPUJE", "Push", pushTemplates.first().name)
        assertEquals("Pull debe ser TIRON", "Pull", pullTemplates.first().name)
    }

    @Test
    fun `templatePreviewShowsWarnings`() {
        val heavyTemplate = SessionTemplate(
            id = "heavy-test",
            sourceType = SessionTemplateSourceType.SYSTEM,
            name = "Heavy Legs",
            description = "Legs heavy",
            tags = listOf(SessionTemplateTag.PIERNA),
            estimatedDurationMinutes = 80,
            session = Session(
                id = "heavy-session",
                name = "Heavy Legs",
                exercises = listOf(
                    makeExercise("tren_inferior_sentadilla_barra", (1..5).map { makeSet(rpe = 9.5) }, name = "Sentadilla"),
                    makeExercise("tren_inferior_peso_muerto_rumano", (1..4).map { makeSet(rpe = 9.0) }, name = "RDL"),
                ),
            ),
        )

        val session = makeSession(
            makeExercise("tren_inferior_sentadilla_barra", (1..3).map { makeSet(rpe = 8.0) }, name = "Sentadilla"),
        )
        val input = makeInput(
            session = session,
            customDrain = PredictedDrain(cns = 85, muscular = 75, spinal = 30),
            customTemplateDrains = mapOf(
                "heavy-test" to PredictedDrain(cns = 85, muscular = 75, spinal = 30),
            ),
        )

        val report = SessionAssistantEngine.evaluate(input, allTemplates = listOf(heavyTemplate))

        val preview = report.plantillasCompatibles.find { it.template.id == "heavy-test" }
        assertNotNull("Preview debe existir para plantilla pesada", preview)
        assertTrue(
            "Preview debe tener advertencias para plantilla pesada",
            preview!!.advertencias.isNotEmpty(),
        )
    }

    @Test
    fun `verdictIsCriticalWhenBlockingRisksExist`() {
        val heavySets = (1..6).map { makeSet(rpe = 9.5, failure = true, mode = IntensityMode.FAILURE) }
        val session = makeSession(
            makeExercise("tren_inferior_peso_muerto_rumano", heavySets, name = "RDL"),
        )
        val input = makeInput(
            session = session,
            ruleLimits = SessionEditorRuleLimits(rigidLimits = true, maxRPE = 8.0),
        )

        val report = SessionAssistantEngine.evaluate(input)

        assertEquals("Veredicto debe ser CRITICAL", Verdict.CRITICAL, report.veredicto)
    }

    @Test
    fun `jointStressDetectedCorrectly`() {
        val tricepsSets = (1..5).map { makeSet(rpe = 8.5) }
        val session = makeSession(
            makeExercise("tren_superior_extension_triceps_polea", tricepsSets, name = "Extensión Tríceps"),
            makeExercise("tren_superior_extension_triceps_polea", tricepsSets, name = "Extensión Tríceps 2"),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val jointRisk = report.riesgos.find { it.type == RiskType.JOINT }
        assertNotNull("Debe detectar riesgo articular con mucho trabajo de tríceps", jointRisk)
    }

    @Test
    fun `deficitModeIncreasesSeverity`() {
        val sets = (1..4).map { makeSet(rpe = 9.0) }
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", sets, name = "Press Banca"),
            makeExercise("tren_superior_extension_triceps_polea", sets, name = "Extensión Tríceps"),
        )

        val settingsDeficit = Settings(calorieGoalObjective = CalorieGoalObjective.DEFICIT)
        val inputDeficit = makeInput(session = session, settings = settingsDeficit)
        val reportDeficit = SessionAssistantEngine.evaluate(inputDeficit)

        val settingsSurplus = Settings(calorieGoalObjective = CalorieGoalObjective.SURPLUS)
        val inputSurplus = makeInput(session = session, settings = settingsSurplus)
        val reportSurplus = SessionAssistantEngine.evaluate(inputSurplus)

        assertTrue(
            "Con déficit debe tener más riesgos o igual que con superávit",
            reportDeficit.riesgos.size >= reportSurplus.riesgos.size ||
                reportDeficit.veredicto.ordinal >= reportSurplus.veredicto.ordinal,
        )
    }

    @Test
    fun `weeklyVolumeBlocksGhostCards`() {
        // Current session has 2 sets of hamstring curl, under session MEV
        // But weekly volume already at MRV
        val session = makeSession(
            makeExercise("tren_inferior_curl_femoral_maquina", (1..2).map { makeSet(rpe = 8.0) }),
        )
        // Another session in the week with 4 sets of hamstrings
        val otherSession = Session(
            id = "other-session",
            name = "Other",
            exercises = listOf(
                makeExercise("tren_inferior_curl_femoral_maquina", (1..4).map { makeSet(rpe = 8.0) }),
            ),
        )
        val input = makeInput(
            session = session,
            weekSessions = listOf(session, otherSession),
            ruleLimits = SessionEditorRuleLimits(
                maxVolumePerMuscleWeekly = 5.0,
                rigidLimits = false,
            ),
        )

        val report = SessionAssistantEngine.evaluate(input)

        // Ghost cards should be blocked because weekly volume is too high
        val hasGhostForHamstring = report.tarjetasFantasma.any {
            it.impactoVolumen.contains("Isquiosurales")
        }
        assertFalse("No debe haber tarjeta fantasma si el volumen semanal ya está alto", hasGhostForHamstring)
    }

    @Test
    fun `weeklyVolumeRiskDetected`() {
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", (1..3).map { makeSet(rpe = 8.0) }),
        )
        val otherSession = Session(
            id = "other-session",
            name = "Other",
            exercises = listOf(
                makeExercise("tren_superior_press_banca_plano_barra", (1..4).map { makeSet(rpe = 8.0) }),
            ),
        )
        val input = makeInput(
            session = session,
            weekSessions = listOf(session, otherSession),
            ruleLimits = SessionEditorRuleLimits(
                maxVolumePerMuscleWeekly = 4.0,
                rigidLimits = false,
            ),
        )

        val report = SessionAssistantEngine.evaluate(input)

        val weeklyRisk = report.riesgos.find { it.id.startsWith("weekly-volume-") || it.id.startsWith("weekly-mrv-") }
        assertNotNull("Debe detectar riesgo de volumen semanal", weeklyRisk)
    }

    @Test
    fun `noOldMarginSeriesInSuggestions`() {
        val session = makeSession(
            makeExercise("tren_superior_press_banca_plano_barra", listOf(makeSet(rpe = 8.0)), name = "Press Banca"),
        )
        val input = makeInput(session = session)

        val report = SessionAssistantEngine.evaluate(input)

        val hasMarginSuggestion = report.ajustes.any { it.id.contains("margin-series") }
        assertFalse("No debe haber sugerencia 'margin-series' del motor nuevo", hasMarginSuggestion)
    }
}
