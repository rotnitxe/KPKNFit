package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SessionEditorSheetSamples
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.sheets.CoverSheetBody
import com.example.kpkn.screens.sessioneditor.components.sheets.HistoryBody
import com.example.kpkn.screens.sessioneditor.components.sheets.RelationshipPickerBody
import com.example.kpkn.screens.sessioneditor.components.sheets.RulesSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SaveSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionClonerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetManagerBody
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetMemberPickerSheet
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO de las hojas del editor (SE7). No comprueba nada: escribe PNG. Dibuja cada hoja con datos de ejemplo a 360 dp, al 100 %
 * y al 130 % de letra: reglas («Reglas» y «Tiempo»), portada, historial con versiones y vacío, copiar, traer, guardar, las dos superseries,
 * la relación entre ejercicios y el diálogo destructivo de borrar una plantilla. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionEditorSheetsRenderSheetTest {

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

    /** Escena que se dibuja ahora y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("reglas")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    @Composable
    private fun RulesScene(state: SessionEditorUiState) {
        RulesSheet(
            uiState = state,
            onApplyRules = { ApplyRulesOutcome.NoChanges },
            onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
            onRuleLimitsChange = { _, _ -> },
            onAdvancedRuleLimitsChange = { _, _, _, _ -> },
            onApplyGlobalIntensityAdjustment = { _, _, _ -> },
            setTargetDuration = {},
            setPartTargetDuration = { _, _ -> },
            setExerciseTargetDuration = { _, _ -> },
        )
    }

    @Composable
    private fun Scene() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas),
        ) {
            when (scene) {
                "reglas" -> Box(Modifier.height(1500.dp)) { RulesScene(SessionEditorSheetSamples.rulesState()) }
                "reglas_tiempo" -> Box(Modifier.height(1500.dp)) { RulesScene(SessionEditorSheetSamples.rulesState(tiempo = true)) }
                "portada" -> CoverSheetBody(session = SessionEditorSheetSamples.session, onSelectGradient = {})
                "historial" -> HistoryBody(
                    versions = SessionEditorSheetSamples.versions,
                    currentSession = SessionEditorSheetSamples.session,
                    hasUnsavedChanges = false,
                    onRestoreSnapshot = {},
                )
                "historial_vacio" -> HistoryBody(
                    versions = emptyList(),
                    currentSession = SessionEditorSheetSamples.session,
                    hasUnsavedChanges = false,
                    onRestoreSnapshot = {},
                )
                // «Copiar» y «Traer» comparten la hoja; la pestaña se elige desde la prueba (ver `renderStatic`).
                "copiar", "traer" -> SessionClonerSheet(
                    uiState = SessionEditorSheetSamples.cloneState,
                    onCloneCurrentToTargets = { _, _, _ -> },
                    onImportFromSource = { _, _, _ -> },
                )
                "guardar" -> SaveSheet(onSave = {}, onDiscardSwitch = {}, isSimpleProgram = false)
                "superseries_crear" -> SupersetMemberPickerSheet(
                    draft = SessionEditorSheetSamples.draft,
                    sessionExercises = SessionEditorSheetSamples.pickerExercises,
                    onUpdateDraft = {},
                    onConfirm = {},
                    onOpenCatalog = {},
                    onDismiss = {},
                )
                "superseries_gestionar" -> SupersetManagerBody(
                    exercises = SessionEditorSheetSamples.supersetMembers,
                    partId = "p-empuje",
                    supersetId = "g1",
                    restBetweenSeconds = 60,
                    restAfterSeconds = 120,
                    onUpdateRestBetween = { _, _, _ -> },
                    onUpdateRestAfter = { _, _, _ -> },
                    onRemove = { _, _ -> },
                    onDismiss = {},
                )
                "relacion" -> RelationshipPickerBody(
                    targetExercise = SessionEditorSheetSamples.relationTarget,
                    candidates = SessionEditorSheetSamples.relationCandidates,
                    query = "",
                    onSearch = {},
                    onSelectAnchor = {},
                    onDismiss = {},
                )
                "borrar" -> Box(Modifier.height(640.dp)) {
                    KpknAlertDialog(
                        onDismissRequest = {},
                        title = "Eliminar plantilla",
                        text = "¿Eliminar la plantilla «Volumen 3 × 10»?",
                        confirmLabel = "Eliminar",
                        onConfirm = {},
                        dismissLabel = "Cancelar",
                        onDismiss = {},
                        destructive = true,
                    )
                }
            }
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /**
     * Pinta una escena estática a 100 % y 130 % de letra. [afterShow] prepara el estado (p. ej., elegir la pestaña «Traer») solo en la
     * pasada del 100 %: al subir la letra la escena sigue igual y ese estado se conserva.
     */
    private fun renderStatic(sceneName: String, file: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            if (suffix.isEmpty()) afterShow()
            // Con el reloj parado, los efectos de arranque (la pestaña inicial) y las animaciones llegan por cuadros: 600 ms antes de capturar.
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            save("sheet", "$file$suffix")
        }
    }

    @Test
    fun renderSessionEditorSheets() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Scene()
            }
        }
        renderStatic("reglas", "se7_reglas")
        renderStatic("reglas_tiempo", "se7_reglas_tiempo")
        renderStatic("portada", "se7_portada")
        renderStatic("historial", "se7_historial")
        renderStatic("historial_vacio", "se7_historial_vacio")
        renderStatic("copiar", "se7_copiar")
        renderStatic("traer", "se7_traer", afterShow = {
            // Los segmentos son `selectable` con `Role.Tab`: se invoca la acción de clic del segmento «Traer», sin depender del punto
            // donde caiga un toque sobre el control.
            rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText("Traer"))
                .performSemanticsAction(SemanticsActions.OnClick)
        })
        renderStatic("guardar", "se7_guardar")
        renderStatic("superseries_crear", "se7_superseries_crear")
        renderStatic("superseries_gestionar", "se7_superseries_gestionar")
        renderStatic("relacion", "se7_relacion")
        renderStatic("borrar", "se7_borrar_dialogo")
    }
}
