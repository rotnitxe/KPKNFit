package com.example.kpkn.screens.onboarding.design.entreno.plan

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.muscleLabel
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.sessioneditor.components.jointLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La ficha de un ejercicio del detalle: se abre desde su fila (sin miniatura) y enseña lo que el CATÁLOGO dice de él: la
 * descripción de su definición tal cual, sus músculos, sus articulaciones y su imagen o «Sin imagen». Un cambio enseña la
 * ficha de su sustituto. Se cierra con «Cerrar», atrás o tocando el velo.
 */
@RunWith(RobolectricTestRunner::class)
// Ventana alta: con la de por defecto la barra inferior tapa el día y el toque no llega (como en PlanDetailDayExercisesTest).
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
// Gráficos nativos: los títulos se miden de verdad y las imágenes del catálogo se cargan.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseInfoOverlayTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** El catálogo se carga como en la app (caché del proceso): la ficha lo lee al instante y no espera a un hilo de fondo. */
    @Before
    fun preloadCatalog() {
        runBlocking { CatalogV2ProcessCache.getOrLoad(ApplicationProvider.getApplicationContext<Context>()) }
    }

    /** El catálogo real de pruebas (el mismo JSON de los assets): las expectativas se leen de aquí, no se copian. */
    private val catalog: ExerciseCatalogV2 get() = CatalogCompositionTestSupport.catalog

    private fun definitionOf(id: String) = catalog.families.asSequence()
        .flatMap { it.definitions.asSequence() }
        .first { it.id == id }

    private val card = PlanCardModel(
        id = "fuerza", title = "Fuerza y masa", kicker = "3 días", blurb = "Un programa de fuerza.",
        profile = TrainingGoalProfile.STRENGTH_MUSCLE, daysLabel = "3 días", minutesLabel = "60 min", levelLabel = "Intermedio",
        coverSeed = 3,
    )

    /** Torso el lunes y pierna el jueves: el jueves es el día que se despliega en las pruebas. */
    private val model = PlanDetailModel(
        card = card,
        description = "Tres días a la semana.",
        mainExercises = listOf("Press de Banca Plano"),
        blocks = listOf(PlanBlockModel("Volumen", "Semanas 1–4", "Más series.")),
        week = listOf(PlanDayModel(1, "Torso", 60, 2, true), PlanDayModel(4, "Pierna", 50, 2, false)),
        reasons = emptyList(),
        notes = emptyList(),
        attribution = null,
    )

    /** Press de banca con barra: tiene imagen del catálogo y su nombre es el del catálogo. */
    private fun benchPress(instanceId: String = "jue-1") = SetupPlanRevealExercise(
        instanceId = instanceId,
        name = "Press de Banca Plano",
        setsLabel = "4 × 6–8",
        swappable = true,
        catalogRevision = catalog.catalogRevision,
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__barbell",
    )

    private fun show(exercises: List<SetupPlanRevealExercise>) {
        rule.setContent {
            DetailSheet(
                model = model,
                selected = true,
                reduced = true,
                blur = false,
                shown = { 1f },
                onChoose = {},
                onClose = {},
                exercises = mapOf(4 to exercises),
                swapOptionsOf = { emptyList() },
                onSwap = { _, _ -> },
                onUndoSwap = {},
            )
        }
    }

    /** Despliega el jueves y toca el ejercicio [instanceId]: debe abrirse su ficha. */
    private fun openExercise(instanceId: String) {
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-$instanceId").performScrollTo().performClick()
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertExists()
    }

    @Test
    fun theDayRowHasNoThumbnailAndOpensItsSheet() {
        show(listOf(benchPress()))
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1")
            .assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("Ver ficha de Press de Banca Plano")
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").performClick()
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertExists()
    }

    @Test
    fun theDescriptionIsTheCatalogDefinitionDescriptionExactly() {
        show(listOf(benchPress()))
        openExercise("jue-1")
        val definition = definitionOf("bench_press")
        assertTrue(definition.description.isNotBlank())
        rule.onNodeWithTag(EXERCISE_INFO_DESCRIPTION_TAG).assertTextEquals(definition.description)
    }

    @Test
    fun theMusclesAndJointsAreTheCatalogsWithTheAppLabels() {
        show(listOf(benchPress()))
        openExercise("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_MUSCLES_TAG).assertTextContains(muscleLabel("pectoralis")!!, substring = true)
        rule.onNodeWithTag(EXERCISE_INFO_JOINTS_TAG).assertTextContains(jointLabel("glenohumeral"), substring = true)
    }

    @Test
    fun aCatalogImageIsShownWhenThereIsOne() {
        show(listOf(benchPress()))
        openExercise("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertExists()
        rule.onNodeWithTag(EXERCISE_INFO_NOIMAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun anExerciseWithoutImageSaysSinImagen() {
        val noImage = catalog.families.asSequence()
            .flatMap { it.definitions.asSequence() }
            .first { def ->
                ExerciseTechniqueImageLookup.resolveImageResId(def.id, null, def.id, def.defaultConfigurationId) == null
            }
        show(
            listOf(
                SetupPlanRevealExercise(
                    instanceId = "jue-1",
                    name = noImage.canonicalName.trim(),
                    setsLabel = "3 × 8",
                    swappable = true,
                    catalogRevision = catalog.catalogRevision,
                    catalogDefinitionId = noImage.id,
                    catalogConfigurationId = noImage.defaultConfigurationId,
                ),
            ),
        )
        openExercise("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_NOIMAGE_TAG).assertExists()
        rule.onNodeWithText(ExerciseInfoCopy.IMAGE_MISSING).assertExists()
        rule.onNodeWithTag(EXERCISE_INFO_IMAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun aSwappedExerciseShowsTheSheetOfItsSubstitute() {
        val substitute = definitionOf("front_squat")
        show(
            listOf(
                benchPress().copy(name = substitute.canonicalName.trim(), swappedConfigId = "front_squat__barbell"),
            ),
        )
        openExercise("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_DESCRIPTION_TAG).assertTextEquals(substitute.description)
        rule.onNodeWithTag(EXERCISE_INFO_JOINTS_TAG).assertTextContains(jointLabel("rodilla"), substring = true)
    }

    @Test
    fun anExerciseOutsideTheCatalogSaysSo() {
        show(listOf(SetupPlanRevealExercise(instanceId = "jue-1", name = "Mi ejercicio", setsLabel = null, swappable = false)))
        openExercise("jue-1")
        rule.onNodeWithText(ExerciseInfoCopy.NOT_IN_CATALOG).assertExists()
        rule.onNodeWithTag(EXERCISE_INFO_MUSCLES_TAG).assertDoesNotExist()
    }

    @Test
    fun closeButtonAndBackCloseTheSheet() {
        show(listOf(benchPress()))
        openExercise("jue-1")
        rule.onNodeWithTag(EXERCISE_INFO_CLOSE_TAG).performClick()
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").performClick()
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingTheVeilClosesTheSheet() {
        show(listOf(benchPress()))
        openExercise("jue-1")
        rule.onRoot().performTouchInput { click(Offset(20f, 20f)) }
        rule.onNodeWithTag(EXERCISE_INFO_TAG).assertDoesNotExist()
    }
}
