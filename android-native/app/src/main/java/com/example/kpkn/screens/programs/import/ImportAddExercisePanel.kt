package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Series por defecto de un ejercicio añadido. */
private const val ADD_SETS_DEFAULT = 3

/** Repeticiones por defecto de un ejercicio añadido: 3 × 10. */
private const val ADD_REPS_DEFAULT = 10

/**
 * «Añade un ejercicio» en un día: se busca en el catálogo (al tocar un resultado, el nombre queda fijado y la decisión es esa
 * configuración) o se escribe un nombre propio; después, series y repeticiones, y «Añadir». El ejercicio va al final del día y
 * deja deshacer. Sus selectores son los del panel de ajuste.
 */
@Composable
internal fun ImportAddExercisePanel(
    dayId: String,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onEdit: (ImportEditOp) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // El resultado del catálogo que se eligió (id y nombre), o, si no hay, el nombre propio que se escribe.
    var pickedId by rememberSaveable(dayId) { mutableStateOf<String?>(null) }
    var pickedName by rememberSaveable(dayId) { mutableStateOf("") }
    var own by rememberSaveable(dayId) { mutableStateOf("") }
    var sets by rememberSaveable(dayId) { mutableStateOf(ADD_SETS_DEFAULT) }
    var rangeMode by rememberSaveable(dayId) { mutableStateOf(false) }
    var repsMin by rememberSaveable(dayId) { mutableStateOf(ADD_REPS_DEFAULT) }
    var repsMax by rememberSaveable(dayId) { mutableStateOf(ADD_REPS_DEFAULT) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }

    // Con un resultado del catálogo, su nombre canónico es el del archivo: así la decisión y el nombre coinciden.
    val rawName = if (pickedId != null) pickedName else own.trim()

    fun add() {
        onEdit(ImportEditOp.AddExercise(dayId, rawName, sets, repsMin, repsMax, configId = pickedId, decided = true))
        onClose()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .importGlassPanel()
            .semantics { paneTitle = "Añade un ejercicio" }
            .testTag("import-add-panel")
            .bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Añade un ejercicio",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (pickedId != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ejercicio del catálogo: $pickedName", style = WizardTypography.controlLabel, color = WizardColors.text)
                ImportTextAction(
                    label = "Cambiar",
                    onClick = {
                        pickedId = null
                        pickedName = ""
                    },
                    modifier = Modifier.testTag("import-add-change"),
                )
            }
        } else {
            ImportCatalogSearch(
                key = dayId,
                search = search,
                onPick = { candidate ->
                    pickedId = candidate.configurationId
                    pickedName = candidate.displayName
                    own = ""
                },
            )
            Text("O escribe un nombre propio", style = WizardTypography.note, color = WizardColors.textMuted)
            SetupFormTextField(
                value = own,
                onValueChange = { own = it },
                label = "Nombre",
                testTag = "import-add-name",
            )
        }
        ImportSection(label = "Series") {
            ImportStepper(
                field = "series",
                valueText = "$sets",
                canDecrease = sets > 1,
                canIncrease = sets < ProgramImportParser.MAX_SETS,
                onDecrease = { sets -= 1 },
                onIncrease = { sets += 1 },
                testTag = "import-add-sets",
            )
        }
        ImportSection(label = "Repeticiones") {
            ImportRepsEditor(
                rangeMode = rangeMode,
                repsMin = repsMin,
                repsMax = repsMax,
                onChange = { range, low, high ->
                    rangeMode = range
                    repsMin = low
                    repsMax = high
                },
                tagPrefix = "import-add",
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportCta(
                label = "Añadir",
                enabled = rawName.isNotBlank(),
                onClick = { add() },
                testTag = "import-add-exercise-save",
                modifier = Modifier.weight(1f),
            )
            ImportTextAction(label = "Cancelar", onClick = onClose, modifier = Modifier.testTag("import-add-cancel"))
        }
    }
}
