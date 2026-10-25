package com.example.kpkn.screens.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal val storyOptionsSaver = listSaver<WorkoutStoryOptions, Any>(
    save = { listOf(it.template.name,it.exerciseId.orEmpty(),it.improvements,it.duration,it.intensity) },
    restore = {
        val offset=if(it.size==6) 1 else 0 // Old saved UI option for retired rings is discarded.
        WorkoutStoryOptions(WorkoutStoryTemplate.valueOf(it[0] as String),(it[1] as String).ifBlank { null },
            it[2+offset] as Boolean,it[3+offset] as Boolean,it[4+offset] as Boolean)
    },
)
private data class StoryPreviewRequest(val data: WorkoutStoryData, val options: WorkoutStoryOptions)
private data class StoryPreviewResult(val request: StoryPreviewRequest, val bitmap: Bitmap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutStorySheet(
    data: WorkoutStoryData,
    hazeState: HazeState,
    options: WorkoutStoryOptions,
    onOptionsChange: (WorkoutStoryOptions) -> Unit,
    shareAfterSave: Boolean,
    onShareAfterSaveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    renderStory: suspend (Context, WorkoutStoryData, WorkoutStoryOptions) -> Bitmap = ::renderWorkoutStory,
    embedded: Boolean = false,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var preview by remember { mutableStateOf<StoryPreviewResult?>(null) }
    var failedRequest by remember { mutableStateOf<StoryPreviewRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val templates=remember(data.presentation.photos){WorkoutStoryTemplate.entries.filter{it!=WorkoutStoryTemplate.PHOTOS||data.presentation.photos.isNotEmpty()}}
    val safeOptions=options.copy(template=options.template.takeIf{it in templates}?:WorkoutStoryTemplate.GENERAL)
    val request=StoryPreviewRequest(data,safeOptions)
    val image=preview?.takeIf{it.request==request}?.bitmap
    val rendering=image==null&&failedRequest!=request
    LaunchedEffect(request,renderStory){
        message=null
        try {
            val bitmap=renderStory(context.applicationContext,data,safeOptions)
            try{currentCoroutineContext().ensureActive()}catch(error:CancellationException){bitmap.recycle();throw error}
            preview=StoryPreviewResult(request,bitmap);failedRequest=null
        }catch(error:CancellationException){throw error}
        catch(_:Exception){currentCoroutineContext().ensureActive();failedRequest=request;message="No se pudo crear la historia. Inténtalo de nuevo."}
    }
    val neighbours by produceState<List<StoryPreviewResult>>(emptyList(),request,renderStory){
        value=emptyList()
        val others=templates.filter{it!=safeOptions.template}
        val rendered=mutableListOf<StoryPreviewResult>()
        for(template in others){
            val key=StoryPreviewRequest(data,safeOptions.copy(template=template))
            try {
                val bitmap=renderStory(context.applicationContext,data,key.options)
                try{currentCoroutineContext().ensureActive()}catch(error:CancellationException){bitmap.recycle();throw error}
                rendered.add(StoryPreviewResult(key,bitmap));value=rendered.toList()
            }catch(error:CancellationException){throw error}catch(_:Exception){currentCoroutineContext().ensureActive()}
        }
    }
    val save:()->Unit={image?.takeIf{!busy&&!rendering}?.let{bitmap->busy=true;scope.launch{
        try{WorkoutShareService.saveImage(context,bitmap);message="Imagen guardada en Imágenes/KPKN"}
        catch(error:CancellationException){throw error}catch(_:Exception){message="No se pudo guardar la imagen."}finally{busy=false}
    }}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)save()else message="Permite guardar imágenes para añadir esta historia a tu galería. Puedes compartirla directamente."
    }
    val enabled=!busy&&!rendering&&image!=null
    val content: @Composable ()->Unit={
        Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).padding(horizontal=16.dp)
            .semantics(mergeDescendants = true) { paneTitle = "Historia" }.testTag("story-sheet"),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Text("Compartir",style=KpknType.title.copy(fontSize=20.sp),color=FinishCream)
                IconButton(onClick={if(!busy)onDismiss()},enabled=!busy,modifier=Modifier.size(30.dp)){Icon(Icons.Default.Close,"Volver al resumen",Modifier.size(18.dp),tint=FinishCream)}
            }
            // Keep the template group as its own accessible node inside KpknSheet's clickable panel.
            Row(Modifier.fillMaxWidth().height(38.dp).background(Color.Black,RoundedCornerShape(18.dp)).padding(4.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Plantillas de historia" }
                .testTag("story-templates"),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                templates.forEach{template->Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if(template==safeOptions.template)FinishCream else Color.Transparent)
                    .clickable(enabled=!busy){onOptionsChange(safeOptions.copy(template=template))},contentAlignment=Alignment.Center){
                    Text(template.label,style=KpknType.caption.copy(fontSize=12.sp,fontWeight=FontWeight.SemiBold),color=if(template==safeOptions.template)Color(0xFF0B0B0B)else FinishCream.copy(alpha=.62f))
                }}
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(1.dp)),contentAlignment=Alignment.Center){
                val centerWidth=minOf(220.dp,maxHeight*9/16)
                val centerHeight=centerWidth*16/9
                val index=templates.indexOf(safeOptions.template)
                val previous=templates[(index+templates.size-1)%templates.size]
                val next=templates[(index+1)%templates.size]
                Row(Modifier.requiredWidth(centerWidth+172.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                    listOf(previous,safeOptions.template,next).forEachIndexed{position,template->
                        val main=position==1
                        val expected=StoryPreviewRequest(data,safeOptions.copy(template=template))
                        val bitmap=if(main)image else neighbours.firstOrNull{it.request==expected}?.bitmap
                        Box(Modifier.requiredSize(if(main)centerWidth else 74.dp,if(main)centerHeight else 132.dp)
                            .alpha(if(main)1f else .45f).clip(RoundedCornerShape(if(main)22.dp else 14.dp)).background(Color(0xFF120E0B))
                            .clickable(enabled=!busy){if(main&&image!=null)expanded=true else if(!main)onOptionsChange(safeOptions.copy(template=template))},contentAlignment=Alignment.Center){
                            bitmap?.let{Image(it.asImageBitmap(),if(main)"Vista previa de historia ${template.label}" else "Historia ${template.label}",Modifier.fillMaxSize().then(if(main)Modifier.testTag("story-preview")else Modifier),contentScale=ContentScale.Fit)}
                            if(main&&rendering)CircularProgressIndicator(Modifier.size(24.dp),color=FinishCream,strokeWidth=2.dp)
                        }
                    }
                }
            }
            if(safeOptions.template==WorkoutStoryTemplate.EXERCISE)LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                items(data.lifts,key={it.exerciseId}){lift->StoryPill(lift.name,lift.exerciseId==(safeOptions.exerciseId?:data.defaultExerciseId),!busy){onOptionsChange(safeOptions.copy(exerciseId=lift.exerciseId))}}
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("INCLUIR",style=KpknType.eyebrow.copy(fontSize=12.sp),color=FinishMuted)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(safeOptions.template!=WorkoutStoryTemplate.PHOTOS)StoryPill("Mejoras en kg",safeOptions.improvements,!busy){onOptionsChange(safeOptions.copy(improvements=!safeOptions.improvements))}
                    if(safeOptions.template!=WorkoutStoryTemplate.EXERCISE)StoryPill("Duración",safeOptions.duration,!busy){onOptionsChange(safeOptions.copy(duration=!safeOptions.duration))}
                    if(safeOptions.template==WorkoutStoryTemplate.GENERAL)StoryPill("Intensidad media",safeOptions.intensity,!busy){onOptionsChange(safeOptions.copy(intensity=!safeOptions.intensity))}
                }
            }
            Row(Modifier.fillMaxWidth().height(28.dp),verticalAlignment=Alignment.CenterVertically){
                Checkbox(shareAfterSave,onShareAfterSaveChange,enabled=!busy,modifier=Modifier.size(28.dp).testTag("story-share-after-save"),colors=CheckboxDefaults.colors(checkedColor=FinishCream,checkmarkColor=Color.Black,uncheckedColor=FinishMuted))
                Text("Compartir también al guardar la sesión",style=KpknType.caption.copy(fontSize=11.sp),color=FinishMuted)
            }
            message?.let{Text(it,style=KpknType.caption,color=FinishMuted,modifier=Modifier.testTag("story-message"))}
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)else save()},enabled=enabled,
                    modifier=Modifier.height(48.dp).testTag("story-save-image"),contentPadding=PaddingValues(horizontal=14.dp),shape=RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FinishCream.copy(alpha=.12f)),colors=ButtonDefaults.outlinedButtonColors(contentColor=FinishCream)){
                    Text("Guardar imagen",style=KpknType.bodyStrong.copy(fontSize=13.sp))
                }
                Button(onClick={image?.takeIf{!busy&&!rendering}?.let{bitmap->busy=true;scope.launch{
                    try{WorkoutShareService.shareBitmap(context,bitmap)}catch(error:CancellationException){throw error}catch(_:Exception){message="No se pudo abrir la pantalla para compartir."}finally{busy=false}
                }}},enabled=enabled,modifier=Modifier.weight(1f).height(48.dp).testTag("story-share"),contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp),shape=RoundedCornerShape(24.dp),colors=ButtonDefaults.buttonColors(containerColor=FinishCream,contentColor=Color(0xFF0B0B0B))){
                    Text("Compartir en historias",style=KpknType.bodyStrong.copy(fontSize=12.sp,lineHeight=14.sp),maxLines=2,textAlign=TextAlign.Center)
                }
            }
        }
    }
    if(embedded)content()else KpknSheet(onDismissRequest={if(!busy)onDismiss()},stableHeightFraction=.95f,maxHeightFraction=.95f,hazeState=hazeState){content()}
    if(expanded&&image!=null)Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black).clickable{expanded=false},contentAlignment=Alignment.Center){Image(image.asImageBitmap(),"Historia ${safeOptions.template.label}",Modifier.fillMaxSize().padding(16.dp),contentScale=ContentScale.Fit)}
    }
}

@Composable
private fun StoryPill(label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit){
    Box(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if(selected)FinishCream.copy(alpha=.06f)else Color.Transparent)
        .border(1.dp,if(selected)FinishCream else FinishCream.copy(alpha=.12f),RoundedCornerShape(15.dp)).clickable(enabled=enabled,onClick=onClick)
        .padding(horizontal=12.dp).testTag("story-toggle-$label"),contentAlignment=Alignment.Center){Text(label,style=KpknType.caption.copy(fontSize=12.sp),color=FinishCream,maxLines=1)}
}
