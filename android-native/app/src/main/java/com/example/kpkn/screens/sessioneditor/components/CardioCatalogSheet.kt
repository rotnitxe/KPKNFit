package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens

private enum class CardioCatalogGroup(val title: String) {
    INDOOR("Indoor"),
    OUTDOOR("Outdoor"),
    SPRINT("Sprint / potencia"),
}

@Composable
internal fun CardioCatalogSheet(
    isReplacing: Boolean = false,
    onAdd: (CardioCatalogItem) -> Unit,
) {
    val grouped = CardioCatalog.items.groupBy { it.catalogGroup() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (isReplacing) "Cambiar ejercicio de cardio" else "Añadir cardio",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Text(
            if (isReplacing) "Selecciona la nueva modalidad."
            else "Elige la máquina o el entorno. Después programas con plantillas, no con un diálogo de posición.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardioCatalogGroup.entries.forEach { group ->
                val groupItems = grouped[group].orEmpty()
                if (groupItems.isEmpty()) return@forEach
                item(key = group.name) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(groupItems, key = { it.id }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(KpknSheetTokens.PanelRadius),
                        color = KpknSheetTokens.Panel,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(item.catalogIcon(), contentDescription = null, tint = Color.White.copy(alpha = 0.78f))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(item.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                if (item.requiresGps) {
                                    Text(
                                        "GPS en vivo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            KpknSheetLightChip(
                                label = if (isReplacing) "Cambiar" else "Añadir",
                                selected = false,
                                onClick = { onAdd(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun CardioCatalogItem.catalogGroup(): CardioCatalogGroup = when (type) {
    CardioType.RUN_OUTDOOR, CardioType.BIKE_OUTDOOR, CardioType.WALK -> CardioCatalogGroup.OUTDOOR
    CardioType.AIR_BIKE, CardioType.SLED, CardioType.CURVED_TREADMILL -> CardioCatalogGroup.SPRINT
    else -> CardioCatalogGroup.INDOOR
}

private fun CardioCatalogItem.catalogIcon(): ImageVector = when (type) {
    CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR, CardioType.AIR_BIKE -> Icons.Default.DirectionsBike
    CardioType.WALK -> Icons.Default.DirectionsWalk
    CardioType.STAIR_CLIMBER, CardioType.SLED -> Icons.Default.Terrain
    CardioType.ROW_MACHINE, CardioType.SKI_ERG, CardioType.ELLIPTICAL -> Icons.Default.FitnessCenter
    else -> Icons.Default.DirectionsRun
}
