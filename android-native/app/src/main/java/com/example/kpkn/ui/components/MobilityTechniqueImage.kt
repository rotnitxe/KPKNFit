package com.example.kpkn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.ReviewedExerciseTechniqueImages

/** Only exact persisted mobility IDs may select reviewed art. */
@Composable
fun MobilityTechniqueImage(
    movementId: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val image = ReviewedExerciseTechniqueImages.mobilityImage(movementId) ?: return
    AsyncImage(
        model = image,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
