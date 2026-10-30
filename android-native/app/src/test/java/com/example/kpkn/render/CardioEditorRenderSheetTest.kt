package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.cardio.CardioValueKind
import com.example.kpkn.screens.sessioneditor.components.CardioAvisosSheet
import com.example.kpkn.screens.sessioneditor.components.CardioCatalogSheet
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.CardioEmptyModule
import com.example.kpkn.screens.sessioneditor.components.CardioFichaContent
import com.example.kpkn.screens.sessioneditor.components.CardioProtocolSheet
import com.example.kpkn.screens.sessioneditor.components.CardioQuickActionsSheet
import com.example.kpkn.screens.sessioneditor.components.CardioSpaceLabel
import com.example.kpkn.screens.sessioneditor.components.CardioTemplatesSheet
import com.example.kpkn.screens.sessioneditor.components.CardioValueMode
import com.example.kpkn.screens.sessioneditor.components.CardioValueSheet
import com.example.kpkn.screens.sessioneditor.components.CardioZoneSheet
import com.example.kpkn.screens.sessioneditor.components.CeBlockChart
import com.example.kpkn.screens.sessioneditor.components.CeColors
import com.example.kpkn.screens.sessioneditor.components.CeIcon
import com.example.kpkn.screens.sessioneditor.components.CeIcons
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.screens.sessioneditor.components.cardioBars
import com.example.kpkn.screens.sessioneditor.components.cardioDoseLine
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.screens.sessioneditor.design.CardioThumb
import com.example.kpkn.screens.sessioneditor.design.CardioTypeArt
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
 * Pulido v1 · CARDIO DEL EDITOR: hoja de render de las 25 escenas con interfaz del paquete `ce` (la 26.ª, `ce-fichas-textos`, es un documento) con los datos de
 * muestra del lienzo. Cada escena se dibuja a 360 × 880 dp al 100 % y al 130 % de letra (`<id>_100.png`, `<id>_130.png`; `_740` en la clase de 360 × 740)
 * en la carpeta de `KPKN_RENDER_DIR`. Solo corre con esa variable.
 *
 * La isla superior, la barra inferior y las filas de FUERZA son del paquete EDITOR (tanda A2): aquí van en blanco, con el mismo hueco que en el lienzo, y
 * la fila de cada cardio (asa, miniatura, nombre, dosis, flecha) es un andamio mínimo de esta prueba. Lo que se compara con el lienzo es el espacio de cardio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
open class CardioEditorRenderSheetTest {
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

    // ───────────────────────────── datos de muestra del lienzo ─────────────────────────────

    private val history = CardioTypeHistory(
        lastDurationSeconds = 1080, lastDistanceKm = 3.1, lastPaceSecondsPerKm = 348, lastRpe = 7.0,
        bestPaceSecondsPerKm = 312, longestDistanceKm = 6.4, longestTimeSeconds = 2700,
    )
    private val runHistory = CardioTypeHistory(
        lastDurationSeconds = 1680, lastDistanceKm = 5.0, lastPaceSecondsPerKm = 336, lastRpe = 6.0,
        bestPaceSecondsPerKm = 298, longestDistanceKm = 10.0, longestTimeSeconds = 3480,
    )

    private fun steady(type: CardioType, seconds: Int? = 1200, rpe: Int = 8, km: Double? = null, pace: Int? = null, zone: Int? = null) = CardioDetails(
        type = type, targetDurationSeconds = seconds, intensityLevel = rpe, targetDistanceKm = km, targetPaceSecondsPerKm = pace,
        targetHrPercent = zone?.let(com.example.kpkn.domain.cardio.CardioZones::midpointPercent),
        requiresGps = type in setOf(CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR, CardioType.WALK),
    )

    private fun series(type: CardioType = CardioType.STAIR_CLIMBER, sets: Int = 1) = CardioRepeatGrammar.applyUniform(
        CardioDetails(type = type, intensityLevel = 9),
        CardioUniformRepeat(warmupSeconds = 300, workSeconds = 60, restSeconds = 60, rounds = 8, cooldownSeconds = 180, targetRpe = 9.0, sets = sets),
    )

