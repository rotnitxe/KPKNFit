package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `.lg-head .t strong`: Syne Bold 19 con −0,3. */
private val HeadTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.3).sp,
)

/**
 * Cabecera del registro (lienzo `lg-head`, f-empty): el cierre a la izquierda, en el centro el título («Registrar comida» o «Editar
 * comida») y debajo el calendario con «Hoy · 08:40», y los ajustes del registro a la derecha. Tocar la fecha abre «¿Cuándo fue?»
 * ([onDateClick]). Debajo van las chips de la comida ([LoggerMealChips]) si [mealType] no es nulo.
 */
@Composable
fun LoggerHeader(
    title: String,
    dateLabel: String,
    mealType: MealType?,
    onMealSelect: (MealType) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDateClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoggerIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar", onClick = onClose, fill = true)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = HeadTitleStyle,
                    color = LoggerColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 24.dp)
                        .then(
                            if (onDateClick != null) {
                                Modifier
                                    .clickable(role = Role.Button, onClick = onDateClick)
                                    .semantics {
                                        contentDescription = "Cambiar la fecha de la comida"
                                        stateDescription = dateLabel
                                    }
                            } else {
                                Modifier.semantics { contentDescription = "Fecha del registro: $dateLabel" }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KpknLineIcons.Calendar, contentDescription = null, modifier = Modifier.size(13.dp), tint = LoggerColors.faint)
                    Spacer(Modifier.width(5.dp))
                    Text(text = dateLabel, style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1)
                }
            }
            LoggerIconButton(icon = KpknLineIcons.Sliders, contentDescription = "Ajustes del registro", onClick = onSettings, fill = true)
        }
        if (mealType != null) LoggerMealChips(mealType = mealType, onMealSelect = onMealSelect)
    }
}

/** `.meal-chips`: Desayuno, Almuerzo, Merienda y Cena en chips de 34 dp. */
@Composable
fun LoggerMealChips(mealType: MealType, onMealSelect: (MealType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MealSlots.mealOrder.forEach { meal ->
            LoggerChip(
                text = MealSlots.mealLabel(meal),
                selected = meal == mealType,
                onClick = { onMealSelect(meal) },
                height = 34.dp,
            )
        }
    }
}

/**
 * La fecha de la cabecera: «Hoy» si es hoy y «lun 13 oct» si no; [time] se añade con « · » («Hoy · 08:40»). Si la fecha no se puede leer,
 * se muestra tal cual.
 */
internal fun loggerDateLabel(logDate: String, locale: Locale, today: LocalDate = LocalDate.now(), time: String? = null): String {
    val date = runCatching { LocalDate.parse(logDate) }.getOrNull()
    val day = when {
        date == null -> logDate
        date == today -> "Hoy"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }
    return if (time.isNullOrBlank()) day else "$day · $time"
}
