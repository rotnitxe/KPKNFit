package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.domain.workout.FreeClockState
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.domain.workout.FreeTimerState
import com.example.kpkn.domain.workout.StopwatchState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.FINISH_EMPTY_SESSION_GUIDANCE
import com.example.kpkn.screens.workout.FINISH_SAVE_FAILED_MESSAGE
import com.example.kpkn.screens.workout.FinishCardio
import com.example.kpkn.screens.workout.FinishDims
import com.example.kpkn.screens.workout.FinishLiftChip
import com.example.kpkn.screens.workout.FinishModel
import com.example.kpkn.screens.workout.FinishMuscle
import com.example.kpkn.screens.workout.FinishRecord
import com.example.kpkn.screens.workout.FinishSheetActions
import com.example.kpkn.screens.workout.FinishSheetContent
import com.example.kpkn.screens.workout.FinishSheetState
import com.example.kpkn.screens.workout.FinishTier
import com.example.kpkn.screens.workout.PacingAlertMode
import com.example.kpkn.screens.workout.components.FreeClockActions
import com.example.kpkn.screens.workout.components.FreeClockBanner
import com.example.kpkn.screens.workout.components.FreeClockPillLine
import com.example.kpkn.screens.workout.components.PillClock
import com.example.kpkn.screens.workout.components.SessionTimeSheetContent
import com.example.kpkn.screens.workout.components.TimeSheetBody
import com.example.kpkn.ui.theme.KPKNTheme
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

private const val TAG = "lx-escena"

/** Hora de pared fija de las hojas: todo lo que depende del reloj sale siempre igual. */
private const val NOW = 1_800_000_000_000L

