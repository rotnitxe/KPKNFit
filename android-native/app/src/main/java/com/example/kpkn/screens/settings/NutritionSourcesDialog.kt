package com.example.kpkn.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.food.NutritionNotice
import com.example.kpkn.data.food.nutritionNotices
import com.example.kpkn.data.food.readNutritionNotice
import com.example.kpkn.ui.components.KpknAlertDialog

/** Diálogo «Fuentes y licencias»: lista los avisos de datos de nutrición y muestra el texto del que se toque. */
@Composable
fun NutritionSourcesDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<NutritionNotice?>(null) }
    val current = selected
    KpknAlertDialog(
        onDismissRequest = { if (current == null) onDismiss() else selected = null },
        title = { Text(current?.title ?: "Fuentes y licencias") },
        text = {
            if (current == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Los datos de alimentos de KPKN Fit vienen de estas fuentes abiertas. Toca una para leer su aviso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    nutritionNotices.forEach { notice ->
                        Row(Modifier.fillMaxWidth().clickable { selected = notice }.padding(vertical = 12.dp)) {
                            Text(notice.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                NoticeText(current)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        dismissButton = if (current != null) {
            { TextButton(onClick = { selected = null }) { Text("Volver") } }
        } else null,
    )
}

@Composable
private fun NoticeText(notice: NutritionNotice) {
    val assets = LocalContext.current.applicationContext.assets
    var body by remember(notice) { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        body = runCatching { readNutritionNotice(assets, notice) }.getOrDefault("No se pudo abrir este aviso.")
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        Text(
            body ?: "Cargando…",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
