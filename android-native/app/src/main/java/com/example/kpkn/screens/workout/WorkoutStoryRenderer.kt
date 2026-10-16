package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
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
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.ProgramCoverRepository
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioPainter
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
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

/** Un único render de portada y vidrio para la vista previa, el PNG de galería y el archivo compartido. */
internal suspend fun renderWorkoutStory(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions): Bitmap = withContext(Dispatchers.Default) {
    val background = storyBackground(context, data)
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawBitmap(background, 0f, 0f, null)
    val blurred = blurStoryBackground(background)
    canvas.scale(3f, 3f)
    val draw = StoryDrawing(canvas, blurred, context)
    draw.text("KPKN", 18f, 72f, 16f, bold = true)
    draw.text(data.presentation.today.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.forLanguageTag("es-CL"))),
        342f, 72f, 11f, align = Paint.Align.RIGHT, color = MUTED)
    when (options.template) {
        WorkoutStoryTemplate.GENERAL -> draw.general(data, options)
        WorkoutStoryTemplate.EXERCISE -> draw.exercise(data, options)
        WorkoutStoryTemplate.PHOTOS -> draw.photos(context, data, options)
    }
    val footer = listOfNotNull(data.presentation.program?.name?.uppercase(), data.presentation.weekNumber?.let { "SEMANA $it" }).joinToString(" · ")
    if (footer.isNotBlank()) draw.text(footer, 180f, 618f, 10f, align = Paint.Align.CENTER, color = MUTED, maxWidth = 324f)
    blurred.recycle()
    background.recycle()
    bitmap
}

private const val CREAM = 0xFFF2EEE6.toInt()
private const val MUTED = 0xFFB5B1AC.toInt()
private const val GREEN = 0xFF7FE3B0.toInt()
private const val GOLD = 0xFFF7CF73.toInt()

