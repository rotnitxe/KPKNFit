package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet

/**
 * Hoja «Semanas» de la pestaña Semana: el índice de todas las semanas del programa. Dentro va el módulo «Fases» o la fila de semanas de un
 * programa simple, tal cual [content] los pone; al pie, «Ir a la semana actual» si [onGoToCurrent] existe. Sin estado: la pantalla decide qué
 * se muestra y cómo se cierra.
 */
@Composable
internal fun ProgramWeeksSheet(
    subtitle: String,
    onDismiss: () -> Unit,
    onGoToCurrent: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, top = 2.dp, end = 18.dp, bottom = 30.dp),
        ) {
            Text(text = "Semanas", style = SheetTitleStyle, color = WizardColors.text)
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = SheetSubtitleStyle, color = WizardColors.textMuted)
            Spacer(Modifier.height(12.dp))
            content()
            if (onGoToCurrent != null) {
                Spacer(Modifier.height(20.dp))
                ProgramLienzoPill(
                    text = "Ir a la semana actual",
                    onClick = onGoToCurrent,
                    modifier = Modifier.fillMaxWidth(),
                    filled = false,
                    small = true,
                )
            }
        }
    }
}

/** El título de la hoja (`.sheet h3`): Syne Bold 21 / 24 con −0,4 sp. */
private val SheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.4).sp,
)

/** El subtítulo de la hoja (`.sheet .sub`): Inter 13 / 19. */
private val SheetSubtitleStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)
