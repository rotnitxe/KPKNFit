package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/**
 * H3f · El archivo parece un programa que KPKN ya tiene: «Parece «X».». «Usar el de KPKN» cambia lo importado por ese plan y
 * «Seguir con mi archivo» lo descarta (y el aviso no vuelve a salir). Con [unavailable], el plan no encaja con las respuestas
 * del alta: el aviso lo dice y solo queda seguir con el archivo. Vidrio tenue con un trazo verde a la izquierda; sin tarjeta.
 */
@Composable
internal fun ImportKnownProgramNotice(
    planId: String,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
) {
    val name = remember(planId) { PersonalizedPlanCatalog.find(planId)?.displayName }
    if (name != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .importGlassPanel()
                .testTag("import-known-notice"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(SymbolPalette.ok))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (unavailable) "No podemos ofrecerte «$name» con tus respuestas." else "Parece «$name».",
                    style = WizardTypography.controlLabel,
                    color = WizardColors.text,
                )
                if (!unavailable) {
                    Text(
                        text = "KPKN ya lo tiene, completo y con su progresión, adaptado a tus datos.",
                        style = WizardTypography.note,
                        color = WizardColors.textMuted,
                    )
                    ImportTextAction(label = "Usar el de KPKN", onClick = onUse, modifier = Modifier.testTag("import-known-use"))
                }
                ImportTextAction(
                    label = "Seguir con mi archivo",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("import-known-dismiss"),
                )
            }
        }
    }
}
