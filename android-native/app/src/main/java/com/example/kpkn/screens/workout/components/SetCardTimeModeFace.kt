package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

/** Full-card timer face shown when a time-mode set is actively running. */
@Composable
internal fun SetCardTimeModeFace(
    running: Boolean,
    elapsedSeconds: Int,
    remainingSeconds: Int,
    plannedTargetSeconds: Int?,
    loadSummary: String?,
    sessionAccentColor: Color,
    enabled: Boolean,
    formatTime: (Int) -> String,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onBackToSpecs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = plannedTargetSeconds?.takeIf { it > 0 }
        ?.let { (elapsedSeconds.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
    val showingRemaining = running && plannedTargetSeconds != null && plannedTargetSeconds > 0
    val shownSeconds = if (showingRemaining) remainingSeconds else elapsedSeconds

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("live-card-set-clock-face"),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when {
                    showingRemaining -> "RESTAN"
                    running -> "TIEMPO EN CURSO"
                    else -> "EN PAUSA"
                },
                style = TextStyle(
                    fontFamily = WizardFonts.body,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                ),
                color = if (running) sessionAccentColor else Color(0xFFA0A0A0),
            )
            loadSummary?.takeIf { it.isNotBlank() }?.let { load ->
                Text(
                    text = load,
                    style = KpknType.caption.copy(fontSize = 12.sp),
                    color = WizardColors.textMuted,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatTime(shownSeconds),
                modifier = Modifier.testTag("live-card-set-clock-value"),
                style = TextStyle(
                    fontFamily = WizardFonts.display,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp,
                    fontFeatureSettings = "'tnum' 1, 'lnum' 1",
                ),
                color = if (running) WizardColors.text else WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
            plannedTargetSeconds?.takeIf { it > 0 }?.let { target ->
                Text(
                    text = "Objetivo · ${formatTime(target)} · Transcurrido ${formatTime(elapsedSeconds)}",
                    style = KpknType.caption.copy(fontSize = 12.sp),
                    color = Color(0xFFA0A0A0),
                    textAlign = TextAlign.Center,
                )
            }
            if (progress != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF282828)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(sessionAccentColor),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF222222))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onTogglePause)
                        .semantics {
                            contentDescription = if (running) "Pausar el cronómetro de la serie" else "Reanudar el cronómetro de la serie"
                        }
                        .testTag("live-card-set-clock-toggle")
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (running) KpknLineIcons.Pause else KpknLineIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WizardColors.text,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (running) "Pausar" else "Reanudar",
                        style = KpknType.action.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = WizardColors.text,
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WizardColors.text)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onFinish)
                        .semantics { contentDescription = "Terminar serie por tiempo y editar registro" }
                        .testTag("live-card-set-clock-finish")
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        KpknLineIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF111111),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Terminar",
                        style = KpknType.action.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF111111),
                    )
                }
            }
            Text(
                text = "Volver a especificaciones",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onBackToSpecs)
                    .testTag("live-card-set-clock-back")
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = KpknType.caption.copy(fontSize = 12.sp),
                color = WizardColors.textMuted,
            )
        }
    }
}
