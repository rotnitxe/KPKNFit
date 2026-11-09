package com.example.kpkn.screens.stories

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pruebas de las historias del programa: las plantillas que se ofrecen, el contenido de cada una con los datos del programa y de sus
 * registros, el formato de la ganancia y que el fondo de Progreso queda negro bajo la portada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ProgramStoryBuilderTest {

    /** Hoy, para los eyebrows con mes: 27 de octubre de 2026. */
    private val hoy = LocalDate.of(2026, 10, 27)

    @Test
    fun sinRegistrosSoloSemanaYBloques() {
        assertEquals(
            listOf(ProgramStoryTemplate.SEMANA, ProgramStoryTemplate.BLOQUES),
            ProgramStoryBuilder.templates(dosBloques(), emptyList()),
        )
    }

    @Test
    fun semanaTieneUnaFilaPorDiaConSuCargaYSuResumen() {
        val story = ProgramStoryBuilder.build(dosBloques(), ProgramStoryTemplate.SEMANA, emptyList(), hoy)
        val content = story.content as ProgramStoryContent.Week
        assertEquals(7, content.days.size)
        assertEquals(StoryDayRow("L", "A · B", 1f), content.days[0])
        assertEquals(StoryDayRow("M", null, 0f), content.days[1])
        assertEquals(StoryDayRow("J", "C", 4f / 11f), content.days[3])
        assertTrue(content.meta, content.meta.endsWith("2 días por semana"))
        assertEquals("Programa · 3 semanas", story.eyebrow)
    }

    @Test
    fun tituloLargoConservaLosSieteDiasEnElOrdenDeInicioAunqueEntreneTres() {
        val longName = "Plan de fuerza con preparación física, técnica y sesiones distribuidas toda la semana ".repeat(5)
        val program = dosBloques().copy(name = longName, startDay = 3, weekDays = 3)

        val story = ProgramStoryBuilder.build(program, ProgramStoryTemplate.SEMANA, emptyList(), hoy)
        val content = story.content as ProgramStoryContent.Week

        assertEquals(longName, story.name)
        assertEquals(listOf("X", "J", "V", "S", "D", "L", "M"), content.days.map { it.letter })
        assertEquals("C", content.days[1].label)
        assertEquals("A · B", content.days[5].label)
    }

    @Test
    fun bloquesTieneUnaPastillaPorSemanaYUnaFilaPorBloque() {
        val story = ProgramStoryBuilder.build(dosBloques(), ProgramStoryTemplate.BLOQUES, emptyList(), hoy)
        val content = story.content as ProgramStoryContent.Blocks
        assertEquals(StoryBlockRow("Base", "2 sem · Carga lineal"), content.blocks[0])
        assertEquals(StoryBlockRow("Fuerza", "1 sem"), content.blocks[1])
        assertEquals(3, content.weekColors.size)
        assertEquals(ProgramStoryBuilder.baseCover(dosBloques()).style, story.cover.style)
    }

    @Test
    fun muchosBloquesConservanTodasLasFilasYElIndicadorDeSemanas() {
        val blocks = (1..12).map { index ->
            bloque("b$index", "Bloque $index · Fuerza", weeks = listOf(semana("s$index")))
        }
        val program = programa("doce-bloques", blocks).copy(name = "Fuerza y técnica · ciclo completo")

        val content = ProgramStoryBuilder.build(program, ProgramStoryTemplate.BLOQUES, emptyList(), hoy).content as ProgramStoryContent.Blocks

        assertEquals(12, content.blocks.size)
        assertEquals(12, content.weekColors.size)
    }

    @Test
    fun historiaConstruidaDeCuarentaBloquesMantieneElIndicadorCompleto() {
        val longName = "Programa de fuerza con muchos bloques de acumulación e intensificación ".repeat(5)
        val blocks = (1..40).map { index ->
            bloque("b$index", "Bloque $index · Fuerza", weeks = listOf(semana("s$index")))
        }
        val program = programa("cuarenta-bloques", blocks).copy(name = longName)
        val story = ProgramStoryBuilder.build(program, ProgramStoryTemplate.BLOQUES, emptyList(), hoy)
        val content = story.content as ProgramStoryContent.Blocks
        val context = RuntimeEnvironment.getApplication()
        val measurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
        val layout = measureBlocksStoryLayout(measurer, content, availableHeight = 900f)
        val visibleBlocks = layout.rows.size - 1
        val remainder = layout.rows.last().left

        assertEquals(longName, story.name)
        assertEquals(40, content.blocks.size)
        assertEquals(40, content.weekColors.size)
        assertTrue("La historia reserva una fila para indicar los bloques omitidos", visibleBlocks in 1 until content.blocks.size)
        assertEquals("+${content.blocks.size - visibleBlocks} bloques más", remainder.layoutInput.text.text)
        assertEquals(1, remainder.lineCount)
        assertFalse("El indicador completo cabe en su columna", remainder.hasVisualOverflow)
    }

    @Test
    fun elEstiloElegidoSustituyeAlDeLaPlantilla() {
        val story = ProgramStoryBuilder.build(dosBloques(), ProgramStoryTemplate.BLOQUES, emptyList(), hoy, LineCoverStyle.ONDAS)
        assertEquals(LineCoverStyle.ONDAS, story.cover.style)
    }

    @Test
    fun laHistoriaPorDefectoConservaLaIdentidadEstudioYLaEleccionGuardada() {
        val program = Program(
            id = "mi-plan",
            name = "Plan propio",
            structureTemplateId = "generated:strength-muscle",
            coverImage = "kpkn-studio://WAVES/NOCHE",
        )

        val week = ProgramStoryBuilder.build(program, ProgramStoryTemplate.SEMANA, emptyList(), hoy)
        val blocks = ProgramStoryBuilder.build(program, ProgramStoryTemplate.BLOQUES, emptyList(), hoy)
        val edited = ProgramStoryBuilder.build(program, ProgramStoryTemplate.SEMANA, emptyList(), hoy, LineCoverStyle.ONDAS)
        val reopened = ProgramStoryBuilder.build(program.copy(), ProgramStoryTemplate.SEMANA, emptyList(), hoy)

        assertEquals(CoverStudioStyle.WAVES, week.studioCover?.style)
        assertEquals(CoverPaletteKind.NOCHE, week.studioCover?.palette)
        assertEquals(week.studioCover, blocks.studioCover)
        assertEquals(LineCoverStyle.ONDAS, week.cover.style)
        assertEquals(LineCoverBackground.NOCHE, week.cover.background)
        assertEquals(null, edited.studioCover)
        assertEquals(week.studioCover, reopened.studioCover)
        assertEquals(week.cover, reopened.cover)
    }

    @Test
    fun unaFotoPersonalYUnaReferenciaEmpaquetadaLleganALaHistoria() {
        val photo = "content://media/external/images/media/42"
        val withPhoto = programa("foto", listOf(bloque("b", "Ciclo", weeks = listOf(semana("s1"))))).copy(coverImage = photo)
        val withLegacyCover = withPhoto.copy(coverImage = "kpkn-cover://native:strength-foundation-v2")

        assertEquals(photo, ProgramStoryBuilder.build(withPhoto, ProgramStoryTemplate.SEMANA, emptyList(), hoy).personalCoverValue)
        assertEquals("kpkn-cover://native:strength-foundation-v2", ProgramStoryBuilder.build(withLegacyCover, ProgramStoryTemplate.SEMANA, emptyList(), hoy).personalCoverValue)
    }

    @Test
    fun unaRutinaDeUnaSemanaSeLlamaRutina() {
        val rutina = programa("rutina", listOf(bloque("b", "Ciclo", weeks = listOf(semana("s1", sesionesSemana())))))
        assertEquals("Rutina", ProgramStoryBuilder.build(rutina, ProgramStoryTemplate.SEMANA, emptyList(), hoy).eyebrow)
    }

    @Test
    fun laGananciaSeEscribeConComaYSinCeroDecimal() {
        assertEquals("+7,5 kg", ProgramStoryBuilder.formatGainKg(7.5))
        assertEquals("+4 kg", ProgramStoryBuilder.formatGainKg(4.0))
        assertEquals("+2,5 kg", ProgramStoryBuilder.formatGainKg(2.3))
    }

    @Test
    fun progresoCuentaLasSemanasRegistradasYLoQueHaSubido() {
        val program = programa("cuatro", listOf(bloque("b", "Ciclo", weeks = (1..4).map { semana("s$it", listOf(sesionFuerza(it))) })))
        val logs = (1..4).map { registro(it) }
        assertTrue(ProgramStoryTemplate.PROGRESO in ProgramStoryBuilder.templates(program, logs))
        val story = ProgramStoryBuilder.build(program, ProgramStoryTemplate.PROGRESO, logs, hoy)
        assertEquals("octubre · progreso", story.eyebrow)
        val content = story.content as ProgramStoryContent.Progress
        assertEquals("4 semanas · 100 % de cumplimiento", content.meta)
        assertEquals(1, content.lifts.size)
        assertEquals("Sentadilla", content.lifts[0].name)
        assertTrue(content.lifts[0].gain, content.lifts[0].gain.startsWith("+"))
    }

    @Test
    fun elFondoDeProgresoEsNegroSolidoBajoLaPortada() {
        val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), ProgramStoryPrototype.de(ProgramStoryTemplate.PROGRESO))
        val negro = 0xFF0A0A0A.toInt()
        // Bajo el texto de la meta (que acaba hacia x = 760) no hay nada de la portada: ni arte ni costura del degradado.
        for (y in 1089 until 1113) {
            for (x in (0 until 60) + (770 until 1080)) {
                assertEquals("x=$x y=$y", negro, bitmap.getPixel(x, y))
            }
        }
    }

    /** Dos bloques: «Bloque 1 · Base» (dos semanas, carga lineal) y «Bloque 2 · Fuerza» (una semana, sin progresión). */
    private fun dosBloques(): Program = programa(
        "dos",
        listOf(
            bloque("b1", "Bloque 1 · Base", BlockProgressionScheme.LINEAR_LOAD, listOf(semana("s1", sesionesSemana()), semana("s2"))),
            bloque("b2", "Bloque 2 · Fuerza", null, listOf(semana("s3"))),
        ),
    )

    /** Las sesiones de la primera semana: «A» y «B» el lunes (A con más series) y «C» el jueves. */
    private fun sesionesSemana(): List<Session> = listOf(
        sesion("a", "A", dayOfWeek = 1, series = listOf(3, 3, 3)),
        sesion("b", "B", dayOfWeek = 1, series = listOf(2)),
        sesion("c", "C", dayOfWeek = 4, series = listOf(4)),
    )

    /** Una sesión con un ejercicio por cada cifra de [series], con esas series. */
    private fun sesion(id: String, name: String, dayOfWeek: Int, series: List<Int>): Session = Session(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        exercises = series.mapIndexed { index, sets ->
            Exercise(id = "$id-$index", name = "Ejercicio $index", sets = List(sets) { ExerciseSet(id = "$id-$index-$it") })
        },
    )

    /** Una sesión de fuerza del lunes, con una sentadilla. */
    private fun sesionFuerza(numero: Int): Session = Session(
        id = "t$numero",
        name = "Fuerza",
        dayOfWeek = 1,
        exercises = listOf(Exercise(id = "sq", name = "Sentadilla", sets = listOf(ExerciseSet(id = "x$numero")))),
    )

    /** El registro de la sesión de la semana [numero] del programa «cuatro»: la sentadilla sube 5 kg cada semana. */
    private fun registro(numero: Int): WorkoutLog = WorkoutLog(
        id = "log$numero",
        programId = "cuatro",
        sessionId = "t$numero",
        sessionName = "Fuerza",
        date = "2026-10-0${numero}T10:00:00.000Z",
        durationMinutes = 60,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "sq",
                exerciseName = "Sentadilla",
                sets = listOf(CompletedSet(id = "c$numero", weight = 100.0 + 5 * numero, reps = 5)),
            ),
        ),
        weekId = "s$numero",
    )

    /** Un programa con un solo macrociclo que lleva los [blocks] dados. */
    private fun programa(id: String, blocks: List<Block>): Program = Program(
        id = id,
        name = "Motor y fuerza",
        macrocycles = listOf(Macrocycle(id = "$id-mc", name = "Macrociclo", blocks = blocks)),
    )

    /** Un bloque con un solo mesociclo de [weeks]. */
    private fun bloque(
        id: String,
        name: String,
        scheme: BlockProgressionScheme? = null,
        weeks: List<ProgramWeek>,
    ): Block = Block(
        id = id,
        name = name,
        mesocycles = listOf(Mesocycle(id = "$id-m", name = "Mesociclo", weeks = weeks)),
        progressionScheme = scheme,
    )

    /** Una semana con las [sessions] dadas. */
    private fun semana(id: String, sessions: List<Session> = emptyList()): ProgramWeek =
        ProgramWeek(id = id, name = "Semana $id", sessions = sessions)
}
