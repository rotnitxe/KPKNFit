package com.example.kpkn.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.kpkn.domain.exercises.ExerciseDisplayParts

@Composable
fun ExerciseNameWithChips(
    parts: ExerciseDisplayParts,
    modifier: Modifier = Modifier,
    showChips: Boolean = true,
    chipsColor: SpanStyle = SpanStyle(),
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
    Text(
        text = buildAnnotatedString {
            append(parts.parentName)
            parts.chips.forEach { chip ->
                append(" · ")
                withStyle(chipsColor) { append(chip) }
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
