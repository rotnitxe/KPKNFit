package com.example.kpkn.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kpkn.domain.training.*
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*
import java.util.Locale

val LocalExerciseProgressOpener = staticCompositionLocalOf<(String?) -> Unit> { {} }
@Composable
fun ExerciseProgressRoute(exerciseId: String?, onBack: () -> Unit) {
    val vm: ExerciseProgressViewModel = viewModel(key = "exercise-progress:$exerciseId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ExerciseProgressViewModel(exerciseId) as T
    })
    val state by vm.state.collectAsState()
    ExerciseProgressScreen(state, vm::selectExercise, vm::selectMetric, vm::selectWindow, vm::selectPoint, onBack)
}
private fun number(value: Double) = String.format(Locale.forLanguageTag("es-CL"), "%.1f", value)
private fun modeTitle(mode: LoadModeV2) = when(mode) {
    LoadModeV2.LOAD -> "Carga externa"; LoadModeV2.BODYWEIGHT -> "Peso corporal"; LoadModeV2.LASTRE -> "Lastre"; LoadModeV2.ASSISTED -> "Asistencia"
}
private fun pointUnit(point: ExerciseProgressPoint, metric: ExerciseProgressMetric): String = when {
    metric == ExerciseProgressMetric.BEST_SET && point.mode == LoadModeV2.ASSISTED -> "kg asistencia ↓"
    metric == ExerciseProgressMetric.MAX_WEIGHT && point.mode == LoadModeV2.ASSISTED -> "kg asistencia ↓"
    metric == ExerciseProgressMetric.MAX_WEIGHT && point.mode == LoadModeV2.LASTRE -> "kg lastre"
    else -> metric.unit
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseProgressScreen(state: ExerciseProgressUiState, onExercise: (String) -> Unit, onMetric: (ExerciseProgressMetric) -> Unit,
    onWindow: (ExerciseProgressWindow) -> Unit, onPoint: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KpknTextAction("Volver", onBack)
        Text("Progreso por ejercicio", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
        if(state.loading) { Text("Cargando historial…", style = KpknType.body, color = WizardColors.textMuted); return@Column }
        val report = state.report
        if(report?.choice == null) {
            Text("Todavía no hay sesiones de este ejercicio. Su progreso aparecerá al registrarlas.", style = KpknType.body, color = WizardColors.textMuted)
        }
        Text(report?.choice?.name ?: "Elige un ejercicio", style = KpknType.bodyStrong, color = WizardColors.text)
        report?.choice?.let { Text(it.configuration.ifBlank { "Sin detalle de configuración en el registro" }, style = KpknType.caption, color = WizardColors.textMuted) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.choices.forEach { choice -> KpknChip(choice.name + choice.configuration.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), choice.id == report?.choice?.id, { onExercise(choice.id) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            report?.available?.sortedBy { it.ordinal }?.forEach { metric -> KpknChip(metric.title, report.metric == metric, { onMetric(metric) }, Modifier.testTag("progress-metric-${metric.name}")) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ExerciseProgressWindow.entries.forEach { window -> KpknChip(window.title, window == state.window, { onWindow(window) }) }
        }
        Text("Se excluyen series saltadas, fallidas, calentamientos y datos inválidos. No se mezclan configuraciones o modalidades.", style = KpknType.caption, color = WizardColors.textMuted)
        if(report != null) {
            if(report.insufficient) Text("Sin datos suficientes: hacen falta dos sesiones comparables. Mostramos lo registrado.", style = KpknType.body, color = WizardColors.textMuted)
            if(report.points.isNotEmpty()) {
                KpknModule(Modifier.fillMaxWidth()) {
                    KpknModuleHeader(report.metric.title)
                    ProgressChart(report.points, report.metric, onPoint)
                    val selected = state.selectedPoint?.let { report.points.getOrNull(it) } ?: report.points.last()
                    Text("${selected.date} · ${selected.session}", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(selected.explanation, style = KpknType.body, color = WizardColors.textMuted)
                    Text("${selected.sets} series · ${selected.context.ifBlank { "Contexto sin detalles" }}${if(selected.isPr) " · PR" else ""}", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            if(report.records.isNotEmpty()) KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader("Récords históricos")
                Text("Por trayectoria y, en repeticiones, por carga. Los empates conservan la primera fecha.", style = KpknType.caption, color = WizardColors.textMuted)
                report.records.forEach { record ->
                    Text("${record.metric.title}: ${number(record.point.value)} ${pointUnit(record.point,record.metric)}", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text("${record.point.date} · ${record.point.session} · ${modeTitle(record.point.mode)} · ${record.point.context}", style = KpknType.caption, color = WizardColors.textMuted)
                    if(record.metric == ExerciseProgressMetric.BEST_SET || record.metric == ExerciseProgressMetric.MAX_REPS)
                        Text(record.point.explanation, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader("Sesiones del intervalo")
                report.points.asReversed().forEach { point ->
                    if(point.breaksBefore) Text("No comparable con lo anterior", style = KpknType.caption, color = WizardColors.textMuted)
                    Text("${point.date} · ${point.session}${if(point.isPr) " · PR" else ""}", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(point.explanation, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
    }
}
@Composable
private fun ProgressChart(points: List<ExerciseProgressPoint>, metric: ExerciseProgressMetric, onPoint: (Int) -> Unit) {
    val ink = WizardColors.text; val muted = WizardColors.textMuted
    // Coordenadas normalizadas fuera del dibujo. Las líneas se cortan al cambiar la trayectoria.
    val low = points.minOf { it.value }; val high = points.maxOf { it.value }; val span = (high-low).takeIf { it > 0 } ?: 1.0
    val coordinates = remember(points) { points.mapIndexed { index, point ->
        Offset(if(points.size == 1) .5f else index.toFloat()/(points.size-1), (1.0-(point.value-low)/span).toFloat())
    } }
    val summary = "${metric.title}: ${points.size} puntos. De ${number(low)} a ${number(high)}. ${points.count { it.isPr }} marcas PR. ${points.count { it.breaksBefore }} cortes no comparables."
    Text("${number(high)} / ${number(low)} ${pointUnit(points.last(), metric)}", style = KpknType.caption, color = muted)
    Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = summary }.pointerInput(points) {
        detectTapGestures { offset -> val index = (((offset.x-12.dp.toPx())/(size.width-24.dp.toPx()).coerceAtLeast(1f))*(points.size-1)).toInt().coerceIn(points.indices); onPoint(index) }
    }) {
        val padding = 12.dp.toPx()
        fun at(index: Int) = Offset(padding + coordinates[index].x*(size.width-2*padding), padding + coordinates[index].y*(size.height-2*padding))
        drawLine(muted.copy(alpha=.3f), Offset(padding,size.height-padding), Offset(size.width-padding,size.height-padding), 1.dp.toPx())
        points.indices.forEach { index ->
            if(index>0 && !points[index].breaksBefore) drawLine(ink.copy(alpha=.65f),at(index-1),at(index),2.dp.toPx())
            drawCircle(ink, if(points[index].isPr) 6.dp.toPx() else 3.dp.toPx(),at(index))
        }
    }
    Text("${points.first().date} → ${points.last().date}. PR: punto grande. Toca un punto para consultar la serie.", style = KpknType.caption, color = muted)
    // Alternativa accesible con objetivos táctiles ≥48 dp; no depende de acertar un punto del Canvas.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        points.forEachIndexed { index, point -> KpknChip("${point.date}${if(point.isPr) " · PR" else ""}", false, { onPoint(index) }) }
    }
}
