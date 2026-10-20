package com.example.kpkn.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.SessionSavedNote
import com.example.kpkn.ui.components.rememberStableUriImageRequest
import com.example.kpkn.ui.components.rings.*
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.workout.components.WorkoutPhotoViewer
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri

internal val FinishCream = Color(0xFFF2EEE6)
internal val FinishMuted = FinishCream.copy(alpha = .55f)
internal val FinishPeach = Color(0xFFF49A6E)
private val FinishSurface = Color(0xFF161616)

/** Five compact tabs and one stable page region; changing page never saves the workout. */
@OptIn(ExperimentalFoundationApi::class)
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
    milestones: @Composable ColumnScope.() -> Unit = {},
    photos: @Composable ColumnScope.() -> Unit = {},
) {
    val names = listOf("Hoy", "Levantamientos", "Hitos", "Fotos", "Notas")
    val pager = rememberPagerState(pageCount = { names.size })
    val scope = rememberCoroutineScope()
    val requesters = remember { List(names.size) { BringIntoViewRequester() } }
    LaunchedEffect(pager.currentPage) { withFrameNanos { }; requesters[pager.currentPage].bringIntoView() }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(horizontal = 16.dp).testTag("finish-layout"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("SESIÓN TERMINADA", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = Color(0xFF43D18C))
            Text(sessionName, style = KpknType.title.copy(fontSize = 26.sp, lineHeight = 30.sp), color = FinishCream,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)
            .horizontalScroll(rememberScrollState()).padding(4.dp).testTag("finish-tabs"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            names.forEachIndexed { index, name ->
                Box(Modifier.bringIntoViewRequester(requesters[index]).height(30.dp)
                    .clip(RoundedCornerShape(15.dp)).background(if (pager.currentPage == index) FinishCream else Color.Transparent)
                    .clickable { scope.launch { pager.animateScrollToPage(index) } }.padding(horizontal = 10.dp)
                    .semantics { stateDescription = if (pager.currentPage == index) "Seleccionada" else "No seleccionada" }, contentAlignment = Alignment.Center) {
                    Text(name, style = KpknType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = if (pager.currentPage == index) Color(0xFF0B0B0B) else FinishCream.copy(alpha = .62f), maxLines = 1)
                }
            }
        }
        warnings()
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("finish-pager")) { page ->
            Column(Modifier.fillMaxSize().testTag("finish-page-$page"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (page) { 0 -> today(); 1 -> lifts(); 2 -> milestones(); 3 -> photos(); else -> notes() }
            }
        }
        Row(Modifier.fillMaxWidth().height(68.dp).padding(bottom = 2.dp).testTag("finish-fabs"),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            FinishRoundAction("Volver al entrenamiento", "finish-back", Icons.AutoMirrored.Filled.ArrowBack, onBack)
            FloatingActionButton(onClick = { if (!saving) onSave() }, modifier = Modifier.size(64.dp).testTag("finish-save")
                .semantics { contentDescription = "Guardar y terminar entrenamiento"; if (saveDescription != null) stateDescription = saveDescription },
                shape = CircleShape, containerColor = FinishCream, contentColor = Color.Black) {
                if (saving) CircularProgressIndicator(Modifier.size(26.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(Icons.Default.Check, null, Modifier.size(27.dp))
            }
            FinishRoundAction("Compartir", "finish-share", Icons.Default.Share, onShare)
        }
    }
}

@Composable
private fun FinishRoundAction(label: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    FilledIconButton(onClick = action, modifier = Modifier.size(48.dp).testTag(tag).border(1.dp, FinishCream.copy(alpha = .12f), CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1A1A1A), contentColor = FinishCream)) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

@Composable
internal fun ColumnScope.FinishTodayPage(
    values: TowerRingValues, before: TowerRingValues, drains: List<Int>, onRing: (RingChannel) -> Unit,
    metrics: List<Pair<String, String>>, onDetails: () -> Unit,
) {
    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        KpknTowerRings(values = values, from = before, onRingTap = onRing,
            modifier = Modifier.weight(1f).heightIn(max = 224.dp).fillMaxHeight().testTag("finish-rings"))
        Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(RingChannel.ENERGIA, RingChannel.MUSCULOS, RingChannel.COLUMNA).forEach { channel ->
                Column(Modifier.fillMaxWidth().clickable { onRing(channel) }.padding(vertical = 2.dp)) {
                    Text(when(channel) { RingChannel.ENERGIA -> "ENERGÍA"; RingChannel.MUSCULOS -> "MÚSCULOS"; RingChannel.COLUMNA -> "COLUMNA" },
                        style = KpknType.eyebrow.copy(fontSize = 11.sp), color = FinishCream.copy(alpha = .7f))
                    Text(values[channel]?.let { "$it %" } ?: "—", style = KpknType.title.copy(fontSize = 22.sp), color = FinishCream)
                }
            }
        }
    }
    Text("Toca un anillo para ajustarlo", style = KpknType.caption.copy(fontSize = 12.sp), color = FinishMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
    metrics.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (name, value) ->
                Column(Modifier.weight(1f).background(FinishSurface, RoundedCornerShape(16.dp)).padding(10.dp)) {
                    Text(name, style = KpknType.caption.copy(fontSize = 11.sp), color = FinishMuted, maxLines = 1)
                    Text(value, style = KpknType.bodyStrong.copy(fontSize = 17.sp), color = FinishCream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text("Ver detalles de la carga", style = KpknType.caption.copy(fontSize = 11.sp), color = FinishMuted,
        modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDetails).padding(2.dp).testTag("finish-details"))
}

@Composable
internal fun ColumnScope.FinishLiftsPage(lifts: List<FinishLiftProgress>, sessionName: String, tonnage: List<Double>, totalVolume: Double, intensity: String, technique: String) {
    val star = lifts.filter { it.isImproved }.maxByOrNull { it.deltaKg ?: 0.0 } ?: lifts.firstOrNull { it.best != null }
    val otherLifts = lifts.filter { it.exerciseId != star?.exerciseId }
    if (star != null) {
        Column(Modifier.fillMaxWidth().weight(1f)
            .testTag("finish-lift-highlight").background(FinishSurface, RoundedCornerShape(20.dp))
            .border(1.dp, FinishCream.copy(alpha = .18f), RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (star.isImproved) "MEJOR PROGRESO" else "MEJOR SERIE DE HOY", style = KpknType.eyebrow.copy(fontSize = 11.sp), color = FinishMuted)
                if (star.deltaKg != null) Text(finishDeltaLabel(star.deltaKg), style = KpknType.caption, color = if(star.isImproved) Color(0xFF7FE3B0) else FinishMuted)
            }
            Text(star.name, style = KpknType.bodyStrong.copy(fontSize = 16.sp), color = FinishCream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (star.history.isNotEmpty()) {
                FinishSparkline(star.history.map { it.weight } + listOfNotNull(star.best?.weight),
                    Modifier.fillMaxWidth().weight(1f))
            } else {
                // A first set is a datum, not a trend with a lone point at the bottom.
                Box(Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center) {
                    BasicText(finishSetLabel(star.best),
                        style = KpknType.title.copy(color = FinishCream, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        maxLines = 1, softWrap = false,
                        autoSize = remember { TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 52.sp, stepSize = 1.sp) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(star.previous?.let { "${finishNumber(it.weight)} kg × ${it.reps}" } ?: "Sin registro previo", style = KpknType.caption, color = FinishMuted)
                if (star.history.isNotEmpty()) Text("Hoy: ${finishSetLabel(star.best)}", style = KpknType.caption, color = FinishMuted, maxLines = 1)
            }
        }
    }
    if (otherLifts.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 160.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(otherLifts, key = { it.exerciseId }) { lift ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(lift.name, style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = FinishCream, maxLines = 2)
                    Text("${finishSetLabel(lift.best)} · ${lift.setCount} series", style = KpknType.caption, color = FinishMuted)
                }
                if (lift.history.isNotEmpty()) FinishSparkline(lift.history.map { it.weight } + listOfNotNull(lift.best?.weight), Modifier.size(54.dp, 28.dp), FinishCream)
                Text(finishDeltaLabel(lift.deltaKg), style = KpknType.caption, color = if(lift.isImproved) Color(0xFF7FE3B0) else FinishMuted)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("finish-tonnage")
        .then(if (star == null) Modifier.weight(1f) else Modifier.heightIn(min = 126.dp, max = 180.dp))
        .background(FinishSurface, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tonelaje de $sessionName", style = KpknType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = FinishCream, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${finishNumber(totalVolume, 0)} kg hoy", style = KpknType.caption, color = FinishMuted)
        }
        FinishTonnageBars((tonnage + totalVolume).takeLast(6), Modifier.fillMaxWidth().height(72.dp))
    }
}

@Composable
private fun FinishTonnageBars(values: List<Double>, modifier: Modifier) {
    Canvas(modifier.testTag("finish-tonnage-bars").semantics { contentDescription = "Tonelaje de las últimas ${values.size} sesiones" }) {
        val peak = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val gap = 10.dp.toPx()
        val width = minOf(36.dp.toPx(), ((size.width - gap * (values.size - 1)) / values.size.coerceAtLeast(1)).coerceAtLeast(1f))
        val start = (size.width - (values.size * width + (values.size - 1) * gap)) / 2f
        values.forEachIndexed { index, value ->
            val height = (value.coerceAtLeast(0.0) / peak).toFloat() * size.height
            drawRoundRect(if(index == values.lastIndex) FinishPeach else Color(0xFF2A2A2A), Offset(start + index*(width+gap),size.height-height),
                androidx.compose.ui.geometry.Size(width,height), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        }
    }
}

@Composable
internal fun FinishMilestonesPage(milestones: List<SessionMilestone>, history: List<com.example.kpkn.data.models.WorkoutLog>, lifts: List<FinishLiftProgress> = emptyList(), onEvolution: ((String) -> Unit)? = null) {
    val ordered = remember(milestones) { milestones.sortedByDescending { it.createdAtIso } }
    val previous = remember(history) { history.flatMap { log -> log.sessionMilestones.map { log.date to it } }.sortedByDescending { it.second.createdAtIso }.take(12) }
    LazyColumn(Modifier.fillMaxSize().testTag("finish-milestones"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Hitos de hoy", style = KpknType.bodyStrong, color = FinishCream) }
        if(ordered.isEmpty()) item { Text("Sin hitos registrados en esta sesión", style = KpknType.caption, color = FinishMuted) }
        items(ordered, key = { "today-${it.id}" }) { milestone ->
            Column(Modifier.fillMaxWidth().background(FinishSurface,RoundedCornerShape(20.dp)).padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(milestone.label.uppercase(),style = KpknType.eyebrow.copy(fontSize=11.sp),color=FinishMuted,modifier=Modifier.weight(1f))
                    Icon(Icons.Default.EmojiEvents,null,Modifier.size(22.dp),tint=FinishPeach)
                }
                Text(milestone.exerciseName,style=KpknType.bodyStrong,color=FinishCream)
                val lift = lifts.firstOrNull { it.exerciseId == milestone.exerciseId }
                lift?.best?.let { best ->
                    Text(finishSetLabel(best), style = KpknType.title.copy(fontSize = 30.sp), color = FinishCream)
                    if(lift.history.isNotEmpty()) FinishSparkline(lift.history.map { it.weight } + best.weight, Modifier.fillMaxWidth().height(70.dp))
                    lift.deltaKg?.let { Text(finishDeltaLabel(it), style = KpknType.caption, color = if(it > 0) Color(0xFF7FE3B0) else FinishMuted) }
                }
                milestone.detail?.let { Text(it,style=KpknType.caption,color=FinishMuted) }
                if (onEvolution != null && lift != null) {
                    TextButton(onClick = { onEvolution(lift.exerciseId) }, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("finish-evolution-${lift.exerciseId}"),
                        colors = ButtonDefaults.textButtonColors(contentColor = FinishCream)) {
                        Text("Ver evolución", style = KpknType.caption)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, null, Modifier.size(15.dp))
                    }
                }
            }
        }
        if(previous.isNotEmpty()) item { Text("Historial de hitos",style=KpknType.bodyStrong,color=FinishCream) }
        items(previous, key = { "${it.first}-${it.second.id}" }) { (date,milestone) ->
            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(date.take(10),style=KpknType.caption,color=FinishMuted)
                Column(Modifier.weight(1f)) { Text(milestone.exerciseName,style=KpknType.bodyStrong,color=FinishCream);Text(milestone.label,style=KpknType.caption,color=FinishMuted) }
            }
        }
    }
}

@Composable
internal fun ColumnScope.FinishNotesPage(
    note: String, onNote: (String)->Unit, date: String, savedNotes: List<SessionSavedNote>, history: List<com.example.kpkn.data.models.WorkoutLog>,
    checklist: List<SessionChecklistItem>, onAddTask: (String)->Unit, onToggleTask: (String)->Unit, onRemoveTask: (String)->Unit,
    discomfortCount: Int, onDiscomforts: ()->Unit,
) {
    var task by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Nota de sesión",style=KpknType.bodyStrong,color=FinishCream);Text(date,style=KpknType.caption,color=FinishMuted) }
    OutlinedTextField(value=note,onValueChange=onNote,modifier=Modifier.weight(1f).fillMaxWidth().testTag("finish-notes"),
        placeholder={Text("Qué funcionó, qué cambiarías la próxima vez…",style=KpknType.body,color=FinishMuted)},shape=RoundedCornerShape(18.dp),
        textStyle=KpknType.body.copy(color=FinishCream),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=FinishCream.copy(alpha=.4f),unfocusedBorderColor=FinishCream.copy(alpha=.1f),focusedContainerColor=FinishSurface,unfocusedContainerColor=FinishSurface,cursorColor=FinishCream))
    Column(Modifier.fillMaxWidth().heightIn(max=190.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Checklist",style=KpknType.bodyStrong,color=FinishCream);Text("${checklist.count{it.done}}/${checklist.size}",style=KpknType.caption,color=FinishMuted) }
        checklist.forEach { item ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(item.done,{onToggleTask(item.id)},modifier=Modifier.size(32.dp).testTag("finish-task-${item.id}"),colors=CheckboxDefaults.colors(checkedColor=FinishCream,checkmarkColor=Color.Black,uncheckedColor=FinishMuted))
                Text(item.text,style=KpknType.caption.copy(textDecoration=if(item.done)TextDecoration.LineThrough else TextDecoration.None),color=if(item.done)FinishMuted else FinishCream,modifier=Modifier.weight(1f))
                IconButton(onClick={onRemoveTask(item.id)},modifier=Modifier.size(28.dp)) { Icon(Icons.Default.Close,"Eliminar tarea",Modifier.size(14.dp),tint=FinishMuted) }
            }
        }
        Row(Modifier.fillMaxWidth().border(1.dp,FinishCream.copy(alpha=.12f),RoundedCornerShape(15.dp)).padding(start=10.dp),verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(value=task,onValueChange={task=it},modifier=Modifier.weight(1f).padding(vertical=8.dp).testTag("finish-new-task"),singleLine=true,textStyle=KpknType.caption.copy(color=FinishCream),decorationBox={inner->Box{if(task.isBlank())Text("Añadir tarea…",style=KpknType.caption,color=FinishMuted);inner()}})
            IconButton(onClick={if(task.isNotBlank()){onAddTask(task.trim());task=""}},enabled=task.isNotBlank(),modifier=Modifier.size(32.dp).testTag("finish-add-task")){Icon(Icons.Default.Add,"Añadir tarea",Modifier.size(18.dp),tint=FinishCream)}
        }
        TextButton(onClick={showHistory=!showHistory},modifier=Modifier.height(30.dp).testTag("finish-note-history"),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(contentColor=FinishCream)){Text("Notas anteriores",style=KpknType.caption)}
        if(showHistory) {
            val previous = savedNotes.map { it.createdAtIso to it.text } + history.flatMap { log ->
                log.sessionSavedNotes.map { it.createdAtIso.ifBlank{log.date} to it.text }.ifEmpty { listOfNotNull((log.notes?.takeIf{it.isNotBlank()} ?: log.sessionNotes.takeIf{it.isNotBlank()})?.let{log.date to it}) }
            }
            previous.distinct().sortedByDescending{it.first}.forEach { (iso,text)->Column(Modifier.padding(vertical=4.dp)){Text(iso.take(10),style=KpknType.caption,color=FinishMuted);Text(text,style=KpknType.caption,color=FinishCream)} }
            if(previous.isEmpty())Text("Sin notas anteriores",style=KpknType.caption,color=FinishMuted)
        }
    }
    TextButton(onClick=onDiscomforts,modifier=Modifier.fillMaxWidth().height(28.dp).testTag("finish-discomforts"),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(contentColor=FinishMuted)){Text("Molestias reportadas ($discomfortCount)",style=KpknType.caption)}
}

@Composable
internal fun FinishPhotosPage(photos: List<StoryPhoto>, onOpenAlbum: (() -> Unit)? = null, onCreateStory: (() -> Unit)? = null, sessionDate: String? = null) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val current = photos.getOrNull(selected)
    Column(Modifier.fillMaxSize().testTag("finish-photos"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Fotos de la sesión",style=KpknType.bodyStrong,color=FinishCream);TextButton(onClick={if(onOpenAlbum!=null)onOpenAlbum() else selected=if(photos.isNotEmpty())0 else -1},enabled=photos.isNotEmpty(),colors=ButtonDefaults.textButtonColors(contentColor=FinishCream)){Text("Álbum · ${photos.size}",style=KpknType.caption)}}
        if (sessionDate != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sessionDate, style = KpknType.caption, color = FinishMuted)
            Text("${photos.size} fotos", style = KpknType.caption, color = FinishMuted)
        }
        if(photos.isEmpty())Text("No hay fotos en esta sesión",style=KpknType.caption,color=FinishMuted)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(photos.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { photo -> Box(Modifier.weight(1f).aspectRatio(.8f).clip(RoundedCornerShape(18.dp)).clickable{selected=photos.indexOf(photo)}) {
                        FinishPhotoImage(photo,Modifier.fillMaxSize(),ContentScale.Crop)
                        photo.createdAtMs?.takeIf { it > 0 }?.let { timestamp ->
                            val time = remember(timestamp) { java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }
                            Text(time, style = KpknType.caption.copy(fontSize = 10.sp), color = FinishCream,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                    .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        if(!photo.exerciseName.isNullOrBlank() || !photo.detail.isNullOrBlank()) {
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(7.dp).background(Color(0xB012100E),RoundedCornerShape(12.dp)).padding(8.dp)) {
                                photo.exerciseName?.let{Text(it,style=KpknType.caption,color=FinishCream,maxLines=1,overflow=TextOverflow.Ellipsis)}
                                photo.detail?.let{Text(it,style=KpknType.caption.copy(fontSize=11.sp),color=FinishCream.copy(alpha=.8f),maxLines=1,overflow=TextOverflow.Ellipsis)}
                            }
                        }
                    } }
                    if(row.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
        if (photos.isNotEmpty() && onCreateStory != null) {
            OutlinedButton(onClick = onCreateStory, modifier = Modifier.fillMaxWidth().testTag("finish-photo-story"),
                shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FinishCream.copy(alpha = .12f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = FinishSurface, contentColor = FinishCream)) {
                Icon(Icons.Default.Share, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear historia con estas fotos", style = KpknType.caption, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(17.dp))
            }
        }
    }
    if (current != null) WorkoutPhotoViewer(
        path = current.path, title = current.exerciseName?.takeIf { it.isNotBlank() } ?: "Foto de la sesión",
        caption = listOfNotNull(current.setIndex?.let { "Serie ${it + 1}" }, current.detail?.takeIf { it.isNotBlank() },
            "Marca".takeIf { current.isPr && current.detail?.contains("Marca", ignoreCase = true) != true }).joinToString(" · "),
        createdAtMs = current.createdAtMs, fallbackDate = sessionDate?.let { "Sesión · $it" },
        positionLabel = "${selected + 1}/${photos.size}", onDismiss = { selected = -1 },
        onPrevious = if (photos.size > 1) ({ selected = (selected - 1 + photos.size) % photos.size }) else null,
        onNext = if (photos.size > 1) ({ selected = (selected + 1) % photos.size }) else null,
    )
}

@Composable
private fun FinishPhotoImage(photo: StoryPhoto, modifier: Modifier, scale: ContentScale) {
    val uri=remember(photo.path){if(photo.path.startsWith("content:")||photo.path.startsWith("file:"))photo.path else Uri.fromFile(File(photo.path)).toString()}
    AsyncImage(model=rememberStableUriImageRequest(uri),contentDescription=photo.exerciseName ?: "Foto de la sesión",modifier=modifier,contentScale=scale)
}

@Composable
internal fun FinishSparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = FinishPeach) {
    Canvas(modifier.semantics { contentDescription = if(values.size < 2) "Sin historial suficiente para mostrar una tendencia" else "Evolución en ${values.size} registros" }) {
        val usable = values.filter { it.isFinite() }
        drawLine(FinishCream.copy(alpha=.08f),Offset(0f,size.height-2f),Offset(size.width,size.height-2f),1.dp.toPx())
        if(usable.isEmpty())return@Canvas
        val minimum=usable.minOrNull() ?: 0.0;val range=((usable.maxOrNull() ?: 0.0)-minimum).coerceAtLeast(1.0);val inset=5.dp.toPx();val path=Path()
        usable.forEachIndexed { index,value ->
            val x=if(usable.size==1)size.width/2 else inset+(size.width-inset*2)*index/(usable.size-1)
            val y=size.height-inset-((value-minimum)/range).toFloat()*(size.height-inset*2)
            if(index==0)path.moveTo(x,y)else path.lineTo(x,y)
            if(index==usable.lastIndex)drawCircle(color,3.dp.toPx(),Offset(x,y))
        }
        if(usable.size>1)drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
