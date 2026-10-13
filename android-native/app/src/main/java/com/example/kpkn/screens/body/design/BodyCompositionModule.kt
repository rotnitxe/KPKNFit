package com.example.kpkn.screens.body.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.roundBmi1
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Módulo «Composición» de Cuerpo (BODY_V1 §2.4): la barra de masa magra y grasa con sus kg y su %, el % de músculo, el FFMI y el IMC.
 * Cada cifra lleva su categoría y su hoja de explicación. Las cifras que salen del % de grasa van con «≈». Sin altura, la acción
 * «Añadir altura»; sin composición de la misma medición, no hay barra ni FFMI, y se dice por qué. Sin pesaje, no se pinta. Sin estado.
 *
 * @param weightKg peso del último pesaje, o null (sin pesaje no hay módulo).
 * @param weightUnit unidad de Ajustes: la masa magra y la grasa se muestran en kg o en lb (los datos siguen en kg).
 * @param leanKg masa magra de la misma medición que el % de grasa, o null.
 * @param fatKg masa grasa de esa misma medición, o null.
 * @param bodyFatPercent % de grasa de la composición, o null.
 * @param musclePercent % de músculo, o null.
 * @param ffmi FFMI normalizado: solo con altura y composición.
 * @param bmi IMC con el último pesaje: solo con altura.
 * @param hasHeight si el perfil tiene altura.
 * @param onInfo abre la hoja de explicación de la cifra indicada.
 */
@Composable
fun BodyCompositionModule(
    weightKg: Double?,
    weightUnit: WizardMassUnit,
    leanKg: Double?,
    fatKg: Double?,
    bodyFatPercent: Double?,
    musclePercent: Double?,
    ffmi: Double?,
    bmi: Double?,
    hasHeight: Boolean,
    onAddHeight: () -> Unit,
    onInfo: (BodyInfoTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weightKg == null) return
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(
            label = "Composición",
            action = {
                KpknIconButton(
                    icon = KpknLineIcons.Info,
                    contentDescription = "Cómo se calcula la composición",
                    onClick = { onInfo(BodyInfoTopic.COMPOSICION) },
                )
            },
        )
        if (leanKg != null && fatKg != null && bodyFatPercent != null) {
            val leanPercent = 100.0 - bodyFatPercent
            val spoken = if (weightUnit == WizardMassUnit.LB) "libras" else "kilos"
            Spacer(Modifier.height(10.dp))
            BodyCompositionBar(
                leanShare = (leanPercent / 100.0).toFloat(),
                description = "Masa magra ≈ ${bodyWeightValue(leanKg, weightUnit)} $spoken, ${formatDecimalEs(leanPercent, 1)} %. " +
                    "Masa grasa ≈ ${bodyWeightValue(fatKg, weightUnit)} $spoken, ${formatDecimalEs(bodyFatPercent, 1)} %.",
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                BodyCompositionLegend(
                    label = "Masa magra",
                    color = SymbolPalette.musculo,
                    weight = "≈ ${bodyWeightValue(leanKg, weightUnit)} ${weightUnit.code}",
                    percent = "${formatDecimalEs(leanPercent, 1)} %",
                    alignEnd = false,
                    modifier = Modifier.weight(1f),
                )
                BodyCompositionLegend(
                    label = "Masa grasa",
                    color = WizardColors.textFaint,
                    weight = "≈ ${bodyWeightValue(fatKg, weightUnit)} ${weightUnit.code}",
                    percent = "${formatDecimalEs(bodyFatPercent, 1)} %",
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        if (musclePercent != null) {
            BodyFigureRow(
                label = "Músculo",
                countKey = "musculo",
                target = musclePercent,
                unit = "%",
                approx = false,
                detail = null,
                infoDescription = "Cómo se mide el % de músculo",
                onInfo = { onInfo(BodyInfoTopic.MUSCULO) },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        }
        if (hasHeight && ffmi != null) {
            BodyFigureRow(
                label = "FFMI",
                countKey = "ffmi",
                target = ffmi,
                unit = "kg/m²",
                approx = true,
                detail = bodyFfmiCategory(ffmi),
                style = KpknType.metricM,
                infoDescription = "Cómo se calcula el FFMI",
                onInfo = { onInfo(BodyInfoTopic.FFMI) },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        }
        if (hasHeight && bmi != null) {
            BodyFigureRow(
                label = "IMC",
                countKey = "imc",
                target = roundBmi1(bmi),
                unit = "kg/m²",
                approx = false,
                detail = bodyBmiCategory(bmi),
                style = KpknType.metricM,
                infoDescription = "Cómo se calcula el IMC",
                onInfo = { onInfo(BodyInfoTopic.IMC) },
            )
        }
        if (!hasHeight) {
            BodyHeightPrompt(onAddHeight = onAddHeight)
        } else if (ffmi == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Registra peso y % de grasa en la misma medición para ver tu masa magra y tu FFMI.",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}

/** Una cifra del módulo: su nombre, el valor que cuenta hacia arriba con su unidad, la categoría y el botón de explicación. */
@Composable
private fun BodyFigureRow(
    label: String,
    countKey: String,
    target: Double,
    unit: String,
    approx: Boolean,
    detail: String?,
    infoDescription: String,
    onInfo: () -> Unit,
    /** Cifra de FFMI e IMC: `metricM` como pide la especificación; el % de músculo, `metricL`. */
    style: TextStyle = KpknType.metricL,
) {
    val shown = bodyCountedValue(countKey, target)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
            KpknMetric(
                value = (if (approx) "≈ " else "") + formatDecimalEs(shown, 1),
                unit = unit,
                style = style,
            )
            if (detail != null) {
                Text(text = detail, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        KpknIconButton(icon = KpknLineIcons.Info, contentDescription = infoDescription, onClick = onInfo)
    }
}

/**
 * Leyenda de un tramo de la barra: el color y el nombre; debajo, los kilos y el porcentaje, cada uno en su línea y en `caption` tabular.
 * Así una cifra nunca se parte de su unidad al crecer la letra. [alignEnd] lo alinea a la derecha.
 */
@Composable
private fun BodyCompositionLegend(
    label: String,
    color: Color,
    weight: String,
    percent: String,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val numbers = KpknType.caption.copy(fontFeatureSettings = "tnum")
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!alignEnd) {
                LegendDot(color = color)
                Spacer(Modifier.width(6.dp))
            }
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
            if (alignEnd) {
                Spacer(Modifier.width(6.dp))
                LegendDot(color = color)
            }
        }
        Text(text = weight, style = numbers, color = WizardColors.text)
        Text(text = percent, style = numbers, color = WizardColors.textMuted)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Sin altura: el aviso y la acción que lleva al perfil. Nunca se inventa una altura. */
@Composable
private fun BodyHeightPrompt(onAddHeight: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Añade tu altura para ver tu IMC y FFMI",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    KpknSecondaryPill(text = "Añadir altura", onClick = onAddHeight, modifier = Modifier.fillMaxWidth())
}
