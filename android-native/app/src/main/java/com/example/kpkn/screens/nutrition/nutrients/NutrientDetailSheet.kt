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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/** Título de las hojas (`.sheet h3`): Syne Bold 21. */
internal val NutrientSheetTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp,
    letterSpacing = (-0.4).sp)
/** Cifra grande (`.big`): Syne ExtraBold 44 con números tabulares. */
private val BigStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp,
    letterSpacing = (-2).sp, fontFeatureSettings = "'lnum' 1, 'tnum' 1")
private val PercentStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1")

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
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 30.dp)
            .testTag("nutrient-detail-${model.key.name}"),
    ) {
        Text(model.eyebrow.uppercase(), style = KpknType.eyebrow, color = WizardColors.textMuted)
        Text(model.name, style = NutrientSheetTitleStyle, color = WizardColors.text, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(model.bigValue, style = BigStyle, color = WizardColors.text, modifier = Modifier.alignByBaseline())
            Text(model.ofText, style = KpknType.body, color = WizardColors.textMuted, modifier = Modifier.weight(1f).alignByBaseline())
            model.percentText?.let {
                Text(it, style = PercentStyle, color = if (model.ceiling && model.over) WizardColors.danger else WizardColors.text, modifier = Modifier.alignByBaseline())
            }
        }
        model.fill?.let { fill ->
            NutrientBar(fill, 0f, Modifier.padding(top = 10.dp), ceiling = model.ceiling, overCeiling = model.ceiling && model.over,
                arrow = model.over && !model.ceiling, height = 8.dp)
        }
        model.alert?.let { Text(it, style = KpknType.caption, color = WizardColors.danger, modifier = Modifier.padding(top = 8.dp)) }

        if (model.contributors.isNotEmpty()) {
            NutrientGroupLabel("De dónde sale")
            model.contributors.forEach { c ->
                Column(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawLine(KpknSurface.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                        .padding(top = 10.dp, bottom = 11.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.name, style = KpknType.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                            color = WizardColors.text, modifier = Modifier.weight(1f).alignByBaseline())
                        Text(c.valueText, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.alignByBaseline())
                    }
                    NutrientBar(c.fraction, 0f, Modifier.padding(top = 7.dp), track = false, fillAlpha = 0.55f, hatchedFill = c.estimated, mark = false)
                }
            }
        }
        model.missingNote?.let { note ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Info, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(14.dp))
                Text(note, style = KpknType.caption, color = WizardColors.textFaint)
            }
        }
        if (model.references.isNotEmpty()) {
            NutrientGroupLabel("Referencia")
            model.references.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawLine(KpknSurface.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(label, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                    Text(value, style = KpknType.caption.copy(fontWeight = FontWeight.Bold), color = WizardColors.text)
                }
            }
        }
        model.notes.forEach { Text(it, style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.padding(top = 10.dp)) }
        Text(model.sourceLine, style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(4.dp))
    }
}
