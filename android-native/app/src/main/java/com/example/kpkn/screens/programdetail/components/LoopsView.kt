package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopState
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.LoopProjection

private data class LoopTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val desc: String,
    val loops: List<Pair<String, LoopType>>,
)

private val LOOP_TEMPLATES = listOf(
    LoopTemplate("deload-4", "Descarga cada 4", "\uD83E\uDDD8", "Semana de descarga automática cada 4 ciclos.", listOf("Descarga" to LoopType.DELOAD)),
    LoopTemplate("1rm-8", "Test 1RM cada 8", "\uD83C\uDFCB\uFE0F", "Semana de pruebas de fuerza cada 8 ciclos.", listOf("Test 1RM" to LoopType.ONE_RM_TEST)),
    LoopTemplate("deload-1rm", "Descarga + Test 1RM", "\u26A1", "Descarga cada 4 y Test 1RM cada 8 ciclos.", listOf("Descarga" to LoopType.DELOAD, "Test 1RM" to LoopType.ONE_RM_TEST)),
    LoopTemplate("competition-12", "Competición cada 12", "\uD83C\uDFC6", "Competición cada 12 ciclos con descarga cada 4.", listOf("Descarga" to LoopType.DELOAD, "Competición" to LoopType.COMPETITION)),
)

@Composable
fun LoopsView(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val loops = program.loops
    val currentCycle = LoopEngine.getCurrentCycle(program)
    val projections = remember(program, currentCycle) { LoopEngine.projectLoops(program, currentCycle, 12) }
    val collisions = remember(projections) { LoopEngine.detectLoopCollisions(projections) }
    val cancelledSet = remember(program) { (program.loopState?.cancelled ?: emptyList()).toSet() }
    val legacyEvents = remember(program) { program.events.filter { it.repeatEveryXCycles != null } }
    val hasLegacy = legacyEvents.isNotEmpty() && loops.isEmpty()
    val normalizedProgram = remember(program) {
        if (program.loops.isNotEmpty()) program else LoopEngine.migrateEventsToLoops(program)
    }
    val actionableOccurrences = remember(normalizedProgram, currentCycle) {
        buildLoopOccurrences(normalizedProgram, currentCycle)
    }

    var showAddModal by remember { mutableStateOf(false) }
    var editingLoop by remember { mutableStateOf<Loop?>(null) }
    var showTemplates by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Legacy migration banner
        if (hasLegacy) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBBF24).copy(alpha = 0.2f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("\u26A1 ${legacyEvents.size} evento${if (legacyEvents.size > 1) "s" else ""} detectado${if (legacyEvents.size > 1) "s" else ""}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF92400E))
                        Text("Migra tus eventos a loops para usar el nuevo sistema.", fontSize = 9.sp, color = Color(0xFF92400E).copy(alpha = 0.7f))
                    }
                    Button(onClick = {
                        val updated = LoopEngine.migrateEventsToLoops(program)
                        onUpdateProgram(updated)
                    }) { Text("Migrar", fontSize = 10.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Header
        Text("Loops", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("${loops.size} loop${if (loops.size != 1) "s" else ""} activo${if (loops.size != 1) "s" else ""} · Ciclo $currentCycle", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.weight(1f)) {
                Text("Templates", fontSize = 10.sp)
            }
            Button(onClick = { showAddModal = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Agregar", fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Templates gallery
        if (showTemplates) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Templates", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showTemplates = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(14.dp))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LOOP_TEMPLATES) { template ->
                            Card(
                                modifier = Modifier.width(200.dp).clickable {
                                    val newLoops = template.loops.mapIndexed { i, (title, type) ->
                                        Loop(
                                            id = "loop_${System.nanoTime()}_$i",
                                            title = title,
                                            type = type,
                                            repeatEveryXLoops = if (type == LoopType.ONE_RM_TEST) 8 else 4,
                                            durationType = com.example.kpkn.data.models.DurationType.WEEK,
                                        )
                                    }
                                    onUpdateProgram(program.copy(loops = program.loops + newLoops))
                                    showTemplates = false
                                },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(template.emoji + " " + template.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(template.desc, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Active loops
        if (loops.isNotEmpty()) {
            Text("Loops activos", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            loops.forEach { loop ->
                LoopCard(
                    loop = loop,
                    isCancelled = loop.id in cancelledSet,
                    onEdit = { editingLoop = loop },
                    onDelete = {
                        onUpdateProgram(program.copy(loops = program.loops.filter { it.id != loop.id }))
                    },
                    onCancel = {
                        onUpdateProgram(LoopEngine.cancelLoop(program, loop.id))
                    },
                    onReactivate = {
                        onUpdateProgram(LoopEngine.reactivateLoop(program, loop.id))
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        // Timeline projections
        if (actionableOccurrences.isNotEmpty()) {
            Text("Acciones sugeridas", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            actionableOccurrences.forEach { occurrence ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${LoopEngine.getLoopTypeEmoji(occurrence.loop.type)} ${occurrence.loop.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Ciclo ${occurrence.projection.cycle} · ${occurrence.weekLabel} · ${occurrence.dayLabel}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    occurrence.statusLabel,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(occurrence.countdownLabel, fontSize = 9.sp) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onFocusWeek(occurrence.blockId, occurrence.weekId) }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Ver semana")
                            }
                            Button(onClick = { onCreateSessionForWeek(occurrence.weekId, occurrence.preferredDayOfWeek) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(occurrence.ctaLabel)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (projections.isNotEmpty()) {
            Text("Proyecciones (12 ciclos)", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(projections.take(24)) { proj ->
                    val isCollision = collisions.containsKey(proj.cycle) && collisions[proj.cycle]!!.any { it.loop.id == proj.loop.id }
                    val bgColor = when {
                        proj.isCancelled -> Color(0xFFEF4444).copy(alpha = 0.2f)
                        proj.isPostponed -> Color(0xFFFBBF24).copy(alpha = 0.2f)
                        isCollision -> Color(0xFFFBBF24).copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    }
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(LoopEngine.getLoopTypeEmoji(proj.loop.type), fontSize = 12.sp)
                        Text("C${proj.cycle}", fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text(LoopEngine.formatLoopCountdown(proj.daysUntil), fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isCollision) Text("⚠️", fontSize = 8.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    // Add/Edit modal
    if (showAddModal || editingLoop != null) {
        LoopEditorModal(
            loop = editingLoop,
            onSave = { loop ->
                if (editingLoop != null) {
                    onUpdateProgram(program.copy(loops = program.loops.map { if (it.id == loop.id) loop else it }))
                } else {
                    onUpdateProgram(program.copy(loops = program.loops + loop))
                }
                showAddModal = false
                editingLoop = null
            },
            onDismiss = { showAddModal = false; editingLoop = null },
        )
    }
}

private data class LoopActionOccurrence(
    val loop: Loop,
    val projection: LoopProjection,
    val blockId: String,
    val weekId: String,
    val weekLabel: String,
    val dayLabel: String,
    val preferredDayOfWeek: Int,
    val existingSession: Session?,
) {
    val statusLabel: String
        get() = if (existingSession != null) {
            "Ya hay una sesión en ese día: ${existingSession.name}"
        } else {
            "Todavía no hay sesión para este loop"
        }

    val countdownLabel: String
        get() = LoopEngine.formatLoopCountdown(projection.daysUntil)

    val ctaLabel: String
        get() = if (existingSession != null) "Crear adicional" else "Crear sesión"
}

private fun buildLoopOccurrences(program: Program, currentCycle: Int): List<LoopActionOccurrence> {
    val baseBlock = program.macrocycles.firstOrNull()?.blocks?.firstOrNull() ?: return emptyList()
    val orderedWeeks = baseBlock.mesocycles.flatMap { it.weeks }
    if (orderedWeeks.isEmpty()) return emptyList()

    val projections = LoopEngine.projectLoops(program, currentCycle, 6)
    return projections.mapNotNull { projection ->
        val weekIndex = preferredLoopWeekIndex(projection.loop, orderedWeeks.lastIndex)
        val targetWeek = orderedWeeks.getOrNull(weekIndex) ?: return@mapNotNull null
        val preferredDay = preferredLoopDay(projection.loop, program.startDay ?: 1)
        val existingSession = targetWeek.sessions.firstOrNull { it.dayOfWeek == preferredDay }
        LoopActionOccurrence(
            loop = projection.loop,
            projection = projection,
            blockId = baseBlock.id,
            weekId = targetWeek.id,
            weekLabel = targetWeek.name,
            dayLabel = dayLabel(preferredDay),
            preferredDayOfWeek = preferredDay,
            existingSession = existingSession,
        )
    }
}

private fun preferredLoopWeekIndex(loop: Loop, lastWeekIndex: Int): Int {
    return when (loop.type) {
        LoopType.COMPETITION -> lastWeekIndex
        LoopType.ONE_RM_TEST -> lastWeekIndex
        LoopType.DELOAD -> 0
        LoopType.CUSTOM -> lastWeekIndex
    }
}

private fun preferredLoopDay(loop: Loop, startDay: Int): Int {
    val explicitDay = loop.dayOfWeek?.takeIf { it in 1..7 }
    if (explicitDay != null) return explicitDay
    return when (loop.type) {
        LoopType.COMPETITION -> ((startDay - 1 + 5) % 7) + 1
        LoopType.ONE_RM_TEST -> ((startDay - 1 + 2) % 7) + 1
        LoopType.DELOAD -> startDay.coerceIn(1, 7)
        LoopType.CUSTOM -> startDay.coerceIn(1, 7)
    }
}

private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $dayOfWeek"
}

@Composable
private fun LoopCard(
    loop: Loop,
    isCancelled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onReactivate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(LoopEngine.getLoopTypeEmoji(loop.type), fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(loop.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${LoopEngine.getLoopTypeLabel(loop.type)} · cada ${loop.repeatEveryXLoops} ciclos",
                    fontSize = 9.sp,
                    color = if (isCancelled) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isCancelled) {
                    IconButton(onClick = onReactivate, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.PlayArrow, "Reactivar", modifier = Modifier.size(14.dp), tint = Color(0xFF10B981))
                    }
                } else {
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancelar", modifier = Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Eliminar", modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun LoopEditorModal(
    loop: Loop?,
    onSave: (Loop) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(loop?.title ?: "") }
    var type by remember { mutableStateOf(loop?.type ?: LoopType.CUSTOM) }
    var repeatEvery by remember { mutableStateOf(loop?.repeatEveryXLoops?.toString() ?: "4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (loop != null) "Editar Loop" else "Nuevo Loop", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nombre") }, singleLine = true)
                Text("Tipo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LoopType.entries.toList()) { lt ->
                        FilterChip(
                            selected = type == lt,
                            onClick = { type = lt },
                            label = { Text(LoopEngine.getLoopTypeLabel(lt), fontSize = 9.sp) },
                        )
                    }
                }
                OutlinedTextField(value = repeatEvery, onValueChange = { repeatEvery = it.filter { c -> c.isDigit() } }, label = { Text("Repetir cada X ciclos") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLoop = (loop ?: Loop(
                        id = "loop_${System.nanoTime()}",
                        title = "",
                        type = LoopType.CUSTOM,
                        repeatEveryXLoops = 4,
                        durationType = com.example.kpkn.data.models.DurationType.WEEK,
                    )).copy(
                        title = title,
                        type = type,
                        repeatEveryXLoops = repeatEvery.toIntOrNull() ?: 4,
                    )
                    onSave(newLoop)
                },
                enabled = title.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
