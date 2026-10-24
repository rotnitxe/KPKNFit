package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** La etiqueta discreta «rápida» de una fila del diario cuyo alimento es una entrada rápida (N4): no depende del color, lleva texto. */
@Composable
fun QuickEntryTag(modifier: Modifier = Modifier) {
    Text(
        text = "rápida",
        style = KpknType.caption,
        color = WizardColors.textMuted,
        maxLines = 1,
        modifier = modifier
            .testTag("quick-entry-tag")
            .semantics { contentDescription = "Entrada rápida" }
            .background(KpknSurface.inset, KpknShape.pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
