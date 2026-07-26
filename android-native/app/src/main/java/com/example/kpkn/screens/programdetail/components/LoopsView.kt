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
import androidx.compose.material.icons.filled.Info
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
import com.example.kpkn.data.models.LoopStatus
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
    val loops: List<LoopTemplateEntry>,
)

private data class LoopTemplateEntry(
    val title: String,
    val type: LoopType,
    val cadence: Int,
)

private val LOOP_TEMPLATES = listOf(
    LoopTemplate("deload-4", "Descarga cada 4", "\uD83E\uDDD8", "Semana de descarga automática cada 4 ciclos.", listOf(LoopTemplateEntry("Descarga", LoopType.DELOAD, 4))),
    LoopTemplate("1rm-8", "Test 1RM cada 8", "\uD83C\uDFCB\uFE0F", "Semana de pruebas de fuerza cada 8 ciclos.", listOf(LoopTemplateEntry("Test 1RM", LoopType.ONE_RM_TEST, 8))),
    LoopTemplate("deload-1rm", "Descarga + Test 1RM", "\u26A1", "Descarga cada 4 y Test 1RM cada 8 ciclos.", listOf(LoopTemplateEntry("Descarga", LoopType.DELOAD, 4), LoopTemplateEntry("Test 1RM", LoopType.ONE_RM_TEST, 8))),
    LoopTemplate("competition-12", "Competición cada 12", "\uD83C\uDFC6", "Competición cada 12 ciclos con descarga cada 4.", listOf(LoopTemplateEntry("Descarga", LoopType.DELOAD, 4), LoopTemplateEntry("Competición", LoopType.COMPETITION, 12))),
)

