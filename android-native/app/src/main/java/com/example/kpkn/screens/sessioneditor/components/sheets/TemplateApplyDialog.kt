package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

/** D4: ambas decisiones conservan su etiqueta completa también con letra grande. */
@Composable
internal fun TemplateApplyDialog(
    decision: SessionTemplateApplyDecision,
    onDismiss: () -> Unit,
    onApply: (SessionTemplateApplyMode) -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar plantilla", style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla «${decision.template.name}»?",
                style = KpknType.body, color = WizardColors.textMuted)
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknPrimaryPill(
                    text = "Reemplazar",
                    onClick = { onApply(SessionTemplateApplyMode.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                )
                KpknSecondaryPill(
                    text = "Añadir al final",
                    onClick = { onApply(SessionTemplateApplyMode.APPEND) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
