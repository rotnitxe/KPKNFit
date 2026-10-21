package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import com.example.kpkn.domain.workout.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * SE10: un caso por forma de añadir o crear ejercicios en el editor. Cada prueba construye la sesión como lo hace el editor
 * y la pasa por normalizeSession (el paso previo al guardado) y por el cortafuegos del catálogo.
 * Todas deben pasar: el bloqueo de SE10 viene del cardio y del reemplazo, no de estos caminos.
 */
class SessionEditorIdentityPathsTest {

    @Test
    fun catalog_page_addition_passes_the_catalog_gate() {
        // ExercisePickerV2Catalog -> onSelect / onMultiSelect -> addExerciseToPart / addExercisesToPart.
        val exercise = catalogExerciseForSession(
            info = catalogInfo("bench_press__standard", "Press banca"),
            history = emptyList(),
            defaults = SessionEditorRuleDefaults(),
            isMeetDay = false,
        )

        assertGate(Session(id = SESSION_ID, name = SESSION_NAME, exercises = listOf(exercise)))
    }

    @Test
    fun compact_sheet_addition_passes_the_catalog_gate() {
        // La hoja compacta toma sus filas de exerciseCatalogSnapshot(), que sale del catálogo v2 (con identidad completa).
        // El editor no la muestra (useFullPageCatalog), pero su entrada llega por las mismas funciones de añadir.
        val exercise = catalogExerciseForSession(
            info = catalogInfo("row_barbell__standard", "Remo con barra"),
            history = emptyList(),
            defaults = SessionEditorRuleDefaults(),
            isMeetDay = false,
        )

        assertGate(Session(id = SESSION_ID, name = SESSION_NAME, exercises = listOf(exercise)))
    }

    @Test
    fun superset_built_from_the_catalog_passes_the_catalog_gate() {
        // addExercisesAsSupersetToPart en la sesión sin parte: miembros del catálogo, rondas y SupersetRules.createSuperset.
        val members = listOf(
            catalogInfo("bench_press__standard", "Press banca"),
            catalogInfo("row_barbell__standard", "Remo con barra"),
        ).map { info ->
            catalogExerciseForSession(info, emptyList(), SessionEditorRuleDefaults(), isMeetDay = false)
                .copy(sets = List(3) { ExerciseSet(id = UUID.randomUUID().toString()) }, restTime = 90)
        }
        val withGroup = SupersetRules.createSuperset(
            session = Session(id = SESSION_ID, name = SESSION_NAME, exercises = members),
            groupId = "group-loose",
            exerciseIds = members.map { it.id },
            restBetweenExercises = 60,
            restAfterSuperset = 120,
            rounds = 3,
            anchorPartId = null,
            anchorExerciseId = members.first().id,
        )

        assertGate(withGroup)
    }

    @Test
    fun superset_created_inside_a_part_passes_the_catalog_gate() {
        // «Crear superserie» con ejercicios que ya están en una parte (SupersetRules.createSuperset con anchorPartId).
        val members = listOf(
            catalogInfo("bench_press__standard", "Press banca"),
            catalogInfo("row_barbell__standard", "Remo con barra"),
        ).map { info -> catalogExerciseForSession(info, emptyList(), SessionEditorRuleDefaults(), isMeetDay = false) }
        val session = Session(
            id = SESSION_ID,
            name = SESSION_NAME,
            parts = listOf(SessionPart(id = "part-push", name = "Empuje", exercises = members)),
        )

        val withGroup = SupersetRules.createSuperset(
            session = session,
            groupId = "group-part",
            exerciseIds = members.map { it.id },
            restBetweenExercises = 60,
            restAfterSuperset = 120,
            rounds = 2,
            anchorPartId = "part-push",
            anchorExerciseId = members.first().id,
        )

        assertGate(withGroup)
    }

