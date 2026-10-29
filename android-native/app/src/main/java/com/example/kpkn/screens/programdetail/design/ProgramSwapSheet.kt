package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.domain.training.SwapScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Los alcances de «Dónde se cambia», en el orden y con el texto del segmentado de m-swap. */
internal val swapScopeOptions: List<Pair<SwapScope, String>> = listOf(
    SwapScope.THIS_SESSION to "Esta sesión",
    SwapScope.REST_OF_BLOCK to "Resto del bloque",
    SwapScope.WHOLE_PROGRAM to "Todo",
)

/** La línea pequeña de una alternativa: «Intención equivalente» o «Mismo patrón», con su variante si la tiene («Mismo patrón · a una mano»). */
internal fun swapOptionLine(option: SwapOption): String {
    val kind = if (option.kind == SwapKind.EQUIVALENT) "Intención equivalente" else "Mismo patrón"
    return listOf(kind, option.variant.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
}

/**
 * «Cambiar ejercicio» (lienzo v6, m-swap): el ejercicio de origen, las alternativas como filas con radio, «Dónde se cambia» en el segmentado
 * de KPKN, la casilla de material cuando el lugar no lo tiene declarado y «Revisar cambio», que se activa al elegir. La revisión enseña la
 * decisión del sistema y deja «Confirmar cambio» o «Editar selección». Nada se aplica hasta confirmar.
 */
@Composable
internal fun ProgramSwapSheet(
    sourceName: String,
    options: List<SwapOption>,
    requiresEquipmentConfirmation: Boolean,
    previewDetail: String?,
    busy: Boolean,
    onPreview: (String, SwapScope, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(sourceName, options) { mutableStateOf<String?>(null) }
    var scope by remember(sourceName, options) { mutableStateOf(SwapScope.THIS_SESSION) }
    var equipmentConfirmed by remember(sourceName, options) { mutableStateOf(false) }
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        if (previewDetail != null) {
            LienzoSheetTitle(title = sourceName, eyebrow = "Cambiar ejercicio", sub = previewDetail)
            ProgramLienzoPill(text = "Confirmar cambio", onClick = onConfirm, modifier = Modifier.fillMaxWidth(), enabled = !busy, tag = "swap-confirm")
            ProgramLienzoPill(
                text = "Editar selección",
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                filled = false,
                enabled = !busy,
            )
            return@ProgramLienzoSheet
        }
        LienzoSheetTitle(
            title = sourceName,
            eyebrow = "Cambiar ejercicio",
            sub = "Conserva repeticiones, RIR y descansos. La carga nueva la eliges al entrenar.",
        )
        if (options.isEmpty()) {
            Text("Sin alternativas disponibles con estos filtros.", style = LienzoCapStyle, color = WizardColors.textFaint)
        }
        options.forEachIndexed { index, option ->
            LienzoOptionRow(
                label = option.displayName,
                sub = swapOptionLine(option),
                selected = selected == option.configId,
                onClick = { selected = option.configId },
                divider = index > 0,
                minHeight = 54.dp,
                enabled = !busy,
                tag = "swap-option:${option.configId}",
            )
        }
        LienzoGroup("Dónde se cambia")
        LienzoSegmented(
            options = swapScopeOptions.map { it.second },
            selectedIndex = swapScopeOptions.indexOfFirst { it.first == scope }.coerceAtLeast(0),
            onSelect = { scope = swapScopeOptions[it].first },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        if (requiresEquipmentConfirmation) {
            SwapEquipmentRow(checked = equipmentConfirmed, onToggle = { equipmentConfirmed = !equipmentConfirmed }, enabled = !busy)
        }
        val chosen = selected
        ProgramLienzoPill(
            text = "Revisar cambio",
            onClick = { if (chosen != null) onPreview(chosen, scope, equipmentConfirmed) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !busy && chosen != null && (!requiresEquipmentConfirmation || equipmentConfirmed),
            tag = "swap-review",
        )
    }
}

/** La casilla de material sin declarar (como hoy), con el aviso como línea pequeña. */
@Composable
private fun SwapEquipmentRow(checked: Boolean, onToggle: () -> Unit, enabled: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = 48.dp)
            .testTag("swap-equipment-confirmation")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (checked) WizardColors.text else Color.Transparent, shape)
                .border(1.5.dp, if (checked) WizardColors.text else WizardColors.textFaint, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(KpknLineIcons.Check, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(14.dp))
        }
        LienzoRowTexts(
            title = "Tengo el material para esta variante",
            sub = "El material de este lugar no está declarado. Comprueba la variante que elijas.",
            modifier = Modifier.weight(1f),
        )
    }
}
