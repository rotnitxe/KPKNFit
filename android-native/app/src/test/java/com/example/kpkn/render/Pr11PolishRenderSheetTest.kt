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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.body.design.BodyChartPoint
import com.example.kpkn.screens.body.design.BodyTrendChart
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.programdetail.NativeProgressionCardUi
import com.example.kpkn.screens.programdetail.NativeProgressionItemUi
import com.example.kpkn.screens.programdetail.design.AutoregulationProposalsCard
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramNextSession
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCalibrationInvite
import com.example.kpkn.screens.programdetail.design.ProgramVolumeModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeScope
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
import java.time.LocalDate

/**
 * HOJA DE DIBUJO del pulido de la Pre Release 11 (Q3): Program Detail en un día sin sesión (con y sin «Siguiente»), el volumen sin
 * calibrar con los músculos sin series al final, Propuestas sin ningún TM y la gráfica de grasa con dos pesajes del mismo día. No
 * comprueba nada: escribe PNG. Pinta a 360 dp, con la letra al 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class Pr11PolishRenderSheetTest {

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
    private val cases = listOf("pdDescanso", "pdSinSiguiente", "volumenSinCalibrar", "propuestasSinTm", "grasaDosPesajes")

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

    /** Alto de la hoja de cada caso: lo bastante para que el contenido entero quepa al 130 %. */
    private fun heightOf(name: String): Int = when (name) {
        "pdDescanso", "pdSinSiguiente" -> 560
        "volumenSinCalibrar" -> 1700
        "propuestasSinTm" -> 460
        "grasaDosPesajes" -> 560
        else -> 800
    }

    // ─── Datos de ejemplo ─────────────────────────────────────────────────────

    private val program = Program(
        id = "base-solida",
        name = "Base sólida",
        description = "",
        mode = ProgramMode.HYPERTROPHY,
    )

    /** La cabecera de un día sin sesión: el programa activo, con o sin la siguiente sesión para la píldora secundaria. */
    private fun headerState(nextSession: ProgramNextSession?) = ProgramHeaderState(
        name = program.name,
        status = ProgramHeaderStatus.ACTIVE,
        meta = "Semana que se repite · Fuerza y músculo",
        identity = coverIdentityFor(program.id, null),
        photoUrl = null,
        bundled = null,
        weekCount = 1,
        currentWeekIndex = null,
        progressLabel = "Semana que se repite · ciclo 1",
        primary = ProgramHeaderPrimary.None,
        nextSession = nextSession,
    )

    private val nextThursday = ProgramNextSession(dayOfWeek = 4, label = "Siguiente: jueves · Pierna A")

    private val noopHeaderActions = ProgramHeaderActions(onPrimary = {}, onStartPause = {}, onEdit = {}, onOpenNextSession = {})

    /** Un volumen sin calibrar: ningún músculo tiene zonas, y tres no tienen series esta semana. */
    private val uncalibratedMuscles = listOf(
        ProgramVolumeMuscle("Glúteos", 20.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Deltoides", 19.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Cuádriceps", 19.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Dorsales", 14.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Isquiosurales", 12.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Pectorales", 8.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Trapecio", 7.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Cuello", 0.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Bíceps", 0.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
        ProgramVolumeMuscle("Tríceps", 0.0, null, null, overtrained = false, overreaching = false, scaleMax = 20.0),
    )

    private val volumeScope = ProgramVolumeScope(
        label = "S1 · Semana repetible",
        detail = "Conteo exacto de esa semana.",
        kinds = listOf("Semana"),
        selectedKind = 0,
        canPrevious = false,
        canNext = false,
    )

    /** Un programa sin perfil de TM: la fila de TM no debe pintarse, y la propuesta nativa sí. */
    private val noTmProgram = Program(id = "fuerza-libre", name = "Fuerza libre", description = "", mode = ProgramMode.HYPERTROPHY)

    /** Dos pesajes del mismo día (13 de octubre) y uno del 14: el eje debe decir «13 oct» y «14 oct», una sola vez cada uno. */
    private val fatPoints = listOf(
        BodyChartPoint(date = LocalDate.of(2026, 10, 13), value = 23.8, trend = 23.9),
        BodyChartPoint(date = LocalDate.of(2026, 10, 13), value = 24.0, trend = 23.9),
        BodyChartPoint(date = LocalDate.of(2026, 10, 14), value = 24.0, trend = 23.95),
    )

    /** Columna con el margen lateral de la pestaña (16 dp) y el aire entre módulos (12 dp), como en `VolumeView`. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "pdDescanso" -> Frame {
                ProgramHeader(state = headerState(nextThursday), actions = noopHeaderActions)
            }
            "pdSinSiguiente" -> Frame {
                ProgramHeader(state = headerState(null), actions = noopHeaderActions)
            }
            "volumenSinCalibrar" -> Frame {
                ProgramVolumeCalibrationInvite(
                    onCalibrate = {},
                    note = "Sin calibrar: las zonas aparecen al calibrar",
                )
                ProgramVolumeModule(
                    muscles = uncalibratedMuscles,
                    scope = volumeScope,
                    onSelectKind = {},
                    onPreviousScope = {},
                    onNextScope = {},
                    expandedMuscle = null,
                    onToggleMuscle = {},
                    breakdown = null,
                    onCalibrate = {},
                )
            }
            "propuestasSinTm" -> Frame {
                AutoregulationProposalsCard(
                    program = noTmProgram,
                    nativeCard = NativeProgressionCardUi(
                        proposals = listOf(
                            NativeProgressionItemUi(
                                id = "sentadilla",
                                title = "Sentadilla: +2,5 kg",
                                body = "Tres sesiones seguidas con la carga completa y una repetición de reserva.",
                            ),
                        ),
                    ),
                    onAccept = {},
                    onAcceptProposal = {},
                    onReject = {},
                    onAcceptNative = {},
                    onRejectNative = {},
                    onDismissNativeNotice = {},
                    onOpenTm = {},
                    onRematerialize = {},
                )
            }
            "grasaDosPesajes" -> Frame {
                BodyTrendChart(
                    points = fatPoints,
                    goal = null,
                    unit = "%",
                    name = "grasa corporal",
                )
            }
            else -> Unit
        }
    }

    @Test
    fun renderPr11PolishSheets() {
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
