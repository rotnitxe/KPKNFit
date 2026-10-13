package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.screens.home.HomePageSample
import com.example.kpkn.screens.home.HomeProgramsSection
import com.example.kpkn.screens.home.HomeRingsSection
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.HomeSessionSection
import com.example.kpkn.screens.home.HomeWikiLabSection
import com.example.kpkn.screens.home.design.HomeBodyToday
import com.example.kpkn.screens.home.design.HomeMasthead
import com.example.kpkn.screens.home.design.HomeNotice
import com.example.kpkn.screens.home.design.HomeNoticeTone
import com.example.kpkn.screens.home.design.HomeNutritionToday
import com.example.kpkn.screens.home.design.HomeSectionGap
import com.example.kpkn.screens.home.design.HomeWeekStrip
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
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
 * HOJA DE DIBUJO de la página completa de Home (no comprueba nada: escribe PNG). Pinta todas las secciones apiladas, como la
 * `LazyColumn` de Home, en una sola imagen larga de 360 dp, con la letra al 100 % y al 130 %, y sin movimiento para ver el cuadro
 * final. Cuatro casos: entreno, descanso, sin programa y recién activado. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h6000dp-xxhdpi", application = android.app.Application::class)
class HomeFullRenderSheetTest {

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

    /** Los casos de la página, en orden: (nombre del PNG, datos). */
    private val cases: List<Pair<String, HomePageSample>> = HomeSamples.pageCases()

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para recortar la página a un bitmap (como en las demás hojas). */
    private var hostView: View? = null

    /** Guarda la caja «sheet» entera en un PNG: solo su tamaño, no la ventana completa. */
    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val left = bounds.left.toInt().coerceIn(0, view.width - 1)
        val top = bounds.top.toInt().coerceIn(0, view.height - 1)
        val width = bounds.width.toInt().coerceIn(1, view.width - left)
        val height = bounds.height.toInt().coerceIn(1, view.height - top)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.translate(-left.toFloat(), -top.toFloat())
            view.draw(canvas)
        }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * La página completa: las secciones de la `LazyColumn` de Home en su orden (cabecera, semana, sesión, RINGS, avisos, nutrición
     * y cuerpo, programas y «Aprende»), con el mismo aire entre ellas.
     */
    @Composable
    private fun Page(sample: HomePageSample) {
        val gap = HomeSectionGap.adapt(LocalViewportAdapt.current)
        val today = HomeSamples.today
        Column(Modifier.testTag("sheet").width(360.dp).background(WizardColors.background)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                HomeMasthead(state = sample.masthead, onOpenProfile = {})
                if (sample.hasActiveProgram && sample.days.isNotEmpty()) {
                    HomeWeekStrip(days = sample.days, selected = today, onSelect = {})
                }
                HomeSessionSection(
                    sessions = sample.sessions,
                    hasActiveProgram = sample.hasActiveProgram,
                    perMuscle = sample.perMuscle,
                    onStartWorkout = { _, _ -> },
                    onResumeWorkout = {},
                    onEditSession = { _, _ -> },
                    onCreateProgram = {},
                    selectedDate = today,
                    weekDays = sample.days,
                    activeProgram = sample.activeProgram,
                )
                HomeRingsSection(
                    muscularProgress = sample.rings.ringScore(RecoveryChannelId.MUSCULAR) / 100f,
                    sncProgress = sample.rings.ringScore(RecoveryChannelId.SYSTEM) / 100f,
                    columnaProgress = sample.rings.ringScore(RecoveryChannelId.STRUCTURE) / 100f,
                    hasActiveProgram = sample.hasActiveProgram,
                    dashboard = sample.rings.dashboard,
                    snapshot = sample.rings,
                    perMuscle = sample.perMuscle,
                )
                if (sample.overtrained.isNotEmpty()) {
                    HomeNotice(
                        title = "Posible exceso de volumen",
                        body = "Hay señales de carga alta en: ${sample.overtrained.joinToString(", ")}. " +
                            "Considera reducir series semanales o un descanso activo.",
                        actionLabel = "Ver recomendaciones",
                        onAction = {},
                        tone = HomeNoticeTone.DANGER,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    HomeNutritionToday(state = sample.nutrition, onAddMeal = {}, onOpenDay = {})
                    HomeBodyToday(state = sample.body, onOpen = {})
                }
                Column {
                    Spacer(Modifier.height(8.dp))
                    HomeProgramsSection(
                        programs = sample.programs,
                        activeProgramId = sample.activeProgram?.id,
                        onProgramClick = {},
                        onCreateProgram = {},
                        activeProgramLine = sample.masthead.program,
                        onSeeAll = {},
                    )
                }
                Column {
                    Spacer(Modifier.height(16.dp))
                    HomeWikiLabSection(onNavigate = {})
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Test
    fun renderHomeFullPage() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                cases.getOrNull(current)?.let { (_, sample) -> Page(sample) }
            }
        }
        for ((index, case) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("home_pagina_${case.first}_$suffix")
            }
        }
    }
}
