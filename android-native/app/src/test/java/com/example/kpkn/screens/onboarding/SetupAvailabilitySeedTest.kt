package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.data.onboarding.SetupPatchField
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.RingsCompletion
import com.example.kpkn.domain.onboarding.SetupRingsMapping
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupAvailabilitySeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private lateinit var app: Application
    private lateinit var persistence: MemoryPersistence

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = MemoryPersistence()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun fresh_draft_seeds_stored_categories_as_an_undeclared_suggestion() = runTest {
        val suggested = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES))
        val vm = viewModel(Settings(equipmentAvailability = suggested))

        vm.initialize(SetupWizardMode.FULL, draftId = "fresh-availability")
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(suggested, draft.trainingOptions.availability)
        assertFalse(SetupStepId.HOME_EQUIPMENT in draft.declaredSteps)
        assertFalse(draft.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, draft, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )
    }

    @Test
    fun settings_patch_keeps_unanswered_null_and_persists_explicit_none() {
        val vm = viewModel(Settings())
        val unanswered = SetupWizardDraft()
        val selectedNone = unanswered.copy(
            trainingOptions = SetupTrainingOptions(availability = EquipmentAvailability()),
        ).touchStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, unanswered, Settings()).equipmentAvailability,
        )
        assertEquals(
            SetupPatchField.Set(EquipmentAvailability()),
            buildSettingsPatch(vm, selectedNone, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun confirming_an_untouched_availability_suggestion_persists_it_without_declaring_it() = runTest {
        val suggested = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES))
        val vm = viewModel(Settings(equipmentAvailability = suggested))

        vm.initialize(SetupWizardMode.FULL, draftId = "confirm-availability-suggestion")
        advanceUntilIdle()

        val seeded = vm.state.value.draft
        assertEquals(suggested, seeded.trainingOptions.availability)
        assertFalse(seeded.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, seeded, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )

        val confirmed = seeded.confirmCurrentStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(
            SetupAnswerProvenance.SUGGESTED,
            confirmed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT],
        )
        assertFalse("Accepting a suggestion must not turn it into a declaration", confirmed.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Set(suggested),
            buildSettingsPatch(vm, confirmed, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )
    }

    @Test
    fun confirming_an_empty_availability_suggestion_persists_explicit_empty() = runTest {
        val suggestedNone = EquipmentAvailability(emptySet())
        val vm = viewModel(Settings(equipmentAvailability = suggestedNone))

        vm.initialize(SetupWizardMode.FULL, draftId = "confirm-empty-availability-suggestion")
        advanceUntilIdle()

        val seeded = vm.state.value.draft
        assertFalse(seeded.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        val confirmed = seeded.confirmCurrentStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(SetupAnswerProvenance.SUGGESTED, confirmed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
        assertFalse(confirmed.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Set(EquipmentAvailability(emptySet())),
            buildSettingsPatch(vm, confirmed, Settings(equipmentAvailability = suggestedNone)).equipmentAvailability,
        )
    }

    @Test
    fun restoring_a_null_availability_draft_does_not_merge_the_settings_suggestion() = runTest {
        val id = "restored-availability"
        val restored = SetupWizardDraft(
            draftId = id,
            commitId = "restored-commit",
            draftScope = "full",
            trainingOptions = SetupTrainingOptions(availability = null),
        ).let { it.copy(stepProgress = SetupStepProgress.initial(it.stepContext())) }
        persistence.seed(restored)
        val vm = viewModel(Settings(equipmentAvailability = EquipmentAvailability(setOf(EquipmentCategory.CARDIO))))

        vm.initialize(SetupWizardMode.RESUME, draftId = id)
        advanceUntilIdle()

        assertNull(vm.state.value.draft.trainingOptions.availability)
    }

    @Test
    fun draft_json_and_equipment_footprint_keep_null_empty_and_change_impact_distinct() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacyDraft = json.decodeFromString<SetupWizardDraft>("""{"trainingOptions":{}}""")
        val explicitNone = SetupWizardDraft(
            trainingOptions = SetupTrainingOptions(availability = EquipmentAvailability()),
        )
        val roundTrip = json.decodeFromString<SetupWizardDraft>(json.encodeToString(explicitNone))
        assertNull(legacyDraft.trainingOptions.availability)
        assertEquals(EquipmentAvailability(emptySet()), roundTrip.trainingOptions.availability)
        assertFalse(legacyDraft.inputFootprint().equipmentAvailability == roundTrip.inputFootprint().equipmentAvailability)

        val withBarbell = explicitNone.copy(
            trainingOptions = SetupTrainingOptions(
                availability = EquipmentAvailability(setOf(EquipmentCategory.BARBELL)),
            ),
        )
        assertEquals(
            setOf(SetupChangeSource.EQUIPMENT),
            SetupChangeDetector.sourcesFor(explicitNone.inputFootprint(), withBarbell.inputFootprint()),
        )
        val impacted = withBarbell.withChangeImpacts(explicitNone)
        assertTrue(impacted.stepProgress.pendingReview.contains(SetupStepId.PLAN))
        assertTrue(
            impacted.stepProgress.stalePreviews.containsAll(
                setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS),
            ),
        )
    }

    private fun viewModel(settings: Settings): SetupWizardViewModel =
        SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = persistence,
            environment = TestEnvironment(settings),
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { viewModels.put("availability-${viewModels.hashCode()}-${System.identityHashCode(it)}", it) }

    private fun buildSettingsPatch(
        viewModel: SetupWizardViewModel,
        draft: SetupWizardDraft,
        base: Settings,
    ): SetupSettingsPatch {
        val builder = SetupWizardViewModel::class.java.getDeclaredMethod(
            "buildSettingsPatch",
            Settings::class.java,
            SetupWizardDraft::class.java,
            Program::class.java,
            NutritionPlan::class.java,
            SetupRingsMapping::class.java,
            Boolean::class.javaPrimitiveType!!,
        ).apply { isAccessible = true }
        return builder.invoke(
            viewModel,
            base,
            draft,
            null,
            null,
            SetupRingsMapping(RingsCompletion.UNKNOWN),
            false,
        ) as SetupSettingsPatch
    }

    private class MemoryPersistence : SetupWizardPersistence {
        private val rows = linkedMapOf<String, SetupDraft>()
        private val json = Json { encodeDefaults = true }

        fun seed(draft: SetupWizardDraft) {
            rows[draft.draftId] = SetupDraft(
                draftId = draft.draftId,
                payloadJson = json.encodeToString(draft),
                revision = draft.revision.toLong(),
                catalogRevision = draft.catalogRevision,
                updatedAtEpochMs = 0L,
            )
        }

        override suspend fun load(draftId: String): SetupDraft? = rows[draftId]

        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
            .also { rows[draftId] = it }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = rows.values.map {
            SetupDraftCandidate(it.draftId, SetupDraftScope.FULL, it.revision, it.updatedAtEpochMs, it.catalogRevision)
        }
    }

    private class TestEnvironment(
        override val settings: Settings,
    ) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
