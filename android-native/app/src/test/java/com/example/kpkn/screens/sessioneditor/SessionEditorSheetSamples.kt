package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.data.models.SessionPart
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Datos de ejemplo de las hojas del editor (SE7), para las pruebas y para la hoja de render. Son inventados: nombres de ejercicios, de
 * sesiones y fechas de dibujo, sin nada real del usuario.
 */
internal object SessionEditorSheetSamples {

    private fun sets(id: String, count: Int) = (1..count).map { ExerciseSet(id = "$id-$it") }

    /** Un ejercicio dentro de la superserie `g1`. */
    fun inGroup(exercise: Exercise) = exercise.copy(supersetGroupRef = "g1", supersetId = "g1")

    val squat = Exercise(id = "ex-squat", name = "Sentadilla trasera", sets = sets("squat", 4))
    val bench = Exercise(id = "ex-bench", name = "Press de banca", sets = sets("bench", 3))
    val incline = Exercise(id = "ex-incline", name = "Press inclinado con mancuernas", sets = sets("incline", 3))
    val row = Exercise(id = "ex-row", name = "Remo con barra", sets = sets("row", 3))
    val curl = Exercise(id = "ex-curl", name = "Curl de bíceps", sets = sets("curl", 2))

    /** La sesión de ejemplo: dos partes y un ejercicio suelto, con portada de degradado. */
    val session = Session(
        id = "s-torso-a",
        name = "Torso A",
        dayOfWeek = 1,
        background = SessionBackground(type = SessionBackgroundType.COLOR, value = "gradient://ember"),
        parts = listOf(
            SessionPart(id = "p-empuje", name = "Empuje", exercises = listOf(bench, incline), targetDurationMinutes = 25),
            SessionPart(id = "p-tiron", name = "Tirón", exercises = listOf(row)),
        ),
        exercises = listOf(curl),
        targetDurationMinutes = 60,
    )

    private fun epochMs(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Dos versiones: la primera coincide con la sesión actual (lleva «Actual») y la segunda se puede restaurar. */
    val versions = listOf(
        SessionDraftSnapshot(
            id = "v2",
            session = session,
            savedAtMs = epochMs(13, 18, 40),
            reason = "Al completar un entreno",
            changedFields = listOf("Series", "Descansos"),
            exerciseCount = 6,
            setCount = 22,
            partCount = 2,
        ),
        SessionDraftSnapshot(
            id = "v1",
            session = session.copy(name = "Torso A · antes"),
            savedAtMs = epochMs(10, 9, 15),
            reason = "Al completar un entreno",
            changedFields = emptyList(),
            exerciseCount = 5,
            setCount = 18,
            partCount = 2,
        ),
    )

    val ruleDefaults = SessionEditorRuleDefaults(
        setCount = 3,
        reps = 8,
        rpe = 8.0,
        intensityType = DefaultIntensityType.RIR,
        normalRestSeconds = 150,
        betweenSidesRestSeconds = 0,
        supersetBetweenRestSeconds = 60,
        supersetRoundRestSeconds = 120,
        applyToNewItems = false,
    )

    val templates = listOf(
        RuleTemplate(id = "t-fuerza", name = "Fuerza base", defaults = ruleDefaults.copy(reps = 5), createdAtMs = 0L, isFactory = true),
        RuleTemplate(id = "t-volumen", name = "Volumen 3 × 10", defaults = ruleDefaults.copy(reps = 10), createdAtMs = 0L),
    )

    /** La hoja de reglas con su pestaña «Reglas». Con [tiempo], abre la pestaña «Tiempo». */
    fun rulesState(tiempo: Boolean = false) = SessionEditorUiState(
        session = session,
        ruleDefaults = ruleDefaults,
        ruleTemplates = templates,
        estimatedDurationMinutes = 58,
        rulesSheetInitialTab = if (tiempo) 1 else 0,
    )

    private fun day(key: String, dayOfWeek: Int, sessionId: String?, sessionName: String?, count: Int) = SessionCloneDayOption(
        key = key,
        macroIndex = 0,
        mesoIndex = 0,
        weekId = "w1",
        dayOfWeek = dayOfWeek,
        macroName = "Macro 1",
        blockName = "Hipertrofia",
        mesoName = "Meso 1",
        weekName = "Semana 2",
        existingSessionId = sessionId,
        existingSessionName = sessionName,
        existingExerciseCount = count,
    )

    /** Días a los que copiar: dos con sesión, uno libre y uno con sesión de otro tamaño. */
    val cloneState = SessionEditorUiState(
        session = session,
        weekId = "w1",
        dayOfWeek = 1,
        cloneDayOptions = listOf(
            day("w1-2", 2, "s-mar", "Torso B", 5),
            day("w1-3", 3, null, null, 0),
            day("w1-4", 4, "s-jue", "Pierna", 6),
            day("w1-5", 5, "s-vie", "Full body", 4),
        ),
    )

    /** Una sesión de origen para «Traer»: su nombre, su día y sus ejercicios. */
    private fun source(sessionId: String, name: String, dayNumber: Int, exercises: List<Exercise>) = SessionCloneSourceOption(
        sessionId = sessionId,
        dayOfWeek = dayNumber,
        macroIndex = 0,
        mesoIndex = 0,
        weekId = "w0",
        macroName = "Macro 1",
        blockName = "Hipertrofia",
        mesoName = "Meso 1",
        weekName = "Semana 1",
        sessionName = name,
        exerciseCount = exercises.size,
        exercises = exercises.map { SessionCloneExerciseOption(exerciseId = it.id, name = it.name, sourcePartName = null) },
    )

    /** La misma hoja de «Copiar», con dos sesiones de origen para «Traer». */
    val cloneStateWithSources = cloneState.copy(
        cloneSourceOptions = listOf(
            source("s-torso-b", "Torso B", 2, listOf(row, curl)),
            source("s-pierna", "Pierna", 4, listOf(squat)),
        ),
    )

    /** Un borrador de superserie con dos miembros: el de la pieza de crear. */
    val draft = SupersetDraft(partId = "p-empuje", exerciseIds = listOf(bench.id, incline.id))

    /** Los ejercicios libres de la sesión que muestra la hoja de crear, con dos ya marcados. */
    val pickerExercises = listOf(bench, incline, row, curl, squat)

    /** Los miembros de la superserie que gestiona la hoja de gestionar. */
    val supersetMembers = listOf(inGroup(bench), inGroup(incline))

    /** El ejercicio que se vincula y los candidatos, con el ancla actual `db-bench`. */
    val relationTarget = Exercise(
        id = "ex-fly",
        name = "Aperturas con mancuernas",
        sets = sets("fly", 3),
        relativeToCanonicalExerciseId = "db-bench",
    )

    val relationCandidates = listOf(
        ProgramExerciseCandidate(
            exerciseId = "c-bench",
            exerciseName = "Press de banca",
            exerciseDbId = "db-bench",
            sessionDayOfWeek = 1,
            sessionName = "Torso A",
            partName = "Empuje",
        ),
        ProgramExerciseCandidate(
            exerciseId = "c-incline",
            exerciseName = "Press inclinado con mancuernas",
            exerciseDbId = "db-incline",
            sessionDayOfWeek = 4,
            sessionName = "Torso B",
            partName = null,
        ),
        ProgramExerciseCandidate(
            exerciseId = "c-row",
            exerciseName = "Remo con barra",
            exerciseDbId = "db-row",
            sessionDayOfWeek = 1,
            sessionName = "Torso A",
            partName = "Tirón",
        ),
    )
}
