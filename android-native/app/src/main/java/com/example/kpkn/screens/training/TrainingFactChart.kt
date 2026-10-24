package com.example.kpkn.screens.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.insights.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Scatter plot: unrelated contexts are never joined by a physiological trajectory. */
@Composable
internal fun TrainingFactChart(allFacts: List<TrainingFact>, nowMs: Long, weeks: Int) {
    val visible = remember(allFacts, nowMs, weeks) { TrainingInsightsEngine.visible(allFacts, nowMs, weeks) }
    var sportName by rememberSaveable { mutableStateOf<String?>(null) }
    var metricKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    val sports = visible.map { it.sport }.distinct().sortedBy { it.name }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(sportName == null, { sportName = null }, label = { Text("Todas las disciplinas") })
        sports.forEach { sport -> FilterChip(sportName == sport.name, { sportName = sport.name }, label = { Text(sport.name.replace('_', ' ')) }) }
    }
    val filtered = visible.filter { sportName == null || it.sport.name == sportName }
    val groups = filtered.groupBy { "${it.sport}:${it.metric}:${it.unit}" }
    val activeKey = metricKey?.takeIf { it in groups } ?: groups.keys.firstOrNull()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { (key, points) -> FilterChip(key == activeKey, { metricKey = key; selectedSource = null },
            label = { Text("${points.first().metric} (${factUnit(points.first().unit)})") }) }
    }
    val points = groups[activeKey].orEmpty()
    if (points.isEmpty()) {
        Text("Sin valores registrados con unidad identificable. El detalle conserva el documento original.")
        return
    }
    val minTime = points.minOf { it.occurredAtMs }; val maxTime = points.maxOf { it.occurredAtMs }
    val minValue = points.minOf { it.value }; val maxValue = points.maxOf { it.value }
    val color = MaterialTheme.colorScheme.primary
    val selected = points.firstOrNull { it.sourceId == selectedSource }
    Text("${points.size} registros · ${factUnit(points.first().unit)} · toca un punto o su fila", style = MaterialTheme.typography.bodySmall)
    Text("${factNumber(minValue)}–${factNumber(maxValue)} ${factUnit(points.first().unit)} · ${factDate(minTime)}–${factDate(maxTime)}",
        style = MaterialTheme.typography.labelSmall)
    fun coordinates(width: Float, height: Float): List<Offset> = points.map { point ->
        Offset(12f + ((point.occurredAtMs-minTime).toDouble() / (maxTime-minTime).coerceAtLeast(1L)).toFloat() * (width-24f),
            height-12f-((point.value-minValue) / (maxValue-minValue).takeIf { it > 0.0 }.let { it ?: 1.0 }).toFloat()*(height-24f))
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
        contentDescription = "Gráfico de ${points.first().metric}, ${points.size} puntos. Todos disponibles como botones debajo."
    }.pointerInput(points) {
        detectTapGestures { tap ->
            val offsets = coordinates(size.width.toFloat(), size.height.toFloat())
            val index = offsets.indices.minByOrNull { (offsets[it] - tap).getDistanceSquared() }
            selectedSource = index?.let { points[it].sourceId }
        }
    }) {
        coordinates(size.width,size.height).forEachIndexed { index, offset ->
            drawCircle(color, if (points[index].sourceId == selectedSource) 8f else 5f, offset)
        }
    }
    // Text alternatives work with TalkBack, large fonts and without gesture precision.
    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
        points.asReversed().forEach { point ->
            TextButton(onClick = { selectedSource = point.sourceId }) {
                Text("${factDate(point.occurredAtMs)} · ${factNumber(point.value)} ${factUnit(point.unit)} · ${factOrigin(point.origin)}")
            }
        }
    }
    Text("Puntos independientes: un contexto desconocido no se considera equivalente.", style = MaterialTheme.typography.bodySmall)
    if (selected != null) {
        val comparison = TrainingInsightsEngine.compare(selected, allFacts, nowMs)
        AlertDialog(onDismissRequest = { selectedSource = null }, title = { Text(selected.metric) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${factNumber(selected.value)} ${factUnit(selected.unit)} · ${factDate(selected.occurredAtMs)}")
                Text("Origen: ${factOrigin(selected.origin)}")
                Text("Actividad: ${selected.activityId}\nFuente: ${selected.sourceId}")
                Text("Método: ${selected.method ?: "no registrado"}\nProtocolo: ${selected.protocol ?: "no registrado"}")
                Text("Contexto: ${selected.context.takeIf { it.isNotEmpty() }?.entries?.joinToString { "${it.key}: ${it.value}" } ?: "desconocido"}")
                Text(comparison.explanation)
                comparison.median?.let { Text("Mediana de ${comparison.previous.size}: ${factNumber(it)} ${factUnit(selected.unit)}") }
            }
        }, confirmButton = { TextButton(onClick = { selectedSource = null }) { Text("Cerrar") } })
    }
}
internal fun factNumber(value: Double): String = String.format(Locale.forLanguageTag("es"), "%.3f", value).trimEnd('0').trimEnd(',')
internal fun factDate(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
internal fun factOrigin(origin: FactOrigin): String = when(origin) {
    FactOrigin.OBSERVED -> "Observación registrada"
    FactOrigin.SELF_REPORTED -> "Autoinforme"
    FactOrigin.DESCRIPTIVE_CALCULATION -> "Cálculo descriptivo"
    FactOrigin.ESTIMATED -> "Estimación"
    FactOrigin.HISTORICAL_AMBIGUOUS -> "Registro con método o contexto incompleto"
}
internal fun factUnit(unit: String): String = when(unit) {
    "KILOGRAM" -> "kg"; "POUND" -> "lb"; "METER" -> "m"; "CENTIMETER" -> "cm"
    "SECOND" -> "s"; "MILLISECOND" -> "ms"; "METER_PER_SECOND" -> "m/s"; else -> unit
}
