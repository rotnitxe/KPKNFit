package com.example.kpkn.render

import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.FINISH_PREVIEW_FAILED_MESSAGE
import com.example.kpkn.screens.workout.WorkoutFinishSamples
import com.example.kpkn.screens.workout.components.WorkoutCockpitPage
import com.example.kpkn.screens.workout.components.WorkoutSessionCockpitBody
import com.example.kpkn.screens.workout.design.FinishCalculationErrorModule
import com.example.kpkn.screens.workout.design.FinishFiguresModule
import com.example.kpkn.screens.workout.design.FinishFooter
import com.example.kpkn.screens.workout.design.FinishPendingSeriesModule
import com.example.kpkn.screens.workout.design.FinishRecoveryModule
import com.example.kpkn.screens.workout.design.FinishSummaryHeader
import com.example.kpkn.screens.workout.design.finishDrainLine
import com.example.kpkn.screens.workout.design.finishFigures
import com.example.kpkn.screens.workout.design.finishTomorrowLine
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO del resumen al terminar y del cockpit «Sesión» (LS6). No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`. Sin esa
 * variable, se salta. Pinta a 360 dp, al 100 % y al 130 % de letra: el resumen con récords y torre, sin récords, con series pendientes,
 * calculando, el error del cálculo, las cuatro páginas del cockpit y el diálogo destructivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutFinishRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val SUMMARY_TAG = "ls6-summary"
        private const val COCKPIT_TAG = "ls6-cockpit"
        private const val DIALOG_TAG = "ls6-dialog"
        private const val SESSION_NAME = "Empuje · Torso A"
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que WorkoutFrameRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    // ─── Resumen al terminar ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun resumenConRecordsYTorre100() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_resumen_records_torre_100", fontScale = 1f) { ResumenConTorre(recordCount = 2) }
    }

    @Test
    fun resumenConRecordsYTorre130() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_resumen_records_torre_130", fontScale = 1.3f) { ResumenConTorre(recordCount = 2) }
    }

    @Test
    fun resumenSinRecords100() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_resumen_sin_records_100", fontScale = 1f) { ResumenConTorre(recordCount = 0) }
    }

    @Test
    fun resumenConSeriesPendientes100() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_resumen_pendientes_100", fontScale = 1f) { ResumenPendientes() }
    }

    @Test
    fun calculando100() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_calculando_100", fontScale = 1f) { Calculando() }
    }

    @Test
    fun calculando130() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_calculando_130", fontScale = 1.3f) { Calculando() }
    }

    @Test
    fun errorDelCalculo100() {
        onlyWhenAsked()
        draw(SUMMARY_TAG, "ls6_error_calculo_100", fontScale = 1f) { ErrorDelCalculo() }
    }

    // ─── Cockpit «Sesión», en sus cuatro páginas ─────────────────────────────────────────────────────────────────────

    @Test
    fun cockpitResumen100() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_resumen_100", fontScale = 1f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Overview) }
    }

    @Test
    fun cockpitResumen130() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_resumen_130", fontScale = 1.3f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Overview) }
    }

    @Test
    fun cockpitFotos100() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_fotos_100", fontScale = 1f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Photos) }
    }

    @Test
    fun cockpitFotos130() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_fotos_130", fontScale = 1.3f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Photos) }
    }

    @Test
    fun cockpitUtilidades100() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_utilidades_100", fontScale = 1f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Tools) }
    }

    @Test
    fun cockpitUtilidades130() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_utilidades_130", fontScale = 1.3f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Tools) }
    }

    @Test
    fun cockpitNotas100() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_notas_100", fontScale = 1f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Notes) }
    }

    @Test
    fun cockpitNotas130() {
        onlyWhenAsked()
        draw(COCKPIT_TAG, "ls6_cockpit_notas_130", fontScale = 1.3f, heightDp = 700.dp) { CockpitPagina(WorkoutCockpitPage.Notes) }
    }

    // ─── Diálogo destructivo ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun dialogoDestructivo100() {
        onlyWhenAsked()
        draw(DIALOG_TAG, "ls6_dialogo_destructivo_100", fontScale = 1f, heightDp = 480.dp) {
            KpknAlertDialog(
                onDismissRequest = {},
                title = "Entrenamiento en curso",
                text = "Ya hay un entrenamiento en curso: $SESSION_NAME. Si empiezas este, se perderá el anterior.",
                confirmLabel = "Descartar",
                onConfirm = {},
                dismissLabel = "Reanudar",
                destructive = true,
            )
        }
    }

    // ─── Escenas ─────────────────────────────────────────────────────────────────────────────────────────────────────

    /** El resumen con las cifras y la torre con su gasto; con [recordCount] cero, la cuarta cifra es «Ejercicios». */
    @Composable
    private fun ResumenConTorre(recordCount: Int) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = SESSION_NAME)
                FinishFiguresModule(
                    figures = finishFigures(
                        durationMinutes = 58,
                        totalSets = 22,
                        totalVolumeKg = 8450.0,
                        recordCount = recordCount,
                        exerciseCount = 6,
                    ),
                )
                FinishRecoveryModule(
                    values = TowerRingValues(columna = 70, musculos = 60, energia = 80),
                    from = TowerRingValues(columna = 82, musculos = 92, energia = 98),
                    drainLine = finishDrainLine(32, 18, 12),
                    tomorrowLine = finishTomorrowLine(84, 90),
                )
            }
            FinishFooter(
                primaryLabel = "Guardar entrenamiento",
                primaryEnabled = true,
                onPrimary = {},
                onShare = {},
                onBack = {},
            )
        }
    }

    /** Con series pendientes: el aviso va primero, con «Seguir con ellas». */
    @Composable
    private fun ResumenPendientes() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = SESSION_NAME)
                FinishPendingSeriesModule(
                    message = "Quedan 2 series por hacer: Press inclinado (2) y Remo con barra. Si guardas ya, no se contarán.",
                    actionLabel = "Seguir con ellas",
                    onContinue = {},
                )
                FinishFiguresModule(
                    figures = finishFigures(
                        durationMinutes = 41,
                        totalSets = 14,
                        totalVolumeKg = 5230.0,
                        recordCount = 1,
                        exerciseCount = 4,
                    ),
                )
            }
            FinishFooter(
                primaryLabel = "Guardar entrenamiento",
                primaryEnabled = true,
                onPrimary = {},
                onShare = {},
                onBack = {},
            )
        }
    }

    /** Mientras se calcula: esqueletos en las cifras y en la torre, el aviso ya visible y el guardado apagado. */
    @Composable
    private fun Calculando() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = SESSION_NAME)
                FinishFiguresModule(figures = null)
                FinishRecoveryModule(
                    values = null,
                    from = null,
                    drainLine = null,
                    tomorrowLine = null,
                    loading = true,
                    loadingCaptionVisible = true,
                )
            }
            FinishFooter(
                primaryLabel = "Guardar entrenamiento",
                primaryEnabled = false,
                onPrimary = {},
                onBack = {},
            )
        }
    }

    /** El error del cálculo: el motivo en lenguaje llano, en un módulo de peligro, y «Cerrar». */
    @Composable
    private fun ErrorDelCalculo() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = SESSION_NAME)
                FinishCalculationErrorModule(reason = FINISH_PREVIEW_FAILED_MESSAGE)
            }
            FinishFooter(
                primaryLabel = "Cerrar",
                primaryEnabled = true,
                onPrimary = {},
            )
        }
    }

    /** Una página del cockpit con los datos de ejemplo de LS6. */
    @Composable
    private fun CockpitPagina(page: WorkoutCockpitPage) {
        WorkoutSessionCockpitBody(
            exercises = emptyList(),
            completedSets = emptyMap(),
            milestones = WorkoutFinishSamples.milestones,
            sessionProgressLabel = "Progreso: 12/22",
            liveEnergySummary = SessionEnergySummary(),
            sessionNotes = "",
            sessionSavedNotes = WorkoutFinishSamples.savedNotes,
            sessionPhotos = emptyList(),
            sessionMedia = emptyList(),
            sessionChecklist = WorkoutFinishSamples.checklist,
            onSessionNotesChange = {},
            onSaveSessionNote = {},
            onAddSessionPhoto = {},
            onBeginSessionCameraCapture = { null },
            onCompleteSessionCameraCapture = { _, _ -> },
            onSessionCameraLaunchFailed = {},
            mediaCaptureError = null,
            onRetryPendingMediaCaptures = {},
            onRemoveSessionPhoto = {},
            onRemoveSessionMedia = {},
            onAddChecklistItem = {},
            onToggleChecklistItem = {},
            onRemoveChecklistItem = {},
            sessionAccentColor = SymbolPalette.musculo,
            bodyWeight = 80.0,
            poseOverlayEnabled = false,
            initialPage = page,
        )
    }

    /** Pinta [content] en una caja de 360 dp sobre negro, con la etiqueta [tag], y guarda el recorte como PNG. */
    private fun draw(tag: String, name: String, fontScale: Float, heightDp: Dp? = null, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .then(if (heightDp != null) Modifier.height(heightDp) else Modifier)
                            .background(Color.Black)
                            .testTag(tag),
                    ) {
                        content()
                    }
                }
            }
        }
        rule.waitForIdle()
        save(tag, name)
    }

    /** Recorta la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
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
}
