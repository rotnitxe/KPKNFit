package com.example.kpkn.screens.stories

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.workout.FinishLiftProgress
import com.example.kpkn.screens.workout.FinishPresentationContext
import com.example.kpkn.screens.workout.FinishProgressPoint
import com.example.kpkn.screens.workout.StoryPhoto
import com.example.kpkn.screens.workout.WorkoutStoryData
import com.example.kpkn.screens.workout.WorkoutStoryOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pruebas puras del modelo de las historias de entreno: fecha, cifras, ganancias, ejercicio y marca. No usan Robolectric: el modelo
 * no toca fuentes ni recursos de Android, porque los estilos de texto viven en un objeto que solo se carga al dibujar.
 */
class WorkoutStoryModelTest {

    /** 28 de octubre de 2026 es miércoles. */
    private val miercoles = LocalDate.of(2026, 10, 28)

    /** Punto del historial de un ejercicio con su e1RM. */
    private fun punto(fecha: String, e1rm: Double): FinishProgressPoint =
        FinishProgressPoint(date = fecha, weight = e1rm, reps = 5, estimated1Rm = e1rm)

    /** Ejercicio con su mejor serie de [mejorPeso] kg × 5 frente a la sesión anterior con [pesoAnterior] kg (null = sin anterior). */
    private fun ejercicio(
        id: String,
        nombre: String,
        mejorPeso: Double,
        pesoAnterior: Double?,
        rir: Int? = null,
        historial: List<FinishProgressPoint> = emptyList(),
    ): FinishLiftProgress = FinishLiftProgress(
        exerciseId = id,
        name = nombre,
        best = CompletedSet(id = "$id-mejor", weight = mejorPeso, reps = 5, rir = rir),
        setCount = 3,
        previous = pesoAnterior?.let { FinishProgressPoint(date = "2026-10-21", weight = it, reps = 5, estimated1Rm = it) },
        history = historial,
    )

    /** Datos mínimos de un entreno «Tirón» de [miercoles]; cada prueba cambia solo lo que necesita. */
    private fun datos(
        lifts: List<FinishLiftProgress> = emptyList(),
        averageRir: Double? = 1.6,
        week: Int? = 4,
        records: Int = 0,
        photos: List<StoryPhoto> = emptyList(),
        program: Program? = null,
    ): WorkoutStoryData = WorkoutStoryData(
        sessionName = "Tirón",
        presentation = FinishPresentationContext(
            program = program,
            weekNumber = week,
            photos = photos,
            records = records,
            today = miercoles,
        ),
        durationMinutes = 48,
        volume = 6240.0,
        sets = 17,
        averageRir = averageRir,
        intensity = "Intensificación",
        lifts = lifts,
    )

    @Test
    fun laFechaVaEnMayusculasYLlevaLaSemanaSiLaHay() {
        assertEquals("MIÉ 28 OCT · SEMANA 4", workoutStoryFrom(datos(week = 4), WorkoutStoryOptions()).dateLine)
        assertEquals("MIÉ 28 OCT", workoutStoryFrom(datos(week = null), WorkoutStoryOptions()).dateLine)
    }

    @Test
    fun elTituloEsElNombreDeLaSesionYLaMetaEsElPrograma() {
        val sinPrograma = workoutStoryFrom(datos(), WorkoutStoryOptions())
        assertEquals("Tirón", sinPrograma.title)
        assertNull(sinPrograma.meta)

        val conPrograma = workoutStoryFrom(datos(program = Program(id = "p1", name = "Motor y fuerza")), WorkoutStoryOptions())
        assertEquals("Motor y fuerza", conPrograma.meta)
    }

    @Test
    fun laDuracionSoloAparecePidiendoLaOpcion() {
        val con = workoutStoryFrom(datos(), WorkoutStoryOptions())
        assertEquals(WorkoutStat("48", "min", "Duración"), con.stats.first())

        val sin = workoutStoryFrom(datos(), WorkoutStoryOptions(duration = false))
        assertEquals(listOf("Volumen", "Series", "RIR medio"), sin.stats.map { it.label })
    }

    @Test
    fun elVolumenTraeSeparadorDeMilesYElRirMedioUnaCifraConComa() {
        val stats = workoutStoryFrom(datos(), WorkoutStoryOptions()).stats

        assertEquals(WorkoutStat("6.240", "kg", "Volumen"), stats[1])
        assertEquals(WorkoutStat("17", null, "Series"), stats[2])
        assertEquals(WorkoutStat("1,6", null, "RIR medio"), stats[3])
    }

    @Test
    fun elRirSoloAparecePidiendoLaOpcionYTeniendoDato() {
        assertTrue(workoutStoryFrom(datos(), WorkoutStoryOptions(intensity = false)).stats.none { it.label == "RIR medio" })
        assertTrue(workoutStoryFrom(datos(averageRir = null), WorkoutStoryOptions()).stats.none { it.label == "RIR medio" })
    }

