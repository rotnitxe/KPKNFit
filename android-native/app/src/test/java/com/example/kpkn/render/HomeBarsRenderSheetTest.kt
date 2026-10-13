package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.NavLabel
import com.example.kpkn.R
import com.example.kpkn.navIconTint
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.BodyIcon
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.permissions.RecommendedPermissionsOverlay
import com.example.kpkn.ui.theme.KPKNTheme
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
 * HOJAS DE DIBUJO de la barra inferior y del diálogo «Activa tus avisos» (no comprueba nada: escribe PNG). Solo corre con la
 * variable de entorno `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. A 360 dp, con la letra al 100 % y al 130 %.
 *
 * La barra usa las piezas reales de MainActivity ([NavLabel] y [navIconTint]) dentro de un `NavigationBar` como el de la app,
 * sin el vidrio ni las rutas. El diálogo se pinta como su contenido (velo y panel), sin la ventana, que Robolectric no fotografía.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rES-w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class HomeBarsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno de gráficos nativos. */
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

    /** Una hoja: la barra con la pestaña [activa] elegida (0 Inicio, 1 Entreno, 2 Nutrición, 3 Cuerpo), o el diálogo con [missing]. */
    private data class Case(
        val name: String,
        val scale: Float = 1f,
        val activa: Int? = null,
        val missing: List<String> = emptyList(),
        val alto: Float = 760f,
    )

    private val notificaciones = "Notificaciones (Recordatorios y Alertas de Descanso)"
    private val microfono = "Micrófono (comandos por voz)"

    private var current by mutableStateOf(Case("none"))

    /** La vista de la actividad, para dibujarla a un bitmap (`captureToImage` espera un pase de dibujo que Robolectric no hace). */
    private var hostView: View? = null

    private fun setSheet() {
        rule.setContent {
            val case = current
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = case.scale),
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    val activa = case.activa
                    if (activa != null) BarraSheet(activa) else DialogoSheet(case)
                }
            }
        }
    }

    private fun render(case: Case) {
        current = case
        rule.waitForIdle()
        save(case.name)
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
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

    @Test
    fun renderBottomBar() {
        onlyWhenAsked()
        setSheet()
        listOf(
            Case("barra_inicio_s100", activa = 0),
            Case("barra_inicio_s130", scale = 1.3f, activa = 0),
            Case("barra_cuerpo_s100", activa = 3),
            Case("barra_cuerpo_s130", scale = 1.3f, activa = 3),
        ).forEach { render(it) }
    }

    @Test
    fun renderPermissionsDialog() {
        onlyWhenAsked()
        setSheet()
        listOf(
            Case("dialogo_1_permiso_s100", missing = listOf(notificaciones)),
            Case("dialogo_1_permiso_s130", scale = 1.3f, missing = listOf(notificaciones), alto = 900f),
            Case("dialogo_2_permisos_s100", missing = listOf(notificaciones, microfono)),
            Case("dialogo_2_permisos_s130", scale = 1.3f, missing = listOf(notificaciones, microfono), alto = 900f),
        ).forEach { render(it) }
    }

    /** La barra inferior: cuatro pestañas como en la app, sobre un recuadro de vidrio neutro tenue. */
    @Composable
    private fun BarraSheet(activa: Int) {
        Box(
            Modifier
                .testTag("sheet")
                .width(360.dp)
                .background(Color.Black)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().background(WizardColors.glassFill, RoundedCornerShape(32.dp))) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.height(80.dp),
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    NavigationBarItem(
                        selected = activa == 0,
                        onClick = {},
                        icon = { Icon(Icons.Default.Home, null, tint = navIconTint(activa == 0)) },
                        label = { NavLabel(stringResource(R.string.nav_home), activa == 0) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = activa == 1,
                        onClick = {},
                        icon = { DumbbellIcon(tint = navIconTint(activa == 1)) },
                        label = { NavLabel(stringResource(R.string.nav_training), activa == 1) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = activa == 2,
                        onClick = {},
                        icon = { NutritionIcon(tint = navIconTint(activa == 2)) },
                        label = { NavLabel(stringResource(R.string.nav_nutrition), activa == 2) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = activa == 3,
                        onClick = {},
                        icon = { BodyIcon(tint = navIconTint(activa == 3)) },
                        label = { NavLabel(stringResource(R.string.nav_body), activa == 3) },
                        colors = navItemColors,
                    )
                }
            }
        }
    }

    /** El diálogo: su velo y su panel dentro de un recuadro de 360 dp de ancho, sin la ventana. */
    @Composable
    private fun DialogoSheet(case: Case) {
        Box(
            Modifier
                .testTag("sheet")
                .size(360.dp, case.alto.dp)
                .background(Color.Black),
        ) {
            RecommendedPermissionsOverlay(
                missing = case.missing,
                blur = true,
                shown = { 1f },
                bellAngle = { 0f },
                onOpenSettings = {},
                onSkip = {},
            )
        }
    }
}
