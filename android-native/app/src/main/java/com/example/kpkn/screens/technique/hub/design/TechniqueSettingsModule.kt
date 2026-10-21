package com.example.kpkn.screens.technique.hub.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknType

/** Ajustes de grabación y automatismo; quien llama aplica cambios sobre el ajuste vigente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechniqueSettingsModule(
    settings: TechniqueVideoSettings,
    showCustomPlate: Boolean,
    onShowCustomPlate: (Boolean) -> Unit,
    onAnalyzeVideos: (Boolean) -> Unit,
    onPrefer60Fps: (Boolean) -> Unit,
    onPlateDiameterMm: (Int) -> Unit,
    onMaxRecordSeconds: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Grabación y análisis")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Analizar la técnica de los vídeos", style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f))
            KpknSwitch(settings.analyzeVideos, onAnalyzeVideos, Modifier.semantics { contentDescription = "Analizar la técnica de los vídeos" })
        }
        Text("El análisis manual siempre está disponible.", style = KpknType.caption, color = WizardColors.textFaint)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Grabar a 60 fps cuando se pueda", style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f))
            KpknSwitch(settings.prefer60Fps, onPrefer60Fps, Modifier.semantics { contentDescription = "Grabar a 60 fps cuando se pueda" })
        }
        Spacer(Modifier.height(18.dp))
        Text("Disco de calibración", style = KpknType.bodyStrong, color = WizardColors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(450, 400, 350).forEach { diameter ->
                KpknChip("${diameter / 10}\u00A0cm", !showCustomPlate && settings.plateDiameterMm == diameter, {
                    onShowCustomPlate(false)
                    onPlateDiameterMm(diameter)
                })
            }
            KpknChip("Otro", showCustomPlate || settings.plateDiameterMm !in listOf(450, 400, 350), { onShowCustomPlate(true) })
        }
        if (showCustomPlate || settings.plateDiameterMm !in listOf(450, 400, 350)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknNumberCell(
                    value = (settings.plateDiameterMm / 10.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') },
                    input = KpknCellInput.Text(onCommit = { raw -> raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 10.0..70.0 }?.let { onPlateDiameterMm((it * 10).toInt()) } }),
                    contentDescription = "Diámetro del disco en centímetros, entre 10 y 70",
                    modifier = Modifier.width(96.dp),
                )
                Text("cm", style = KpknType.unit, color = WizardColors.textMuted)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Duración máxima", style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(8.dp))
        val durations = listOf(60, 120, 180, 300)
        KpknSegmentedControl(
            options = listOf("1\u00A0min", "2\u00A0min", "3\u00A0min", "5\u00A0min"),
            selectedIndex = durations.indexOf(settings.maxRecordSeconds),
            onSelect = { onMaxRecordSeconds(durations[it]) },
            descriptions = listOf("1 minuto", "2 minutos", "3 minutos", "5 minutos"),
        )
    }
}
