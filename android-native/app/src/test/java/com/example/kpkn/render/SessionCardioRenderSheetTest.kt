package com.example.kpkn.render

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.CardioIntWheelDialog
import com.example.kpkn.screens.sessioneditor.components.CardioIntervalsEditor
import com.example.kpkn.screens.sessioneditor.components.CardioValuePill
import com.example.kpkn.screens.sessioneditor.design.SessionCardioModule
import com.example.kpkn.screens.sessioneditor.design.SessionPreparationModule
import com.example.kpkn.screens.sessioneditor.design.SessionPreparationRow
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del cardio y la preparación del editor de sesiones (SE5). No comprueba nada: escribe PNG en `KPKN_RENDER_DIR` a 360 dp,
 * con la letra al 100 % y al 130 %. Sin la variable, la clase se salta. Cada escala es una prueba propia (Compose solo admite un
 * `setContent` por prueba). Los datos de ejemplo están al final, en [SessionCardioSamples].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
class SessionCardioRenderSheetTest {

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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Pinta [content] a 360 dp de ancho, con la escala de letra [fontScale], y guarda la hoja como [name].png. */
    private fun shoot(name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(KpknSurface.canvas)
                        .padding(16.dp)
                        .testTag("hoja"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("hoja").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    // ── Escenas: cada una es una hoja. El 100 % y el 130 % son pruebas aparte con la misma escena. ──

    @Composable
    private fun PreparacionSheet() {
        SessionPreparationModule(rows = SessionCardioSamples.preparacion, onEdit = {})
    }

    @Composable
    private fun CardioPlegadoSheet() {
        SessionCardioSamples.plegados.forEach { sample ->
            KpknModule(contentPadding = PaddingValues(vertical = 4.dp)) {
                SessionCardioModule(
                    name = sample.name,
                    details = sample.details,
                    expanded = false,
                    onToggle = {},
                    onLongPress = {},
                    onDragStart = {},
                    onDrag = {},
                    onDragEnd = {},
                    onDragCancel = {},
                    onDelete = {},
                )
            }
        }
    }

    @Composable
    private fun ContinuoDesplegadoSheet() {
        KpknModule {
            CardioEditorCard(details = SessionCardioSamples.continuo, accentColor = SymbolPalette.energia, onChange = {})
        }
    }

    @Composable
    private fun HiitDesplegadoSheet() {
        KpknModule {
            CardioEditorCard(details = SessionCardioSamples.hiit, accentColor = SymbolPalette.energia, onChange = {})
        }
    }

    @Composable
    private fun IntervalosSheet() {
        KpknModule {
            SessionCardioModule(
                name = "Bicicleta estática",
                details = SessionCardioSamples.intervalos,
                expanded = true,
                onToggle = {},
                onLongPress = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
                onDelete = {},
            )
            Spacer(Modifier.height(12.dp))
            CardioIntervalsEditor(details = SessionCardioSamples.intervalos, accentColor = SymbolPalette.energia, onChange = {})
        }
    }

    @Composable
    private fun RuedasSheet() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardioValuePill(label = "Tiempo", value = "30 min", accentColor = SymbolPalette.energia, onClick = {}, modifier = Modifier.weight(1f))
            CardioValuePill(label = "Distancia", value = "5 km", accentColor = SymbolPalette.energia, onClick = {}, modifier = Modifier.weight(1f))
            CardioValuePill(label = "RPM", value = "—", accentColor = SymbolPalette.energia, onClick = {}, selected = true, modifier = Modifier.weight(1f))
        }
        CardioIntWheelDialog(
            title = "% FC",
            initial = 65,
            range = 50..100,
            unit = "%",
            accentColor = SymbolPalette.energia,
            onDismiss = {},
            onConfirm = {},
            allowZeroAsNone = false,
        )
    }

