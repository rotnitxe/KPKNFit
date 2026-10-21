package com.example.kpkn.screens.workout

import com.example.kpkn.domain.performance.StrengthModel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.data.programs.ProgramCoverRepository
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.ui.components.rings.TowerRingValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal enum class WorkoutStoryTemplate(val label: String) { GENERAL("General"), EXERCISE("Ejercicio"), PHOTOS("Fotos") }
internal data class WorkoutStoryOptions(
    val template: WorkoutStoryTemplate = WorkoutStoryTemplate.GENERAL,
    val exerciseId: String? = null,
    val rings: Boolean = true,
    val improvements: Boolean = true,
    val duration: Boolean = true,
    val intensity: Boolean = true,
)
internal data class WorkoutStoryData(
    val sessionName: String,
    val presentation: FinishPresentationContext,
    val durationMinutes: Int,
    val volume: Double,
    val sets: Int,
    val averageRir: Double?,
    val intensity: String,
    val rings: TowerRingValues,
    val lifts: List<FinishLiftProgress>,
) {
    val defaultExerciseId: String? get() = (lifts.filter { it.isImproved }.maxByOrNull { it.deltaKg ?: 0.0 } ?: lifts.firstOrNull())?.exerciseId
}

/** Same native 360×640 composition at 3× for preview, MediaStore and chooser; no HTML or invented data. */
internal suspend fun renderWorkoutStory(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions): Bitmap = withContext(Dispatchers.Default) {
    val background=storyBackground(context,data,options.template)
    val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
    val blurred=blurStoryBackground(background)
    try {
        val canvas=Canvas(bitmap)
        canvas.drawBitmap(background,0f,0f,null)
        canvas.scale(3f,3f)
        val draw=StoryDrawing(canvas,blurred,context)
        if(options.template!=WorkoutStoryTemplate.PHOTOS)draw.header(data)
        when(options.template){
            WorkoutStoryTemplate.GENERAL->draw.general(data,options)
            WorkoutStoryTemplate.EXERCISE->draw.exercise(data,options)
            WorkoutStoryTemplate.PHOTOS->draw.photos(context,data,options)
        }
        val footer=listOfNotNull(data.presentation.program?.name?.uppercase(),data.presentation.weekNumber?.let{"SEMANA $it"}).joinToString(" · ")
        if(footer.isNotBlank())draw.text(footer,180f,618f,11f,align=Paint.Align.CENTER,color=MUTED,maxWidth=324f)
        bitmap
    } catch(error:Throwable){bitmap.recycle();throw error}
    finally{blurred.recycle();background.recycle()}
}

private const val CREAM=0xFFF2EEE6.toInt()
private const val MUTED=0xFFB5B1AC.toInt()
private const val GREEN=0xFF7FE3B0.toInt()
private const val PEACH=0xFFF49A6E.toInt()
private const val GOLD=0xFFF7CF73.toInt()

