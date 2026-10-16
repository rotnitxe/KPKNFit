package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.screens.home.HomeRingsSection
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.RingDetailSheet
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.rings.LivingCharacterPose
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
 * HOJA DE DIBUJO de Home · RINGS (no comprueba nada: escribe PNG). Pinta la sección con datos, con «Estimación inicial» y sin datos,
 * y los fotogramas del personaje (en el aire y aterrizando) con órbitas, y el detalle de Músculos y el de Columna con su rango habitual,
 * a 360 dp, con la letra al 100 % y al 130 %, sin movimiento para ver el cuadro final. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class HomeRingsRenderSheetTest {

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

    /** El caso que se dibuja ahora y la escala de letra; fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableStateOf<RingsRenderCase?>(null)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap (como en la hoja de Home). */
    private var hostView: View? = null

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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
    fun renderHomeRingsSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                current?.let { it.content() }
            }
        }
        for ((name, case) in renderCases()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = case
                    fontScale = scale
                }
                rule.waitForIdle()
                save(case.crop, "${name}_$suffix")
            }
        }
    }
}

/** Un caso de la hoja: la etiqueta del nodo que se recorta y lo que se pinta. */
private class RingsRenderCase(val crop: String, val content: @Composable () -> Unit)

/** Los casos: la sección con datos, con «Estimación inicial», sin datos, poses de aire/aterrizaje, y el detalle de Músculos y Columna. */
private fun renderCases(): List<Pair<String, RingsRenderCase>> {
    val data = HomeSamples.ringsSample()
    val initial = HomeSamples.ringsSample(
        sources = mapOf(
            RecoveryChannelId.SYSTEM to RingsCoverageSource.INITIAL_ESTIMATE,
            RecoveryChannelId.STRUCTURE to RingsCoverageSource.SUBJECTIVE_SENSATION,
        ),
    )
    val noData = HomeSamples.ringsSample(structure = null)
    return listOf(
        "section_data" to RingsRenderCase(crop = "sheet") { RingsSectionCase(data) },
        "section_initial" to RingsRenderCase(crop = "sheet") { RingsSectionCase(initial) },
        "section_nodata" to RingsRenderCase(crop = "sheet") { RingsSectionCase(noData) },
        "section_air" to RingsRenderCase(crop = "sheet") { RingsSectionCase(data, forcedPose = LivingCharacterPose.AIR, forcedOrbitTimeMillis = 2500f, animating = true) },
        "section_landing" to RingsRenderCase(crop = "sheet") { RingsSectionCase(data, forcedPose = LivingCharacterPose.LANDING, forcedOrbitTimeMillis = 5000f, animating = true) },
        "detail_muscular" to RingsRenderCase(crop = "home-ring-sheet") { RingsDetailCase(RecoveryChannelId.MUSCULAR, data) },
        "detail_structure" to RingsRenderCase(crop = "home-ring-sheet") { RingsDetailCase(RecoveryChannelId.STRUCTURE, data) },
    )
}

/** La sección de RINGS sobre negro, a 360 dp, con la torre y sus tres columnas. */
@Composable
private fun RingsSectionCase(
    snapshot: AugeSnapshot,
    forcedPose: LivingCharacterPose? = null,
    forcedOrbitTimeMillis: Float? = null,
    animating: Boolean? = null,
) {
    Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black).padding(bottom = 24.dp)) {
        HomeRingsSection(
            muscularProgress = snapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f,
            sncProgress = snapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f,
            columnaProgress = snapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f,
            dashboard = snapshot.dashboard,
            snapshot = snapshot,
            forcedPose = forcedPose,
            forcedOrbitTimeMillis = forcedOrbitTimeMillis,
            animatingOverride = animating,
        )
    }
}

/** El detalle de un canal: la hoja de vidrio, anclada abajo de una caja alta para que quepa entera. */
@Composable
private fun RingsDetailCase(channel: RecoveryChannelId, snapshot: AugeSnapshot) {
    Box(Modifier.width(360.dp).height(1800.dp).background(Color.Black)) {
        RingDetailSheet(
            channel = channel,
            snapshot = snapshot,
            showModelUpdateNotice = false,
            onDismiss = {},
            onDismissAdvisory = {},
            onModelNoticeShown = {},
            perMuscle = snapshot.perMuscle,
        )
    }
}
