package com.example.kpkn.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.DetailSheet
import com.example.kpkn.screens.onboarding.design.entreno.plan.EXERCISE_INFO_IMAGE_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.EXERCISE_INFO_JOINTS_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogImageVariants
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
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
 * HOJAS DE DIBUJO de la ficha de un ejercicio (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR` apuntando a una
 * carpeta; sin ella se salta. Pinta [DetailSheet] a 360 dp con «Tu semana» del jueves desplegada (sin miniaturas), la ficha con
 * imagen y la ficha sin imagen, con la letra al 100 % y al 130 %, y las articulaciones tras desplazarla.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ExerciseInfoRenderSheetTest {

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

    /** La vista de Compose del host, para dibujarla a un bitmap (igual que en [PlanDetailDayRenderSheetTest]). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Before
    fun preloadCatalog() {
        runBlocking { CatalogV2ProcessCache.getOrLoad(ApplicationProvider.getApplicationContext<Context>()) }
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

    private val catalog: ExerciseCatalogV2 get() = CatalogCompositionTestSupport.catalog

    private fun definitionOf(id: String) = catalog.families.asSequence()
        .flatMap { it.definitions.asSequence() }
        .first { it.id == id }

    /** Un ejercicio sin imagen del catálogo (el primero que el buscador no ilustra). */
    private fun withoutImage() = catalog.families.asSequence()
        .flatMap { it.definitions.asSequence() }
        .first { definition ->
            exerciseCatalogImageVariants(definition.id, definition.defaultConfigurationId).isEmpty()
        }

    private fun exerciseOf(instanceId: String, definitionId: String, configurationId: String, sets: String) = run {
        val def = definitionOf(definitionId)
        SetupPlanRevealExercise(
            instanceId = instanceId,
            name = def.canonicalName.trim(),
            setsLabel = sets,
            swappable = true,
            catalogRevision = catalog.catalogRevision,
            catalogDefinitionId = def.id,
            catalogConfigurationId = configurationId,
        )
    }

    private val card = PlanCardModel(
        id = "fuerza", title = "Fuerza y masa", kicker = "4 SEMANAS · PERIODIZADO",
        blurb = "Un programa de fuerza con tres días a la semana.", profile = TrainingGoalProfile.STRENGTH_MUSCLE,
        daysLabel = "3 días", minutesLabel = "~60 min", levelLabel = "Intermedio", coverSeed = 3,
    )

    private val model = PlanDetailModel(
        card = card,
        description = "Tres días a la semana, con la fuerza en los básicos y el volumen en los accesorios.",
        mainExercises = listOf("Press de Banca Plano", "Sentadilla Frontal"),
        blocks = listOf(PlanBlockModel("Volumen", "Semanas 1–2", "Más series y repeticiones.")),
        week = listOf(
            PlanDayModel(1, "Torso fuerza", 60, 4, true),
            PlanDayModel(4, "Pierna", 55, 4, false),
        ),
        reasons = listOf("Entrenas 3 días: es la frecuencia que pediste."),
        notes = emptyList(),
        attribution = null,
    )

    /** El jueves: con imagen (press de banca y sentadilla frontal), sin imagen (el primero del catálogo sin ella) y otro con imagen. */
    private fun jueves(): Map<Int, List<SetupPlanRevealExercise>> {
        val sinImagen = withoutImage()
        return mapOf(
            4 to listOf(
                exerciseOf("jue-1", "bench_press", "bench_press__barbell", "4 × 6–8"),
                exerciseOf("jue-2", "front_squat", "front_squat__barbell", "4 × 5–6"),
                SetupPlanRevealExercise(
                    instanceId = "jue-3",
                    name = sinImagen.canonicalName.trim(),
                    setsLabel = "3 × 8–10",
                    swappable = true,
                    catalogRevision = catalog.catalogRevision,
                    catalogDefinitionId = sinImagen.id,
                    catalogConfigurationId = sinImagen.defaultConfigurationId,
                ),
            ),
        )
    }

    /** La hoja a [scale] de letra (1 o 1,3), con el jueves como día inicial. */
    private fun host(scale: Float) {
        val exercises = jueves()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                DetailSheet(
                    model = model,
                    selected = true,
                    reduced = true,
                    blur = false,
                    shown = { 1f },
                    onChoose = {},
                    onClose = {},
                    weekStartDay = 1,
                    exercises = exercises,
                    swapOptionsOf = { emptyList() },
                    onSwap = { _, _ -> },
                    onUndoSwap = {},
                )
            }
        }
    }

    private fun openDay() {
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
    }

    private fun openInfo(instanceId: String) {
        openDay()
        rule.onNodeWithTag("setup-plan-exercise-$instanceId").performScrollTo().performClick()
    }

    // Cada captura es su propio test: Compose solo admite un setContent por test.

    @Test
    fun renderTheDayWithoutThumbnailsAt100() {
        onlyWhenAsked()
        host(1f)
        openDay()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        save("exercise_info_day_s100")
    }

    @Test
    fun renderTheDayWithoutThumbnailsAt130() {
        onlyWhenAsked()
        host(1.3f)
        openDay()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        save("exercise_info_day_s130")
    }

    @Test
    fun renderTheInfoWithImageAt100() {
        onlyWhenAsked()
        host(1f)
        openInfo("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertExists()
        save("exercise_info_image_s100")
    }

    @Test
    fun renderTheInfoWithImageAt130() {
        onlyWhenAsked()
        host(1.3f)
        openInfo("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertExists()
        save("exercise_info_image_s130")
        rule.onNodeWithTag(EXERCISE_INFO_JOINTS_TAG).performScrollTo()
        save("exercise_info_joints_s130")
    }

    @Test
    fun renderTheInfoWithoutImageAt100() {
        onlyWhenAsked()
        host(1f)
        openInfo("jue-3")
        rule.onNodeWithText("Sin imagen revisada para esta configuración.").assertExists()
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertDoesNotExist()
        save("exercise_info_noimage_s100")
    }

    @Test
    fun renderTheInfoWithoutImageAt130() {
        onlyWhenAsked()
        host(1.3f)
        openInfo("jue-3")
        rule.onNodeWithText("Sin imagen revisada para esta configuración.").assertExists()
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertDoesNotExist()
        save("exercise_info_noimage_s130")
    }
}
