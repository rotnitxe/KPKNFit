package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface

/**
 * Cabecera de semana: conserva navegación solo cuando hay varias semanas reales. La rutina semanal repetitiva se presenta sin contador,
 * selector ni flechas, y mantiene sus cifras de sesiones, series y tiempo.
 */
@Composable
internal fun ProgramWeekHeader(
    title: String,
    subtitle: String?,
    phaseColor: Color?,
    stats: ProgramWeekStats?,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenWeeks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
) {
    val titleInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpknShape.module)
            .background(KpknSurface.module, KpknShape.module)
            .border(1.dp, KpknSurface.moduleBorder, KpknShape.module)
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showNavigation) {
                ProgramRoundButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Semana anterior",
                    onClick = onPrev,
                    enabled = canPrev,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("week-title")
                    .then(
                        onOpenWeeks?.let { open ->
                            Modifier.clickable(
                                interactionSource = titleInteraction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Ver todas las semanas",
                                onClick = open,
                            )
                        } ?: Modifier,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = WeekTitleStyle,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (phaseColor != null) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(phaseColor, CircleShape))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = subtitle,
                            style = WeekSubtitleStyle,
                            color = WizardColors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showNavigation) {
                ProgramRoundButton(
                    icon = KpknLineIcons.ChevronRight,
                    contentDescription = "Semana siguiente",
                    onClick = onNext,
                    enabled = canNext,
                )
            }
        }
        if (stats != null) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                ProgramWeekStatCell(value = stats.sessionsLabel, label = "sesiones", modifier = Modifier.weight(1f))
                ProgramWeekStatCell(value = stats.setsLabel, label = "series", modifier = Modifier.weight(1f))
                ProgramWeekStatCell(value = stats.minutesLabel, label = "en total", modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Una cifra del navegador: el valor en Syne con cifras tabulares, y debajo su etiqueta en `textFaint`. */
@Composable
private fun ProgramWeekStatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = WeekStatValueStyle,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = WeekStatLabelStyle,
            color = WizardColors.textFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** El título de la semana: Syne Bold 20 / 22, con −0,4 sp de espaciado. */
private val WeekTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.4).sp,
)

/** La fase y el rango de fechas, bajo el título: Inter 12 / 16. */
private val WeekSubtitleStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** El valor de cada cifra: Syne Bold 17 / 20, con cifras tabulares. */
private val WeekStatValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** La etiqueta de cada cifra: Inter 11 / 14. */
private val WeekStatLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)
