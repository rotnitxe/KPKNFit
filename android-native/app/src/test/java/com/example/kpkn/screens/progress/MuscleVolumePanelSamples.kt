package com.example.kpkn.screens.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.MuscleVolumePanelBuilder
import com.example.kpkn.domain.training.MuscleVolumePanelRequest
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import java.time.LocalDate
import java.time.ZoneId

/** Datos de ejemplo del panel de volumen para la hoja de render y las pruebas de pantalla. */
internal object MuscleVolumePanelSamples {
    val today: LocalDate = LocalDate.of(2026, 10, 21)
    val zone: ZoneId = ZoneId.of("America/Santiago")

    private fun info(id: String, name: String, vararg muscles: InvolvedMuscle) =
        ExerciseMuscleInfo(id, name, involvedMuscles = muscles.toList())

    val catalog: Map<String, ExerciseMuscleInfo> = listOf(
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

    private fun log(id: String, date: String, exercise: String, sets: Int, rpe: Double = 7.5) = WorkoutLog(
        id = id, programId = "plan", sessionId = "s", sessionName = "Sesión", date = date, durationMinutes = 55,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "$exercise-o", exerciseName = exercise, exerciseDbId = exercise,
                sets = List(sets) { CompletedSet("$id-$it", weight = 60.0, reps = 8, rpe = rpe) },
            ),
        ),
    )

    /** Doce semanas de entrenamiento repartido: empuje, tirón y pierna cada pocos días. */
    fun fullHistory(): List<WorkoutLog> {
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
                logs += log("l$index-$slot", today.minusDays(day).toString(), exercise, sets)
            }
            index++
            day += if (index % 3 == 0) 2 else 1
        }
        return logs
    }

    /** Solo pecho esta semana: el resto de los grupos están a cero (hay registros), no «sin datos». */
    fun sparseHistory(): List<WorkoutLog> = listOf(
        log("a", today.minusDays(1).toString(), "press", 4),
        log("b", today.minusDays(3).toString(), "press", 3),
    )

    fun withUnassigned(): List<WorkoutLog> = sparseHistory() + listOf(
        log("sin-fecha-1", "fecha ilegible", "remo", 3),
        log("sin-fecha-2", "", "curl", 3),
    )

    fun zones(): Map<String, ProgramVolumeZones> = mapOf(
        "Pectorales" to ProgramVolumeZones(8, 16, 22),
        "Dorsales" to ProgramVolumeZones(8, 16, 22),
        "Cuádriceps" to ProgramVolumeZones(8, 14, 20),
        "Bíceps" to ProgramVolumeZones(6, 12, 18),
    )

    val recovery: Map<String, Int> = mapOf(
        "Pectorales" to 54, "Dorsales" to 86, "Deltoides" to 72, "Bíceps" to 90, "Tríceps" to 61,
        "Cuádriceps" to 38, "Isquiosurales" to 45, "Glúteos" to 83, "Pantorrillas" to 95, "Abdomen" to 100,
    )

    private fun state(
        logs: List<WorkoutLog>,
        request: MuscleVolumePanelRequest = MuscleVolumePanelRequest(),
        view: MuscleVolumeView = MuscleVolumeView.LIST,
        metric: BodyMetric = BodyMetric.VOLUME,
        side: BodySide = BodySide.FRONT,
        selected: String? = null,
        zones: Map<String, ProgramVolumeZones> = emptyMap(),
        recovery: Map<String, Int> = emptyMap(),
        scopeName: String? = null,
    ): MuscleVolumePanelUiState = MuscleVolumePanelUiState(
        request = request,
        view = view,
        metric = metric,
        side = side,
        selectedMuscle = selected,
        loading = false,
        panel = MuscleVolumePanelBuilder.build(logs, catalog, request, today, zone),
        zones = zones,
        recovery = recovery,
        scopeName = scopeName,
    )

    /** Nombre → estado, con los mismos nombres que el extra `state` de la vista previa de depuración. */
    fun byName(name: String): MuscleVolumePanelUiState = when (name) {
        "data" -> state(
            fullHistory(),
            MuscleVolumePanelRequest(MuscleVolumeWindow.WEEKS_4, true, VolumeCountingMode.SECONDARY),
            selected = "Pectorales", zones = zones(), scopeName = "Hipertrofia 4 días",
        )
        "total" -> state(
            fullHistory(),
            MuscleVolumePanelRequest(MuscleVolumeWindow.WEEKS_12, false, VolumeCountingMode.STABILIZER, adjustByIntensity = true),
            selected = "Dorsales",
        )
        "empty" -> state(emptyList())
        "sparse" -> state(sparseHistory(), MuscleVolumePanelRequest(MuscleVolumeWindow.WEEK_1), selected = "Pectorales")
        "unassigned" -> state(withUnassigned(), MuscleVolumePanelRequest(MuscleVolumeWindow.WEEK_1))
        "body" -> state(fullHistory(), view = MuscleVolumeView.BODY, selected = "Pectorales")
        "body_back" -> state(fullHistory(), view = MuscleVolumeView.BODY, side = BodySide.BACK, selected = "Dorsales")
        "body_sparse" -> state(sparseHistory(), MuscleVolumePanelRequest(MuscleVolumeWindow.WEEK_1), view = MuscleVolumeView.BODY)
        "body_empty" -> state(emptyList(), view = MuscleVolumeView.BODY)
        "recovery" -> state(fullHistory(), view = MuscleVolumeView.BODY, metric = BodyMetric.RECOVERY, selected = "Cuádriceps", recovery = recovery)
        "loading" -> MuscleVolumePanelUiState()
        else -> error("Estado de ejemplo desconocido: $name")
    }
}
