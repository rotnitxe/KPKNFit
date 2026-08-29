package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.contentOn

@Composable
internal fun WorkoutRecordFab(
    sessionAccentColor: Color,
    isUpdateMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (enabled) sessionAccentColor else sessionAccentColor.copy(alpha = 0.38f)
    val onFill = contentOn(fill)
    val label = if (isUpdateMode) "Actualizar serie" else "Registrar serie"
    Box(
        modifier = modifier
            .shadow(10.dp, CircleShape, clip = false)
            .size(58.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isUpdateMode) Icons.Default.Update else Icons.Default.Check,
            contentDescription = label,
            tint = if (enabled) onFill else onFill.copy(alpha = 0.72f),
            modifier = Modifier.size(26.dp),
        )
    }
}
