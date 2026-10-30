package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.sessioneditor.design.CardioTypeArt
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset

private enum class CardioCatalogGroup(val title: String) {
    INDOOR("EN INTERIOR"),
    OUTDOOR("AL AIRE LIBRE"),
    SPRINT("SPRINT Y POTENCIA"),
}

/**
 * Selector de cardio (puntos 5 y 11): la hoja de «Añadir cardio» y de «Cambiar ejercicio de cardio» (`ceAddSheet` de `scenes-ce.js`).
 * Las 12 modalidades, agrupadas por lugar (interior, al aire libre y sprint y potencia), en dos columnas: cada una con su ilustración sobre
 * una ventana, el nombre entero y su descripción completa (sin «…»). El GPS va como etiqueta sobre la ilustración. Tocar una la añade; al
 * cambiar, la que ya tiene el ejercicio lleva una marca ([currentType]).
 */
@Composable
internal fun CardioCatalogSheet(
    isReplacing: Boolean = false,
    currentType: CardioType? = null,
    onDismiss: () -> Unit = {},
    onAdd: (CardioCatalogItem) -> Unit,
) {
    val grouped = CardioCatalog.items.groupBy { it.catalogGroup() }
    CeSheet(onDismiss = onDismiss, modifier = Modifier.pullUp(12.dp), gap = 0.dp, maxHeightFraction = 0.9f) {
        Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CeEyebrow("Cardio", color = CeColors.col)
            Text(
                if (isReplacing) "Cambiar ejercicio de cardio" else "Añadir cardio",
                modifier = Modifier.padding(vertical = 15.77.dp),
                style = edDisplay(19f, FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 21.9.sp),
            )
            Text(
                if (isReplacing) "Selecciona la nueva modalidad." else "Elige la máquina o el lugar. Después eliges cómo programarlo.",
                modifier = Modifier.padding(top = 2.dp),
                style = edText(13.5f, FontWeight.Normal, EdColors.muted, 19.6f),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            CardioCatalogGroup.entries.forEachIndexed { index, group ->
                val groupItems = grouped[group].orEmpty()
                if (groupItems.isEmpty()) return@forEachIndexed
                item(key = "cabecera-${group.name}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 6.dp else 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CeEyebrow(group.title)
                        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.078f)))
                    }
                }
                items(groupItems.chunked(2), key = { row -> "fila-${row.first().id}" }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->
                            CardioCatalogTile(
                                item = item,
                                current = isReplacing && item.type == currentType,
                                onClick = { onAdd(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** `.ce-tile`: la ventana de 112 dp con la ilustración de 84 dp, la etiqueta GPS y el «+» (o la marca de la actual), y debajo el nombre y la descripción. */
@Composable
private fun CardioCatalogTile(
    item: CardioCatalogItem,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${item.name}. ${item.description}" },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(CeColors.module, window)
                .border(1.dp, if (current) EdColors.ink else Color.White.copy(alpha = 0.051f), window)
                // ce-S05 `.ce-tile.cur .ce-win` borde interior de la actual (preset interior-crema-1)
                .then(if (current) Modifier.kpknInset(KpknShadow.InteriorCrema1, window) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CardioTypeArt(type = item.type, size = 84.dp)
            if (item.requiresGps) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(CeColors.col.copy(alpha = 0.133f))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CeIcon(CeIcons.Gps, 12.dp, CeColors.col)
                    Text("GPS en vivo", style = edText(10.5f, FontWeight.SemiBold, CeColors.col, 12f), maxLines = 1)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(EdColors.ink),
                contentAlignment = Alignment.Center,
            ) { CeIcon(if (current) CeIcons.Check else CeIcons.Plus, 14.dp, EdColors.onInk) }
        }
        Text(item.name, modifier = Modifier.padding(horizontal = 2.dp), style = edText(14.5f, FontWeight.SemiBold, EdColors.ink, 18.1f))
        Text(item.description, modifier = Modifier.padding(horizontal = 2.dp), style = edText(12f, FontWeight.Normal, EdColors.faint, 16.8f))
    }
}

private fun CardioCatalogItem.catalogGroup(): CardioCatalogGroup = when (type) {
    CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR, CardioType.WALK -> CardioCatalogGroup.OUTDOOR
    CardioType.AIR_BIKE, CardioType.SLED, CardioType.CURVED_TREADMILL -> CardioCatalogGroup.SPRINT
    else -> CardioCatalogGroup.INDOOR
}

/** Sube el contenido [amount] y recorta ese alto de su medida: `.ce-add{gap:0}` arranca 12 dp antes que el resto de las hojas (sin el hueco del asa y su `gap`). */
private fun Modifier.pullUp(amount: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val up = amount.roundToPx()
    // El hijo mide `up` más de alto y sube `up`: la hoja conserva su alto y su contenido arranca antes.
    val room = if (constraints.hasBoundedHeight) constraints.copy(maxHeight = constraints.maxHeight + up) else constraints
    val placeable = measurable.measure(room)
    layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.placeRelative(0, -up) }
}
