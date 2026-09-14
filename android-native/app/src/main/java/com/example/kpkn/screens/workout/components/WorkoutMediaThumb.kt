package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.io.File

@Composable
fun WorkoutMediaThumb(
    media: WorkoutMedia,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val preview = mediaPreviewFile(media)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WorkoutUiTokens.setInnerHighestColor())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = media.exerciseName ?: media.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        if (media.kind == WorkoutMediaKind.VIDEO) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Vídeo",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp),
            )
            WorkoutAlbumGrouping.formatDurationMs(media.durationMs)?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (media.isPr) {
            Icon(
                Icons.Default.Star,
                contentDescription = "PR",
                tint = Color(0xFFFFC107),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp),
            )
        }
    }
}

internal fun mediaPreviewFile(media: WorkoutMedia): File? {
    val thumb = media.thumbPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    if (thumb != null) return thumb
    if (media.kind == WorkoutMediaKind.PHOTO) {
        return File(media.filePath).takeIf { it.isFile }
    }
    return null
}
