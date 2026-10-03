package com.example.kpkn.screens.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.media.WorkoutAlbum
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.media.WorkoutMediaPendingRetryAlbum
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import java.io.File
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutAlbumsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: WorkoutAlbumsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var exerciseMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Álbumes de entrenamiento", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.prOnly,
                    onClick = { viewModel.setPrOnly(!state.prOnly) },
                    label = { Text("PR") },
                    leadingIcon = if (state.prOnly) {
                        { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                )
                Box {
                    OutlinedButton(onClick = { exerciseMenu = true }) {
                        Text(state.exerciseQuery.ifBlank { "Ejercicio" })
                    }
                    DropdownMenu(expanded = exerciseMenu, onDismissRequest = { exerciseMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Todos") },
                            onClick = {
                                viewModel.setExerciseQuery("")
                                exerciseMenu = false
                            },
                        )
                        state.exerciseOptions.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setExerciseQuery(name)
                                    exerciseMenu = false
                                },
                            )
                        }
                    }
                }
            }
            if (state.albums.isEmpty() && state.pendingRetries.isEmpty() && !state.isLoading) {
                Text(
                    "Todavía no hay fotos ni vídeos de entrenamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.pendingRetries.isNotEmpty()) {
                        item(key = "pending-media-heading") {
                            Text(
                                "Medios pendientes de guardar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(state.pendingRetries, key = { "pending:${it.historyKey}" }) { pending ->
                            PendingCaptureRetryRow(
                                pending = pending,
                                onRetry = { viewModel.retryPendingCaptures(pending.sessionKey) },
                            )
                        }
                    }
                    items(state.albums, key = { it.albumKey }) { album ->
                        AlbumRow(album = album, onClick = { onOpenAlbum(album.albumKey) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCaptureRetryRow(
    pending: WorkoutMediaPendingRetryAlbum,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    pending.sessionName?.trim()?.takeIf(String::isNotEmpty) ?: "Sesión de entrenamiento",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append(WorkoutAlbumGrouping.dateLabel(pending.createdAtMs))
                        append(" · ")
                        append(pending.pendingCount)
                        append(if (pending.pendingCount == 1) " medio pendiente" else " medios pendientes")
                        if (!pending.workoutLogId.isNullOrBlank()) append(" · Sesión finalizada")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pending.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun AlbumRow(album: WorkoutAlbum, onClick: () -> Unit) {
    val coverPath = album.coverThumbPath
    val resolvedCover by produceState<File?>(initialValue = null, key1 = coverPath) {
        value = null
        value = withContext(Dispatchers.IO) {
            coverPath?.let(::File)?.takeIf { it.isFile }
        }
    }
    val cover = resolvedCover
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverIsVideoFile = cover != null && cover.extension.lowercase() in setOf("mp4", "webm", "mov", "m4v", "3gp")
            if (cover != null && !coverIsVideoFile) {
                LocalMediaImage(
                    source = rememberLocalMediaImageSource(cover),
                    contentDescription = album.title,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▶", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(album.title, fontWeight = FontWeight.Bold)
                Text(
                    "${album.dateLabel} · ${album.mediaCount} ${if (album.mediaCount == 1) "medio" else "medios"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (album.prCount > 0) {
                Icon(Icons.Default.Star, contentDescription = "PR", tint = Color(0xFFFFC107))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutAlbumDetailScreen(
    albumKey: String,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    viewModel: WorkoutAlbumsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val album = remember(state.albums, albumKey) {
        state.albums.firstOrNull { it.albumKey == albumKey }
            ?: WorkoutAlbumGroupingSafe(state.media, albumKey)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.title ?: "Álbum", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        val items = album?.items.orEmpty()
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Este álbum está vacío.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(items.size, key = { items[it].id }) { index ->
                    val item = items[index]
                    com.example.kpkn.screens.workout.components.WorkoutMediaThumb(
                        media = item,
                        modifier = Modifier.height(110.dp),
                        onClick = { onOpenMedia(item.id) },
                    )
                }
            }
        }
    }
}

private fun WorkoutAlbumGroupingSafe(
    media: List<com.example.kpkn.data.models.WorkoutMedia>,
    albumKey: String,
): WorkoutAlbum? = com.example.kpkn.data.media.WorkoutAlbumGrouping.group(media)
    .firstOrNull { it.albumKey == albumKey }
