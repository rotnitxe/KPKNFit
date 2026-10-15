package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.generator.CardioBuilder
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import com.example.kpkn.screens.sessioneditor.design.SessionSetIntensity
import com.example.kpkn.screens.sessioneditor.design.sessionSetColumns
import com.example.kpkn.screens.sessioneditor.design.withIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SE11: el editor no reescribe los básicos del programa. La sesión se construye como la hace el generador (con marcas de
 * sentadilla, banca y peso muerto: series con rango, RIR y %). Cada mutación del editor no debe cambiar el rango, el RIR, el %
 * ni el descanso de esos básicos.
 */
class SessionEditorBasicsPreservationTest {

    private val generated: Session by lazy {
        val routine = RoutineGenerator.generate(
            RoutineTestSupport.request(
                RoutineTestSupport.gym,
                RoutineMode.GENERAL_STRENGTH_MUSCLE,
                RoutineLevel.INTERMEDIATE,
                3,
                90,
                marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 180.0),
            ),
        )
        sessionsOf(routine.program).first { session -> session.allExercises().any { it.isHybridBasic() } }
    }

    @Test
    fun laSesionDelGeneradorTraeBasicosConRangoRirYPorcentaje() {
        val basics = basicsOf(generated)
        assertTrue("el generador debe dar básicos con marca", basics.isNotEmpty())
        assertTrue(basics.any { exercise -> exercise.sets.any { it.range != null && it.rir != null && it.percent != null } })
    }

    @Test
    fun anadirUnEjercicioDelCatalogoNoTocaLosBasicos() {
        val info = ExerciseMuscleInfo(id = "curl-maquina", name = "Curl de bíceps en máquina", type = "Aislamiento")
        val added = catalogExerciseForSession(info, emptyList(), SessionEditorRuleDefaults(), isMeetDay = false)
        val after = generated.copy(exercises = generated.exercises + added).normalizeSession()
        assertBasicsKept(generated, after, "añadir un ejercicio del catálogo")
    }

    @Test
    fun anadirCardioNoTocaLosBasicos() {
        val cardio = CardioBuilder.exercise("cardio-prueba", CardioBuilder.steady(CardioType.WALK, 600))
        val after = generated.copy(parts = generated.parts + CardioBuilder.part("parte-cardio-prueba", listOf(cardio))).normalizeSession()
        assertBasicsKept(generated, after, "añadir cardio")
    }

    @Test
    fun reordenarNoTocaLosBasicos() {
        val after = generated.copy(
            exercises = generated.exercises.reversed(),
            parts = generated.parts.map { part -> part.copy(exercises = part.exercises.reversed()) },
        ).normalizeSession()
        assertBasicsKept(generated, after, "reordenar")
    }

    @Test
    fun editarLasSeriesDeOtroEjercicioNoTocaLosBasicos() {
        val other = generated.allExercises().first { it.cardioDetails == null && it.trainingMode != TrainingMode.RM && it.sets.isNotEmpty() }
        val after = generated.transformExercises { exercise ->
            if (exercise.id != other.id) {
                exercise
            } else {
                exercise.copy(sets = exercise.sets.mapIndexed { index, set ->
                    if (index == 0) set.copy(targetReps = 12).normalizeSet(exercise) else set
                })
            }
        }.normalizeSession()
        assertBasicsKept(generated, after, "editar las series de otro ejercicio")
    }

    @Test
    fun elCaminoDeGuardadoNoTocaLosBasicos() {
        assertBasicsKept(generated, generated.normalizedForEditorSave(), "guardar")
    }

    @Test
    fun normalizarDosVecesDaLoMismoQueUnaVez() {
        val once = generated.normalizeSession()
        assertEquals(once, once.normalizeSession())
    }

    @Test
    fun elRirSobreviveAPasarAlModoRirYVolverAlPorcentaje() {
        val basic = generated.allExercises().first { it.isHybridBasic() }
        val back = basic.withIntensity(SessionSetIntensity.RIR).withIntensity(SessionSetIntensity.PERCENT).normalizeExercise()
        assertEquals(basic.sets.map { it.targetRIR }, back.sets.map { it.targetRIR })
        assertEquals(basic.sets.map { it.targetRepsRange }, back.sets.map { it.targetRepsRange })
    }

    @Test
    fun laDosisDelModoPorcentajeLlevaRangoPorcentajeRirYDescanso() {
        val sets = (1..4).map { ExerciseSet(id = "s$it", targetRepsRange = RepRange(4, 6), targetPercentageRM = 80.0, targetRIR = 2) }
        assertEquals("4 × 4–6 · 80 % 1RM · RIR 2 · 3:15", sessionExerciseDose(sets, TrainingMode.RM, restSeconds = 195))
    }

    @Test
    fun laTablaDelModoPorcentajeMuestraElRirSiLasSeriesLoLlevan() {
        val conRir = Exercise(
            id = "sq",
            name = "Sentadilla",
            trainingMode = TrainingMode.RM,
            restTime = 195,
            sets = listOf(ExerciseSet(id = "a", targetRepsRange = RepRange(4, 6), targetPercentageRM = 80.0, targetRIR = 2)),
        )
        assertEquals(listOf("percent", "reps", "rir", "rest"), sessionSetColumns(conRir, withRest = true).map { it.key })
        val sinRir = conRir.copy(sets = listOf(ExerciseSet(id = "a", targetPercentageRM = 80.0)))
        assertEquals(listOf("percent", "reps", "rest"), sessionSetColumns(sinRir, withRest = true).map { it.key })
    }

    // ─── Ayudas ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun Exercise.isHybridBasic(): Boolean =
        trainingMode == TrainingMode.RM && sets.any { it.targetRepsRange != null && it.targetRIR != null }

    private fun sessionsOf(program: Program): List<Session> = program.macrocycles.flatMap { macro ->
        macro.blocks.flatMap { block -> block.mesocycles.flatMap { meso -> meso.weeks.flatMap { week -> week.sessions } } }
    }

    private data class SetBasics(val reps: Int?, val range: RepRange?, val rir: Int?, val percent: Double?, val rest: Int?)

    private data class ExerciseBasics(val id: String, val restTime: Int?, val sets: List<SetBasics>)

    private fun basicsOf(session: Session): List<ExerciseBasics> = session.allExercises()
        .filter { it.trainingMode == TrainingMode.RM }
        .sortedBy { it.id }
        .map { exercise ->
            ExerciseBasics(
                id = exercise.id,
                restTime = exercise.restTime,
                sets = exercise.sets.map { set ->
                    SetBasics(set.targetReps, set.targetRepsRange, set.targetRIR, set.targetPercentageRM, set.restAfterSeconds)
                },
            )
        }

    private fun assertBasicsKept(before: Session, after: Session, mutation: String) {
        assertEquals("tras $mutation cambian los básicos", basicsOf(before), basicsOf(after))
    }
}
