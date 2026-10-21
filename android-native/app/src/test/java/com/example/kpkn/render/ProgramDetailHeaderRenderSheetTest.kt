package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.ProgramAboutModule
import com.example.kpkn.screens.programdetail.design.ProgramEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramEmptyStructure
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramMoreMenu
import com.example.kpkn.screens.programdetail.design.ProgramPinnedTabs
import com.example.kpkn.screens.programdetail.design.ProgramPinnedTopOffset
import com.example.kpkn.screens.programdetail.design.programDetailTabsOptions
import com.example.kpkn.screens.programdetail.design.programEditDraftOf
import com.example.kpkn.screens.programdetail.design.programHeaderDotColor
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknTopIsland
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
 * HOJA DE DIBUJO de la cabecera de Program Detail (P1): la cabecera activa, pausada y borrador, un programa simple, un nombre largo, las
 * pestañas en cada estado, la isla, la pestaña pegada, el menú y la hoja «Editar programa» con el estudio. No comprueba nada: escribe PNG.
 * Pinta a 360 dp, con la letra al 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ProgramDetailHeaderRenderSheetTest {

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

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "active", "paused", "draft", "simple", "longName",
        "tabsSemana", "tabsEstructura", "tabsVolumen",
        "pinned", "island", "more", "empty", "editSheet",
    )

    private val active = ProgramDetailSamples.active
    private val activeState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.ACTIVE, ProgramHeaderPrimary.Start("Torso A"), 12, 3, "Semana 3 de 12 · Acumulación",
    )
    private val pausedState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.PAUSED, ProgramHeaderPrimary.Activate, 12, 3, "Semana 3 de 12 · Acumulación",
    )
    private val draftState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.DRAFT, ProgramHeaderPrimary.Activate, 12, null, null,
    )
    private val simpleState = ProgramDetailSamples.headerState(
        ProgramDetailSamples.simple, ProgramHeaderStatus.ACTIVE, ProgramHeaderPrimary.Continue, 1, 1,
        "Semana que se repite · ciclo 5", repeating = true,
    )
    private val longState = ProgramDetailSamples.headerState(
        ProgramDetailSamples.longName, ProgramHeaderStatus.ACTIVE, ProgramHeaderPrimary.Start("Torso A"), 12, 3,
        "Semana 3 de 12 · Acumulación",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
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

    /** Alto de la hoja de cada caso: la hoja de «Editar programa» necesita el suyo para que su altura sea la de la pantalla. */
    private fun heightOf(name: String): Int = when (name) {
        "editSheet" -> 760
        "more" -> 600
        "pinned", "island" -> 240
        "empty" -> 260
        else -> 660
    }

    /** La cabecera con su acción principal, y con la pestaña [tab] si se enseña. */
    @Composable
    private fun HeaderCase(state: ProgramHeaderState, tab: Int? = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            ProgramHeader(state = state, actions = ProgramHeaderActions(onPrimary = {}, onStartPause = {}, onEdit = {}))
            Spacer(Modifier.height(16.dp))
            ProgramAboutModule(
                provenance = "Plan KPKN · Adaptación KPKN",
                notes = listOf("Dos series de calentamiento en cada básico.", "Descansos de dos a tres minutos."),
                onOpenPlanInfo = {},
                onOpenMore = {},
            )
            if (tab != null) {
                Spacer(Modifier.height(20.dp))
                KpknSegmentedControl(
                    options = programDetailTabsOptions,
                    selectedIndex = tab,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "active" -> HeaderCase(activeState)
            "paused" -> HeaderCase(pausedState)
            "draft" -> HeaderCase(draftState)
            "simple" -> HeaderCase(simpleState)
            "longName" -> HeaderCase(longState)
            "tabsSemana" -> HeaderCase(activeState, tab = 0)
            "tabsEstructura" -> HeaderCase(activeState, tab = 1)
            "tabsVolumen" -> HeaderCase(activeState, tab = 2)
            "pinned" -> Box(Modifier.fillMaxWidth().height(240.dp)) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 140.dp)) {
                    ProgramAboutModule(
                        provenance = "Plan KPKN · Adaptación KPKN",
                        notes = emptyList(),
                        onOpenPlanInfo = null,
                        onOpenMore = {},
                    )
                }
                ProgramPinnedTabs(
                    visible = true,
                    options = programDetailTabsOptions,
                    selectedIndex = 1,
                    onSelect = {},
                    topOffset = ProgramPinnedTopOffset,
                )
            }
            "island" -> Box(Modifier.fillMaxWidth().height(240.dp)) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 100.dp)) {
                    ProgramAboutModule(
                        provenance = "Plan KPKN · Adaptación KPKN",
                        notes = listOf("Dos series de calentamiento en cada básico."),
                        onOpenPlanInfo = null,
                        onOpenMore = {},
                    )
                }
                KpknTopIsland(
                    visible = true,
                    title = active.name,
                    leading = {
                        KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {})
                    },
                    actions = {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(programHeaderDotColor(ProgramHeaderStatus.ACTIVE)))
                        Spacer(Modifier.width(8.dp))
                        KpknIconButton(icon = KpknLineIcons.More, contentDescription = "Más opciones", onClick = {})
                    },
                )
            }
            "more" -> Box(Modifier.fillMaxWidth().height(600.dp)) {
                HeaderCase(activeState, tab = 0)
                ProgramMoreMenu(
                    items = listOf(
                        ProgramMenuItem(label = "Editar programa", onClick = {}),
                        ProgramMenuItem(label = "Historial", onClick = {}),
                    ),
                    onDismiss = {},
                )
            }
            "empty" -> Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ProgramEmptyStructure(onAddFirstBlock = {})
            }
            "editSheet" -> {
                val studio = active.copy(coverImage = "kpkn-studio://GRID/PAPEL")
                Box(Modifier.fillMaxWidth().height(760.dp)) {
                    ProgramEditSheet(
                        program = studio,
                        draft = programEditDraftOf(studio),
                        onDraftChange = {},
                        onSave = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun renderProgramDetailHeaderSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Sheet(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
