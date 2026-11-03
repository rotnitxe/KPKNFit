package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameWeekSheet
import com.example.kpkn.screens.sessioneditor.frame.frameWeekOrder
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.DragLiftPreview
import com.example.kpkn.screens.sessioneditor.DragSlotHole
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdExerciseRow
import com.example.kpkn.screens.sessioneditor.components.EdEyebrowStyle
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.screens.sessioneditor.components.edRowTitle
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.screens.sessioneditor.computeDragSlotRect
import com.example.kpkn.screens.sessioneditor.design.EdSwipeSurface
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import com.example.kpkn.screens.sessioneditor.design.edReveal
import com.example.kpkn.screens.sessioneditor.design.edPillWidth
import com.example.kpkn.screens.sessioneditor.design.edArmed
import com.example.kpkn.screens.sessioneditor.frame.EdListIcons
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameHeader
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameTopBar
import com.example.kpkn.screens.sessioneditor.frame.FrameAddActions
import com.example.kpkn.screens.sessioneditor.frame.sessionFrameSummaryChips
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.screens.sessioneditor.components.EdFooterLink
import com.example.kpkn.screens.sessioneditor.components.EdFooterPill
import com.example.kpkn.screens.sessioneditor.components.edBleed
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pulido v1 · EDITOR · tanda A2: hoja de render de las 12 escenas de la lista, las islas, la barra inferior y los gestos
 * (`ed-lista`, `ed-lista-scroll`, `ed-dia` (la hoja de la semana la dibuja la clase de la semana), `ed-pildora-mas`, `ed-anadir`, `ed-barra`,
 * `ed-borrar`, `-arrastre`, `-aviso`, `-umbral`, `ed-ordenar`, `-levantado`) con los datos de muestra del lienzo (`scenes-ed.js`).
 *
 * Cada escena se dibuja a 360 × 880 dp al 100 % y al 130 % de letra. Los PNG van a `KPKN_RENDER_DIR` como `<id>_100.png` y `<id>_130.png`
 * (`_740` en la clase de 360 × 740). Solo corre con esa variable. El vidrio real no se mide aquí (Robolectric no desenfoca): detrás de las islas hay
 * el velo de respaldo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
