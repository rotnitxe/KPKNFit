package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSkeletonModule

/**
 * Carga del editor (SESSION_EDITOR_V1 §6): un bloque de cabecera y dos módulos en relleno `inset` que brillan, sobre el negro. Sustituye
 * al indicador de progreso suelto.
 */
@Composable
fun SessionEditorLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknSkeleton(
            modifier = Modifier
                .width(132.dp)
                .height(32.dp),
            shape = KpknShape.pill,
        )
        Spacer(Modifier.height(4.dp))
        // Dos líneas de título, como el nombre de la sesión, y no un bloque que parezca un botón.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KpknSkeleton(modifier = Modifier.fillMaxWidth(0.85f).height(30.dp), shape = KpknShape.pill)
            KpknSkeleton(modifier = Modifier.fillMaxWidth(0.55f).height(30.dp), shape = KpknShape.pill)
        }
        KpknSkeleton(
            modifier = Modifier
                .width(220.dp)
                .height(16.dp),
            shape = KpknShape.pill,
        )
        KpknSkeletonModule(lines = 3, modifier = Modifier.fillMaxWidth())
        KpknSkeletonModule(lines = 4, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Error de carga del editor (SESSION_EDITOR_V1 §6): el motivo en texto llano dentro de un `KpknEmptyState`, con «Volver» y «Reintentar».
 */
@Composable
fun SessionEditorLoadError(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        KpknEmptyState(
            icon = KpknLineIcons.Info,
            title = message,
            body = "Puedes reintentar la carga o volver al programa.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknSecondaryPill(text = "Volver", onClick = onBack)
                    KpknPrimaryPill(text = "Reintentar", onClick = onRetry)
                }
            },
        )
    }
}
