package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.ui.theme.MacroColors
import java.time.LocalDate

/** Objetivo de un día del reparto semanal, tal como lo dibuja la franja de días. */
@Immutable
data class WizardNutritionDay(
    val date: LocalDate,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val isToday: Boolean = false,
) {
    val values: PlanValues get() = PlanValues(kcal, proteinG, carbsG, fatG)
}

private val DayChipShape = RoundedCornerShape(10.dp)

/**
 * Franja compacta de los días del reparto semanal: la inicial (L M X J V S D) y las kcal en pequeño. Tocar
 * un día lleva los anillos a los objetivos de ese día; tocarlo otra vez vuelve a la media. Solo tiene sentido
 * con reparto variable: con uniforme no se compone.
 *
 * [enabled] a false (mientras se arrastra un deslizador) la apaga: los días se recalculan al soltar.
 */
@Composable
fun WizardDayStrip(
    days: List<WizardNutritionDay>,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEachIndexed { index, day ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(
                targetValue = if (selected) MacroColors.calories.copy(alpha = 0.18f) else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "day-fill",
            )
            val dayName = weekdayNameEs(day.date.dayOfWeek)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = WizardSpacing.touchTarget + 4.dp)
                    .testTag("setup-nutrition-day-$index")
                    .clip(DayChipShape)
                    .background(fill)
                    .border(
                        width = 1.dp,
                        color = if (selected) MacroColors.calories.copy(alpha = 0.7f) else WizardColors.divider,
                        shape = DayChipShape,
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClickLabel = if (selected) "Volver a la media" else "Ver $dayName",
                        onClick = { onSelect(if (selected) null else index) },
                    )
                    .semantics {
                        this.selected = selected
                        contentDescription = "$dayName, ${formatKcalEs(day.kcal)} kilocalorías"
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(
                    modifier = Modifier.clearAndSetSemantics { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = weekdayInitialEs(day.date.dayOfWeek),
                        style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) WizardColors.text else WizardColors.textMuted,
                        maxLines = 1,
                    )
                    Text(
                        text = formatKcalEs(day.kcal),
                        style = WizardTypography.note,
                        color = if (selected) WizardColors.text else WizardColors.textFaint,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (day.isToday) {
                        Box(Modifier.size(4.dp).background(MacroColors.calories, CircleShape))
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