    private fun block(id: String, type: CardioBlockType, seconds: Int, level: Int, speed: Double? = null, incline: Double? = null) =
        CardioIntervalBlock(id = id, type = type, durationSeconds = seconds, intensityLevel = level, speedKmh = speed, inclinePercent = incline)

    /** `CE_IRR`: 7 bloques de 20 min. */
    private fun irregular(type: CardioType) = CardioDetails(
        type = type,
        targetDurationSeconds = 1200,
        intervalBlocks = listOf(
            block("a", CardioBlockType.WARMUP, 180, 3, 6.0, 1.0), block("b", CardioBlockType.WORK, 120, 5, 10.0, 1.0),
            block("c", CardioBlockType.WORK, 180, 6, 10.0, 1.0), block("d", CardioBlockType.RECOVER, 60, 3, 5.5, 1.0),
            block("e", CardioBlockType.WORK, 240, 8, 10.0, 1.0), block("f", CardioBlockType.WORK, 240, 8, 10.0, 1.0),
            block("g", CardioBlockType.COOLDOWN, 180, 2, 5.0, 1.0),
        ),
    )

    private fun exercise(details: CardioDetails, name: String) = Exercise(id = "c-${details.type.name}", name = name, cardioDetails = details)

    // ───────────────────────────── andamios de la escena ─────────────────────────────

