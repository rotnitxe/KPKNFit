package com.example.kpkn.screens.programs.importer

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.kpkn.data.importer.PROGRAM_IMPORT_MIME_TYPES
import com.example.kpkn.data.importer.programTitleOf
import com.example.kpkn.data.importer.readSheets
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.EquipmentArt
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.programSourceArtOf
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanNotice
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanNoticeAction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Periodo de un dibujo estático: tan largo que el reloj compartido nunca vuelve a empezar. */
private const val STATIC_ART_PERIOD = 86_400f

/** Texto de ejemplo del campo «Pegar texto» (cada línea, un día o un ejercicio). */
internal const val IMPORT_TEXT_EXAMPLE = "Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10"

/** Carpeta de caché de las fotos de la cámara (la declara `provider_paths.xml`). Cada foto se borra al leerla. */
private const val IMPORT_PHOTO_DIR = "import_photos"

/** Lo que se muestra mientras se lee un archivo o un texto. */
internal const val IMPORT_READING_TEXT = "Leyendo el programa…"

/** Lo que se muestra mientras se lee una foto. */
internal const val IMPORT_READING_PHOTO_TEXT = "Leyendo la foto…"

/** Lo que se muestra mientras se lee un PDF: la página que se está leyendo. */
internal fun importReadingPageText(page: Int, pages: Int): String = "Leyendo la página $page de $pages…"

