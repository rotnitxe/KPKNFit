package com.example.kpkn.screens.habits.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Color de acento de cada hábito: energía para las comidas, columna para el peso y músculo para el entreno. */
internal fun habitAccent(kind: HabitKind): Color = when (kind) {
    HabitKind.MEALS -> SymbolPalette.energia
    HabitKind.WEIGHING -> SymbolPalette.columna
    HabitKind.TRAINING -> SymbolPalette.musculo
}

/**
 * Celda de un día del calendario. El color no es la única señal: sin registro, un contorno; con registro, un relleno
 * sólido; en el entreno, el relleno es más denso cuanto más nivel. Hoy lleva un anillo blanco y el futuro no lleva nada.
 * Con [description], la celda se lee con esa frase; sin ella (leyenda), la lee su texto.
 */
@Composable
internal fun HabitDaySwatch(
    kind: HabitKind,
    level: Int,
    isToday: Boolean,
    isFuture: Boolean,
    size: Dp,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val accent = habitAccent(kind)
    val fill = when {
        isFuture || level <= 0 -> Color.Transparent
        kind != HabitKind.TRAINING -> accent
        level >= 3 -> accent
        level == 2 -> accent.copy(alpha = 0.65f)
        else -> accent.copy(alpha = 0.35f)
    }
    val edge = when {
        isToday -> WizardColors.text
        isFuture -> Color.Transparent
        level <= 0 -> WizardColors.textFaint.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .size(size)
            .background(fill, shape)
            .border(if (isToday) 2.dp else 1.dp, edge, shape)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    )
}
