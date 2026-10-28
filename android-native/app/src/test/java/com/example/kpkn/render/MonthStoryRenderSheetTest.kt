package com.example.kpkn.render

import android.graphics.Bitmap
import com.example.kpkn.screens.stories.MonthLift
import com.example.kpkn.screens.stories.MonthMuscle
import com.example.kpkn.screens.stories.MonthStat
import com.example.kpkn.screens.stories.MonthStory
import com.example.kpkn.screens.stories.MonthStoryTemplate
import com.example.kpkn.screens.stories.renderMonthStory
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * HOJA DE DIBUJO de las historias del mes: no comprueba nada, escribe PNG de 1080 × 1920 en `KPKN_RENDER_DIR`: `mes_resumen.png`,
 * `mes_constancia.png`, `mes_fuerza.png` y `mes_musculos.png`, con los datos del prototipo («Motor y fuerza», octubre de 2026).
 * Sin `KPKN_RENDER_DIR` la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MonthStoryRenderSheetTest {

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
    private fun shoot(template: MonthStoryTemplate, name: String) {
        onlyWhenAsked()
        val folder = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        val bitmap: Bitmap = renderMonthStory(RuntimeEnvironment.getApplication(), prototipo(), template)
        File(folder, "$name.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }

    @Test
    fun escribeElResumenDelMes() = shoot(MonthStoryTemplate.RESUMEN, "mes_resumen")

    @Test
    fun escribeLaConstancia() = shoot(MonthStoryTemplate.CONSTANCIA, "mes_constancia")

    @Test
    fun escribeLaFuerza() = shoot(MonthStoryTemplate.FUERZA, "mes_fuerza")

    @Test
    fun escribeLosMusculos() = shoot(MonthStoryTemplate.MUSCULOS, "mes_musculos")

    /** Los datos del prototipo aprobado: octubre de 2026, «Motor y fuerza». */
    private fun prototipo(): MonthStory = MonthStory(
        monthLabel = "octubre 2026",
        monthName = "octubre",
        programName = "Motor y fuerza",
        stats = listOf(
            MonthStat("14", null, "Sesiones"),
            MonthStat("12", "h 40", "Entrenando"),
            MonthStat("42,4", "t", "Movidas"),
            MonthStat("92", "%", "Del plan"),
        ),
        badge = "Marca: sentadilla 142,5 kg",
        month = YearMonth.of(2026, 10),
        trainedDays = setOf(6, 7, 8, 10, 11, 13, 14, 15, 17, 20, 21, 22, 27, 28),
        markDay = 27,
        sessions = 14,
        consistencyMeta = "12 h 40 min entrenando · 92 % del plan",
        longestStreak = 9,
        lifts = listOf(
            MonthLift("Sentadilla", listOf(135.0, 136.0, 138.0, 139.0, 141.0, 142.5), "+7,5 kg"),
            MonthLift("Press banca", listOf(97.0, 97.5, 99.0, 99.0, 100.0, 101.0), "+4 kg"),
            MonthLift("Dominadas", listOf(20.0, 21.0, 22.0, 23.0, 24.0, 25.0), "+5 kg"),
        ),
        totalSets = 246,
        muscles = listOf(
            MonthMuscle("Dorsales", 58),
            MonthMuscle("Deltoides", 56),
            MonthMuscle("Cuádriceps", 52),
            MonthMuscle("Pectorales", 46),
            MonthMuscle("Glúteos", 42),
            MonthMuscle("Isquios", 36),
            MonthMuscle("Bíceps", 34),
            MonthMuscle("Tríceps", 28),
            MonthMuscle("Pantorrillas", 0),
        ),
    )
}
