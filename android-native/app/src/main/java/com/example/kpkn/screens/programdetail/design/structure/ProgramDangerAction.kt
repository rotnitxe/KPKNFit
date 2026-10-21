package com.example.kpkn.screens.programdetail.design.structure

// `ProgramDangerAction` vive en ProgramSheetParts.kt (P3b); P3c la había definido también aquí y chocaban.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknSurface

/** Separador de 1 dp dentro de un módulo (`divider`, blanco al 6 %). */
@Composable
internal fun ProgramRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
