package com.example.kpkn.screens.sessioneditor.design

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.MuscleInvolvementSection
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogImageVariants
import com.example.kpkn.screens.sessioneditor.components.formatVolumePercent
import com.example.kpkn.screens.workout.components.WorkoutMediaThumb
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import com.example.kpkn.ui.components.rememberStableUriImageRequest
import com.example.kpkn.ui.design.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun SmartExerciseEditorPage(exercise: ExerciseMuscleInfo, onSave: (ExerciseMuscleInfo) -> Unit, onClose: () -> Unit) {
    SmartExerciseForm(initial = exercise, onSave = onSave, onDismiss = onClose)
}

@Composable
internal fun SmartExerciseCreatePage(initialName: String, onSave: (ExerciseMuscleInfo) -> Unit,
    onUseCandidate: (ReferenceCandidate) -> Unit, onClose: () -> Unit) {
    SmartExerciseForm(initial = null, initialName = initialName, onSave = onSave, onDismiss = onClose, onUseCandidate = onUseCandidate)
}

/** E7: el formulario conserva el borrador y solo comunica éxito cuando el repositorio confirma Room. */
@Composable
internal fun SmartExerciseForm(
    initial: ExerciseMuscleInfo?, onSave: (ExerciseMuscleInfo) -> Unit, onDismiss: () -> Unit,
    initialName: String = "", onUseCandidate: ((ReferenceCandidate) -> Unit)? = null,
    editor: CustomExerciseEditorViewModel? = null,
) {
    val key = rememberSaveable(initial?.id, initialName) { initial?.id ?: UUID.randomUUID().toString() }
    val factory = remember(key) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomExerciseEditorViewModel(
            initial, initialName, exerciseCatalogSnapshot(), approvedExerciseCatalogV2(),
            persist = CustomExerciseRepository::upsertAndAwait,
            importPhoto = { selected, info ->
                val seed = WorkoutMedia(id = selected.importId, kind = WorkoutMediaKind.PHOTO, filePath = "",
                    createdAtMs = System.currentTimeMillis(), canonicalExerciseId = info.id, exerciseId = info.id, exerciseName = info.name)
                WorkoutMediaRepository.getInstance().submitUriCapture(Uri.parse(selected.uri), seed).await()
                    ?: error("No se pudo importar la foto")
            },
        ) as T
    } }
    val vm = editor ?: viewModel<CustomExerciseEditorViewModel>(key = "custom-exercise-$key", factory = factory)
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) { state.saved?.let { saved -> vm.consumeSaved(); onSave(saved) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.cover(CustomExerciseCoverDraft.DevicePhoto(it.toString())) }
    }
    var privatePicker by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<WorkoutMedia>>(emptyList()) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(privatePicker) {
        if (privatePicker) {
            val result = withContext(Dispatchers.IO) { runCatching {
                val repository = WorkoutMediaRepository.getInstance()
                repository.listAll().filter { it.kind == WorkoutMediaKind.PHOTO && File(it.filePath).isFile && repository.isManagedPath(File(it.filePath)) }
            } }
            photos = result.getOrDefault(emptyList())
            libraryError = result.exceptionOrNull()?.let { "No se pudo abrir el almacenamiento privado. Inténtalo de nuevo." }
        }
    }
    CustomExerciseEditorContent(state = state, editing = initial != null,
        onEdit = vm::edit, onReview = vm::review, onCover = vm::cover, onSave = vm::save,
        onCancel = { if (!state.saving) onDismiss() }, onUseCandidate = onUseCandidate,
        onPickDevice = { picker.launch(arrayOf("image/*")) }, onPickPrivate = { privatePicker = true })
    if (privatePicker) KpknSheet(onDismissRequest = { privatePicker = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tus fotos privadas", style = KpknType.title, color = WizardColors.text)
            libraryError?.let { Text(it, style = KpknType.body, color = WizardColors.danger) }
            if (photos.isEmpty() && libraryError == null) Text("Todavía no hay fotos privadas disponibles.", style = KpknType.body, color = WizardColors.textMuted)
            photos.forEach { media ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) {
                    vm.cover(CustomExerciseCoverDraft.PrivatePhoto(media)); privatePicker = false
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WorkoutMediaThumb(media, Modifier.size(56.dp))
                    Text(media.caption ?: media.exerciseName ?: "Foto privada", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
                }
            }
            KpknTextAction("Cancelar", onClick = { privatePicker = false })
        }
    }
}