    @Test
    fun lasGananciasSonLasDosMayoresYLosEnterosNoLLevanCeroDecimal() {
        val lifts = listOf(
            ejercicio("r", "Remo con barra", mejorPeso = 62.5, pesoAnterior = 60.0),
            ejercicio("d", "Dominadas lastradas", mejorPeso = 75.0, pesoAnterior = 70.0),
            ejercicio("p", "Press banca", mejorPeso = 90.0, pesoAnterior = 80.0),
            ejercicio("s", "Sentadilla", mejorPeso = 100.0, pesoAnterior = 100.0),
        )

        val story = workoutStoryFrom(datos(lifts = lifts), WorkoutStoryOptions())
        assertEquals(listOf(WorkoutGain("Press banca", "+10 kg"), WorkoutGain("Dominadas lastradas", "+5 kg")), story.gains)

        val soloRemo = workoutStoryFrom(datos(lifts = lifts.take(1)), WorkoutStoryOptions())
        assertEquals(listOf(WorkoutGain("Remo con barra", "+2,5 kg")), soloRemo.gains)

        assertTrue(workoutStoryFrom(datos(lifts = lifts), WorkoutStoryOptions(improvements = false)).gains.isEmpty())
    }

    @Test
    fun elEjercicioEsElPedidoOElDeMayorGanancia() {
        val sentadilla = ejercicio(
            "s",
            "Sentadilla trasera",
            mejorPeso = 135.0,
            pesoAnterior = 127.5,
            historial = listOf(punto("2026-10-01", 135.0), punto("2026-10-14", 137.5), punto("2026-10-28", 142.5)),
        )
        val remo = ejercicio("r", "Remo con barra", mejorPeso = 62.5, pesoAnterior = 60.0, historial = listOf(punto("2026-10-01", 70.0)))
        val delDia = datos(lifts = listOf(remo, sentadilla))

        val porDefecto = workoutStoryFrom(delDia, WorkoutStoryOptions())
        assertEquals("Sentadilla trasera", porDefecto.exerciseName)
        assertEquals("142,5", porDefecto.exerciseE1rm)
        assertEquals("+7,5 kg", porDefecto.exerciseDelta)
        assertEquals(3, porDefecto.exerciseSessions)
        assertEquals(listOf(135.0, 137.5, 142.5), porDefecto.exercisePoints)
        assertEquals("135 kg × 5", porDefecto.bestSet)

        assertEquals("Remo con barra", workoutStoryFrom(delDia, WorkoutStoryOptions(exerciseId = "r")).exerciseName)
        assertEquals("Sentadilla trasera", workoutStoryFrom(delDia, WorkoutStoryOptions(exerciseId = "no-existe")).exerciseName)
    }

    @Test
    fun sinDosSesionesNoHayGananciaDelEjercicio() {
        val remo = ejercicio("r", "Remo con barra", mejorPeso = 62.5, pesoAnterior = 60.0, historial = listOf(punto("2026-10-01", 70.0)))

        val story = workoutStoryFrom(datos(lifts = listOf(remo)), WorkoutStoryOptions())

        assertNull(story.exerciseDelta)
        assertEquals(1, story.exerciseSessions)
        assertEquals("70", story.exerciseE1rm)
    }

    @Test
    fun laMejorSerieYSuEsfuerzoSaleDeLaMejorSerieDelEjercicio() {
        val conRir = ejercicio("s", "Sentadilla", mejorPeso = 120.0, pesoAnterior = 115.0, rir = 1)
        val story = workoutStoryFrom(datos(lifts = listOf(conRir)), WorkoutStoryOptions())
        assertEquals("120 kg × 5", story.bestSet)
        assertEquals("RIR 1", story.bestEffort)

        val sinRir = ejercicio("s", "Sentadilla", mejorPeso = 120.0, pesoAnterior = 115.0)
        assertNull(workoutStoryFrom(datos(lifts = listOf(sinRir)), WorkoutStoryOptions()).bestEffort)
    }

    @Test
    fun sinEjerciciosLosCamposDelEjercicioQuedanVacios() {
        val story = workoutStoryFrom(datos(), WorkoutStoryOptions(exerciseId = "s"))

        assertNull(story.exerciseName)
        assertNull(story.exerciseE1rm)
        assertNull(story.exerciseDelta)
        assertEquals(0, story.exerciseSessions)
        assertTrue(story.exercisePoints.isEmpty())
        assertNull(story.bestSet)
        assertNull(story.bestEffort)
        assertTrue(story.gains.isEmpty())
    }

    @Test
    fun laGraficaTomaComoMaximoLasOchoUltimasSesiones() {
        val historial = (1..10).map { punto("sesion-$it", it.toDouble()) }
        val remo = ejercicio("r", "Remo con barra", mejorPeso = 62.5, pesoAnterior = 60.0, historial = historial)

        val story = workoutStoryFrom(datos(lifts = listOf(remo)), WorkoutStoryOptions())

        assertEquals((3..10).map { it.toDouble() }, story.exercisePoints)
        assertEquals(10, story.exerciseSessions)
    }

    @Test
    fun lasMarcasNuevasYLaFotoSalenDelContexto() {
        val story = workoutStoryFrom(
            datos(records = 2, photos = listOf(StoryPhoto(path = "/fotos/1.jpg"), StoryPhoto(path = "/fotos/2.jpg"))),
            WorkoutStoryOptions(),
        )

        assertEquals(2, story.records)
        assertEquals("/fotos/1.jpg", story.photoPath)
        assertNull(workoutStoryFrom(datos(), WorkoutStoryOptions()).photoPath)
    }
}