/**
 * HOJA DE RENDER del paquete «En vivo · más» (Pulido v1, prefijo `lx`): la hoja «Tiempo» (Sesión · Cronómetro · Temporizador, `lx-t-*`) y
 * la hoja «Sesión completada» (`lx-f-*`) con los datos de muestra del lienzo, a 360 × 880 dp (y 360 × 740 y 130 % en las densas), más los
 * fotogramas de sus animaciones propias (fin del temporizador, franja «Listo», entrada de la hoja de cierre). Sin `KPKN_RENDER_DIR` se salta.
 * Las escenas de la Lista y del modo visual las dibujan `LiveListRenderSheetTest` y `LiveCardRenderSheetMediaTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = Application::class)
class VivoRestoRenderSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    // ── hoja «Tiempo» ──────────────────────────────────────────────────────────────────────────────────────────────

    private val lapsDemo = listOf(41_200L, 38_900L, 44_600L, 40_100L, 39_400L).runningFold(0L) { acc, p -> acc + p }.drop(1)

    private fun timeScene(name: String, tab: Int, clock: FreeClockState, fontScale: Float = 1f, height: Dp = 880.dp) =
        draw(name, fontScale, height) {
            val nowMs = mutableStateOf(NOW)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF1B1B1D))) {
                TimeSheetBody(tab, {}, nowMs, clock, FreeClockActions(), session = {
                    val start = NOW - 3_134_000L
                    SessionTimeSheetContent(
                        elapsedSeconds = 3_134, startTimeMs = start, targetMinutes = 60, accent = Color.White, completedWorkingSets = 10, totalWorkingSets = 11,
                        selectedMinutes = 60, pendingDescription = null, onProposeMinutes = {}, onAssignTime = {}, onClear = {}, onApplyPending = {},
                        alertMode = PacingAlertMode.FINAL, onAlertMode = {}, ultraFastApplied = false, ultraFastSavedSeconds = 0,
                        onUltraFastPreview = {}, onRevertUltraFast = null, sessionMs = { NOW - start },
                    )
                })
            }
        }

    @Test fun ltSesion() = timeScene("lx-t-sesion", 0, FreeClockState())
    @Test fun ltCronCero() = timeScene("lx-t-cron-cero", 1, FreeClockState())
    @Test fun ltCronVueltas() = timeScene("lx-t-cron-vueltas", 1, FreeClockState(
        stopwatch = StopwatchState(running = true, startedAtMs = NOW - 4_500L, accumulatedMs = 133_400L, lapTotalsMs = listOf(41_200L, 80_100L, 124_700L))))
    @Test fun ltCronPausa() = timeScene("lx-t-cron-pausa", 1, FreeClockState(
        stopwatch = StopwatchState(running = false, accumulatedMs = 248_300L, lapTotalsMs = lapsDemo)))
    @Test fun ltTempConfig() = timeScene("lx-t-temp-config", 2, FreeClockState(timer = FreeTimerState(digits = "130")))
    @Test fun ltTempCorriendo() = timeScene("lx-t-temp-corriendo", 2, FreeClockState(
        timer = FreeTimerState(phase = FreeTimerPhase.RUNNING, digits = "130", totalMs = 90_000L, endAtMs = NOW + 43_000L)))
    @Test fun ltTempFin() = timeScene("lx-t-temp-fin", 2, FreeClockState(
        timer = FreeTimerState(phase = FreeTimerPhase.FINISHED, digits = "130", totalMs = 90_000L), bannerPending = true))
    @Test fun ltTemp740() = timeScene("lx-t-temp-config-740", 2, FreeClockState(timer = FreeTimerState(digits = "130")), fontScale = 1.3f, height = 740.dp)
    @Test fun ltCoexiste() = draw("lx-t-coexiste") {
        // Cápsula (solo su píldora de dos líneas) y la franja «Listo»; el resto de la pantalla (descanso y dock) es de otros paquetes.
        val state = FreeClockState(stopwatch = StopwatchState(running = true, startedAtMs = NOW - 72_400L), bannerPending = true)
        Box(Modifier.align(Alignment.TopCenter).padding(top = 32.dp).fillMaxWidth().padding(horizontal = 10.dp)) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 52.dp, top = 6.dp).size(width = 90.dp, height = 46.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x07FFFFFF)))
            Box(Modifier.align(Alignment.TopEnd).padding(end = 62.dp, top = 28.dp)) { FreeClockPillLine(state, PillClock.Stopwatch) }
            Box(Modifier.padding(top = 76.dp)) { FreeClockBanner(null, {}) }
        }
    }

    // ── hoja «Sesión completada» ───────────────────────────────────────────────────────────────────────────────────

    private val rows = listOf(
        listOf(.62f, .68f, .72f), listOf(.5f, .55f, .6f), listOf(.8f, .86f, .92f), listOf(.42f, .45f, .48f), listOf(.34f, .36f, .38f),
    )
    private val muscles = listOf(
        FinishMuscle("Glúteos", 11.5, 9.0), FinishMuscle("Cuádriceps", 8.0, 6.0), FinishMuscle("Isquiotibiales", 7.5, 3.0),
        FinishMuscle("Espalda baja", 4.0, 0.0), FinishMuscle("Aductores", 3.0, 3.0),
    )
    private val normal = FinishModel(
        name = "Legs II · Semana 1", dateLine = "Mié 29 oct", minutes = 52, sets = 18, plannedSets = 18, volume = 8_420.0, volumeDelta = 340.0,
        trend = listOf(7_380.0, 7_640.0, 7_510.0, 7_790.0, 8_080.0, 8_420.0), effort = 7.5, records = 0, record = null, cardio = null,
        muscles = muscles, lifts = listOf(FinishLiftChip("Hip Thrust", 5.0), FinishLiftChip("Peso Muerto", 2.5)), targetMinutes = null, empty = false,
    )

    private fun dims(tier: FinishTier = FinishTier.A, big: Boolean = false, dense: Boolean = false, error: Boolean = false, roomy: Boolean = false) =
        FinishDims(tier, big, dense, error, roomy)

    private fun finScene(
        name: String, model: FinishModel, layout: FinishDims = dims(), fontScale: Float = 1f, height: Dp = 880.dp, saving: Boolean = false,
        error: String? = null, share: Boolean = false, panel: Boolean = false, notes: String = "", photos: Int = 0, effortPick: Double? = null,
        recordIndex: Int = -1,
    ) = draw(name, fontScale, height) {
        val state = FinishSheetState(
            model = model, dims = layout, saving = saving, errorMessage = error,
            blockedMessage = if (model.empty) FINISH_EMPTY_SESSION_GUIDANCE else null, notes = notes, photoCount = photos, shareAfterSave = share,
            panelOpen = panel, checklist = listOf(SessionChecklistItem("t1", "Estirar cuádriceps 5 min", true), SessionChecklistItem("t2", "Subir peso del hip thrust la semana próxima", false)),
            effortSelected = effortPick, storyThumb = null, ridges = if (model.empty) emptyList() else rows, ridgeRecordIndex = recordIndex,
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(if (model.empty) Modifier else Modifier.fillMaxHeight(0.94f))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF1B1B1D)),
        ) { FinishSheetContent(state, FinishSheetActions()) }
    }

    private val record = normal.copy(
        volumeDelta = 620.0, trend = listOf(7_380.0, 7_640.0, 7_510.0, 7_790.0, 7_800.0, 8_420.0), records = 1,
        record = FinishRecord(
            exerciseId = "peso-muerto-convencional",
            exerciseName = "Peso Muerto Convencional",
            metric = "e1RM",
            value = 182.0,
            previousValue = 177.5,
            improvement = 4.5,
            unit = "kg",
            currentLabel = "182,0 kg e1RM",
            previousLabel = "177,5 kg e1RM",
            isInitialReference = false,
        ),
        lifts = listOf(FinishLiftChip("Peso Muerto", 5.0), FinishLiftChip("Hip Thrust", 5.0)),
    )
    private val cardio = normal.copy(
        name = "Pierna + cardio · Semana 1", minutes = 74, sets = 21, plannedSets = 21, volume = 7_260.0, volumeDelta = -120.0,
        trend = listOf(7_600.0, 7_380.0, 7_410.0, 7_500.0, 7_390.0, 7_260.0), effort = 8.0, cardio = FinishCardio("Cinta", 20, 2.4, 148, 190),
        muscles = listOf(FinishMuscle("Cuádriceps", 9.0, 7.0), FinishMuscle("Glúteos", 8.0, 6.0), FinishMuscle("Isquiotibiales", 5.0, 3.0),
            FinishMuscle("Pantorrillas", 3.0, 3.0), FinishMuscle("Core", 2.0, 0.0)),
        lifts = listOf(FinishLiftChip("Sentadilla", 2.5), FinishLiftChip("Prensa", 0.0)),
    )
    private val short = normal.copy(
        name = "Press militar rápido", minutes = 12, sets = 3, plannedSets = 3, volume = 1_080.0, volumeDelta = null, trend = emptyList(), effort = null,
        muscles = listOf(FinishMuscle("Deltoides", 3.0, 3.0), FinishMuscle("Tríceps", 1.5, 0.0)), lifts = emptyList(),
    )
    private val empty = normal.copy(
        minutes = 3, sets = 0, plannedSets = 18, volume = 0.0, volumeDelta = null, trend = emptyList(), effort = null, muscles = emptyList(), lifts = emptyList(), empty = true,
    )

    @Test fun lfNormal() = finScene("lx-f-normal", normal)
    @Test fun lfRecord() = finScene("lx-f-record", record, dims(dense = true), recordIndex = 0)
    @Test fun lfCardio() = finScene("lx-f-cardio", cardio, dims(dense = true))
    @Test fun lfCorta() = finScene("lx-f-corta", short)
    @Test fun lfVacia() = finScene("lx-f-vacia", empty)
    @Test fun lfGuardando() = finScene("lx-f-guardando", normal, saving = true, share = true)
    @Test fun lfError() = finScene("lx-f-error", normal, dims(error = true), error = FINISH_SAVE_FAILED_MESSAGE)
    @Test fun lfNotas() = finScene("lx-f-notas", normal, panel = true, notes = "Buen día. El hip thrust se movió rápido; bajar a 90 s de descanso la próxima.", photos = 3, effortPick = 7.0)
    @Test fun lfNivelB() = finScene("lx-f-nivel-b", normal, dims(FinishTier.B))
    @Test fun lfNivelB740() = finScene("lx-f-nivel-b-740", normal, dims(FinishTier.B), height = 740.dp)
    @Test fun lfNivelC() = finScene("lx-f-nivel-c", normal, dims(FinishTier.C))
    @Test fun lfNivelC130() = finScene("lx-f-nivel-c-130", normal, dims(FinishTier.C, big = true), fontScale = 1.3f, height = 740.dp)
    @Test fun lfRecord740() = finScene("lx-f-record-740", record, dims(FinishTier.C, dense = true), height = 740.dp, recordIndex = 0)

    // ── fotogramas de las animaciones propias ──────────────────────────────────────────────────────────────────────

    /** Avanza el reloj de cuadros hasta [instants] (ms desde el primer cuadro) y guarda un PNG por instante: `<name>_t<ms>.png`. */
    private fun frames(name: String, instants: List<Long>, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f), LocalWizardReducedMotion provides false) {
                KPKNTheme { MaterialTheme { Box(Modifier.size(360.dp, 880.dp).background(Color.Black).testTag(TAG)) { content() } } }
            }
        }
        rule.waitForIdle()
        var elapsed = 0L
        rule.mainClock.advanceTimeByFrame()
        for (t in instants) {
            val step = t - elapsed
            if (step > 0) rule.mainClock.advanceTimeBy(step)
            elapsed = t
            repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            save("${name}_t${"%05d".format(t)}")
        }
    }

    /** `lx-A14`: el anillo del temporizador terminado late 2 veces (1,4 s, `vaiven`): 0, 25, 50, 75 y 100 % de una vuelta. */
    @Test fun framesTimerPulse() = frames("lx-fr-temp-fin", listOf(0, 350, 700, 1_050, 1_400, 2_100)) {
        val nowMs = mutableStateOf(NOW)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF1B1B1D))) {
            TimeSheetBody(2, {}, nowMs, FreeClockState(timer = FreeTimerState(phase = FreeTimerPhase.FINISHED, digits = "130", totalMs = 90_000L)),
                FreeClockActions(), session = {})
        }
    }

    /** `lx-A03`: la franja «Listo» entra en 320 ms (`lxrise`, `estandar`). */
    @Test fun framesBanner() = frames("lx-fr-banner", listOf(0, 80, 160, 240, 320)) {
        Box(Modifier.padding(top = 100.dp, start = 10.dp, end = 10.dp)) { FreeClockBanner(null, {}) }
    }

    /** `lx-A04…A11`: la entrada de la hoja de cierre (anillos, cuenta, línea de volumen, barras de músculos). */
    @Test fun framesFinishEntry() = frames("lx-fr-cierre", listOf(0, 400, 800, 1_200, 1_600, 2_400)) {
        val state = FinishSheetState(model = normal, dims = dims(), saving = false, errorMessage = null, blockedMessage = null, notes = "", photoCount = 0,
            shareAfterSave = false, panelOpen = false, checklist = emptyList(), effortSelected = null, storyThumb = null, ridges = rows, ridgeRecordIndex = -1)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.94f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF1B1B1D))) {
            FinishSheetContent(state, FinishSheetActions())
        }
    }

    // ── dibujo ─────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun draw(name: String, fontScale: Float = 1f, height: Dp = 880.dp, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale), LocalWizardReducedMotion provides true) {
                KPKNTheme { MaterialTheme { Box(Modifier.size(360.dp, height).background(Color.Black).testTag(TAG)) { content() } } }
            }
        }
        rule.waitForIdle()
        repeat(4) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        save(name)
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot
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
        FileOutputStream(File(dir, "$name.png")).use { Bitmap.createBitmap(full, left, top, width, height).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