private class StoryDrawing(val canvas:Canvas,val blurred:Bitmap,context:Context){
    private val headingFont=androidx.core.content.res.ResourcesCompat.getFont(context,com.example.kpkn.R.font.syne_bold)
    private val displayFont=androidx.core.content.res.ResourcesCompat.getFont(context,com.example.kpkn.R.font.syne_extrabold)
    private val bodyFont=androidx.core.content.res.ResourcesCompat.getFont(context,com.example.kpkn.R.font.wizard_body_regular)
    private val strongFont=androidx.core.content.res.ResourcesCompat.getFont(context,com.example.kpkn.R.font.wizard_body_semibold)
    private val brand=ContextCompat.getDrawable(context,com.example.kpkn.R.drawable.kpkn_logo_horizontal)?.mutate()?.apply{setTint(CREAM)}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val locale=Locale.forLanguageTag("es-CL")
    fun text(value:String,x:Float,y:Float,size:Float,bold:Boolean=false,color:Int=CREAM,align:Paint.Align=Paint.Align.LEFT,maxWidth:Float=288f,display:Boolean=false){
        paint.reset();paint.isAntiAlias=true;paint.color=color;paint.textSize=size
        paint.typeface=if(display)displayFont else if(bold&&size>=20f)headingFont else if(bold)strongFont else bodyFont
        paint.textAlign=align
        var label=value.replace('\n',' ')
        if(paint.measureText(label)>maxWidth){while(label.isNotEmpty()&&paint.measureText("$label…")>maxWidth)label=label.dropLast(1);label+="…"}
        canvas.drawText(label,x,y,paint)
    }
    private fun logo(x:Float,y:Float,width:Float=64f){
        brand?.let{drawable->
            val height=width*drawable.intrinsicHeight/drawable.intrinsicWidth
            canvas.save();canvas.translate(x,y+(20f-height)/2)
            canvas.scale(width/drawable.intrinsicWidth,height/drawable.intrinsicHeight)
            drawable.setBounds(0,0,drawable.intrinsicWidth,drawable.intrinsicHeight);drawable.draw(canvas);canvas.restore()
        }
    }
    private fun date(data:WorkoutStoryData)=data.presentation.today.format(DateTimeFormatter.ofPattern("EEEE d MMM",locale)).replaceFirstChar{if(it.isLowerCase())it.titlecase(locale)else it.toString()}
    fun header(data:WorkoutStoryData){logo(18f,56f);text(date(data),342f,71f,12f,color=CREAM,align=Paint.Align.RIGHT,maxWidth=180f)}
    fun glass(rect:RectF,radius:Float=26f){
        canvas.save();canvas.clipPath(Path().apply{addRoundRect(rect,radius,radius,Path.Direction.CW)})
        paint.reset();paint.isFilterBitmap=true;canvas.drawBitmap(blurred,null,RectF(0f,0f,360f,640f),paint)
        canvas.drawColor(Color.argb(97,18,16,14));canvas.restore();glassBorder(rect,radius)
    }
    private fun glassBorder(rect:RectF,radius:Float){paint.reset();paint.isAntiAlias=true;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;paint.color=0x24FFFFFF;canvas.drawRoundRect(rect,radius,radius,paint)}
    private fun rings(data:WorkoutStoryData){
        canvas.save();canvas.translate(36f,182f);canvas.scale(96f/170f,104f/190f)
        val channels=listOf(arrayOf(150f,78f,26f,data.rings.columna,0xFF8FB2FF.toInt()),arrayOf(98f,58f,20f,data.rings.musculos,PEACH),arrayOf(52f,38f,14f,data.rings.energia,GOLD))
        channels.forEach{v->
            val cy=v[0] as Float;val rx=v[1] as Float;val ry=v[2] as Float;val value=v[3] as Int?;val color=v[4] as Int
            val rect=RectF(85-rx,cy-ry,85+rx,cy+ry)
            paint.reset();paint.isAntiAlias=true;paint.style=Paint.Style.STROKE;paint.strokeWidth=12f;paint.strokeCap=Paint.Cap.ROUND;paint.color=0x24FFFFFF;canvas.drawOval(rect,paint)
            if(value!=null){paint.color=color;paint.setShadowLayer(4f,0f,0f,color);canvas.drawArc(rect,-90f,360f*value.coerceIn(0,100)/100f,false,paint);paint.clearShadowLayer()}
        };canvas.restore()
    }
    fun general(data:WorkoutStoryData,options:WorkoutStoryOptions){
        val visible=data.lifts.take(7)
        val rowHeight=if(visible.size>5)26f else 28f
        val bottom=max(360f,306f+visible.size*rowHeight+18f+if(data.lifts.size>7)18f else 0f).coerceAtMost(584f)
        glass(RectF(18f,96f,342f,bottom))
        text("SESIÓN TERMINADA",36f,126f,11f,color=MUTED)
        text(data.sessionName,36f,158f,30f,bold=true)
        if(options.rings)rings(data)
        val metrics=buildList{
            if(options.duration)add("Duración" to "${data.durationMinutes} min")
            if(options.intensity)add("RIR medio" to (data.averageRir?.let{finishNumber(it)}?:"Sin registro"))
            add("Tonelaje" to "${finishNumber(data.volume,0)} kg");add("Series" to "${data.sets}")
        }
        val start=if(options.rings)142f else 36f
        val step=if(options.rings)89f else 148f
        metrics.forEachIndexed{i,(label,value)->val x=start+(i%2)*step;val y=199f+(i/2)*46f;text(label,x,y,11f,color=MUTED,maxWidth=step-5);text(value,x,y+21,16f,bold=true,maxWidth=step-5)}
        line(36f,302f,324f,302f)
        if(visible.isEmpty())text("Sin ejercicios registrados",36f,332f,13f,color=MUTED)
        visible.forEachIndexed{i,lift->
            val y=331f+i*rowHeight
            text(lift.name,36f,y,13f,bold=true,maxWidth=if(options.improvements)143f else 202f)
            text(finishSetLabel(lift.best),if(options.improvements)270f else 324f,y,12f,color=MUTED,align=Paint.Align.RIGHT,maxWidth=if(options.improvements)82f else 100f)
            if(options.improvements)text(finishDeltaLabel(lift.deltaKg),324f,y,12f,bold=true,color=if(lift.isImproved)GREEN else MUTED,align=Paint.Align.RIGHT,maxWidth=50f)
        }
        if(data.lifts.size>7)text("+${data.lifts.size-7} ejercicios",36f,bottom-10,10f,color=MUTED)
    }
    fun exercise(data:WorkoutStoryData,options:WorkoutStoryOptions){
        val lift=data.lifts.firstOrNull{it.exerciseId==options.exerciseId}?:data.lifts.firstOrNull{it.exerciseId==data.defaultExerciseId}
        glass(RectF(18f,120f,342f,516f))
        if(lift==null){text("Sin series de fuerza registradas",36f,158f,15f,bold=true);return}
        text(lift.name,36f,153f,14f,bold=true,maxWidth=if(options.improvements)180f else 288f)
        if(options.improvements){
            val rect=RectF(242f,138f,324f,164f)
            paint.reset();paint.isAntiAlias=true;paint.color=0x18F2EEE6;canvas.drawRoundRect(rect,13f,13f,paint);glassBorder(rect,13f)
            text(if(lift.isImproved)"Marca ${finishDeltaLabel(lift.deltaKg)}"else finishDeltaLabel(lift.deltaKg),283f,155f,11f,bold=true,align=Paint.Align.CENTER,maxWidth=72f)
        }
        val best=lift.best
        text(best?.let{if(it.weight==0.0)"${it.reps} reps"else "${finishNumber(it.weight)} kg"}?:"Sin serie comparable",36f,220f,if(best==null)23f else 46f,bold=true,display=best!=null)
        val effort=best?.let(::finishActualRir)?.let{" · RIR ${finishNumber(it)}"}.orEmpty()
        text(best?.let{if(it.weight==0.0)"Peso corporal$effort"else "× ${it.reps} reps$effort · 1RM estimado ${finishNumber(StrengthModel.estimate1RM(it),0)} kg"}.orEmpty(),36f,244f,12f,color=MUTED)
        val points=lift.history.map{it.weight}+listOfNotNull(best?.weight)
        val dates=lift.history.mapNotNull{runCatching{LocalDate.parse(it.date.take(10))}.getOrNull()}+listOfNotNull(data.presentation.today.takeIf{best!=null})
        graph(points,RectF(36f,264f,324f,414f),dates.takeIf{it.size==points.size})
        val firstDate=lift.history.firstOrNull()?.date?.take(10)?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
        text(firstDate?.format(DateTimeFormatter.ofPattern("MMM",locale))?:"Hoy",36f,438f,11f,color=MUTED)
        if(firstDate!=null&&java.time.temporal.ChronoUnit.DAYS.between(firstDate,data.presentation.today)>45){val middle=firstDate.plusDays(java.time.temporal.ChronoUnit.DAYS.between(firstDate,data.presentation.today)/2);text(middle.format(DateTimeFormatter.ofPattern("MMM",locale)),180f,438f,11f,color=MUTED,align=Paint.Align.CENTER)}
        text(data.presentation.today.format(DateTimeFormatter.ofPattern("MMM",locale)),324f,438f,11f,color=MUTED,align=Paint.Align.RIGHT)
        val first=lift.first?.weight
        val gain=if(first!=null&&first>0&&best!=null)(best.weight/first-1)*100 else null
        val stats=listOf("Sesiones" to "${lift.totalHistorySessions+if(best!=null)1 else 0}","Desde" to(first?.let{"${finishNumber(it)} kg"}?:"Sin historial"))+
            if(options.improvements)listOf("Subida" to(gain?.let{"${if(it>0)"+"else ""}${finishNumber(it,0)} %"}?:"—"))else emptyList()
        stats.forEachIndexed{i,(label,value)->text(label,36f+i*98f,471f,11f,color=MUTED);text(value,36f+i*98f,493f,15f,bold=true,color=if(label=="Subida"&&gain!=null&&gain>0)GREEN else CREAM,maxWidth=95f)}
    }
    suspend fun photos(context:Context,data:WorkoutStoryData,options:WorkoutStoryOptions){
        glass(RectF(18f,40f,342f,106f),20f)
        text(data.sessionName,32f,72f,20f,bold=true,maxWidth=228f)
        text(date(data)+if(options.duration)" · ${data.durationMinutes} min"else "",32f,91f,12f,color=MUTED,maxWidth=230f)
        logo(270f,63f,58f)
        val photos=data.presentation.photos.take(4)
        require(photos.isNotEmpty()){"No hay fotos reales en esta sesión"}
        val rects=when(photos.size){
            1->listOf(RectF(18f,124f,342f,428f))
            2->listOf(RectF(18f,124f,176f,428f),RectF(184f,124f,342f,428f))
            else->listOf(RectF(18f,124f,176f,428f),RectF(184f,124f,342f,272f),RectF(184f,280f,342f,428f),RectF(18f,436f,176f,584f))
        }
        photos.forEachIndexed{i,photo->
            val rect=rects[i];val image=loadStoryImage(context,photo.path,960)
            canvas.save();canvas.clipPath(Path().apply{addRoundRect(rect,20f,20f,Path.Direction.CW)})
            if(image!=null)drawCover(canvas,image,rect)else{paint.reset();paint.color=0xFF161616.toInt();canvas.drawRect(rect,paint);text("Foto no disponible",rect.centerX(),rect.centerY(),11f,color=MUTED,align=Paint.Align.CENTER,maxWidth=rect.width()-16)}
            canvas.restore()
            if(!photo.exerciseName.isNullOrBlank()||!photo.detail.isNullOrBlank()){
                val caption=RectF(rect.left+8,rect.bottom-50,rect.right-8,rect.bottom-8)
                if(image!=null){val blurredPhoto=blurStoryBackground(image);canvas.save();canvas.clipPath(Path().apply{addRoundRect(caption,12f,12f,Path.Direction.CW)});drawCover(canvas,blurredPhoto,rect);canvas.drawColor(Color.argb(107,18,16,14));canvas.restore();blurredPhoto.recycle();glassBorder(caption,12f)}else glass(caption,12f)
                text(photo.exerciseName.orEmpty(),rect.left+16,rect.bottom-32,11f,bold=true,maxWidth=rect.width()-32)
                text(photo.detail.orEmpty(),rect.left+16,rect.bottom-16,11f,color=MUTED,maxWidth=rect.width()-32)
            };image?.recycle()
        }
        val total=if(photos.size==4)RectF(184f,436f,342f,584f)else RectF(18f,436f,342f,584f)
        glass(total,20f);text(finishNumber(data.volume,0),total.centerX(),total.centerY()+4,26f,bold=true,display=true,align=Paint.Align.CENTER,maxWidth=total.width()-24)
        text("kg movidos hoy",total.centerX(),total.centerY()+27,11f,color=MUTED,align=Paint.Align.CENTER,maxWidth=total.width()-24)
    }
    private fun line(x1:Float,y1:Float,x2:Float,y2:Float){paint.reset();paint.color=0x24FFFFFF;paint.strokeWidth=1f;canvas.drawLine(x1,y1,x2,y2,paint)}
    private fun graph(values:List<Double>,rect:RectF,dates:List<LocalDate>?=null){
        repeat(3){val y=rect.top+it*rect.height()/2;line(rect.left,y,rect.right,y)}
        if(values.isEmpty()){text("Sin historial comparable",rect.centerX(),rect.centerY(),12f,color=MUTED,align=Paint.Align.CENTER);return}
        val low=values.minOrNull()?:0.0;val span=((values.maxOrNull()?:0.0)-low).coerceAtLeast(1.0);val path=Path();var x=rect.centerX();var y=rect.centerY()
        values.forEachIndexed{i,v->val days=dates?.let{java.time.temporal.ChronoUnit.DAYS.between(it.first(),it.last())};val position=if(dates!=null&&days!=null&&days>0)java.time.temporal.ChronoUnit.DAYS.between(dates.first(),dates[i]).toFloat()/days else if(values.size>1)i.toFloat()/(values.size-1)else .5f
            x=rect.left+6+(rect.width()-12)*position;y=rect.bottom-8-((v-low)/span).toFloat()*(rect.height()-16);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
        paint.reset();paint.isAntiAlias=true;paint.color=PEACH;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        if(values.size>1)canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,5f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=0x66F49A6E;canvas.drawCircle(x,y,11f,paint)
    }
}

private suspend fun storyBackground(context:Context,data:WorkoutStoryData,template:WorkoutStoryTemplate):Bitmap{
    val value=data.presentation.program?.coverImage
    val fromLibrary=programCoverEntryIdOf(value)?.let{ProgramCoverRepository.getInstance(context).load(it,1080)}
    val remote=value?.takeIf{it.startsWith("https://")||it.startsWith("http://")}?.let{url->(context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(1080,1920).build())as? SuccessResult)?.drawable?.toBitmap()}
    val photo=fromLibrary?:remote?:value?.takeIf{it.startsWith("content:")||it.startsWith("file:")||File(it).isAbsolute}?.let{loadStoryImage(context,it,1920)}
    val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
    if(photo!=null){drawCover(canvas,photo,RectF(0f,0f,1080f,1920f));if(fromLibrary==null&&remote==null)photo.recycle()}
    else if(value?.startsWith("gradient://")==true){
        val density=Density(1f)
        CanvasDrawScope().draw(density,LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(canvas),Size(1080f,1920f)){drawRect(com.example.kpkn.screens.programs.programCoverBrush(value))}
    }else{
        canvas.drawColor(0xFF120E0B.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun radial(x:Float,y:Float,rx:Float,ry:Float,color:Int){
            paint.shader=android.graphics.RadialGradient(0f,0f,1f,intArrayOf(color,color and 0x00FFFFFF),floatArrayOf(0f,.7f),android.graphics.Shader.TileMode.CLAMP).apply{setLocalMatrix(android.graphics.Matrix().apply{setScale(rx,ry);postTranslate(x,y)})};canvas.drawRect(0f,0f,1080f,1920f,paint)
        }
        radial(648f,1824f,540f,768f,0x80F7CF73.toInt());radial(162f,1344f,648f,960f,0x738FB2FF);radial(842.4f,345.6f,756f,1056f,0xD9F49A6E.toInt())
        if(template!=WorkoutStoryTemplate.PHOTOS){paint.reset();paint.isAntiAlias=true;paint.style=Paint.Style.STROKE;paint.strokeWidth=72f;paint.color=0x14F2EEE6;canvas.drawRoundRect(RectF(312f,366f,1164f,1218f),354f,354f,paint)}
    }
    return bitmap
}

private suspend fun loadStoryImage(context:Context,path:String,width:Int):Bitmap?=withContext(Dispatchers.IO) {
    runCatching {
        val uri=if(path.startsWith("content:") || path.startsWith("file:")) Uri.parse(path) else Uri.fromFile(File(path))
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        val opts=BitmapFactory.Options().apply { var scale=1;while(bounds.outWidth/scale>width*2 || bounds.outHeight/scale>width*2)scale*=2;inSampleSize=scale }
        val bitmap=context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,opts) } ?: return@runCatching null
        val orientation=runCatching { context.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
        } }.getOrNull() ?: 1
        val matrix=android.graphics.Matrix()
        when(orientation) {
            2 -> matrix.postScale(-1f,1f)
            3 -> matrix.postRotate(180f)
            4 -> matrix.postScale(1f,-1f)
            5 -> { matrix.postRotate(90f);matrix.postScale(-1f,1f) }
            6 -> matrix.postRotate(90f)
            7 -> { matrix.postRotate(270f);matrix.postScale(-1f,1f) }
            8 -> matrix.postRotate(270f)
        }
        if(matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if(it !== bitmap) bitmap.recycle() }
    }.getOrNull()
}
private fun drawCover(canvas:Canvas,image:Bitmap,dest:RectF) {
    val scale=max(dest.width()/image.width,dest.height()/image.height)
    val w=image.width*scale;val h=image.height*scale
    canvas.drawBitmap(image,null,RectF(dest.centerX()-w/2,dest.centerY()-h/2,dest.centerX()+w/2,dest.centerY()+h/2),Paint(Paint.FILTER_BITMAP_FLAG))
}

