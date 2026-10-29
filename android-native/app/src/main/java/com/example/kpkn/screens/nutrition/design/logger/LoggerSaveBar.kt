package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import java.text.NumberFormat
import java.util.Locale

private val SaveKcalStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp)
private val SaveGoStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)

/** Cifras con el punto de miles de España («1.752»). */
internal fun loggerThousands(value: Int): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).apply {
    isGroupingUsed = true
}.format(value).let { formatted ->
    // Java solo agrupa a partir de 10.000 en «es»: el lienzo escribe «1.752».
    if (value in 1000..9999 || value in -9999..-1000) {
        val digits = kotlin.math.abs(value).toString()
        (if (value < 0) "-" else "") + digits.dropLast(3) + "." + digits.takeLast(3)
    } else formatted
}

/**
 * La línea de la barra de guardado (lienzo `saveBar`): «4 alimentos · 25 g de proteína», o con dudas sin respuesta posible
 * «Resuelve 2 dudas o sigue con la estimación».
 */
fun loggerSaveBarLine(foodCount: Int, proteinGrams: Int, blockingQuestions: Int): String = when {
    blockingQuestions > 0 -> "Resuelve ${if (blockingQuestions == 1) "1 duda" else "$blockingQuestions dudas"} o sigue con la estimación"
    else -> "${if (foodCount == 1) "1 alimento" else "$foodCount alimentos"} · $proteinGrams g de proteína"
}

/** El verbo de la barra: «Guardar en desayuno» (la comida en minúscula) o «Guardar cambios» al editar una comida registrada. */
fun loggerSaveLabel(mealLabel: String, editing: Boolean): String =
    if (editing) "Guardar cambios" else "Guardar en ${mealLabel.lowercase(Locale.forLanguageTag("es"))}"

/** `.pending` (f-result): «2 dudas: tócalas para afinar o guarda con la estimación». */
fun loggerPendingLine(count: Int): String =
    if (count == 1) "1 duda: tócala para afinar o guarda con la estimación"
    else "$count dudas: tócalas para afinar o guarda con la estimación"

/** La línea de la media hoja de Inicio (f-home-res): «3 alimentos · 1 duda»; sin dudas, solo los alimentos. */
fun loggerQuickSubtitle(foodCount: Int, pending: Int): String {
    val foods = if (foodCount == 1) "1 alimento" else "$foodCount alimentos"
    return when (pending) {
        0 -> foods
        1 -> "$foods · 1 duda"
        else -> "$foods · $pending dudas"
    }
}

/**
 * Barra de guardado flotante (lienzo `.savebar.glass`): píldora de vidrio de 64 dp con las calorías en Syne 20, la línea de [line] y el
 * botón de tinta de 48 dp «Guardar en desayuno». Apagado ([enabled] falso) va en gris (`.go.off`) y no hace nada.
 */
@Composable
fun LoggerSaveBar(
    kcal: Int,
    line: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haze = LocalHazeState.current
    val goFill by animateColorAsState(if (enabled) LoggerColors.ink else Color.White.copy(alpha = 0.1f), tween(200), label = "loggerSaveGo")
    val goInk by animateColorAsState(if (enabled) LoggerColors.onInk else LoggerColors.faint, tween(200), label = "loggerSaveGoInk")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .kpknGlassOrFallback(haze, RoundedCornerShape(32.dp), variant = KpknGlassVariant.ISLAND)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(text = "${loggerThousands(kcal)} kcal", style = SaveKcalStyle, color = LoggerColors.ink, maxLines = 1)
            Text(text = line, style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(goFill, RoundedCornerShape(24.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onAction)
                .semantics { contentDescription = actionLabel }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = actionLabel, style = SaveGoStyle, color = goInk, maxLines = 1)
        }
    }
}
