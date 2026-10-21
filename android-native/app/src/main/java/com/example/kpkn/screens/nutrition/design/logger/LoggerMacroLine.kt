package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.MacroColors

/**
 * Macros de una comida en una línea tabular (NUTRITION_V1 §3): un punto de 6 dp de su color de [MacroColors] delante de cada cifra
 * («P 32 g», «H 4 g», «G 12 g»). Si no caben, pasan a otra línea. Con [alignEnd], se alinean a la derecha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggerMacroLine(
    protein: Int,
    carbs: Int,
    fats: Int,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LoggerMacroItem(color = MacroColors.protein, text = "P $protein g")
        LoggerMacroItem(color = MacroColors.carbs, text = "H $carbs g")
        LoggerMacroItem(color = MacroColors.fat, text = "G $fats g")
    }
}

@Composable
private fun LoggerMacroItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
        )
    }
}
