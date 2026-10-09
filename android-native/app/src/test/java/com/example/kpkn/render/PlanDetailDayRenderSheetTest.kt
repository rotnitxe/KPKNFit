package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.DetailSheet
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
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
 * HOJAS DE DIBUJO del detalle con «Tu semana» desplegada y el panel «Cambiar» abierto (no comprueba nada: escribe PNG).
 * Solo corre con `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Pinta [DetailSheet] sin ventana de diálogo, a
 * 360 dp y letra normal, con el mismo criterio que [WizardRenderSheetTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class PlanDetailDayRenderSheetTest {

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

    /** La vista de Compose del host, para dibujarla a un bitmap (igual que en [WizardRenderSheetTest]). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

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

    private val card = PlanCardModel(
        id = "fuerza", title = "Fuerza y masa", kicker = "4 SEMANAS · PERIODIZADO",
        blurb = "Un programa de fuerza con tres días a la semana.", profile = TrainingGoalProfile.STRENGTH_MUSCLE,
        daysLabel = "3 días", minutesLabel = "~60 min", levelLabel = "Intermedio", coverSeed = 3,
    )

    private val model = PlanDetailModel(
        card = card,
        description = "Tres días a la semana, con la fuerza en los básicos y el volumen en los accesorios.",
        mainExercises = listOf("Sentadilla", "Press banca"),
        blocks = listOf(PlanBlockModel("Volumen", "Semanas 1–2", "Más series y repeticiones.")),
        week = listOf(
            PlanDayModel(1, "Torso fuerza", 60, 4, true),
            PlanDayModel(3, "Pierna", 55, 4, false),
            PlanDayModel(5, "Torso volumen", 50, 4, false),
        ),
        reasons = listOf("Entrenas 3 días: es la frecuencia que pediste."),
        notes = emptyList(),
        attribution = null,
    )

    private val lunes = mapOf(
        1 to listOf(
            SetupPlanRevealExercise(instanceId = "lun-1", name = "Sentadilla", setsLabel = "4 × 5–6", swappable = true),
            SetupPlanRevealExercise(instanceId = "lun-2", name = "Press banca", setsLabel = "4 × 6–8", swappable = true),
            SetupPlanRevealExercise(instanceId = "lun-3", name = "Remo con barra", setsLabel = "3 × 8–10", swappable = true),
            SetupPlanRevealExercise(instanceId = "lun-4", name = "Peso muerto rumano", setsLabel = "3 × 8–10", swappable = true),
        ),
    )

    private val alternativas = listOf(
        SwapOption(configId = "cfg-a", displayName = "Sentadilla frontal", variant = "Barra", pattern = "knee_dominant", kind = SwapKind.EQUIVALENT),
        SwapOption(configId = "cfg-b", displayName = "Prensa", variant = "Máquina", pattern = "knee_dominant", kind = SwapKind.SAME_PATTERN),
        SwapOption(configId = "cfg-c", displayName = "Sentadilla goblet", variant = "Mancuerna", pattern = "knee_dominant", kind = SwapKind.SAME_PATTERN),
    )

    private fun host() {
        rule.setContent {
            hostView = LocalView.current
            DetailSheet(
                model = model,
                selected = true,
                reduced = true,
                blur = false,
                shown = { 1f },
                onChoose = {},
                onClose = {},
                weekStartDay = 1,
                exercises = lunes,
                swapOptionsOf = { alternativas },
                onSwap = { _, _ -> },
                onUndoSwap = {},
            )
        }
    }

    @Test
    fun renderTheDayExpanded() {
        onlyWhenAsked()
        host()
        rule.onNodeWithTag("setup-plan-day-1").performScrollTo()
        rule.onNodeWithTag("setup-plan-day-1").performClick()
        rule.onNodeWithTag("setup-plan-exercise-lun-1").assertExists()
        save("detail_day_expanded_s100")
    }

    @Test
    fun renderThePanelOpen() {
        onlyWhenAsked()
        host()
        rule.onNodeWithTag("setup-plan-day-1").performScrollTo()
        rule.onNodeWithTag("setup-plan-day-1").performClick()
        rule.onNodeWithTag("setup-plan-swap-lun-1").performScrollTo()
        rule.onNodeWithTag("setup-plan-swap-lun-1").performClick()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertExists()
        save("detail_panel_open_s100")
    }
}
