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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
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
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO de los diálogos KPKN (D2): no comprueba nada, escribe PNG. Pinta el diálogo normal, el destructivo, uno con icono, uno
 * de texto largo, uno antiguo con botones de Material (para ver que salen en Inter) y un diálogo de vidrio, a 360 dp y con la letra al
 * 100 % y al 130 %. Sin el desenfoque de pantalla: el panel sale con su respaldo sin desenfoque. Solo corre con `KPKN_RENDER_DIR`;
 * sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknDialogRenderSheetTest {

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

    /** Diálogo que se dibuja ahora y escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var sample by mutableStateOf("normal")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    @Composable
    private fun Sample(name: String) {
        when (name) {
            "normal" -> KpknAlertDialog(
                onDismissRequest = {},
                title = "¿Guardar los cambios?",
                text = "Tienes cambios sin guardar en esta sesión. Si sales ahora, se pierden.",
                confirmLabel = "Guardar",
                onConfirm = {},
                dismissLabel = "Cancelar",
            )
            "destructivo" -> KpknAlertDialog(
                onDismissRequest = {},
                title = "¿Eliminar esta medición?",
                text = "Se borran el peso y las medidas de este día y hora. Podrás deshacerlo justo después.",
                confirmLabel = "Eliminar",
                onConfirm = {},
                dismissLabel = "Cancelar",
                destructive = true,
            )
            "icono" -> KpknAlertDialog(
                onDismissRequest = {},
                icon = {
                    Box(
                        modifier = Modifier.size(44.dp).background(KpknSurface.inset, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = KpknLineIcons.Bell,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = WizardColors.text,
                        )
                    }
                },
                title = { Text("Descanso terminado") },
                text = { Text("Puedes seguir con la siguiente serie cuando quieras.") },
                confirmButton = { KpknPrimaryPill(text = "Seguir", onClick = {}) },
                dismissButton = { KpknAlertDismissButton(text = "Más tarde", onClick = {}) },
            )
            "largo" -> KpknAlertDialog(
                onDismissRequest = {},
                title = "Reajustar el plan de la semana",
                text = "Al cambiar los días de entreno, las sesiones que ya tenías se recolocan en el nuevo reparto. " +
                    "Revisa la semana antes de confirmar.",
                confirmLabel = "Reajustar",
                onConfirm = {},
                dismissLabel = "Cancelar",
            )
            "antiguo" -> KpknAlertDialog(
                onDismissRequest = {},
                title = { Text("Permiso de notificaciones") },
                text = { Text("Activa las notificaciones para recibir el aviso del descanso entre series.") },
                confirmButton = { Button(onClick = {}) { Text("Activar") } },
                dismissButton = { TextButton(onClick = {}) { Text("Ahora no") } },
            )
            "antiguo_rojo" -> KpknAlertDialog(
                onDismissRequest = {},
                title = { Text("¿Quitar el objetivo?") },
                text = { Text("Deja de aparecer en Cuerpo. El historial de medidas se conserva.") },
                confirmButton = { TextButton(onClick = {}) { Text("Eliminar", color = Color.Red) } },
                dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
            )
            else -> KpknGlassDialog(onDismissRequest = {}) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Ajustes del descanso", style = KpknType.dialogTitle, color = WizardColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("El aviso suena al terminar cada serie.", style = KpknType.body, color = WizardColors.textMuted)
                    Spacer(Modifier.height(20.dp))
                    KpknPrimaryPill(text = "Listo", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

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
    fun renderTheDialogSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(Modifier.testTag("sheet").width(360.dp).height(480.dp).background(Color.Black)) {
                    Sample(sample)
                }
            }
        }
        for (name in listOf("normal", "destructivo", "icono", "largo", "antiguo", "antiguo_rojo", "vidrio")) {
            for ((suffix, scale) in listOf("s100" to 1f, "s130" to 1.3f)) {
                rule.runOnUiThread {
                    sample = name
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "dialog_${name}_$suffix")
            }
        }
    }
}
