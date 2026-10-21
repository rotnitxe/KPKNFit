package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cabecera del registro de comida (NUTRITION_V1 §3): el título («Registrar comida» o «Editar comida»), la configuración y el cierre,
 * la fecha y las cuatro comidas de [MealSlots] en un control compacto. [onClose] es la misma acción de cierre y descarte que el arrastre
 * de la hoja. Con [onDateClick], la fecha es un botón de 48 dp que abre el calendario (N3b); sin él, solo informa.
 */
@Composable
fun LoggerHeader(
    title: String,
    dateLabel: String,
    mealType: MealType,
    onMealSelect: (MealType) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDateClick: (() -> Unit)? = null,
) {
    val meals = MealSlots.mealOrder
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // El título va a todo el ancho, para que casi siempre quepa en una línea. La fecha y los botones van debajo.
        Text(
            text = title,
            style = KpknType.title,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LoggerDatePill(label = dateLabel, onClick = onDateClick)
            Spacer(Modifier.weight(1f))
            KpknIconButton(icon = KpknLineIcons.Sliders, contentDescription = "Configuración de comidas", onClick = onSettings)
            KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar", onClick = onClose)
        }
        KpknSegmentedControl(
            options = meals.map { MealSlots.mealLabel(it) },
            selectedIndex = meals.indexOf(mealType).coerceAtLeast(0),
            onSelect = { index -> onMealSelect(meals[index]) },
            size = KpknSegmentedSize.Compact,
        )
    }
}

/**
 * Fecha del registro en una píldora `inset`. Con [onClick] es un botón de 48 dp de alto que se anuncia como «Cambiar la fecha de la
 * comida», con la fecha como estado; sin él, es solo informativa.
 */
@Composable
private fun LoggerDatePill(label: String, onClick: (() -> Unit)?) {
    val pill: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = KpknType.chip,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick == null) {
        Box(modifier = Modifier.semantics { contentDescription = "Fecha del registro: $label" }) { pill() }
    } else {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = "Cambiar la fecha de la comida"
                    stateDescription = label
                },
            contentAlignment = Alignment.CenterStart,
        ) { pill() }
    }
}

/**
 * Etiqueta de la fecha del registro: «Hoy · lun 13 oct» si es hoy y «lun 13 oct» si no. Si la fecha no se puede leer, se muestra tal cual.
 */
internal fun loggerDateLabel(logDate: String, locale: Locale, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(logDate) }.getOrNull() ?: return logDate
    val formatted = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    return if (date == today) "Hoy · $formatted" else formatted
}
