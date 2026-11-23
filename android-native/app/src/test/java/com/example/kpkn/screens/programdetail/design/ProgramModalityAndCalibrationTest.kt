package com.example.kpkn.screens.programdetail.design

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.VolumeCalibrationEngine
import com.example.kpkn.screens.onboarding.SetupVolumeAnswers
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.resolvedVolumeCalibrationProfile
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationSheet
import com.example.kpkn.screens.programs.ProgramsViewModel
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.testsupport.SyncRoomExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1400dp-mdpi")
class ProgramModalityAndCalibrationTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        context = ApplicationProvider.getApplicationContext()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        SyncRoomExecutor.uninstall()
    }

    @Test
    fun createBlankProgramWithPowerbuildingSetsModeAndClearsStandardIdentityLabel() {
        val repo = ProgramRepository.initForTests(context)
        val vm = ProgramsViewModel(context as Application)

        val id = vm.createBlankProgram(
            name = "Mi Programa",
            identityLabel = "Powerbuilding",
        )
        val created = repo.programs.value.first { it.id == id }
        assertEquals(ProgramMode.POWERBUILDING, created.mode)
        assertNull("Modalidad estándar no debe quedar duplicada en identityLabel", created.identityLabel)

        val draft = programEditDraftOf(created)
        assertEquals(ProgramMode.POWERBUILDING, draft.mode)
        assertFalse(draft.canSave(created))

        val switchedToHypertrophy = draft.copy(mode = ProgramMode.HYPERTROPHY)
        assertTrue(switchedToHypertrophy.canSave(created))
    }

    @Test
    fun legacyProgramWithMismatchedIdentityLabelAndModeAllowsSavingAndReflectsDisplayedMode() {
        val legacyProgram = Program(
            id = "legacy-pb",
            name = "Programa Manual",
            mode = ProgramMode.HYPERTROPHY,
            identityLabel = "Powerbuilding",
        )
        // El borrador arranca con la modalidad efectiva mostrada (Powerbuilding)
        val initialDraft = programEditDraftOf(legacyProgram)
        assertEquals(ProgramMode.POWERBUILDING, initialDraft.mode)

        // Al cambiar a Hipertrofia, canSave debe ser true porque difiere de la modalidad efectiva mostrada ("Powerbuilding")
        val changedToHypertrophy = initialDraft.copy(mode = ProgramMode.HYPERTROPHY)
        assertTrue(changedToHypertrophy.canSave(legacyProgram))
    }

    @Test
    fun volumeCalibrationSheetAndPartialWizardAnswersPreserveDeclaredResponsesAndNotes() {
        val partialDraft = SetupWizardDraft(
            volumeAnswers = SetupVolumeAnswers(
                technique = 3,
                consistency = 1,
                strength = 3,
                mobility = null,
                responseState = CalibrationResponseState.DECLARED,
            ),
        )
        // Aunque movilidad se omitiera, resolvedVolumeCalibrationProfile conserva 3, 1, 3 y usa 2 en movilidad
        val resolved = partialDraft.resolvedVolumeCalibrationProfile()
        assertNotNull(resolved)
        assertEquals(3, resolved!!.athleteProfileScore.technicalScore)
        assertEquals(1, resolved.athleteProfileScore.consistencyScore)
        assertEquals(3, resolved.athleteProfileScore.strengthScore)
        assertEquals(2, resolved.athleteProfileScore.mobilityScore)

        val calc = VolumeCalibrationEngine.calculate(TrainingStyle.POWERBUILDER, 3, 1, 3, 2)
        val settingsProfile = VolumeCalibrationProfile(
            trainingStyle = TrainingStyle.POWERBUILDER,
            athleteProfileScore = calc.score,
            responses = VolumeCalibrationResponses(3, 1, 3, 2, CalibrationResponseState.DECLARED),
            recommendations = calc.recommendations,
            calibratedAtMs = 1000L,
            calculatorRevision = VolumeCalibrationEngine.REVISION,
            notes = "Priorizo press de banca",
        )

        var savedResult: VolumeCalibrationResult? = null
        rule.setContent {
            EditorOverlayTestHost {
                VolumeCalibrationSheet(
                    currentMode = ProgramMode.POWERBUILDING,
                    initialScore = settingsProfile.athleteProfileScore,
                    initialNotes = settingsProfile.notes,
                    onDismiss = {},
                    onSave = { savedResult = it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Priorizo press de banca").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Guardar respuestas").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertNotNull(savedResult)
        assertEquals(TrainingStyle.POWERBUILDER, savedResult!!.score.trainingStyle)
        assertEquals(3, savedResult!!.score.technicalScore)
        assertEquals(1, savedResult!!.score.consistencyScore)
        assertEquals(3, savedResult!!.score.strengthScore)
        assertEquals(2, savedResult!!.score.mobilityScore)
        assertEquals("Priorizo press de banca", savedResult!!.notes)
    }
}
