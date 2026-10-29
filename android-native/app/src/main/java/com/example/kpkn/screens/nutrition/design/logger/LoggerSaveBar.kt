package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import java.text.NumberFormat
import java.util.Locale

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
 * La línea de la barra de guardado (lienzo `.np-save`): «1 alimento» o «4 alimentos», sin la proteína (se cortaba). Con dudas que no se
 * pueden saltar ([blockingQuestions] mayor que 0) dice por qué el botón está apagado: «Resuelve 2 dudas». [proteinGrams] ya no se usa:
 * se conserva para quien llama.
 */
@Suppress("UNUSED_PARAMETER")
fun loggerSaveBarLine(foodCount: Int, proteinGrams: Int, blockingQuestions: Int): String = when {
    blockingQuestions > 0 -> "Resuelve ${if (blockingQuestions == 1) "1 duda" else "$blockingQuestions dudas"}"
    else -> if (foodCount == 1) "1 alimento" else "$foodCount alimentos"
}

/** El verbo de la barra: «Guardar en desayuno» (la comida en minúscula) o «Guardar cambios» al editar una comida registrada. */
fun loggerSaveLabel(mealLabel: String, editing: Boolean): String =
    if (editing) "Guardar cambios" else "Guardar en ${mealLabel.lowercase(Locale.forLanguageTag("es"))}"

/** El aviso de dudas (`.np-pend`): «1 duda por revisar · o guarda con lo estimado», «2 dudas por revisar · o guarda con lo estimado». */
fun loggerPendingLine(count: Int): String = loggerPendingTitle(count) + LOGGER_PENDING_HINT

/**
 * Barra de guardado flotante (lienzo `.np-save`): plana (superficie 2, sin vidrio), 64 dp y radio 32; a la izquierda las calorías
 * («297 kcal», Inter SemiBold 18, se encoge antes que cortarse) con [line] debajo («1 alimento») y a la derecha el botón de tinta de 48 dp
 * «Guardar en merienda». Apagado ([enabled] falso) va en gris (`.go.off`) y no hace nada.
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
    val goFill by animateColorAsState(if (enabled) LoggerColors.ink else Color.White.copy(alpha = 0.1f), tween(200), label = "loggerSaveGo")
    val goInk by animateColorAsState(if (enabled) LoggerColors.onInk else LoggerColors.faint, tween(200), label = "loggerSaveGoInk")
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(LoggerColors.surface2, shape)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Las cifras se miden primero y nunca se cortan; el botón se queda con el resto y, si no le cabe su verbo, lo encoge.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            LoggerFitText(text = "${loggerThousands(kcal)} kcal", style = LoggerText.number(18f), color = LoggerColors.ink, minSize = 13.sp)
            LoggerFitText(text = line, style = LoggerText.cap, color = LoggerColors.faint, minSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f, fill = false)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(goFill, RoundedCornerShape(24.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onAction)
                .semantics { contentDescription = actionLabel }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Con letra grande, si «Guardar en merienda» no cabe ni encogido, el botón dice solo «Guardar» (la comida ya va en los chips
            // de arriba y la descripción accesible conserva la frase entera).
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val minStyle = SaveGoStyle.copy(fontSize = 13.sp)
                val fits = measurer.measure(actionLabel, minStyle, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
                LoggerFitText(text = if (fits) actionLabel else loggerSaveShortLabel(actionLabel), style = SaveGoStyle, color = goInk, minSize = 13.sp)
            }
        }
    }
}

/** El verbo corto de la barra cuando la frase no cabe: «Guardar en merienda» y «Guardar cambios» → «Guardar». */
fun loggerSaveShortLabel(actionLabel: String): String = actionLabel.substringBefore(' ')
