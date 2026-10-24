package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Settings
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*
import kotlin.math.roundToInt

@Composable
fun TrainingSettingsBlock(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, modifier: Modifier = Modifier) {
    KpknModule(modifier.fillMaxWidth().testTag("training-settings")) {
        KpknModuleHeader("Entrenamiento")
        Text("Descanso por defecto",style=KpknType.bodyStrong,color=WizardColors.text)
        Text("Se usa cuando el ejercicio no declara un descanso propio. De 30 a 300 segundos, en pasos de 15.",style=KpknType.caption,color=WizardColors.textMuted)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KpknSecondaryPill("−15 s",{onUpdate { old -> old.copy(restTimerDefaultSeconds=((old.restTimerDefaultSeconds/15.0).roundToInt()*15-15).coerceIn(30,300)) }},Modifier.weight(1f).testTag("rest-default-less"))
            Text("${settings.restTimerDefaultSeconds} s",style=KpknType.bodyStrong,color=WizardColors.text)
            KpknSecondaryPill("+15 s",{onUpdate { old -> old.copy(restTimerDefaultSeconds=((old.restTimerDefaultSeconds/15.0).roundToInt()*15+15).coerceIn(30,300)) }},Modifier.weight(1f).testTag("rest-default-more"))
        }
        TrainingToggle("Iniciar descanso al registrar una serie", "Inicia el descanso entre series. Las pausas guiadas de técnicas conservan su secuencia.",
            settings.autoStartRestAfterRecording,{ checked -> onUpdate { it.copy(autoStartRestAfterRecording=checked) } },"rest-auto-start")
        TrainingToggle("Permitir sugerencias de descarga de AUGE", "Habilita el criterio de fatiga alta y preparación baja al cambiar de bloque. Otras señales pueden seguir sugiriendo descarga; no modifica el plan por sí solo.",
            settings.algorithmSettings.augeAutoDeload,{ checked -> onUpdate { it.copy(algorithmSettings=it.algorithmSettings.copy(augeAutoDeload=checked)) } },"auge-deload-setting")
    }
}
@Composable
private fun TrainingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).testTag(tag).toggleable(checked,role=Role.Switch,onValueChange=onChecked).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=KpknType.bodyStrong,color=WizardColors.text)
            Text(description,style=KpknType.caption,color=WizardColors.textMuted)
        }
        KpknSwitch(checked,null)
    }
}
