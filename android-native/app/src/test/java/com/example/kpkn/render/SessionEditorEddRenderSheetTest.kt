package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.CardioCatalogSheet
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetMemberPickerSheet
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
 * HOJA DE DIBUJO de ED-D: el creador de superseries (C2), las rondas y descansos (C3), el cardio continuo con sus avisos (C4), el cardio
 * por series (C5), el cardio por intervalos (C6) y el selector de cardio (C7). A 360 × 880 dp, al 100 % y al 130 % de letra. No comprueba
 * nada: escribe PNG con `edd_<escena>[_s130].png`. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class SessionEditorEddRenderSheetTest {

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

    /** La vista de Compose del host, para dibujarla a un bitmap. */
    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)

    // ───────────────────────────── datos ─────────────────────────────

    private fun strength(id: String, name: String, sets: Int, group: String? = null) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.REPS,
        supersetGroupRef = group,
        sets = (1..sets).map {
            ExerciseSet(id = "$id-$it", targetReps = 10, targetRepsRange = RepRange(8, 10), targetRIR = 2)
        },
    )

    private val sentadilla = strength("sent", "Sentadilla trasera con barra alta", 4)
    private val peso = strength("peso", "Peso muerto rumano", 4)
    private val zancada = strength("zan", "Zancada con mancuernas", 3, group = "ss")
    private val curl = strength("curl", "Curl femoral tumbado", 3, group = "ss")
    private val talones = strength("tal", "Elevación de talones de pie", 3, group = "ss")
    private val dominadas = strength("dom", "Dominadas", 3)

    private val continuo = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        targetDurationSeconds = 20 * 60,
    )

    private val series = CardioDetails(
        type = CardioType.ROW_MACHINE,
        supportsDistance = false,
        hiit = CardioHiitConfig(
            warmupSeconds = 300,
            workSeconds = 30,
            restSeconds = 60,
            rounds = 8,
            cooldownSeconds = 180,
            targetRpe = 9.0,
        ),
    )

    private val intervalos = CardioDetails(
        type = CardioType.TREADMILL,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "b1", type = CardioBlockType.WARMUP, durationSeconds = 300, intensityLevel = 3, speedKmh = 6.0),
            CardioIntervalBlock(id = "b2", type = CardioBlockType.WORK, durationSeconds = 180, intensityLevel = 8, speedKmh = 11.0),
            CardioIntervalBlock(id = "b3", type = CardioBlockType.RECOVER, durationSeconds = 120, intensityLevel = 3, speedKmh = 5.0),
            CardioIntervalBlock(id = "b4", type = CardioBlockType.WORK, durationSeconds = 180, intensityLevel = 9, speedKmh = 12.0),
            CardioIntervalBlock(id = "b5", type = CardioBlockType.COOLDOWN, durationSeconds = 240, intensityLevel = 3, speedKmh = 5.0),
        ),
        intervalRounds = 1,
    )

    // ───────────────────────────── host ─────────────────────────────

    private fun host(content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KpknSurface.canvas)
                        .padding(12.dp),
                ) {
                    content()
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        FileOutputStream(File(dir, "$name.png")).use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Dibuja la escena a las dos medidas de letra. */
    private fun renderBothScales(name: String, beforeSave: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            beforeSave()
            rule.waitForIdle()
            save("edd_${name}$suffix")
        }
    }

    /** Abre el panel «Avanzado» del cardio y deja a la vista sus avisos. */
    private fun openAdvanced() {
        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()
    }

    // ───────────────────────────── escenas ─────────────────────────────

    @Test
    fun renderC2() {
        host {
            SupersetMemberPickerSheet(
                draft = SupersetDraft(
                    partId = "acc",
                    exerciseIds = listOf("zan", "curl", "tal"),
                    sourceGroupId = "ss",
                    restBetweenExercises = 60,
                    restAfterSuperset = 120,
                    rounds = 3,
                ),
                sessionExercises = listOf(sentadilla, peso, zancada, curl, talones, dominadas),
                sessionParts = listOf(
                    SessionPart(id = "p1", name = "Principal", exercises = listOf(sentadilla, peso)),
                    SessionPart(id = "p2", name = "Accesorios", exercises = listOf(zancada, curl, talones, dominadas)),
                ),
                onUpdateDraft = {},
                onConfirm = {},
                onOpenCatalog = {},
                onDismiss = {},
            )
        }
        renderBothScales("c2_crear_superserie")
        renderBothScales("c2_crear_superserie_config") {
            rule.onNodeWithText("Descanso tras la ronda").performScrollTo()
        }
    }

    @Test
    fun renderC3() {
        host {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SupersetGroupEditorCard(
                    group = SupersetGroup(
                        id = "ss",
                        exerciseOrder = listOf("zan", "curl", "tal"),
                        rounds = 3,
                        restBetweenExercises = 60,
                        restAfterSuperset = 120,
                        roundRestBetweenExercises = mapOf(1 to 45),
                        roundRestAfterSuperset = mapOf(1 to 150),
                    ),
                    exercises = listOf(zancada, curl, talones),
                    accentHex = null,
                    partId = "acc",
                    onOpenSupersetCreator = { _, _ -> },
                    onUpdateSupersetRest = { _, _, _, _ -> },
                    onRemoveFromSuperset = { _, _ -> },
                    onDissolve = {},
                    onAddRound = {},
                ) {
                    listOf(zancada, curl, talones).forEach { member -> Text(member.name, color = WizardColors.text) }
                }
            }
        }
        rule.onNodeWithText("3 rondas").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ronda 2").performScrollTo().performClick()
        rule.waitForIdle()
        renderBothScales("c3_rondas")
        renderBothScales("c3_rondas_descanso") {
            rule.onNodeWithText("Usar el base").performScrollTo()
        }
    }

    @Test
    fun renderC4() {
        host {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CardioEditorCard(
                    details = continuo,
                    accentColor = SymbolPalette.columna,
                    exerciseName = "Bici estática",
                    onChange = {},
                )
            }
        }
        openAdvanced()
        rule.onNodeWithText("AVISOS Y PANTALLA").performScrollTo()
        rule.waitForIdle()
        renderBothScales("c4_cardio_continuo") {
            rule.onNodeWithText("Pantalla encendida").performScrollTo()
        }
    }

    @Test
    fun renderC5() {
        host {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CardioEditorCard(
                    details = series,
                    accentColor = SymbolPalette.columna,
                    exerciseName = "Remo ergómetro",
                    onChange = {},
                )
            }
        }
        openAdvanced()
        rule.onNodeWithText("OBJETIVO DE TRABAJO").performScrollTo()
        rule.waitForIdle()
        renderBothScales("c5_cardio_series") {
            rule.onNodeWithText("m por esfuerzo").performScrollTo()
        }
    }

    @Test
    fun renderC6() {
        host {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CardioEditorCard(
                    details = intervalos,
                    accentColor = SymbolPalette.columna,
                    exerciseName = "Cinta de correr",
                    onChange = {},
                )
            }
        }
        rule.onNodeWithText("PATRÓN DE INTERVALO").performScrollTo()
        rule.waitForIdle()
        renderBothScales("c6_cardio_intervalos") {
            rule.onNodeWithText("Añadir bloque").performScrollTo()
        }
    }

    @Test
    fun renderC7() {
        host {
            CardioCatalogSheet(onAdd = {})
        }
        rule.onNodeWithText("AL AIRE LIBRE").performScrollTo()
        rule.waitForIdle()
        renderBothScales("c7_selector_cardio") {
            rule.onNodeWithText("AL AIRE LIBRE").performScrollTo()
            rule.waitForIdle()
            rule.onAllNodesWithText("GPS en vivo")[0].performScrollTo()
        }
    }
}
