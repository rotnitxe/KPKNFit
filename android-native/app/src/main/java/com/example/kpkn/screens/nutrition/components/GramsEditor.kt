package com.example.kpkn.screens.nutrition.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.GramsSliderSpec
import com.example.kpkn.domain.nutrition.ResolvedTag

/** Gramos que se asumen si la tarjeta aún no trae masa (la tarjeta visible siempre la trae). */
private const val FALLBACK_GRAMS = 100.0

/**
 * Gramos de una tarjeta de alimento: slider + campo numérico (WP-U10 / C8).
 *
 * - El tope del slider sale de un ancla leída UNA vez por tarjeta ([GramsSliderSpec.maxFor]);
 *   nunca del valor que se arrastra, así el pulgar no se escapa.
 * - Mientras se arrastra solo cambia el valor local `pendingGrams`; [onGramsChange] se llama una
 *   vez al soltar (un evento de telemetría y una reinterpretación por gesto, no uno por cuadro).
 * - El campo numérico confirma con «Listo» del teclado, al perder el foco o al ocultarse el teclado
 *   (cerrarlo con «Atrás» deja el foco en el campo y el texto, si no, sin confirmar).
 * - TalkBack lee «Gramos de <alimento>» y «N gramos» en vez de un porcentaje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GramsEditor(
    tag: ResolvedTag,
    onGramsChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentGrams = tag.amountGrams ?: tag.loggedFood?.amount ?: FALLBACK_GRAMS
    val committedGrams = GramsSliderSpec.wholeGrams(currentGrams)
    val gramsMax = remember(tag.id) {
        GramsSliderSpec.maxFor(GramsSliderSpec.anchorFor(tag.baseAmountGrams, currentGrams)).toFloat()
    }
    // Valor del gesto en curso (null = sin arrastre): la tarjeta no reinterpreta hasta soltar.
    var pendingGrams by remember(tag.id) { mutableStateOf<Float?>(null) }
    // Texto a medio escribir (null = el campo muestra los gramos del modelo).
    var typedText by remember(tag.id) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val shownGrams = pendingGrams?.let { GramsSliderSpec.wholeGrams(it.toDouble()) } ?: committedGrams
    val shownLabel = shownGrams.toInt().toString()

    // Un cambio externo (porción, aclaración, otra ficha) descarta lo que quedó a medio escribir.
    LaunchedEffect(committedGrams) { typedText = null }

    fun commitTypedGrams() {
        val raw = typedText ?: return
        typedText = null
        val grams = GramsSliderSpec.parseGrams(raw) ?: return
        if (grams != committedGrams) onGramsChange(grams)
    }

    // «Atrás» oculta el teclado pero el campo conserva el foco: sin esto el número escrito quedaría sin aplicar.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible) commitTypedGrams() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Gramos",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            OutlinedTextField(
                value = typedText ?: shownLabel,
                onValueChange = { typedText = GramsSliderSpec.sanitizeInput(it) },
                modifier = Modifier
                    .width(120.dp)
                    .onFocusChanged { if (!it.isFocused) commitTypedGrams() },
                textStyle = MaterialTheme.typography.bodyMedium,
                suffix = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitTypedGrams()
                        focusManager.clearFocus()
                    },
                ),
            )
        }
        Slider(
            value = shownGrams.toFloat().coerceIn(GramsSliderSpec.MIN_GRAMS.toFloat(), gramsMax),
            onValueChange = {
                typedText = null
                pendingGrams = it
            },
            onValueChangeFinished = {
                val dragged = pendingGrams
                pendingGrams = null
                if (dragged != null) {
                    val grams = GramsSliderSpec.wholeGrams(dragged.toDouble())
                    if (grams != committedGrams) onGramsChange(grams)
                }
            },
            valueRange = GramsSliderSpec.MIN_GRAMS.toFloat()..gramsMax,
            modifier = Modifier.semantics {
                contentDescription = "Gramos de ${tag.tag}"
                stateDescription = "$shownLabel gramos"
            },
        )
    }
}
