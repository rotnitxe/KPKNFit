package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknShape

/**
 * Barra de progreso de la sesión (LS2a, §2 punto 2): una regla de 6 dp con 3 dp entre tramos, uno por ejercicio. Los hechos van en
 * tinta, el actual en `musculo` y los futuros en blanco al 12 %. [currentIndex] se cuenta desde 0; fuera de rango, ningún tramo es el
 * actual. Tocarla abre la ruta de la sesión con [onOpenRoute].
 *
 * La zona táctil mide 48 dp (KPKN_UI_V2 §8) pero no ocupa su alto en la columna: se centra sobre la regla y sobresale por arriba y por
 * abajo. Así la barra queda a 8 dp de la fila superior y a 12 dp de la cabecera, como pide el marco compacto (LS4). La fila superior y la
 * cabecera van por encima en el orden de dibujo (`zIndex`), para que sus botones ganen los toques del solapamiento.
 *
 * El dibujo sale de `ProgramWeekRuler` (P1, `screens/programdetail/design/ProgramWeekRuler.kt`), con su misma forma y colores. Su
 * descripción es otra: aquí es el ejercicio, no la semana.
 */
@Composable
fun WorkoutProgressBar(
    exerciseCount: Int,
    currentIndex: Int,
    doneFlags: List<Boolean>,
    onOpenRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (exerciseCount <= 0) return
    val current = currentIndex.takeIf { it in 0 until exerciseCount }
    val interaction = remember { MutableInteractionSource() }
    val position = (current ?: 0) + 1
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (index in 0 until exerciseCount) {
                val color = when {
                    index == current -> SymbolPalette.musculo
                    doneFlags.getOrElse(index) { false } -> WizardColors.text
                    else -> FutureSegment
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color, KpknShape.pill),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .requiredHeight(48.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Abrir la ruta de la sesión",
                    onClick = onOpenRoute,
                )
                .semantics { contentDescription = "Ejercicio $position de $exerciseCount. Abrir la ruta de la sesión" },
        )
    }
}

/** Tramos futuros: blanco al 12 %, como en `ProgramWeekRuler`. */
private val FutureSegment: Color = Color.White.copy(alpha = 0.12f)
