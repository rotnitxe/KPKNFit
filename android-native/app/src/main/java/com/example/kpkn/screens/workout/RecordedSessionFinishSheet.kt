package com.example.kpkn.screens.workout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CalibrationDecisionRecord
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.insights.MuscleVolumeAccounting
import com.example.kpkn.domain.training.insights.RpeConversion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

private val SheetCream = WizardColors.text
private val SheetMuted = WizardColors.textMuted
private val FinishFaint = WizardColors.textFaint
private val FinishGreen = androidx.compose.ui.graphics.Color(0xFF7FE3B0)
private val FinishAmber = androidx.compose.ui.graphics.Color(0xFFFFA15C)

/**
 * Cierre de la sesión en tres pestañas. El pie es el mismo en las tres y lleva el interruptor de
 * compartir: es una decisión de guardado, no de revisión.
 */
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
    checklist: List<SessionChecklistItem> = emptyList(),
    onAddTask: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onRemoveTask: (String) -> Unit = {},
    onSummaryReady: (WorkoutSessionSummary) -> Unit = {},
    onShareAfterSave: (WorkoutStoryData, WorkoutStoryOptions) -> Unit = { _, _ -> },
    onNotes: (String) -> Unit,
    onAlbum: () -> Unit,
    onConfirm: (String, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var effort by rememberSaveable { mutableStateOf<Double?>(null) }
    var shareAfterSave by rememberSaveable { mutableStateOf(false) }
    var showStory by rememberSaveable { mutableStateOf(false) }
    var storyOptions by rememberSaveable(stateSaver = storyOptionsSaver) { mutableStateOf(WorkoutStoryOptions()) }

    val lifts = remember(completedExercises, presentation) {
        buildFinishLiftProgress(completedExercises, presentation.history)
    }
    val story = remember(completedExercises, presentation, durationMinutes, effort) {
        WorkoutStoryData(
            name,
            presentation,
            durationMinutes,
            sessionTonnage(completedExercises),
            logicalSetCountFromCompleted(completedExercises),
            finishAverageRir(completedExercises),
            "Sin registro",
            lifts,
            sessionRpe = RpeConversion.sessionRpe(effort, completedExercises),
        )
    }
    LaunchedEffect(name, completedExercises, durationMinutes) {
        onSummaryReady(
            WorkoutSessionSummary(
                durationMinutes,
                logicalSetCountFromCompleted(completedExercises),
                discomforts,
            ),
        )
    }
    val blocked = finishEmptySessionGuidance(completedExercises)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text("SESIÓN COMPLETADA", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
                Spacer(Modifier.height(4.dp))
                Text(name, style = KpknType.title, color = SheetCream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${durationMinutes} min · ${completedExercises.size} ejercicios",
                    style = KpknType.caption.copy(fontSize = 12.sp),
                    color = SheetMuted,
                )
                Spacer(Modifier.height(14.dp))
                KpknSegmentedControl(
                    options = listOf("Resumen", "Progreso", "Anotaciones"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    size = KpknSegmentedSize.Compact,
                )
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                when (tab) {
                    0 -> SummaryTab(
                        name = name,
                        completedExercises = completedExercises,
                        durationMinutes = durationMinutes,
                        sessionRpe = RpeConversion.sessionRpe(effort, completedExercises),
                        presentation = presentation,
                        lifts = lifts,
                        onPrepareStory = { showStory = true },
                        onAlbum = onAlbum,
                    )
                    1 -> ProgressTab(
                        name = name,
                        completedExercises = completedExercises,
                        presentation = presentation,
                        lifts = lifts,
                    )
                    else -> NotesTab(
                        notes = notes,
                        onNotes = { notes = it; onNotes(it) },
                        effort = effort,
                        onEffort = { effort = it },
                        checklist = checklist,
                        onAddTask = onAddTask,
                        onToggleTask = onToggleTask,
                        onRemoveTask = onRemoveTask,
                        onAlbum = onAlbum,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Compartir al guardar", style = KpknType.body.copy(fontSize = 14.sp), color = SheetMuted)
                    Spacer(Modifier.weight(1f))
                    KpknSwitch(checked = shareAfterSave, onChange = { shareAfterSave = it })
                }
                blocked?.let {
                    Text(it, style = KpknType.caption.copy(fontSize = 12.sp), color = FinishFaint)
                    Spacer(Modifier.height(6.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (saving || blocked != null) SheetCream.copy(alpha = 0.38f) else SheetCream)
                        .clickable(enabled = !saving && blocked == null) {
                            if (shareAfterSave) onShareAfterSave(story, storyOptions)
                            onConfirm(notes, effort)
                        }
                        .testTag("finish-save"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "Guardando…" else "Guardar sesión",
                        style = KpknType.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = WizardColors.background,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Volver a la sesión",
                    style = KpknType.action,
                    color = SheetMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { onDismiss() }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
    if (showStory) {
        WorkoutStorySheet(
            data = story,
            hazeState = remember { HazeState() },
            options = storyOptions,
            onOptionsChange = { storyOptions = it },
            shareAfterSave = shareAfterSave,
            onShareAfterSaveChange = { shareAfterSave = it },
            onDismiss = { showStory = false },
        )
    }
}

@Composable
private fun SummaryTab(
    name: String,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    sessionRpe: Double?,
    presentation: FinishPresentationContext,
    lifts: List<FinishLiftProgress>,
    onPrepareStory: () -> Unit,
    onAlbum: () -> Unit,
) {
    val tonnage = sessionTonnage(completedExercises)
    val previous = presentation.history.filter { it.sessionName == name }.maxByOrNull { it.date }
    val tonnageDelta = previous?.let { tonnage - it.totalVolume }
    val sets = logicalSetCountFromCompleted(completedExercises)
    val direct = MuscleVolumeAccounting.sessionDirectSets(completedExercises)
    val total = MuscleVolumeAccounting.sessionSets(completedExercises).entries
        .sortedByDescending { it.value }
        .take(6)

    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MetricCell("SERIES", "$sets", Modifier.weight(1f))
        MetricCell(
            label = "TONELAJE",
            value = "${finishNumber(tonnage, 0)} kg",
            modifier = Modifier.weight(1f),
            delta = tonnageDelta?.let { finishDeltaLabel(it) },
            deltaPositive = (tonnageDelta ?: 0.0) > 0.0,
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MetricCell("DURACIÓN", "$durationMinutes min", Modifier.weight(1f))
        MetricCell("sRPE", sessionRpe?.let { finishNumber(it) } ?: "—", Modifier.weight(1f))
    }

    if (total.isNotEmpty()) {
        SectionRule()
        Text("VOLUMEN POR MÚSCULO", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
        Spacer(Modifier.height(10.dp))
        val max = total.first().value.coerceAtLeast(1.0)
        total.forEach { (muscle, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    muscle,
                    style = KpknType.body.copy(fontSize = 13.sp),
                    color = SheetCream,
                    maxLines = 1,
                    modifier = Modifier.width(104.dp),
                )
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(SheetCream.copy(alpha = 0.10f)),
                ) {
                    val directShare = ((direct[muscle] ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
                    val totalShare = (value / max).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(totalShare).height(6.dp).background(SheetCream.copy(alpha = 0.30f)))
                    Box(Modifier.fillMaxWidth(directShare).height(6.dp).background(SheetCream))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    finishNumber(value),
                    style = KpknType.metricM.copy(fontSize = 14.sp),
                    color = SheetCream,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }

    val progress = lifts.filter { it.best != null }
        .sortedByDescending { it.best?.let { set -> StrengthModel.estimate1RM(set) } ?: 0.0 }
        .take(3)
    if (progress.isNotEmpty()) {
        SectionRule()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PROGRESO", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
            Spacer(Modifier.weight(1f))
            Text("vs. última vez", style = KpknType.caption.copy(fontSize = 12.sp), color = FinishFaint)
        }
        Spacer(Modifier.height(6.dp))
        progress.forEach { lift -> LiftRow(lift) }
    }

    SectionRule()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KpknSurface.inset)
            .clickable(onClick = onPrepareStory)
            .padding(14.dp)
            .testTag("finish-prepare-story"),
    ) {
        Column {
            Text("Preparar historia", style = KpknType.bodyStrong, color = SheetCream)
            Text(
                "General · mejoras y duración",
                style = KpknType.caption.copy(fontSize = 12.sp),
                color = FinishFaint,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Fotos y medios de la sesión",
        style = KpknType.action,
        color = SheetMuted,
        modifier = Modifier.clickable(onClick = onAlbum).padding(vertical = 4.dp),
    )
}

@Composable
private fun ProgressTab(
    name: String,
    completedExercises: List<CompletedExercise>,
    presentation: FinishPresentationContext,
    lifts: List<FinishLiftProgress>,
) {
    val history = presentation.history.filter { it.sessionName == name }
        .sortedBy { it.date }
        .takeLast(8)
    val points = history.map { it.totalVolume } + sessionTonnage(completedExercises)
    val previousVolume = history.lastOrNull()?.totalVolume
    val current = points.last()
    val delta = previousVolume?.let { current - it }

    Spacer(Modifier.height(16.dp))
    Text("TONELAJE DE LA SESIÓN", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(finishNumber(current, 0), style = KpknType.metricL, color = SheetCream)
        Spacer(Modifier.width(6.dp))
        Text("kg", style = KpknType.unit, color = SheetMuted, modifier = Modifier.padding(bottom = 3.dp))
        Spacer(Modifier.weight(1f))
        delta?.let {
            Text(
                finishDeltaLabel(it),
                style = KpknType.bodyStrong.copy(fontSize = 14.sp),
                color = if (it > 0.0) FinishGreen else SheetMuted,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Sparkline(points, Modifier.fillMaxWidth().height(64.dp))

    SectionRule()
    Text("EJERCICIOS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
    Spacer(Modifier.height(6.dp))
    lifts.forEach { lift -> LiftRow(lift) }

    val records = lifts.filter { lift ->
        val best = lift.best ?: return@filter false
        val previousBest = lift.history.maxOfOrNull { it.estimated1Rm } ?: 0.0
        StrengthModel.estimate1RM(best) > previousBest + 0.005
    }
    if (records.isNotEmpty()) {
        SectionRule()
        Text("HITOS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
        Spacer(Modifier.height(8.dp))
        records.forEach { lift ->
            val best = lift.best ?: return@forEach
            val value = StrengthModel.estimate1RM(best)
            val previousBest = lift.history.maxOfOrNull { it.estimated1Rm }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FinishGreen.copy(alpha = 0.08f))
                    .padding(12.dp),
            ) {
                Column {
                    Text("PR e1RM · ${lift.name}", style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = SheetCream)
                    Text(
                        text = buildString {
                            append(finishNumber(value))
                            append(" kg")
                            previousBest?.let { append(" · antes ${finishNumber(it)} kg") }
                        },
                        style = KpknType.caption.copy(fontSize = 12.sp),
                        color = FinishGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: String,
    onNotes: (String) -> Unit,
    effort: Double?,
    onEffort: (Double?) -> Unit,
    checklist: List<SessionChecklistItem>,
    onAddTask: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onAlbum: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Spacer(Modifier.height(16.dp))
    Text("NOTAS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KpknSurface.inset)
            .padding(12.dp),
    ) {
        BasicTextField(
            value = notes,
            onValueChange = onNotes,
            textStyle = KpknType.body.copy(color = SheetCream, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth().testTag("finish-notes"),
        )
        if (notes.isBlank()) {
            Text("Nota de la sesión", style = KpknType.body.copy(fontSize = 14.sp), color = FinishFaint)
        }
    }

    SectionRule()
    Text("TAREAS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
    Spacer(Modifier.height(6.dp))
    checklist.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (item.done) SheetCream else SheetCream.copy(alpha = 0.12f))
                    .clickable { onToggleTask(item.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) Text("✓", style = KpknType.caption.copy(fontSize = 12.sp), color = WizardColors.background)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                item.text,
                style = KpknType.body.copy(fontSize = 14.sp),
                color = if (item.done) FinishFaint else SheetCream,
                modifier = Modifier.weight(1f),
            )
            Text(
                "×",
                style = KpknType.body.copy(fontSize = 16.sp),
                color = FinishFaint,
                modifier = Modifier.clickable { onRemoveTask(item.id) }.padding(horizontal = 6.dp),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = KpknType.body.copy(color = SheetCream, fontSize = 14.sp),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Añadir",
            style = KpknType.action,
            color = if (draft.isBlank()) FinishFaint else SheetCream,
            modifier = Modifier
                .clickable(enabled = draft.isNotBlank()) {
                    onAddTask(draft)
                    draft = ""
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("finish-add-task"),
        )
    }

    SectionRule()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("ESFUERZO", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
        Spacer(Modifier.weight(1f))
        Text(effort?.let { finishNumber(it, 0) } ?: "—", style = KpknType.metricM, color = SheetCream)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (0..10).forEach { value ->
            val selected = effort == value.toDouble()
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SheetCream else SheetCream.copy(alpha = 0.13f))
                    .clickable { onEffort(if (selected) null else value.toDouble()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$value",
                    style = KpknType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = if (selected) WizardColors.background else SheetMuted,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Fotos y medios de la sesión",
        style = KpknType.action,
        color = SheetMuted,
        modifier = Modifier.clickable(onClick = onAlbum).padding(vertical = 4.dp),
    )
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = false,
) {
    Column(modifier) {
        Text(label, style = KpknType.eyebrow.copy(fontSize = 12.sp), color = FinishFaint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = KpknType.metricL, color = SheetCream, maxLines = 1)
        if (delta != null) {
            Text(
                delta,
                style = KpknType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = if (deltaPositive) FinishGreen else SheetMuted,
            )
        }
    }
}

@Composable
private fun LiftRow(lift: FinishLiftProgress) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                lift.name,
                style = KpknType.bodyStrong.copy(fontSize = 14.sp),
                color = SheetCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                lift.best?.let { finishSetLabel(it) } ?: "Sin serie comparable",
                style = KpknType.caption.copy(fontSize = 12.sp),
                color = FinishFaint,
                maxLines = 1,
            )
        }
        Sparkline(lift.history.map { it.estimated1Rm }, Modifier.width(56.dp).height(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            lift.deltaKg?.let { finishDeltaLabel(it) } ?: "—",
            style = KpknType.bodyStrong.copy(fontSize = 13.sp),
            color = if (lift.isImproved) FinishGreen else SheetMuted,
        )
    }
}

@Composable
private fun Sparkline(points: List<Double>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val low = points.min()
    val span = (points.max() - low).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier) {
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1).toFloat()
            val y = size.height - ((value - low) / span).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, SheetMuted, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        drawCircle(
            FinishAmber,
            radius = 2.6f,
            center = Offset(size.width, size.height - ((points.last() - low) / span).toFloat() * size.height),
        )
    }
}

@Composable
private fun SectionRule() {
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun KpknSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val offset by animateDpAsState(if (checked) 21.dp else 3.dp, label = "finishSwitch")
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) SheetCream else SheetCream.copy(alpha = 0.16f))
            .clickable { onChange(!checked) }
            .testTag("finish-share-switch"),
    ) {
        Box(
            Modifier
                .padding(top = 3.dp, start = 0.dp)
                .padding(start = offset)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) WizardColors.background else SheetMuted),
        )
    }
}
