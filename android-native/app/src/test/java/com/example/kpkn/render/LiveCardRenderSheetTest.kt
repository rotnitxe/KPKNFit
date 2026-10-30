package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.ExerciseOptionsBar
import com.example.kpkn.screens.sessioneditor.components.rememberExerciseEditorLocalState
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.LiveExerciseHistorySheet
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.buildEffortScale
import com.example.kpkn.screens.workout.components.LiveEffortRange
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.RestLiveCard
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FRAME_TAG = "lv-frame"

/**
 * HOJA DE RENDER del paquete TARJETA EN VIVO (`lv`, pestaña «En vivo · tarjeta»): dibuja las 16 escenas del lienzo con la pieza REAL
 * (`SetInputCardV2`, `LiveEffortRange`, `LiveSetDetailSheet`, `ExerciseOptionsBar`, `LiveExerciseHistorySheet`, `RestLiveCard`) y los
 * datos de muestra del lienzo, en un marco de 360 × 880 dp con la tarjeta de 312 × 430 en el sitio del lienzo (x = 24, y = 238). Escribe
 * el marco entero como PNG de 1080 px de ancho en `KPKN_RENDER_DIR` (`<escena>.png`; `_740` y `_130` en las variantes densas); la
 * herramienta `tools/compare.cjs` del lienzo lo compara recortado al rectángulo de la tarjeta o de la hoja. Sin `KPKN_RENDER_DIR` se
 * salta. Pasada A: sin sombras, vidrio ni animaciones definitivas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveCardRenderSheetTest {
    private val room = RoomTestRule()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(room).around(compose)
    private val rule get() = compose

    private var view: View? = null
    private val holder = RecordActionHolder()

    // ── Datos de muestra del lienzo («Legs II · Semana 1», peso muerto) ─────────────────────────────

    private fun serie(
        range: RepRange? = RepRange(4, 6),
        reps: Int? = null,
        purpose: SetPurpose = SetPurpose.NONE,
        amrapMin: Int? = null,
        unit: UnitModeV2 = UnitModeV2.REPS,
        load: LoadModeV2 = LoadModeV2.LOAD,
        weight: Double? = 120.0,
        duration: Int? = null,
    ) = ExerciseSet(
        id = "pm-1", targetReps = reps, targetRepsRange = range, targetRPE = 8.0, intensityMode = IntensityMode.RPE, weight = weight,
        loadModeV2 = load, unitModeV2 = unit, purpose = purpose, amrapMinimumReps = amrapMin, targetDuration = duration,
    )

    private fun ejercicio(set: ExerciseSet, trackRom: Boolean, name: String) = Exercise(
        id = "pm", name = name, trainingMode = TrainingMode.REPS,
        sets = List(3) { set.copy(id = "pm-${it + 1}") }, trackRom = trackRom,
    )

    private val anterior = CompletedSet(id = "g", weight = 120.0, reps = 5, rir = 2)
    private val anteriorRom = CompletedSet(id = "g", weight = 120.0, reps = 5, rom = 100)

    // ── Escenas: repeticiones ────────────────────────────────────────────────────────────────────

    @Test fun repsRango() = tarjeta("lv-reps-rango", serie(), draft = WorkoutSetDraft(valueText = "5"))
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun repsRango740() =
        tarjeta("lv-reps-rango_740", serie(), draft = WorkoutSetDraft(valueText = "5"), alto = 740)
    @Test fun repsRango130() = tarjeta("lv-reps-rango_130", serie(), draft = WorkoutSetDraft(valueText = "5"), fontScale = 1.3f)
    @Test fun repsFijo() = tarjeta("lv-reps-fijo", serie(range = null, reps = 5), draft = WorkoutSetDraft(valueText = "4"))
    @Test fun repsAmrap() = tarjeta("lv-reps-amrap", serie(range = null, purpose = SetPurpose.AMRAP, amrapMin = 8),
        draft = WorkoutSetDraft(valueText = "11"), ghost = CompletedSet(id = "g", weight = 100.0, reps = 12, rir = 0))
    @Test fun repsFallo() = tarjeta("lv-reps-fallo", serie(),
        completed = CompletedSet(id = "pm_0", weight = 120.0, reps = 0, isFailedSet = true))

    // ── Escenas: esfuerzo ────────────────────────────────────────────────────────────────────────

    @Test fun esfuerzoVivo() = tarjeta("lv-esf-vivo", serie(), draft = WorkoutSetDraft(valueText = "5"))
    @Test fun esfuerzoFallo() = tarjeta("lv-esf-fallo", serie(), draft = WorkoutSetDraft(valueText = "5", reachedFailure = true))
    @Test fun esfuerzoFallo130() = tarjeta("lv-esf-fallo_130", serie(), draft = WorkoutSetDraft(valueText = "5", reachedFailure = true), fontScale = 1.3f)
    @Test fun esfuerzoReducido() = tarjeta("lv-esf-reducido", serie(), draft = WorkoutSetDraft(valueText = "5", intensityText = "9.5"))

    /** Los seis estados del slider, uno bajo otro (la escena «escala de calor» es un catálogo: en la app es el mismo slider de la tarjeta). */
    @Test fun esfuerzoEscala() = escena("lv-esf-escala") {
        val scale = buildEffortScale(IntensityMode.RPE, IntensityMode.RPE, null, 8.0)
        val items = scale.items
        fun at(rpe: Double) = items.indexOfFirst { !it.isFailure && it.numericValue == rpe }
        Column(Modifier.offset(16.dp, 112.dp).width(328.dp).background(Color(0xFF111111), RoundedCornerShape(24.dp)).padding(16.dp, 6.dp, 16.dp, 10.dp)) {
            listOf(at(6.0), at(7.0), at(8.0), at(9.0), at(10.0), items.indexOfFirst { it.isFailure }).forEach { index ->
                LiveEffortRange(scale = scale, selectedIndex = index, onSelect = {}, compact = true)
            }
        }
    }

    // ── Escenas: ROM ─────────────────────────────────────────────────────────────────────────────

    @Test fun romEditor() = escena("lv-rom-editor", abrir = { rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick() }) {
        val exercise = ejercicio(serie(), trackRom = false, name = "Peso Muerto Convencional")
        Box(Modifier.offset(16.dp, 120.dp).width(328.dp)) {
            ExerciseOptionsBar(exercise = exercise, local = rememberExerciseEditorLocalState(exercise), accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false, suppressIndividualRest = false, relationshipAnchorName = null,
                onUpdateExercise = {}, onOpenSuperset = {}, onOpenRelationshipPicker = {}, onClearRelationship = {},
                onOpenCustomUnit = {}, onOpenSmartLoad = {}, onOpenGoal = {}, onOpenRest = {})
        }
    }

    @Test fun romTarjeta() = tarjeta("lv-rom-tarjeta", serie(), trackRom = true,
        draft = WorkoutSetDraft(valueText = "4", rom = 80, partialReps = 1), ghost = anteriorRom)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun romTarjeta740() = tarjeta("lv-rom-tarjeta_740", serie(), trackRom = true,
        draft = WorkoutSetDraft(valueText = "4", rom = 80, partialReps = 1), ghost = anteriorRom, alto = 740)
    @Test fun romTarjeta130() = tarjeta("lv-rom-tarjeta_130", serie(), trackRom = true,
        draft = WorkoutSetDraft(valueText = "4", rom = 80, partialReps = 1), ghost = anteriorRom, fontScale = 1.3f)
    @Test fun romCorporal() = tarjeta("lv-rom-corporal", serie(range = RepRange(6, 8), load = LoadModeV2.BODYWEIGHT, weight = null), trackRom = true,
        draft = WorkoutSetDraft(valueText = "7", rom = 70), ghost = null, ejercicioNombre = "Dominadas", pesoCorporal = 82.0)
    @Test fun romTiempo() = tarjeta("lv-rom-tiempo",
        serie(range = null, unit = UnitModeV2.TIME, load = LoadModeV2.BODYWEIGHT, weight = null, duration = 45),
        trackRom = true, draft = WorkoutSetDraft(rom = 90), ghost = null, ejercicioNombre = "Sentadilla isométrica", pesoCorporal = 82.0)
    @Test fun romHoja() = tarjeta("lv-rom-hoja", serie(), draft = WorkoutSetDraft(valueText = "4"), abrir = "live-card-detail")
    @Test fun romHojaOn() = tarjeta("lv-rom-hoja-on", serie(), trackRom = true, draft = WorkoutSetDraft(valueText = "4", rom = 80, partialReps = 1),
        abrir = "live-card-detail-open")
    @Test fun romHojaOn130() = tarjeta("lv-rom-hoja-on_130", serie(), trackRom = true,
        draft = WorkoutSetDraft(valueText = "4", rom = 80, partialReps = 1), abrir = "live-card-detail-open", fontScale = 1.3f)

    @Test fun romDescanso() = escena("lv-rom-descanso") {
        val last = CompletedSet(id = "last", weight = 120.0, reps = 4, rpe = 8.0, rom = 80)
        Box(Modifier.offset(24.dp, 238.dp).size(312.dp, 430.dp)) {
            RestLiveCard(
                remainingSeconds = 161, totalSeconds = 220, sessionAccentColor = Color(0xFFF49A6E),
                restState = WorkoutRestModalState(exerciseId = "pm", exerciseName = "Peso Muerto Convencional", kind = RestTimerKind.STANDARD,
                    plannedSeconds = 220, suggestedSeconds = 220, activeSeconds = 220),
                pendingRestSuggestion = PendingRestSuggestion(220, 220, "Peso Muerto Convencional", "pm", last, null),
                lastSetOutcome = null, lastCompletedSet = last,
                onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onExpand = {},
                nextSet = RestNextSetSummary(setNumber = 2, setTotal = 3, loadKg = 120.0, repsText = "5"), clockActive = false,
            )
        }
    }

    @Test fun romHistorial() = tarjeta("lv-rom-historial", serie(), draft = WorkoutSetDraft(valueText = "5"), extra = {
        val hoy = LocalDate.now()
        LiveExerciseHistorySheet(
            exerciseName = "Peso Muerto Convencional", activeTag = null,
            history = listOf(
                ExerciseHistoryEntry(hoy.minusDays(3).toString(), listOf(
                    CompletedSet(id = "a", weight = 120.0, reps = 5, rir = 2),
                    CompletedSet(id = "b", weight = 120.0, reps = 4, rir = 2, rom = 80),
                    CompletedSet(id = "c", weight = 120.0, reps = 5, rir = 1)), e1rm = null),
                ExerciseHistoryEntry(hoy.minusDays(7).toString(), listOf(CompletedSet(id = "d", weight = 117.5, reps = 5, rpe = 8.0)), e1rm = null),
                ExerciseHistoryEntry(hoy.minusDays(14).toString(), listOf(CompletedSet(id = "e", weight = 115.0, reps = 5, rpe = 8.5)), e1rm = null),
            ),
            onUseWeight = {}, onDismiss = {},
        )
    }, despues = { rule.onAllNodesWithTag("live-history-line")[0].performClick() })

    // ── Maquinaria ───────────────────────────────────────────────────────────────────────────────

    /** La tarjeta REAL en su sitio del lienzo; [abrir] pulsa la etiqueta indicada (una hoja) y [extra] pone algo más en el marco. */
    private fun tarjeta(
        nombre: String,
        set: ExerciseSet,
        trackRom: Boolean = false,
        draft: WorkoutSetDraft? = null,
        ghost: CompletedSet? = anterior,
        completed: CompletedSet? = null,
        fontScale: Float = 1f,
        alto: Int = 880,
        abrir: String? = null,
        pesoCorporal: Double = 80.0,
        ejercicioNombre: String = "Peso Muerto Convencional",
        extra: (@Composable () -> Unit)? = null,
        despues: (() -> Unit)? = null,
    ) {
        escena(nombre, fontScale = fontScale, alto = alto, abrir = abrir?.let { tag -> { rule.onNodeWithTag(tag).performClick(); Unit } }, despues = despues) {
            Box(Modifier.offset(24.dp, (if (alto == 740) 200 else 238).dp).size(312.dp, (if (alto == 740) 394 else 430).dp)) {
                SetInputCardV2(
                    exercise = ejercicio(set, trackRom, ejercicioNombre), setIndex = 0, currentSet = set, ghostSet = ghost,
                    sessionCompletedSet = completed,
                    sessionAccentColor = Color(0xFFF49A6E),
                    initialBodyWeight = pesoCorporal, recordActionHolder = holder, recordActionPageKey = "lv-render",
                    initialDraft = draft, onShowHistory = {}, onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
            extra?.invoke()
        }
    }

    private fun escena(
        nombre: String,
        fontScale: Float = 1f,
        alto: Int = 880,
        abrir: (() -> Unit)? = null,
        despues: (() -> Unit)? = null,
        contenido: @Composable () -> Unit,
    ) {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", !System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        rule.setContent {
            view = LocalView.current
            val host = rememberKpknOverlayHostController()
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides host, LocalRestClockFrozen provides 1.7f,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.requiredSize(360.dp, alto.dp).background(Color.Black).testTag(FRAME_TAG)) {
                            contenido()
                            KpknOverlayHostContent(host)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        abrir?.let {
            it()
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
        }
        despues?.let {
            it()
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
        }
        guardar(nombre)
    }

    /** Guarda el marco entero (`lv-frame`) como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(nombre: String) {
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag(FRAME_TAG).fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0)
        val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left),
            bounds.height.toInt().coerceAtMost(full.height - top))
        FileOutputStream(File(folder, "$nombre.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle(); full.recycle()
    }
}
