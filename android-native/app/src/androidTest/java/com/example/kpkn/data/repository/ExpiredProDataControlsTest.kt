package com.example.kpkn.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.PersonalDataErasure
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.entitlements.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExpiredProDataControlsTest {
    private lateinit var context: Context
    private var repository: ProgramRepository? = null
    private var trustedNow = 1500L
    private val receipt = ConfirmedEntitlement(PlanLevel.PRO, "ACTIVE", 2000L, 1000L)
    @Before fun setup() {
        val base: Context = ProfileStorage.platformContext()
        NutritionRepository.closeInstance(); AugeRepository.closeForDataSession()
        ProgramRepository.closeInstance(); KpknDatabase.closeInstance()
        context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
    }
    private suspend fun closeRepository() {
        repository?.let { repo ->
            (ProgramRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible=true }.get(repo) as Job).cancelAndJoin()
        }
        repository=null
    }
    @After fun cleanup(): Unit = runBlocking {
        closeRepository(); KpknDatabase.closeInstance()
        context.deleteDatabase("kpkn.db"); context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively()
    }
    private suspend fun initialize(): ProgramRepository {
        val db=KpknDatabase.getInstance(context)
        val constructor=ProgramRepository::class.java.declaredConstructors.single { it.parameterTypes.size==3 }.apply { isAccessible=true }
        val repo=constructor.newInstance(db, false, { EntitlementPolicy.level(receipt,trustedNow) }) as ProgramRepository
        ProgramRepository::class.java.getDeclaredMethod("loadFromDb").apply { isAccessible=true }.invoke(repo)
        withTimeout(10_000) { repo.isReady.first { it } }
        repository=repo; return repo
    }
    private fun advancedProgram(): Program {
        val weeks=(1..2).map { week -> ProgramWeek("week-$week","Week $week",sessions=(1..6).map {
            Session("session-$week-$it","Session $it",dayOfWeek=it)
        }) }
        val meso=Mesocycle("meso","Meso",weeks=weeks)
        return Program("paid-history","Paid history",macrocycles=listOf(Macrocycle("macro","Macro",listOf(Block("block","Block",mesocycles=listOf(meso))))))
    }

    @Test fun verifiedExpiryKeepsHistoryReadableExportableAndErasableAfterColdReopen() = runBlocking(Dispatchers.IO) {
        val initial=initialize(); val original=advancedProgram()
        assertEquals(PlanLevel.PRO,initial.planLevel())
        initial.addProgramNow(original).getOrThrow()
        closeRepository(); KpknDatabase.closeInstance()
        val repo=initialize()
        val program=requireNotNull(repo.getProgramById(original.id))
        assertEquals(PlanLevel.PRO,repo.planLevel())
        assertEquals(original.name,program.name)
        assertEquals(original.macrocycles,program.macrocycles)
        val workout=WorkoutLog("historic-workout",program.id,"session-1-1","Paid session","2026-10-08T10:00:00Z",30)
        repo.addWorkoutLog(workout); repo.flushPendingWrites()
        val db=repo.databaseForTests()
        val meal=NutritionLog(id="historic-meal",date="2026-10-08T12:00:00Z",foods=listOf(
            LoggedFood(foodName="Synthetic meal",amount=100.0,calories=100.0,protein=5.0,carbs=10.0,fats=2.0)))
        db.nutritionDao().upsertLog(meal.toEntity())
        val photo=File(context.filesDir,"workout_media/retained.png").also { file ->
            file.parentFile!!.mkdirs(); file.writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("media-formats/sample.png").use { it.readBytes() })
        }
        val originalPhoto=photo.readBytes()
        val programBefore=db.programDao().getById(program.id)
        trustedNow=receipt.validUntilEpochMs
        assertEquals(PlanLevel.FREE,repo.planLevel())
        assertEquals(program,repo.getProgramById(program.id))
        assertEquals(workout.id,repo.history.value.single().id)
        assertTrue(repo.addProgramNow(program.copy(name="Denied new edit")).exceptionOrNull() is CapabilityRequiredException)
        assertEquals(programBefore,db.programDao().getById(program.id))
        val archive=ByteArrayOutputStream(); PortableBackup.export(context,archive)
        val entries=mutableMapOf<String,ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
            while(true){val entry=zip.nextEntry ?: break; entries[entry.name]=zip.readBytes()}
        }
        assertArrayEquals(originalPhoto,entries["files/workout_media/retained.png"])
        val exportedDb=File(context.cacheDir,"expired-pro-export-${UUID.randomUUID()}.db").also { it.writeBytes(entries.getValue("database.db")) }
        try {
            SQLiteDatabase.openDatabase(exportedDb.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use { exported ->
                for((table,id) in listOf("programs" to program.id,"workout_logs" to workout.id,"nutrition_logs" to meal.id)) {
                    exported.rawQuery("SELECT COUNT(*) FROM $table WHERE id=?",arrayOf(id)).use { assertTrue(it.moveToFirst()); assertEquals(table,1,it.getInt(0)) }
                }
            }
        } finally { exportedDb.delete() }
        closeRepository(); KpknDatabase.closeInstance()
        val reopened=initialize()
        assertEquals(PlanLevel.FREE,reopened.planLevel())
        assertEquals(program,reopened.getProgramById(program.id))
        assertEquals(workout.id,reopened.history.value.single().id)
        assertEquals(meal.id,reopened.databaseForTests().nutritionDao().getAllLogs().single().id)
        assertArrayEquals(originalPhoto,photo.readBytes())
        reopened.deleteProgram(program.id); reopened.flushPendingWrites()
        assertNull(reopened.databaseForTests().programDao().getById(program.id))
        assertEquals(workout.id,reopened.databaseForTests().workoutLogDao().getAll().single().id)
        closeRepository()
        PersonalDataErasure.erase(context)
        assertFalse(context.getDatabasePath("kpkn.db").exists())
        assertFalse(photo.exists())
        assertFalse(PersonalDataErasure.pending())
    }
}
