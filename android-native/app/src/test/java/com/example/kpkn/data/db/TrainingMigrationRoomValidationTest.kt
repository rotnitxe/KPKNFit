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
    @Test fun populated28_29_30_32OpenWithFullRoom36IdentityAndPreserveUsefulData() = runBlocking {
        for(version in listOf(28,29,30,32,33,34)) {
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
                old.execSQL("INSERT INTO programs(id,name,data) VALUES ('program','Programa original',?)",arrayOf("""{"id":"program","name":"Programa original","notes":"Mi nota AUGE","macrocycles":[{"id":"manual","originalLoad":73.125}],"runState":{"runId":"run","weekId":"week","pendingAction":{"type":"CONFIRM_AUTOREGULATION","proposals":[{"kind":"SCALE_WEEK_VOLUME","volumeFactor":0.7},{"kind":"ADJUST_TM","liftSlot":"SQUAT","kgDelta":2.5,"explanation":"Regla del programa"}]}}}"""))
                old.execSQL("INSERT INTO workout_logs VALUES ('log','program','session','2026-10-01',?)",arrayOf("""{"id":"log","programId":"program","sessionId":"session","sessionName":"Síntesis de prueba","date":"2026-10-01","notes":"Mi nota personal AUGE no se borra","durationMinutes":17,"sessionStressScore":88,"completedExercises":[]}"""))
                val sleepRaw="""{"id":"sleep","date":"2026-10-01","duration":7.25,"endTime":"2026-10-01T08:00:00Z"}"""
                if(version<31) old.execSQL("INSERT INTO auge_sleep(id,date,data) VALUES ('sleep','2026-10-01',?)",arrayOf(sleepRaw))
                else old.execSQL("INSERT INTO training_observations VALUES ('auge_sleep:sleep','sleep','2026-10-01',?,'historical_original')",arrayOf(sleepRaw))
                val custom="""{"id":"custom","name":"Anillas","efc":4.25,"cnc":3,"ssc":0.5,"ttc":1,"axialLoadFactor":0.8,"fatigueTier":"ALTA","technicalDifficulty":7.5,"notes":"Mi nota efc AUGE se conserva"}"""
                old.execSQL("INSERT INTO custom_exercises VALUES ('custom','Anillas',?,'2026-10-01','2026-10-01')",arrayOf(custom))
                val profile=JSONObject("""{"technicalDifficulty":7.5,"efc":4.25,"richMetadata":{"fatigue":{"cnc":3},"programming":{"fatigueCost":"high","role":"manual"}}}""")
                val snapshot=JSONObject().put("catalogRevision","original-revision").put("resolvedProfile",profile)
                val exercise=JSONObject().put("exerciseId","exercise").put("exerciseName","Anillas").put("sets",org.json.JSONArray()).put("resolvedProfileSnapshotJson",snapshot.toString())
                val existingLog=JSONObject("""{"id":"log","programId":"program","sessionId":"session","sessionName":"Síntesis de prueba","date":"2026-10-01","notes":"Mi nota personal AUGE no se borra","durationMinutes":17,"sessionStressScore":88}""").put("completedExercises",org.json.JSONArray().put(exercise))
                old.execSQL("UPDATE workout_logs SET data=? WHERE id='log'",arrayOf(existingLog.toString()))
                old.execSQL("INSERT INTO workout_media(id,kind,filePath,createdAtMs,isPr,workoutLogId,caption) VALUES ('media','PHOTO','synthetic.jpg',123,0,'log','Mi foto')")
                old.execSQL("INSERT INTO setup_drafts(draftId,payloadJson,revision,updatedAtEpochMs) VALUES ('draft',?,7,123)",arrayOf("""{"draftId":"draft","commitId":"commit","name":"Ana","includeTraining":false,"includeNutrition":false,"weightKg":70.25,"draftScope":"rings_only","ringsAnswers":{"energyFeeling":3},"stepProgress":{"origin":"NATIVE","currentStepId":"RINGS_RESULT","stalePreviews":["RINGS_BATTERIES"]}}"""))
                old.execSQL("INSERT INTO setup_commit_receipts(commitId,draftId,bodyGoalIdsJson,committedAtEpochMs) VALUES ('previous-commit','other','[]',456)")
                if(version>=32) old.execSQL("UPDATE setup_drafts SET payloadJson=? WHERE draftId='draft'",arrayOf("""{"draftId":"draft","commitId":"commit","name":"Ana","includeTraining":false,"includeNutrition":false,"weightKg":70.25,"draftScope":"resume","stepProgress":{"origin":"NATIVE","currentStepId":"REVIEW_ACTIVATE"}}"""))
                // A v33 fixture has already passed the v32→33 original-data purge.
                // Seed that prerequisite, then independently exercise v33→34 proposal retirement.
                if (version >= 33) {
                    old.execSQL("UPDATE workout_logs SET data=? WHERE id='log'", arrayOf(
                        com.example.kpkn.data.compatibility.LegacyTrainingImport.log(existingLog.toString())))
                    old.execSQL("UPDATE custom_exercises SET data=? WHERE id='custom'", arrayOf(
                        com.example.kpkn.data.compatibility.LegacyExerciseCoefficientImport.customExercise(custom)))
                }
                // A v34 fixture must already contain the result of v33→34.
                // Earlier fixture versions still exercise that migration independently.
                if (version >= 34) {
                    val originalProgram = old.rawQuery("SELECT data FROM programs WHERE id='program'", null).use {
                        check(it.moveToFirst()); it.getString(0)
                    }
                    old.execSQL("UPDATE programs SET data=? WHERE id='program'", arrayOf(
                        com.example.kpkn.data.compatibility.LegacyProgramProposalImport.program(originalProgram)))
                }
                old.version=version
            }
            val db=Room.databaseBuilder(context,KpknDatabase::class.java,"test.db").allowMainThreadQueries()
                .addMigrations(videoAnalysisMigration(28),CALIBRATION_MIGRATION_29_30,TRAINING_OBSERVATIONS_30_31,RETIRE_DERIVED_TRAINING_31_32,RETIRE_CATALOG_COEFFICIENTS_32_33,RETIRE_PENDING_TRAINING_PROPOSALS_33_34,*COMBINED_NUTRITION_TRAINING_35,NUTRITION_CATALOG_35_36).build()
            try {
                val sqlite=db.openHelper.writableDatabase // Full Room delegate validates the final schema, not only SQL execution.
                assertEquals(36,sqlite.version)
                sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'auge_%'").use { assertEquals(0,it.count) }
                sqlite.query("SELECT data FROM programs WHERE id='program'").use { c ->
                    assertTrue(c.moveToFirst());val program=JSONObject(c.getString(0))
                    assertEquals("Mi nota AUGE",program.getString("notes"))
                    assertEquals(73.125,program.getJSONArray("macrocycles").getJSONObject(0).getDouble("originalLoad"),0.0)
                    val run=program.getJSONObject("runState")
                    assertEquals("run",run.getString("runId"));assertEquals("week",run.getString("weekId"))
                    val proposals=run.getJSONObject("pendingAction").getJSONArray("proposals")
                    assertEquals(1,proposals.length());assertEquals("ADJUST_TM",proposals.getJSONObject(0).getString("kind"))
                    assertEquals(2.5,proposals.getJSONObject(0).getDouble("kgDelta"),0.0)
                }
                sqlite.query("SELECT data FROM workout_logs WHERE id='log'").use { c ->
                    assertTrue(c.moveToFirst());val log=JSONObject(c.getString(0))
                    assertEquals("Mi nota personal AUGE no se borra",log.getString("notes"));assertEquals(17,log.getInt("durationMinutes"));assertFalse(log.has("sessionStressScore"))
                    val snapshot=JSONObject(log.getJSONArray("completedExercises").getJSONObject(0).getString("resolvedProfileSnapshotJson"))
                    assertEquals("original-revision",snapshot.getString("catalogRevision"))
                    val profile=snapshot.getJSONObject("resolvedProfile")
                    assertEquals(7.5,profile.getDouble("technicalDifficulty"),0.0);assertFalse(profile.has("efc"))
                    assertFalse(profile.getJSONObject("richMetadata").has("fatigue"))
                    assertEquals("manual",profile.getJSONObject("richMetadata").getJSONObject("programming").getString("role"))
                }
                sqlite.query("SELECT data FROM custom_exercises WHERE id='custom'").use { c ->
                    assertTrue(c.moveToFirst());val custom=JSONObject(c.getString(0))
                    assertEquals("Anillas",custom.getString("name"));assertEquals("custom",custom.getString("id"))
                    assertEquals("Mi nota efc AUGE se conserva",custom.getString("notes"))
                    assertEquals(7.5,custom.getDouble("technicalDifficulty"),0.0)
                    for(key in listOf("efc","cnc","ssc","ttc","axialLoadFactor","fatigueTier")) assertFalse(custom.has(key))
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
