package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.programs.pluralDias
import com.example.kpkn.screens.programs.pluralSesiones

/** Los días de la semana con su letra, en el orden de la semana (ISO: 1 = lunes). */
private val WEEKDAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

/** El nombre de cada día, para el lector de pantalla. */
private val WEEKDAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * La selección inicial, según los días de la semana con más sesiones: 1 → L; 2 → L J; 3 → L X V; 4 → L M J V; 5 → L M X J V; 6 →
 * L a S; 7 → todos. Con más de 7, todos; con 0, como con 1.
 */
internal fun importInitialWeekdays(sessionsPerWeek: Int): Set<Int> = when (sessionsPerWeek.coerceIn(1, 7)) {
    1 -> setOf(1)
    2 -> setOf(1, 4)
    3 -> setOf(1, 3, 5)
    4 -> setOf(1, 2, 4, 5)
    5 -> setOf(1, 2, 3, 4, 5)
    6 -> setOf(1, 2, 3, 4, 5, 6)
    else -> setOf(1, 2, 3, 4, 5, 6, 7)
}

/**
 * La línea bajo los días: con tantos días como sesiones, «3 sesiones en 3 días.»; con menos, que algunos días tendrán más de una
 * sesión; con más, nada. Sin ningún día elegido, nada: lo dice la revisión.
 */
internal fun importWeekdaysHelp(sessionsPerWeek: Int, chosen: Int): String? = when {
    chosen <= 0 -> null
    chosen == sessionsPerWeek -> "${pluralSesiones(sessionsPerWeek)} en ${pluralDias(chosen)}."
    chosen < sessionsPerWeek -> "Algunos días tendrán más de una sesión."
    else -> null
}

/**
 * «Días de entrenamiento»: siete interruptores, L M X J V S D, con 48 dp de alto táctil. El elegido va con la tinta de la
 * pantalla (como el conmutador de opciones del paquete).
 */
@Composable
internal fun ImportWeekdaysSection(
    selected: Set<Int>,
    sessionsPerWeek: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("import-weekdays"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Días de entrenamiento",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                ImportWeekdayToggle(
                    day = day,
                    on = day in selected,
                    onToggle = { onToggle(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        importWeekdaysHelp(sessionsPerWeek, selected.size)?.let { help ->
            Text(
                help,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("import-weekdays-help"),
            )
        }
    }
}

/** Un día: un símbolo de 40 dp dentro de su objetivo táctil de 48 dp; elegido, relleno con la tinta de la pantalla. */
@Composable
private fun ImportWeekdayToggle(day: Int, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag("import-weekday-$day")
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = WEEKDAY_NAMES[day - 1] },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) WizardColors.cta else Color.Transparent)
                .border(1.dp, if (on) WizardColors.cta else WizardColors.textFaint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                WEEKDAY_LETTERS[day - 1],
                style = WizardTypography.cardTitle,
                color = if (on) WizardColors.ctaContent else WizardColors.textMuted,
            )
        }
    }
}
