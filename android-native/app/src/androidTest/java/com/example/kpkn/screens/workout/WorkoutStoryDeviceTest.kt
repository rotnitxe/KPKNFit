package com.example.kpkn.screens.workout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.ui.components.rings.TowerRingValues
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WorkoutStoryDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun exercise(weight:Double=85.0,reps:Int=6,configuration:String="free")=CompletedExercise(
        "squat","Sentadilla",canonicalExerciseId="squat",catalogConfigurationId=configuration,
        sets=listOf(CompletedSet("set",weight=weight,reps=reps,rir=2)))
    private fun log(id:String,date:String,exercise:CompletedExercise)=WorkoutLog(id,"program","session","Pierna",date,40,listOf(exercise))
    private fun data(photos:List<StoryPhoto> = emptyList()):WorkoutStoryData {
        val previous=log("previous","2026-09-15T12:00:00Z",exercise(80.0))
        return WorkoutStoryData("Pierna",FinishPresentationContext(program=Program("program","Base",coverImage="gradient://forest"),
            weekNumber=3,photos=photos,today=LocalDate.of(2026,10,16)),40,4080.0,8,2.0,"Alta",TowerRingValues(65,70,75),
            buildFinishLiftProgress(listOf(exercise()),listOf(previous)))
    }

    @Test fun averageRirKeepsHighReserveZeroRpeAndActualModeWithoutAssumingPlannedFailure() {
        fun average(vararg sets:CompletedSet)=finishAverageRir(listOf(exercise().copy(sets=sets.toList())))
        assertEquals(11.0,average(
            CompletedSet("legacy-rir",rir=12),
            CompletedSet("actual-rir",actualIntensityMode=IntensityMode.RIR,actualIntensityValue=12.0),
            CompletedSet("legacy-rpe",rpe=0.0),
            CompletedSet("actual-rpe",actualIntensityMode=IntensityMode.RPE,actualIntensityValue=0.0),
        )!!,0.001)
        // El modo elegido manda sobre campos legacy que una edición anterior pudiera conservar.
        assertEquals(12.0,average(CompletedSet("rir",rpe=8.0,actualIntensityMode=IntensityMode.RIR,actualIntensityValue=12.0))!!,0.001)
        assertEquals(10.0,average(CompletedSet("rpe",rir=2,actualIntensityMode=IntensityMode.RPE,actualIntensityValue=0.0))!!,0.001)
        // El recorder puede heredar FAILURE del plan aunque no se haya alcanzado el fallo.
        assertNull(average(CompletedSet("planned",rpe=8.0,actualIntensityMode=IntensityMode.FAILURE)))
        assertEquals(0.0,average(CompletedSet("reached",isFailure=true,actualIntensityMode=IntensityMode.FAILURE))!!,0.001)
        assertNull(average(CompletedSet("invalid-rir",rir=-1),CompletedSet("invalid-rpe",rpe=12.0),
            CompletedSet("nan",actualIntensityMode=IntensityMode.RIR,actualIntensityValue=Double.NaN)))
    }

    @Test fun progressUsesActualLoadAndSameConfigurationRatherThanEstimated1RmGain() {
        val sameLoadMoreReps=exercise(80.0,10)
        val previous=log("previous","2026-09-15T12:00:00Z",exercise(80.0,5))
        val otherVariant=log("smith","2026-10-15T12:00:00Z",exercise(130.0,5,"smith"))
        val result=buildFinishLiftProgress(listOf(sameLoadMoreReps),listOf(previous,otherVariant)).single()
        assertEquals(0.0,result.deltaKg!!,0.001)
        assertEquals("=",finishDeltaLabel(result.deltaKg))
        assertEquals(1,result.history.size)
        assertFalse(result.isImproved)
    }

    @Test fun storiesAre1080By1920AndEveryIncludeOptionChangesTheActualBitmap()=runBlocking {
        val file=File(context.cacheDir,"lv13-photo-test.png")
        val photo=Bitmap.createBitmap(160,240,Bitmap.Config.ARGB_8888)
        Canvas(photo).apply { drawColor(Color.rgb(20,70,110));drawRect(0f,0f,80f,240f,android.graphics.Paint().apply { color=Color.rgb(150,75,50) }) }
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG,100,it) };photo.recycle()
        try {
            val data=data(listOf(StoryPhoto(file.absolutePath,"Sentadilla","85 kg × 6")))
            WorkoutStoryTemplate.entries.forEach { template ->
                val options=WorkoutStoryOptions(template=template)
                val base=renderWorkoutStory(context,data,options)
                try {
                    assertEquals(1080,base.width);assertEquals(1920,base.height)
                    // Solo fixtures de prueba, exportados por Android para la inspección física del ZFlip.
                    val output=File(context.getExternalFilesDir(null),"qa-stories").apply { mkdirs() }
                    File(output,"lv13-demo-${template.name.lowercase()}.png").outputStream().use { base.compress(Bitmap.CompressFormat.PNG,100,it) }
                    listOf(options.copy(rings=false),options.copy(improvements=false),options.copy(duration=false),options.copy(intensity=false)).forEach { alternate ->
                        val rendered=renderWorkoutStory(context,data,alternate)
                        try { assertFalse("${template.name}: $alternate debe cambiar los píxeles",base.sameAs(rendered)) } finally { rendered.recycle() }
                    }
                } finally { base.recycle() }
            }
        } finally { file.delete() }
    }


    @Test fun galleryPublishesReadablePngInsideKpkn()=runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        val bitmap=renderWorkoutStory(context,data(),WorkoutStoryOptions())
        val uri=try { WorkoutShareService.saveImage(context,bitmap) } finally { bitmap.recycle() }
        try {
            context.contentResolver.query(uri,arrayOf(android.provider.MediaStore.Images.Media.RELATIVE_PATH,android.provider.MediaStore.Images.Media.IS_PENDING),null,null,null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst());assertTrue(cursor.getString(0).contains("KPKN"));assertEquals(0,cursor.getInt(1))
            }
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
            assertEquals(1080,bounds.outWidth);assertEquals(1920,bounds.outHeight)
        } finally { context.contentResolver.delete(uri,null,null) }
    }

    @Test fun minimalGeneralAndExerciseRenderWithoutFabricatingHistoryOrPhotos()=runBlocking {
        val empty=data().copy(lifts=emptyList(),sets=0,volume=0.0,averageRir=null)
        listOf(WorkoutStoryTemplate.GENERAL,WorkoutStoryTemplate.EXERCISE).forEach { template ->
            val bitmap=renderWorkoutStory(context,empty,WorkoutStoryOptions(template=template))
            assertEquals(1080,bitmap.width);assertEquals(1920,bitmap.height);bitmap.recycle()
        }
        assertTrue(empty.presentation.photos.isEmpty())
    }
}
