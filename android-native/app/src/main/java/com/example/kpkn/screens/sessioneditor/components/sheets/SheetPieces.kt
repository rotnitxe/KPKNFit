package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlinx.coroutines.delay

/** Pista del análisis que aparece solo si tarda más de este tiempo: un cálculo rápido no parpadea con texto. */
private const val AnalysisHintDelayMillis = 600L

/** Eyebrow de una sección dentro de un módulo: mayúsculas, en `textMuted`. */
@Composable
internal fun SheetEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
        modifier = modifier,
    )
}

/** Nota corta en `caption`. */
@Composable
internal fun SheetCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WizardColors.textMuted,
) {
    Text(
        text = text,
        style = KpknType.caption,
        color = color,
        modifier = modifier,
    )
}

/** Marca de texto que va encima de algo `inset`: sin relleno nuevo, con filete de blanco al 12 %. */
@Composable
internal fun SheetTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WizardColors.textMuted,
) {
    Text(
        text = text,
        style = KpknType.chip,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Esqueleto mientras el asistente o las reglas calculan (KPKN_UI_V2): dos bloques de carga. Si el cálculo tarda más de 600 ms, debajo
 * sale «Calculando análisis…» en `caption`.
 */
@Composable
internal fun SheetLoadingSkeleton(modifier: Modifier = Modifier) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(AnalysisHintDelayMillis)
        showHint = true
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(96.dp))
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(72.dp))
        if (showHint) {
            SheetCaption(text = "Calculando análisis…", modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Área de un diálogo del ejercicio (ER1e): llena la pantalla por encima del teclado. El diálogo sigue siendo el de siempre, centrado;
 * solo que su centro es el espacio libre, así sube con el teclado y su campo y su botón quedan a la vista (como hizo SE11 con las hojas).
 * [imeInsets] se cambia en las pruebas para simular el teclado.
 */
@Composable
internal fun EditorDialogImeArea(
    imeInsets: WindowInsets = WindowInsets.ime,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(imeInsets),
    ) {
        content()
    }
}

/** Cifra decimal con coma, como pide la piel («102,5»). Sin decimales, solo la cifra. */
internal fun formatDecimalComma(value: Double?): String = formatEditableNumber(value).replace('.', ',')
