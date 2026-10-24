package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.MuscleVolumePanelRequest
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.progress.BodyMetric
import com.example.kpkn.screens.progress.MuscleVolumePanelActions
import com.example.kpkn.screens.progress.MuscleVolumePanelScreen
import com.example.kpkn.screens.progress.MuscleVolumePanelViewModel
import com.example.kpkn.screens.progress.MuscleVolumeView
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

/**
 * SOLO DEPURACIÓN. Aloja el panel de volumen por músculo con datos en memoria (no toca la base real).
 * Extras: `state` = data | empty | sparse | unassigned | body | recovery, `fontScale`, `reducedMotion` y `widthDp`.
 * Lanzar: `launch:MuscleVolumePanelHarnessActivity|--es|state|body`.
 */
class MuscleVolumePanelHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val name = extra("state") ?: "data"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize()) {
                            val model: MuscleVolumePanelViewModel = viewModel { buildModel(name) }
                            LaunchedEffect(name) { applyState(model, name) }
                            val state by model.state.collectAsStateWithLifecycle()
                            MuscleVolumePanelScreen(state, MuscleVolumePanelActions.from(model, onBack = ::finish))
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()

    private fun buildModel(name: String): MuscleVolumePanelViewModel {
        val logs = when (name) {
            "empty" -> emptyList()
            "sparse" -> sparseLogs()
            "unassigned" -> sparseLogs() + undatedLogs()
            else -> fullLogs()
        }
        val scoped = name == "data"
        return MuscleVolumePanelViewModel(
            programId = if (scoped) PROGRAM_ID else null,
            history = MutableStateFlow(logs),
            programs = MutableStateFlow(listOf(program())),
            recovery = MutableStateFlow(RECOVERY),
            exerciseIndex = { CATALOG },
            today = { TODAY },
            zone = { ZONE },
            computeDispatcher = Dispatchers.Default,
            initialRequest = MuscleVolumePanelRequest(
                window = if (name == "sparse" || name == "unassigned") MuscleVolumeWindow.WEEK_1 else MuscleVolumeWindow.WEEKS_4,
                mode = if (name == "data") VolumeCountingMode.SECONDARY else VolumeCountingMode.DIRECT,
            ),
        )
    }

    private fun applyState(model: MuscleVolumePanelViewModel, name: String) {
        when (name) {
            "body" -> {
                model.selectView(MuscleVolumeView.BODY)
                model.selectRegion("Pectorales")
            }
            "recovery" -> {
                model.selectView(MuscleVolumeView.BODY)
                model.selectMetric(BodyMetric.RECOVERY)
                model.selectRegion("Cuádriceps")
            }
            "data" -> model.selectRegion("Pectorales")
        }
    }

    private fun program() = Program(
        id = PROGRAM_ID,
        name = "Hipertrofia 4 días",
        volumeRecommendations = listOf(
            VolumeRecommendation("Pectorales", 8, 16, 22),
            VolumeRecommendation("Dorsales", 8, 16, 22),
            VolumeRecommendation("Cuádriceps", 8, 14, 20),
            VolumeRecommendation("Bíceps", 6, 12, 18),
        ),
        athleteProfileScore = AthleteProfileScore(5, 5, 5, 5, TrainingStyle.BODYBUILDER, 20, AthleteProfileLevel.BEGINNER),
    )

    private fun log(id: String, date: String, exercise: String, sets: Int) = WorkoutLog(
        id = id, programId = PROGRAM_ID, sessionId = "s", sessionName = "Sesión", date = date, durationMinutes = 55,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "$exercise-o", exerciseName = exercise, exerciseDbId = exercise,
                sets = List(sets) { CompletedSet("$id-$it", weight = 60.0, reps = 8, rpe = 7.5) },
            ),
        ),
    )

    private fun fullLogs(): List<WorkoutLog> {
        val plan = listOf(
            listOf("press" to 4, "inclinado" to 3, "lateral" to 3),
            listOf("remo" to 4, "dominada" to 3, "curl" to 3),
            listOf("sentadilla" to 5, "curl-femoral" to 3, "gemelo" to 4, "crunch" to 3),
        )
        val logs = mutableListOf<WorkoutLog>()
        var day = 0L
        var index = 0
        while (day < 84) {
            plan[index % 3].forEachIndexed { slot, (exercise, sets) ->
                logs += log("l$index-$slot", TODAY.minusDays(day).toString(), exercise, sets)
            }
            index++
            day += if (index % 3 == 0) 2 else 1
        }
        return logs
    }

    private fun sparseLogs() = listOf(
        log("a", TODAY.minusDays(1).toString(), "press", 4),
        log("b", TODAY.minusDays(3).toString(), "press", 3),
    )

    private fun undatedLogs() = listOf(
        log("sin-fecha-1", "fecha ilegible", "remo", 3),
        log("sin-fecha-2", "", "curl", 3),
    )

    private companion object {
        const val PROGRAM_ID = "plan-harness"
        val TODAY: LocalDate = LocalDate.now()
        val ZONE: ZoneId = ZoneId.systemDefault()

        val RECOVERY = mapOf(
            "Pectorales" to 54, "Dorsales" to 86, "Deltoides" to 72, "Bíceps" to 90, "Tríceps" to 61,
            "Cuádriceps" to 38, "Isquiosurales" to 45, "Glúteos" to 83, "Pantorrillas" to 95, "Abdomen" to 100,
        )

        private fun info(id: String, name: String, vararg muscles: InvolvedMuscle) =
            ExerciseMuscleInfo(id, name, involvedMuscles = muscles.toList())

        val CATALOG: Map<String, ExerciseMuscleInfo> = listOf(
            info("press", "Press de banca", InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5), InvolvedMuscle("Deltoides anterior", MuscleRole.SECONDARY, 0.4, "anterior")),
            info("inclinado", "Press inclinado con mancuernas", InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "superior"), InvolvedMuscle("Deltoides anterior", MuscleRole.SECONDARY, 0.5, "anterior")),
            info("remo", "Remo con barra", InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY, 0.5), InvolvedMuscle("Trapecio", MuscleRole.SECONDARY, 0.4, "medio")),
            info("dominada", "Dominadas", InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY, 0.5)),
            info("sentadilla", "Sentadilla", InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0), InvolvedMuscle("Glúteos", MuscleRole.PRIMARY, 0.7, "mayor"), InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER, 0.4)),
            info("curl-femoral", "Curl femoral", InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, 1.0)),
            info("gemelo", "Elevación de gemelos", InvolvedMuscle("Pantorrillas", MuscleRole.PRIMARY, 1.0, "gastrocnemio")),
            info("curl", "Curl con barra", InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 1.0), InvolvedMuscle("Antebrazo", MuscleRole.SECONDARY, 0.4)),
            info("lateral", "Elevaciones laterales", InvolvedMuscle("Deltoides lateral", MuscleRole.PRIMARY, 1.0, "lateral")),
            info("crunch", "Crunch en polea", InvolvedMuscle("Abdomen", MuscleRole.PRIMARY, 1.0)),
        ).associateBy { it.id }
    }
}
