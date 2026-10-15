package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.RepPhaseTimes
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.analysis.AnalysisStatusUi
import com.example.kpkn.screens.technique.analysis.PlateCalibrationDialog
import com.example.kpkn.screens.technique.analysis.PlateModuleUi
import com.example.kpkn.screens.technique.analysis.RepMetricKind
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisActions
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisScreen
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisUiState
import com.example.kpkn.screens.technique.analysis.buildBreakdown
import com.example.kpkn.screens.technique.analysis.buildObservations
import com.example.kpkn.screens.technique.analysis.buildPlate
import com.example.kpkn.screens.technique.analysis.buildSummary
import com.example.kpkn.screens.technique.analysis.buildTips
import com.example.kpkn.screens.technique.analysis.design.AnalysisStatusModule
import com.example.kpkn.screens.technique.analysis.design.AnalysisSummaryModule
import com.example.kpkn.screens.technique.analysis.design.CameraTipsModule
import com.example.kpkn.screens.technique.analysis.design.ObservationsModule
import com.example.kpkn.screens.technique.analysis.design.PlateCalibrationModule
import com.example.kpkn.screens.technique.analysis.design.RepBreakdownModule
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.design.TechniqueOverlay
import com.example.kpkn.screens.technique.player.design.fitVideoRect
import com.example.kpkn.ui.design.KpknSurface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de la pantalla «Análisis de técnica» (VT7b; no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin
 * ella se salta. 360 dp de ancho a densidad 3, con las muestras de `screens/technique/TechniqueSamples.kt`, al 100 % y al 130 % de
 * letra y con el movimiento reducido (las cifras salen ya contadas).
 *
 * - **Pantalla lista:** la serie de diez sentadillas con todos los módulos, en trozos de 900 dp de alto (`lista_*`).
 * - **Estados:** analizando al 42 %, en cola, actualizando, «¿Qué movimiento es?», sin soporte, falló, sin analizar y sin informe.
 * - **Disco:** los tres estados del módulo, con sus avisos, y el diálogo para marcarlo.
 * - **Honestidad:** el resumen con m/s, sin calibrar y con la pérdida «sin dato fiable»; confianza MEDIA con sus consejos.
 * - **Gráfica:** las tres medidas, sin repetición elegida y con una serie larga.
 *
 * El reproductor de verdad no se dibuja: en su lugar, un fotograma gris con la capa de [TechniqueOverlay] encima.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class TechniqueAnalysisRenderSheetTest {

    companion object {
        /** Marca de la hoja que se recorta y se guarda. */
        private const val HOJA = "hoja"

        /** Alto de cada trozo en que se corta una hoja larga (dp). */
        private const val TROZO_DP = 900f

        /** Lo que se repite del trozo anterior al principio de cada trozo (dp): así un módulo partido por el corte se lee entero en uno. */
        private const val SOLAPE_DP = 48f

        /** Si tras un trozo queda menos que esto (dp), el trozo se alarga hasta el final en vez de dejar una cola de unas pocas líneas. */
        private const val COLA_MINIMA_DP = 150f

        /** Lo que se deja de negro (dp) tras lo último que se dibuja, al cortar el negro vacío del final de una hoja corta. */
        private const val MARGEN_FINAL_DP = 24f

        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Vista de la prueba: se dibuja a un bitmap, como en las demás hojas de render. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** El alto de [bitmap] hasta su última fila que no es negro puro, más [MARGEN_FINAL_DP]: no se guardan metros de negro vacío. */
    private fun altoUtil(bitmap: Bitmap, densidad: Float): Int {
        val fila = IntArray(bitmap.width)
        for (y in bitmap.height - 1 downTo 0) {
            bitmap.getPixels(fila, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (fila.any { it != android.graphics.Color.BLACK }) return minOf(bitmap.height, y + 1 + (MARGEN_FINAL_DP * densidad).toInt())
        }
        return bitmap.height
    }

    /**
     * Recorta el nodo con la marca [tag] de la vista completa y lo guarda en `KPKN_RENDER_DIR/<nombre>.png`. Con [trozos] lo corta, sin
     * el negro vacío del final, en franjas de [TROZO_DP] de alto que se solapan [SOLAPE_DP] (`<nombre>_1.png`, `<nombre>_2.png`…),
     * entre [desdeTrozo] y [hastaTrozo] (desde 1).
     */
    private fun guardar(nombre: String, tag: String = HOJA, trozos: Boolean = false, desdeTrozo: Int = 1, hastaTrozo: Int = Int.MAX_VALUE) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        val completa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val lienzo = android.graphics.Canvas(completa)
            lienzo.drawColor(android.graphics.Color.BLACK)
            vista.draw(lienzo)
        }
        val left = bounds.left.toInt().coerceIn(0, completa.width - 1)
        val top = bounds.top.toInt().coerceIn(0, completa.height - 1)
        val width = bounds.width.toInt().coerceIn(1, completa.width - left)
        val height = bounds.height.toInt().coerceIn(1, completa.height - top)
        val recorte = Bitmap.createBitmap(completa, left, top, width, height)
        if (!trozos) {
            FileOutputStream(File(dir, "$nombre.png")).use { recorte.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val densidad = vista.resources.displayMetrics.density
        val alto = (TROZO_DP * densidad).toInt()
        val solape = (SOLAPE_DP * densidad).toInt()
        val cola = (COLA_MINIMA_DP * densidad).toInt()
        val total = altoUtil(recorte, densidad)
        var y = 0
        var k = 1
        while (y < total) {
            val resto = total - y
            val h = if (resto <= alto + cola) resto else alto
            if (k in desdeTrozo..hastaTrozo) {
                val trozo = Bitmap.createBitmap(recorte, 0, y, recorte.width, h)
                FileOutputStream(File(dir, "${nombre}_$k.png")).use { trozo.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (h == resto) break
            y += alto - solape
            k++
        }
    }

    /** Monta [contenido] sobre el negro de la pantalla, con la escala de letra [escala] y el movimiento reducido. */
    private fun montar(escala: Float, contenido: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, escala),
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                    contenido()
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500L)
    }

    /** Una pila de módulos con los márgenes de la pantalla (16 dp a los lados, 12 entre ellos). */
    private fun pila(nombre: String, escala: Float, contenido: @Composable () -> Unit) {
        onlyWhenAsked()
        montar(escala) {
            Column(
                modifier = Modifier.testTag(HOJA).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { contenido() }
        }
        guardar(nombre)
    }

    // ── La pantalla entera ────────────────────────────────────────────────────

    private val posicion get() = TechniqueSamples.analysisPositionUs

    /** El disco confirmado de la serie de ejemplo, listo para el módulo del disco. */
    private val discoConfirmado get() = TechniqueSamples.analysisSeed.copy(userConfirmed = true)

    /** La serie de ejemplo con todos los módulos: disco confirmado y un aviso de la escala (con su consejo). */
    private fun estadoLista(): TechniqueAnalysisUiState = TechniqueSamples.analysisUiState(
        report = TechniqueSamples.analysisReport.copy(flags = setOf(AnalysisFlag.PLATE_ELLIPSE)),
        bar = TechniqueSamples.analysisPlate.copy(seed = discoConfirmado, plateDiameterMm = 450),
        overrides = CaptureOverrides(plateSeed = discoConfirmado, plateDiameterMm = 450),
        usesPlate = true,
    )

    /** La pantalla con [estado]: el vídeo de mentira, el estado del reproductor con la 4.ª repetición elegida y el desplazamiento [scroll]. */
    @Composable
    private fun Pantalla(estado: TechniqueAnalysisUiState, scroll: ScrollState, fotograma: ImageBitmap? = null) {
        val overlay = estado.overlay
        val player = remember { TechniquePlayerState(initialPositionUs = posicion, initialRep = 3) }
        SideEffect {
            player.updateOverlay(overlay)
            player.reportDurationMs(TechniqueSamples.analysisDurationUs / 1000L)
        }
        Box(Modifier.testTag(HOJA).fillMaxSize()) {
            TechniqueAnalysisScreen(
                state = estado,
                player = player,
                plateFrame = fotograma,
                actions = TechniqueAnalysisActions.None,
                videoSlot = { modificador -> VideoDeMentira(modificador, overlay, player) },
                scroll = scroll,
                viewportHeight = 800.dp,
                magnifierEnabled = false,
            )
        }
    }

    /** Un fotograma gris con las bandas negras del FIT y, encima, la capa de técnica. */
    @Composable
    private fun VideoDeMentira(modificador: Modifier, overlay: TechniqueOverlayData?, player: TechniquePlayerState) {
        val aspecto = overlay?.videoAspect?.takeIf { it > 0f } ?: (9f / 16f)
        Box(modificador.background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = fitVideoRect(size.width, size.height, aspecto)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF6B7075), Color(0xFF30353A)), startY = r.top, endY = r.bottom),
                    topLeft = r.topLeft,
                    size = r.size,
                )
            }
            if (overlay != null) {
                TechniqueOverlay(overlay = overlay, positionUs = { player.positionUs }, modifier = Modifier.fillMaxSize(), videoAspect = aspecto)
            }
        }
    }

    private fun pantalla(
        nombre: String,
        escala: Float,
        estado: TechniqueAnalysisUiState,
        desplazamientosDp: List<Int>,
        fotograma: ImageBitmap? = null,
    ) {
        onlyWhenAsked()
        val scroll = ScrollState(0)
        montar(escala) { Pantalla(estado, scroll, fotograma) }
        val densidad = 3f
        for ((i, desplazamiento) in desplazamientosDp.withIndex()) {
            rule.runOnUiThread { runBlocking { scroll.scrollTo((desplazamiento * densidad).toInt()) } }
            rule.waitForIdle()
            guardar("${nombre}_${('a' + i)}", trozos = true)
        }
    }

    @Test
    fun pantallaLista100() = pantalla("lista_100", 1f, estadoLista(), listOf(0, 1500))

    @Test
    fun pantallaLista130() = pantalla("lista_130", 1.3f, estadoLista(), listOf(0, 1700))

    @Test
    fun pantallaAnalizando100() =
        pantalla("analizando_100", 1f, TechniqueSamples.statusUiState(AnalysisStatusUi.Analyzing(42, updating = false, queued = false)), listOf(0))

    @Test
    fun pantallaEligeElMovimiento100() =
        pantalla("elige_movimiento_100", 1f, TechniqueSamples.statusUiState(AnalysisStatusUi.NeedsProfile(AnalysisProfileId.ROW)), listOf(0))

    // ── Estados ───────────────────────────────────────────────────────────────

    @Composable
    private fun Estados(grupo: Int) {
        if (grupo == 1) {
            AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 42, updating = false, queued = false))
            AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 0, updating = false, queued = true))
            AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 70, updating = true, queued = false))
            AnalysisStatusModule(AnalysisStatusUi.Unsupported("Los levantamientos olímpicos aún no se analizan"))
            AnalysisStatusModule(AnalysisStatusUi.Failed("El móvil se quedó sin memoria al analizarlo."))
            AnalysisStatusModule(AnalysisStatusUi.NotAnalyzed)
            AnalysisStatusModule(AnalysisStatusUi.ReportMissing)
        } else {
            AnalysisStatusModule(
                status = AnalysisStatusUi.NeedsProfile(AnalysisProfileId.ROW),
                selectedProfile = AnalysisProfileId.ROW,
                profilesExpanded = false,
            )
            AnalysisStatusModule(
                status = AnalysisStatusUi.NeedsProfile(null),
                selectedProfile = null,
                profilesExpanded = false,
            )
        }
    }

    @Test
    fun estadosDelAnalisis100() = pila("estados_a_100", 1f) { Estados(1) }

    @Test
    fun estadosDelAnalisis130() = pila("estados_a_130", 1.3f) { Estados(1) }

    @Test
    fun elegirElMovimiento100() = pila("estados_b_100", 1f) { Estados(2) }

    @Test
    fun elegirElMovimiento130() = pila("estados_b_130", 1.3f) { Estados(2) }

    // ── El disco ──────────────────────────────────────────────────────────────

    private fun modulosDelDisco(): Triple<PlateModuleUi, PlateModuleUi, PlateModuleUi> {
        val informe = TechniqueSamples.analysisReport.copy(
            flags = setOf(
                AnalysisFlag.PLATE_ELLIPSE,
                AnalysisFlag.PARALLAX_SUPPRESSED,
            ),
        )
        val marcas = TechniqueSamples.analysisOverlay.reps
        val semilla = TechniqueSamples.analysisSeed
        fun modulo(barra: BarTrack?, ajustes: CaptureOverrides = CaptureOverrides()) =
            buildPlate(informe, marcas, barra, ajustes, true, 450, TechniqueSamples.VIDEO_WIDTH, TechniqueSamples.VIDEO_HEIGHT)!!
        return Triple(
            modulo(TechniqueSamples.analysisPlate.copy(seed = semilla)),
            modulo(TechniqueSamples.analysisPlate, CaptureOverrides(plateSeed = semilla.copy(userConfirmed = true), plateDiameterMm = 450)),
            modulo(null),
        )
    }

    /** Grupo 0: el disco hallado sin confirmar (con el diámetro de partida y con uno propio); 1: confirmado (con y sin m/s) y sin disco. */
    @Composable
    private fun Discos(grupo: Int) {
        val (sugerido, confirmado, sinDisco) = modulosDelDisco()
        val fotograma = TechniqueSamples.plateFrameImage(TechniqueSamples.analysisSeed)
        if (grupo == 0) {
            PlateCalibrationModule(sugerido, fotograma, 450, {}, {}, {}, {})
            PlateCalibrationModule(sugerido.copy(diameterMm = 425), fotograma, 425, {}, {}, {}, {})
        } else {
            PlateCalibrationModule(confirmado, null, 450, {}, {}, {}, {})
            PlateCalibrationModule(confirmado.copy(inMetersPerSecond = false), null, 450, {}, {}, {}, {})
            PlateCalibrationModule(sinDisco, null, 450, {}, {}, {}, {})
        }
    }

    @Test
    fun moduloDelDisco100() = pila("disco_100_a", 1f) { Discos(0) }

    @Test
    fun moduloDelDiscoConfirmado100() = pila("disco_100_b", 1f) { Discos(1) }

    @Test
    fun moduloDelDisco130() = pila("disco_130_a", 1.3f) { Discos(0) }

    @Test
    fun moduloDelDiscoConfirmado130() = pila("disco_130_b", 1.3f) { Discos(1) }

    @Test
    fun dialogoDelDisco100() {
        onlyWhenAsked()
        val (sugerido, _, _) = modulosDelDisco()
        montar(1f) {
            Box(Modifier.testTag(HOJA).size(360.dp, 800.dp)) {
                PlateCalibrationDialog(
                    plate = sugerido,
                    frame = TechniqueSamples.plateFrameImage(TechniqueSamples.analysisSeed),
                    initialDiameterMm = 450,
                    onSave = { _, _ -> },
                    onDismiss = {},
                    magnifierEnabled = false,
                )
            }
        }
        guardar("dialogo_disco_100")
    }

    @Test
    fun dialogoDelDisco130() {
        onlyWhenAsked()
        val (sugerido, _, _) = modulosDelDisco()
        montar(1.3f) {
            Box(Modifier.testTag(HOJA).size(360.dp, 800.dp)) {
                PlateCalibrationDialog(
                    plate = sugerido.copy(diameterMm = 425),
                    frame = TechniqueSamples.plateFrameImage(TechniqueSamples.analysisSeed),
                    initialDiameterMm = 425,
                    onSave = { _, _ -> },
                    onDismiss = {},
                    magnifierEnabled = false,
                )
            }
        }
        guardar("dialogo_disco_130")
    }

    // ── Honestidad ────────────────────────────────────────────────────────────

    /** Grupo 0: los tres (100 %); 1: con m/s y sin dato fiable; 2: sin calibrar. */
    @Composable
    private fun Resumenes(grupo: Int) {
        // Con el disco calibrado: m/s con «≈».
        if (grupo != 2) AnalysisSummaryModule(buildSummary(TechniqueSamples.analysisReport))
        // Sin calibrar: velocidad solo relativa, sin m/s; el desvío en % del recorrido con «≈».
        if (grupo != 1) AnalysisSummaryModule(buildSummary(TechniqueSamples.analysisReportRelative))
        // Barras relativas pero sin pérdida medible: «sin dato fiable», nunca un 0 %.
        if (grupo != 2) AnalysisSummaryModule(buildSummary(TechniqueSamples.analysisReportLossUnknown))
    }

    @Test
    fun resumenes100() = pila("resumenes_100", 1f) { Resumenes(0) }

    @Test
    fun resumenes130() = pila("resumenes_130_a", 1.3f) { Resumenes(1) }

    @Test
    fun resumenesSinCalibrar130() = pila("resumenes_130_b", 1.3f) { Resumenes(2) }

    /** Grupo 0: todo (100 %); 1: el resumen; 2: la gráfica (velocidad relativa) y los consejos. */
    @Composable
    private fun ConfianzaMedia(grupo: Int) {
        val informe = TechniqueSamples.analysisReportMedium
        val marcas = TechniqueSamples.analysisOverlay.reps
        if (grupo != 2) AnalysisSummaryModule(buildSummary(informe))
        if (grupo != 1) {
            buildBreakdown(informe, marcas)?.let { RepBreakdownModule(it, RepMetricKind.VELOCITY, {}, 3, {}) }
            CameraTipsModule(buildTips(informe))
        }
    }

    @Test
    fun confianzaMedia100() = pila("confianza_media_100", 1f) { ConfianzaMedia(0) }

    @Test
    fun confianzaMedia130() = pila("confianza_media_130_a", 1.3f) { ConfianzaMedia(1) }

    @Test
    fun confianzaMediaGrafica130() = pila("confianza_media_130_b", 1.3f) { ConfianzaMedia(2) }

    // ── Gráfica y observaciones ───────────────────────────────────────────────

    /** La serie de ejemplo con seis repeticiones más (16 en total), para ver la gráfica apretada. */
    private fun informeLargo(): SetTechniqueReport {
        val base = TechniqueSamples.analysisReport
        val desplazamiento = 60_000_000L
        val extra = base.reps.take(6).mapIndexed { k, r ->
            r.copy(
                index = base.reps.size + k,
                phases = RepPhaseTimes(
                    r.phases.departUs + desplazamiento, r.phases.turnStartUs + desplazamiento,
                    r.phases.turnEndUs + desplazamiento, r.phases.arriveUs + desplazamiento,
                ),
            )
        }
        return base.copy(reps = base.reps + extra, repCount = base.repCount + extra.count { it.kind == RepKind.FULL })
    }

    /** Grupo 0: las tres medidas con una repetición elegida; 1: sin ninguna elegida y la serie larga con una repetición sin dato elegida. */
    @Composable
    private fun Graficas(grupo: Int) {
        val informe = TechniqueSamples.analysisReport
        val desglose = buildBreakdown(informe, TechniqueSamples.analysisOverlay.reps)!!
        if (grupo == 0) {
            RepBreakdownModule(desglose, RepMetricKind.VELOCITY, {}, 3, {})
            RepBreakdownModule(desglose, RepMetricKind.ROM, {}, 8, {})
            RepBreakdownModule(desglose, RepMetricKind.TIME, {}, 9, {})
        } else {
            // Sin ninguna elegida.
            RepBreakdownModule(desglose, RepMetricKind.VELOCITY, {}, -1, {})
            // Una serie larga y una repetición sin dato elegida (la parcial).
            val largo = informeLargo()
            val marcasLargas = TechniqueOverlayData.from(null, null, largo, 720, 1280).reps
            RepBreakdownModule(buildBreakdown(largo, marcasLargas)!!, RepMetricKind.VELOCITY, {}, 2, {})
        }
    }

    @Test
    fun graficas100() = pila("graficas_100_a", 1f) { Graficas(0) }

    @Test
    fun graficasSinElegir100() = pila("graficas_100_b", 1f) { Graficas(1) }

    @Test
    fun graficas130() = pila("graficas_130_a", 1.3f) { Graficas(0) }

    @Test
    fun graficasSinElegir130() = pila("graficas_130_b", 1.3f) { Graficas(1) }

    @Composable
    private fun Observaciones() {
        ObservationsModule(buildObservations(TechniqueSamples.analysisReport, TechniqueSamples.analysisOverlay.reps), {})
        CameraTipsModule(buildTips(TechniqueSamples.analysisReportMedium))
    }

    @Test
    fun observaciones100() = pila("observaciones_100", 1f) { Observaciones() }

    @Test
    fun observaciones130() = pila("observaciones_130", 1.3f) { Observaciones() }
}
