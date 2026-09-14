package com.example.kpkn.screens.albums

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.workout.components.WorkoutVideoPlayer
import com.example.kpkn.ui.components.KpknAlertDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutMediaViewerScreen(
    mediaId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: WorkoutMediaViewerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkoutMediaViewerViewModel(context.applicationContext as android.app.Application, mediaId) as T
            }
        },
    )
    val media by viewModel.media.collectAsState()
    val message by viewModel.message.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val poseOverlayEnabled by viewModel.poseOverlayEnabled.collectAsState()
    var caption by remember(media?.id, media?.caption) { mutableStateOf(media?.caption.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(media?.exerciseName ?: "Medio", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = viewModel.shareIntent() ?: return@IconButton
                            context.startActivity(android.content.Intent.createChooser(intent, "Compartir"))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                    IconButton(onClick = { viewModel.saveToGallery() }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar en galería")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar")
                    }
                },
            )
        },
    ) { padding ->
        val item = media
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("No se encontró el archivo.", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val file = File(item.filePath)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (item.kind == WorkoutMediaKind.VIDEO && file.isFile) {
                    WorkoutVideoPlayer(
                        file = file,
                        modifier = Modifier.fillMaxSize(),
                        poseOverlayEnabled = poseOverlayEnabled,
                        poseSidecarPath = item.poseTrackPath,
                    )
                } else {
                    AsyncImage(
                        model = file.takeIf { it.isFile },
                        contentDescription = item.caption ?: item.exerciseName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                }
                            },
                    )
                }
            }
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pie de foto") },
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.setCaption(caption) }) {
                    Text("Guardar pie")
                }
            }
        }
    }
    if (confirmDelete) {
        KpknAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar este medio?") },
            text = { Text("Se eliminará de la app. El archivo en la galería, si lo guardaste, no se toca.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}
