package com.example.kpkn.screens.nutrition.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CreatineProtocol
import com.example.kpkn.domain.nutrition.CreatineSaturationEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknType

/** Hoja de seguimiento de creatina: explica las dos pautas con las dosis de [doses] y deja elegir una antes de iniciar el seguimiento. */
@Composable
fun CreatineSaturationOverlay(
    visible: Boolean,
    weightKg: Double?,
    doses: CreatineSaturationEngine.CreatineProtocolDoses,
    onDismiss: () -> Unit,
    onConfirm: (CreatineProtocol) -> Unit,
) {
    if (!visible) return
    var selected by remember { mutableStateOf(CreatineProtocol.LOADING) }
    val protocols = listOf(CreatineProtocol.LOADING, CreatineProtocol.GRADUAL)

    KpknSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Seguimiento de creatina",
            style = KpknType.title,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "La creatina satura los músculos tras una carga (~5–7 días) o con 3–5 g/día durante ~3–4 semanas. " +
                    "A partir de ahí suele notarse el efecto ergogénico.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            // Sin peso registrado, las dosis salen de una referencia de 70 kg: se dice así, no se presenta como el peso del usuario.
            Text(
                text = weightKg?.let { "Con tu peso (${"%.0f".format(it)} kg):" }
                    ?: "Sin peso registrado: dosis orientativas para 70 kg:",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            Text(
                text = "• Carga: ${"%.1f".format(doses.loadingDailyGrams)} g/día × ${doses.loadingDays} días, " +
                    "luego ${"%.1f".format(doses.maintenanceDailyGrams)} g/día",
                style = KpknType.body,
                color = WizardColors.text,
            )
            Text(
                text = "• Gradual: ${"%.0f".format(doses.gradualDailyGrams)} g/día (~28 días)",
                style = KpknType.body,
                color = WizardColors.text,
            )

            KpknSegmentedControl(
                options = listOf("Semana de carga", "Pauta gradual"),
                selectedIndex = protocols.indexOf(selected),
                onSelect = { selected = protocols[it] },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Basado en ISSN/EFSA. No sustituye orientación médica.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )

            KpknSheetWhiteButton(
                text = "Iniciar seguimiento",
                onClick = { onConfirm(selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
