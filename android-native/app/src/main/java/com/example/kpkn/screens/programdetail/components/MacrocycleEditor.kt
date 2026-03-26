package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*

@Composable
fun MacrocycleEditor(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedBlocks by remember { mutableStateOf(setOf("0")) }
    var editingBlock by remember { mutableStateOf<EditingItem?>(null) }
    var editingMeso by remember { mutableStateOf<EditingItem?>(null) }
    var editingWeek by remember { mutableStateOf<EditingItem?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    val isSimple = remember(program) {
        program.structure == ProgramStructure.SIMPLE ||
            (program.macrocycles.size == 1 && (program.macrocycles.firstOrNull()?.blocks?.size ?: 0) <= 1)
    }

    val stats = remember(program) {
        var weeks = 0; var sessions = 0; var mesos = 0; var blocks = 0
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                blocks++
                block.mesocycles.forEach { meso ->
                    mesos++
                    weeks += meso.weeks.size
                    sessions += meso.weeks.sumOf { it.sessions.size }
                }
            }
        }
        ProgramStats(weeks, sessions, mesos, blocks)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Stats summary
        Text("Editor de Estructura", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("Bloques", "${stats.blocks}")
            StatChip("Mesos", "${stats.mesos}")
            StatChip("Semanas", "${stats.weeks}")
            StatChip("Sesiones", "${stats.sessions}")
        }
        Spacer(Modifier.height(8.dp))

        // Simple warning
        if (isSimple) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBBF24).copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A0\uFE0F", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Programa simple. Agregar más de 1 bloque lo convertirá en complejo.", fontSize = 9.sp, color = Color(0xFF92400E))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Add block button
        OutlinedButton(
            onClick = {
                editingBlock = EditingItem(type = EditType.ADD, blockIndex = null)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Agregar Bloque", fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))

        // Tree view
        program.macrocycles.forEachIndexed { macroIdx, macro ->
            macro.blocks.forEachIndexed { blockIdx, block ->
                val blockKey = "$macroIdx-$blockIdx"
                val isExpanded = blockKey in expandedBlocks

                BlockNode(
                    block = block,
                    macroIndex = macroIdx,
                    blockIndex = blockIdx,
                    isExpanded = isExpanded,
                    onToggle = {
                        expandedBlocks = if (isExpanded) expandedBlocks - blockKey else expandedBlocks + blockKey
                    },
                    onEditBlock = {
                        editingBlock = EditingItem(type = EditType.EDIT, blockIndex = blockIdx, data = block)
                    },
                    onDeleteBlock = {
                        pendingDelete = DeleteTarget.Block(macroIdx, blockIdx)
                    },
                    onAddMeso = {
                        editingMeso = EditingItem(type = EditType.ADD, blockIndex = blockIdx)
                    },
                    onEditMeso = { mesoIdx, meso ->
                        editingMeso = EditingItem(type = EditType.EDIT, blockIndex = blockIdx, mesoIndex = mesoIdx, data = meso)
                    },
                    onDeleteMeso = { mesoIdx ->
                        pendingDelete = DeleteTarget.Mesocycle(macroIdx, blockIdx, mesoIdx)
                    },
                    onAddWeek = { mesoIdx ->
                        editingWeek = EditingItem(type = EditType.ADD, blockIndex = blockIdx, mesoIndex = mesoIdx)
                    },
                    onDeleteWeek = { mesoIdx, weekIdx ->
                        pendingDelete = DeleteTarget.Week(macroIdx, blockIdx, mesoIdx, weekIdx)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    // Edit dialogs
    editingBlock?.let { item ->
        BlockEditDialog(
            block = if (item.type == EditType.EDIT) item.data as? Block else null,
            onSave = { name ->
                val updated = if (item.type == EditType.ADD) {
                    val newBlock = Block(id = "block_${System.nanoTime()}", name = name, mesocycles = listOf(
                        Mesocycle(id = "meso_${System.nanoTime()}", name = "Meso 1", goal = MesocycleGoal.ACCUMULATION, weeks = listOf(
                            ProgramWeek(id = "week_${System.nanoTime()}", name = "Semana 1"),
                        )),
                    ))
                    program.copy(macrocycles = program.macrocycles.map { macro ->
                        macro.copy(blocks = macro.blocks + newBlock)
                    })
                } else {
                    program.copy(macrocycles = program.macrocycles.map { macro ->
                        macro.copy(blocks = macro.blocks.mapIndexed { i, b ->
                            if (i == item.blockIndex) b.copy(name = name) else b
                        })
                    })
                }
                onUpdateProgram(updated)
                editingBlock = null
            },
            onDismiss = { editingBlock = null },
        )
    }

    editingMeso?.let { item ->
        MesoEditDialog(
            onSave = { name, goal ->
                val newMeso = Mesocycle(id = "meso_${System.nanoTime()}", name = name, goal = goal, weeks = listOf(
                    ProgramWeek(id = "week_${System.nanoTime()}", name = "Semana 1"),
                ))
                val updated = program.copy(macrocycles = program.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                        if (bi != item.blockIndex) block
                        else if (item.type == EditType.ADD) block.copy(mesocycles = block.mesocycles + newMeso)
                        else block.copy(mesocycles = block.mesocycles.mapIndexed { mi, meso ->
                            if (mi == item.mesoIndex) meso.copy(name = name, goal = goal) else meso
                        })
                    })
                })
                onUpdateProgram(updated)
                editingMeso = null
            },
            onDismiss = { editingMeso = null },
        )
    }

    editingWeek?.let { item ->
        WeekEditDialog(
            onSave = { name ->
                val newWeek = ProgramWeek(id = "week_${System.nanoTime()}", name = name)
                val updated = program.copy(macrocycles = program.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                        if (bi != item.blockIndex) block
                        else block.copy(mesocycles = block.mesocycles.mapIndexed { mi, meso ->
                            if (mi != item.mesoIndex) meso
                            else meso.copy(weeks = meso.weeks + newWeek)
                        })
                    })
                })
                onUpdateProgram(updated)
                editingWeek = null
            },
            onDismiss = { editingWeek = null },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar", fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar este elemento?") },
            confirmButton = {
                Button(onClick = {
                    val updated = when (target) {
                        is DeleteTarget.Block -> program.copy(macrocycles = program.macrocycles.map { macro ->
                            macro.copy(blocks = macro.blocks.filterIndexed { i, _ -> i != target.blockIndex })
                        })
                        is DeleteTarget.Mesocycle -> program.copy(macrocycles = program.macrocycles.map { macro ->
                            macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                                if (bi != target.blockIndex) block
                                else block.copy(mesocycles = block.mesocycles.filterIndexed { i, _ -> i != target.mesoIndex })
                            })
                        })
                        is DeleteTarget.Week -> program.copy(macrocycles = program.macrocycles.map { macro ->
                            macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                                if (bi != target.blockIndex) block
                                else block.copy(mesocycles = block.mesocycles.mapIndexed { mi, meso ->
                                    if (mi != target.mesoIndex) meso
                                    else meso.copy(weeks = meso.weeks.filterIndexed { i, _ -> i != target.weekIndex })
                                })
                            })
                        })
                    }
                    onUpdateProgram(updated)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

