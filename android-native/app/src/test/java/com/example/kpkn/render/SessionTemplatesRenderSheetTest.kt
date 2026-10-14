package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionTemplatesSamples
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO de la pestaña Plantillas del asistente (SE4b). No comprueba nada: escribe PNG. Pinta la pestaña con dos plantillas propias
 * (y una archivada) y el catálogo; sin plantillas propias; con un filtro activo; una plantilla desplegada; el menú «⋯» abierto; y la hoja de
 * guardar. A 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionTemplatesRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escena que se dibuja ahora y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("pestana")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private class NoSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()

        override fun markSeen(tourId: String) = Unit
    }

    /**
     * La pestaña tal como la monta el asistente: el buscador y el navegador de plantillas. La escena «sinPropias» no tiene plantillas de
     * usuario; el resto, dos propias (con una archivada) y el catálogo.
     */
    @Composable
    private fun Escena() {
        val catalogoSolo = scene == "sinPropias"
        Column(
            modifier = Modifier
                .testTag("contenido")
                .width(360.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknTextField(
                value = "",
                onValueChange = {},
                placeholder = "Buscar plantilla…",
                leadingIcon = KpknLineIcons.Search,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TemplateCatalogBrowser(
                templates = if (catalogoSolo) {
                    SessionTemplatesSamples.catalogo
                } else {
                    SessionTemplatesSamples.propias + SessionTemplatesSamples.catalogo
                },
                searchQuery = "",
                onSelectTemplate = {},
                exerciseIndex = SessionTemplatesSamples.exerciseIndex,
                archivedUserTemplates = if (catalogoSolo) emptyList() else SessionTemplatesSamples.archivadas,
                sessionSummary = "6 ejercicios · 22 series",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /**
     * Deja llegar los cálculos de fondo (la fatiga de cada fila) y las animaciones: unos cuadros con un poco de tiempo real entre medias.
     */
    private fun settle(frames: Int = 6) {
        repeat(frames) {
            Thread.sleep(80)
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    /**
     * Pinta una escena a 100 % y 130 % de letra. [afterShow] prepara el estado (abrir un menú, desplegar una fila) solo en la pasada del
     * 100 %: al subir la letra la escena sigue igual y ese estado se conserva. [tag] es el recuadro que se guarda.
     */
    private fun renderStatic(sceneName: String, file: String, tag: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            if (suffix.isEmpty()) {
                afterShow()
            }
            settle()
            save(tag, "$file$suffix")
        }
    }

    @Test
    fun renderSessionTemplates() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            val host = remember { KpknOverlayHostController(tourSeen = NoSeen()) }
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides false,
            ) {
                Box(
                    modifier = Modifier
                        .testTag("lienzo")
                        .width(360.dp)
                        .height(820.dp)
                        .background(KpknSurface.canvas),
                ) {
                    key(scene) { Escena() }
                    KpknOverlayHostContent(host)
                }
            }
        }

        renderStatic("pestana", "se4b_pestana", tag = "contenido")
        renderStatic("sinPropias", "se4b_sin_propias", tag = "contenido")
        renderStatic("filtro", "se4b_filtro_activo", tag = "contenido", afterShow = {
            rule.onNodeWithText("Nivel").performClick()
            // Con el reloj parado, el menú del filtro llega a la raíz con los cuadros: dejamos pasar el tiempo antes de elegir.
            settle()
            rule.onNodeWithText("Exigente").performClick()
        })
        renderStatic("desplegada", "se4b_plantilla_desplegada", tag = "contenido", afterShow = {
            rule.onNodeWithText("Pierna pesada").performClick()
        })
        renderStatic("menu", "se4b_menu_mas", tag = "lienzo", afterShow = {
            rule.onNodeWithContentDescription("Más opciones de Empuje de hoy").performClick()
        })
        renderStatic("guardar", "se4b_guardar_plantilla", tag = "lienzo", afterShow = {
            rule.onNodeWithText("Guardar esta sesión").performClick()
            // La hoja sube con un muelle: con el reloj parado hay que dejarle tiempo a llegar a su sitio antes de capturar.
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
        })
    }
}
