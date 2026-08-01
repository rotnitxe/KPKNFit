package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.exercises.ExerciseDisplayParts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseNameWithChips(
    parts: ExerciseDisplayParts,
    modifier: Modifier = Modifier,
    showChips: Boolean = true,
) {
    if (!showChips || parts.chips.isEmpty()) {
        Text(
            text = parts.parentName,
            modifier = modifier,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = parts.parentName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        parts.chips.forEach { chip ->
            AssistChip(
                onClick = {},
                label = { Text(chip, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}
