package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

@Composable
internal fun CardioCatalogSheet(
    onAdd: (CardioCatalogItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Añadir cardio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
        Text(
            "Elige una modalidad; después puedes editar duración, intensidad y distancia inline.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CardioCatalog.items, key = { it.id }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color.White.copy(alpha = 0.75f))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(item.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            if (item.requiresGps) {
                                Text("GPS opcional en vivo", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FE7B2))
                            }
                        }
                        FilledTonalButton(
                            onClick = { onAdd(item) },
                            colors = kpknSheetWhiteTonalButtonColors(),
                        ) { Text("Añadir") }
                    }
                }
            }
        }
    }
}
