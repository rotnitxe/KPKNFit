package com.example.kpkn.screens.programs.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Periodo de un dibujo estático: tan largo que el reloj compartido nunca vuelve a empezar. */
private const val STATIC_ART_PERIOD = 86_400f

/** Texto de ejemplo del campo «Pegar texto» (cada línea, un día o un ejercicio). */
internal const val IMPORT_TEXT_EXAMPLE = "Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10"

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
 * Las dos formas de traer un programa: «Elegir archivo» (Excel o CSV, con el símbolo de la hoja) y «Pegar texto» (el
 * portapapeles). Son símbolos sobre la página, sin tarjetas.
 */
@Composable
internal fun ImportSourceChoices(onChooseFile: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val choices = listOf(
        StartingPointChoice(
            art = programSourceArtOf(ProgramSource.IMPORT, reduced),
            label = "Elegir archivo",
            tag = "import-choose-file",
            selected = false,
            description = "Elegir un archivo de Excel, CSV o texto",
            role = Role.Button,
            onChoose = onChooseFile,
        ),
        StartingPointChoice(
            art = ImportClipboardArt(),
            label = "Pegar texto",
            tag = "import-paste-text",
            selected = false,
            description = "Pegar el texto del programa",
            role = Role.Button,
            onChoose = onPaste,
        ),
    )
    StartingPointGrid(
        choices = choices,
        columns = choices.size,
        canvas = SCENE_CANVAS,
        artScale = SCENE_ART_SCALE,
        labelStyle = WizardTypography.controlLabel,
        modifier = modifier,
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
 * Abre el selector de Excel, CSV o texto. [onPicked] recibe el archivo elegido; devuelve la acción que lo abre.
 */
@Composable
internal fun rememberImportFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(PROGRAM_IMPORT_MIME_TYPES) }
}

/**
 * Lee un archivo elegido en IO: su título (nombre sin extensión) y sus hojas. Un fallo sale con el texto para la persona.
 */
internal suspend fun readPickedFile(contentResolver: ContentResolver, uri: Uri): Result<Pair<String, List<ImportSheet>>> {
    val mime = withContext(Dispatchers.IO) { contentResolver.getType(uri) }
    val title = programTitleOf(contentResolver, uri)
    return readSheets(contentResolver, uri, mime).map { sheets -> title to sheets }
}
