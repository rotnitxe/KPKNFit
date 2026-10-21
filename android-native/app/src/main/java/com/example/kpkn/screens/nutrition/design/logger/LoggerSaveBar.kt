package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Barra fija de la hoja (N3b): el total de la comida y la acción principal. Arriba, las kcal en `metricL`, que cuentan hasta su valor,
 * y los macros en una línea. Abajo, la píldora a todo el ancho: a la derecha del total, «Guardar en Almuerzo» se cortaría al 130 %.
 *
 * Sin alimentos ([kcal] nulo), el total es «—» y no hay macros. Con [busyLabel] («Interpretando…» o «Guardando…») la píldora se apaga y
 * muestra ese texto. Con [succeeded], la píldora pasa a ser el check de éxito. [approximate] pone «≈» si hay estimaciones.
 */
@Composable
fun LoggerSaveBar(
    kcal: Int?,
    protein: Int?,
    carbs: Int?,
    fats: Int?,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    approximate: Boolean = false,
    actionIcon: ImageVector? = null,
    busyLabel: String? = null,
    succeeded: Boolean = false,
) {
    val reduced = wizardReducedMotion()
    // Las kcal cuentan hasta su valor en 900 ms (KPKN_UI_V2 §7); con movimiento reducido, el valor final.
    val counted by animateIntAsState(
        targetValue = kcal ?: 0,
        animationSpec = if (reduced) snap() else tween(durationMillis = 900),
        label = "loggerSaveKcal",
    )
    val kcalText = if (kcal == null) "—" else (if (approximate) "≈ " else "") + counted.toString()

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KpknMetric(value = kcalText, unit = "kcal", style = KpknType.metricL)
                if (kcal != null && protein != null && carbs != null && fats != null) {
                    Spacer(Modifier.width(12.dp))
                    LoggerMacroLine(
                        protein = protein,
                        carbs = carbs,
                        fats = fats,
                        modifier = Modifier.weight(1f),
                        alignEnd = true,
                    )
                }
            }
            if (succeeded) {
                LoggerSuccessPill()
            } else {
                KpknPrimaryPill(
                    text = busyLabel ?: actionLabel,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (busyLabel == null) actionIcon else null,
                    enabled = enabled && busyLabel == null,
                )
            }
        }
    }
}
