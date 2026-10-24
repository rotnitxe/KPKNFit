package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.kpkn.data.models.CalibrationDecisionRecord
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedExercise

/** Optional self-report and recorded results. The save never depends on a recovery preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordedSessionFinishSheet(
    name: String,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    initialNotes: String,
    saving: Boolean,
    discomforts: List<String> = emptyList(),
    calibrationDecisions: List<CalibrationDecisionRecord> = emptyList(),
    presentation: FinishPresentationContext = FinishPresentationContext(),
    onSummaryReady: (WorkoutSessionSummary) -> Unit = {},
    onNotes: (String) -> Unit,
    onAlbum: () -> Unit,
    onConfirm: (String, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var effort by rememberSaveable { mutableStateOf<Double?>(null) }
    var showStory by rememberSaveable { mutableStateOf(false) }
    var storyOptions by rememberSaveable(stateSaver = storyOptionsSaver) { mutableStateOf(WorkoutStoryOptions()) }
    val story = remember(completedExercises, presentation, durationMinutes) {
        WorkoutStoryData(name,presentation,durationMinutes,sessionTonnage(completedExercises),
            logicalSetCountFromCompleted(completedExercises),finishAverageRir(completedExercises),"Sin registro",
            buildFinishLiftProgress(completedExercises,presentation.history))
    }
    LaunchedEffect(name, completedExercises, durationMinutes) {
        onSummaryReady(WorkoutSessionSummary(durationMinutes,logicalSetCountFromCompleted(completedExercises),discomforts))
    }
    ModalBottomSheet(onDismissRequest = { onNotes(notes); onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text("Resultados registrados · $durationMinutes min")
            Text("${completedExercises.sumOf { it.sets.size }} series registradas")
            completedExercises.forEach { exercise ->
                Text("${exercise.exerciseName} · ${exercise.sets.size} series")
            }
            calibrationSummaryLines(calibrationDecisions).forEach { line ->
                Text(line,modifier=Modifier.testTag("finish-calibration-line"))
            }
            TextButton(onClick={showStory=true}) { Text("Preparar historia para compartir") }
            Text("Esfuerzo global de la sesión (opcional)")
            Text("Autoinforme 0–10: 0 sin esfuerzo, 10 máximo. No es una medición de recuperación.", style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=effort==null,onClick={effort=null},label={Text("Sin responder")})
                (0..10).forEach { value -> FilterChip(selected=effort==value.toDouble(),onClick={effort=value.toDouble()},label={Text("$value")}) }
            }
            OutlinedTextField(value=notes,onValueChange={notes=it;onNotes(it)},label={Text("Notas (opcional)")},modifier=Modifier.fillMaxWidth())
            TextButton(onClick=onAlbum) { Text("Fotos y medios de la sesión") }
            val blocked=finishEmptySessionGuidance(completedExercises)
            blocked?.let { Text(it) }
            Button(enabled=!saving && blocked==null,onClick={onConfirm(notes,effort)},modifier=Modifier.fillMaxWidth()) {
                Text(if(saving) "Guardando…" else "Guardar sesión")
            }
            TextButton(enabled=!saving,onClick={onNotes(notes);onDismiss()}) { Text("Volver a la sesión") }
        }
    }
    if (showStory) WorkoutStorySheet(data=story,hazeState=remember { HazeState() },options=storyOptions,
        onOptionsChange={storyOptions=it},shareAfterSave=false,onShareAfterSaveChange={},onDismiss={showStory=false})

}
