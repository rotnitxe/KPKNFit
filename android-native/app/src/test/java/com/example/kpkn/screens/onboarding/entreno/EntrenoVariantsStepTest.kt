package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
 * TRAINING_VARIANTS: una fila por levantamiento del perfil (dos en Powerlifting, cuatro en Powerbuilding), la variante de siempre
 * sale elegida, tocar otra la escribe (`setLiftVariant`) y el material quita las que no puede usar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
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

    /** Una fila de variantes: la etiqueta `setup-variant-<LIFT>` (dos guiones) y no sus palabras (que tienen tres). */
    private val variantRows = SemanticsMatcher("fila de variantes") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)
            ?.let { tag -> tag.startsWith("setup-variant-") && tag.count { it == '-' } == 2 } == true
    }

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
    fun theUsualVariantComesOutChosenAsASuggestion() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, gymAvailability, emptyMap()) { _, _ -> } }
        rule.onNodeWithTag("setup-variant-SQUAT-high_bar_back_squat__barbell").assertIsSelected()
        rule.onNodeWithTag("setup-variant-SQUAT-low_bar_back_squat__barbell").assertIsNotSelected()
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
        rule.onNodeWithTag("setup-variant-DEADLIFT-conventional_deadlift__bilateral__barbell").performClick()
        rule.waitForIdle()
        assertEquals(listOf(LiftMark.DEADLIFT to "conventional_deadlift__bilateral__barbell"), writes)
    }

    @Test
    fun withoutABarbellTheBarbellVariantsAreNotOffered() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, dumbbellsAndBenchAvailability, emptyMap()) { _, _ -> } }
        // El material sí cuenta: con mancuernas y banco aparece la búlgara con mancuernas, y no la frontal ni las de barra.
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__dumbbells").assertExists()
        rule.onNodeWithTag("setup-variant-SQUAT-front_squat__barbell").assertDoesNotExist()
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__barbell").assertDoesNotExist()
        rule.onNodeWithText("Frontal").assertDoesNotExist()
    }

    @Test
    fun withDumbbellsButNoBenchTheBulgarianIsNotOffered() {
        rule.setContent { LiftVariantRows(TrainingGoalProfile.POWERBUILDING, dumbbellsAvailability, emptyMap()) { _, _ -> } }
        // La búlgara pide banco o cajón: con mancuernas solas no se ofrece.
        rule.onNodeWithTag("setup-variant-SQUAT-bulgarian_split_squat__dumbbells").assertDoesNotExist()
    }

    @Test
    fun theStepComposesWithTheDraftAndItsNote() {
        val draft = SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        show(draft)
        rule.onNodeWithText("Puedes cambiarlas cuando quieras desde tu programa.").assertExists()
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