private class StoryDrawing(val canvas: Canvas, val blurred: Bitmap, context: Context) {
    private val headingFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.syne_bold)
    private val bodyFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.wizard_body_regular)
    private val strongFont = androidx.core.content.res.ResourcesCompat.getFont(context, com.example.kpkn.R.font.wizard_body_semibold)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun text(value: String, x: Float, y: Float, size: Float, bold: Boolean = false, color: Int = CREAM,
        align: Paint.Align = Paint.Align.LEFT, maxWidth: Float = 288f) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.textSize = size
        paint.typeface = if(bold && size >= 23f) headingFont else if(bold) strongFont else bodyFont
        paint.textAlign = align
        var label = value.replace('\n', ' ')
        if (paint.measureText(label) > maxWidth) {
            while (label.isNotEmpty() && paint.measureText("$label…") > maxWidth) label = label.dropLast(1)
            label += "…"
        }
        canvas.drawText(label, x, y, paint)
    }
    fun glass(rect: RectF, radius: Float = 26f) {
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        paint.reset(); paint.isFilterBitmap = true
        canvas.drawBitmap(blurred, null, RectF(0f, 0f, 360f, 640f), paint)
        canvas.drawColor(Color.argb(130, 18, 16, 14)); canvas.restore()
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x24FFFFFF
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
    private fun metrics(data: WorkoutStoryData, options: WorkoutStoryOptions, y: Float, x: Float = 36f) {
        val rows = buildList {
            if (options.duration) add("Duración" to "${data.durationMinutes} min")
            if (options.intensity) add("RIR medio" to (data.averageRir?.let { finishNumber(it) } ?: "Sin registro"))
            add("Tonelaje" to "${finishNumber(data.volume, 0)} kg")
            add("Series" to "${data.sets}")
        }
        rows.forEachIndexed { index, (label, value) ->
            val px = x + (index % 2) * (if(x > 40) 89f else 148f)
            val py = y + (index / 2) * 42f
            text(label, px, py, 10f, color = MUTED, maxWidth = 140f)
            text(value, px, py + 19f, 15f, bold = true, maxWidth = if(x > 40) 88f else 140f)
        }
    }
    private fun rings(data: WorkoutStoryData, x: Float, y: Float) {
        listOf(Triple(data.rings.columna, 78f, 0xFF8FB2FF.toInt()), Triple(data.rings.musculos, 58f, 0xFFF49A6E.toInt()), Triple(data.rings.energia, 38f, GOLD))
            .forEachIndexed { index, (value, width, color) ->
                val cy = y - index * 24f
                val rect = RectF(x - width / 2, cy - width / 6, x + width / 2, cy + width / 6)
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND
                paint.color = 0x24FFFFFF; canvas.drawOval(rect, paint)
                if(value != null) { paint.color = color; canvas.drawArc(rect, -90f, 360f * value.coerceIn(0,100) / 100f, false, paint) }
            }
    }
    private fun extras(data: WorkoutStoryData, options: WorkoutStoryOptions, y: Float) {
        if(options.rings) rings(data, 78f, y + 40f)
        var line = y
        if(options.duration) { text("${data.durationMinutes} min", 142f, line, 11f, color = MUTED); line += 18f }
        if(options.intensity) text("Intensidad · ${data.intensity}", 142f, line, 11f, color = MUTED, maxWidth = 182f)
    }
    fun general(data: WorkoutStoryData, options: WorkoutStoryOptions) {
        val visible = data.lifts.take(7)
        val rowHeight = if(visible.size > 5) 39f else 46f
        val bottom = (302f + visible.size * rowHeight + if(data.lifts.size > 7) 18f else 0f).coerceAtMost(584f)
        glass(RectF(18f, 96f, 342f, max(360f, bottom)))
        text("SESIÓN TERMINADA", 36f, 127f, 11f, color = MUTED)
        text(data.sessionName, 36f, 162f, 29f, bold = true)
        if(options.rings) { rings(data, 79f, 255f); metrics(data, options, 204f, 142f) }
        else metrics(data, options, 204f)
        line(36f, 288f, 324f, 288f)
        if(visible.isEmpty()) text("Sin ejercicios registrados", 36f, 326f, 13f, color = MUTED)
        visible.forEachIndexed { index, lift ->
            val y = 316f + rowHeight * index
            text(lift.name, 36f, y, 13f, bold = true, maxWidth = if(options.improvements) 210f else 288f)
            if(options.improvements) text(finishDeltaLabel(lift.deltaKg), 324f, y, 11f, bold = true,
                color = if(lift.isImproved) GREEN else MUTED, align = Paint.Align.RIGHT, maxWidth = 82f)
            text(finishSetLabel(lift.best), 36f, y + 17f, 12f, color = MUTED)
        }
        if(data.lifts.size > 7) text("+${data.lifts.size - 7} ejercicios", 36f, bottom - 12f, 10f, color = MUTED)
    }
    fun exercise(data: WorkoutStoryData, options: WorkoutStoryOptions) {
        val lift = data.lifts.firstOrNull { it.exerciseId == options.exerciseId } ?: data.lifts.firstOrNull { it.exerciseId == data.defaultExerciseId }
        glass(RectF(18f, 112f, 342f, 584f))
        if(lift == null) { text("Sin series de fuerza registradas", 36f, 158f, 15f, bold = true); extras(data, options, 480f); return }
        text(lift.name, 36f, 151f, 17f, bold = true)
        if(options.improvements) text(if(lift.isImproved) "Marca ${finishDeltaLabel(lift.deltaKg)}" else finishDeltaLabel(lift.deltaKg),
            36f, 178f, 12f, color = if(lift.isImproved) GOLD else MUTED)
        val best = lift.best
        text(best?.let { if(it.weight==0.0) "${it.reps} reps" else "${finishNumber(it.weight)} kg" } ?: "Sin serie comparable", 36f, 229f, if(best == null) 23f else 44f, bold = true)
        text(best?.let { if(it.weight==0.0) "Peso corporal" else "× ${it.reps} reps · 1RM estimado ${finishNumber(calculateHybrid1RM(it.weight,it.reps),0)} kg" } ?: "", 36f, 254f, 12f, color = MUTED)
        val points = lift.history.map { it.weight } + listOfNotNull(best?.weight)
        val dates=lift.history.mapNotNull { point -> runCatching { LocalDate.parse(point.date.take(10)) }.getOrNull() } + listOfNotNull(data.presentation.today.takeIf { best != null })
        graph(points, RectF(36f, 280f, 324f, 405f), dates.takeIf { it.size==points.size })
        val firstDate = lift.history.firstOrNull()?.date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if(firstDate!=null && java.time.temporal.ChronoUnit.DAYS.between(firstDate,data.presentation.today)>45) {
            val middle=firstDate.plusDays(java.time.temporal.ChronoUnit.DAYS.between(firstDate,data.presentation.today)/2)
            text(middle.format(DateTimeFormatter.ofPattern("MMM",Locale.forLanguageTag("es-CL"))),180f,426f,10f,color=MUTED,align=Paint.Align.CENTER)
        }
        text(firstDate?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-CL"))) ?: "Hoy", 36f, 426f, 10f, color = MUTED)
        text("Hoy", 324f, 426f, 10f, color = MUTED, align = Paint.Align.RIGHT)
        val first = lift.first?.weight
        val gain = if(first != null && first > 0 && best != null) (best.weight / first - 1) * 100 else null
        val stats = listOf("Sesiones" to "${lift.totalHistorySessions + if(best != null) 1 else 0}", "Desde" to (first?.let { "${finishNumber(it)} kg" } ?: "Sin historial")) +
            if(options.improvements) listOf("Subida" to (gain?.let { "${if(it>0) "+" else ""}${finishNumber(it,0)} %" } ?: "—")) else emptyList()
        stats.forEachIndexed { index, (label,value) -> text(label, 36f + index*98f, 457f, 10f, color=MUTED); text(value,36f+index*98f,477f,14f,bold=true,maxWidth=95f) }
        extras(data, options, 514f)
    }
    suspend fun photos(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions) {
        text("SESIÓN TERMINADA", 18f, 104f, 10f, color = MUTED)
        text(data.sessionName, 18f, 135f, 25f, bold = true, maxWidth = 324f)
        val photos = data.presentation.photos.take(4)
        require(photos.isNotEmpty()) { "No hay fotos reales en esta sesión" }
        val photoBottom = 430f
        photos.forEachIndexed { index, photo ->
            val rect = when(photos.size) {
                1 -> RectF(18f,158f,342f,photoBottom)
                2 -> RectF(18f+index*166f,158f,176f+index*166f,photoBottom)
                3 -> if(index==0) RectF(18f,158f,176f,photoBottom) else RectF(184f,158f+(index-1)*140f,342f,290f+(index-1)*140f)
                else -> RectF(18f + (index%2)*166f,158f + (index/2)*140f,176f+(index%2)*166f,290f+(index/2)*140f)
            }
            glass(rect,20f)
            val image = loadStoryImage(context,photo.path,960)
            if(image != null) {
                canvas.save(); canvas.clipPath(Path().apply { addRoundRect(rect,20f,20f,Path.Direction.CW) }); drawCover(canvas,image,rect); canvas.restore(); image.recycle()
            } else text("Foto no disponible", rect.centerX(), rect.centerY(), 11f, color=MUTED,align=Paint.Align.CENTER,maxWidth=rect.width()-16)
            if(!photo.exerciseName.isNullOrBlank() || !photo.detail.isNullOrBlank()) {
                paint.reset(); paint.color=0xB012100E.toInt(); canvas.drawRoundRect(RectF(rect.left+6,rect.bottom-48,rect.right-6,rect.bottom-6),12f,12f,paint)
                text(photo.exerciseName.orEmpty(),rect.left+12,rect.bottom-31,11f,bold=true,maxWidth=rect.width()-24)
                text(photo.detail.orEmpty(),rect.left+12,rect.bottom-15,10f,color=MUTED,maxWidth=rect.width()-24)
            }
        }
        glass(RectF(18f,442f,342f,590f),20f)
        text("${finishNumber(data.volume,0)} kg movidos hoy",36f,470f,18f,bold=true)
        val summary=data.lifts.take(2).joinToString(" · ") { it.name }
        text(summary,36f,491f,11f,color=MUTED)
        if(options.improvements) {
            val improved=data.lifts.filter { it.isImproved }.take(2).joinToString(" · ") { "${it.name} ${finishDeltaLabel(it.deltaKg)}" }
            if(improved.isNotBlank()) text(improved,36f,509f,10f,color=GREEN)
        }
        extras(data,options,537f)
    }
    private fun line(x1:Float,y1:Float,x2:Float,y2:Float) { paint.reset();paint.color=0x24FFFFFF;paint.strokeWidth=1f;canvas.drawLine(x1,y1,x2,y2,paint) }
    private fun graph(values:List<Double>,rect:RectF,dates:List<LocalDate>?=null) {
        repeat(3) { val y=rect.top+it*rect.height()/2;line(rect.left,y,rect.right,y) }
        if(values.isEmpty()) { text("Sin historial comparable",rect.centerX(),rect.centerY(),12f,color=MUTED,align=Paint.Align.CENTER);return }
        val low=values.minOrNull() ?: 0.0;val span=((values.maxOrNull() ?: 0.0)-low).coerceAtLeast(1.0)
        val path=Path();var lastX=rect.centerX();var lastY=rect.centerY()
        values.forEachIndexed { i,v ->
            val days=dates?.let { java.time.temporal.ChronoUnit.DAYS.between(it.first(),it.last()) }
            val position=if(dates!=null && days!=null && days>0) java.time.temporal.ChronoUnit.DAYS.between(dates.first(),dates[i]).toFloat()/days else if(values.size>1) i.toFloat()/(values.size-1) else 0.5f
            lastX=rect.left+6+(rect.width()-12)*position
            lastY=rect.bottom-8-((v-low)/span).toFloat()*(rect.height()-16)
            if(i==0)path.moveTo(lastX,lastY) else path.lineTo(lastX,lastY)
        }
        paint.reset();paint.isAntiAlias=true;paint.color=CREAM;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f;paint.strokeJoin=Paint.Join.ROUND
        if(values.size>1) canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL;paint.color=GOLD;canvas.drawCircle(lastX,lastY,5f,paint)
    }
}

private suspend fun storyBackground(context:Context,data:WorkoutStoryData):Bitmap {
    val program=data.presentation.program
    val value=program?.coverImage
    val fromLibrary=programCoverEntryIdOf(value)?.let { ProgramCoverRepository.getInstance(context).load(it,1080) }
    // La portada personalizada admite los mismos URL que ProgramPoster (Coil), además de los archivos locales.
    val remote=value?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
        val request=ImageRequest.Builder(context).data(url).allowHardware(false).size(1080,1920).build()
        (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
    val photo=fromLibrary ?: remote ?: value?.takeIf { it.startsWith("content:") || it.startsWith("file:") || File(it).isAbsolute }?.let { loadStoryImage(context,it,1920) }
    val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap)
    if(photo!=null) {
        drawCover(canvas,photo,RectF(0f,0f,1080f,1920f));if(fromLibrary==null && remote==null)photo.recycle()
    } else {
        val density=Density(1f)
        val measurer=TextMeasurer(createFontFamilyResolver(context),density,LayoutDirection.Ltr)
        val painter=CoverStudioPainter(coverIdentityForProgram(program ?: Program(id="session-${data.sessionName}",name=data.sessionName)),1080f,1920f,measurer,density)
        CanvasDrawScope().draw(density,LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(canvas),Size(1080f,1920f)) {
            if(value?.startsWith("gradient://")==true) drawRect(com.example.kpkn.screens.programs.programCoverBrush(value)) else painter.draw(this,0f,false,0f)
        }
    }
    canvas.drawColor(0x40000000)
    return bitmap
}

/** Decodificación acotada: las fotos originales no se cargan a resolución completa. */
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
    val small=Bitmap.createScaledBitmap(source,216,384,true)
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
