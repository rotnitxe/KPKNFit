package com.example.kpkn.screens.stories

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverRolesOf
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.fallbackCoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.restyled
import androidx.compose.ui.graphics.toArgb
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
 * Datos de ejemplo de las tres historias: los del prototipo aprobado («Motor y fuerza»). Los usan estas pruebas y la hoja de
 * render de `ProgramStoryRenderSheetTest`.
 */
internal object ProgramStoryPrototype {
    /** La historia de la plantilla [template] con los datos del prototipo. */
    fun de(template: ProgramStoryTemplate): ProgramStory = when (template) {
        ProgramStoryTemplate.SEMANA -> ProgramStory(
            name = "Motor y fuerza",
            eyebrow = "Programa · 6 semanas",
            cover = LineCoverSpec(LineCoverStyle.PERFIL, LineCoverBackground.CARBON, seed = 3, weekLoads = emptyList()),
            content = ProgramStoryContent.Week(
                meta = "Músculo · 5 días por semana",
                days = listOf(
                    StoryDayRow("L", "Pierna A", 0.8f),
                    StoryDayRow("M", "Empuje", 0.7f),
                    StoryDayRow("X", "Tirón · Cardio", 1f),
                    StoryDayRow("J", null, 0f),
                    StoryDayRow("V", "Pierna B", 0.85f),
                    StoryDayRow("S", "Movilidad", 0.4f),
                    StoryDayRow("D", null, 0f),
                ),
            ),
        )
        ProgramStoryTemplate.BLOQUES -> ProgramStory(
            name = "Motor y fuerza",
            eyebrow = "Programa · 6 semanas",
            cover = LineCoverSpec(LineCoverStyle.ESPIRAL, LineCoverBackground.CARBON, seed = 3, currentWeek = 3),
            content = ProgramStoryContent.Blocks(
                meta = "Músculo · 3 bloques",
                weekColors = listOf(
                    Color(0xFFF49A6E), Color(0xFFF49A6E), Color(0xFFF49A6E),
                    Color(0xFFF7CF73), Color(0xFFF7CF73),
                    Color(0xFF7FCFA4),
                ),
                blocks = listOf(
                    StoryBlockRow("Base", "3 sem · Doble progresión"),
                    StoryBlockRow("Fuerza", "2 sem · Top set + back-off"),
                    StoryBlockRow("Descarga", "1 sem · Mitad de series, RIR 4"),
                ),
            ),
        )
        ProgramStoryTemplate.PROGRESO -> ProgramStory(
            name = "Motor y fuerza",
            eyebrow = "Octubre · progreso",
            cover = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 3),
            content = ProgramStoryContent.Progress(
                meta = "4 semanas · 92 % de cumplimiento",
                lifts = listOf(
                    StoryLiftRow("Sentadilla", "+7,5 kg"),
                    StoryLiftRow("Press banca", "+4 kg"),
                    StoryLiftRow("Dominadas", "+5 kg"),
                ),
            ),
        )
    }
}

