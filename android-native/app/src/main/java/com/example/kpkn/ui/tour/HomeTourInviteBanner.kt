package com.example.kpkn.ui.tour

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

private val InviteShape = RoundedCornerShape(22.dp)
private val InviteFallbackBg = Color(0xFF151515).copy(alpha = 0.94f)

/**
 * Invitación flotante glass no bloqueante para iniciar el recorrido de Inicio (Instancia 0).
 * Sustituye al bloqueo inmediato de `LaunchedEffect(Unit)` en el primer frame.
 */
@Composable
fun HomeTourInviteBanner(
    onStart: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = Modifier.clip(InviteShape).background(InviteFallbackBg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(surface)
            .border(1.dp, SymbolPalette.musculo.copy(alpha = 0.45f), InviteShape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("tour_invite_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "¿Recorrido rápido de 30 s?",
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Conoce Inicio sin bloquear tu pantalla.",
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.Normal,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = WizardColors.textMuted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .testTag("tour_invite_later")
                    .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = 36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onLater)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Luego",
                    fontFamily = WizardFonts.body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = WizardColors.textMuted,
                )
            }
            Box(
                modifier = Modifier
                    .testTag("tour_invite_start")
                    .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = 36.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text)
                    .clickable(role = Role.Button, onClick = onStart)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Empezar",
                    fontFamily = WizardFonts.body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF0B0B0B),
                )
            }
        }
    }
}