    @Composable
    private fun IconosSheet() {
        // Filas de tres: con seis en una fila, «ChevronDown» y «ChevronUp» se partían en letras.
        val todos = SessionCardioSamples.iconosNuevos + SessionCardioSamples.iconosExistentes
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            todos.chunked(3).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    fila.forEach { (label, icon) -> IconCell(label, icon) }
                }
            }
        }
    }

    @Composable
    private fun IconCell(label: String, icon: ImageVector) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(24.dp))
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }

    // ── Pruebas: una por escena y escala. ──

    @Test
    fun renderPreparacion() {
        onlyWhenAsked()
        shoot("se5_preparacion_360", 1f) { PreparacionSheet() }
    }

    @Test
    fun renderPreparacionLetra130() {
        onlyWhenAsked()
        shoot("se5_preparacion_360_letra130", 1.3f) { PreparacionSheet() }
    }

    @Test
    fun renderCardioPlegado() {
        onlyWhenAsked()
        shoot("se5_cardio_plegado_360", 1f) { CardioPlegadoSheet() }
    }

    @Test
    fun renderCardioPlegadoLetra130() {
        onlyWhenAsked()
        shoot("se5_cardio_plegado_360_letra130", 1.3f) { CardioPlegadoSheet() }
    }

    @Test
    fun renderCardioContinuoDesplegado() {
        onlyWhenAsked()
        shoot("se5_cardio_continuo_desplegado_360", 1f) { ContinuoDesplegadoSheet() }
    }

    @Test
    fun renderCardioContinuoDesplegadoLetra130() {
        onlyWhenAsked()
        shoot("se5_cardio_continuo_desplegado_360_letra130", 1.3f) { ContinuoDesplegadoSheet() }
    }

    @Test
    fun renderHiitDesplegado() {
        onlyWhenAsked()
        shoot("se5_hiit_desplegado_360", 1f) { HiitDesplegadoSheet() }
    }

    @Test
    fun renderHiitDesplegadoLetra130() {
        onlyWhenAsked()
        shoot("se5_hiit_desplegado_360_letra130", 1.3f) { HiitDesplegadoSheet() }
    }

    @Test
    fun renderIntervalosConLineaYDesplegados() {
        onlyWhenAsked()
        shoot("se5_intervalos_360", 1f) { IntervalosSheet() }
    }

    @Test
    fun renderIntervalosConLineaYDesplegadosLetra130() {
        onlyWhenAsked()
        shoot("se5_intervalos_360_letra130", 1.3f) { IntervalosSheet() }
    }

    @Test
    fun renderRuedasYPildora() {
        onlyWhenAsked()
        shoot("se5_ruedas_360", 1f) { RuedasSheet() }
    }

    @Test
    fun renderRuedasYPildoraLetra130() {
        onlyWhenAsked()
        shoot("se5_ruedas_360_letra130", 1.3f) { RuedasSheet() }
    }

    @Test
    fun renderIconosCardio() {
        onlyWhenAsked()
        shoot("se5_iconos_360", 1f) { IconosSheet() }
    }

    @Test
    fun renderIconosCardioLetra130() {
        onlyWhenAsked()
        shoot("se5_iconos_360_letra130", 1.3f) { IconosSheet() }
    }
}

/** Datos de ejemplo de la hoja del cardio (SE5): un bloque de cada clase y la preparación de una sesión. */
internal object SessionCardioSamples {

    class Plegado(val name: String, val details: CardioDetails)

    val preparacion: List<SessionPreparationRow> = listOf(
        SessionPreparationRow(name = "Movilidad de cadera", dose = "2 min"),
        SessionPreparationRow(name = "Movilidad de hombro", dose = "2 × 10"),
        SessionPreparationRow(name = "Calentamiento · Sentadilla", dose = "3 × 10"),
        SessionPreparationRow(name = "Calentamiento · Press banca", dose = "2 series"),
    )

    val continuo = CardioDetails(
        type = CardioType.TREADMILL,
        intensity = CardioIntensity.BAJA,
        targetDurationSeconds = 1800,
        targetHrPercent = 65,
    )

    val hiit = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        hiit = CardioHiitConfig(warmupSeconds = 180, workSeconds = 30, restSeconds = 60, rounds = 8, cooldownSeconds = 120),
    )

    val intervalos = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        intervalBlocks = buildList {
            repeat(8) { round ->
                add(CardioIntervalBlock(id = "w$round", type = CardioBlockType.WORK, durationSeconds = 30, speedKmh = 16.0))
                add(CardioIntervalBlock(id = "r$round", type = CardioBlockType.RECOVER, durationSeconds = 90, speedKmh = 8.0))
            }
        },
        intervalRounds = 1,
    )

    val plegados: List<Plegado> = listOf(
        Plegado("Cinta", continuo),
        Plegado("Bicicleta estática", hiit),
        Plegado(
            "Remo en máquina",
            CardioDetails(type = CardioType.ROW_MACHINE, targetDurationSeconds = null, targetDistanceKm = 5.0, targetPaceSecondsPerKm = 330),
        ),
    )

    val iconosNuevos: List<Pair<String, ImageVector>> = listOf(
        "Run" to KpknLineIcons.Run,
        "Walk" to KpknLineIcons.Walk,
        "Bike" to KpknLineIcons.Bike,
        "Row" to KpknLineIcons.Row,
        "Stairs" to KpknLineIcons.Stairs,
    )

    val iconosExistentes: List<Pair<String, ImageVector>> = listOf(
        "Training" to KpknLineIcons.Training,
        "Pulse" to KpknLineIcons.Pulse,
        "Tower" to KpknLineIcons.Tower,
        "Plus" to KpknLineIcons.Plus,
        "ChevronDown" to KpknLineIcons.ChevronDown,
        "ChevronUp" to KpknLineIcons.ChevronUp,
    )
}