/** Vidrio real en todas las API: desenfoque separable pequeño del mismo fondo, sin RenderEffect exclusivo de API31. */
private fun blurStoryBackground(source:Bitmap):Bitmap {
    val shrink=minOf(216f/source.width,384f/source.height,1f)
    val resized=Bitmap.createScaledBitmap(source,(source.width*shrink).toInt().coerceAtLeast(1),(source.height*shrink).toInt().coerceAtLeast(1),true)
    val small=if(resized===source)checkNotNull(source.copy(Bitmap.Config.ARGB_8888,true))else resized
    val width=small.width;val height=small.height
    var pixels=IntArray(width*height);small.getPixels(pixels,0,width,0,0,width,height)
    val radius=6
    repeat(3) {
        repeat(2) { axis ->
            val out=IntArray(pixels.size);val lines=if(axis==0)height else width;val length=if(axis==0)width else height
            fun index(line:Int,pos:Int)=if(axis==0)line*width+pos else pos*width+line
            repeat(lines) { line ->
                var r=0;var g=0;var b=0
                for(k in -radius..radius) { val c=pixels[index(line,k.coerceIn(0,length-1))];r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c) }
                repeat(length) { pos ->
                    val n=radius*2+1;out[index(line,pos)]=Color.rgb(r/n,g/n,b/n)
                    val old=pixels[index(line,(pos-radius).coerceIn(0,length-1))];val new=pixels[index(line,(pos+radius+1).coerceIn(0,length-1))]
                    r+=Color.red(new)-Color.red(old);g+=Color.green(new)-Color.green(old);b+=Color.blue(new)-Color.blue(old)
                }
            }
            pixels=out
        }
    }
    small.setPixels(pixels,0,width,0,0,width,height)
    return small
}
