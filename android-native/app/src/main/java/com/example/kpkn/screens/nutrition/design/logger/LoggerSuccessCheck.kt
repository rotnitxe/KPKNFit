package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface

/** Trazo del check de éxito (KPKN_UI_V2 §7 y el brief N3b): 600 ms. Con movimiento reducido, el check aparece ya dibujado. */
internal const val LoggerSuccessDrawMs = 600

/** Lo que la hoja espera con el check a la vista antes de cerrarse: 600 ms, o 300 ms con movimiento reducido. */
internal const val LoggerSuccessHoldMs = 600L
internal const val LoggerSuccessHoldReducedMs = 300L

/** Extremos del check en una retícula de 24: el brazo corto va de A a B y el largo de B a C. */
private const val CheckAx = 5f
private const val CheckAy = 12.5f
private const val CheckBx = 10f
private const val CheckBy = 17.5f
private const val CheckCx = 19f
private const val CheckCy = 7f

/**
 * Píldora del éxito (N3b): ocupa el sitio de la acción principal, con 56 dp como ella y un check que se dibuja en 600 ms en `done`.
 * Con movimiento reducido, el check aparece ya dibujado. Se anuncia como «Comida guardada».
 */
@Composable
fun LoggerSuccessPill(modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = LoggerSuccessDrawMs))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .semantics { contentDescription = "Comida guardada" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val s = size.minDimension / 24f
            val total = CheckLengthAB + CheckLengthBC
            val drawn = progress.value * total
            val a = Offset(CheckAx * s, CheckAy * s)
            val b = Offset(CheckBx * s, CheckBy * s)
            val c = Offset(CheckCx * s, CheckCy * s)
            val stroke = 3f * s
            if (drawn <= CheckLengthAB) {
                val f = drawn / CheckLengthAB
                drawLine(
                    color = WizardColors.done,
                    start = a,
                    end = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                drawLine(color = WizardColors.done, start = a, end = b, strokeWidth = stroke, cap = StrokeCap.Round)
                val f = (drawn - CheckLengthAB) / CheckLengthBC
                drawLine(
                    color = WizardColors.done,
                    start = b,
                    end = Offset(b.x + (c.x - b.x) * f, b.y + (c.y - b.y) * f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Longitudes de los dos brazos del check, en unidades de la retícula de 24. */
private val CheckLengthAB = kotlin.math.sqrt(
    (CheckBx - CheckAx) * (CheckBx - CheckAx) + (CheckBy - CheckAy) * (CheckBy - CheckAy),
)
private val CheckLengthBC = kotlin.math.sqrt(
    (CheckCx - CheckBx) * (CheckCx - CheckBx) + (CheckCy - CheckBy) * (CheckCy - CheckBy),
)
