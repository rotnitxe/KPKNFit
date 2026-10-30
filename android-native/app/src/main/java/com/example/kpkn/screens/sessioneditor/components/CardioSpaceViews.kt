package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.sessioneditor.design.CardioTypeArt

/*
 * Pulido v1 · CARDIO DEL EDITOR · el espacio de cardio de la lista (`ceCardioSec` de `scenes-ce.js`): su rótulo con la flecha de la posición y
 * el «+» del alta (puntos 11 y 26) y su estado vacío.
 */

/**
 * `.ce-sec.ce-sec-c`: «CARDIO · AL FINAL» en el azul del cardio, su filete y, a la derecha, dos botones redondos y pequeños: la flecha que lo pasa
 * antes (o después) de la fuerza —solo si la sesión tiene fuerza y cardio ([canMove])— y el «+» que abre «Añadir cardio».
 */
@Composable
internal fun CardioSpaceLabel(
    canMove: Boolean,
    cardioFirst: Boolean,
    onMove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `margin-top: 6px` fuera de la altura mínima de 48 dp del rótulo.
            .padding(start = 4.dp, end = 4.dp, top = 6.dp)
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (canMove) (if (cardioFirst) "CARDIO · AL INICIO" else "CARDIO · AL FINAL") else "CARDIO",
            style = EdEyebrowStyle.copy(color = CeColors.col, letterSpacing = 1.5.sp, fontSize = 11.sp),
            maxLines = 1,
        )
        Box(Modifier.weight(1f).widthIn(min = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.078f)))
        if (canMove) {
            CeRoundIcon(
                icon = if (cardioFirst) CeIcons.Down else CeIcons.Up,
                description = if (cardioFirst) "Pasar el cardio después de la fuerza" else "Pasar el cardio antes de la fuerza",
                enabled = true,
                onClick = onMove,
            )
        }
        CeRoundIcon(CeIcons.Plus, "Añadir cardio", enabled = true, onClick = onAdd)
    }
}

/**
 * `.ce-mod.empty`: el módulo sin ningún cardio. No se queda en blanco con un texto suelto: dice qué falta y ofrece «Elegir cardio» (el único sitio
 * donde un botón de alta vive dentro del módulo, porque no hay ninguna tarjeta que lo contradiga).
 */
@Composable
internal fun CardioEmptyModule(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CeColors.well, shape)
            .border(1.dp, Color.White.copy(alpha = 0.043f), shape)
            .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(bottom = 4.dp)) { CardioTypeArt(CardioType.TREADMILL, 72.dp) }
        Text("Todavía sin cardio", style = edDisplay(19f, FontWeight.Bold, EdColors.ink, -0.3f).copy(lineHeight = 22.sp), textAlign = TextAlign.Center)
        Text(
            "Elige una máquina o un lugar y después decides cómo programarlo.",
            modifier = Modifier.padding(bottom = 10.dp).widthIn(max = 260.dp),
            style = edText(13.5f, FontWeight.Normal, EdColors.muted, 20.3f),
            textAlign = TextAlign.Center,
        )
        val pill = RoundedCornerShape(26.dp)
        Row(
            modifier = Modifier
                .height(52.dp)
                .clip(pill)
                .background(EdColors.ink, pill)
                .clickable(role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "Elegir cardio" }
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CeIcon(CeIcons.Plus, 16.dp, EdColors.onInk)
            Text("Elegir cardio", style = edText(15f, FontWeight.SemiBold, EdColors.onInk, 19f), maxLines = 1)
        }
    }
}
