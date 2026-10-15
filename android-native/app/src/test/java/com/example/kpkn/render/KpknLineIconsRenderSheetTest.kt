package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del set KPKN Line (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * Mismo entorno que [WizardRenderSheetTest]: Inter real, 360 dp de ancho a densidad 3.
 *
 * Tres láminas: `iconos_set` (todo el set a 24 y 20 dp, y ×4 de navegación y comidas), `iconos_navegacion` (la barra con
 * Inicio activo) y `iconos_comidas` (cuatro comidas, dos registradas).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknLineIconsRenderSheetTest {

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

    /** Vista de Compose de la prueba: se dibuja a un bitmap porque `captureToImage` no tiene pase de dibujo en Robolectric. */
    private var hostView: View? = null

    /** Los cuatro de navegación, con el nombre del set. */
    private val navegacion: List<Pair<String, ImageVector>> = listOf(
        "Home" to KpknLineIcons.Home,
        "Training" to KpknLineIcons.Training,
        "Nutrition" to KpknLineIcons.Nutrition,
        "Body" to KpknLineIcons.Body,
    )

    /** Los cuatro de comidas, con el nombre del set. */
    private val comidas: List<Pair<String, ImageVector>> = listOf(
        "Sunrise" to KpknLineIcons.Sunrise,
        "Sun" to KpknLineIcons.Sun,
        "Apple" to KpknLineIcons.Apple,
        "Moon" to KpknLineIcons.Moon,
    )

    /** El set completo, en el orden de la lista del documento de diseño (§4), y al final los del reproductor de técnica (VT7a). */
    private val todos: List<Pair<String, ImageVector>> = listOf(
        "Home" to KpknLineIcons.Home,
        "Training" to KpknLineIcons.Training,
        "Nutrition" to KpknLineIcons.Nutrition,
        "Body" to KpknLineIcons.Body,
        "Play" to KpknLineIcons.Play,
        "PlayFilled" to KpknLineIcons.PlayFilled,
        "Plus" to KpknLineIcons.Plus,
        "Edit" to KpknLineIcons.Edit,
        "Info" to KpknLineIcons.Info,
        "ChevronRight" to KpknLineIcons.ChevronRight,
        "ChevronLeft" to KpknLineIcons.ChevronLeft,
        "ChevronDown" to KpknLineIcons.ChevronDown,
        "Close" to KpknLineIcons.Close,
        "Check" to KpknLineIcons.Check,
        "Clock" to KpknLineIcons.Clock,
        "Calendar" to KpknLineIcons.Calendar,
        "Bell" to KpknLineIcons.Bell,
        "Book" to KpknLineIcons.Book,
        "TrendUp" to KpknLineIcons.TrendUp,
        "TrendDown" to KpknLineIcons.TrendDown,
        "Repeat" to KpknLineIcons.Repeat,
        "More" to KpknLineIcons.More,
        "Sliders" to KpknLineIcons.Sliders,
        "Person" to KpknLineIcons.Person,
        "Sunrise" to KpknLineIcons.Sunrise,
        "Sun" to KpknLineIcons.Sun,
        "Apple" to KpknLineIcons.Apple,
        "Moon" to KpknLineIcons.Moon,
        "Tower" to KpknLineIcons.Tower,
        "FrameBack" to KpknLineIcons.FrameBack,
        "FrameForward" to KpknLineIcons.FrameForward,
        "Target" to KpknLineIcons.Target,
    )

    private fun guardar(etiqueta: String, nombre: String) {
        val bounds = rule.onNodeWithTag(etiqueta).fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        rule.runOnUiThread { SymbolRenderSheet.savePng(vista, bounds, nombre) }
    }

    @Test
    fun renderIconosSet() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            HojaSet()
        }
        rule.waitForIdle()
        guardar("set", "iconos_set")
    }

    @Test
    fun renderIconosNavegacion() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            HojaNavegacion()
        }
        rule.waitForIdle()
        guardar("nav", "iconos_navegacion")
    }

    @Test
    fun renderIconosComidas() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            HojaComidas()
        }
        rule.waitForIdle()
        guardar("comidas", "iconos_comidas")
    }

    // ── Lámina 1: el set completo ──────────────────────────────────────────────

    @Composable
    private fun HojaSet() {
        Column(
            Modifier.testTag("set").width(360.dp).background(Color.Black).padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                todos.chunked(6).forEach { fila ->
                    Row(Modifier.fillMaxWidth()) {
                        fila.forEach { (nombre, icono) ->
                            CeldaDelSet(nombre, icono, Modifier.weight(1f))
                        }
                        repeat(6 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Etiqueta("NAVEGACIÓN · AMPLIADO ×4")
            Ampliados(navegacion)
            Etiqueta("COMIDAS · AMPLIADO ×4")
            Ampliados(comidas)
        }
    }

    /** Una celda de la rejilla: el icono a 24 dp y a 20 dp, y su nombre debajo en 10 sp. */
    @Composable
    private fun CeldaDelSet(nombre: String, icono: ImageVector, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Icon(icono, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = conEspacios(nombre),
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp),
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }

    /** Dos filas de dos iconos a 96 dp (×4 de 24), con su nombre debajo. */
    @Composable
    private fun Ampliados(iconos: List<Pair<String, ImageVector>>) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            iconos.chunked(2).forEach { par ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                ) {
                    par.forEach { (nombre, icono) ->
                        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icono, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(96.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = conEspacios(nombre),
                                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
                                color = WizardColors.textFaint,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Etiqueta(texto: String) {
        Text(
            text = texto,
            style = TextStyle(
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.2.sp,
            ),
            color = WizardColors.textMuted,
        )
    }

    // ── Lámina 2: la barra de navegación ───────────────────────────────────────

    /** Barra de 360 × 64 dp. Activo = tinta y punto músculo; inactivo = textFaint. Solo Inicio está activo. */
    @Composable
    private fun HojaNavegacion() {
        val pestanas = listOf(
            "Inicio" to KpknLineIcons.Home,
            "Entreno" to KpknLineIcons.Training,
            "Nutrición" to KpknLineIcons.Nutrition,
            "Cuerpo" to KpknLineIcons.Body,
        )
        Row(Modifier.testTag("nav").width(360.dp).height(64.dp).background(Color.Black)) {
            pestanas.forEachIndexed { indice, (etiqueta, icono) ->
                val activa = indice == 0
                val tinta = if (activa) WizardColors.text else WizardColors.textFaint
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icono, contentDescription = null, tint = tinta, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = etiqueta,
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
                        color = tinta,
                    )
                    Spacer(Modifier.height(4.dp))
                    Spacer(Modifier.size(4.dp).background(if (activa) SymbolPalette.musculo else Color.Transparent, CircleShape))
                }
            }
        }
    }

    // ── Lámina 3: las comidas ──────────────────────────────────────────────────

    /**
     * Cuatro comidas en círculos de 44 dp. Registradas: icono en tinta y marca de check de 14 dp en `done` en la esquina.
     * Sin registrar: icono en textFaint y «Añadir».
     */
    @Composable
    private fun HojaComidas() {
        val franjas = listOf(
            Triple("Desayuno", KpknLineIcons.Sunrise, true),
            Triple("Almuerzo", KpknLineIcons.Sun, true),
            Triple("Merienda", KpknLineIcons.Apple, false),
            Triple("Cena", KpknLineIcons.Moon, false),
        )
        Row(Modifier.testTag("comidas").width(360.dp).background(Color.Black).padding(vertical = 16.dp)) {
            for ((nombre, icono, registrada) in franjas) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp)) {
                        // #1A1A1A es KpknSurface.inset (todavía sin el módulo ui/design en esta rama).
                        Box(
                            Modifier.fillMaxSize().background(Color(0xFF1A1A1A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icono,
                                contentDescription = null,
                                tint = if (registrada) WizardColors.text else WizardColors.textFaint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (registrada) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(14.dp)
                                    .background(WizardColors.done, CircleShape)
                                    .border(1.5.dp, Color.Black, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    KpknLineIcons.Check,
                                    contentDescription = null,
                                    tint = SymbolPalette.onOk,
                                    modifier = Modifier.size(9.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = nombre,
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
                        color = if (registrada) WizardColors.text else WizardColors.textMuted,
                    )
                    if (!registrada) {
                        Text(
                            text = "Añadir",
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
                            color = WizardColors.textFaint,
                        )
                    }
                }
            }
        }
    }

    /** «ChevronRight» se lee «Chevron Right» para que el nombre parta en dos líneas dentro de la celda. */
    private fun conEspacios(nombre: String): String = nombre.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
}
