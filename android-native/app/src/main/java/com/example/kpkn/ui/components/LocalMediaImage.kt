package com.example.kpkn.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Miniaturas y vistas previas de medios locales con Coil sin disco en el hilo principal.
 *
 * Con un `File` como modelo, Coil calcula la clave de caché de memoria en Main con
 * `FileKeyer` (`File.lastModified()`), lo que StrictMode marca como DiskReadViolation en
 * cada miniatura. Si la petición trae `memoryCacheKey`, Coil usa esa clave tal cual y no
 * invoca al keyer. Aquí la clave se calcula en `Dispatchers.IO` e incluye `lastModified` y
 * `length`, de modo que reemplazar el archivo en la misma ruta (p. ej. regenerar la
 * miniatura de un medio) sigue invalidando la entrada de la caché.
 */

/** Archivo local para Coil junto con su clave de caché de memoria ya calculada. */
internal data class LocalMediaImageSource(
    val file: File,
    val memoryCacheKey: String,
)

/**
 * Construye la fuente de un archivo local. Lee `lastModified` y `length` (disco): llamar
 * únicamente fuera del hilo principal.
 */
internal fun localMediaImageSource(file: File): LocalMediaImageSource =
    LocalMediaImageSource(
        file = file,
        memoryCacheKey = "kpkn-media:${file.path}:${file.lastModified()}:${file.length()}",
    )

/**
 * Resuelve en IO la [LocalMediaImageSource] de [file]. Devuelve `null` hasta la primera
 * resolución o si [file] es `null`; se recalcula cuando cambia el archivo. Al cambiar de
 * archivo conserva la fuente anterior hasta tener la nueva (Coil mantiene la imagen previa
 * hasta que carga la siguiente, igual que con `model = File`).
 */
@Composable
internal fun rememberLocalMediaImageSource(file: File?): LocalMediaImageSource? {
    val source by produceState<LocalMediaImageSource?>(initialValue = null, key1 = file) {
        value = file?.let { withContext(Dispatchers.IO) { localMediaImageSource(it) } }
    }
    return source
}

/**
 * Imagen de un archivo local con `memoryCacheKey` explícito. Con [source] `null` se comporta
 * como `AsyncImage(model = null)` (no dibuja nada).
 */
@Composable
internal fun LocalMediaImage(
    source: LocalMediaImageSource?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val request = remember(source, context) {
        source?.let {
            ImageRequest.Builder(context)
                .data(it.file)
                .memoryCacheKey(it.memoryCacheKey)
                .build()
        }
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/**
 * Petición para una miniatura identificada por un `uri` inmutable (archivos con nombre
 * UUID copiados a la app): el propio `uri` es la clave de caché y Coil no toca el disco en
 * Main para derivarla.
 */
@Composable
internal fun rememberStableUriImageRequest(uri: String): ImageRequest {
    val context = LocalContext.current
    return remember(uri, context) {
        ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            .memoryCacheKey(uri)
            .build()
    }
}
