package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale

/** Qué salió a medias al analizar: se leyeron alimentos pero no todas las cantidades, o no se leyó nada. */
enum class LoggerNoticeKind { PARTIAL, NOTHING }

/** Un aviso de análisis ya redactado para la persona (f-error): sin jerga ni nombres de excepciones. */
data class LoggerNoticeText(val title: String, val message: String)

/**
 * Los textos del aviso (f-error). A medias: «Lo leí a medias» y «Reconocí las lentejas y el chorizo, pero no las cantidades. Las dejé en
 * una ración normal: revísalas antes de guardar.» con los nombres de [recognized] (dos como mucho y «y N más»). Sin nada: «No pude leer
 * tu comida» y qué hacer. El detalle técnico se queda en los registros de la app.
 */
fun loggerNoticeText(kind: LoggerNoticeKind, recognized: List<String>): LoggerNoticeText {
    val names = recognized.map { it.trim().lowercase(Locale.forLanguageTag("es")) }.filter { it.isNotBlank() }.distinct()
    if (kind == LoggerNoticeKind.NOTHING || names.isEmpty()) {
        return LoggerNoticeText(
            title = "No pude leer tu comida",
            message = "No reconocí los alimentos de lo que escribiste. Escríbelo de otra forma o búscalo a mano.",
        )
    }
    val listed = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} y ${names[1]}"
        else -> "${names[0]}, ${names[1]} y ${names.size - 2} más"
    }
    val plural = names.size > 1
    return LoggerNoticeText(
        title = "Lo leí a medias",
        message = "Reconocí $listed, pero no ${if (plural) "las cantidades" else "la cantidad"}. " +
            "${if (plural) "Las dejé en una ración normal: revísalas" else "La dejé en una ración normal: revísala"} antes de guardar.",
    )
}

/**
 * Aviso de análisis (lienzo f-error): módulo con borde de energía, el icono de aviso (decisión F-D4) y el título en energía, el mensaje y
 * dos píldoras pequeñas «Volver a analizar» ([onRetry]) y «Buscar a mano» ([onSearch]).
 */
@Composable
fun LoggerAnalysisNotice(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LoggerColors.card, shape)
            .border(1.dp, LoggerColors.ene.copy(alpha = 0.2f), shape)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(KpknLineIcons.Alert, contentDescription = null, modifier = Modifier.size(14.dp), tint = LoggerColors.ene)
            Text(text = title.uppercase(Locale.forLanguageTag("es")), style = LoggerText.eyebrow, color = LoggerColors.ene)
        }
        Text(
            text = message,
            style = LoggerText.body,
            color = LoggerColors.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoggerPill(text = "Volver a analizar", onClick = onRetry, tone = LoggerPillTone.SECONDARY, small = true)
            LoggerPill(text = "Buscar a mano", onClick = onSearch, tone = LoggerPillTone.SECONDARY, small = true)
        }
    }
}
