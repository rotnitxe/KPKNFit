package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknType

/**
 * La hoja de detalle de un aviso de «Pendiente»: la misma hoja KPKN que las decisiones, con su título y el módulo de siempre dentro. Lo
 * que pinta [content] es el mismo composable que antes ocupaba la portada o la pestaña.
 */
@Composable
internal fun ProgramPendingDetailSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.85f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
            content()
        }
    }
}
