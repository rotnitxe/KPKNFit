package com.example.kpkn.screens.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import com.example.kpkn.ui.design.depth.kpknFilter
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
    save = { listOf("v2", it.template.name, it.exerciseId.orEmpty(), it.improvements, it.duration, it.intensity, it.exerciseList) },
    restore = {
        if (it.firstOrNull() == "v2" && it.size >= 7) {
            WorkoutStoryOptions(
                WorkoutStoryTemplate.valueOf(it[1] as String),
                (it[2] as String).ifBlank { null },
                it[3] as Boolean,
                it[4] as Boolean,
                it[5] as Boolean,
                it[6] as Boolean,
            )
        } else {
            val offset = if (it.size == 6) 1 else 0
            WorkoutStoryOptions(
                WorkoutStoryTemplate.valueOf(it[0] as String),
                (it[1] as String).ifBlank { null },
                it[2 + offset] as Boolean,
                it[3 + offset] as Boolean,
                it[4 + offset] as Boolean,
            )
        }
    },
)
private data class StoryPreviewRequest(val data: WorkoutStoryData, val options: WorkoutStoryOptions)
private data class StoryPagesResult(val request: StoryPreviewRequest, val bitmaps: List<Bitmap>)

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
    renderStoryPages: suspend (Context, WorkoutStoryData, WorkoutStoryOptions) -> List<Bitmap> = ::renderWorkoutStoryPages,
    embedded: Boolean = false,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var pagesResult by remember { mutableStateOf<StoryPagesResult?>(null) }
    var currentPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var failedRequest by remember { mutableStateOf<StoryPreviewRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val templates=remember(data.presentation.photos){WorkoutStoryTemplate.entries.filter{it!=WorkoutStoryTemplate.PHOTOS||data.presentation.photos.isNotEmpty()}}
    val safeOptions=options.copy(template=options.template.takeIf{it in templates}?:WorkoutStoryTemplate.GENERAL)
    val request=StoryPreviewRequest(data,safeOptions)
    val bitmaps=pagesResult?.takeIf{it.request==request}?.bitmaps.orEmpty()
    val safePageIndex=if(bitmaps.isEmpty()) 0 else currentPageIndex.coerceIn(0, bitmaps.lastIndex)
    val image=bitmaps.getOrNull(safePageIndex)
    val rendering=image==null&&failedRequest!=request
    LaunchedEffect(request,renderStoryPages){
        message=null
        try {
            val list=renderStoryPages(context.applicationContext,data,safeOptions)
            try{currentCoroutineContext().ensureActive()}catch(error:CancellationException){list.forEach{it.recycle()};throw error}
            pagesResult=StoryPagesResult(request,list);failedRequest=null
        }catch(error:CancellationException){throw error}
        catch(_:Exception){currentCoroutineContext().ensureActive();failedRequest=request;message="No se pudo crear la historia. Inténtalo de nuevo."}
    }
    val save:()->Unit={bitmaps.takeIf{!busy&&!rendering&&it.isNotEmpty()}?.let{list->busy=true;scope.launch{
        try{
            list.forEach { WorkoutShareService.saveImage(context, it) }
            message=if(list.size>1) "${list.size} imágenes guardadas en Imágenes/KPKN" else "Imagen guardada en Imágenes/KPKN"
        }
        catch(error:CancellationException){throw error}catch(_:Exception){message="No se pudo guardar la imagen."}finally{busy=false}
    }}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)save()else message="Permite guardar imágenes para añadir esta historia a tu galería. Puedes compartirla directamente."
    }
    val enabled=!busy&&!rendering&&bitmaps.isNotEmpty()
    val rendering0 = com.example.kpkn.ui.design.motion.rememberKpknPose(com.example.kpkn.screens.workout.LxSpinAnimation)
    // Miniaturas de las plantillas: la propia historia a baja resolución (una por plantilla), en cuanto se pueden dibujar.
    val thumbs by produceState(emptyMap<WorkoutStoryTemplate, androidx.compose.ui.graphics.ImageBitmap>(), data, templates) {
        templates.forEach { template ->
            val small = try {
                val full = renderStoryPages(context.applicationContext, data, WorkoutStoryOptions(template = template, exerciseId = safeOptions.exerciseId, exerciseList = safeOptions.exerciseList)).first()
                Bitmap.createScaledBitmap(full, 96, 171, true).also { if (it !== full) full.recycle() }
            } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
            if (small != null) value = value + (template to small.asImageBitmap())
        }
    }
    val content: @Composable ()->Unit={
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)
            .semantics(mergeDescendants = true) { paneTitle = "Historia" }.testTag("story-sheet"),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth().padding(top = 2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Text("Compartir",style=androidx.compose.ui.text.TextStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.5).sp),color=FinishCream)
                Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x0DFFFFFF)).clickable(enabled = !busy){onDismiss()},contentAlignment=Alignment.Center){
                    Icon(Icons.Default.Close,"Volver al resumen",Modifier.size(20.dp),tint=FinishCream)
                }
            }
            // La vista previa es la protagonista: 9:16 a casi todo el alto, con un resplandor suave detrás (`lx-V03`, `lx-G02`) y la sombra de «historia» (`lx-S08`).
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Resplandor suave detrás de la vista previa: `inset: 6% 10%`, desenfoque de 26 dp y degradado crema (`lx-V03`, `lx-G02`).
                Box(Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.88f).kpknFilter(com.example.kpkn.ui.design.depth.KpknFilterPreset.ResplandorDesenfoque)
                    .kpknGradient(com.example.kpkn.ui.design.depth.KpknGradient.ResplandorCrema, androidx.compose.ui.graphics.RectangleShape))
                Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true).testTag("story-preview-frame"), contentAlignment = Alignment.Center) {
                    val previewShape = RoundedCornerShape(20.dp)
                    Box(
                        Modifier.fillMaxSize().kpknShadow(com.example.kpkn.ui.design.depth.KpknShadow.Historia, previewShape).clip(previewShape).background(Color(0xFF0A0A0A))
                            .clickable(enabled = !busy) { if (image != null) expanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        image?.let {
                            Image(it.asImageBitmap(), "Vista previa de historia ${safeOptions.template.label}", Modifier.fillMaxSize().testTag("story-preview"), contentScale = ContentScale.Fit)
                        }
                        if (rendering) Canvas(Modifier.size(26.dp).graphicsLayer { rotationZ = rendering0.pose().rotationDeg }) {
                            val w = 2.5.dp.toPx()
                            val box = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                            drawArc(Color(0x33F2EEE6), 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = box, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                            drawArc(FinishCream, -135f, 90f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = box, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                        } else if (failedRequest == request) Icon(com.example.kpkn.ui.components.icons.KpknLineIcons.Info, null, Modifier.size(24.dp), tint = FinishMuted)
                    }
                }
            }
            if (bitmaps.size > 1) {
                Row(
                    Modifier.fillMaxWidth().testTag("story-pagination"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x1AFFFFFF))
                            .clickable(enabled = !busy && safePageIndex > 0) { currentPageIndex = safePageIndex - 1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Página anterior", Modifier.size(20.dp), tint = if (safePageIndex > 0) FinishCream else FinishMuted)
                    }
                    Text(
                        "Página ${safePageIndex + 1} de ${bitmaps.size}",
                        style = com.example.kpkn.screens.workout.components.Lx.inter(12.sp, FontWeight.Medium),
                        color = FinishCream,
                    )
                    Box(
                        Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x1AFFFFFF))
                            .clickable(enabled = !busy && safePageIndex < bitmaps.lastIndex) { currentPageIndex = safePageIndex + 1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Página siguiente", Modifier.size(20.dp), tint = if (safePageIndex < bitmaps.lastIndex) FinishCream else FinishMuted)
                    }
                }
            }
            // Plantillas como miniaturas de la propia historia (`lx-tpl`): 84 dp de ancho, la elegida con anillo de tinta.
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Plantillas de historia" }.testTag("story-templates"),
                horizontalArrangement=Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)){
                templates.forEach{template->
                    val on = template==safeOptions.template
                    val thumbShape = RoundedCornerShape(7.dp)
                    Column(Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled=!busy){onOptionsChange(safeOptions.copy(template=template))}.padding(top = 6.dp, bottom = 2.dp)
                        .semantics { selected = on }.testTag("story-template-${template.name}"),
                        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)){
                        val thumbAlpha by com.example.kpkn.ui.design.motion.animateKpknFloatAsState(if (on) 1f else 0.7f)
                        Box(Modifier.width(32.dp).height(57.dp).graphicsLayer { alpha = thumbAlpha }
                            .kpknShadow(if (on) com.example.kpkn.ui.design.depth.KpknShadow.AnilloActual else com.example.kpkn.ui.design.depth.KpknShadow.BordeInterior, thumbShape)
                            .clip(thumbShape).background(Color(0xFF0A0A0A))){
                            thumbs[template]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Text(template.label,style=com.example.kpkn.screens.workout.components.Lx.inter(12.sp, FontWeight.SemiBold),color=if(on)FinishCream else FinishMuted)
                    }
                }
            }
            if(safeOptions.template==WorkoutStoryTemplate.EXERCISE)FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(6.dp)){
                data.lifts.forEach{lift->StoryPill(lift.name,lift.exerciseId==(safeOptions.exerciseId?:data.defaultExerciseId),!busy){onOptionsChange(safeOptions.copy(exerciseId=lift.exerciseId))}}
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("INCLUIR",style=com.example.kpkn.screens.workout.components.Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp),color=FinishMuted)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    if(safeOptions.template!=WorkoutStoryTemplate.PHOTOS)StoryPill("Mejoras en kg",safeOptions.improvements,!busy){onOptionsChange(safeOptions.copy(improvements=!safeOptions.improvements))}
                    if(safeOptions.template!=WorkoutStoryTemplate.EXERCISE)StoryPill("Duración",safeOptions.duration,!busy){onOptionsChange(safeOptions.copy(duration=!safeOptions.duration))}
                    if(safeOptions.template==WorkoutStoryTemplate.GENERAL)StoryPill("sRPE declarado",safeOptions.intensity,!busy){onOptionsChange(safeOptions.copy(intensity=!safeOptions.intensity))}
                    if(safeOptions.template==WorkoutStoryTemplate.GENERAL)StoryPill("Ejercicios",safeOptions.exerciseList,!busy){onOptionsChange(safeOptions.copy(exerciseList=!safeOptions.exerciseList))}
                }
            }
            Text(message ?: "",Modifier.fillMaxWidth().heightIn(min = 16.dp).testTag("story-message"),style=com.example.kpkn.screens.workout.components.Lx.inter(12.sp, line = 15.6.sp),color=FinishMuted,textAlign=TextAlign.Center)
            // «Guardar imagen» pasa a botón SOLO con icono (con etiqueta para los lectores de pantalla) para que «Compartir en historias» quepa entero.
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(52.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }.clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x0FFFFFFF))
                    .border(1.dp, Color(0x1FFFFFFF), androidx.compose.foundation.shape.CircleShape)
                    .clickable(enabled=enabled,role=androidx.compose.ui.semantics.Role.Button){if(Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)else save()}
                    .semantics { contentDescription = "Guardar imagen" }.testTag("story-save-image"),contentAlignment=Alignment.Center){
                    Icon(com.example.kpkn.screens.workout.components.FreeClockIcons.Download,null,Modifier.size(22.dp),tint=FinishCream)
                }
                Row(Modifier.weight(1f).height(52.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }.clip(RoundedCornerShape(50)).background(FinishCream)
                    .clickable(enabled=enabled,role=androidx.compose.ui.semantics.Role.Button){bitmaps.takeIf{!busy&&!rendering&&it.isNotEmpty()}?.let{list->busy=true;scope.launch{
                        try{WorkoutShareService.shareBitmaps(context,list)}catch(error:CancellationException){throw error}catch(_:Exception){message="No se pudo abrir la pantalla para compartir."}finally{busy=false}
                    }}}.padding(horizontal = 12.dp).testTag("story-share"),
                    horizontalArrangement=Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically){
                    Icon(com.example.kpkn.screens.workout.components.FreeClockIcons.Share,null,Modifier.size(17.dp),tint=Color(0xFF111111))
                    Text("Compartir en historias",style=com.example.kpkn.screens.workout.components.Lx.inter(15.sp, FontWeight.SemiBold, line = 16.5.sp),color=Color(0xFF111111),textAlign=TextAlign.Center)
                }
            }
        }
    }
    if(embedded)content()else KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, onDismissRequest={if(!busy)onDismiss()},stableHeightFraction=.95f,maxHeightFraction=.95f,hazeState=hazeState){content()}
    if(expanded&&image!=null)Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black).clickable{expanded=false},contentAlignment=Alignment.Center){Image(image.asImageBitmap(),"Historia ${safeOptions.template.label}",Modifier.fillMaxSize().padding(16.dp),contentScale=ContentScale.Fit)}
    }
}

@Composable
private fun StoryPill(label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit){
    // `.lx-chip`: 40 dp, píldora; la activa en tinta.
    Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if(selected)FinishCream else Color(0x0BFFFFFF))
        .clickable(enabled=enabled,onClick=onClick).semantics { this.selected = selected }
        .padding(horizontal=12.dp).testTag("story-toggle-$label"),contentAlignment=Alignment.Center){
        Text(label,style=com.example.kpkn.screens.workout.components.Lx.num(13.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium),color=if(selected)Color(0xFF111111)else FinishMuted,maxLines=1)}
}