/** Misma regla MOT de compatibilidad; la disponibilidad de imagen es un hecho externo, no otra búsqueda. */
internal fun customExerciseReferenceCover(preview: SmartCreatePreview?,
    imageAvailable: (String, String) -> Boolean = { definition, configuration -> exerciseCatalogImageVariants(definition, configuration).isNotEmpty() },
): ExerciseCover? = preview?.let { current -> ExerciseCoverResolver.suggestReferenceCover(current.exercise,
    referenceImplementId = current.reference?.equipment?.let(SmartExerciseCreator::implementoIdFromLabel), catalogImageAvailable = imageAvailable) }

/** Cuerpo sin repositorios ni lanzadores; las pruebas usan los mismos controles y callbacks de producción. */
@Composable
internal fun CustomExerciseEditorContent(
    state: CustomExerciseEditorState, editing: Boolean,
    onEdit: ((CustomExerciseEditorState) -> CustomExerciseEditorState) -> Unit,
    onReview: (Boolean) -> Unit, onCover: (CustomExerciseCoverDraft) -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit, onUseCandidate: ((ReferenceCandidate) -> Unit)?,
    onPickDevice: () -> Unit, onPickPrivate: () -> Unit,
    referenceCover: ExerciseCover? = customExerciseReferenceCover(state.preview),
) {
    var manual by remember { mutableStateOf(false) }
    val preview = state.preview
    val info = preview?.exercise

    Column(Modifier.fillMaxSize().background(KpknSurface.canvas).imePadding().testTag("custom-exercise-editor")) {
        KpknTopRow(onBack = onCancel)
        Text(if (editing) "Editar ejercicio" else "Crear ejercicio", style = KpknType.title, color = WizardColors.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("custom-exercise-editor-scroll"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KpknTextField(value = state.name, onValueChange = { value -> onEdit { it.copy(name = value) } }, label = "Nombre del ejercicio",
                modifier = Modifier.testTag("custom-exercise-name"))
            preview?.referenceResult?.let { result ->
                if (state.candidates.isNotEmpty()) {
                    Text("Ya existe algo parecido", style = KpknType.bodyStrong, color = WizardColors.text)
                    state.candidates.forEach { candidate ->
                        Row(Modifier.fillMaxWidth().testTag("custom-reference-${candidate.definitionId}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name, style = KpknType.bodyStrong, color = WizardColors.text)
                                Text(candidate.reasons.joinToString(" · "), style = KpknType.caption, color = WizardColors.textMuted)
                            }
                            if (onUseCandidate != null) KpknTextAction(if (candidate.configurationId != null) "Usar" else "Ver opciones",
                                onClick = { if (!state.saving) onUseCandidate(candidate) })
                        }
                    }
                }
                Text(result.reason, style = KpknType.caption,
                    color = if (result.outcome == ReferenceOutcome.CONFIRMED) WizardColors.textMuted else SymbolPalette.energia,
                    modifier = Modifier.testTag("custom-reference-reason"))
            }
            KpknModule(contentPadding = PaddingValues(12.dp)) {
                Text("Imagen del ejercicio", style = KpknType.bodyStrong, color = WizardColors.text)
                CustomExerciseDraftCover(state, info)
                // Cuatro fuentes: documento, foto privada, referencia compatible o patrón.
                KpknTextAction("Añadir foto", onClick = onPickDevice, modifier = Modifier.testTag("custom-cover-device"))
                KpknTextAction("Elegir foto privada", onClick = onPickPrivate, modifier = Modifier.testTag("custom-cover-private"))
                if (referenceCover != null) KpknTextAction("Usar la del parecido", onClick = { onCover(CustomExerciseCoverDraft.Reference(referenceCover)) },
                    modifier = Modifier.testTag("custom-cover-reference"))
                KpknTextAction("Usar ilustración del patrón", onClick = { onCover(CustomExerciseCoverDraft.Pattern) },
                    modifier = Modifier.testTag("custom-cover-pattern"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Descripción", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
                KpknTextAction("Regenerar", onClick = { onEdit { it.copy(description = null) } })
            }
            KpknTextField(value = state.description ?: info?.description.orEmpty(), onValueChange = { value -> onEdit { it.copy(description = value) } },
                label = "Descripción editable", placeholder = "Describe el ejercicio si no hay una configuración confirmada",
                singleLine = false, minLines = 3, maxLines = 8, modifier = Modifier.testTag("custom-exercise-description"))
            Text(if (state.description != null) "Descripción editada por ti" else if (preview?.referenceResult?.outcome == ReferenceOutcome.CONFIRMED)
                "Generada solo desde la configuración confirmada" else "Sin descripción automática: falta una configuración confirmada.",
                style = KpknType.caption, color = WizardColors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomOptionField("Material", state.implement, SmartExerciseCreator.IMPLEMENTO_IDS, SmartExerciseCreator::implementoLabel,
                    { value -> onEdit { it.copy(implement = value) } }, Modifier.weight(1f))
                CustomOptionField("Estación", state.station, SmartExerciseCreator.ESTACION_IDS, SmartExerciseCreator::estacionLabel,
                    { value -> onEdit { it.copy(station = value) } }, Modifier.weight(1f))
            }
            CustomOptionField("Lateralidad", state.laterality, SmartExerciseCreator.LATERALIDAD_IDS, SmartExerciseCreator::lateralidadLabel,
                { value -> onEdit { it.copy(laterality = value) } }, Modifier.fillMaxWidth())
            Text("Patrón · ${info?.movementPattern ?: "Por confirmar"}", style = KpknType.body, color = WizardColors.text)
            if (info != null && info.involvedMuscles.isNotEmpty()) MuscleInvolvementSection(info)
            else Text("Músculos por completar: el nombre no confirma una configuración.", style = KpknType.caption, color = WizardColors.textMuted)
            KpknTextAction(if (manual) "Ocultar ajuste manual" else "Ajustar involucramiento muscular", onClick = { manual = !manual })
            if (manual) ManualMuscleEditor(state.muscles ?: info?.involvedMuscles.orEmpty()) { muscles -> onEdit { it.copy(muscles = muscles) } }
            KpknCheckRow(checked = state.reviewed, title = "He revisado material, patrón y músculos",
                onClick = { onReview(!state.reviewed) }, enabled = !state.saving && state.preview != null, modifier = Modifier.testTag("custom-exercise-review"))
            state.error?.let { Text(it, style = KpknType.body, color = WizardColors.danger, modifier = Modifier.testTag("custom-exercise-save-error")) }
        }
        Row(Modifier.fillMaxWidth().background(KpknSurface.module).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            KpknTextAction("Cancelar", onClick = onCancel, modifier = Modifier.testTag("custom-exercise-cancel"))
            KpknPrimaryPill(text = when { state.saving -> "Guardando…"; state.error != null -> "Reintentar"; editing -> "Guardar ejercicio"; else -> "Crear ejercicio" },
                onClick = onSave, enabled = state.reviewed && state.preview != null && !state.saving && state.name.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("custom-exercise-save"))
        }
    }
}

@Composable
private fun CustomOptionField(title: String, value: String?, options: List<String>, label: (String) -> String,
    onSelect: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.heightIn(min = 64.dp).clip(KpknShape.module).background(KpknSurface.inset)
        .clickable(role = Role.Button) { open = true }.padding(12.dp).testTag("custom-option-$title")) {
        Text(title, style = KpknType.caption, color = WizardColors.textMuted)
        Text(value?.let(label) ?: "Por confirmar", style = KpknType.bodyStrong, color = WizardColors.text)
    }
    if (open) KpknSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(title, style = KpknType.title, color = WizardColors.text)
            options.forEach { option -> KpknRadioRow(selected = value == option, title = label(option), onClick = { onSelect(option); open = false }) }
            KpknTextAction("Cancelar", onClick = { open = false })
        }
    }
}

