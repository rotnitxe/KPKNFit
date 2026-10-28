package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.DiscomfortKnowledge
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/*
 * La ficha de una molestia (LV6, B2, F7): zona, título, la animación curada de su sección, qué es, «CAUSAS COMUNES», «EN TU CASO» (lo que
 * el motor ve hoy y esta semana, solo si hay datos), «QUÉ PUEDES HACER» (siempre con el cierre de consultar a un profesional) y «Reportar
 * molestia». No hay selector de gravedad: la app no la mide. Tocar el botón añade la molestia al feedback del ejercicio, igual que el chip.
 */

private val EyebrowStyle = KpknType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.6.sp)

/** La ficha como hoja de la app (arrastrar o atrás la cierran). El cuerpo es [DiscomfortSheetBody]. */
@Composable
internal fun DiscomfortSheet(
    knowledge: DiscomfortKnowledge,
    selected: Boolean,
    insight: DiscomfortInsight?,
    onToggleReport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, onDismissRequest = onDismiss, modifier = modifier, maxHeightFraction = 0.96f) {
        DiscomfortSheetBody(
            knowledge = knowledge,
            selected = selected,
            insight = insight,
            onToggleReport = onToggleReport,
            animated = animated,
        )
    }
}

/**
 * El contenido de la ficha sin la hoja que lo envuelve: así se pinta también suelto (hojas de render, pruebas). [insight] es lo que el
 * motor dice de esta molestia hoy y esta semana; null (o sin datos) y «EN TU CASO» no sale.
 */
@Composable
internal fun DiscomfortSheetBody(
    knowledge: DiscomfortKnowledge,
    selected: Boolean,
    insight: DiscomfortInsight?,
    onToggleReport: () -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val tint = discomfortZoneColor(knowledge.animation)
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DiscomfortScene(
                    section = knowledge.animation,
                    modifier = Modifier.size(width = 110.dp, height = 120.dp),
                    animated = animated,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = knowledge.zoneLabel,
                        style = KpknType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.4.sp),
                        color = tint,
                        maxLines = 1,
                    )
                    Text(
                        text = knowledge.title,
                        style = KpknType.title.copy(fontSize = 20.sp, lineHeight = 25.sp),
                        color = WizardColors.text,
                    )
                }
            }

            Text(
                text = knowledge.explanation,
                style = KpknType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = WizardColors.text.copy(alpha = 0.8f),
            )

            SheetSection(title = "CAUSAS COMUNES") {
                knowledge.commonCauses.forEach { cause ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "●", style = KpknType.caption, color = SymbolPalette.musculo)
                        Text(
                            text = cause,
                            style = KpknType.caption.copy(lineHeight = 18.sp),
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            val recorded = (insight?.today.orEmpty() + insight?.week.orEmpty()).filter {
                it.kind == com.example.kpkn.domain.discomfort.DiscomfortEvidenceKind.REPORTED_HISTORY
            }
            if (recorded.isNotEmpty()) {
                SheetSection(title = "REGISTROS ANTERIORES") {
                    recorded.forEach { report ->
                        Text(report.text, style = KpknType.caption, color = WizardColors.text)
                    }
                }
            }

            SheetSection(title = "QUÉ PUEDES HACER") {
                Text(
                    text = knowledge.whatYouCanDo,
                    style = KpknType.caption.copy(lineHeight = 19.sp),
                    color = WizardColors.text.copy(alpha = 0.85f),
                )
                Text(
                    text = PROFESSIONAL_ADVICE,
                    style = KpknType.caption.copy(lineHeight = 19.sp),
                    color = WizardColors.text.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Box(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp)) {
            if (selected) {
                KpknSecondaryPill(
                    text = "Quitar molestia",
                    onClick = onToggleReport,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                KpknPrimaryPill(
                    text = "Reportar molestia",
                    onClick = onToggleReport,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = EyebrowStyle, color = WizardColors.textMuted)
        content()
    }
}

/** Una celda de «EN TU CASO»: «Hoy» o «Esta semana» y la frase del motor, con la cifra del principio en color. */
@Composable
private fun CaseCell(label: String, text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = KpknType.caption.copy(fontSize = 12.sp), color = WizardColors.textMuted)
        Text(
            text = emphasizeLeadingFigure(text, SymbolPalette.musculo),
            style = KpknType.caption.copy(lineHeight = 18.sp),
            color = WizardColors.text,
        )
    }
}

/** «+35 % de series…»: la cifra con su signo y su «%» del principio va en el color de acento y en seminegrita. El resto, tal cual. */
internal fun emphasizeLeadingFigure(text: String, color: Color): AnnotatedString {
    val match = LeadingFigure.find(text) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(match.value) }
        append(text.substring(match.range.last + 1))
    }
}

private val LeadingFigure = Regex("""^\+[\d.,]+[\u00A0 ]%""")
