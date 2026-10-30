package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.food.NutritionNotice
import com.example.kpkn.data.food.nutritionNotices
import com.example.kpkn.data.food.readNutritionNotice

/** Hoja «Fuentes y licencias» (aj-fuentes): lista los avisos de datos de nutrición y muestra el texto del que se toque. */
@Composable
fun NutritionSourcesSheet(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<NutritionNotice?>(null) }
    val current = selected
    val body = current?.let { noticeBody(it) }
    AjSheet(onDismiss, tall = true) {
        NutritionSourcesContent(current, body, { selected = it }, { selected = null }, Modifier.weight(1f))
    }
}

@Composable
private fun noticeBody(notice: NutritionNotice): String? {
    val assets = LocalContext.current.applicationContext.assets
    var body by remember(notice) { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        body = runCatching { readNutritionNotice(assets, notice) }.getOrDefault("No se pudo abrir este aviso.")
    }
    return body
}

/** Contenido de la hoja, sin estado: la lista de avisos o, con [current], su texto. */
@Composable
internal fun NutritionSourcesContent(
    current: NutritionNotice?,
    body: String?,
    onSelect: (NutritionNotice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (current == null) {
            AjSheetTitle("Fuentes y licencias")
            AjSheetSub("Los datos de alimentos de KPKN Fit vienen de estas fuentes abiertas. Toca una para leer su aviso.")
            Column(Modifier.verticalScroll(rememberScrollState())) {
                nutritionNotices.forEachIndexed { i, notice ->
                    AjOption(notice.title, false, { onSelect(notice) }, chevron = true, divider = i > 0)
                }
            }
        } else {
            AjSheetTitle(current.title)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(body ?: "Cargando…", style = AjType.mono)
            }
            AjButton("Volver", onBack, tone = AjButtonTone.Ghost, fill = true, tag = "aj-sources-back")
        }
    }
}
