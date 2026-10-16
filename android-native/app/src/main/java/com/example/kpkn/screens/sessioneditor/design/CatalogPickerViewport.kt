package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Único dueño del inset inferior de resultados y pies; respeta lo ya consumido por el anfitrión. */
@Composable
internal fun ColumnScope.CatalogPickerViewport(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), content = content)
}
