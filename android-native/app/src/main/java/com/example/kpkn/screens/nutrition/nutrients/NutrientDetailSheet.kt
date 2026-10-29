package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetGroup
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import dev.chrisbanes.haze.HazeState

/** Cifra grande de la hoja de un nutriente: Inter SemiBold 44 con números tabulares (Syne queda solo para el total de la comida). */
private val BigStyle = LoggerText.number(44f).copy(lineHeight = 44.sp, letterSpacing = (-1.5).sp)

/**
 * La hoja de un nutriente (n-nutrient): eyebrow «Mineral · hoy», nombre, la cifra grande respecto a tu referencia con su barra, «De dónde
 * sale» (los mayores con barras relativas y «Otros alimentos (N)»), qué alimento no trae dato, «Referencia» (RDA/AI y límite superior con
 * su alcance, topes de guía) y la fuente con «No sustituye el consejo de un profesional». Los datos son los de `NutrientCoverage`.
 */
@Composable
fun NutrientDetailSheet(model: NutrientDetailModel, onDismiss: () -> Unit, hazeState: HazeState? = null) {
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, maxHeightFraction = 0.88f) {
        NutrientDetailContent(model)
    }
}

@Composable
fun NutrientDetailContent(model: NutrientDetailModel, modifier: Modifier = Modifier) {
    val hair = LoggerColors.hair
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 30.dp)
            .testTag("nutrient-detail-${model.key.name}"),
    ) {
        Text(model.eyebrow.uppercase(), style = LoggerText.eyebrow, color = LoggerColors.muted)
        Text(model.name, style = LoggerText.sheetTitle, color = LoggerColors.ink, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(model.bigValue, style = BigStyle, color = LoggerColors.ink, modifier = Modifier.alignByBaseline())
            Text(model.ofText, style = LoggerText.body, color = LoggerColors.muted, modifier = Modifier.weight(1f).alignByBaseline())
            model.percentText?.let {
                Text(it, style = LoggerText.number(18f), color = if (model.ceiling && model.over) LoggerColors.danger else LoggerColors.ink, modifier = Modifier.alignByBaseline())
            }
        }
        model.fill?.let { fill ->
            NutrientBar(fill, Modifier.padding(top = 10.dp), ceiling = model.ceiling, over = model.ceiling && model.over, height = 6.dp)
        }
        model.alert?.let { Text(it, style = LoggerText.cap, color = LoggerColors.danger, modifier = Modifier.padding(top = 8.dp)) }

        if (model.contributors.isNotEmpty()) {
            LoggerSheetGroup("De dónde sale")
            model.contributors.forEach { c ->
                Column(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawLine(hair, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
                        .padding(vertical = 10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.name, style = LoggerText.body.copy(fontWeight = FontWeight.Medium), color = LoggerColors.ink, modifier = Modifier.weight(1f).alignByBaseline())
                        Text(c.valueText, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.alignByBaseline())
                    }
                    NutrientBar(c.fraction, Modifier.padding(top = 8.dp), fillAlpha = if (c.estimated) 0.4f else 0.75f)
                }
            }
        }
        model.missingNote?.let { note ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Info, contentDescription = null, tint = LoggerColors.faint, modifier = Modifier.size(15.dp))
                Text(note, style = LoggerText.cap, color = LoggerColors.faint)
            }
        }
        if (model.references.isNotEmpty()) {
            LoggerSheetGroup("Referencia")
            model.references.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawLine(hair, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(label, style = LoggerText.cap, color = LoggerColors.muted, modifier = Modifier.weight(1f))
                    Text(value, style = LoggerText.number(13f), color = LoggerColors.ink)
                }
            }
        }
        model.notes.forEach { Text(it, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.padding(top = 10.dp)) }
        Text(model.sourceLine, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(4.dp))
    }
}
