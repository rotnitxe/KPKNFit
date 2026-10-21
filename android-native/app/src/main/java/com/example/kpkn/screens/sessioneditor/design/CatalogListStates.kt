package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSkeleton

/**
 * Carga del catálogo (SE6a): [rows] filas de `KpknSkeleton` con la forma de una fila (miniatura de 48 dp y dos líneas). Sin estado.
 */
@Composable
internal fun CatalogListSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknSkeleton(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpknSkeleton(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp), shape = KpknShape.pill)
                    KpknSkeleton(modifier = Modifier.fillMaxWidth(0.36f).height(10.dp), shape = KpknShape.pill)
                }
            }
        }
    }
}

/**
 * Sin resultados (SE6a): «No encontramos “<búsqueda>”» dentro de un `KpknEmptyState`, con «Crear ejercicio» como acción principal.
 * [creating] apaga la acción mientras se crea. Sin estado.
 */
@Composable
internal fun CatalogNoResults(
    query: String,
    creating: Boolean,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        KpknEmptyState(
            icon = KpknLineIcons.Search,
            title = "No encontramos “$query”",
            body = null,
            action = {
                KpknPrimaryPill(
                    text = if (creating) "Creando…" else "Crear ejercicio",
                    onClick = onCreateClick,
                    enabled = !creating,
                )
            },
        )
    }
}

/**
 * Error al cargar el catálogo (SE6a): un `KpknEmptyState` con «Reintentar». Sin estado.
 */
@Composable
internal fun CatalogLoadError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        KpknEmptyState(
            icon = KpknLineIcons.Info,
            title = "No se pudo cargar el catálogo de ejercicios.",
            body = null,
            action = { KpknPrimaryPill(text = "Reintentar", onClick = onRetry) },
        )
    }
}
