package com.example.kpkn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.kpkn.data.programs.ProgramCoverRepository

/**
 * La portada de la entrada [entryId] a [widthPx] de ancho, lista para pintar. Es null si no se pide ninguna, si no hay
 * archivo o mientras carga: quien llama pinta entonces su degradado de siempre. Lee la caché en memoria al instante y, si
 * falta, decodifica en `Dispatchers.IO`.
 */
@Composable
fun rememberProgramCover(entryId: String?, widthPx: Int): ImageBitmap? {
    val repository = ProgramCoverRepository.getInstance(LocalContext.current)
    val cover = remember(repository, entryId, widthPx) {
        mutableStateOf(entryId?.let { repository.peek(it, widthPx) }?.asImageBitmap())
    }
    LaunchedEffect(repository, entryId, widthPx) {
        if (entryId != null && cover.value == null) {
            cover.value = repository.load(entryId, widthPx)?.asImageBitmap()
        }
    }
    return cover.value
}
