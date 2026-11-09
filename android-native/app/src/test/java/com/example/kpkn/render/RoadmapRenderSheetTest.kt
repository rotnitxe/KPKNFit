package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.LiveSessionLine
import com.example.kpkn.screens.workout.LiveSetStepperSnapshot
import com.example.kpkn.screens.workout.TimelineElement
import com.example.kpkn.screens.workout.WorkoutSetCardVisualState
import com.example.kpkn.screens.workout.components.LiveRouteSheetContent
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.liveRoadmapVariantFor
import com.example.kpkn.screens.workout.workoutSetKey
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * HOJA DE RENDER del ROADMAP de la sesión en vivo (Pulido v1, paquete `lv`, escenas `lv-road-*`).
 * Dibuja con los datos de muestra del lienzo (7 ejercicios de 3 series y el cardio; 18 series en total) cada escena: A «Calma» (ventana de
 * menos de 820 dp: `h760`) y B «Con ruta visible» (880 dp), la «Ruta» y las medidas. Los PNG de 1080 × 2640 (360 × 880 dp a xxhdpi) van a
 * `KPKN_RENDER_DIR` con el id de la escena y se comparan con `node tools/compare.cjs <lienzo.png> <app.png>`. Las cifras de dp salen de las
 * pruebas de medidas. También escribe los fotogramas (0, 25, 50, 75 y 100 %) de «Registrar serie», de la tira al deslizar y del descanso.
 * El vidrio no existe en esta hoja (el roadmap es plano) y las sombras se miden aquí tal como las pinta el modificador de profundidad.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class RoadmapRenderSheetTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule val rule = createComposeRule()
    private var view: View? = null
    private var density = 1f

    // ---------------------------------------------------------------------------------------------------------------- datos de muestra
    private data class Ex(val name: String, val sets: Int = 3, val cardio: Boolean = false, val group: String? = null)

    private class Scene(
        val exs: List<Ex>,
        val ex: Int,
        val cur: Int,
        val reg: List<String>,
        val rest: Pair<String, Float>? = null,
        val prep: Boolean = false,
        val pend: List<String> = emptyList(),
        val label: String? = null,
        val update: Boolean = false,
        val calibrated: Boolean = false,
    )

    private val base = listOf(Ex("Peso Muerto Convencional"), Ex("Sentadilla \"Belt Squat\""), Ex("Hip Thrust · Barra"),
        Ex("Sentadilla en Máquina Pendulum"), Ex("Patada de Glúteo · Polea"), Ex("Plancha Copenhague Dinámica"), Ex("Caminadora", cardio = true))
    private val base14 = listOf("Peso Muerto Convencional", "Sentadilla \"Belt Squat\"", "Hip Thrust · Barra", "Sentadilla en Máquina Pendulum",
        "Prensa de piernas", "Curl femoral tumbado", "Extensión de cuádriceps", "Elevación de talones de pie", "Abducción de cadera en máquina",
        "Buenos días con barra", "Zancadas caminando", "Hiperextensión a 45°", "Patada de Glúteo · Polea", "Plancha Copenhague Dinámica").map { Ex(it) }

    private val reposo = Scene(base, 1, 1, listOf("ddd", "d"))
    private val registrada = Scene(base, 1, 0, listOf("ddd", "d"), update = true)
    private val ultima = Scene(base, 1, 2, listOf("ddd", "dd"))
    private val ultimo = Scene(base, 5, 2, listOf("ddd", "ddd", "ddd", "ddd", "ddd", "dd"))
    private val descanso = Scene(base, 1, 0, listOf("ddd", "d"), rest = "2:41" to .27f)
    private val prep = Scene(base, 1, 0, listOf("ddd"), prep = true, pend = listOf("Estado previo"), label = "Registrar aproximación A1")
    private val diez = Scene(listOf(base[0].copy(sets = 10)) + base.drop(1), 0, 5, listOf("ddddd"))
    private val catorce = Scene(base14, 8, 2, List(8) { "ddd" } + "dd")
    private val ruta = Scene(base.mapIndexed { i, e -> if (i == 3 || i == 4) e.copy(group = "A") else e }, 2, 1, listOf("ddd", "ddd", "d"), calibrated = true)
    private val ruta14 = Scene(base14, 8, 2, List(8) { "ddd" } + "dd")

    private fun exercises(sc: Scene): List<Exercise> = sc.exs.mapIndexed { i, s ->
        if (s.cardio) Exercise("e$i", s.name, cardioDetails = CardioDetails(CardioType.TREADMILL, targetDurationSeconds = 900))
        else Exercise("e$i", s.name, sets = List(s.sets) { ExerciseSet("e$i-$it", targetReps = 5) }, supersetGroupRef = s.group,
            warmupSets = if (sc.prep && i == sc.ex) listOf(WarmupSetDefinition("w1", 50.0, 8), WarmupSetDefinition("w2", 70.0, 5)) else emptyList())
    }

    private fun groups(sc: Scene): List<SupersetGroup> {
        val members = sc.exs.indices.filter { sc.exs[it].group == "A" }.map { "e$it" }
        return if (members.isEmpty()) emptyList() else listOf(SupersetGroup("A", members, rounds = 3))
    }

    private fun completed(sc: Scene): Map<String, CompletedSet> = buildMap {
        sc.reg.forEachIndexed { i, r -> r.forEachIndexed { k, c -> if (c == 'd') put(workoutSetKey("e$i", k), CompletedSet("c$i-$k")) } }
    }

    private fun doneAndTotal(sc: Scene): Pair<Int, Int> =
        sc.reg.sumOf { it.count { c -> c == 'd' } } to sc.exs.filter { !it.cardio }.sumOf { it.sets }

    private fun snapshot(sc: Scene, doneNow: Int? = null, justRecorded: Boolean = false): LiveSetStepperSnapshot {
        val n = sc.exs[sc.ex].sets
        val reg = sc.reg.getOrElse(sc.ex) { "" }
        val sets = (0 until n).map { k ->
            TimelineElement.BilateralSet(pageIndex = 40 + k, label = "S${k + 1}", state = when {
                reg.getOrNull(k) == 'd' || (justRecorded && k == sc.cur) -> WorkoutSetCardVisualState.COMPLETED
                k == sc.cur && sc.rest == null && !sc.prep -> WorkoutSetCardVisualState.ACTIVE
                else -> WorkoutSetCardVisualState.FUTURE
            })
        }
        val elements: List<TimelineElement> = when {
            sc.prep -> listOf(TimelineElement.WarmupPill(true, false, 0f, onSelect = {})) + sets
            sc.rest != null -> sets.take(sc.cur + 1) + TimelineElement.RestPill(88, sc.rest.second, sc.rest.first) + sets.drop(sc.cur + 1)
            else -> sets
        }
        val (done, total) = doneAndTotal(sc)
        return LiveSetStepperSnapshot(elements, when { sc.prep -> 0; sc.rest != null -> sc.cur + 1; else -> sc.cur }, doneNow ?: done, total, null, true,
            activePageIndex = when { sc.prep -> null; sc.rest != null -> 88; else -> 40 + sc.cur })
    }

    // ---------------------------------------------------------------------------------------------------------------- pantalla
    private fun setContent(fontScale: Float, reduced: Boolean, body: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            view = LocalView.current; density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalWizardReducedMotion provides reduced,
                LocalKpknOverlayHost provides null) { KPKNTheme { MaterialTheme { body() } } }
        }
        rule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Line(sc: Scene, height: Int, modifier: Modifier = Modifier, bottom: Int = if (height <= 760) 14 else 22) {
        val ex = exercises(sc)
        val (done, total) = doneAndTotal(sc)
        LiveSessionLine(ex, "e${sc.ex}", snapshot(sc), {}, {}, {}, {}, {}, recordVisible = true, recordEnabled = true,
            isUpdateMode = sc.update, recordLabel = sc.label, onNavigateAdjacentExercise = {}, supersetGroups = groups(sc),
            pendingActions = sc.pend.map { RoadmapPendingAction(it) {} }, completedSets = completed(sc),
            sessionCompletedCount = done, sessionTotalCount = total, restActive = sc.rest != null, onEndRest = {},
            modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = bottom.dp))
    }

    private fun scene(name: String, sc: Scene, height: Int = 880, fontScale: Float = 1f, bottom: Int = if (height <= 760) 14 else 22) {
        setContent(fontScale, reduced = true) {
            Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("road-scene")) {
                Line(sc, height, Modifier.align(Alignment.BottomCenter), bottom)
            }
        }
        rule.onNodeWithTag("live-session-line").assertIsDisplayed()
        save("road-scene", name)
    }

    // A «Calma»: ventana de menos de 820 dp (h760) aunque la escena del lienzo mida 880.
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aReposo() = scene("lv-road-a-reposo", reposo, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aRegistrada() = scene("lv-road-a-registrada", registrada, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aUltima() = scene("lv-road-a-ultima", ultima, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aUltimo() = scene("lv-road-a-ultimo", ultimo, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aDescanso() = scene("lv-road-a-descanso", descanso, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun aPrep() = scene("lv-road-a-prep", prep, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun a10Series() = scene("lv-road-a-10series", diez, 760, bottom = 22)
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun a14Ej() = scene("lv-road-a-14ej", catorce, 760, bottom = 22)

    // B «Con ruta visible»: ventana de 880 dp (el Flip5).
    @Test fun bReposo() = scene("lv-road-b-reposo", reposo)
    @Test fun bRegistrada() = scene("lv-road-b-registrada", registrada)
    @Test fun bUltima() = scene("lv-road-b-ultima", ultima)
    @Test fun bUltimo() = scene("lv-road-b-ultimo", ultimo)
    @Test fun bDescanso() = scene("lv-road-b-descanso", descanso)
    @Test fun bPrep() = scene("lv-road-b-prep", prep)
    @Test fun b10Series() = scene("lv-road-b-10series", diez)
    @Test fun b14Ej() = scene("lv-road-b-14ej", catorce)

    // Densas: 360 × 740 y letra al 130 % (en 740 dp de alto B se ve como A).
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun reposo740() = scene("lv-road-a-reposo-740", reposo, 740)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun reposo740x130() = scene("lv-road-a-reposo-740-130", reposo, 740, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun prep740x130() = scene("lv-road-a-prep-740-130", prep, 740, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun descanso740x130() = scene("lv-road-a-descanso-740-130", descanso, 740, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun diez740x130() = scene("lv-road-a-10series-740-130", diez, 740, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun ultimo740x130() = scene("lv-road-a-ultimo-740-130", ultimo, 740, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun catorce740() = scene("lv-road-a-14ej-740", catorce, 740)

    // ---------------------------------------------------------------------------------------------------------------- la «Ruta»
    private fun rutaScene(name: String, sc: Scene, scrollDp: Int) {
        val scroll = androidx.compose.foundation.ScrollState(0)
        setContent(1f, reduced = true) {
            Box(Modifier.requiredSize(360.dp, 880.dp).background(Color.Black).testTag("road-scene")) {
                val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(739.dp).background(Color(0xFF1B1B1D), shape).kpknShadow(KpknShadow.Hoja, shape)) {
                    // Cabecera del lienzo (`.lv2-sheet .hd` y `.tt`): asa de 33 × 3, título en Syne 21 y la ✕ de 44 dp.
                    Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 33.dp, height = 3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x30FFFFFF)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 19.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recorrido de la sesión", Modifier.weight(1f).padding(vertical = 6.dp), color = Color(0xFFF2EEE6),
                            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp))
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(KpknLineIcons.Close, null, Modifier.size(18.dp), tint = Color(0xFFB9B5AE)) }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 15.dp)) {
                        val ex = exercises(sc)
                        LiveRouteSheetContent(ex, completed(sc), emptyList(), emptySet(), "e${sc.ex}", groups(sc), null, 80.0, 0L, 70,
                            if (sc.calibrated) mapOf(workoutSetKey("e${sc.ex}", 0) to 120.0) else emptyMap(),
                            {}, {}, { _, _ -> }, {}, {}, {}, {}, currentSetNumber = sc.cur + 1)
                    }
                }
            }
        }
        if (scrollDp > 0) { rule.runOnIdle { kotlinx.coroutines.runBlocking { scroll.scrollTo((scrollDp * density).toInt()) } }; rule.waitForIdle() }
        save("road-scene", name)
    }

    @Test fun rutaConSuperserieYCardio() = rutaScene("lv-road-ruta", ruta, 0)
    @Test fun rutaCon14EjerciciosConScroll() = rutaScene("lv-road-ruta-14", ruta14, 380)

    // ---------------------------------------------------------------------------------------------------------------- medidas (lv-road-medidas)
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun measures(variantHeight: Int, height: Int, name: String) {
        setContent(1f, reduced = true) {
            Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black)) { Line(reposo, height, Modifier.align(Alignment.BottomCenter)) }
        }
        val island = bounds("live-session-line"); val route = bounds("live-session-route"); val record = bounds("live-session-record")
        val dp = { v: Float -> v / density }
        val b = liveRoadmapVariantFor(height).band1.value
        val text = StringBuilder("variante=${liveRoadmapVariantFor(height)} ventana=$height dp\n")
        text.append("isla: ancho ${dp(island.width)} alto ${dp(island.height)} · margen izq ${dp(island.left)} der ${360f - dp(island.right)}\n")
        // La zona táctil de la banda 1 mide la banda más 6 dp por arriba y por abajo.
        text.append("banda 1: zona táctil ${dp(route.height)} (banda $b) · arriba ${dp(route.top - island.top)} (12 − 6 = 6)\n")
        text.append("banda 2: botón alto ${dp(record.height)} · arriba ${dp(record.top - island.top)} · abajo ${dp(island.bottom - record.bottom)}\n")
        assertEquals(variantHeight.toFloat(), dp(island.height), .6f)
        assertEquals(328f, dp(island.width), .6f)
        assertEquals(b + 12f, dp(route.height), .6f)
        assertEquals(12f - 6f, dp(route.top - island.top), .6f)
        assertEquals(48f, dp(record.height), .6f)
        assertEquals(12f, dp(island.bottom - record.bottom), .6f)
        // Hueco de 8 dp entre la banda 1 y lo que sigue (la banda 2, o la tira con sus 8 dp más).
        val bandsGap = dp(record.top - island.top) - (12f + b + 8f + if (variantHeight == 132) 14f + 8f else 0f)
        assertEquals(0f, bandsGap, .6f)
        if (variantHeight == 132) {
            val strip = bounds("live-session-strip")
            text.append("tira: zona táctil ${dp(strip.height)} · centro ${dp(strip.center.y - island.top)} (esperado 12+30+8+7 = 57)\n")
            assertEquals(44f, dp(strip.height), .6f)
            assertEquals(12f + 30f + 8f + 7f, dp(strip.center.y - island.top), .6f)
            val seg = bounds("live-session-seg-1"); val seg0 = bounds("live-session-seg-0")
            text.append("segmento actual: alto ${dp(seg.height)} (14) · otro: alto ${dp(seg0.height)} (8)\n")
            assertEquals(14f, dp(seg.height), .6f); assertEquals(8f, dp(seg0.height), .6f)
        }
        val navLeft = rule.onNodeWithTag("live-session-line").fetchSemanticsNode()
        text.append("alto del roadmap según la medida de la ventana: ${liveRoadmapVariantFor(height).height}\n")
        File(File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }, "$name.txt").writeText(text.toString())
        assertTrue(navLeft.layoutInfo.isPlaced)
    }

    @Test fun medidasB() = measures(132, 880, "lv-road-medidas-b")
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun medidasA() = measures(112, 760, "lv-road-medidas-a")
    @Test @Config(qualifiers = "w360dp-h819dp-xxhdpi") fun a819UsaA() = measures(112, 819, "lv-road-medidas-819")
    @Test @Config(qualifiers = "w360dp-h820dp-xxhdpi") fun a820UsaB() = measures(132, 820, "lv-road-medidas-820")

    // ---------------------------------------------------------------------------------------------------------------- fotogramas
    private var done by mutableIntStateOf(4)
    private var justRecorded by mutableStateOf(false)
    private var restProgress by mutableStateOf(.27f)

    private fun settle() {
        rule.waitForIdle()
        repeat(4) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
    }

    private fun frame(name: String, ms: Int) { save("road-scene", "${name}_t${String.format("%04d", ms)}") }

    /** «Registrar serie»: barrido verde, ✓ que se dibuja, cifra que rueda y anillo que avanza (550 / 360 / 260 / 700 ms). */
    @Test fun fotogramasDeRegistrarSerie() {
        rule.mainClock.autoAdvance = false
        setContent(1f, reduced = false) {
            Box(Modifier.requiredSize(360.dp, 880.dp).background(Color.Black).testTag("road-scene")) {
                val sc = reposo
                val ex = exercises(sc)
                LiveSessionLine(ex, "e1", snapshot(sc, doneNow = done, justRecorded = justRecorded), {}, {}, {}, {},
                    { done = 5; justRecorded = true }, recordVisible = true, recordEnabled = true, isUpdateMode = justRecorded,
                    onNavigateAdjacentExercise = {}, supersetGroups = emptyList(), completedSets = completed(sc),
                    sessionCompletedCount = done, sessionTotalCount = 18,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 22.dp))
            }
        }
        settle()
        frame("registrar", -1)
        rule.onNodeWithTag("live-session-record").performClick()
        settle()
        var elapsed = 0
        for (t in listOf(0, 138, 275, 413, 550, 700)) {
            rule.mainClock.advanceTimeBy((t - elapsed).toLong()); elapsed = t
            rule.waitForIdle()
            frame("registrar", t)
        }
    }

    /** La tira al deslizar: segmento provisional de 18 dp, pulso de 80 ms. */
    @Test fun fotogramasDeLaTiraAlDeslizar() {
        rule.mainClock.autoAdvance = false
        setContent(1f, reduced = false) {
            Box(Modifier.requiredSize(360.dp, 880.dp).background(Color.Black).testTag("road-scene")) {
                Line(reposo, 880, Modifier.align(Alignment.BottomCenter))
            }
        }
        settle()
        frame("tira", -1)
        rule.onNodeWithTag("live-session-strip").performTouchInput { down(center.copy(x = width * 0.25f)) }
        rule.waitForIdle()
        var elapsed = 0
        for (t in listOf(0, 20, 40, 60, 80, 400)) {
            rule.mainClock.advanceTimeBy((t - elapsed).toLong()); elapsed = t
            rule.waitForIdle()
            frame("tira", t)
        }
        rule.onNodeWithTag("live-session-strip").performTouchInput { up() }
    }

    /** El descanso: el fondo melocotón se vacía con `width 250ms linear` (lv-T13). */
    @Test fun fotogramasDelDescanso() {
        rule.mainClock.autoAdvance = false
        setContent(1f, reduced = false) {
            Box(Modifier.requiredSize(360.dp, 880.dp).background(Color.Black).testTag("road-scene")) {
                val sc = Scene(base, 1, 0, listOf("ddd", "d"), rest = "2:41" to restProgress)
                Line(sc, 880, Modifier.align(Alignment.BottomCenter))
            }
        }
        settle()
        frame("descanso", -1)
        rule.runOnIdle { restProgress = .77f }
        settle()
        var elapsed = 0
        for (t in listOf(0, 62, 125, 188, 250)) {
            rule.mainClock.advanceTimeBy((t - elapsed).toLong()); elapsed = t
            rule.waitForIdle()
            frame("descanso", t)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- guardar
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { val canvas = Canvas(full); canvas.drawColor(android.graphics.Color.BLACK); host.draw(canvas) }
        val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left), bounds.height.toInt().coerceAtMost(full.height - top))
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        FileOutputStream(File(folder, "$name.png")).use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cropped.recycle(); full.recycle()
    }
}
