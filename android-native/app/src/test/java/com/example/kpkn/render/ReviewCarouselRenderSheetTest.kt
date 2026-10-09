package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.ReviewCarousel
import com.example.kpkn.screens.onboarding.ReviewPage
import com.example.kpkn.screens.onboarding.ReviewRingsPage
import com.example.kpkn.screens.onboarding.ReviewNutritionPage
import com.example.kpkn.screens.onboarding.ReviewTrainingPage
import com.example.kpkn.screens.onboarding.ReviewYouPage
import com.example.kpkn.screens.onboarding.SetupBodyFatSource
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.reviewBodyLine
import com.example.kpkn.screens.onboarding.reviewCompetitionLine
import com.example.kpkn.screens.onboarding.reviewGenderMarkOf
import com.example.kpkn.screens.onboarding.reviewSexLabelOf
import com.example.kpkn.screens.onboarding.reviewTrainingLine
import com.example.kpkn.screens.onboarding.withStepChoice
import com.example.kpkn.ui.components.rings.TowerRingValues
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
 * HOJA DE DIBUJO de la revisión en carrusel (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * Usa un borrador de prueba realista y las cuatro páginas (Tú, Entreno, Nutrición y RINGS) a 360 dp de ancho, con la letra al 100 %
 * y, en dos páginas, al 130 %. Los símbolos se dibujan en su cuadro final (movimiento reducido), como en la comprobación de diseño.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ReviewCarouselRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
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

    /** La vista de Compose de la actividad, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private var fontScale by mutableStateOf(1f)
    private var shownPage by mutableStateOf(0)
    private var sheetWidth by mutableStateOf(360f)

    /** Un borrador de prueba con datos reales del alta: lo que la revisión lee se obtiene de aquí con las mismas funciones de la app. */
    private val draft = SetupWizardDraft(
        name = "Marta",
        ageYears = 34,
        heightCm = 168.0,
        weightKg = 64.5,
        weightUnit = "kg",
        bodyFatPercent = 22.0,
        bodyFatSource = SetupBodyFatSource.MEASURED,
        goalProfile = TrainingGoalProfile.BODYBUILDING,
        experience = SetupExperience.INTERMEDIATE,
        minutesPerSession = 60,
        competition = CompetitionAnswer.WITH_DATE,
        meetDateIso = "2027-03-14",
        meetName = "Nacional Open",
    ).withStepChoice(SetupStepId.EQUATION_SEX, "female")

    private val card = PlanCardModel(
        id = "render-hipertrofia",
        title = "Hipertrofia en 4 días",
        kicker = "4 SEMANAS · SE REPITE",
        blurb = "Cuatro días a la semana, con volumen moderado y progresión por repeticiones.",
        profile = TrainingGoalProfile.BODYBUILDING,
        daysLabel = "4 días",
        minutesLabel = "~60 min",
        levelLabel = "Intermedio",
        coverSeed = 3,
    )

    private fun pages(): List<ReviewPage> = listOf(
        ReviewPage(title = "Tú", onEdit = { }) { _ ->
            ReviewYouPage(
                alias = draft.name,
                ageLabel = draft.ageYears?.let { "$it años" },
                sexMark = reviewGenderMarkOf(draft),
                sexLabel = reviewSexLabelOf(draft),
                bodyLine = reviewBodyLine(draft),
            )
        },
        ReviewPage(title = "Entreno", onEdit = { }) { settled ->
            ReviewTrainingPage(
                later = false,
                card = card,
                trainingLine = reviewTrainingLine(days = 4, minutes = 60, mainDay = 4),
                placesLine = "Gimnasio y casa · Barra y discos, Rack +12",
                competitionLine = reviewCompetitionLine(draft),
                settled = settled,
            )
        },
        ReviewPage(title = "Nutrición", onEdit = { }) { _ ->
            ReviewNutritionPage(
                trackingOnly = false,
                kcal = 2450,
                proteinG = 165,
                carbsG = 240,
                fatG = 70,
                fractions = MacroRingFractions(calories = 0.78f, protein = 0.7f, carbs = 0.6f, fat = 0.5f, referenceMark = 0.8f),
                goalLine = "Objetivo: 72 kg",
                goalDateLine = "Fecha estimada: entre marzo y abril de 2027",
            )
        },
        ReviewPage(title = "RINGS", onEdit = { }) { _ ->
            ReviewRingsPage(values = TowerRingValues(columna = 72, musculos = 64, energia = 80))
        },
    )

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
    fun renderTheReviewPages() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Column(
                    Modifier
                        .testTag("sheet")
                        .wrapContentSize()
                        .background(Color.Black),
                ) {
                    Column(Modifier.width(sheetWidth.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
                        // La página se monta de nuevo para cada captura: así el carrusel empieza en la página pedida.
                        key(shownPage) { ReviewCarousel(pages = pages(), initialPage = shownPage) }
                    }
                }
            }
        }
        // Una captura por página y letra; la de Nutrición también a 320 dp de ancho (la pantalla pequeña del alta).
        val shots = listOf(
            Shot("review_tu_s100", 0, 1f, 360f),
            Shot("review_entreno_s100", 1, 1f, 360f),
            Shot("review_entreno_s130", 1, 1.3f, 360f),
            Shot("review_nutricion_s100", 2, 1f, 360f),
            Shot("review_nutricion_s130", 2, 1.3f, 360f),
            Shot("review_nutricion_w320_s130", 2, 1.3f, 320f),
            Shot("review_rings_s100", 3, 1f, 360f),
            Shot("review_rings_s130", 3, 1.3f, 360f),
        )
        for (shot in shots) {
            rule.runOnUiThread {
                fontScale = shot.scale
                shownPage = shot.page
                sheetWidth = shot.width
            }
            rule.waitForIdle()
            save("sheet", shot.name)
        }
    }

    /** Una captura: nombre del PNG, página del carrusel, escala de letra y ancho de la hoja en dp. */
    private data class Shot(val name: String, val page: Int, val scale: Float, val width: Float)

    @Test
    fun renderTheTrackingOnlyNutritionPage() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1f),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").wrapContentSize().background(Color.Black).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Column(Modifier.width(312.dp)) {
                        ReviewNutritionPage(
                            trackingOnly = true,
                            kcal = null,
                            proteinG = null,
                            carbsG = null,
                            fatG = null,
                            fractions = null,
                            goalLine = null,
                            goalDateLine = null,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        save("sheet", "review_nutricion_solo_registro_s100")
    }
}
