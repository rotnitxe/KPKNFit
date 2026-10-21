package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Acción de texto destructiva (KPKN_UI_V2 §6): como `KpknTextAction`, pero en `danger`. Zona táctil de 48 dp de alto. La usan los
 * diálogos para lo que borra algo («Descartar»).
 */
@Composable
fun LoggerDangerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = KpknType.action,
            color = WizardColors.danger,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
