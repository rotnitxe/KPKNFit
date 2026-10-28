package com.example.kpkn.render

import android.graphics.Bitmap
import com.example.kpkn.screens.stories.WorkoutGain
import com.example.kpkn.screens.stories.WorkoutStat
import com.example.kpkn.screens.stories.WorkoutStory
import com.example.kpkn.screens.stories.renderWorkoutStoryBitmap
import com.example.kpkn.screens.workout.WorkoutStoryTemplate
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
 * HOJA DE DIBUJO de las historias del entreno: no comprueba nada, escribe PNG de 1080 × 1920 en `KPKN_RENDER_DIR`:
 * `entreno_sesion.png`, `entreno_ejercicio.png` y `entreno_fotos.png` (sin foto: la portada de fondo), con los datos del prototipo
 * («Tirón», semana 4). Sin `KPKN_RENDER_DIR` la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutStoryRenderSheetTest {

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
    private fun shoot(template: WorkoutStoryTemplate, name: String) {
        onlyWhenAsked()
        val folder = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        val bitmap: Bitmap = renderWorkoutStoryBitmap(RuntimeEnvironment.getApplication(), prototipo(), template)
        File(folder, "$name.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }

    @Test
    fun escribeLaSesion() = shoot(WorkoutStoryTemplate.GENERAL, "entreno_sesion")

    @Test
    fun escribeElEjercicio() = shoot(WorkoutStoryTemplate.EXERCISE, "entreno_ejercicio")

    @Test
    fun escribeLasFotos() = shoot(WorkoutStoryTemplate.PHOTOS, "entreno_fotos")

    /** Los datos del prototipo aprobado: «Tirón», miércoles de la semana 4 de «Motor y fuerza». */
    private fun prototipo(): WorkoutStory = WorkoutStory(
        dateLine = "MIÉ 28 OCT · SEMANA 4",
        title = "Tirón",
        meta = "Motor y fuerza",
        stats = listOf(
            WorkoutStat("48", "min", "Duración"),
            WorkoutStat("6.240", "kg", "Volumen"),
            WorkoutStat("17", null, "Series"),
            WorkoutStat("1,6", null, "RIR medio"),
        ),
        gains = listOf(WorkoutGain("Dominadas lastradas", "+2,5 kg"), WorkoutGain("Remo con barra", "+2,5 kg")),
        records = 1,
        exerciseName = "Sentadilla trasera",
        exerciseE1rm = "142,5",
        exerciseDelta = "+7,5 kg",
        exerciseSessions = 6,
        exercisePoints = listOf(135.0, 136.5, 138.0, 139.0, 141.0, 142.5),
        bestSet = "120 kg × 5",
        bestEffort = "RIR 1",
        photoPath = null,
    )
}
