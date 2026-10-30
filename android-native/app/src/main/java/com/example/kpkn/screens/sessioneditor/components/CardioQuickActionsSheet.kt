package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.screens.sessioneditor.design.CardioThumb
import androidx.compose.ui.platform.testTag

/*
 * Pulido v1 · CARDIO DEL EDITOR · punto 9: el menú de pulsación larga de un cardio (`ceMenuSheet` de `scenes-ce.js`). Las acciones del
 * ejercicio de fuerza no valen aquí: «Ficha del cardio» (la ficha propia), «Cambiar máquina o lugar» (conserva la programación), «Duplicar»,
 * «Pasar el cardio antes o después de la fuerza» (solo si la sesión tiene los dos espacios) y «Eliminar» (borrado blando con «Deshacer»).
 * Se van «+ Aproximación» y «+ Movilidad» (series que el cardio en vivo nunca reproduce), «Crear superserie» (el cardio va en su espacio) y
 * la descripción (pasa a la ficha); el subtítulo es la dosis real del cardio, no «1 × 8 · 2:00».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardioQuickActionsSheet(
    exercise: Exercise,
    canMovePlacement: Boolean,
    cardioFirst: Boolean,
    onOpenFicha: () -> Unit,
    onChange: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePlacement: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val details = exercise.cardioDetails ?: return
    val item = remember(details.type) { CardioCatalog.findByType(details.type) }
    val form = when (CardioRepeatGrammar.shape(details)) {
        is CardioAuthoringShape.Steady -> "Continuo"
        is CardioAuthoringShape.Uniform -> "Series"
        is CardioAuthoringShape.Irregular -> "Intervalos"
    }
    CeSheet(onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CardioThumb(details.type, tile = 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name.ifBlank { item?.name.orEmpty() }, style = edText(18f, FontWeight.SemiBold, EdColors.ink, 23.4f))
                Text(cardioDoseLine(details), style = edText(13f, FontWeight.Normal, EdColors.muted, 16.9f, true))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MenuTag("Cardio")
            MenuTag(if (item?.requiresGps == true) "Exterior · GPS" else "Interior")
            MenuTag(form)
        }
        Column(Modifier.fillMaxWidth()) {
            CeMenuRow(CeIcons.Guide, "Ficha del cardio", "Qué es, cómo medir el esfuerzo y formatos.", first = true, onClick = onOpenFicha)
            CeMenuRow(CeIcons.Swap, "Cambiar máquina o lugar", "Mantiene tu programación.", onClick = onChange)
            CeMenuRow(CeIcons.Copy, "Duplicar", "La copia queda justo debajo.", onClick = onDuplicate)
            if (canMovePlacement) {
                CeMenuRow(
                    icon = if (cardioFirst) CeIcons.Down else CeIcons.Up,
                    title = if (cardioFirst) "Pasar el cardio después de la fuerza" else "Pasar el cardio antes de la fuerza",
                    sub = if (cardioFirst) "Ahora lo haces al inicio de la sesión." else "Ahora lo haces al final de la sesión.",
                    onClick = onTogglePlacement,
                )
            }
        }
        val danger = Color(0xFFFF9B92)
        val shape = RoundedCornerShape(26.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(danger.copy(alpha = 0.063f), shape)
                .border(1.dp, danger.copy(alpha = 0.333f), shape)
                .clickable(role = Role.Button, onClick = onDelete)
                .testTag("quick-action-eliminar")
                .semantics { contentDescription = "Eliminar" },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CeIcon(CeIcons.Trash, 20.dp, danger)
            Text("Eliminar", style = edText(15.5f, FontWeight.SemiBold, danger, 19f))
        }
    }
}

@Composable
private fun MenuTag(text: String) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.122f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = edText(12.5f, FontWeight.Medium, EdColors.ink, 16f), maxLines = 1)
    }
}
