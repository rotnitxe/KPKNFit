package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknSetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de la tabla de series del ejercicio desplegado (SE3b): filas, columnas por modo y escritura de celdas. */
class SessionExerciseSetRowsTest {

    private fun ejercicio(
        vararg sets: ExerciseSet,
        mode: TrainingMode = TrainingMode.REPS,
        warmups: List<WarmupSetDefinition> = emptyList(),
        restTime: Int? = 90,
    ): Exercise = Exercise(
        id = "ex",
        name = "Press",
        sets = sets.toList(),
        warmupSets = warmups,
        trainingMode = mode,
        restTime = restTime,
    )

    private fun columnas(exercise: Exercise, withRest: Boolean = true) = sessionSetColumns(exercise, withRest).map { it.key }

    // Filas y etiquetas

    @Test
    fun trabajoAproximacionYDropLlevanSuEtiqueta() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetReps = 8),
            ExerciseSet(id = "d1", targetReps = 6, isDropSet = true),
            ExerciseSet(id = "w2", targetReps = 6),
            warmups = listOf(
                WarmupSetDefinition(id = "a1", percentageOfWorkingWeight = 0.5, targetReps = 10),
                WarmupSetDefinition(id = "a2", percentageOfWorkingWeight = 0.7, targetReps = 5),
            ),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), emptyMap())

        assertEquals(listOf("A", "A", "1", "D", "2"), rows.map { it.label })
        assertEquals(
            listOf(KpknSetKind.APPROACH, KpknSetKind.APPROACH, KpknSetKind.WORK, KpknSetKind.DROP, KpknSetKind.WORK),
            rows.map { it.kind },
        )
    }

    @Test
    fun laRondaVaciaOcupaSuNumeroYNoTieneDato() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetReps = 8),
            ExerciseSet(id = "hueco", isEmptySlot = true),
            ExerciseSet(id = "w3", targetReps = 8),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), emptyMap())

        assertEquals(listOf("1", "2", "3"), rows.map { it.label })
        assertEquals("", rows[1].cells.getValue("reps").value)
    }

    @Test
    fun elRangoSeEscribeConGuionLargo() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetRepsRange = RepRange(6, 8), targetReps = 8),
            ExerciseSet(id = "w2", targetReps = 10),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), emptyMap())

        assertEquals("6–8", rows[0].cells.getValue("reps").value)
        assertEquals("10", rows[1].cells.getValue("reps").value)
    }

    @Test
    fun sinDatoLaCeldaQuedaVacia() {
        val exercise = ejercicio(ExerciseSet(id = "w1", targetReps = 8, intensityMode = IntensityMode.RPE))

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), emptyMap())

        assertEquals("", rows[0].cells.getValue("rpe").value)
        assertEquals(KpknCellInput.ReadOnly, rows[0].cells.getValue("rpe").input)
    }

    @Test
    fun laCargaSugeridaLlevaAproximacionYLaCargaExplicitaNo() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetReps = 8),
            ExerciseSet(id = "w2", targetReps = 8, intensityMode = IntensityMode.LOAD, weight = 60.0),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), mapOf("w1" to 80.0, "w2" to 70.0))

        assertEquals("≈80", rows[0].cells.getValue("kg").value)
        assertEquals("60", rows[1].cells.getValue("kg").value)
    }

    @Test
    fun laEstimacionSeRedondeaAlMedioKiloYVaPegadaAlSimbolo() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetReps = 8),
            ExerciseSet(id = "w2", targetReps = 8),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), mapOf("w1" to 77.75, "w2" to 52.5))

        assertEquals("≈78", rows[0].cells.getValue("kg").value)
        assertEquals("≈52,5", rows[1].cells.getValue("kg").value)
    }

    @Test
    fun laAproximacionSaleDeLaCargaDeTrabajo() {
        val exercise = ejercicio(
            ExerciseSet(id = "w1", targetReps = 8),
            warmups = listOf(WarmupSetDefinition(id = "a1", percentageOfWorkingWeight = 0.5, targetReps = 10, restBetween = 60)),
        )

        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), mapOf("w1" to 80.0))

        assertEquals("≈40", rows[0].cells.getValue("kg").value)
        assertEquals("10", rows[0].cells.getValue("reps").value)
        assertEquals("1:00", rows[0].cells.getValue("rest").value)
    }

    // Columnas por modo

    @Test
    fun columnasRir() {
        val exercise = ejercicio(ExerciseSet(id = "w1", intensityMode = IntensityMode.RIR, targetRIR = 2))
        assertEquals(listOf("kg", "reps", "rir", "rest"), columnas(exercise))
    }

    @Test
    fun columnasRpeSonLasPorDefecto() {
        val exercise = ejercicio(ExerciseSet(id = "w1", intensityMode = IntensityMode.RPE, targetRPE = 8.0))
        assertEquals(listOf("kg", "reps", "rpe", "rest"), columnas(exercise))
    }

    @Test
    fun columnasPorcentajeEnModoRm() {
        val exercise = ejercicio(ExerciseSet(id = "w1", targetPercentageRM = 80.0), mode = TrainingMode.RM)
        assertEquals(listOf("percent", "reps", "rest"), columnas(exercise))
    }

    @Test
    fun columnasTiempo() {
        val exercise = ejercicio(ExerciseSet(id = "w1", targetDuration = 45), mode = TrainingMode.TIME)
        assertEquals(listOf("kg", "time", "rest"), columnas(exercise))
    }

    @Test
    fun columnasDistanciaYPersonalizada() {
        assertEquals(listOf("distance", "time", "rest"), columnas(ejercicio(mode = TrainingMode.DISTANCE)))
        val personalizada = ejercicio(mode = TrainingMode.CUSTOM).copy(customUnit = "brazadas")
        assertEquals(listOf("value", "rest"), columnas(personalizada))
        assertEquals("brazadas", sessionSetColumns(personalizada, true).first().label)
    }

    @Test
    fun sinDescansoNoHayColumnaDeDescanso() {
        val exercise = ejercicio(ExerciseSet(id = "w1", intensityMode = IntensityMode.RIR, targetRIR = 2))
        assertEquals(listOf("kg", "reps", "rir"), columnas(exercise, withRest = false))
    }

    // Intensidad de la tabla

    @Test
    fun intensidadRirSoloSiTodasLasSeriesLaLlevan() {
        val todasRir = ejercicio(
            ExerciseSet(id = "a", intensityMode = IntensityMode.RIR, targetRIR = 2),
            ExerciseSet(id = "b", intensityMode = IntensityMode.RIR, targetRIR = 1),
        )
        val mezcla = ejercicio(
            ExerciseSet(id = "a", intensityMode = IntensityMode.RIR, targetRIR = 2),
            ExerciseSet(id = "b", intensityMode = IntensityMode.RPE, targetRPE = 8.0),
        )
        assertEquals(SessionSetIntensity.RIR, sessionSetIntensity(todasRir))
        assertEquals(SessionSetIntensity.RPE, sessionSetIntensity(mezcla))
        assertEquals(SessionSetIntensity.PERCENT, sessionSetIntensity(ejercicio(mode = TrainingMode.RM)))
    }

    // Escritura de celdas: las mismas reglas que la fila de hoy

    @Test
    fun laCeldaDeKgEscribeCargaExplicitaEnModoLoad() {
        val escrita = ExerciseSet(id = "w1").withTypedValue("kg", "82,5")
        assertEquals(82.5, escrita.weight!!, 1e-9)
        assertEquals(IntensityMode.LOAD, escrita.intensityMode)
    }

    @Test
    fun laCeldaDeRepsAceptaRangoYNumero() {
        val rango = ExerciseSet(id = "w1").withTypedValue("reps", "6-8")
        assertEquals(8, rango.targetReps)
        assertEquals(RepRange(6, 8), rango.targetRepsRange)

        val numero = rango.withTypedValue("reps", "10")
        assertEquals(10, numero.targetReps)
        assertNull(numero.targetRepsRange)
    }

    @Test
    fun laCeldaDeRirYRpeFijanSuIntensidad() {
        val rir = ExerciseSet(id = "w1", targetRPE = 8.0).withTypedValue("rir", "3")
        assertEquals(3, rir.targetRIR)
        assertEquals(IntensityMode.RIR, rir.intensityMode)

        val rpe = ExerciseSet(id = "w1", targetRIR = 2).withTypedValue("rpe", "8,5")
        assertEquals(8.5, rpe.targetRPE!!, 1e-9)
        assertEquals(IntensityMode.RPE, rpe.intensityMode)
    }

    @Test
    fun elPorcentajeLimpiaRpeYRir() {
        val escrita = ExerciseSet(id = "w1", targetRPE = 8.0, targetRIR = 2).withTypedValue("percent", "78,456")
        assertEquals(78.46, escrita.targetPercentageRM!!, 1e-9)
        assertNull(escrita.targetRPE)
        assertNull(escrita.targetRIR)
    }

    @Test
    fun elTiempoAceptaMSSOSegundos() {
        assertEquals(90, parseDurationSeconds("1:30"))
        assertEquals(45, parseDurationSeconds("45"))
        assertNull(parseDurationSeconds("x"))
        assertEquals(90, ExerciseSet(id = "w1").withTypedValue("time", "1:30").targetDuration)
    }

    @Test
    fun elArrastreSumaOResta1() {
        assertEquals(9, ExerciseSet(id = "w1", targetReps = 8).steppedValue("reps", 1).targetReps)
        assertEquals(1, ExerciseSet(id = "w1", targetReps = 1).steppedValue("reps", -1).targetReps)
        assertEquals(3, ExerciseSet(id = "w1", targetRIR = 2).steppedValue("rir", 1).targetRIR)
        assertEquals(9.0, ExerciseSet(id = "w1").steppedValue("rpe", 1).targetRPE!!, 1e-9)
    }

    // Tipo de serie

    @Test
    fun elTipoDropEncendeYElTrabajoApagaLaTecnica() {
        val drop = ExerciseSet(id = "w1", targetReps = 8).asDropSet("tec-1")
        assertTrue(drop.isDropSet)
        assertTrue(drop.plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET && it.id == "tec-1" })
        assertTrue(drop.hasTechnique())

        val trabajo = drop.asWorkSet()
        assertFalse(trabajo.isDropSet)
        assertFalse(trabajo.plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET })
        assertFalse(trabajo.hasTechnique())
    }

    @Test
    fun laFilaDeDropMuestraDYLaSerieDeTrabajoSigueNumerada() {
        val exercise = SessionEditorSamples.exerciseDrop()
        val rows = toKpknSetRows(exercise, sessionSetColumns(exercise, true), emptyMap())
        assertEquals(listOf("1", "D", "2"), rows.map { it.label })
    }

    // Duplicar, intensidad y unilateral

    @Test
    fun duplicarCopiaLaSerieDebajoConOtroId() {
        val exercise = ejercicio(
            ExerciseSet(id = "a", targetReps = 8),
            ExerciseSet(id = "b", targetReps = 6, targetRIR = 1),
            ExerciseSet(id = "c", targetReps = 5),
        )

        val duplicado = exercise.withDuplicatedSet("b", "b2")

        assertEquals(listOf("a", "b", "b2", "c"), duplicado.sets.map { it.id })
        assertEquals(6, duplicado.sets[2].targetReps)
        assertEquals(1, duplicado.sets[2].targetRIR)
    }

    @Test
    fun duplicarNoTocaSuperseriesNiHuecos() {
        val superserie = ejercicio(ExerciseSet(id = "a")).copy(supersetGroupRef = "grupo")
        assertEquals(superserie, superserie.withDuplicatedSet("a", "a2"))

        val hueco = ejercicio(ExerciseSet(id = "a"), ExerciseSet(id = "h", isEmptySlot = true))
        assertEquals(hueco, hueco.withDuplicatedSet("h", "h2"))
    }

    @Test
    fun elSegmentoRirCambiaElModoYEscribeCadaSerie() {
        val exercise = ejercicio(
            ExerciseSet(id = "a", intensityMode = IntensityMode.RPE, targetRPE = 7.0),
            mode = TrainingMode.TIME,
        )

        val cambiado = exercise.withIntensity(SessionSetIntensity.RIR)

        assertEquals(TrainingMode.REPS, cambiado.trainingMode)
        assertEquals(IntensityMode.RIR, cambiado.sets[0].intensityMode)
        assertEquals(2, cambiado.sets[0].targetRIR)
        assertNull(cambiado.sets[0].targetRPE)
    }

    @Test
    fun elSegmentoPorcentajePoneElModoRm() {
        val exercise = ejercicio(ExerciseSet(id = "a", targetReps = 5))
        assertEquals(TrainingMode.RM, exercise.withIntensity(SessionSetIntensity.PERCENT).trainingMode)
    }

    @Test
    fun unilateralAlternoYPorLadosYBilateral() {
        val exercise = ejercicio(ExerciseSet(id = "a", targetReps = 8))

        val alterno = exercise.withUnilateralChoice(1)
        assertEquals(UnilateralMode.UNILATERAL_PAIRED, alterno.unilateralMode)
        assertEquals(UnilateralIntensityMode.SHARED, alterno.unilateralIntensityMode)
        assertEquals(1, alterno.unilateralChoiceIndex())

        val porLados = exercise.withUnilateralChoice(2)
        assertEquals(UnilateralIntensityMode.INDEPENDENT, porLados.unilateralIntensityMode)
        assertEquals(2, porLados.unilateralChoiceIndex())

        val bilateral = porLados.withUnilateralChoice(0)
        assertEquals(UnilateralMode.BILATERAL, bilateral.unilateralMode)
        assertEquals(0, bilateral.unilateralChoiceIndex())
    }
}