@Composable
private fun CustomExerciseDraftCover(state: CustomExerciseEditorState, info: ExerciseMuscleInfo?) {
    val context = LocalContext.current
    val coverInfo = info?.copy(cover = when (val cover = state.cover) {
        CustomExerciseCoverDraft.Keep -> info.cover
        CustomExerciseCoverDraft.Pattern -> ExerciseCover(patternId = ExerciseCoverResolver.patternIdFor(info))
        is CustomExerciseCoverDraft.Reference -> cover.cover
        is CustomExerciseCoverDraft.PrivatePhoto -> ExerciseCover.userPhoto(cover.media.id)
        is CustomExerciseCoverDraft.DevicePhoto -> null
    })
    val model by produceState(CustomCoverModel(), coverInfo) { if (coverInfo != null) value = customExerciseCoverModel(context, coverInfo) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(76.dp).clip(KpknShape.module).background(KpknSurface.inset), contentAlignment = Alignment.Center) {
            val device = state.cover as? CustomExerciseCoverDraft.DevicePhoto
            when {
                device != null -> AsyncImage(model = rememberStableUriImageRequest(device.uri), contentDescription = "Foto elegida del dispositivo", modifier = Modifier.fillMaxSize())
                model.photoFile != null -> LocalMediaImage(rememberLocalMediaImageSource(model.photoFile), "Foto privada elegida", Modifier.fillMaxSize(), ContentScale.Crop)
                model.imageResId != null -> Image(painterResource(model.imageResId!!), "Imagen de referencia", Modifier.fillMaxSize())
                else -> PatternGlyph(exercisePatternOf(state.name), SymbolPalette.musculo, Modifier.size(48.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(if (state.cover is CustomExerciseCoverDraft.DevicePhoto) "Foto del dispositivo · pendiente de guardar" else model.provenance,
                style = KpknType.caption, color = WizardColors.textMuted)
            model.unavailableReason?.let { Text(it, style = KpknType.caption, color = SymbolPalette.energia) }
        }
    }
}

private val MANUAL_MUSCLE_OPTIONS = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo",
    "Cuádriceps", "Isquiosurales", "Glúteos", "Core", "Abdomen", "Trapecio",
    "Romboides", "Erectores Espinales", "Pantorrillas", "Aductores",
)

/**
 * Ajuste manual del involucramiento (SE6b): cada músculo en su módulo, con sus papeles como `KpknChip` y su aporte en `KpknNumberCell`:
 * un arrastre o los gestos de accesibilidad suben o bajan 5 puntos. Abajo, los músculos que faltan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualMuscleEditor(
    muscles: List<InvolvedMuscle>,
    onChange: (List<InvolvedMuscle>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Involucramiento manual",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        muscles.forEachIndexed { index, muscle ->
            val volume = (muscle.volumeContribution ?: resolveMuscleVolumeContribution(muscle)).toFloat()
            val percent = formatVolumePercent(volume.toDouble())
            KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = muscle.muscle,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        KpknTextAction(
                            text = "Quitar",
                            onClick = { onChange(muscles.filterIndexed { i, _ -> i != index }) },
                            color = WizardColors.textMuted,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MuscleRole.PRIMARY to "Primario",
                            MuscleRole.SECONDARY to "Secundario",
                            MuscleRole.STABILIZER to "Estabilizador",
                        ).forEach { (role, label) ->
                            KpknChip(
                                text = label,
                                selected = muscle.role == role,
                                onClick = {
                                    onChange(muscles.toMutableList().also { it[index] = muscle.copy(role = role) })
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Aporte por 1 serie",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        KpknNumberCell(
                            value = percent,
                            input = KpknCellInput.ReadOnly,
                            contentDescription = "Aporte de ${muscle.muscle}: $percent",
                            onStep = { step ->
                                onChange(
                                    muscles.toMutableList().also {
                                        it[index] = muscle.copy(volumeContribution = stepVolume(volume, step))
                                    },
                                )
                            },
                            modifier = Modifier.width(88.dp),
                        )
                    }
                }
            }
        }
        val available = MANUAL_MUSCLE_OPTIONS.filter { option ->
            muscles.none { it.muscle.equals(option, ignoreCase = true) }
        }
        if (available.isNotEmpty()) {
            Text(
                text = "Agregar músculo",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                available.forEach { option ->
                    KpknChip(
                        text = option,
                        selected = false,
                        onClick = { onChange(muscles + InvolvedMuscle(option, MuscleRole.SECONDARY, 0.5)) },
                    )
                }
            }
        }
    }
}

/** Un paso de aporte: 5 puntos por cada paso, entre 0 y 1, redondeado a centésimas. */
private fun stepVolume(current: Float, direction: Int): Double =
    ((current + direction * 0.05f).coerceIn(0f, 1f) * 100f).roundToInt() / 100.0
