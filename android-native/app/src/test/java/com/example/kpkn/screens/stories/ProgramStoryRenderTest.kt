package com.example.kpkn.screens.stories

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import org.junit.Assert.assertEquals
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
}
