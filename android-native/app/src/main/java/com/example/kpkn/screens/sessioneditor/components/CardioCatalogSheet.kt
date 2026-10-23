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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.design.CardioTypeIcon
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

private enum class CardioCatalogGroup(val title: String) {
    INDOOR("EN INTERIOR"),
    OUTDOOR("AL AIRE LIBRE"),
    SPRINT("SPRINT Y POTENCIA"),
}

/**
 * Selector de cardio (ED-D · C7): la hoja de «Añadir cardio». Las máquinas salen en una rejilla de tres columnas, agrupadas por
 * lugar (interior, al aire libre y sprint), con el disco de la máquina, su insignia de «+», el nombre y su descripción. Tocar
 * cualquiera de ellas la añade a la sesión.
 */
@Composable
internal fun CardioCatalogSheet(
    isReplacing: Boolean = false,
    onAdd: (CardioCatalogItem) -> Unit,
) {
    val grouped = CardioCatalog.items.groupBy { it.catalogGroup() }
    val accent = SymbolPalette.columna
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("CARDIO", style = KpknType.eyebrow, color = accent)
            Text(
                if (isReplacing) "Cambiar ejercicio de cardio" else "Añadir cardio",
                style = KpknType.dialogTitle,
                color = WizardColors.text,
            )
            Text(
                if (isReplacing) "Selecciona la nueva modalidad."
                else "Elige la máquina o el lugar. Después eliges cómo programarlo.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            CardioCatalogGroup.entries.forEach { group ->
                val groupItems = grouped[group].orEmpty()
                if (groupItems.isEmpty()) return@forEach
                item(key = "cabecera-${group.name}") {
                    CardioCatalogGroupHeader(title = group.title)
                }
                item(key = "rejilla-${group.name}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groupItems.chunked(CardioCatalogColumns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowItems.forEach { item ->
                                    CardioCatalogTile(
                                        item = item,
                                        accent = accent,
                                        onClick = { onAdd(item) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(CardioCatalogColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Columnas de la rejilla de máquinas. */
private const val CardioCatalogColumns = 3

/** El rótulo del grupo y su filete, que se estira hasta el borde. */
@Composable
private fun CardioCatalogGroupHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = KpknType.eyebrow, color = WizardColors.textMuted)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )
    }
}

/** Una máquina: su disco con la insignia de «+», el nombre y su descripción. */
@Composable
private fun CardioCatalogTile(
    item: CardioCatalogItem,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CardioTypeIcon(type = item.type, tint = accent, size = 24.dp)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Plus,
                    contentDescription = null,
                    tint = WizardColors.ctaContent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.description,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.requiresGps) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = KpknLineIcons.Route,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(11.dp),
                )
                Text("GPS en vivo", style = KpknType.caption, color = accent)
            }
        }
    }
}

private fun CardioCatalogItem.catalogGroup(): CardioCatalogGroup = when (type) {
    CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR, CardioType.WALK -> CardioCatalogGroup.OUTDOOR
    CardioType.AIR_BIKE, CardioType.SLED, CardioType.CURVED_TREADMILL -> CardioCatalogGroup.SPRINT
    else -> CardioCatalogGroup.INDOOR
}