// ─── Block Node ─────────────────────────────────────────────────────────────

@Composable
private fun BlockNode(
    block: Block,
    macroIndex: Int,
    blockIndex: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEditBlock: () -> Unit,
    onDeleteBlock: () -> Unit,
    onAddMeso: () -> Unit,
    onEditMeso: (Int, Mesocycle) -> Unit,
    onDeleteMeso: (Int) -> Unit,
    onAddWeek: (Int) -> Unit,
    onDeleteWeek: (Int, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column {
            // Block header
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(block.name, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${block.mesocycles.size} meso${if (block.mesocycles.size != 1) "s" else ""} · ${block.mesocycles.sumOf { it.weeks.size }} semanas", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEditBlock, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDeleteBlock, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Eliminar", modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)) {
                    block.mesocycles.forEachIndexed { mesoIdx, meso ->
                        MesoNode(
                            meso = meso,
                            onEdit = { onEditMeso(mesoIdx, meso) },
                            onDelete = { onDeleteMeso(mesoIdx) },
                            onAddWeek = { onAddWeek(mesoIdx) },
                            onDeleteWeek = { weekIdx -> onDeleteWeek(mesoIdx, weekIdx) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedButton(onClick = onAddMeso, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mesociclo", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MesoNode(
    meso: Mesocycle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddWeek: () -> Unit,
    onDeleteWeek: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meso.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${meso.goal.name} · ${meso.weeks.size} sem", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(12.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Eliminar", modifier = Modifier.size(12.dp), tint = Color(0xFFEF4444))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                meso.weeks.forEachIndexed { idx, week ->
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 3.dp).clickable { onDeleteWeek(idx) },
                    ) {
                        Text(week.name, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onAddWeek, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Add, "Agregar semana", modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

// ─── Edit Dialogs ───────────────────────────────────────────────────────────

@Composable
private fun BlockEditDialog(block: Block?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(block?.name ?: "Nuevo Bloque") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (block != null) "Editar Bloque" else "Nuevo Bloque", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MesoEditDialog(onSave: (String, MesocycleGoal) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Nuevo Mesociclo") }
    var goal by remember { mutableStateOf(MesocycleGoal.ACCUMULATION) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mesociclo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                Text("Objetivo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MesocycleGoal.entries.forEach { g ->
                        FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(g.name, fontSize = 9.sp) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, goal) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WeekEditDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Nueva Semana") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Semana", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ─── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ProgramStats(val weeks: Int, val sessions: Int, val mesos: Int, val blocks: Int)

private enum class EditType { ADD, EDIT }
private data class EditingItem(val type: EditType, val blockIndex: Int?, val mesoIndex: Int? = null, val data: Any? = null)

private sealed class DeleteTarget {
    data class Block(val macroIndex: Int, val blockIndex: Int) : DeleteTarget()
    data class Mesocycle(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int) : DeleteTarget()
    data class Week(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int, val weekIndex: Int) : DeleteTarget()
}
