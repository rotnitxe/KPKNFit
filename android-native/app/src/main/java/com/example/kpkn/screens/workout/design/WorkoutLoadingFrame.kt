package com.example.kpkn.screens.workout.design
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/**
 * Carga de la sesión en vivo (LS2a, §5): esqueletos sobre negro puro, sin el fondo azul ni el spinner. «Cargando la sesión…» aparece
 * solo si la carga pasa de 600 ms. «Cancelar carga» es una acción de texto. Movimiento: el brillo de los esqueletos ya respeta el
 * movimiento reducido.
 */
@Composable
fun WorkoutLoadingFrame(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600L)
        slow = true
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknSkeleton(modifier = Modifier.width(120.dp).height(12.dp), shape = KpknShape.pill)
        KpknSkeleton(modifier = Modifier.fillMaxWidth(0.72f).height(32.dp), shape = KpknShape.pill)
        KpknSkeletonModule(lines = 3)
        KpknSkeletonModule(lines = 2)
        if (slow) {
            Text(
                text = "Cargando la sesión…",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknTextAction(text = "Cancelar carga", onClick = onCancel)
    }
}
