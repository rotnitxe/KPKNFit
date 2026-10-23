package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogAxisLabel
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogVariantTagLabel
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Solo valores materializados y compatibles con el implemento que se está mostrando. */
internal data class CatalogInlineAxis(
    val id: String,
    val values: List<String>,
    val selectedValue: String?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogInlineVariants(
    definitionId: String,
    axes: List<CatalogInlineAxis>,
    onSelect: (String, String) -> Unit,
    onOpenFicha: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 60.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        axes.forEach { axis ->
            Column(Modifier.testTag("catalog_inline_${axis.id}")) {
                if (axis.id != "implement") {
                    Text(
                        text = exerciseCatalogAxisLabel(axis.id, definitionId),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    axis.values.forEach { value ->
                        val label = exerciseCatalogVariantTagLabel(value, definitionId)
                        if (axis.id == "pulley_height") {
                            PulleyHeightOption(
                                value = value,
                                label = label,
                                selected = value == axis.selectedValue,
                                onClick = { onSelect(axis.id, value) },
                            )
                        } else {
                            KpknChip(
                                text = label,
                                selected = value == axis.selectedValue,
                                onClick = { onSelect(axis.id, value) },
                            )
                        }
                    }
                }
            }
        }
        KpknTextAction(text = "Ver ficha", onClick = onOpenFicha)
    }
}

/** Raíl y punto a la altura real; cada opción conserva al menos 48 dp de objetivo táctil. */
@Composable
private fun PulleyHeightOption(value: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val ink = if (selected) WizardColors.ctaContent else WizardColors.text
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Polea $label" }
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() })
            .padding(vertical = 4.dp)
            .background(if (selected) WizardColors.text else KpknSurface.inset, RoundedCornerShape(12.dp))
            .border(1.dp, WizardColors.divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 12.dp, height = 24.dp)) {
            val x = size.width / 2
            drawLine(ink.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            val position = when (value) { "high" -> 0.15f; "low" -> 0.85f; else -> 0.5f }
            drawCircle(ink, radius = 3.dp.toPx(), center = Offset(x, size.height * position))
        }
        Text(label, style = KpknType.chip, color = ink)
    }
}
