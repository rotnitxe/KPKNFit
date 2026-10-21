package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.workout.components.AlbumRowTag
import com.example.kpkn.screens.workout.components.AlbumSelectTag
import com.example.kpkn.screens.workout.components.AlbumSheetContent
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.test.TestScope
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val ALBUM_SHEET_TAG = "lv8-album-sheet"
private const val ALBUM_DENSITY = 3f

/**
 * HOJA DE RENDER del álbum por ejercicio de la ronda LV8 (A4): «Álbum · Sentadilla trasera · 4 registros» con los filtros, los registros
 * agrupados por fecha («HOY · …» y otro día) con la serie, la carga, el esfuerzo, el tipo y la hora, la marca de récord y la duración de
 * los vídeos; con el aviso «Vídeo borrado · Deshacer» y con «Seleccionar». Dibuja la pieza REAL (`AlbumSheetContent`) a 360 dp, al 100 % y
 * al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveCardRenderSheetAlbumTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val ahora = System.currentTimeMillis()

    private fun medio(id: String, tipo: WorkoutMediaKind, hace: Long, serie: Int, marca: Boolean = false) = WorkoutMedia(
        id = id,
        kind = tipo,
        filePath = "/medios/$id",
        createdAtMs = ahora - hace,
        exerciseId = "sentadilla",
        canonicalExerciseId = "sentadilla",
        exerciseName = "Sentadilla trasera",
        setIndex = serie,
        weightKg = if (marca) 92.5 else 95.0,
        reps = if (marca) 5 else 6,
        isPr = marca,
        durationMs = if (tipo == WorkoutMediaKind.VIDEO) 14_000L else null,
    )

    private val registros = listOf(
        medio("v1", WorkoutMediaKind.VIDEO, hace = 60_000L, serie = 2),
        medio("f1", WorkoutMediaKind.PHOTO, hace = 900_000L, serie = 0),
        medio("v2", WorkoutMediaKind.VIDEO, hace = 3L * 24 * 3_600_000L, serie = 3, marca = true),
        medio("v3", WorkoutMediaKind.VIDEO, hace = 3L * 24 * 3_600_000L + 3_000_000L, serie = 1),
    )

    private fun esfuerzoDe(media: WorkoutMedia): String? = when (media.id) {
        "v1", "f1" -> "RIR 2"
        "v2" -> "RIR 1"
        else -> null
    }

    @Test fun albumDosFechas100() { onlyWhenAsked(); dibujar(fontScale = 1f, nombre = "album_dos_fechas_100") }
    @Test fun albumDosFechas130() { onlyWhenAsked(); dibujar(fontScale = 1.3f, nombre = "album_dos_fechas_130") }
    @Test fun albumConAvisoDeshacer100() { onlyWhenAsked(); dibujar(fontScale = 1f, nombre = "album_deshacer_100", borrarElUltimo = true) }
    @Test fun albumConAvisoDeshacer130() { onlyWhenAsked(); dibujar(fontScale = 1.3f, nombre = "album_deshacer_130", borrarElUltimo = true) }
    @Test fun albumSeleccionando100() { onlyWhenAsked(); dibujar(fontScale = 1f, nombre = "album_seleccionando_100", seleccionar = true) }
    @Test fun albumVacio100() { onlyWhenAsked(); dibujar(fontScale = 1f, nombre = "album_vacio_100", items = emptyList()) }

    private fun dibujar(
        fontScale: Float,
        nombre: String,
        items: List<WorkoutMedia> = registros,
        borrarElUltimo: Boolean = false,
        seleccionar: Boolean = false,
    ) {
        val reloj = TestScope()
        val aplazado = DeferredMediaDelete(reloj, commit = {})
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(ALBUM_DENSITY, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(780.dp)
                                .background(Color(0xFF0E0E0E))
                                .testTag(ALBUM_SHEET_TAG),
                        ) {
                            AlbumSheetContent(
                                subject = "Sentadilla trasera",
                                items = items,
                                loading = false,
                                effortFor = ::esfuerzoDe,
                                deferred = aplazado,
                                poseOverlayEnabled = false,
                                emptyText = "Todavía no hay fotos ni vídeos de este ejercicio.",
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        if (borrarElUltimo) {
            // El aviso «Vídeo borrado · Deshacer»: se borra el registro más antiguo y el reloj de prueba no corre, así que el aviso sigue.
            rule.runOnUiThread { aplazado.schedule(listOf(items.last())) }
            rule.waitForIdle()
        }
        if (seleccionar) {
            rule.onNodeWithTag(AlbumSelectTag).performClick()
            rule.waitForIdle()
            rule.onAllNodesWithTag(AlbumRowTag).onFirst().performClick()
            rule.waitForIdle()
        }
        guardar(ALBUM_SHEET_TAG, nombre)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
