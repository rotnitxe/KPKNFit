package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionCardioDose
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Plantillas de intervalos del tipo de cardio (SESSION_EDITOR_V1 §6): un módulo tocable entero por plantilla, con su nivel y sus dos
 * píldoras de resumen (los bloques por rondas y la duración). Elegir una la aplica con [onSelect]. Vidrio KPKN en el diálogo.
 */
@Composable
internal fun HiitTemplatePickerDialog(
    cardioType: CardioType,
    accentColor: Color,
    title: String = "Plantillas HIIT",
    onSelect: (HiitTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates = CardioHiitTemplates.forType(cardioType)
    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = KpknType.dialogTitle, color = WizardColors.text)
            Text(
                text = "Elige un circuito listo para usar. Todo queda editable después.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(templates) { tmpl ->
                    val totalSec = (if (tmpl.warmupSeconds > 0) tmpl.warmupSeconds else 0) +
                        tmpl.blocks.sumOf { it.durationSeconds } * tmpl.rounds.coerceAtLeast(1) +
                        (if (tmpl.cooldownSeconds > 0) tmpl.cooldownSeconds else 0)
                    KpknModule(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(tmpl) },
                        onClickLabel = "Elegir ${tmpl.name}",
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tmpl.name,
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            InfoPill(text = tmpl.level)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tmpl.description,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill(text = "${tmpl.blocks.size} bloques × ${tmpl.rounds} rondas")
                            InfoPill(text = SessionCardioDose.minutesText(totalSec))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KpknTextAction(text = "Cerrar", onClick = onDismiss)
            }
        }
    }
}

/** Pastilla informativa, no tocable: texto en `caption` sobre el relleno `inset`. */
@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(KpknSurface.inset)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = KpknType.caption,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