@Composable
fun LoopsView(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val loops = program.loops
    val currentCycle = LoopEngine.getCurrentCycle(program)
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
    var showInfo by remember { mutableStateOf(false) }

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Loops", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("${loops.size} loop${if (loops.size != 1) "s" else ""} activo${if (loops.size != 1) "s" else ""} · Ciclo $currentCycle", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Default.Info, contentDescription = "Qué son los loops")
            }
        }
        Text(
            "Un loop es un conjunto de ciclos espejo que termina en una semana de evento editable (E1, E2...).",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
        )
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
                                    val usedCadences = program.loops.map { it.repeatEveryXLoops.coerceAtLeast(1) }.toSet()
                                    val newLoops = template.loops.mapIndexedNotNull { i, entry ->
                                        val cadence = entry.cadence.coerceAtLeast(1)
                                        if (cadence in usedCadences) return@mapIndexedNotNull null
                                        Loop(
                                            id = "loop_${System.nanoTime()}_$i",
                                            title = entry.title,
                                            type = entry.type,
                                            repeatEveryXLoops = cadence,
                                            durationType = com.example.kpkn.data.models.DurationType.WEEK,
                                        )
                                    }
                                    onUpdateProgram(LoopEngine.materializeLoopWeeks(program.copy(loops = program.loops + newLoops)))
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
                        onUpdateProgram(LoopEngine.deleteLoop(program, loop.id))
                    },
                    onCancel = {
                        onUpdateProgram(LoopEngine.cancelLoop(program, loop.id))
                    },
                    onReactivate = {
                        onUpdateProgram(LoopEngine.reactivateLoop(program, loop.id))
                    },
                    onPostpone = {
                        onUpdateProgram(LoopEngine.postponeNextOccurrence(program, loop.id))
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        if (actionableOccurrences.isNotEmpty()) {
            Text("Semanas de loop", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            actionableOccurrences.forEach { occurrence ->
                val occurrenceId = program.loopOccurrences
                    .firstOrNull {
                        it.loopId == occurrence.loop.id &&
                            it.scheduledCycle == occurrence.projection.cycle
                    }?.id
                    ?: "occ_${occurrence.loop.id}_${occurrence.projection.cycle}"
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
                                    buildString {
                                        append(occurrence.weekLabel)
                                        append(" · ciclo ${occurrence.projection.cycle}")
                                        val origin = occurrence.originCycle
                                        if (origin != null && origin != occurrence.projection.cycle) {
                                            append(" (origen $origin)")
                                        }
                                        append(" · ${occurrence.dayLabel}")
                                        append(" · cada ${occurrence.loop.repeatEveryXLoops} ciclos")
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("Semana especial completa", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
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
                            OutlinedButton(
                                onClick = {
                                    onUpdateProgram(LoopEngine.postponeOccurrence(program, occurrenceId))
                                },
                            ) {
                                Text("Posponer")
                            }
                            TextButton(
                                onClick = {
                                    onUpdateProgram(LoopEngine.cancelOccurrence(program, occurrenceId))
                                },
                            ) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(120.dp))
    }

    // Add/Edit modal
    if (showAddModal || editingLoop != null) {
        LoopEditorModal(
            loop = editingLoop,
            existingLoops = program.loops,
            onSave = { loop ->
                onUpdateProgram(LoopEngine.upsertLoop(program, loop))
                showAddModal = false
                editingLoop = null
            },
            onDismiss = { showAddModal = false; editingLoop = null },
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Para qué sirven los loops", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Un loop no es el evento: es el tramo completo de ciclos que llevan a ese evento.")
                    Text("Las semanas S1, S2, etc. son espejo en cada ciclo; si editas S1, cambia S1 en todos los ciclos.")
                    Text("El evento se muestra como E1, E2... y es una semana real donde puedes crear sesiones.")
                    Text("Ejemplos: 5 ciclos hasta E1, descarga cada 4 ciclos o competición cada 12 ciclos.")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Entendido") } },
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
    val existingSession: Session?,
    val originCycle: Int? = null,
) {
    val statusLabel: String
        get() = if (existingSession != null) {
            "Ya hay una sesión en ese día: ${existingSession.name}"
        } else {
            "Todavía no hay sesión para este loop"
        }

    val countdownLabel: String
        get() = LoopEngine.formatLoopCountdown(projection.daysUntil)
}

private fun buildLoopOccurrences(program: Program, currentCycle: Int): List<LoopActionOccurrence> {
    val baseBlock = program.macrocycles.firstOrNull()?.blocks?.firstOrNull() ?: return emptyList()
    val loopWeeks = baseBlock.mesocycles.flatMap { it.weeks }.filter { it.isLoopWeek && it.loopId != null }
    if (loopWeeks.isEmpty()) return emptyList()

    val synced = if (program.loopOccurrences.isEmpty()) {
        LoopEngine.syncOccurrences(program)
    } else {
        program
    }
    val occurrencesByLoop = synced.loopOccurrences
        .filter {
            it.status != LoopStatus.CANCELLED &&
                it.status != LoopStatus.COMPLETED &&
                it.status != LoopStatus.POSTPONED
        }
        .groupBy { it.loopId }
    val projections = LoopEngine.projectLoops(program, fromCycle = currentCycle.coerceAtLeast(0), lookAheadCycles = 24)
    val projectionByLoopId = projections.groupBy { it.loop.id }

    return loopWeeks.mapNotNull { targetWeek ->
        val loop = program.loops.firstOrNull { it.id == targetWeek.loopId } ?: return@mapNotNull null
        val nextOcc = occurrencesByLoop[loop.id]?.minByOrNull { it.scheduledCycle }
        val projection = projectionByLoopId[loop.id]?.firstOrNull()
            ?: LoopProjection(
                loop = loop,
                cycle = nextOcc?.scheduledCycle ?: currentCycle.coerceAtLeast(1),
                isPostponed = (nextOcc?.originCycle ?: nextOcc?.scheduledCycle) != nextOcc?.scheduledCycle,
                isCancelled = nextOcc?.status == LoopStatus.CANCELLED ||
                    loop.id in (program.loopState?.cancelled ?: emptyList()),
                daysUntil = 0,
                weekInCycle = LoopEngine.getCycleLength(program),
            )
        val preferredDay = preferredLoopDay(loop, program.startDay ?: 1)
        val existingSession = targetWeek.sessions.firstOrNull { it.dayOfWeek == preferredDay }
        LoopActionOccurrence(
            loop = loop,
            projection = projection.copy(
                cycle = nextOcc?.scheduledCycle ?: projection.cycle,
                isPostponed = nextOcc?.let { it.originCycle != it.scheduledCycle } == true || projection.isPostponed,
                isCancelled = nextOcc?.status == LoopStatus.CANCELLED || projection.isCancelled,
            ),
            blockId = baseBlock.id,
            weekId = nextOcc?.weekInstanceId ?: targetWeek.id,
            weekLabel = targetWeek.name,
            dayLabel = dayLabel(preferredDay),
            existingSession = existingSession,
            originCycle = nextOcc?.originCycle,
        )
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
    onPostpone: () -> Unit,
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
                    IconButton(onClick = onPostpone, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.CalendarMonth, "Postergar", modifier = Modifier.size(14.dp), tint = Color(0xFF60A5FA))
                    }
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
    existingLoops: List<Loop>,
    onSave: (Loop) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(loop?.title ?: "") }
    var type by remember { mutableStateOf(loop?.type ?: LoopType.CUSTOM) }
    var repeatEvery by remember { mutableStateOf(loop?.repeatEveryXLoops?.toString() ?: "4") }
    val repeatValue = repeatEvery.toIntOrNull()?.coerceAtLeast(1) ?: 0
    val conflict = existingLoops.firstOrNull { candidate ->
        candidate.id != loop?.id && candidate.repeatEveryXLoops.coerceAtLeast(1) == repeatValue
    }

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
                if (conflict != null) {
                    Text(
                        "Conflicto: ${conflict.title} ya usa cada $repeatValue ciclos. Cambia la cadencia para evitar que dos loops compitan por la misma semana.",
                        fontSize = 10.sp,
                        color = Color(0xFFEF4444),
                    )
                }
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
                        repeatEveryXLoops = repeatValue.coerceAtLeast(1),
                    )
                    onSave(newLoop)
                },
                enabled = title.isNotBlank() && repeatValue > 0 && conflict == null,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
