package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withMaterial
import com.example.kpkn.screens.onboarding.withPlaces
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TRAINING_VARIANTS: una celda por levantamiento del perfil (dos en Powerlifting, cuatro en Powerbuilding). Al entrar todas van
 * cerradas; al abrir una salen sus variantes; la de siempre sale elegida, tocar otra la escribe (`setLiftVariant`) y el material
 * quita las que no puede usar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntrenoVariantsStepTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("variants-step-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    /** Una celda de levantamiento: la etiqueta `setup-variant-<LIFT>` (dos guiones) y no sus palabras (que tienen tres). */
    private val variantRows = SemanticsMatcher("celda de variantes") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)
            ?.let { tag -> tag.startsWith("setup-variant-") && tag.count { it == '-' } == 2 } == true
    }

    /** Un panel de variantes desplegado: `setup-variant-panel-<LIFT>`. */
    private val openPanels = SemanticsMatcher("panel de variantes desplegado") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("setup-variant-panel-") == true
    }

    private fun stateIs(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private fun describedAs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private val gym = setOf(TrainingPlace.GYM)
    private val gymAvailability = SetupWizardDraft().withPlaces(gym).trainingOptions.availability
    private val dumbbellsAvailability = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
        .withMaterial(setOf(EquipmentSymbolId.DUMBBELLS)).trainingOptions.availability
    // La búlgara con mancuernas pide banco o cajón (`LiftVariants`): con mancuernas y banco sí sale.
    private val dumbbellsAndBenchAvailability = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
        .withMaterial(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH)).trainingOptions.availability

    @Test
    fun powerliftingAsksTwoLifts() {
        show(SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING))
        rule.onAllNodes(variantRows).assertCountEquals(2)
        rule.onNodeWithTag("setup-variant-SQUAT").assertExists()
        rule.onNodeWithTag("setup-variant-DEADLIFT").assertExists()
        rule.onNodeWithTag("setup-variant-BENCH").assertDoesNotExist()
    }

    @Test
    fun powerbuildingAsksFourLifts() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, gymAvailability, emptyMap()) { _, _ -> } }
        rule.onAllNodes(variantRows).assertCountEquals(4)
        rule.onNodeWithTag("setup-variant-OVERHEAD_PRESS").assertExists()
    }

    @Test
    fun atEntryNothingIsOpenAndEveryCellSaysClosed() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, gymAvailability, emptyMap()) { _, _ -> } }
        rule.onAllNodes(openPanels).assertCountEquals(0)
        rule.onNodeWithTag("setup-variant-SQUAT-high_bar_back_squat__barbell").assertDoesNotExist()
        rule.onNodeWithTag("setup-variant-SQUAT").assert(stateIs("Cerrado"))
        rule.onNodeWithTag("setup-variant-DEADLIFT").assert(stateIs("Cerrado"))
    }

    @Test
    fun theUsualVariantComesOutChosenAsASuggestion() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, gymAvailability, emptyMap()) { _, _ -> } }
        openCell("SQUAT")
        rule.onNodeWithTag("setup-variant-SQUAT-high_bar_back_squat__barbell").assertIsSelected()
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").assertIsNotSelected()
        rule.onNodeWithTag("setup-variant-SQUAT").assert(describedAs("Sentadilla, Barra alta"))
    }

    @Test
    fun aCellOpensItsVariantsAndTheOtherCloses() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, gymAvailability, emptyMap()) { _, _ -> } }
        openCell("SQUAT")
        rule.onNodeWithTag("setup-variant-panel-SQUAT").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT").assert(stateIs("Abierto"))
        openCell("BENCH")
        rule.onNodeWithTag("setup-variant-BENCH-bench_press__barbell").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").assertDoesNotExist()
        rule.onNodeWithTag("setup-variant-SQUAT").assert(stateIs("Cerrado"))
        rule.onNodeWithTag("setup-variant-BENCH").assert(stateIs("Abierto"))
        rule.onAllNodes(openPanels).assertCountEquals(1)
    }

    @Test
    fun tappingAnOpenCellClosesIt() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERLIFTING, gymAvailability, emptyMap()) { _, _ -> } }
        openCell("DEADLIFT")
        openCell("DEADLIFT")
        rule.onNodeWithTag("setup-variant-DEADLIFT").assert(stateIs("Cerrado"))
        rule.onAllNodes(openPanels).assertCountEquals(0)
    }

    @Test
    fun tappingSumoWritesItsVariantAndTappingTheStoredOneDoesNothing() {
        val writes = mutableListOf<Pair<LiftMark, String>>()
        rule.setContent {
            LiftVariantRows(
                goal = TrainingGoalProfile.POWERLIFTING,
                availability = gymAvailability,
                stored = mapOf(LiftMark.DEADLIFT to "conventional_deadlift__bilateral__barbell"),
            ) { lift, configId -> writes += lift to configId }
        }
        openCell("DEADLIFT")
        rule.onNodeWithTag("setup-variant-DEADLIFT-conventional_deadlift__bilateral__barbell").performClick()
        rule.waitForIdle()
        assertTrue("tocar la ya guardada no escribe: $writes", writes.isEmpty())
        rule.onNodeWithTag("setup-variant-DEADLIFT-sumo_deadlift__barbell").performClick()
        rule.waitForIdle()
        assertEquals(listOf(LiftMark.DEADLIFT to "sumo_deadlift__barbell"), writes)
    }

    @Test
    fun aSuggestionIsConfirmedByTouchingIt() {
        val writes = mutableListOf<Pair<LiftMark, String>>()
        rule.setContent {
            LiftVariantRows(TrainingGoalProfile.POWERLIFTING, gymAvailability, emptyMap()) { lift, configId -> writes += lift to configId }
        }
        openCell("DEADLIFT")
        rule.onNodeWithTag("setup-variant-DEADLIFT-conventional_deadlift__bilateral__barbell").performClick()
        rule.waitForIdle()
        assertEquals(listOf(LiftMark.DEADLIFT to "conventional_deadlift__bilateral__barbell"), writes)
    }

    @Test
    fun choosingAVariantUpdatesTheCellLineAndKeepsThePanelOpen() {
        rule.setContent { StoredRows(TrainingGoalProfile.POWERBUILDING, gymAvailability) }
        rule.onNodeWithTag("setup-variant-SQUAT").assert(describedAs("Sentadilla, Barra alta"))
        openCell("SQUAT")
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-variant-SQUAT").assert(describedAs("Sentadilla, Barra baja"))
        rule.onNodeWithTag("setup-variant-panel-SQUAT").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").assertIsSelected()
    }

    @Test
    fun withoutABarbellTheBarbellVariantsAreNotOffered() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, dumbbellsAndBenchAvailability, emptyMap()) { _, _ -> } }
        openCell("SQUAT")
        // El material sí cuenta: con mancuernas y banco aparece la búlgara con mancuernas, y no la frontal ni las de barra.
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__dumbbells").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT-front_squat__barbell").assertDoesNotExist()
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__barbell").assertDoesNotExist()
        rule.onNodeWithText("Frontal").assertDoesNotExist()
    }

    @Test
    fun withDumbbellsButNoBenchTheBulgarianIsNotOffered() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, dumbbellsAvailability, emptyMap()) { _, _ -> } }
        // La búlgara pide banco o cajón: con mancuernas solas no se ofrece, y sin otra variante de sentadilla no sale la celda.
        rule.onNodeWithTag("setup-variant-SQUAT").assertDoesNotExist()
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__dumbbells").assertDoesNotExist()
    }

    @Test
    fun theStepComposesWithTheDraftAndItsNote() {
        val draft = SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        show(draft)
        rule.onNodeWithText("Puedes cambiarlas cuando quieras desde tu programa.").assertExists()
    }

    /** Toca la celda del levantamiento [lift] (su nombre en `LiftMark`) y espera a que termine el despliegue. */
    private fun openCell(lift: String) {
        rule.onNodeWithTag("setup-variant-$lift").performClick()
        rule.waitForIdle()
    }

    /** Las filas con su propio estado, como en la app: lo que se elige se guarda y la celda lo vuelve a leer. */
    @Composable
    private fun StoredRows(goal: TrainingGoalProfile, availability: EquipmentAvailability?) {
        var stored by remember { mutableStateOf(emptyMap<LiftMark, String>()) }
        LiftVariantRows(goal = goal, availability = availability, stored = stored) { lift, configId ->
            stored = stored + (lift to configId)
        }
    }

    private fun show(draft: SetupWizardDraft) {
        val state = SetupWizardState(draft = draft)
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { EntrenoVariantsStep(state = state, vm = vm) }
        }
        rule.waitForIdle()
    }

    private object NoPersistence : SetupWizardPersistence {
        override suspend fun load(draftId: String): SetupDraft? = null
        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)

        override suspend fun discard(draftId: String) = Unit
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private object StubEnvironment : SetupWizardEnvironment {
        override val settings: Settings = Settings()
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
