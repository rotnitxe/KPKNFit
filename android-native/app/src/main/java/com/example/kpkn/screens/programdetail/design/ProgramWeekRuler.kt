package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknShape

/**
 * Regla de semanas del programa (KPKN_UI_V2, P1): una franja por semana, a todo el ancho. Las hechas van en tinta, la actual en
 * `musculo` y las futuras en blanco al 12 %. [currentIndex] es la semana en curso, contada desde 1; null = ninguna en curso, así que
 * todas quedan futuras. Sin semanas no pinta nada.
 */
@Composable
fun ProgramWeekRuler(
    weekCount: Int,
    currentIndex: Int?,
    modifier: Modifier = Modifier,
) {
    if (weekCount <= 0) return
    val description = if (currentIndex != null) {
        "Semana $currentIndex de $weekCount"
    } else {
        "$weekCount semanas, ninguna en curso"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (week in 1..weekCount) {
            val color = when {
                currentIndex == null -> FutureWeek
                week < currentIndex -> WizardColors.text
                week == currentIndex -> SymbolPalette.musculo
                else -> FutureWeek
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color, KpknShape.pill),
            )
        }
    }
}

/** Semanas futuras: blanco al 12 %. */
private val FutureWeek: Color = Color.White.copy(alpha = 0.12f)
