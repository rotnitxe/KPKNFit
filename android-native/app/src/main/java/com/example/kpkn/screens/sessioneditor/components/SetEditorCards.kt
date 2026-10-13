package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.kpkn.screens.sessioneditor.toSetCardBackground

enum class SetCardDensity { Comfortable, Compact, SupersetCompact }
enum class SetSideMode { BILATERAL, LEFT, RIGHT }

data class SetEditorCardState(
    val setId: String,
    val setNumber: Int,
    val mode: TrainingMode,
    val isUnilateral: Boolean = false,
    val unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    val sideMode: SetSideMode = SetSideMode.BILATERAL,
    val metricLabel: String,
    val metricValue: String,
    val intensityLabel: String? = null,
    val intensityValue: String? = null,
    val loadModeLabel: String,
    val estimatedLoadLabel: String? = null,
    val percentRmLabel: String? = null,
    val isAmrap: Boolean = false,
    val isRmMode: Boolean = false,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val showActions: Boolean = true,
    val isSuggested: Boolean = false,
    val isAutoCalculated: Boolean = false,
    val isValid: Boolean = true,
    val validationError: String? = null,
)

sealed class SetEditorAction {
    data class UpdateMetric(val value: String) : SetEditorAction()
    data class UpdateIntensity(val value: String) : SetEditorAction()
    data class UpdateWeight(val value: String) : SetEditorAction()
    data class UpdatePercentageRM(val pct: Double) : SetEditorAction()
    data class UpdateLoadMode(val mode: LoadModeV2) : SetEditorAction()
    data class UpdateIntensityMode(val mode: IntensityMode) : SetEditorAction()
    object Duplicate : SetEditorAction()
    object MoveUp : SetEditorAction()
    object MoveDown : SetEditorAction()
    object Remove : SetEditorAction()
    object RemoveSide : SetEditorAction()
    data class SelectSide(val side: String) : SetEditorAction()
    object ToggleLink : SetEditorAction()
}

object KpknSetEditorTokens {
    val CardShape = RoundedCornerShape(24.dp)
    val MiniFieldShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(999.dp)
    val CardPadding = PaddingValues(8.dp)
    val FieldGap = 6.dp
}

@Composable
fun SetEditorCard(
    accentColor: Color,
    backgroundAccentColor: Color = accentColor,
    isFocused: Boolean = false,
    isValid: Boolean = true,
    density: SetCardDensity = SetCardDensity.Comfortable,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderAlpha = if (isFocused) 0.42f else if (!isValid) 0.55f else 0.10f
    val borderColor = if (!isValid) MaterialTheme.colorScheme.error else accentColor
    val borderWidth = if (isFocused || !isValid) 1.5.dp else 1.dp
    val cardBg = backgroundAccentColor.toSetCardBackground()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KpknSetEditorTokens.CardShape,
        color = cardBg,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(borderWidth, borderColor.copy(alpha = borderAlpha))
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier.padding(
                    if (density == SetCardDensity.SupersetCompact) PaddingValues(8.dp) else KpknSetEditorTokens.CardPadding
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (density == SetCardDensity.SupersetCompact) 4.dp else KpknSetEditorTokens.FieldGap
                ),
                content = content
            )
        }
    }
}
