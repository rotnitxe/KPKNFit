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
internal const val ADD_SETS_DEFAULT = 3

/** Repeticiones por defecto de un ejercicio añadido: 3 × 10. */
internal const val ADD_REPS_DEFAULT = 10

/**
 * «Añade un ejercicio»: se busca en el catálogo (al tocar un resultado, el nombre queda fijado y la decisión es esa
 * configuración) o se escribe un nombre propio; después, series y repeticiones, y «Añadir». [onAdd] recibe el nombre, la
 * configuración (null = propio) y los valores; quien lo usa decide la operación (añadir a un día o desde un aviso). [key]
 * reinicia el estado al cambiar de día o de aviso. [initialName] y los valores iniciales son lo que propone un aviso;
 * [leading] va bajo el título (los avisos ponen ahí el selector de día). Sus selectores son los del panel de ajuste.
 */
@Composable
internal fun ImportAddExercisePanel(
    key: String,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onAdd: (rawName: String, configId: String?, sets: Int, repsMin: Int, repsMax: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialSets: Int = ADD_SETS_DEFAULT,
    initialRepsMin: Int = ADD_REPS_DEFAULT,
    initialRepsMax: Int = ADD_REPS_DEFAULT,
    leading: @Composable () -> Unit = {},
) {
    // El resultado del catálogo que se eligió (id y nombre), o, si no hay, el nombre propio que se escribe.
    var pickedId by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var pickedName by rememberSaveable(key) { mutableStateOf("") }
    var own by rememberSaveable(key) { mutableStateOf(initialName) }
    var sets by rememberSaveable(key) { mutableStateOf(initialSets) }
    var rangeMode by rememberSaveable(key) { mutableStateOf(initialRepsMin != initialRepsMax) }
    var repsMin by rememberSaveable(key) { mutableStateOf(initialRepsMin) }
    var repsMax by rememberSaveable(key) { mutableStateOf(initialRepsMax) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }

    // Con un resultado del catálogo, su nombre canónico es el del archivo: así la decisión y el nombre coinciden.
    val rawName = if (pickedId != null) pickedName else own.trim()

    fun add() {
        onAdd(rawName, pickedId, sets, repsMin, repsMax)
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
        leading()
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
                key = key,
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
