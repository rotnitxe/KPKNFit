package com.example.kpkn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Content design tokens from the Session Assistant sheet.
 *
 * - Loose text → white
 * - Chips / buttons / inputs / filled controls → white surface + black label
 * - No gray control wells, no yellow/primary tab chrome
 */
object KpknSheetTokens {
    val Title = Color.White.copy(alpha = 0.90f)
    val TitleStrong = Color.White.copy(alpha = 0.92f)
    val Body = Color.White
    val Muted = Color.White.copy(alpha = 0.55f)
    val MutedSoft = Color.White.copy(alpha = 0.45f)
    val MutedStrong = Color.White.copy(alpha = 0.70f)

    /** Subtle glass panel behind groups (not a gray well). */
    val Panel = Color.White.copy(alpha = 0.06f)

    val ChipIdle = Color.White.copy(alpha = 0.78f)
    val ChipSelected = Color.White
    val ChipLabel = Color.Black
    val ControlFill = Color.White
    val ControlLabel = Color.Black
    val ControlLabelMuted = Color.Black.copy(alpha = 0.55f)
    val ControlPlaceholder = Color.Black.copy(alpha = 0.40f)
    val Handle = Color.White.copy(alpha = 0.35f)

    val ContentPaddingHorizontal = 20.dp
    val ContentPaddingTop = 14.dp
    val ContentPaddingBottom = 24.dp
    val SectionGap = 16.dp
    val PanelRadius = 16.dp
    val ControlRadius = 14.dp
    val ChipHeight = 32.dp

    val EyebrowSize = 14.sp
    val EyebrowTracking = 1.sp
}

@Composable
fun KpknSheetContentTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    // Surfaces stay dark-luminance so Material contentColorFor → white for loose text.
    // Controls themselves use explicit white fills via [kpknSheetWhiteFieldColors] / chips.
    val darkPanel = Color(0xFF1A1A1F)
    val darkPanelHigh = Color(0xFF24242A)
    val sheetScheme = base.copy(
        onSurface = KpknSheetTokens.Body,
        onSurfaceVariant = KpknSheetTokens.MutedStrong,
        onBackground = KpknSheetTokens.Body,
        background = Color(0xFF0E0E12),
        surface = darkPanel,
        surfaceVariant = darkPanelHigh,
        surfaceContainer = darkPanel,
        surfaceContainerHigh = darkPanelHigh,
        surfaceContainerHighest = darkPanelHigh,
        surfaceContainerLow = darkPanel,
        surfaceContainerLowest = Color(0xFF121218),
        outline = Color.White.copy(alpha = 0.22f),
        outlineVariant = Color.White.copy(alpha = 0.12f),
        inverseOnSurface = Color.Black,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onTertiary = Color.Black,
        secondaryContainer = KpknSheetTokens.ControlFill,
        onSecondaryContainer = KpknSheetTokens.ControlLabel,
        primaryContainer = KpknSheetTokens.ControlFill,
        onPrimaryContainer = KpknSheetTokens.ControlLabel,
        onErrorContainer = KpknSheetTokens.Body,
    )
    CompositionLocalProvider(
        LocalContentColor provides KpknSheetTokens.Body,
        // IMPORTANT: do NOT bake a color into LocalTextStyle.
        // Text() prefers style.color over LocalContentColor; a white style would make
        // labels inside white buttons invisible (e.g. delete-group alerts).
        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
    ) {
        MaterialTheme(
            colorScheme = sheetScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/** Assistant-style light chip / tab (white well, black label — never primary/yellow). */
@Composable
fun KpknSheetLightChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(KpknSheetTokens.ChipHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) KpknSheetTokens.ChipSelected else KpknSheetTokens.ChipIdle)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = KpknSheetTokens.ChipLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Full-width white CTA used inside sheets (black label). */
@Composable
fun KpknSheetWhiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KpknSheetTokens.ControlFill,
            contentColor = KpknSheetTokens.ControlLabel,
            disabledContainerColor = KpknSheetTokens.ChipIdle,
            disabledContentColor = KpknSheetTokens.ControlLabelMuted,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
fun KpknSheetEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontWeight = FontWeight.Black,
        fontSize = KpknSheetTokens.EyebrowSize,
        letterSpacing = KpknSheetTokens.EyebrowTracking,
        color = KpknSheetTokens.Title,
    )
}

val KpknSheetPanelShape = RoundedCornerShape(KpknSheetTokens.PanelRadius)

/** White filled fields with black text (inputs inside glass sheets). */
@Composable
fun kpknSheetWhiteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = KpknSheetTokens.ControlFill,
    unfocusedContainerColor = KpknSheetTokens.ControlFill,
    disabledContainerColor = KpknSheetTokens.ChipIdle,
    focusedTextColor = KpknSheetTokens.ControlLabel,
    unfocusedTextColor = KpknSheetTokens.ControlLabel,
    disabledTextColor = KpknSheetTokens.ControlLabel,
    focusedLabelColor = KpknSheetTokens.ControlLabelMuted,
    unfocusedLabelColor = KpknSheetTokens.ControlLabelMuted,
    disabledLabelColor = KpknSheetTokens.ControlLabelMuted,
    cursorColor = KpknSheetTokens.ControlLabel,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedPlaceholderColor = KpknSheetTokens.ControlPlaceholder,
    unfocusedPlaceholderColor = KpknSheetTokens.ControlPlaceholder,
)

/** @deprecated Use [kpknSheetWhiteFieldColors]. */
@Composable
fun kpknSheetTextFieldColors() = kpknSheetWhiteFieldColors()

@Composable
fun kpknSheetWhiteTonalButtonColors() = ButtonDefaults.filledTonalButtonColors(
    containerColor = KpknSheetTokens.ControlFill,
    contentColor = KpknSheetTokens.ControlLabel,
    disabledContainerColor = KpknSheetTokens.ChipIdle,
    disabledContentColor = KpknSheetTokens.ControlLabelMuted,
)

@Composable
fun kpknSheetWhiteFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = KpknSheetTokens.ChipIdle,
    selectedContainerColor = KpknSheetTokens.ChipSelected,
    labelColor = KpknSheetTokens.ChipLabel,
    selectedLabelColor = KpknSheetTokens.ChipLabel,
    selectedLeadingIconColor = KpknSheetTokens.ChipLabel,
    iconColor = KpknSheetTokens.ChipLabel,
)

/** TextButton on glass (Cancelar): white label, no fill. */
@Composable
fun kpknSheetDialogTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = KpknSheetTokens.Body,
    disabledContentColor = KpknSheetTokens.Muted,
)

/** OutlinedButton on glass: white stroke + white label. */
@Composable
fun kpknSheetDialogOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = KpknSheetTokens.Body,
    disabledContentColor = KpknSheetTokens.Muted,
)
