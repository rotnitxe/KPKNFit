package com.example.kpkn.domain.training.quality

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSlopRulesTest {

    private fun exercise(
        id: String,
        name: String,
        configurationId: String?,
        definitionId: String? = configurationId?.substringBefore("__"),
        linkedTo: String? = null,
        sets: List<ExerciseSet> = listOf(ExerciseSet(id = "$id-s1")),
    ): Exercise = Exercise(
        id = id,
        name = name,
        catalogConfigurationId = configurationId,
        catalogDefinitionId = definitionId,
        relativeToCanonicalExerciseId = linkedTo,
        sets = sets,
    )

    private fun session(vararg exercises: Exercise): Session = Session(id = "s", name = "Día A", exercises = exercises.toList())

    @Test
    fun `dos barra alta sin enlace es configuracion duplicada`() {
        val violations = SessionSlopRules.violations(
            session(
                exercise("e1", "Sentadilla trasera (barra alta)", "high_bar_back_squat__barbell"),
                exercise("e2", "Sentadilla trasera (barra alta)", "high_bar_back_squat__barbell"),
            ),
        )
        val rules = violations.map { it.rule }
        assertTrue(violations.toString(), SlopRule.DUPLICATE_CONFIGURATION in rules)
        assertTrue(violations.toString(), SlopRule.DUPLICATE_DEFINITION in rules)
        assertTrue(violations.toString(), SlopRule.SAME_MOVEMENT_FAMILY in rules)
        assertEquals("Día A", violations.first().sessionName)
    }

    @Test
    fun `la segunda aparicion enlazada no es violacion`() {
        val violations = SessionSlopRules.violations(
            session(
                exercise("e1", "Sentadilla trasera (barra alta)", "high_bar_back_squat__barbell"),
                exercise("e2", "Sentadilla trasera (barra alta)", "high_bar_back_squat__barbell", linkedTo = "high_bar_back_squat__barbell"),
            ),
        )
        assertEquals(emptyList<SlopViolation>(), violations)
    }

    @Test
    fun `barra baja y barra alta sin enlace son la misma familia`() {
        val violations = SessionSlopRules.violations(
            session(
                exercise("e1", "Sentadilla trasera (barra baja)", "low_bar_back_squat__barbell"),
                exercise("e2", "Sentadilla trasera (barra alta)", "high_bar_back_squat__barbell"),
            ),
        )
        assertEquals(listOf(SlopRule.SAME_MOVEMENT_FAMILY), violations.map { it.rule })
    }

    @Test
    fun `nombre interno en minusculas inglesas`() {
        val violations = SessionSlopRules.violations(
            session(
                exercise("e1", "bike stationary", null),
                exercise("e2", "row_machine", null),
                exercise("e3", "Remo en máquina", null),
            ),
        )
        assertEquals(listOf("bike stationary", "row_machine"), violations.filter { it.rule == SlopRule.INTERNAL_NAME }.map { it.detail })
    }

    @Test
    fun `ejercicio de fuerza sin series`() {
        val violations = SessionSlopRules.violations(
            session(exercise("e1", "Press de banca", "bench_press__barbell", sets = emptyList())),
        )
        assertEquals(listOf(SlopRule.EMPTY_EXERCISE), violations.map { it.rule })
        assertEquals("Press de banca", violations.single().detail)
    }

    @Test
    fun `sesion sin ejercicios`() {
        assertEquals(listOf(SlopRule.EMPTY_SESSION), SessionSlopRules.violations(session()).map { it.rule })
    }
}
