package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Marca de prueba del símbolo de un lugar: `setup-place-GYM`, `setup-place-HOME`, `setup-place-PUBLIC`. */
internal fun placeSymbolTag(place: TrainingPlace): String = "setup-place-${place.name}"

/** Proporción del lienzo de una escena de lugar (120 × 170 unidades). */
private const val PLACE_ASPECT = 120f / 170f

/** El dibujo ocupa un 96 % del lienzo: el resto es margen para que ningún trazo del borde se recorte al atenuarlo. */
private const val PLACE_ART_SCALE = 0.96f

/**
 * «¿Dónde entrenas?»: tres escenas dibujadas en una fila, sin tarjeta (Gimnasio, En casa y Espacios públicos), de
 * las que se puede marcar una, dos o las tres.
 *
 * Sin marcar, cada escena es un dibujo estático en tinta tenue. Marcada, pasa a tinta plena con el acento de su
 * módulo, hace un pequeño resorte, se anima en bucle (con un reloj COMPARTIDO por toda la fila, y solo mientras
 * haya algo marcado, la fila se vea y la app esté en primer plano) y traza la marca de «hecho». Con
 * «reducir movimiento» no hay bucles: el cuadro estático lleva la tinta y el acento.
 *
 * Cada símbolo es una casilla (`Role.Checkbox`) anunciada como «Gimnasio, seleccionado» y tiene un objetivo
 * táctil que ocupa su tercio de la fila. [onToggle] recibe el lugar tocado; quien llama decide qué hacer con él.
 */
@Composable
fun PlaceSymbolRow(
    selected: Set<TrainingPlace>,
    onToggle: (TrainingPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceSymbolRow(selected, onToggle, wizardReducedMotion(), modifier)
}

/** La fila con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun PlaceSymbolRow(
    selected: Set<TrainingPlace>,
    onToggle: (TrainingPlace) -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val clock = rememberSymbolClock(active = !reduced && foreground && visible && selected.isNotEmpty())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSymbolsVisible { visible = it },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (place in TrainingPlace.entries) {
            SymbolCell(
                art = placeArt(place),
                label = place.label,
                tag = placeSymbolTag(place),
                selected = place in selected,
                clock = clock,
                reducedMotion = reduced,
                onToggle = { onToggle(place) },
                canvasModifier = Modifier.fillMaxWidth().aspectRatio(PLACE_ASPECT),
                labelStyle = WizardTypography.controlLabel,
                artScale = PLACE_ART_SCALE,
                badgeRadius = 9.dp,
                labelGap = 4.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
