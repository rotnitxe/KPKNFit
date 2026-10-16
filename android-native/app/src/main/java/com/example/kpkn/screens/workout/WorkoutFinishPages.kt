package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.rings.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.launch

/** Altura única para las tres páginas: el contenido llega hasta los FAB, que no tienen una barra propia. */
@Composable
internal fun FinishPagerLayout(
    sessionName: String,
    saving: Boolean,
    saveDescription: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    warnings: @Composable ColumnScope.() -> Unit,
    today: @Composable ColumnScope.() -> Unit,
    lifts: @Composable ColumnScope.() -> Unit,
    notes: @Composable ColumnScope.() -> Unit,
) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("finish-layout"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("SESIÓN TERMINADA", style = KpknType.eyebrow, color = Color(0xFF43D18C))
            Text(sessionName, style = KpknType.title, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        KpknSegmentedControl(
            options = listOf("Hoy", "Levantamientos", "Notas"),
            selectedIndex = pager.currentPage,
            onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.fillMaxWidth().testTag("finish-tabs"),
        )
        warnings()
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("finish-pager")) { page ->
            Column(Modifier.fillMaxSize().testTag("finish-page-$page"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (page) { 0 -> today(); 1 -> lifts(); else -> notes() }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0E0E0E))))
                .padding(top = 2.dp, bottom = 16.dp).testTag("finish-fabs"),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinishRoundAction("Volver al entrenamiento", "finish-back", Icons.AutoMirrored.Filled.ArrowBack, onBack)
            FloatingActionButton(
                onClick = { if (!saving) onSave() },
                modifier = Modifier.size(64.dp).testTag("finish-save")
                    .semantics { contentDescription = "Guardar y terminar entrenamiento"; if(saveDescription != null) stateDescription = saveDescription },
                shape = CircleShape,
                containerColor = WizardColors.text,
                contentColor = Color.Black,
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(26.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(Icons.Default.Check, null, Modifier.size(28.dp))
            }
            FinishRoundAction("Compartir", "finish-share", Icons.Default.Share, onShare)
        }
    }
}

@Composable
private fun FinishRoundAction(label: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    FilledIconButton(onClick = action, modifier = Modifier.size(48.dp).testTag(tag),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1A1A1A), contentColor = WizardColors.text)) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

@Composable
internal fun ColumnScope.FinishTodayPage(
    values: TowerRingValues,
    before: TowerRingValues,
    drains: List<Int>,
    onRing: (RingChannel) -> Unit,
    metrics: List<Pair<String, String>>,
    onDetails: () -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            KpknTowerRings(values = values, from = before, onRingTap = onRing,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("finish-rings"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(RingChannel.ENERGIA, RingChannel.MUSCULOS, RingChannel.COLUMNA).forEach { channel ->
                    val name = when(channel) { RingChannel.ENERGIA -> "Energía"; RingChannel.MUSCULOS -> "Músculos"; RingChannel.COLUMNA -> "Columna" }
                    val index = when(channel) { RingChannel.MUSCULOS -> 0; RingChannel.ENERGIA -> 1; RingChannel.COLUMNA -> 2 }
                    Column(Modifier.clickable { onRing(channel) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, style = KpknType.caption, color = KpknRingColors.of(channel))
                        Text("${values[channel]}\u00A0%", style = KpknType.bodyStrong, color = WizardColors.text)
                        drains.getOrNull(index)?.takeIf { it > 0 }?.let { Text("−$it", style = KpknType.caption, color = WizardColors.textMuted) }
                    }
                }
            }
        }
    }
    Text("Toca un anillo para ajustarlo", style = KpknType.caption, color = WizardColors.textFaint,
        modifier = Modifier.align(Alignment.CenterHorizontally))
    val columns = if (LocalDensity.current.fontScale > 1.15f) 2 else 3
    metrics.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (name, value) ->
                Column(Modifier.weight(1f).background(Color(0xFF161616), RoundedCornerShape(16.dp)).padding(10.dp)) {
                    Text(name, style = KpknType.caption, color = WizardColors.textMuted)
                    Text(value, style = KpknType.bodyStrong, color = WizardColors.text)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("finish-details")) {
        Text("Ver detalles de la carga", style = KpknType.caption, color = WizardColors.textMuted)
    }
}

@Composable
internal fun ColumnScope.FinishLiftsPage(
    lifts: List<FinishLiftProgress>,
    sessionName: String,
    tonnage: List<Double>,
    totalVolume: Double,
    intensity: String,
    technique: String,
) {
    val star = lifts.filter { it.isImproved }.maxByOrNull { it.deltaKg ?: 0.0 } ?: lifts.firstOrNull { it.best != null }
    if (star != null) {
        Column(Modifier.weight(0.4f).fillMaxWidth().background(Color(0xFF161616), RoundedCornerShape(18.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (star.isImproved) "MEJOR PROGRESO" else "MEJOR SERIE DE HOY", style = KpknType.eyebrow, color = WizardColors.textMuted)
            Text(star.name, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(finishDeltaLabel(star.deltaKg), style = KpknType.caption, color = Color(0xFF43D18C))
            FinishSparkline(star.history.map { it.weight } + listOfNotNull(star.best?.weight), Modifier.weight(1f).fillMaxWidth())
            Text("Hoy: ${finishSetLabel(star.best)}", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
    LazyColumn(Modifier.weight(0.35f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(lifts, key = { it.exerciseId }) { lift ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(lift.name, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text("${finishSetLabel(lift.best)} · ${lift.setCount} series", style = KpknType.caption, color = WizardColors.textMuted)
                }
                if (lift.history.isNotEmpty()) FinishSparkline(lift.history.map { it.weight } + listOfNotNull(lift.best?.weight), Modifier.size(54.dp, 28.dp))
                Text(finishDeltaLabel(lift.deltaKg), style = KpknType.caption, color = if(lift.isImproved) Color(0xFF43D18C) else WizardColors.textMuted)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Intensidad · $intensity", style = KpknType.caption, color = WizardColors.textMuted)
        Text("Técnica · $technique", style = KpknType.caption, color = WizardColors.textMuted)
    }
    Column(Modifier.weight(0.25f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tonelaje de $sessionName", style = KpknType.bodyStrong, color = WizardColors.text)
        FinishSparkline(tonnage + totalVolume, Modifier.weight(1f).fillMaxWidth(), Color(0xFF8FB2FF))
        Text("${finishNumber(totalVolume, 0)}\u00A0kg hoy", style = KpknType.caption, color = WizardColors.textMuted)
    }
}

@Composable
internal fun FinishSparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Color(0xFFF49A6E)) {
    Canvas(modifier.semantics { contentDescription = if(values.size < 2) "Sin historial suficiente para mostrar una tendencia" else "Evolución en ${values.size} registros" }) {
        val usable = values.filter { it.isFinite() }
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 1.dp.toPx())
        if (usable.isEmpty()) return@Canvas
        val minimum = usable.minOrNull() ?: 0.0
        val range = ((usable.maxOrNull() ?: 0.0) - minimum).coerceAtLeast(1.0)
        val inset = 5.dp.toPx()
        val path = Path()
        usable.forEachIndexed { index, value ->
            val x = if (usable.size == 1) size.width / 2 else inset + (size.width - inset * 2) * index / (usable.size - 1)
            val y = size.height - inset - ((value - minimum) / range).toFloat() * (size.height - inset * 2)
            if(index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (index == usable.lastIndex) drawCircle(Color(0xFFF7CF73), 3.dp.toPx(), Offset(x,y))
        }
        if(usable.size > 1) drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
