package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
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
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.SetupBodyFatSource
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupPlanRevealDay
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.design.ActivationFooter
import com.example.kpkn.screens.onboarding.design.ActivationHeader
import com.example.kpkn.screens.onboarding.design.ActivationMoment
import com.example.kpkn.screens.onboarding.design.ActivationNotice
import com.example.kpkn.screens.onboarding.design.ActivationNutritionMoment
import com.example.kpkn.screens.onboarding.design.ActivationPager
import com.example.kpkn.screens.onboarding.design.ActivationProgramMoment
import com.example.kpkn.screens.onboarding.design.ActivationYouMoment
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.activationNoticesOf
import com.example.kpkn.screens.onboarding.design.activationTitleOf
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.reviewBodyFatPercentOf
import com.example.kpkn.screens.onboarding.reviewGenderMarkOf
import com.example.kpkn.screens.onboarding.reviewSexLabelOf
import com.example.kpkn.screens.onboarding.withStepChoice
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
 * HOJA DE DIBUJO de la revisión y activación (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. Pinta el overlay entero (cabecera, carrusel con sus cuatro momentos, pie) como lo verá la persona: un alta COMPLETA
 * (programa con nombre, semanas, días y fases; nutrición con kcal y macros; RINGS calculados) a 360 dp con la letra al 100 %
 * y al 130 %, y una sola hoja del estado vacío. El carrusel toma el alto de su página más alta: cada momento se ve entero.
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

    /** El programa elegido: nombre, semanas y fases en el kicker, días y minutos, y nivel. */
    private val card = PlanCardModel(
        id = "render-hipertrofia",
        title = "Hipertrofia en 4 días",
        kicker = "12 SEMANAS · 3 FASES",
        blurb = "Cuatro días a la semana, con volumen moderado y progresión por repeticiones.",
        profile = TrainingGoalProfile.BODYBUILDING,
        daysLabel = "4 días",
        minutesLabel = "~60 min",
        levelLabel = "Intermedio",
        coverSeed = 3,
    )

    /** La semana del programa: cuatro días de entreno; el jueves, la sesión principal. */
    private val week = listOf(
        SetupPlanRevealDay(day = 1, title = "Empuje", minutes = 60, exerciseCount = 6, isMain = false),
        SetupPlanRevealDay(day = 3, title = "Tirón", minutes = 60, exerciseCount = 6, isMain = false),
        SetupPlanRevealDay(day = 4, title = "Piernas", minutes = 65, exerciseCount = 7, isMain = true),
        SetupPlanRevealDay(day = 6, title = "Torso", minutes = 55, exerciseCount = 6, isMain = false),
    )

    /** Los avisos de un alta completa: lo que el programa pide saber, y lo que los RINGS se ajustan solos. */
    private val completeNotices = listOf(
        ActivationNotice("~62 min por sesión: un poco más de los 60 que pediste."),
        ActivationNotice("Tus RINGS se ajustarán con tus primeros entrenos."),
    )

    /**
     * Los cuatro momentos con datos explícitos (los mismos composables que usa el overlay de la app). [full] = alta completa;
     * sin él, el estado vacío: sin programa, sin plan de comidas y sin RINGS calculados.
     */
    private fun moments(full: Boolean): List<ActivationMoment> = listOf(
        ActivationMoment(SetupWizardBlock.BASICS, "Tú", onEdit = { }) { settled ->
            ActivationYouMoment(
                alias = draft.name,
                sexMark = reviewGenderMarkOf(draft),
                sexLabel = reviewSexLabelOf(draft),
                ageYears = draft.ageYears,
                heightCm = draft.heightCm,
                weightKg = draft.weightKg,
                weightUnit = WizardMassUnit.KG,
                bodyFatPercent = reviewBodyFatPercentOf(draft),
                settled = settled,
            )
        },
        ActivationMoment(SetupWizardBlock.TRAINING, "Tu programa", onEdit = { }) { settled ->
            ActivationProgramMoment(
                draft = draft,
                later = false,
                card = if (full) card else null,
                week = if (full) week else emptyList(),
                startDay = 1,
                minutes = 60,
                settled = settled,
            )
        },
        ActivationMoment(SetupWizardBlock.NUTRITION, "Nutrición", onEdit = { }) { settled ->
            ActivationNutritionMoment(
                trackingOnly = false,
                kcal = if (full) 2450 else null,
                proteinG = if (full) 165 else null,
                carbsG = if (full) 240 else null,
                fatG = if (full) 70 else null,
                fractions = if (full) {
                    MacroRingFractions(calories = 0.78f, protein = 0.7f, carbs = 0.6f, fat = 0.5f, referenceMark = 0.8f)
                } else {
                    null
                },
                goalLine = if (full) "Objetivo: 72 kg" else null,
                goalDateLine = if (full) "Fecha estimada: entre marzo y abril de 2027" else null,
                settled = settled,
            )
        },

    )

    /**
     * El overlay como se ve (cabecera, carrusel y pie), en su alto natural: el carrusel toma el alto de su página más alta y el
     * pie va debajo. El título y la etiqueta salen de la misma función que usa la app ([activationTitleOf]).
     */
    @Composable
    private fun OverlayStack(full: Boolean, page: Int, notices: List<ActivationNotice>) {
        val ready = notices.none { it.blocking }
        Column(Modifier.width(sheetWidth.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
            ActivationHeader(
                title = activationTitleOf(alias = draft.name.ifBlank { null }, ready = ready),
                rise = { 1f },
                ready = ready,
            )
            ActivationPager(moments = moments(full), initialPage = page)
            ActivationFooter(
                state = SetupWizardState(draft = draft, requiresActivationConfirmation = full),
                notices = notices,
                activateEnabled = ready,
                onConfirmActivation = { },
                onConfirmFixedRecipe = { },
                onActivate = { },
            )
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
    fun renderTheCompleteActivationOverlay() {
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
                    // El overlay se monta de nuevo para cada captura: así el carrusel empieza en el momento pedido.
                    key(shownPage) { OverlayStack(full = true, page = shownPage, notices = completeNotices) }
                }
            }
        }
        // Una captura por momento y letra; el momento de Nutrición también a 320 dp de ancho (la pantalla pequeña del alta).
        val shots = listOf(
            Shot("review_overlay_tu_s100", 0, 1f, 360f),
            Shot("review_overlay_tu_s130", 0, 1.3f, 360f),
            Shot("review_overlay_programa_s100", 1, 1f, 360f),
            Shot("review_overlay_programa_s130", 1, 1.3f, 360f),
            Shot("review_overlay_nutricion_s100", 2, 1f, 360f),
            Shot("review_overlay_nutricion_s130", 2, 1.3f, 360f),
            Shot("review_overlay_nutricion_w320_s130", 2, 1.3f, 320f),
            Shot("review_overlay_rings_s100", 3, 1f, 360f),
            Shot("review_overlay_rings_s130", 3, 1.3f, 360f),
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

    /** Una captura: nombre del PNG, momento del carrusel, escala de letra y ancho de la hoja en dp. */
    private data class Shot(val name: String, val page: Int, val scale: Float, val width: Float)

    @Test
    fun renderTheEmptyStateOnce() {
        onlyWhenAsked()
        // Estado vacío: lo que bloquea la activación se dice en lenguaje llano; el encabezado no dice «todo listo».
        val emptyNotices = activationNoticesOf(
            state = SetupWizardState(draft = SetupWizardDraft()),
            nutritionPage = true,
        )
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1f),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                    OverlayStack(full = false, page = 1, notices = emptyNotices)
                }
            }
        }
        rule.waitForIdle()
        save("sheet", "review_overlay_vacio_s100")
    }

    @Test
    fun renderTheProgramCardInTheMiddleOfItsEntry() {
        onlyWhenAsked()
        // Reloj de pruebas a mano: la entrada de la tarjeta (360 ms) se detiene a mitad.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1f),
                LocalWizardReducedMotion provides false,
            ) {
                Column(
                    Modifier
                        .testTag("sheet")
                        .wrapContentSize()
                        .background(Color.Black)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Column(Modifier.width(312.dp)) {
                        ActivationProgramMoment(
                            draft = draft,
                            later = false,
                            card = card,
                            week = week,
                            startDay = 1,
                            minutes = 60,
                            settled = true,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(150)
        rule.waitForIdle()
        save("sheet", "review_programa_tarjeta_mitad_s100")
    }
}
