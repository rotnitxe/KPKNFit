package com.example.kpkn.screens.programs.importer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programs.MenuDivider
import com.example.kpkn.screens.programs.NewProgramTitleStyle
import com.example.kpkn.screens.programs.ProgramLineIcons
import com.example.kpkn.screens.programs.ProgramSheetRow
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import java.io.File

/** Lo que la persona eligió en la hoja «Importar un programa» de Programas y la pantalla de lectura recoge al abrirse. */
internal sealed interface ImportRequest {
    /** «Elegir archivo»: el documento ya elegido. */
    data class File(val uri: Uri) : ImportRequest

    /** «Foto de tu rutina»: la foto (de la cámara o de la galería) y, si es de la cámara, el archivo temporal que se borra al leerla. */
    data class Photo(val uri: Uri, val takenFile: java.io.File?) : ImportRequest

    /** «Pegar texto»: abre el campo para pegarlo. */
    data object PasteText : ImportRequest
}

/**
 * El relevo entre la hoja de Programas y la pantalla de lectura (`ImportProgramScreen`): la hoja deja aquí lo elegido y navega; la
 * pantalla lo recoge una sola vez al abrirse. Sin nada pendiente, la pantalla muestra la misma hoja por su cuenta.
 */
internal object ImportHandoff {
    @Volatile
    var pending: ImportRequest? = null

    /** Entrega lo pendiente y lo vacía: se recoge una sola vez. */
    fun take(): ImportRequest? = pending.also { pending = null }
}

/** Las dos acciones de la foto: abrir la cámara (a un archivo temporal de la caché) y elegir de la galería. */
private class ImportPhotoActions(val openCamera: () -> Unit, val openGallery: () -> Unit)

@Composable
private fun rememberImportPhotoActions(onPhoto: (photo: Uri, takenFile: File?) -> Unit): ImportPhotoActions {
    val context = LocalContext.current
    // La ruta de la foto que la cámara está escribiendo (guardada para sobrevivir a girar la pantalla).
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingPhoto
        pendingPhoto = null
        if (path != null) {
            val file = File(path)
            if (saved) onPhoto(photoUriOf(context, file), file) else file.delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri, null)
    }
    return ImportPhotoActions(
        openCamera = {
            val dir = File(context.cacheDir, IMPORT_PHOTO_DIR).apply { mkdirs() }
            val file = File(dir, "rutina-${System.currentTimeMillis()}.jpg")
            pendingPhoto = file.path
            try {
                takePicture.launch(photoUriOf(context, file))
            } catch (_: ActivityNotFoundException) {
                // Sin app de cámara no hay foto: se descarta lo preparado.
                pendingPhoto = null
                file.delete()
            }
        },
        openGallery = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}

/**
 * La hoja «Importar un programa» (escena pr-import): tres filas con icono, «Elegir archivo» (Excel, CSV, Word, texto o PDF), «Pegar
 * texto» y «Foto de tu rutina». Esta última cambia el contenido de la hoja a «Hacer una foto» y «Elegir de la galería». Los selectores
 * viven en la hoja, así que ella sigue abierta hasta que llega el resultado: [onFile] y [onPhoto] la cierran por quien la usa. [onNotice]
 * deja un aviso (un error o una nota de la lectura anterior) bajo el subtítulo.
 */
@Composable
internal fun ImportSourceSheet(
    onDismiss: () -> Unit,
    onFile: (Uri) -> Unit,
    onPhoto: (photo: Uri, takenFile: File?) -> Unit,
    onPaste: () -> Unit,
    notice: (@Composable () -> Unit)? = null,
) {
    val pickFile = rememberImportFilePicker(onPicked = onFile)
    val photo = rememberImportPhotoActions(onPhoto)
    var photoStep by rememberSaveable { mutableStateOf(false) }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = if (photoStep) "Foto de tu rutina" else "Importar un programa",
                style = NewProgramTitleStyle,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (photoStep) "Hacer una foto o elegirla de la galería." else "KPKN lo lee y te deja revisarlo antes de guardarlo.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            if (notice != null) {
                notice()
                Spacer(Modifier.height(8.dp))
            }
            if (photoStep) {
                ProgramSheetRow(
                    icon = KpknLineIcons.Camera,
                    title = "Hacer una foto",
                    subtitle = null,
                    onClick = photo.openCamera,
                    modifier = Modifier.testTag("import-photo-camera"),
                )
                MenuDivider()
                ProgramSheetRow(
                    icon = KpknLineIcons.Layers,
                    title = "Elegir de la galería",
                    subtitle = null,
                    onClick = photo.openGallery,
                    modifier = Modifier.testTag("import-photo-gallery"),
                )
            } else {
                ProgramSheetRow(
                    icon = ProgramLineIcons.Upload,
                    title = "Elegir archivo",
                    subtitle = "Excel, CSV, Word, texto o PDF",
                    onClick = pickFile,
                    modifier = Modifier.testTag("import-choose-file"),
                )
                MenuDivider()
                ProgramSheetRow(
                    icon = ProgramLineIcons.Paste,
                    title = "Pegar texto",
                    subtitle = IMPORT_PASTE_EXAMPLE,
                    onClick = onPaste,
                    modifier = Modifier.testTag("import-paste-text"),
                )
                MenuDivider()
                ProgramSheetRow(
                    icon = ProgramLineIcons.Eye,
                    title = "Foto de tu rutina",
                    subtitle = "Hacer una foto o elegirla de la galería",
                    onClick = { photoStep = true },
                    modifier = Modifier.testTag("import-source-photo"),
                )
            }
        }
    }
}

/** La línea de ejemplo de «Pegar texto» en la hoja: un día y un ejercicio. */
internal const val IMPORT_PASTE_EXAMPLE = "Ej.: Día 1 — Pierna / Sentadilla 4x6 @120kg"