    /** Cardio abierto o cerrado en el módulo de la lista. [openIndex] abre la fila con su tarjeta. */
    @Composable
    private fun CardioModule(cards: List<Triple<String, CardioDetails, CardioTypeHistory?>>, openIndex: Int?, block: Int = 0) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .background(CeColors.module, shape)
                .border(1.dp, Color.White.copy(alpha = 0.043f), shape),
        ) {
            cards.forEachIndexed { i, (name, details, hist) ->
                val open = openIndex == i
                Column(Modifier.fillMaxWidth().background(if (open) CeColors.open else Color.Transparent)) {
                    Row(Modifier.fillMaxWidth().height(80.dp).padding(start = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { CeIcon(CeIcons.More, 20.dp, EdColors.dim) }
                        Row(Modifier.weight(1f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardioThumb(details.type, 56.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(name, style = edText(16f, FontWeight.SemiBold, EdColors.ink, 20f))
                                Text(cardioDoseLine(details), style = edText(13f, FontWeight.Normal, EdColors.muted, 16.9f, true))
                                if (!open && details.hasIntervals()) CeBlockChart(cardioBars(details), height = 22.dp, sparkline = true, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                        Box(Modifier.width(44.dp).height(48.dp), contentAlignment = Alignment.Center) { CeIcon(if (open) CeIcons.ChevUp else CeIcons.ChevDown, 20.dp, EdColors.faint) }
                    }
                    if (open) {
                        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)) {
                            var current by androidx.compose.runtime.remember { mutableStateOf(details) }
                            CardioEditorCard(details = current, onChange = { current = it }, history = hist, initialBlockIndex = block)
                        }
                    }
                }
            }
        }
    }

    /** La lista del editor: hueco de la isla y de FUERZA en blanco y el espacio de cardio a su altura del lienzo (rótulo a 369 dp, módulo a 423 dp). */
    @Composable
    private fun ListFrame(scroll: Int, both: Boolean = true, content: @Composable () -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .offset(y = ((if (both) 369 else 94) - scroll).dp)
                .padding(horizontal = 14.dp),
        ) { content() }
    }

    private fun scene(id: String, scroll: Int = 0, both: Boolean = true, manualClock: Boolean = false, sheet: (@Composable () -> Unit)? = null, list: @Composable () -> Unit) {
        // El cursor del selector parpadea sin fin (`blink`, 1 s): con el reloj automático la prueba no llega a estar en reposo.
        if (manualClock) rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                EditorOverlayTestHost {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ListFrame(scroll, both) { list() }
                        sheet?.invoke()
                    }
                }
            }
        }
        shoot(id)
    }

    private fun shoot(id: String) {
        for ((fontScale, tag) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { scale = fontScale }
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
            save("${id}_$tag$suffix")
        }
    }

    /** Pantalla completa sin lista (la ficha). */
    private fun page(id: String, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { EditorOverlayTestHost { content() } }
        }
        shoot(id)
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

    private fun three() = listOf(
        Triple("Cinta de correr", steady(CardioType.TREADMILL), history as CardioTypeHistory?),
        Triple("Escaladora", series(), null),
        Triple("Remo ergómetro", irregular(CardioType.ROW_MACHINE), null),
    )

    private val strengthSpace: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().height(1.dp)) }

    @Composable
    private fun Cardio(cards: List<Triple<String, CardioDetails, CardioTypeHistory?>>, open: Int?, both: Boolean = true, block: Int = 0) {
        CardioSpaceLabel(canMove = both, cardioFirst = false, onMove = {}, onAdd = {})
        CardioModule(cards, open, block)
    }

    // ───────────────────────────── las escenas ─────────────────────────────

    @Test fun ceAnadir() = scene("ce-anadir", sheet = { CardioCatalogSheet(onAdd = {}) }) { Cardio(three(), null) }

    @Test fun ceCambiar() = scene("ce-cambiar", sheet = { CardioCatalogSheet(isReplacing = true, currentType = CardioType.STAIR_CLIMBER, onAdd = {}) }) { Cardio(three(), null) }

    @Test fun ceIlustracionesRef() = page("ce-ilustraciones-ref") {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 56.dp, start = 12.dp, end = 12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // El orden de la galería del lienzo (`CE_T`): interior, al aire libre y sprint.
                listOf(
                    CardioType.TREADMILL, CardioType.ELLIPTICAL, CardioType.ROW_MACHINE, CardioType.BIKE_STATIONARY, CardioType.STAIR_CLIMBER,
                    CardioType.SKI_ERG, CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR, CardioType.WALK, CardioType.AIR_BIKE,
                    CardioType.CURVED_TREADMILL, CardioType.SLED,
                ).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t ->
                            Column(
                                Modifier.weight(1f).background(CeColors.module, RoundedCornerShape(18.dp)).padding(top = 12.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CardioTypeArt(t, 96.dp)
                                Text(com.example.kpkn.data.models.CardioCatalog.findByType(t)?.name.orEmpty(), style = edText(11f, FontWeight.Normal, EdColors.muted, 14f))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun ceLista() = scene("ce-lista") { Cardio(three(), null) }

    @Test fun ceSolo() = scene("ce-solo", both = false) {
        CardioSpaceLabel(canMove = false, cardioFirst = true, onMove = {}, onAdd = {})
        CardioEmptyModule(onAdd = {})
    }

    @Test fun ceContinuo() = scene("ce-continuo", scroll = 300) { Cardio(three(), 0) }

    @Test fun ceContinuoGps() = scene("ce-continuo-gps", scroll = 300) {
        Cardio(listOf(Triple("Carrera exterior", steady(CardioType.RUN_OUTDOOR, 1800, 6, 5.0, 330), runHistory as CardioTypeHistory?)), 0)
    }

    @Test fun ceSeries() = scene("ce-series", scroll = 300) {
        Cardio(listOf(Triple("Escaladora", series(), CardioTypeHistory(lastDurationSeconds = 1440, lastRpe = 9.0, bestPaceSecondsPerKm = null, longestTimeSeconds = 1800) as CardioTypeHistory?)), 0)
    }

    @Test fun ceIntervalos() = scene("ce-intervalos", scroll = 300) { Cardio(listOf(Triple("Cinta de correr", irregular(CardioType.TREADMILL), null)), 0, block = 1) }

    private fun valueScene(id: String, kind: CardioValueKind, n: Int, subtitle: String, mode: CardioValueMode = CardioValueMode.VALUE, typing: Boolean = false, buffer: String = "") =
        scene(id, manualClock = typing, sheet = {
            CardioValueSheet(kind = kind, subtitle = subtitle, initialN = n, onApply = { _, _ -> }, onDismiss = {}, initialMode = mode, startTyping = typing, startBuffer = buffer)
        }) { Cardio(three(), null) }

    @Test fun ceDuracion() = valueScene("ce-duracion", CardioValueKind.DURATION, 18, "Escaladora")

    @Test fun ceDuracionTeclado() = valueScene("ce-duracion-teclado", CardioValueKind.DURATION, 25, "Escaladora", typing = true, buffer = "25")

    @Test fun ceDuracionError() = valueScene("ce-duracion-error", CardioValueKind.DURATION, 18, "Escaladora", typing = true, buffer = "250")

    @Test fun ceEsfuerzo() = valueScene("ce-esfuerzo", CardioValueKind.WORK, 12, "Escaladora")

    @Test fun ceCalentamiento() = valueScene("ce-calentamiento", CardioValueKind.WARMUP, 20, "Escaladora")

    @Test fun ceCalentamientoLibre() = valueScene("ce-calentamiento-libre", CardioValueKind.WARMUP, 20, "Escaladora", mode = CardioValueMode.FREE)

    @Test fun ceDistancia() = valueScene("ce-distancia", CardioValueKind.DISTANCE, 50, "Carrera exterior")

    @Test fun ceZona() = scene("ce-zona", sheet = { CardioZoneSheet(typeName = "Cinta de correr", initialZone = 2, onApply = {}, onDismiss = {}) }) { Cardio(three(), null) }

    @Test fun ceProtocolo() = scene("ce-protocolo", sheet = {
        val d = series(sets = 2)
        CardioProtocolSheet(
            typeName = "Escaladora", dose = cardioDoseLine(d),
            repeat = (CardioRepeatGrammar.shape(d) as com.example.kpkn.domain.cardio.CardioAuthoringShape.Uniform).repeat,
            onChange = {}, onOpenValue = {}, onDismiss = {},
        )
    }) { Cardio(three(), null) }

    @Test fun ceAvisos() = scene("ce-avisos", sheet = { CardioAvisosSheet("Escaladora", series(), {}, {}) }) { Cardio(three(), null) }

    @Test fun ceAvisosContinuo() = scene("ce-avisos-continuo", sheet = { CardioAvisosSheet("Cinta de correr", steady(CardioType.TREADMILL), {}, {}) }) { Cardio(three(), null) }

    @Test fun cePlantillas() = scene("ce-plantillas", sheet = {
        CardioTemplatesSheet(CardioFeaturedTemplates.apply("hiit_one_on_one_off", steady(CardioType.TREADMILL)), {}, {}, {})
    }) { Cardio(three(), null) }

    @Test fun ceMenu() = scene("ce-menu", sheet = {
        CardioQuickActionsSheet(
            exercise = exercise(series(), "Escaladora"), canMovePlacement = true, cardioFirst = false,
            onOpenFicha = {}, onChange = {}, onDuplicate = {}, onTogglePlacement = {}, onDelete = {}, onDismiss = {},
        )
    }) { Cardio(three(), null) }

    @Test fun ceMenuEliminado() = scene("ce-menu-eliminado") { Cardio(three().filterIndexed { i, _ -> i != 1 }, null) }

    @Test fun ceFicha() = page("ce-ficha") {
        CardioFichaContent(CardioType.STAIR_CLIMBER, "18 min · 3,1 km · 5:48/km · RPE 7", onOpenProgress = {}, onDismiss = {})
    }

    @Test fun ceFichaExterior() = page("ce-ficha-exterior") {
        CardioFichaContent(CardioType.RUN_OUTDOOR, null, onOpenProgress = {}, onDismiss = {})
    }
}

/** La misma hoja a 360 × 740 dp (las escenas densas del lienzo `--size=360x740`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", application = android.app.Application::class)
class CardioEditorRenderSheet740Test : CardioEditorRenderSheetTest() {
    override val windowHeight = 740
    override val suffix = "_740"
}
