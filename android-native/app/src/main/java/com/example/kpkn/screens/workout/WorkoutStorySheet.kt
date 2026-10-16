package com.example.kpkn.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val storyOptionsSaver = listSaver<WorkoutStoryOptions, Any>(
    save = { listOf(it.template.name,it.exerciseId.orEmpty(),it.rings,it.improvements,it.duration,it.intensity) },
    restore = { WorkoutStoryOptions(WorkoutStoryTemplate.valueOf(it[0] as String),(it[1] as String).ifBlank { null },it[2] as Boolean,it[3] as Boolean,it[4] as Boolean,it[5] as Boolean) },
)

@Composable
internal fun WorkoutStorySheet(
    data: WorkoutStoryData,
    hazeState: HazeState,
    options: WorkoutStoryOptions,
    onOptionsChange: (WorkoutStoryOptions) -> Unit,
    shareAfterSave: Boolean,
    onShareAfterSaveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val templates = remember(data.presentation.photos) { WorkoutStoryTemplate.entries.filter { it != WorkoutStoryTemplate.PHOTOS || data.presentation.photos.isNotEmpty() } }
    val safeOptions = options.copy(template = options.template.takeIf { it in templates } ?: WorkoutStoryTemplate.GENERAL)
    LaunchedEffect(data, safeOptions) {
        rendering = true
        try { image = renderWorkoutStory(context.applicationContext,data,safeOptions); message = null }
        catch(error:CancellationException) { throw error }
        catch(_:Exception) { image = null; message = "No se pudo crear la historia. Inténtalo de nuevo." }
        finally { rendering = false }
    }
    val save: () -> Unit = {
        image?.let { bitmap ->
            scope.launch {
                busy=true
                try { WorkoutShareService.saveImage(context,bitmap); message="Imagen guardada en Imágenes/KPKN" }
                catch(error:CancellationException) { throw error }
                catch(_:Exception) { message="No se pudo guardar la imagen." }
                finally { busy=false }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) save() else message="Permite guardar imágenes para añadir esta historia a tu galería. Puedes compartirla directamente."
    }
    val enabled = !busy && !rendering
    KpknSheet(onDismissRequest={ if(!busy) onDismiss() },stableHeightFraction=0.92f,hazeState=hazeState) {
        Column(Modifier.fillMaxSize().padding(horizontal=18.dp).testTag("story-sheet"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Compartir la sesión",style=KpknType.title,color=WizardColors.text)
            KpknSegmentedControl(options=templates.map { it.label },selectedIndex=templates.indexOf(safeOptions.template),
                onSelect={ if(enabled) onOptionsChange(safeOptions.copy(template=templates[it])) },size=KpknSegmentedSize.Compact,modifier=Modifier.fillMaxWidth().testTag("story-templates"))
            if(safeOptions.template==WorkoutStoryTemplate.EXERCISE) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                items(data.lifts,key={it.exerciseId}) { lift -> FilterChip(selected=lift.exerciseId==(safeOptions.exerciseId ?: data.defaultExerciseId),
                    onClick={onOptionsChange(safeOptions.copy(exerciseId=lift.exerciseId))},enabled=enabled,label={Text(lift.name)}) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                image?.let { Image(it.asImageBitmap(),"Vista previa de historia ${safeOptions.template.label}",
                    modifier=Modifier.fillMaxHeight().aspectRatio(9f/16f).testTag("story-preview"),contentScale=ContentScale.Fit) }
                if(rendering) CircularProgressIndicator(Modifier.size(28.dp),color=WizardColors.text)
            }
            Text("INCLUIR",style=KpknType.eyebrow,color=WizardColors.textMuted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                StoryToggle("RINGS",safeOptions.rings,enabled) { onOptionsChange(safeOptions.copy(rings=it)) }
                StoryToggle("Mejoras en kg",safeOptions.improvements,enabled) { onOptionsChange(safeOptions.copy(improvements=it)) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                StoryToggle("Duración",safeOptions.duration,enabled) { onOptionsChange(safeOptions.copy(duration=it)) }
                StoryToggle("Intensidad media",safeOptions.intensity,enabled) { onOptionsChange(safeOptions.copy(intensity=it)) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=shareAfterSave,onCheckedChange=onShareAfterSaveChange,enabled=enabled,modifier=Modifier.testTag("story-share-after-save"))
                Text("Compartir también al guardar la sesión",style=KpknType.caption,color=WizardColors.textMuted)
            }
            message?.let { Text(it,style=KpknType.caption,color=WizardColors.textMuted,modifier=Modifier.testTag("story-message")) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={
                    if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                },enabled=enabled&&image!=null,modifier=Modifier.weight(1f).testTag("story-save-image")) { Text("Guardar imagen") }
                Button(onClick={ image?.let { bitmap -> scope.launch {
                    busy=true
                    try { WorkoutShareService.shareBitmap(context,bitmap) }
                    catch(error:CancellationException) { throw error }
                    catch(_:Exception) { message="No se pudo abrir la pantalla para compartir." }
                    finally { busy=false }
                } } },enabled=enabled&&image!=null,modifier=Modifier.weight(1f).testTag("story-share")) { Text("Compartir en historias") }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RowScope.StoryToggle(label:String,selected:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    FilterChip(selected=selected,onClick={onChange(!selected)},enabled=enabled,label={Text(label,style=KpknType.caption)},
        modifier=Modifier.weight(1f).testTag("story-toggle-$label"))
}