    @Test
    fun custom_unit_passes_the_catalog_gate() {
        // ExerciseCustomUnitDialog: onUpdateExercise { copy(customUnit = ...) }, que pasa por updateExercise -> normalizeExercise.
        val exercise = catalogExerciseForSession(
            info = catalogInfo("bench_press__standard", "Press banca"),
            history = emptyList(),
            defaults = SessionEditorRuleDefaults(),
            isMeetDay = false,
        ).copy(customUnit = "brazadas").normalizeExercise()

        assertEquals("brazadas", exercise.customUnit)
        assertGate(Session(id = SESSION_ID, name = SESSION_NAME, exercises = listOf(exercise)))
    }

    @Test
    fun exercise_goal_passes_the_catalog_gate() {
        // ExerciseGoalDialog («Meta del ejercicio»): isStarTarget y goal1RM, mismas copias que el diálogo.
        val exercise = catalogExerciseForSession(
            info = catalogInfo("bench_press__standard", "Press banca"),
            history = emptyList(),
            defaults = SessionEditorRuleDefaults(),
            isMeetDay = false,
        ).copy(isStarTarget = true, goal1RM = 120.0).normalizeExercise()

        assertEquals(120.0, exercise.goal1RM ?: 0.0, 0.0)
        assertGate(Session(id = SESSION_ID, name = SESSION_NAME, exercises = listOf(exercise)))
    }

    @Test
    fun copy_to_another_session_passes_the_catalog_gate() {
        // «Copiar a otras sesiones»: buildClonePayload + createSessionForTargetDay (día nuevo) o mergeSessionWithPayload (APPEND).
        val source = Session(
            id = "source",
            name = SESSION_NAME,
            exercises = listOf(
                catalogExerciseForSession(catalogInfo("bench_press__standard", "Press banca"), emptyList(), SessionEditorRuleDefaults(), false),
                catalogExerciseForSession(catalogInfo("row_barbell__standard", "Remo con barra"), emptyList(), SessionEditorRuleDefaults(), false),
            ),
        )
        val payload = buildClonePayload(source, selectedExerciseIds = null)

        val newDay = createSessionForTargetDay(source = source, dayOfWeek = 6, payload = payload, selectedExerciseIds = null)
        assertGate(newDay)

        val existing = Session(
            id = "target",
            name = "Sábado",
            exercises = listOf(
                catalogExerciseForSession(catalogInfo("squat__standard", "Sentadilla"), emptyList(), SessionEditorRuleDefaults(), false),
            ),
        )
        val appended = mergeSessionWithPayload(
            base = existing,
            source = source,
            payload = payload,
            selectedExerciseIds = null,
            applyMode = SessionCloneApplyMode.APPEND,
        )
        assertGate(appended)
    }

    @Test
    fun manual_exercise_passes_the_catalog_gate() {
        // Ejercicio manual (SmartExerciseCreator): identidad custom:<uuid>, fuera del catálogo a propósito.
        val manual = SmartExerciseCreator.createAutomatic("Remo con toalla", emptyList())

        val exercise = catalogExerciseForSession(manual, emptyList(), SessionEditorRuleDefaults(), isMeetDay = false)

        assertTrue(exercise.exerciseDbId.orEmpty().startsWith("custom:"))
        assertGate(Session(id = SESSION_ID, name = SESSION_NAME, exercises = listOf(exercise)))
    }

    private fun catalogInfo(configurationId: String, name: String): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = configurationId,
        name = name,
        catalogRevision = REVISION,
        catalogDefinitionId = configurationId.substringBefore("__"),
        catalogConfigurationId = configurationId,
        performanceProfileId = "profile-$configurationId",
    )

    /** Lo mismo que el guardado: normalizeSession antes del cortafuegos. */
    private fun assertGate(session: Session) {
        assertEquals(emptyList<String>(), session.normalizeSession().catalogV2SelectionIssues().map { it.code })
    }

    private companion object {
        const val REVISION = "v2-test"
        const val SESSION_ID = "session-sab"
        const val SESSION_NAME = "Pierna A"
    }
}
