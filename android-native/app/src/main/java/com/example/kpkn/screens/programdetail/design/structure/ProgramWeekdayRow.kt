package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.weekdaysFrom
import com.example.kpkn.screens.programdetail.weekdayInitial
import com.example.kpkn.ui.design.KpknType

/** Nombres de los días ISO (1 = lunes) para TalkBack. */
private val NOMBRES_DIA = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/**
 * Fila de los siete días (L M X J V S D, desde [startDay]), tocable: cada círculo mide 44 dp y marca o desmarca un día de entreno (`Role.Checkbox`, con su día en la descripción).
 * Si la fila no cabe en el ancho, los círculos se encogen en lugar de salirse de la hoja.
 */
@Composable
internal fun ProgramWeekdayToggles(
    trainingDays: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startDay: Int = 1,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val dot: Dp = minOf(44.dp, maxWidth / 7)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdaysFrom(startDay).forEach { day ->
                WeekdayDot(
                    day = day,
                    selected = day in trainingDays,
                    size = dot,
                    onToggle = { onToggle(day) },
                )
            }
        }
    }
}

/** Un día: círculo de [size] con la letra. Elegido = tinta y letra negra; si no, anillo fino y letra en `textMuted`. */
@Composable
private fun WeekdayDot(day: Int, selected: Boolean, size: Dp, onToggle: (() -> Unit)?) {
    val name = NOMBRES_DIA.getOrElse(day - 1) { "Día" }
    val state = if (selected) "día de entreno" else "sin entreno"
    val tapping = if (onToggle != null) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(size)
            .then(tapping)
            .semantics { contentDescription = "$name, $state" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(if (selected) Modifier.background(WizardColors.text, CircleShape) else Modifier)
                .then(if (!selected) Modifier.border(1.dp, WizardColors.divider, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = weekdayInitial(day),
                style = KpknType.chip,
                color = if (selected) WizardColors.ctaContent else WizardColors.textMuted,
                maxLines = 1,
            )
        }
    }
}