/**
 * Comprueba las tres plantillas: salen a 1080 × 1920, el fondo de abajo es el negro de la marca (#0A0A0A) y el «KPKN» del pie
 * deja tinta clara. Las medidas del texto son las del lienzo de 1 px = 1 sp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ProgramStoryRenderTest {

    /** Rojo de un píxel ARGB. */
    private fun Int.red(): Int = (this shr 16) and 0xFF

    @Test
    fun cadaPlantillaSaleA1080Por1920() {
        for (template in ProgramStoryTemplate.entries) {
            val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), ProgramStoryPrototype.de(template))
            assertEquals(template.label, 1080, bitmap.width)
            assertEquals(template.label, 1920, bitmap.height)
        }
    }

    @Test
    fun elFondoDeAbajoEsElNegroDeLaMarca() {
        for (template in ProgramStoryTemplate.entries) {
            val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), ProgramStoryPrototype.de(template))
            assertEquals(template.label, 0xFF0A0A0A.toInt(), bitmap.getPixel(540, 1900))
        }
    }

    @Test
    fun elPieDejaTintaClaraConLaMarca() {
        for (template in ProgramStoryTemplate.entries) {
            val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), ProgramStoryPrototype.de(template))
            val inked = (75 until 300).any { x -> bitmap.getPixel(x, 1800).red() > 200 }
            assertTrue(template.label, inked)
        }
    }

    @Test
    fun nombresYLadosLargosSeMidenSinCruzarColumnas() {
        val context = RuntimeEnvironment.getApplication()
        val measurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
        val row = measuredPairRow(
            measurer = measurer,
            left = "Sentadilla trasera con pausa y tempo extremadamente largo".repeat(3),
            right = "4 semanas · Doble progresión con control técnico".repeat(3),
            leftStyle = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Medium),
            rightStyle = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Medium),
            rightShare = 0.46f,
        )

        val leftEnd = StoryCanvas.LEFT + row.left.size.width
        val rightStart = StoryCanvas.RIGHT - row.right.size.width
        assertTrue("Las columnas de texto no deben tocarse", leftEnd < rightStart)
        assertEquals(1, row.left.lineCount)
        assertEquals(1, row.right.lineCount)
    }

    @Test
    fun unaHistoriaLargaConMuchosBloquesConservaLosExtremosDelIndicador() {
        val colors = (0 until 56).map { index ->
            when (index) {
                0 -> Color(0xFFFF0000)
                55 -> Color(0xFF0000FF)
                else -> Color(0xFF35B879)
            }
        }
        val story = ProgramStory(
            name = "Plan de fuerza con bloques de trabajo y progresión detallada ".repeat(7),
            eyebrow = "Programa · 112 semanas",
            cover = LineCoverSpec(LineCoverStyle.PERFIL, LineCoverBackground.CARBON, seed = 3),
            content = ProgramStoryContent.Blocks(
                meta = "Músculo · 40 bloques",
                weekColors = colors,
                blocks = (1..40).map { index ->
                    StoryBlockRow("Bloque $index · acumulación y fuerza", "4 sem · Doble progresión")
                },
            ),
        )

        val context = RuntimeEnvironment.getApplication()
        val content = story.content as ProgramStoryContent.Blocks
        val measurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
        val layout = measureBlocksStoryLayout(measurer, content, availableHeight = 900f)
        assertTrue("la historia reserva una fila para indicar los bloques omitidos", layout.rows.size < content.blocks.size)
        val remaining = content.blocks.size - (layout.rows.size - 1)
        val remainder = layout.rows.last().left
        assertEquals("+$remaining bloques más", remainder.layoutInput.text.text)
        assertEquals(1, remainder.lineCount)
        assertFalse("el indicador completo cabe en su columna", remainder.hasVisualOverflow)

        val bitmap = renderProgramStory(context, story)
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(1920, bitmap.height)
            System.getenv("KPKN_RENDER_DIR")?.let { directory ->
                val output = java.io.File(directory).apply { mkdirs() }.resolve("historia_bloques_muchos.png")
                output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val firstWeekIsVisible = (1150 until 1800).any { y ->
                (75..125).any { x ->
                    val pixel = bitmap.getPixel(x, y)
                    android.graphics.Color.red(pixel) > 170 && android.graphics.Color.green(pixel) < 80 && android.graphics.Color.blue(pixel) < 80
                }
            }
            val lastWeekIsVisible = (1150 until 1800).any { y ->
                (955..1020).any { x ->
                    val pixel = bitmap.getPixel(x, y)
                    android.graphics.Color.blue(pixel) > 170 && android.graphics.Color.red(pixel) < 80 && android.graphics.Color.green(pixel) < 80
                }
            }
            assertTrue("La primera semana de la cinta sigue visible", firstWeekIsVisible)
            assertTrue("La última semana de la cinta sigue visible", lastWeekIsVisible)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun unaFotoPersonalQuedaEncimaDeLaIdentidadDeEstudioEnLaHistoria() {
        val identity = fallbackCoverIdentity("story-photo")
            .restyled(CoverStudioStyle.WAVES, CoverPaletteKind.NOCHE)
        val story = ProgramStoryPrototype.de(ProgramStoryTemplate.SEMANA).copy(
            studioCover = identity,
            personalCoverValue = "content://media/external/images/media/42",
        )
        val photo = android.graphics.Bitmap.createBitmap(40, 24, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }

        val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), story, photo.asImageBitmap())
        try {
            val top = bitmap.getPixel(540, 100)
            assertTrue("La foto personal pinta sobre el azul de NOCHE", android.graphics.Color.red(top) > 150)
            assertTrue("El velo conserva la tinta legible", android.graphics.Color.green(top) < 50)
        } finally {
            bitmap.recycle()
            photo.recycle()
        }
    }

    @Test
    fun sieteDiasConTituloLargoSiguenVisiblesEnLaHistoria() {
        val story = ProgramStoryPrototype.de(ProgramStoryTemplate.SEMANA).copy(
            name = "Plan semanal de fuerza y preparación con sesiones distribuidas en los siete días ".repeat(6),
        )

        val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), story)
        try {
            System.getenv("KPKN_RENDER_DIR")?.let { directory ->
                val output = java.io.File(directory).apply { mkdirs() }.resolve("historia_semana_titulo_largo.png")
                output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val inkRuns = countLeftColumnInkRuns(bitmap, startY = 1180, endY = 1780)
            assertTrue("Las siete letras de día siguen visibles, filas encontradas: $inkRuns", inkRuns >= 7)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun laPaletaDeEstudioEsElFondoVisibleDeLaHistoria() {
        val identity = fallbackCoverIdentity("story-paper")
            .restyled(CoverStudioStyle.WAVES, CoverPaletteKind.PAPEL)
        val story = ProgramStoryPrototype.de(ProgramStoryTemplate.SEMANA).copy(studioCover = identity)

        val bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), story)
        try {
            assertEquals(coverRolesOf(identity).background.toArgb(), bitmap.getPixel(10, 10))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun unaEleccionDeOndasYNocheSeVeEnLaHistoriaSobreDiscosYCremaHeredados() {
        val base = Program(
            id = "programa-personal",
            name = "Plan propio",
            structureTemplateId = "generated:powerlifting",
        )
        val inherited = coverIdentityFor(base.id, base.structureTemplateId)
        assertEquals(CoverStudioStyle.PLATES, inherited.style)
        assertEquals(CoverPaletteKind.PAPEL, inherited.palette)
        assertEquals(LineCoverBackground.CREMA, inherited.lines?.background)
        val chosen = base.copy(coverImage = "kpkn-studio://WAVES/NOCHE")
        val today = LocalDate.of(2026, 10, 27)
        val chosenStory = ProgramStoryBuilder.build(chosen, ProgramStoryTemplate.SEMANA, emptyList(), today)
        val identity = chosenStory.studioCover
        assertTrue("Ondas debe reemplazar la especificación heredada de Discos", identity != null && identity.lines == null)
        assertEquals(CoverStudioStyle.WAVES, identity?.style)
        assertEquals(CoverPaletteKind.NOCHE, identity?.palette)

        // Comparamos el render real con Discos/Noche: el programa de base heredaba Discos/Crema.
        val platesStory = ProgramStoryBuilder.build(
            base.copy(coverImage = "kpkn-studio://PLATES/NOCHE"),
            ProgramStoryTemplate.SEMANA,
            emptyList(),
            today,
        )
        val wavesBitmap = renderProgramStory(RuntimeEnvironment.getApplication(), chosenStory)
        val platesBitmap = renderProgramStory(RuntimeEnvironment.getApplication(), platesStory)
        try {
            assertEquals(coverRolesOf(identity!!).background.toArgb(), wavesBitmap.getPixel(10, 10))
            val differentCoverPixels = (0 until StoryCanvas.ART_BOTTOM.toInt()).sumOf { y ->
                (0 until wavesBitmap.width).count { x -> wavesBitmap.getPixel(x, y) != platesBitmap.getPixel(x, y) }
            }
            assertTrue("La portada de Ondas debe cambiar píxeles visibles frente a Discos", differentCoverPixels > 10_000)
        } finally {
            wavesBitmap.recycle()
            platesBitmap.recycle()
        }
    }

    private fun countLeftColumnInkRuns(bitmap: android.graphics.Bitmap, startY: Int, endY: Int): Int {
        var runs = 0
        var hitsInRun = 0
        var emptyRows = 0
        for (y in startY until endY) {
            val hit = (75..140).any { x ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.red(pixel) > 75 && android.graphics.Color.green(pixel) > 70
            }
            if (hit) {
                if (emptyRows > 14 && hitsInRun > 0) {
                    runs++
                    hitsInRun = 0
                }
                hitsInRun++
                emptyRows = 0
            } else {
                emptyRows++
            }
        }
        if (hitsInRun > 0) runs++
        return runs
    }
}
