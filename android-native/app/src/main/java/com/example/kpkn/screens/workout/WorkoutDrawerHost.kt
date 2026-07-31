package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.util.Locale
import com.example.kpkn.ui.components.KpknAlertDialog

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WorkoutDrawer(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    showCloseButton: Boolean = true,
    hazeState: HazeState? = LocalHazeState.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    fun handleDismiss() {
        showContent = false
        onDismiss()
    }

    BackHandler(enabled = dismissible) { handleDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(state = hazeState, style = kpknGlassStyle())
                        } else {
                            Modifier.background(KpknGlass.FallbackScrim)
                        }
                    )
                    .clickable(
                        onClick = { handleDismiss() }
                    )
            )
        }

        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.40f),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        if (showCloseButton) {
                            IconButton(
                                onClick = { handleDismiss() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun QuickExecutionErrorDiscomfortSheet(
    exerciseName: String,
    onSave: (discomfortIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val filteredEntries = remember(searchQuery) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) emptyList()
        else DISCOMFORT_CATALOG.filter { it.label.lowercase(Locale.ROOT).contains(normalized) || it.description.lowercase(Locale.ROOT).contains(normalized) }.sortedBy { it.label }
    }
    KpknSheet(
        onDismissRequest = {},
        dismissible = false,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Reportar molestias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text(exerciseName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Tuviste alguna molestia al realizar este ejercicio?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar molestia") }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) }, textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
                if (filteredEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredEntries.forEach { entry ->
                            val selected = selectedIds.contains(entry.id)
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selected, onClick = { if (selected) selectedIds.remove(entry.id) else selectedIds.add(entry.id) }, label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                                IconButton(onClick = { infoEntry = entry }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Info, "Detalle", Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f)) }
                            }
                        }
                    }
                } else if (searchQuery.isBlank()) {
                    Text("Escribe para buscar molestias...", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                } else {
                    Text("No se encontraron resultados para \"$searchQuery\"", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                if (selectedIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedIds.forEach { id ->
                            val entry = DISCOMFORT_CATALOG.find { it.id == id }
                            val label = entry?.label ?: id
                            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.Close, "Quitar", Modifier.size(14.dp).clickable { selectedIds.remove(id) }, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSave(emptyList()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text("Sin molestias", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                Button(onClick = { onSave(selectedIds.toList()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), enabled = selectedIds.isNotEmpty()) { Text("Guardar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }
    }
    infoEntry?.let { entry ->
        KpknAlertDialog(onDismissRequest = { infoEntry = null }, title = { Text(entry.label, fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(entry.description, style = MaterialTheme.typography.bodySmall); Text("Sección: ${entry.section.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onClick = { infoEntry = null }) { Text("Entendido") } })
    }
}