/** Un portapapeles de tablero con sujeción y tres renglones: dibujo estático (no se anima). */
internal class ImportClipboardArt : EquipmentArt(SymbolPalette.mente, STATIC_ART_PERIOD, 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.box(16f, 12f, 48f, 58f, 4f, pen.soft, SymbolStroke.LINE)
        pen.box(24f, 7f, 40f, 17f, 2.5f, pen.accent, SymbolStroke.LINE)
        pen.line(23f, 26f, 41f, 26f, pen.soft, SymbolStroke.FINE)
        pen.line(23f, 34f, 41f, 34f, pen.soft, SymbolStroke.FINE)
        pen.line(23f, 42f, 36f, 42f, pen.soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = Unit
}

/**
 * Una cámara delante de una hoja con renglones: dibujo estático (no se anima), con la cámara en el acento de la columna. Lo
 * que la hoja tiene detrás se tapa con el negro de la página.
 */
internal class ImportPhotoArt : EquipmentArt(SymbolPalette.columna, STATIC_ART_PERIOD, 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.box(8f, 10f, 40f, 56f, 3f, pen.soft, SymbolStroke.LINE)
        pen.line(14f, 22f, 34f, 22f, pen.soft, SymbolStroke.FINE)
        pen.line(14f, 30f, 34f, 30f, pen.soft, SymbolStroke.FINE)
        pen.line(14f, 38f, 28f, 38f, pen.soft, SymbolStroke.FINE)
        pen.fillBox(30f, 19f, 42f, 27f, 2f, WizardColors.background)
        pen.fillBox(22f, 26f, 58f, 56f, 5f, WizardColors.background)
        pen.box(30f, 19f, 42f, 27f, 2f, pen.accent, SymbolStroke.LINE)
        pen.box(22f, 26f, 58f, 56f, 5f, pen.accent, SymbolStroke.LINE)
        pen.ring(40f, 41f, 8f, pen.accent, SymbolStroke.LINE)
        pen.dot(40f, 41f, 3f, pen.accent)
        pen.dot(51f, 33f, 1.8f, pen.accent)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = Unit
}

/**
 * Las formas de traer un programa: «Elegir archivo» (Excel, CSV, Word, texto o PDF), «Pegar texto» (el portapapeles) y «Foto de tu
 * rutina», que despliega debajo «Hacer una foto» y «Elegir de la galería». Son símbolos sobre la página, sin tarjetas. [onPhoto]
 * recibe la foto y, si es de la cámara, el archivo temporal que se borra al terminar de leerla.
 */
@Composable
internal fun ImportSourceChoices(
    onChooseFile: () -> Unit,
    onPaste: () -> Unit,
    onPhoto: (photo: Uri, takenFile: File?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val context = LocalContext.current
    // Las acciones de la foto salen bajo su símbolo; elegir otra forma las pliega.
    var photoOpen by rememberSaveable { mutableStateOf(false) }
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
    val openCamera: () -> Unit = {
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
    }
    val choices = listOf(
        StartingPointChoice(
            art = programSourceArtOf(ProgramSource.IMPORT, reduced),
            label = "Elegir archivo",
            tag = "import-choose-file",
            selected = false,
            description = "Elegir un archivo de Excel, CSV, Word, texto o PDF",
            role = Role.Button,
            onChoose = {
                photoOpen = false
                onChooseFile()
            },
        ),
        StartingPointChoice(
            art = ImportClipboardArt(),
            label = "Pegar texto",
            tag = "import-paste-text",
            selected = false,
            description = "Pegar el texto del programa",
            role = Role.Button,
            onChoose = {
                photoOpen = false
                onPaste()
            },
        ),
        StartingPointChoice(
            art = ImportPhotoArt(),
            label = "Foto de tu rutina",
            tag = "import-source-photo",
            // Desplegar las acciones no es elegir: sin «selected», el símbolo no lleva el check de «hecho».
            selected = false,
            description = "Hacer una foto de tu rutina o elegirla de la galería",
            role = Role.Button,
            onChoose = { photoOpen = !photoOpen },
        ),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartingPointGrid(
            choices = choices,
            columns = choices.size,
            canvas = SCENE_CANVAS,
            artScale = SCENE_ART_SCALE,
            labelStyle = WizardTypography.controlLabel,
        )
        if (photoOpen) {
            Column(modifier = Modifier.fillMaxWidth().testTag("import-photo-actions")) {
                ImportTextAction(
                    label = "Hacer una foto",
                    onClick = openCamera,
                    modifier = Modifier.testTag("import-photo-camera"),
                )
                ImportTextAction(
                    label = "Elegir de la galería",
                    onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("import-photo-gallery"),
                )
            }
        }
    }
}

/** La uri de [file] para la cámara, por el proveedor de la app (la carpeta que declara `provider_paths.xml`). */
private fun photoUriOf(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * La línea de lectura: lo que se está leyendo (la página de un PDF, «Leyendo la foto…» o «Leyendo el programa…») y «Cancelar»,
 * que corta la lectura y deja la pantalla como estaba.
 */
@Composable
internal fun ImportReadingNotice(text: String, onCancel: () -> Unit) {
    EntrenoPlanNotice(
        text = text,
        error = false,
        actions = listOf(EntrenoPlanNoticeAction(label = "Cancelar", tag = "import-cancel", onClick = onCancel)),
    )
}

/**
 * El campo multilínea de «Pegar texto», con el ejemplo en gris, y su acción «Leer el texto» (sin texto, no hace nada).
 */
@Composable
internal fun ImportPasteField(
    text: String,
    onTextChange: (String) -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Texto del programa") },
            placeholder = { Text(IMPORT_TEXT_EXAMPLE, style = WizardTypography.note, color = WizardColors.textMuted) },
            minLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WizardColors.cardFill,
                unfocusedContainerColor = WizardColors.cardFill,
                focusedTextColor = WizardColors.text,
                unfocusedTextColor = WizardColors.text,
                focusedLabelColor = WizardColors.textMuted,
                unfocusedLabelColor = WizardColors.textMuted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .testTag("import-paste-field"),
        )
        ImportTextAction(
            label = "Leer el texto",
            enabled = text.isNotBlank(),
            onClick = onRead,
            modifier = Modifier.testTag("import-read-text"),
        )
    }
}

/**
 * Abre el selector de Excel, CSV, Word, texto, PDF o fotos. [onPicked] recibe el archivo elegido; devuelve la acción que lo abre.
 */
@Composable
internal fun rememberImportFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(PROGRAM_IMPORT_MIME_TYPES) }
}

/**
 * Lee un archivo elegido en IO: su título (nombre sin extensión) y sus hojas. Un PDF se lee con OCR y avisa de cada página por
 * [onProgress]; [cacheDir] es donde se copia un PDF que el proveedor no deja abrir. Un fallo sale con el texto para la persona.
 */
internal suspend fun readPickedFile(
    contentResolver: ContentResolver,
    uri: Uri,
    cacheDir: File,
    onProgress: (page: Int, pages: Int) -> Unit = { _, _ -> },
): Result<Pair<String, List<ImportSheet>>> {
    val mime = withContext(Dispatchers.IO) { contentResolver.getType(uri) }
    val title = programTitleOf(contentResolver, uri)
    return readSheets(contentResolver, uri, mime, cacheDir, onProgress).map { sheets -> title to sheets }
}
