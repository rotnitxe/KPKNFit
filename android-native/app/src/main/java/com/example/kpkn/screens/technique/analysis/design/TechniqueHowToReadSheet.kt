package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja explicativa «Cómo leer este análisis» (`.tc-help` / `"analysis-help-sheet"`):
 * explica la escala de colores de la trayectoria, la distinción m/s vs velocidad relativa y la regla «≈» / «—».
 */
@Composable
fun TechniqueHowToReadSheet(
    onDismiss: () -> Unit,
) {
    LiveSheet(
        title = "Cómo leer este análisis",
        eyebrow = "Guía visual",
        onDismiss = onDismiss,
        tag = "analysis-help-sheet",
    ) {
        LiveSheetSubtitle(
            "El análisis sale de tu vídeo: la pose y la barra se estiman fotograma a fotograma sin inventar datos.",
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x0DFFFFFF))
                    .padding(14.dp),
            ) {
                Text(
                    text = "Trayectoria de barra",
                    style = KpknType.bodyStrong.copy(fontSize = 13.sp),
                    color = WizardColors.text,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lenta", style = KpknType.caption.copy(fontSize = 11.sp), color = WizardColors.textMuted)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(SymbolPalette.columna, SymbolPalette.energia, Color(0xFFF2EEE6)),
                                ),
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Rápida", style = KpknType.caption.copy(fontSize = 11.sp), color = WizardColors.textMuted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "El degradado refleja la velocidad instantánea dentro de cada repetición; en las parciales la banda usa rayas diagonales.",
                    style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = WizardColors.textMuted,
                )
            }
            HowToReadNote(
                title = "Velocidad real vs relativa",
                body = "Sin escala física validada con un disco calibrado en pantalla, la velocidad se muestra en % respecto a la mejor repetición y nunca se inventan m/s.",
            )
            HowToReadNote(
                title = "Unidades, estimaciones y revisiones",
                body = "Un dato que no pudo medirse queda como «—»; las cifras estimadas llevan «≈». Puedes corregir completas o parciales sin borrar lo observado.",
            )
        }
    }
}

@Composable
private fun HowToReadNote(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color(0x2DFFFFFF),
                    start = Offset(1.dp.toPx(), 0f),
                    end = Offset(1.dp.toPx(), size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(text = title, style = KpknType.bodyStrong.copy(fontSize = 13.sp), color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp), color = WizardColors.textMuted)
    }
}
