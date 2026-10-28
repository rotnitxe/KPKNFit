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
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

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
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(text = "Semanas", style = SheetTitleStyle, color = WizardColors.text)
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, style = KpknType.body, color = WizardColors.textMuted)
            Spacer(Modifier.height(16.dp))
            content()
            if (onGoToCurrent != null) {
                Spacer(Modifier.height(18.dp))
                KpknSecondaryPill(
                    text = "Ir a la semana actual",
                    onClick = onGoToCurrent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** El título de la hoja: Syne Bold 22 / 26. */
private val SheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
)
