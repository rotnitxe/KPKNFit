package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceRuntime
import com.example.kpkn.services.workout.VoiceSessionCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutVoiceCommandHandlerWarmupTest {

    @After
    fun clearRuntimeCallbacks() {
        WorkoutVoiceRuntime.registerActionSink(null)
        WorkoutVoiceRuntime.registerStopCaptureHandler(null)
    }

    @Test
    fun unreachable_load_is_reported_as_unknown_and_auto_feedback_has_no_zero_kg() {
        val exercise = exercise(percentages = listOf(0.4, 0.6))
        val ports = RecordingPorts(
            exercise = exercise,
            suggestedLoads = mapOf(0 to null, 1 to null),
            workingAnchor = null,
        )

        runWarmupCommand(
            exercise = exercise,
            ports = ports,
            command = VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = null,
                reps = null,
                effort = null,
            ),
        )

        assertEquals(1, ports.reports.size)
        assertNull(ports.reports.single().weightKg)
        assertEquals(6, ports.reports.single().reps ?: -1)
        val feedback = ports.autoRegulationFeedback.single()
        assertFalse(feedback.contains("0"))
        assertFalse(feedback.contains("kilos"))
        assertTrue(ports.events.indexOf("record-effort") < ports.events.indexOf("suggest-1"))
    }

    @Test
    fun explicit_manual_weight_23_5_is_kept_when_reporting_and_speaking() {
        val exercise = exercise(percentages = listOf(0.4, 0.6))
        val ports = RecordingPorts(
            exercise = exercise,
            suggestedLoads = mapOf(0 to 20.0, 1 to null),
            workingAnchor = 100.0,
        )

        runWarmupCommand(
            exercise = exercise,
            ports = ports,
            command = VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = 23.5,
                reps = 9,
                effort = WarmupEffort.NORMAL,
            ),
        )

        assertEquals(23.5, ports.reports.single().weightKg!!, 0.0)
        assertEquals(9, ports.reports.single().reps ?: -1)
        assertTrue(ports.autoRegulationFeedback.single().contains("23.5 kilos"))
    }

    @Test
    fun reachable_next_load_from_warmup_port_replaces_calibration_percentage() {
        val exercise = exercise(percentages = listOf(0.4, 0.6), workingWeight = 100.0)
        val ports = RecordingPorts(
            exercise = exercise,
            suggestedLoads = mapOf(0 to 40.0, 1 to 55.0),
            workingAnchor = 100.0,
        )

        runWarmupCommand(
            exercise = exercise,
            ports = ports,
            command = VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = 23.5,
                reps = null,
                effort = WarmupEffort.HEAVY,
            ),
        )

        // The theoretical 60% load after a heavy report is 58.5 kg; the
        // equipment-aware port's reachable 55 kg is what the handler announces.
        val feedback = ports.autoRegulationFeedback.single()
        assertTrue(feedback.contains("Siguiente aproximación calibrada a 55 kilos"))
        assertFalse(feedback.contains("58.5"))
        assertTrue(ports.events.indexOf("record-effort") < ports.events.indexOf("suggest-1"))
        assertTrue(ports.events.indexOf("suggest-1") < ports.events.indexOf("speak-auto"))
    }

    @Test
    fun final_transition_without_a_real_working_anchor_does_not_infer_one_from_warmup_load() {
        val exercise = exercise(percentages = listOf(0.4))
        val ports = RecordingPorts(
            exercise = exercise,
            suggestedLoads = mapOf(0 to 40.0),
            workingAnchor = null,
        )

        runWarmupCommand(
            exercise = exercise,
            ports = ports,
            warmupId = "warmup-0",
            command = VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = null,
                reps = null,
                effort = WarmupEffort.NORMAL,
            ),
        )

        assertEquals(40.0, ports.reports.single().weightKg!!, 0.0)
        assertEquals(1, ports.transitions.size)
        assertNull(ports.transitions.single().firstEffectiveKg)
    }

    @Test
    fun fractional_percentage_uses_the_real_anchor_without_inflating_by_one_hundred() {
        val exercise = exercise(percentages = listOf(0.4), workingWeight = 100.0)
        val ports = RecordingPorts(
            exercise = exercise,
            suggestedLoads = mapOf(0 to 40.0),
            workingAnchor = null,
        )

        runWarmupCommand(
            exercise = exercise,
            ports = ports,
            warmupId = "warmup-0",
            command = VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = null,
                reps = null,
                effort = WarmupEffort.NORMAL,
            ),
        )

        assertEquals(100.0, ports.transitions.single().firstEffectiveKg!!, 0.0)
        assertTrue(ports.transitions.single().firstEffectiveKg!! < 1_000.0)
    }

    private fun exercise(
        percentages: List<Double>,
        workingWeight: Double? = null,
    ): Exercise = Exercise(
        id = "exercise-1",
        name = "Press",
        sets = listOf(ExerciseSet(id = "working-0", weight = workingWeight, targetReps = 8)),
        warmupSets = percentages.mapIndexed { index, percentage ->
            WarmupSetDefinition(
                id = "warmup-$index",
                percentageOfWorkingWeight = percentage,
                targetReps = 6,
            )
        },
    )

    private fun runWarmupCommand(
        exercise: Exercise,
        ports: RecordingPorts,
        command: VoiceSessionCommand.RecordWarmupEffortAndLoad,
        warmupId: String = "warmup-0",
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val step = WorkoutStep(
            type = WorkoutStepType.WARMUP,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            stepKey = "step-$warmupId",
            warmupSetId = warmupId,
        )
        var state = WorkoutUiState(
            currentExerciseIdx = 0,
            activeStepKey = step.stepKey,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val handler = WorkoutVoiceCommandHandler(
                appContext = context,
                scope = scope,
                voiceRecognizer = WorkoutVoiceRecognizer(context),
                voiceController = WorkoutVoiceController(context),
                getState = { state },
                updateState = { transform -> state = transform(state) },
                ports = ports.asPorts,
            )
            handler.handleVoiceCommand(command)
        } finally {
            scope.cancel()
        }
    }

    private class RecordingPorts(
        private val exercise: Exercise,
        private val suggestedLoads: Map<Int, Double?>,
        private val workingAnchor: Double?,
    ) {
        data class Report(val weightKg: Double?, val reps: Int?)
        data class Transition(val firstEffectiveKg: Double?)

        val events = mutableListOf<String>()
        val reports = mutableListOf<Report>()
        val autoRegulationFeedback = mutableListOf<String>()
        val transitions = mutableListOf<Transition>()

        val asPorts: WorkoutVoiceCommandHandler.Ports = Proxy.newProxyInstance(
            WorkoutVoiceCommandHandler.Ports::class.java.classLoader,
            arrayOf(WorkoutVoiceCommandHandler.Ports::class.java),
        ) { _, method, args ->
            val arguments = args.orEmpty()
            when (method.name) {
                "visibleExercises" -> listOf(exercise)
                "workoutStepPositions" -> exercise.warmupSets.map { warmup ->
                    WorkoutStep(
                        type = WorkoutStepType.WARMUP,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        stepKey = "step-${warmup.id}",
                        warmupSetId = warmup.id,
                    )
                }
                "getWarmupSuggestedWeight" -> {
                    val index = arguments[1] as Int
                    events += "suggest-$index"
                    suggestedLoads[index]
                }
                "getWarmupWorkingWeightAnchor" -> {
                    events += "get-anchor"
                    workingAnchor
                }
                "reportWarmupStep" -> {
                    reports += Report(arguments[2] as Double?, arguments[3] as Int?)
                    events += "report"
                    null
                }
                "markWarmupComplete" -> {
                    events += "mark-complete"
                    null
                }
                "recordWarmupHeaviness" -> {
                    events += "record-effort"
                    null
                }
                "speakWarmupAutoRegulation" -> {
                    autoRegulationFeedback += arguments[0] as String
                    events += "speak-auto"
                    null
                }
                "speakWarmupCompletedTransition" -> {
                    transitions += Transition(arguments[1] as Double?)
                    events += "speak-transition"
                    null
                }
                "toString" -> "RecordingWorkoutVoicePorts"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === arguments.firstOrNull()
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as WorkoutVoiceCommandHandler.Ports
    }
}
