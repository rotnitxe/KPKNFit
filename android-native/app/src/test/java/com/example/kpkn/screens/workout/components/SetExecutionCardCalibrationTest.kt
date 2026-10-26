package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w360dp-h880dp-xxhdpi", application=android.app.Application::class)
class SetExecutionCardCalibrationTest {
    @get:Rule val compose = createComposeRule()
    private val holder = RecordActionHolder()
    private val suggestion = mutableStateOf(WeightSuggestion(155.0, "Protocol assignment", isCalibrationAssignment=true))
    private val recorded = mutableListOf<Double>()
    private val set = ExerciseSet("s4", weight=150.0, purpose=SetPurpose.AMRAP, amrapMinimumReps=10,
        loadModeV2=LoadModeV2.LOAD, unitModeV2=UnitModeV2.REPS)
    private fun show(draft: WorkoutSetDraft? = null, completed: CompletedSet? = null) {
        val exercise = Exercise("squat", "Squat", sets=listOf(set))
        compose.setContent { MaterialTheme { Box(Modifier.width(272.dp)) {
            SetInputCardV2(exercise, 0, set, null, sessionCompletedSet=completed,
                weightSuggestion=suggestion.value, initialDraft=draft, initialBodyWeight=70.0,
                recordActionHolder=holder, recordActionPageKey="s4",
                onShowHistory={}, onSetBodyWeight={},
                onRecordV2={ _, _, weight, _, _, _, _, _, _, onResult ->
                    recorded += weight; onResult(RecordSetResult.Created("squat_0"))
                })
        } } }
        compose.waitForIdle()
    }
    private fun record() {
        compose.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { checkNotNull(holder.actionForPage("s4")).invoke() }
        compose.waitForIdle()
    }
    @Test fun confirmedAssignmentRecords155WithoutTouchingLoad() {
        show(); record(); assertEquals(155.0, recorded.single(), 0.0)
    }
    @Test fun advisoryWithSameLabelDoesNotApplyWithoutConfirmation() {
        suggestion.value=WeightSuggestion(155.0, "Protocol assignment")
        show(); record(); assertEquals(150.0, recorded.single(), 0.0)
    }
    @Test fun confirmationAndRevertRefreshAnAlreadyComposedPendingCard() {
        suggestion.value=WeightSuggestion(150.0, "Pending", isCalibrationAssignment=true)
        show()
        compose.runOnIdle { suggestion.value=WeightSuggestion(155.0, "Renamed display text", isCalibrationAssignment=true) }
        compose.waitForIdle(); record(); assertEquals(155.0, recorded.single(), 0.0)
        compose.runOnIdle { suggestion.value=WeightSuggestion(150.0, "Reverted", isCalibrationAssignment=true) }
        compose.waitForIdle(); record(); assertEquals(listOf(155.0,150.0), recorded)
    }
    @Test fun explicitManualLoadIsPreserved() {
        show(draft=WorkoutSetDraft(weightText="160", weightManuallyFixed=true, isDirty=true))
        record(); assertEquals(160.0, recorded.single(), 0.0)
    }
}
