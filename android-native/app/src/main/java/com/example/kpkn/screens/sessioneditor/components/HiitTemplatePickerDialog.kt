package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitTemplate

@Composable
internal fun HiitTemplatePickerDialog(
    cardioType: CardioType,
    accentColor: Color,
    onSelect: (HiitTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates = CardioHiitTemplates.forType(cardioType)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF1A1A1A), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Plantillas HIIT", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Elige un circuito listo para usar. Todo queda editable después.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(templates) { tmpl ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(tmpl) },
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(tmpl.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(999.dp), color = accentColor.copy(alpha = 0.22f)) {
                                        Text(tmpl.level, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                                Text(tmpl.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.70f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChipSmall("${tmpl.blocks.size} bloques × ${tmpl.rounds} rondas", accentColor)
                                    val totalSec = (if (tmpl.warmupSeconds > 0) tmpl.warmupSeconds else 0) + tmpl.blocks.sumOf { it.durationSeconds } * tmpl.rounds.coerceAtLeast(1) + (if (tmpl.cooldownSeconds > 0) tmpl.cooldownSeconds else 0)
                                    ChipSmall(formatTemplateDuration(totalSec), accentColor)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White.copy(alpha = 0.85f)) }
                }
            }
        }
    }
}

@Composable
private fun ChipSmall(text: String, accentColor: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.08f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
    }
}

private fun formatTemplateDuration(sec: Int): String {
    val m = sec / 60
    return if (m < 60) "${m} min" else "${m / 60}h ${m % 60}m"
}
