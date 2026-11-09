package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comprueba que una portada propia reemplaza la identidad heredada; los píxeles se verifican en Android real. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi", application = android.app.Application::class)
class ProgramPosterCoverRenderTest {

    @Test
    fun portadaPropiaReemplazaEstiloYPaletaHeredadosDeLaPlantilla() {
        val program = Program(
            id = "programa-personal",
            name = "Plan propio",
            structureTemplateId = "generated:powerlifting",
        )
        val inherited = coverIdentityFor(program.id, program.structureTemplateId)
        assertEquals(CoverStudioStyle.PLATES, inherited.style)
        assertEquals(CoverPaletteKind.PAPEL, inherited.palette)
        assertEquals(LineCoverBackground.CREMA, inherited.lines?.background)
        val waves = coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://WAVES/NOCHE"))
        val plates = coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://PLATES/NOCHE"))

        assertEquals(CoverStudioStyle.WAVES, waves.style)
        assertEquals(CoverPaletteKind.NOCHE, waves.palette)
        assertNull("Ondas debe quitar la especificación heredada de Discos", waves.lines)
        assertEquals(LineCoverBackground.NOCHE, plates.lines?.background)
        assertNotEquals("La paleta elegida reemplaza PAPEL heredado", inherited.palette, waves.palette)
    }
}
