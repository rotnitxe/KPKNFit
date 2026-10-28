package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts

/** Título del aviso: Inter 600 13,5, como el de una fila de diagnóstico del lienzo. */
private val NoticeTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)

/**
 * Aviso «Revisa la estructura temporal» en un `.mod`: el punto de 8 dp en `--danger` y, a su lado (10 dp), el título y hasta tres mensajes
 * del motor en `.cap`. Sin [messages] no se pinta nada: la pantalla lo llama solo si hay avisos.
 */
@Composable
internal fun ProgramStructureNotice(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier) {
        Row {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(WizardColors.danger, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = "Revisa la estructura temporal", style = NoticeTitleStyle, color = StructureInk)
                messages.take(3).forEach { message ->
                    Text(text = message, style = StructureCaptionStyle, color = StructureFaint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