open class EditorListRenderSheetTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
    }

    @get:Rule
    val rule = createComposeRule()

    protected open val windowHeight = 880
    protected open val suffix = ""

    private var view: View? = null
    private var scale by mutableStateOf(1f)

    // ───────────────────────────── datos de muestra (`edData()`) ─────────────────────────────

    private data class Sample(val parent: String, val chips: List<String>, val dose: String, val star: Boolean = false, val clock: Boolean = false)

    private val samples = listOf(
        Sample("Peso Muerto Convencional", listOf("Barra"), "3 × 5 · RPE 8 · 3:00", star = true),
        Sample("Sentadilla \"Belt Squat\"", emptyList(), "3 × 5 · RPE 8 · 3:00"),
        Sample("Hip Thrust", listOf("Barra"), "3 × 5 · RPE 8 · 3:00"),
        Sample("Sentadilla en Máquina Pendular", emptyList(), "3 × 5 · RPE 8 · 3:00"),
        Sample("Patada de Glúteo", listOf("Polea", "Unilateral"), "3 × 5 · RPE 8 · 3:00"),
        Sample("Plancha Copenhague Dinámica", emptyList(), "3 × 0:40 · 1:30", clock = true),
    )

    private val cardioSamples = listOf(
        Sample("Cinta de correr", emptyList(), "10 min · RPE 8", clock = true),
        Sample("Escaladora", emptyList(), "8 × 0:30 / 0:30 · RPE 9", clock = true),
    )

    @Composable
    private fun Glyph(clock: Boolean) {
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (clock) KpknLineIcons.Clock else EdListIcons.Dumbbell,
                contentDescription = null,
                tint = EdColors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    @Composable
    private fun SampleRow(sample: Sample, modifier: Modifier = Modifier, lifted: Boolean = false) {
        EdExerciseRow(
            title = edRowTitle(sample.parent, sample.chips.filter { it != "Unilateral" }, sample.chips.contains("Unilateral")),
            dose = sample.dose,
            thumbnail = { Glyph(sample.clock) },
            expanded = false,
            onToggle = {},
            onLongPress = {},
            gripLifted = lifted,
            starActive = if (sample.clock && sample.parent.startsWith("Cinta") || sample.parent.startsWith("Escal")) null else sample.star,
            modifier = modifier,
        )
    }

    private fun edge(i: Int, n: Int) = when {
        n == 1 -> SessionModuleEdge.ONLY
        i == 0 -> SessionModuleEdge.TOP
        i == n - 1 -> SessionModuleEdge.BOTTOM
        else -> SessionModuleEdge.MIDDLE
    }

    // ───────────────────────────── la pantalla ─────────────────────────────

    /**
     * La pantalla del editor con sus piezas reales: isla superior (a 36 dp del borde como en el lienzo: 28 de barra de estado + 8), cabecera,
     * módulo de ejercicios, pies, y la barra inferior a 22 dp del borde. [rowContent] pinta cada fila de la lista (para congelar los gestos).
     */
    @Composable
    private fun EditorScreen(
        scrollDp: Int,
        minutes: Int = 81,
        limit: Int? = 90,
        chips: List<String> = sessionFrameSummaryChips(6, 18, 81),
        withCardio: Boolean = true,
        barDimmed: Boolean = false,
        menuOpen: Boolean = false,
        dividerVisible: Boolean = true,
        rowContent: @Composable (Int, Sample) -> Unit = { _, s -> SampleRow(s) },
        overlay: @Composable () -> Unit = {},
    ) {
        val density = LocalDensity.current
        val glassHaze = remember { HazeState() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val state = rememberScrollState(initial = with(density) { scrollDp.dp.roundToPx() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(state)
                    .padding(top = 104.dp, bottom = 128.dp),
            ) {
                SessionEditorFrameHeader(
                    sessionId = "legs",
                    name = "Legs II",
                    description = "",
                    protocolLabel = "Sesión",
                    summaryChips = chips,
                    onNameChange = {},
                    onDescriptionChange = {},
                    onDaySwipe = { false },
                )
                Column {
                    samples.forEachIndexed { i, sample ->
                        SessionModuleSlot(edge = edge(i, samples.size), dividerVisible = dividerVisible) { rowContent(i, sample) }
                    }
                }
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EdFooterLink(text = "Añadir ejercicio", onClick = {})
                    EdFooterPill(text = "Nuevo grupo", onClick = {})
                }
                if (withCardio) {
                    Text(
                        text = "CARDIO · AL FINAL",
                        modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp),
                        style = edText(11f, FontWeight.SemiBold, Color(0xFF8FB2FF)),
                    )
                    cardioSamples.forEachIndexed { i, sample ->
                        SessionModuleSlot(edge = edge(i, cardioSamples.size)) { SampleRow(sample) }
                    }
                }
            }
            Box(Modifier.padding(top = 28.dp)) {
                SessionEditorFrameTopBar(
                    dayLabel = "Jue · Semana 3",
                    forward = true,
                    canGoPrevious = true,
                    canGoNext = true,
                    onBack = {},
                    onPrevious = {},
                    onNext = {},
                    onOpenWeek = {},
                    onTransfer = {},
                    onHistory = {},
                    hazeState = glassHaze,
                )
            }
            SessionEditorFrameBottomBar(
                hazeState = glassHaze,
                suggestionCount = 3,
                estimatedMinutes = minutes,
                limitMinutes = limit,
                onAdd = {},
                onAssistant = {},
                onTime = {},
                dimmed = barDimmed,
                initialMenuOpen = menuOpen,
                addActions = FrameAddActions(onExercise = {}, onCardio = {}, onGroup = {}),
                hasStrengthSpace = true,
                hasCardioSpace = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 22.dp),
            )
            overlay()
        }
    }

    // ───────────────────────────── marco de la hoja ─────────────────────────────

    private fun scene(id: String, steps: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalWizardReducedMotion provides true,
            ) {
                EditorOverlayTestHost { content() }
            }
        }
        steps()
        for ((fontScale, tag) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { scale = fontScale }
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
            save("${id}_$tag$suffix")
        }
    }

    private fun save(name: String) {
        val host = view!!
        rule.runOnUiThread { host.invalidate(); host.requestLayout() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(Canvas(bitmap)) }
        val target = File(System.getenv("KPKN_RENDER_DIR")!!, "$name.png")
        target.parentFile!!.mkdirs()
        val viewport = Bitmap.createScaledBitmap(bitmap, 360, windowHeight, true)
        target.outputStream().use { viewport.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (viewport !== bitmap) viewport.recycle()
        bitmap.recycle()
    }

    // ───────────────────────────── las escenas ─────────────────────────────

    @Test fun edLista() = scene("ed-lista") { EditorScreen(scrollDp = 0) }

    @Test fun edListaScroll() = scene("ed-lista-scroll") { EditorScreen(scrollDp = 300) }

    /** `ed-dia`: la hoja de la semana sobre la lista (jueves con dos sesiones, la competición en sábado y las cuatro semanas del bloque). */
    @Test fun edDia() = scene("ed-dia") {
        EditorScreen(scrollDp = 0)
        SessionEditorFrameWeekSheet(
            orderedDays = frameWeekOrder(1),
            sessions = listOf(
                Session(id = "l", name = "Empuje", dayOfWeek = 1),
                Session(id = "x", name = "Tirón", dayOfWeek = 3),
                Session(id = "legs", name = "Legs II", dayOfWeek = 4, isMainSession = true),
                Session(id = "core", name = "Core y movilidad", dayOfWeek = 4),
                Session(id = "s", name = "Competición", dayOfWeek = 6),
            ),
            currentSessionId = "legs",
            currentDayOfWeek = 4,
            competitionKeyDaysInWeek = setOf(6),
            roadmapOptions = listOf("Adaptación", "Adaptación", "Acumulación", "Descarga").mapIndexed { i, name ->
                SessionRoadmapOption(0, 0, 0, i, "w${i + 1}", "Macro", "B1", name, 5)
            },
            currentWeekId = "w3",
            isSimpleProgram = true,
            hasActiveLoops = true,
            activeVariant = WeekVariant.A,
            availableVariants = listOf(WeekVariant.A),
            variantName = { "Original" },
            nextVariant = WeekVariant.B,
            defaultVariantName = "Legs II – Rápida",
            onSelectSession = {},
            onSetMainSession = {},
            onCreateSessionForDay = {},
            onSelectRoadmapOption = {},
            onSelectVariant = {},
            onCreateVariant = { _, _ -> },
            onDeleteActiveVariant = {},
            onDismiss = {},
        )
    }

    @Test fun edPildoraMas() = scene("ed-pildora-mas", steps = {
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.mainClock.advanceTimeBy(1_000)
    }) { EditorScreen(scrollDp = 330) }

    @Test fun edAnadir() = scene("ed-anadir") { EditorScreen(scrollDp = 0, menuOpen = true) }

    /** `ed-barra`: los cinco estados del tiempo sobre un fondo de color (para que se vea el vidrio). */
    @Test fun edBarra() = scene("ed-barra") {
        val glassHaze = remember { HazeState() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.padding(top = 91.dp))
                listOf(
                    Triple("SIN EJERCICIOS", null as Int?, null as Int?) to 0,
                    Triple("EN REPOSO · 3 SUGERENCIAS", 52, 90) to 3,
                    Triple("CERCA DEL LÍMITE (85 % O MÁS)", 80, 90) to 3,
                    Triple("LÍMITE SUPERADO (MÁS DEL 108 %)", 100, 90) to 3,
                    Triple("SIN LÍMITE DE DURACIÓN", 59, null) to 0,
                ).forEach { (spec, sugg) ->
                    val (label, total, limit) = spec
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = EdEyebrowStyle)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(listOf(Color(0x55F49A6E), Color(0x448FB2FF), Color(0x3343D18C))),
                                    RoundedCornerShape(24.dp),
                                )
                                .padding(vertical = 14.dp),
                        ) {
                            SessionEditorFrameBottomBar(
                                hazeState = glassHaze,
                                suggestionCount = sugg,
                                estimatedMinutes = if (label.startsWith("SIN EJERCICIOS")) 0 else total,
                                limitMinutes = limit,
                                onAdd = {},
                                onAssistant = {},
                                onTime = {},
                                modifier = Modifier.edBleed(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── borrar deslizando ─────────────────────────────

    @Test fun edBorrar() = scene("ed-borrar") { EditorScreen(scrollDp = 60) }

    private fun swipeFrozen(id: String, dx: Float) = scene(id) {
        val density = LocalDensity.current.density
        val reveal = edReveal(-dx)
        EditorScreen(scrollDp = 60, rowContent = { i, sample ->
            if (i != 0) {
                SampleRow(sample)
            } else {
                EdSwipeSurface(
                    rowOffsetPx = { -reveal * density },
                    armed = edArmed(reveal),
                    pillWidthPx = { edPillWidth(reveal) * density },
                    pillAlpha = { 1f },
                    popScale = { 1f },
                    noticeAlpha = { 0f },
                    notice = null,
                ) { SampleRow(sample) }
            }
        })
    }

    @Test fun edBorrarArrastre() = swipeFrozen("ed-borrar-arrastre", 60f)

    @Test fun edBorrarUmbral() = swipeFrozen("ed-borrar-umbral", 150f)

    @Test fun edBorrarAviso() = scene("ed-borrar-aviso") {
        val density = LocalDensity.current.density
        val twoSixty = sessionFrameSummaryChips(5, 15, 72)
        EditorScreen(scrollDp = 60, minutes = 72, chips = twoSixty, rowContent = { i, sample ->
            if (i != 2) {
                SampleRow(sample)
            } else {
                EdSwipeSurface(
                    rowOffsetPx = { -(328f + 8f) * density },
                    armed = true,
                    pillWidthPx = { 0f },
                    pillAlpha = { 0f },
                    popScale = { 1f },
                    noticeAlpha = { 1f },
                    notice = { SessionExerciseUndoNotice(onUndo = {}, frozenRemaining = 0.62f) },
                ) { SampleRow(sample) }
            }
        })
    }

    // ───────────────────────────── ordenar arrastrando ─────────────────────────────

    @Test fun edOrdenar() = scene("ed-ordenar") { EditorScreen(scrollDp = 60) }

    /** `freezeDrag`: se levanta la 2.ª fila y se baja un 66 % de su alto; la 3.ª sube a su sitio y el hueco queda debajo. */
    @Test fun edOrdenarLevantado() = scene("ed-ordenar-levantado") {
        val density = LocalDensity.current.density
        val rects = remember { mutableStateMapOf<Int, Rect>() }
        val windowPx = Rect(0f, 0f, 360f * density, 880f * density)
        val dragged = rects[1]
        val rowHeight = dragged?.height ?: (68f * density)
        val others = (0..5).filter { it != 1 }.mapNotNull { i -> rects[i]?.let { i to it } }
        val slot = if (dragged != null && others.size == 5) computeDragSlotRect(dragged, rowHeight, true, 1, 3, others) else null
        EditorScreen(
            scrollDp = 60,
            barDimmed = true,
            dividerVisible = false,
            rowContent = { i, sample ->
                Box(
                    Modifier
                        .onGloballyPositioned { rects[i] = it.boundsInWindow() }
                        .graphicsLayer {
                            alpha = if (i == 1) 0f else 1f
                            translationY = if (i == 2) -rowHeight else 0f
                        },
                ) { SampleRow(sample) }
            },
            overlay = {
                if (dragged != null) {
                    DragSlotHole(slot = slot, rootBounds = windowPx, modifier = Modifier.zIndex(499f))
                    DragLiftPreview(
                        exercise = Exercise(id = "e2", name = "Sentadilla \"Belt Squat\""),
                        rect = dragged,
                        offsetProvider = { Offset(0f, 0.66f * rowHeight) },
                        rootBounds = windowPx,
                        modifier = Modifier.zIndex(500f),
                        titleOverride = "Sentadilla \"Belt Squat\"",
                        subtitleOverride = "3 × 5 · RPE 8 · 3:00",
                        frozenScale = 1.025f,
                        frozenRing = 1f,
                    )
                }
            },
        )
    }
}

/** La misma hoja a 360 × 740 dp (las escenas se cortan antes: la barra y el contenido se acercan). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", application = android.app.Application::class)
class EditorListRenderSheet740Test : EditorListRenderSheetTest() {
    override val windowHeight = 740
    override val suffix = "_740"
}
