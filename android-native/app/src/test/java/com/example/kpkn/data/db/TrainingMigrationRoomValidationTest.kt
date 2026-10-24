package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.screens.onboarding.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[34],application=Application::class)
class TrainingMigrationRoomValidationTest {
    @Test fun populated28_29_30OpenWithFullRoom32IdentityAndPreserveUsefulData() = runBlocking {
        for(version in listOf(28,29,30)) {
            val dir=Files.createTempDirectory("post-rings-synthetic-").toFile()
            val context=object: ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getApplicationContext(): Context = this
                override fun getDatabasePath(name:String)=File(dir,name)
                override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase =
                    SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY)
                override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase =
                    SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath,factory,mode or SQLiteDatabase.CREATE_IF_NECESSARY,handler)
            }
            val folder=listOf(File("schemas"),File("app/schemas")).first { it.isDirectory }
            val schema=JSONObject(File(folder,"com.example.kpkn.data.db.KpknDatabase/$version.json").readText()).getJSONObject("database")
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("test.db"),null).use { old ->
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                    entity.optJSONArray("indices")?.let { indices ->
                        for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                    }
                }
                val setup=schema.optJSONArray("setupQueries")
                if(setup!=null) for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO workout_logs VALUES ('log','program','session','2026-10-01',?)",arrayOf("""{"id":"log","programId":"program","sessionId":"session","sessionName":"Síntesis de prueba","date":"2026-10-01","notes":"Mi nota personal AUGE no se borra","durationMinutes":17,"sessionStressScore":88,"completedExercises":[]}"""))
                old.execSQL("INSERT INTO auge_sleep(id,date,data) VALUES ('sleep','2026-10-01',?)",arrayOf("""{"id":"sleep","date":"2026-10-01","duration":7.25,"endTime":"2026-10-01T08:00:00Z"}"""))
                old.execSQL("INSERT INTO workout_media(id,kind,filePath,createdAtMs,isPr,workoutLogId,caption) VALUES ('media','PHOTO','synthetic.jpg',123,0,'log','Mi foto')")
                old.execSQL("INSERT INTO setup_drafts(draftId,payloadJson,revision,updatedAtEpochMs) VALUES ('draft',?,7,123)",arrayOf("""{"draftId":"draft","commitId":"commit","name":"Ana","includeTraining":false,"includeNutrition":false,"weightKg":70.25,"draftScope":"rings_only","ringsAnswers":{"energyFeeling":3},"stepProgress":{"origin":"NATIVE","currentStepId":"RINGS_RESULT","stalePreviews":["RINGS_BATTERIES"]}}"""))
                old.execSQL("INSERT INTO setup_commit_receipts(commitId,draftId,bodyGoalIdsJson,committedAtEpochMs) VALUES ('previous-commit','other','[]',456)")
                old.version=version
            }
            val db=Room.databaseBuilder(context,KpknDatabase::class.java,"test.db").allowMainThreadQueries()
                .addMigrations(videoAnalysisMigration(28),CALIBRATION_MIGRATION_29_30,TRAINING_OBSERVATIONS_30_31,RETIRE_DERIVED_TRAINING_31_32).build()
            try {
                val sqlite=db.openHelper.writableDatabase // Full Room delegate validates the final schema, not only SQL execution.
                assertEquals(32,sqlite.version)
                sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'auge_%'").use { assertEquals(0,it.count) }
                sqlite.query("SELECT data FROM workout_logs WHERE id='log'").use { c ->
                    assertTrue(c.moveToFirst());val log=JSONObject(c.getString(0))
                    assertEquals("Mi nota personal AUGE no se borra",log.getString("notes"));assertEquals(17,log.getInt("durationMinutes"));assertFalse(log.has("sessionStressScore"))
                }
                sqlite.query("SELECT filePath,caption,workoutLogId FROM workout_media WHERE id='media'").use { c ->
                    assertTrue(c.moveToFirst());assertEquals("synthetic.jpg",c.getString(0));assertEquals("Mi foto",c.getString(1));assertEquals("log",c.getString(2))
                }
                assertNotNull(db.setupCommitReceiptDao().get("previous-commit"))
                val saved=db.setupDraftDao().getDraft("draft")!!
                assertEquals(7,saved.revision.toInt())
                val repaired=SetupDraftCompatibility.repair(dbJson.decodeFromString<SetupWizardDraft>(saved.payloadJson))
                assertEquals("draft",repaired.draftId);assertEquals("commit",repaired.commitId);assertEquals(70.25,repaired.weightKg!!,0.0)
                assertEquals(com.example.kpkn.domain.onboarding.SetupStepId.REVIEW_ACTIVATE,repaired.stepProgress.currentStepId)
                val sleep=db.trainingObservationDao().all().single { it.kind=="sleep" }
                assertEquals(7.25,JSONObject(sleep.payload).getDouble("duration"),0.0)
            } finally { db.close();dir.deleteRecursively() }
        }
    }
}
