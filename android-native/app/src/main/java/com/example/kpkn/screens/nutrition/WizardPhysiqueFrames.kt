package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.R

/**
 * Recursos del bloque de grasa corporal, extraídos de `NutritionWizardScreen.kt`
 * en la Fase 7, cuando la pantalla y el ViewModel legacy del wizard nutricional
 * se retiraron. Los consumidores vivos son `WizChatPhysiquePicker` (wizard de
 * configuración) y `design/WizardPhysiqueSelector` (prototipo de la puerta
 * visual): el paquete no cambia para no tocar sus imports.
 *
 * NO borrar: los frames `wizard_h_*`/`wizard_m_*` se usan en el bloque de grasa.
 */

private val PhysiqueSliderBorder = Color.White.copy(alpha = 0.10f)

@Composable
internal fun VerticalPhysiqueSlider(pos: Float, onPosChange: (Float) -> Unit,
    height: Dp = 360.dp, hitWidth: Dp = 22.dp, tint: Color = Color.White, withBorder: Boolean = true) {
    var hPx by remember { mutableStateOf(1f) }
    val frac = ((pos - 1f) / 6f).coerceIn(0f, 1f)
    Box(
        Modifier.width(hitWidth).height(height).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))
            .then(if (withBorder) Modifier.border(1.dp, PhysiqueSliderBorder, RoundedCornerShape(999.dp)) else Modifier)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(pos.coerceIn(1f, 7f), 1f..7f)
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir porcentaje") { onPosChange((pos - .1f).coerceAtLeast(1f)); true },
                    CustomAccessibilityAction("Aumentar porcentaje") { onPosChange((pos + .1f).coerceAtMost(7f)); true },
                )
            }
            .onSizeChanged { hPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y.coerceIn(0f, hPx)
                    val p = 1f + (y / hPx) * 6f
                    onPosChange(p.coerceIn(1f, 7f))
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            val y = ev.changes.firstOrNull()?.position?.y ?: continue
                            val p = 1f + (y.coerceIn(0f, hPx) / hPx) * 6f
                            onPosChange(p.coerceIn(1f, 7f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxSize().padding(vertical = 8.dp).width(2.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.14f)))
        val thumbPad = with(LocalDensity.current) { (frac * (hPx - 18.dp.toPx())).coerceAtLeast(0f).toDp() }
        Box(Modifier.padding(top = thumbPad).size(18.dp).clip(CircleShape).background(tint)
            .then(if (withBorder) Modifier.border(1.dp, Color.Black.copy(alpha = 0.10f), CircleShape) else Modifier))
    }
}

internal val WizardMaleFrames = intArrayOf(
    R.drawable.wizard_h_00, R.drawable.wizard_h_01, R.drawable.wizard_h_02, R.drawable.wizard_h_03, R.drawable.wizard_h_04,
    R.drawable.wizard_h_05, R.drawable.wizard_h_06, R.drawable.wizard_h_07, R.drawable.wizard_h_08, R.drawable.wizard_h_09,
    R.drawable.wizard_h_10, R.drawable.wizard_h_11, R.drawable.wizard_h_12, R.drawable.wizard_h_13, R.drawable.wizard_h_14,
    R.drawable.wizard_h_15, R.drawable.wizard_h_16, R.drawable.wizard_h_17, R.drawable.wizard_h_18, R.drawable.wizard_h_19,
    R.drawable.wizard_h_20, R.drawable.wizard_h_21, R.drawable.wizard_h_22, R.drawable.wizard_h_23, R.drawable.wizard_h_24,
    R.drawable.wizard_h_25, R.drawable.wizard_h_26, R.drawable.wizard_h_27, R.drawable.wizard_h_28, R.drawable.wizard_h_29,
    R.drawable.wizard_h_30, R.drawable.wizard_h_31, R.drawable.wizard_h_32, R.drawable.wizard_h_33, R.drawable.wizard_h_34,
    R.drawable.wizard_h_35, R.drawable.wizard_h_36, R.drawable.wizard_h_37, R.drawable.wizard_h_38, R.drawable.wizard_h_39,
    R.drawable.wizard_h_40, R.drawable.wizard_h_41, R.drawable.wizard_h_42, R.drawable.wizard_h_43, R.drawable.wizard_h_44,
    R.drawable.wizard_h_45, R.drawable.wizard_h_46, R.drawable.wizard_h_47, R.drawable.wizard_h_48, R.drawable.wizard_h_49,
    R.drawable.wizard_h_50, R.drawable.wizard_h_51, R.drawable.wizard_h_52, R.drawable.wizard_h_53, R.drawable.wizard_h_54,
    R.drawable.wizard_h_55, R.drawable.wizard_h_56, R.drawable.wizard_h_57, R.drawable.wizard_h_58, R.drawable.wizard_h_59,
    R.drawable.wizard_h_60,
)

internal val WizardFemaleFrames = intArrayOf(
    R.drawable.wizard_m_00, R.drawable.wizard_m_01, R.drawable.wizard_m_02, R.drawable.wizard_m_03, R.drawable.wizard_m_04,
    R.drawable.wizard_m_05, R.drawable.wizard_m_06, R.drawable.wizard_m_07, R.drawable.wizard_m_08, R.drawable.wizard_m_09,
    R.drawable.wizard_m_10, R.drawable.wizard_m_11, R.drawable.wizard_m_12, R.drawable.wizard_m_13, R.drawable.wizard_m_14,
    R.drawable.wizard_m_15, R.drawable.wizard_m_16, R.drawable.wizard_m_17, R.drawable.wizard_m_18, R.drawable.wizard_m_19,
    R.drawable.wizard_m_20, R.drawable.wizard_m_21, R.drawable.wizard_m_22, R.drawable.wizard_m_23, R.drawable.wizard_m_24,
    R.drawable.wizard_m_25, R.drawable.wizard_m_26, R.drawable.wizard_m_27, R.drawable.wizard_m_28, R.drawable.wizard_m_29,
    R.drawable.wizard_m_30, R.drawable.wizard_m_31, R.drawable.wizard_m_32, R.drawable.wizard_m_33, R.drawable.wizard_m_34,
    R.drawable.wizard_m_35, R.drawable.wizard_m_36, R.drawable.wizard_m_37, R.drawable.wizard_m_38, R.drawable.wizard_m_39,
    R.drawable.wizard_m_40, R.drawable.wizard_m_41, R.drawable.wizard_m_42, R.drawable.wizard_m_43, R.drawable.wizard_m_44,
    R.drawable.wizard_m_45, R.drawable.wizard_m_46, R.drawable.wizard_m_47, R.drawable.wizard_m_48, R.drawable.wizard_m_49,
    R.drawable.wizard_m_50, R.drawable.wizard_m_51, R.drawable.wizard_m_52, R.drawable.wizard_m_53, R.drawable.wizard_m_54,
    R.drawable.wizard_m_55, R.drawable.wizard_m_56, R.drawable.wizard_m_57, R.drawable.wizard_m_58, R.drawable.wizard_m_59,
    R.drawable.wizard_m_60,
)
