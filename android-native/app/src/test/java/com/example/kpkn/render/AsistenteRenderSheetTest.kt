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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.sessionassistant.AssistantActionEffect
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantNoticeKind
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantReport
import com.example.kpkn.domain.sessionassistant.SessionTotals
import com.example.kpkn.domain.sessionassistant.TimeCoachAction
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.domain.sessionassistant.TimeCoachSuggestion
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.sessioneditor.AssistantSuggestionOutcome
import com.example.kpkn.screens.sessioneditor.AssistantTab
import com.example.kpkn.screens.sessioneditor.RuleScope
import com.example.kpkn.screens.sessioneditor.RuleTemplateStore
import com.example.kpkn.screens.sessioneditor.RulesSection
import com.example.kpkn.screens.sessioneditor.SessionEditorRuleLimits
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.assistant.AsApplyModel
import com.example.kpkn.screens.sessioneditor.components.assistant.AsSheetSpec
import com.example.kpkn.screens.sessioneditor.components.assistant.LocalAsFrozenMs
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknRing
import com.example.kpkn.screens.sessioneditor.components.assistant.AsColors
import com.example.kpkn.screens.sessioneditor.components.assistant.AsDecisionModel
import com.example.kpkn.screens.sessioneditor.components.assistant.AsDuration
import com.example.kpkn.screens.sessioneditor.components.assistant.AsGroupMode
import com.example.kpkn.screens.sessioneditor.components.assistant.AsInter
import com.example.kpkn.screens.sessioneditor.components.assistant.AsMineRow
import com.example.kpkn.screens.sessioneditor.components.assistant.AsPart
import com.example.kpkn.screens.sessioneditor.components.assistant.AsPartLine
import com.example.kpkn.screens.sessioneditor.components.assistant.AsPlaView
import com.example.kpkn.screens.sessioneditor.components.assistant.AsRulesActions
import com.example.kpkn.screens.sessioneditor.components.assistant.AsSaveState
import com.example.kpkn.screens.sessioneditor.components.assistant.AsSyne
import com.example.kpkn.screens.sessioneditor.components.assistant.AsTabBadge
import com.example.kpkn.screens.sessioneditor.components.assistant.AsTemplateCard
import com.example.kpkn.screens.sessioneditor.components.assistant.AsTemplateDetail
import com.example.kpkn.screens.sessioneditor.components.assistant.AsTemplateFilters
import com.example.kpkn.screens.sessioneditor.components.assistant.AsText
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantMetricsPane
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantRulesPane
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantSheetFrame
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantSuggestionsPane
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantTemplatesPane
import com.example.kpkn.screens.sessioneditor.components.assistant.AsToast
import com.example.kpkn.screens.sessioneditor.components.assistant.buildClashBanner
import com.example.kpkn.screens.sessioneditor.components.assistant.buildFx
import com.example.kpkn.screens.sessioneditor.components.assistant.buildLimitStatuses
import com.example.kpkn.screens.sessioneditor.components.assistant.buildMetricsModel
import com.example.kpkn.screens.sessioneditor.components.assistant.buildSuggestionsModel
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
 * HOJA DE DIBUJO del ASISTENTE (pulido, paquete `as`; pasada A). Dibuja cada escena del lienzo con sus datos de muestra: las pestañas
 * Métricas, Sugerencias, Plantillas y Reglas, la pregunta Reemplazar o añadir, aplicando, Mis plantillas y guardar. Escribe
 * `as_<escena>_s100.png` (360 × 880, letra al 100 %), `as_<escena>_h740.png` (360 × 740) y `as_<escena>_s130.png` (360 × 880 al 130 %)
 * en `KPKN_RENDER_DIR`, sin movimiento, para compararlos con `node tools/compare.cjs`. No comprueba nada. Sin `KPKN_RENDER_DIR` se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xxhdpi", application = android.app.Application::class)
class AsistenteRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var fontScale by mutableStateOf(1f)
    private var frameHeight by mutableIntStateOf(880)
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    // ── Dibujo ───────────────────────────────────────────────────────────────

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
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

    /** Escena: [full] = hoja completa (93 %) o media (62 %); [scrollDp] desplaza el cuerpo antes de dibujar. */
    private fun render(id: String, full: Boolean, scrollDp: Int = 0, content: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
                LocalContentColor provides Color.White,
            ) {
                Box(Modifier.testTag("frame").width(360.dp).height(frameHeight.dp).background(Color.Black)) {
                    EditorBackdrop()
                    Box(Modifier.fillMaxSize().background(AsColors.scrim))
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height((frameHeight * (if (full) 0.93f else 0.62f)).dp)
                            .sheetSurface(),
                    ) { content() }
                }
            }
        }
        for ((suffix, height, scale) in listOf(Triple("s100", 880, 1f), Triple("h740", 740, 1f), Triple("s130", 880, 1.3f))) {
            rule.runOnUiThread { fontScale = scale; frameHeight = height }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            if (scrollDp > 0) {
                rule.onAllNodesScrollable().performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, -100_000f) }
                rule.waitForIdle()
                rule.onAllNodesScrollable().performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                    scrollBy(0f, scrollDp * rule.density.density)
                }
                rule.waitForIdle()
            }
            save("as_${id}_$suffix")
        }
    }

    /** `.as-sheet`: fondo `#1b1b1d`, esquinas de 28 y `box-shadow: 0 -14px 44px #000c, inset 0 1px #ffffff10` (as-S05, preset `hoja`), como la hoja de la app. */
    private fun Modifier.sheetSurface(): Modifier {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        return this.kpknRing(KpknShadow.Hoja.spec, shape).clip(shape).background(AsColors.sheet).kpknInset(KpknShadow.Hoja.spec, shape)
    }

    /** Congela el reloj de las animaciones del asistente y deja que el cambio llegue hasta el dibujo (varios cuadros alternados). */
    private fun settle() {
        rule.waitForIdle()
        repeat(4) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }

    private val frozenMs = androidx.compose.runtime.mutableFloatStateOf(0f)

    /**
     * FOTOGRAMAS (pasada B/C): la escena [id] con TODAS las animaciones del asistente paradas en cada instante de [instants] (ms desde que entra el
     * elemento), con el movimiento activado. Escribe `asf_<id>_t<ms>.png` (360 × 880, letra al 100 %) para compararlo con `node tools/shots.cjs shot as-<id> --t=<ms>`.
     */
    private fun renderFrames(id: String, instants: List<Int>, full: Boolean, content: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
                LocalWizardReducedMotion provides false,
                LocalAsFrozenMs provides frozenMs.floatValue,
                LocalContentColor provides Color.White,
            ) {
                Box(Modifier.testTag("frame").width(360.dp).height(880.dp).background(Color.Black)) {
                    EditorBackdrop()
                    Box(Modifier.fillMaxSize().background(AsColors.scrim))
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((880 * (if (full) 0.93f else 0.62f)).dp).sheetSurface(),
                    ) { content() }
                }
            }
        }
        for (ms in instants) {
            rule.runOnIdle { frozenMs.floatValue = ms.toFloat() }
            settle()
            save(String.format("asf_%s_t%05d", id, ms))
        }
    }

    @Test fun framesShimmer() = renderFrames("sug-carga", listOf(0, 350, 700, 1050, 1400), full = false) {
        Suggestions(state(report = report()), badge = null, loading = true)
    }

    @Test fun framesApplying() = renderFrames("pla-aplicando", listOf(0, 50, 100, 150, 200, 275, 550, 825, 1100), full = true) {
        Frame(AssistantTab.TEMPLATES) {
            Templates(view = AsPlaView.DETAIL, detail = true, applying = AsApplyModel("Aplicando «Leg Day · Cuádriceps»…", "Cambiando 6 ejercicios por los de la plantilla.", false))
        }
    }

    @Test fun framesDecision() = renderFrames("pla-aplicar", listOf(0, 90, 180, 270, 360), full = true) {
        Frame(AssistantTab.TEMPLATES) { Templates(view = AsPlaView.DETAIL, detail = true, decision = decisionModel) }
    }

    @Test fun framesMetrics() = renderFrames("met-completa", listOf(0, 175, 350, 525, 700), full = true) { Metrics(state(), open = "Glúteos") }

    @Test fun framesFlash() = renderFrames("reg-valores", listOf(0, 150, 300, 450, 600), full = true) { Rules(state(), RulesSection.VALUES) }

    @Test fun framesPane() = renderFrames("met-media", listOf(0, 65, 130, 195, 260), full = false) { Metrics(state()) }

    /** La hoja de verdad (`KpknSheet` con los valores del lienzo): velo, hoja y contenido entrando, con el reloj de cuadros real parado y avanzado a mano. */
    @Test fun framesSheetEntrance() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
                LocalWizardReducedMotion provides false,
                LocalContentColor provides Color.White,
            ) {
                Box(Modifier.testTag("frame").width(360.dp).height(880.dp).background(Color.Black)) {
                    EditorBackdrop()
                    com.example.kpkn.ui.components.KpknSheet(
                        onDismissRequest = {}, showDragHandle = false, maxHeightFraction = 0.93f, stableHeightFraction = 0.62f,
                        surfaceColor = AsColors.sheet, backdropScrim = AsColors.scrim, lienzo = AsSheetSpec,
                    ) { Metrics(state()) }
                }
            }
        }
        var elapsed = 0
        for (ms in listOf(0, 55, 110, 165, 220, 330, 460, 600)) {
            rule.mainClock.advanceTimeBy((ms - elapsed).toLong(), ignoreFrameDuration = true)
            elapsed = ms
            rule.waitForIdle()
            save(String.format("asf_entrada_t%05d", ms))
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesScrollable() =
        onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("frame"))).onFirst()

    /** `.as-bg`: el editor de fondo, atenuado detrás de la hoja. */
    @Composable
    private fun EditorBackdrop() {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 96.dp)) {
            AsText("Legs II", 34f, weight = FontWeight.ExtraBold, family = AsSyne, lineHeight = 1f, letterSpacing = -1f)
            AsText("Enfoque DL", 14f, Modifier.padding(top = 6.dp, bottom = 12.dp), color = AsColors.muted)
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("6 ejercicios · 18 series", "≈ 59 min").forEach {
                    Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        AsText(it, 12.5f, color = AsColors.muted)
                    }
                }
            }
            listOf("Sentadilla búlgara" to 4, "Peso muerto convencional" to 3, "Prensa de piernas" to 3).forEach { (name, sets) ->
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().height(66.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D1D1D)))
                    Column {
                        AsText(name, 14f, weight = FontWeight.SemiBold)
                        AsText("$sets series", 12f, color = AsColors.faint)
                    }
                }
            }
        }
    }

    // ── Datos de muestra (los del lienzo: «Legs II», 6 ejercicios, 18 series, 59 min, límite de tiempo 50) ─────────────────────

    private data class Ex(val id: String, val name: String, val sets: Int, val rpe: Double, val weights: Map<String, Double>, val force: String? = null)

    private val exercises = listOf(
        Ex("bulgara", "Sentadilla búlgara", 4, 8.0, mapOf("Cuádriceps" to 1.0, "Glúteos" to .5, "Aductores" to .5, "Core" to .3), "Empuje"),
        Ex("muerto", "Peso muerto convencional", 3, 8.5, mapOf("Glúteos" to 1.0, "Isquiosurales" to .5, "Erectores Espinales" to .4, "Antebrazo" to .4, "Core" to .3), "Bisagra"),
        Ex("prensa", "Prensa de piernas", 3, 7.5, mapOf("Cuádriceps" to 1.0, "Glúteos" to .5, "Aductores" to .5), "Empuje"),
        Ex("thrust", "Hip thrust", 4, 8.0, mapOf("Glúteos" to 1.0, "Isquiosurales" to .25, "Aductores" to .5, "Core" to .3), "Empuje"),
        Ex("curl", "Curl femoral tumbado", 2, 8.5, mapOf("Isquiosurales" to 1.0), "Tirón"),
        Ex("extension", "Extensión de cuádriceps", 2, 9.0, mapOf("Cuádriceps" to 1.0), "Empuje"),
    )

    private val index: Map<String, ExerciseMuscleInfo> = exercises.associate { ex ->
        ex.id to ExerciseMuscleInfo(
            id = ex.id, name = ex.name, force = ex.force,
            involvedMuscles = ex.weights.map { (muscle, w) -> InvolvedMuscle(muscle, if (w == 1.0) MuscleRole.PRIMARY else MuscleRole.SECONDARY, w) },
        )
    }

    private fun exercise(ex: Ex, id: String = ex.id) = Exercise(
        id = id, name = ex.name, exerciseDbId = ex.id,
        sets = List(ex.sets) { ExerciseSet(id = "$id-$it", targetReps = 10, targetRPE = ex.rpe, weight = 60.0) },
    )

    private val cardioExercise = Exercise(
        id = "cardio", name = "Cinta de correr",
        sets = listOf(ExerciseSet(id = "c0", targetDuration = 900)),
        cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.entries.first(), targetDurationSeconds = 900),
    )

    private fun session(target: Int? = 50, cardio: Boolean = false) = Session(
        id = "legs", name = "Legs II", targetDurationMinutes = target, lastModifiedAtMs = 5L,
        parts = listOf(
            SessionPart("p1", "Compuestos", exercises = exercises.take(4).map { exercise(it) }, targetDurationMinutes = 22),
            SessionPart("p2", "Aislamientos", exercises = exercises.drop(4).map { exercise(it) } + if (cardio) listOf(cardioExercise) else emptyList(), targetDurationMinutes = 20),
        ),
    )

    /** Otra sesión de la semana que suma 12 series de Glúteos (la semana lleva 22,5). */
    private val otherSession = Session(id = "otra", name = "Glúteo", exercises = listOf(exercise(exercises[3], "thrust-otra").let { it.copy(sets = List(12) { i -> ExerciseSet(id = "o$i", targetReps = 10, weight = 60.0) }) }))

    private fun breakdown(total: Int) = when (total) {
        59 -> SessionTimeBreakdown(setupSeconds = 360, executionSeconds = 1260, restSeconds = 1740, warmupSeconds = 180, totalSeconds = 3540, exerciseCount = 6, totalSetCount = 18)
        74 -> SessionTimeBreakdown(setupSeconds = 360, executionSeconds = 2160, restSeconds = 1740, warmupSeconds = 180, totalSeconds = 4440, exerciseCount = 7, totalSetCount = 18)
        else -> SessionTimeBreakdown(
            setupSeconds = 360, executionSeconds = (total * 60 - 360 - 1740 - 180).coerceAtLeast(0), restSeconds = 1740, warmupSeconds = 180,
            totalSeconds = total * 60, exerciseCount = 6, totalSetCount = 18,
        )
    }

    private val limits = SessionEditorRuleLimits(maxRPE = 9.0, maxExercisesPerMuscle = 6, maxVolumePerMuscleSession = 10.0, maxVolumePerMuscleWeekly = 24.0, maxSamePatternPerSession = 4, rigidLimits = true)

    private fun state(
        session: Session = session(),
        total: Int = 59,
        limits: SessionEditorRuleLimits = this.limits,
        report: SessionAssistantReport? = null,
        outcomes: List<AssistantSuggestionOutcome> = emptyList(),
        coach: List<TimeCoachSuggestion> = emptyList(),
    ) = SessionEditorUiState(
        session = session, weekSessions = listOf(session, otherSession), sessionTimeBreakdown = breakdown(total), estimatedDurationMinutes = total,
        ruleLimits = limits, ruleTemplates = RuleTemplateStore.factoryPresets(), assistantReport = report, assistantOutcomes = outcomes, timeCoachSuggestions = coach,
    )

    private fun effect(seconds: Int, sets: Int = 0) = AssistantActionEffect(deltaSeconds = -seconds, deltaSets = -sets)

    private val limitsCard = AssistantSuggestion(
        id = "user_limits", type = AssistantActionType.COMPOSITE, title = "Dentro de tus límites",
        message = "Pusiste límites rígidos y esta sesión los supera: Glúteos supera tu límite de series efectivas por sesión.",
        kind = AssistantNoticeKind.USER_LIMIT,
        details = listOf(AssistantSuggestionDetail("limit_detail_volume_prensa", "Quitar 1 serie de Prensa de piernas", AssistantDetailAction.ReduceSet(exerciseId = "prensa"), true, effect(120, 1))),
    )

    private val timeCard = AssistantSuggestion(
        id = "time_overage", type = AssistantActionType.COMPOSITE, title = "Ajustar al tiempo que fijaste",
        message = "La estimación (59 min) supera tus 50 min en 9 min.", kind = AssistantNoticeKind.USER_LIMIT,
        details = listOf(
            AssistantSuggestionDetail("time_detail_rest", "Bajar 15 s de descanso entre series en 2 accesorios", AssistantDetailAction.ReduceRest(15, listOf("curl", "extension")), true, effect(120)),
            AssistantSuggestionDetail("time_detail_superset_curl", "Unir Curl femoral tumbado con Extensión de cuádriceps en superserie", AssistantDetailAction.ConvertToSuperset("curl"), false, effect(180)),
            AssistantSuggestionDetail("time_detail_reduce_extension", "Quitar 1 serie de Extensión de cuádriceps", AssistantDetailAction.ReduceSet(exerciseId = "extension"), false, effect(120, 1)),
        ),
    )

    private fun report(vararg cards: AssistantSuggestion, notes: List<String> = emptyList(), minutes: Int = 59) = SessionAssistantReport(
        veredicto = null, scoreEstimado = null, riesgos = emptyList(), ajustes = cards.toList(), oportunidades = emptyList(),
        tarjetasFantasma = emptyList(), plantillasCompatibles = emptyList(), volumenPorMusculo = emptyMap(), umbralesPorMusculo = emptyMap(),
        duracionEstimada = minutes, resumenTexto = "", notas = notes,
    )

    private val coach = listOf(
        TimeCoachSuggestion("c1", "Recortar descansos", "Recorta los descansos de monoarticulares (3) y de multiarticulares (2), pero la sesión no llega al límite.", 4, TimeCoachFatigueDelta.SIMILAR, TimeCoachAction.RemoveExercise("curl")),
        TimeCoachSuggestion("c2", "Superserie: Curl femoral tumbado + Extensión de cuádriceps", "Empareja estaciones compatibles o funciones distintas para recortar descansos muertos.", 3, TimeCoachFatigueDelta.SIMILAR, TimeCoachAction.RemoveExercise("extension")),
    )

    // ── Plantillas del lienzo ────────────────────────────────────────────────

    private fun style(name: String) = when (name) {
        "relieve" -> LineCoverStyle.RELIEVE; "perfil" -> LineCoverStyle.PERFIL; "espiral" -> LineCoverStyle.ESPIRAL; "ondas" -> LineCoverStyle.ONDAS
        "campo" -> LineCoverStyle.CAMPO; "orbitas" -> LineCoverStyle.ORBITAS; else -> LineCoverStyle.BRUMA
    }

    private fun background(name: String) = when (name) {
        "carbon" -> LineCoverBackground.CARBON; "musgo" -> LineCoverBackground.MUSGO; "grafito" -> LineCoverBackground.GRAFITO; else -> LineCoverBackground.NOCHE
    }

    private fun card(id: String, g: String, n: String, sh: String, min: Int, ex: Int, sets: Int, lv: Int, mus: String, ty: List<String>, sp: String, st: String, bg: String, seed: Int) =
        AsTemplateCard(id, n, sh, min, ex, sets, lv, mus, ty.toSet(), g, sp, "$n $sh $mus".lowercase(), LineCoverSpec(style(st), background(bg), seed))

    private val cards = listOf(
        card("leg-quad", "Pierna", "Leg Day · Cuádriceps", "Tren inferior énfasis cuádriceps", 35, 6, 17, 1, "Cuádriceps · Isquios · Glúteos · Aductores", listOf("Piernas"), "Upper/Lower", "relieve", "carbon", 3),
        card("leg-hinge", "Pierna", "Leg Day · Isquios", "Tren inferior énfasis isquiosurales", 35, 6, 17, 1, "Isquios · Glúteos · Aductores · Pantorrillas", listOf("Piernas"), "Upper/Lower", "relieve", "musgo", 8),
        card("legs-full", "Pierna", "Legs Day · Completo", "Tren inferior completo", 36, 7, 18, 1, "Cuádriceps · Isquios · Glúteos · Pantorrillas", listOf("Piernas"), "PPL", "perfil", "carbon", 5),
        card("glute-isq", "Pierna", "Glúteo / Isquios", "Tren inferior cadena posterior glúteo", 35, 4, 16, 1, "Glúteos · Isquiosurales", listOf("Piernas", "Glúteos"), "Glúteo", "espiral", "carbon", 2),
        card("glute-pump", "Pierna", "Glúteo Pump", "Bombeo estético de glúteos", 35, 4, 13, 1, "Glúteos · Pantorrillas", listOf("Piernas", "Glúteos"), "Glúteo", "ondas", "grafito", 6),
        card("hf-pierna", "Pierna", "Pierna · Dosis compacta", "Máquinas y poleas comunes", 16, 4, 8, 1, "Piernas", listOf("Piernas"), "Dosis compacta", "campo", "grafito", 9),
        card("hf-posterior", "Pierna", "Posterior · Dosis compacta", "Máquinas y poleas comunes", 18, 4, 8, 1, "Cadena posterior", listOf("Piernas", "Glúteos"), "Dosis compacta", "campo", "musgo", 4),
        card("push", "Torso", "Push Day · PPL", "Empuje hipertrofia clásico", 35, 5, 16, 1, "Pecho · Hombros · Tríceps", listOf("Torso", "Pecho", "Hombros"), "PPL", "ondas", "noche", 7),
        card("pull", "Torso", "Pull Day · PPL", "Tracciones hipertrofia clásica", 35, 6, 16, 1, "Espalda · Bíceps · Romboides", listOf("Torso", "Espalda"), "PPL", "campo", "noche", 11),
        card("torso-a", "Torso", "Torso A · Upper/Lower", "Upper balanceado pecho y espalda", 36, 7, 18, 1, "Pecho · Espalda · Hombros · Bíceps · Tríceps", listOf("Torso", "Pecho", "Espalda"), "Upper/Lower", "perfil", "noche", 12),
        card("pecho-esp", "Torso", "Pecho / Espalda", "Pecho y espalda antagonistas", 36, 5, 14, 2, "Pecho · Espalda", listOf("Torso", "Pecho", "Espalda"), "Antagonistas", "orbitas", "noche", 13),
        card("torso-liv", "Torso", "Torso Liviano", "Mantenimiento torso fatiga mínima", 35, 5, 19, 0, "Pecho · Espalda · Hombros · Brazos", listOf("Torso"), "Upper/Lower", "bruma", "grafito", 14),
        card("hombro-brazo", "Brazos", "Hombro / Brazo", "Hombros y brazos estética", 35, 6, 20, 1, "Hombros · Bíceps · Tríceps", listOf("Brazos", "Hombros"), "Antagonistas", "espiral", "grafito", 15),
        card("fb-base", "Full body", "Full Body · Base", "Cuerpo completo base principiante", 35, 6, 16, 0, "Cuerpo completo", listOf("Full body"), "Full body", "orbitas", "musgo", 16),
        card("minimal", "Full body", "Minimalista · Fuerza", "Entrenamiento de fuerza minimalista", 36, 4, 13, 1, "Cuerpo completo", listOf("Full body"), "Full body", "relieve", "grafito", 17),
        card("sbd", "Full body", "SBD · Powerlifting", "SBD competitivo volumen moderado", 41, 6, 14, 2, "Sentadilla · Banca · Peso muerto", listOf("Full body"), "Powerlifting", "perfil", "carbon", 18),
    )

    private fun line(name: String, sets: Int, reps: Int, rpe: Double, rest: Int) = AsPartLine(name, sets, reps, rpe, rest)

    private val legQuadDetail = AsTemplateDetail(
        card = cards.first(),
        description = "Día de Pierna con énfasis en Cuádriceps: sentadilla, bisagra, aislamientos, aductores y gemelos en patrón A-B-A-B.",
        parts = listOf(
            AsPart("Compuestos", listOf(line("Sentadilla con barra alta", 3, 6, 8.0, 180), line("Peso muerto rumano con barra", 2, 8, 8.0, 150))),
            AsPart("Aislamientos", listOf(
                line("Extensión de cuádriceps en máquina", 2, 12, 8.5, 90), line("Curl de isquios con sliders", 2, 12, 8.5, 90),
                line("Aducción sentado en máquina", 4, 12, 8.5, 75), line("Elevación de talones en máquina", 4, 15, 8.5, 60),
            )),
        ),
        volume = listOf("Aductores" to 5.5, "Cuádriceps" to 5.0, "Isquiosurales" to 4.0, "Pantorrillas" to 4.0, "Glúteos" to 2.5, "Erectores Espinales" to 0.6),
    )

    private val mine = listOf(
        AsMineRow("m1", "Pierna pesada martes", "", 1, 6, false),
        AsMineRow("m2", "Torso rápido 30", "", 1, 5, false),
        AsMineRow("m3", "Brazos viejo", "", 1, 4, true),
    )

    @Composable
    private fun Templates(
        view: AsPlaView = AsPlaView.LIST,
        detail: Boolean = false,
        filters: AsTemplateFilters = AsTemplateFilters(),
        panel: Boolean = false,
        decision: AsDecisionModel? = null,
        applying: AsApplyModel? = null,
        corrupt: Int = 0,
        menu: Int? = null,
        save: AsSaveState = AsSaveState.IDLE,
    ) = AssistantTemplatesPane(
        ready = true, cards = cards, mineRows = mine, corruptCount = corrupt, query = filters.query, onQueryChange = {},
        detailFor = { if (it == "leg-quad") legQuadDetail else null }, sessionName = "Legs II", sessionSummary = "6 ejercicios, 18 series, ≈59 min",
        decision = decision, applying = applying, onApplyTemplate = {}, onConfirmDecision = {}, onCancelDecision = {}, onFinishApply = {}, onUndoApply = {},
        onArchive = {}, onRestore = {}, onDelete = {}, onEdit = { _, _, _, _ -> true }, onSave = { _, _ -> true },
        initialView = view, initialDetailId = if (detail) "leg-quad" else null, initialFilters = filters, initialPanelOpen = panel, initialMenuIndex = menu, initialSaveState = save,
    )

    private val decisionModel = AsDecisionModel("Leg Day · Cuádriceps", SessionTotals(6, 17, 35), SessionTotals(12, 35, 94), 44, SessionTemplateApplyMode.REPLACE)

    // ── Marco de una escena ──────────────────────────────────────────────────

    @Composable
    private fun Frame(
        tab: AssistantTab,
        badges: Map<AssistantTab, AsTabBadge> = mapOf(AssistantTab.SUGGESTIONS to AsTabBadge.Count(2), AssistantTab.RULES to AsTabBadge.Dot),
        toast: Pair<String, String?>? = null,
        body: @Composable () -> Unit,
    ) {
        Box(Modifier.fillMaxSize()) {
            AssistantSheetFrame(sessionName = "Legs II", tab = tab, badges = badges, onTab = {}, onGrab = {}, body = body)
            toast?.let { AsToast(it.first, it.second, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp)) }
        }
    }

    @Composable
    private fun Metrics(s: SessionEditorUiState, open: String? = null, all: Boolean = false) = Frame(AssistantTab.METRICS) {
        AssistantMetricsPane(buildMetricsModel(s, index), onGoTemplates = {}, onGoTimeRules = {}, initialOpenMuscle = open, initialShowAll = all)
    }

    @Composable
    private fun Suggestions(s: SessionEditorUiState, badge: Int?, minutes: Int = 59, loading: Boolean = false) {
        val shown = if (loading) s.copy(assistantReport = null) else s
        val model = buildSuggestionsModel(shown, null)
        Frame(AssistantTab.SUGGESTIONS, badges = if (badge == null) emptyMap() else mapOf(AssistantTab.SUGGESTIONS to AsTabBadge.Count(badge), AssistantTab.RULES to AsTabBadge.Dot)) {
            AssistantSuggestionsPane(
                model = model, fxFor = { suggestion, accepted -> buildFx(shown, suggestion, accepted, index) },
                onApply = { _, _, _ -> }, onDismiss = { _, _ -> }, onUndo = {}, onGoTimeRules = {}, onGoLimitRules = {},
            )
        }
    }

    @Composable
    private fun Rules(s: SessionEditorUiState, section: RulesSection, scope: RuleScope? = null, badges: Map<AssistantTab, AsTabBadge>? = null) {
        val statuses = buildLimitStatuses(s, index)
        Frame(AssistantTab.RULES, badges = badges ?: mapOf(AssistantTab.SUGGESTIONS to AsTabBadge.Count(2), AssistantTab.RULES to AsTabBadge.Dot)) {
            AssistantRulesPane(
                uiState = s, actions = AsRulesActions.None, limitStatuses = statuses, clashBanner = buildClashBanner(s, index),
                section = section, onSection = {}, onGoSuggestions = {}, onToast = { _, _ -> }, onApplied = {}, initialScope = scope,
            )
        }
    }

    // ── Las 25 escenas del paquete ───────────────────────────────────────────

    @Test fun entrada() = render("entrada", full = false) { Metrics(state(), null) }
    @Test fun metMedia() = render("met-media", full = false) { Metrics(state()) }
    @Test fun metCompleta() = render("met-completa", full = true) { Metrics(state(), open = "Glúteos") }
    @Test fun metTodos() = render("met-todos", full = true, scrollDp = 360) { Metrics(state(), all = true) }
    @Test fun metCardio() = render("met-cardio", full = true) { Metrics(state(session(cardio = true), total = 74)) }
    @Test fun metVacia() = render("met-vacia", full = false) {
        Frame(AssistantTab.METRICS, badges = emptyMap()) {
            AssistantMetricsPane(buildMetricsModel(state(Session(id = "v", name = "Legs II")), index), onGoTemplates = {}, onGoTimeRules = {})
        }
    }

    @Test fun sugLista() = render("sug-lista", full = true) { Suggestions(state(report = report(limitsCard, timeCard)), badge = 2) }
    @Test fun sugAplicada() = render("sug-aplicada", full = true) {
        val s = state(
            report = report(limitsCard, timeCard),
            outcomes = listOf(AssistantSuggestionOutcome("time_overage", AssistantSuggestionOutcome.Kind.APPLIED, "1 cambio · −2 min", null, com.example.kpkn.data.models.WeekVariant.A, 5L, 1)),
        )
        Suggestions(s, badge = 1)
    }
    @Test fun sugVacio() = render("sug-vacio", full = false) { Suggestions(state(session(target = 50), total = 47, report = report(minutes = 47), limits = SessionEditorRuleLimits()), badge = null) }
    @Test fun sugNota() = render("sug-nota", full = false) {
        Suggestions(state(report = report(notes = listOf("La estimación (51 min) supera tus 50 min en 1 min. Es una diferencia pequeña: no hay ajuste que proponer."))), badge = null)
    }
    @Test fun sugCarga() = render("sug-carga", full = false) { Suggestions(state(report = report()), badge = null, loading = true) }

    @Test fun plaCatalogo() = render("pla-catalogo", full = true) { Frame(AssistantTab.TEMPLATES) { Templates() } }
    @Test fun plaFiltros() = render("pla-filtros", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(filters = AsTemplateFilters(type = "Piernas", level = 1), panel = true) } }
    @Test fun plaVacio() = render("pla-vacio", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(filters = AsTemplateFilters(duration = AsDuration.MEDIUM)) } }
    @Test fun plaVista() = render("pla-vista", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(view = AsPlaView.DETAIL, detail = true) } }
    @Test fun plaAplicar() = render("pla-aplicar", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(view = AsPlaView.DETAIL, detail = true, decision = decisionModel) } }
    @Test fun plaAplicando() = render("pla-aplicando", full = true) {
        Frame(AssistantTab.TEMPLATES) {
            Templates(view = AsPlaView.DETAIL, detail = true, applying = AsApplyModel("Aplicando «Leg Day · Cuádriceps»…", "Cambiando 6 ejercicios por los de la plantilla.", false))
        }
    }
    @Test fun plaAplicada() = render("pla-aplicada", full = true) {
        Frame(AssistantTab.TEMPLATES) {
            Templates(view = AsPlaView.DETAIL, detail = true, applying = AsApplyModel("Plantilla aplicada", "Reemplazaste 6 ejercicios por 6: 17 series, ≈35 min.", true))
        }
    }
    @Test fun plaMias() = render("pla-mias", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(view = AsPlaView.MINE, corrupt = 1, menu = 0) } }
    @Test fun plaGuardar() = render("pla-guardar", full = true) { Frame(AssistantTab.TEMPLATES) { Templates(view = AsPlaView.SAVE, save = AsSaveState.ERROR) } }

    @Test fun regValores() = render("reg-valores", full = true) { Rules(state(), RulesSection.VALUES) }
    @Test fun regCompuestos() = render("reg-compuestos", full = true) { Rules(state(), RulesSection.VALUES, scope = RuleScope.COMPOUND_ISOLATION) }
    @Test fun regLimites() = render("reg-limites", full = true) { Rules(state(), RulesSection.LIMITS) }
    @Test fun regLimitesVacio() = render("reg-limites-vacio", full = false) { Rules(state(limits = SessionEditorRuleLimits()), RulesSection.LIMITS, badges = emptyMap()) }
    @Test fun regTiempo() = render("reg-tiempo", full = true) { Rules(state(coach = coach), RulesSection.TIME) }
}
