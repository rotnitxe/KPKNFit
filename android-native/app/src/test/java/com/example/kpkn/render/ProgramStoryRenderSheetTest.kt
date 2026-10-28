package com.example.kpkn.render

import android.graphics.Bitmap
import com.example.kpkn.screens.stories.ProgramStoryPrototype
import com.example.kpkn.screens.stories.ProgramStoryTemplate
import com.example.kpkn.screens.stories.renderProgramStory
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOJA DE DIBUJO de las historias de programa: no comprueba nada, escribe PNG de 1080 × 1920 en `KPKN_RENDER_DIR`:
 * `historia_semana.png`, `historia_bloques.png` y `historia_progreso.png`, con los datos del prototipo («Motor y fuerza»).
 * Sin `KPKN_RENDER_DIR` la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ProgramStoryRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Pinta la plantilla [template] y la guarda como [name].png en la carpeta de `KPKN_RENDER_DIR`. */
    private fun shoot(template: ProgramStoryTemplate, name: String) {
        onlyWhenAsked()
        val folder = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        val bitmap: Bitmap = renderProgramStory(RuntimeEnvironment.getApplication(), ProgramStoryPrototype.de(template))
        File(folder, "$name.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }

    @Test
    fun escribeLaHistoriaDeSemana() = shoot(ProgramStoryTemplate.SEMANA, "historia_semana")

    @Test
    fun escribeLaHistoriaDeBloques() = shoot(ProgramStoryTemplate.BLOQUES, "historia_bloques")

    @Test
    fun escribeLaHistoriaDeProgreso() = shoot(ProgramStoryTemplate.PROGRESO, "historia_progreso")
}
