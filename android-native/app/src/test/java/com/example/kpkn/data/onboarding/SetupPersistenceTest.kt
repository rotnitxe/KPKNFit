package com.example.kpkn.data.onboarding

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.db.toEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupPersistenceTest {
    private lateinit var db: KpknDatabase
    private lateinit var drafts: SetupDraftRepository

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        drafts = SetupDraftRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun draftSurvivesRepositoryRestartAndRevisionIsMonotonic() = runBlocking {
        drafts.save("draft-1", "{\"step\":2}", 2, "catalog-7")
        val reopened = SetupDraftRepository(db).load("draft-1")
        assertEquals("{\"step\":2}", reopened?.payloadJson)
        assertEquals(2L, reopened?.revision)
        assertThrows<IllegalArgumentException> {
            runBlocking { drafts.save("draft-1", "{\"step\":1}", 1) }
        }
    }

    @Test
    fun equalRevisionWithDifferentPayloadIsRejectedAcrossRepositoryInstances() = runBlocking {
        val first = SetupDraftRepository(db)
        val second = SetupDraftRepository(db)
        first.save("draft-conflict", "{\"step\":1}", 4)

        assertThrows<IllegalArgumentException> {
            runBlocking { second.save("draft-conflict", "{\"step\":2}", 4) }
        }
        assertEquals("{\"step\":1}", first.load("draft-conflict")?.payloadJson)
    }

    @Test
    fun equalRevisionWithSamePayloadIsIdempotentAcrossRepositoryInstances() = runBlocking {
        val first = SetupDraftRepository(db)
        val second = SetupDraftRepository(db)
        val saved = first.save("draft-idempotent", "{\"step\":1}", 4)
        val repeated = second.save("draft-idempotent", "{\"step\":1}", 4)

        assertEquals(saved.payloadJson, repeated.payloadJson)
        assertEquals(saved.revision, repeated.revision)
    }

    @Test
    fun setupCommitPersistsAcrossDiskReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "TESTDB"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try {
            SetupCommitCoordinator(first).commit(
                SetupCommitRequest("disk-commit", "disk-draft", Settings(), null, null, false, false),
            )
        } finally {
            first.close()
        }
        val second = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try {
            assertNotNull(second.setupCommitReceiptDao().get("disk-commit"))
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun commitIsIdempotent() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val request = SetupCommitRequest("commit-1", "draft-1", Settings(), null, null, false, false)
        val first = coordinator.commit(request)
        val second = coordinator.commit(request.copy(settings = Settings().copy(username = "different")))
        assertEquals(first, second)
        assertEquals(1, db.setupCommitReceiptDao().get("commit-1")?.let { 1 })
        assertEquals("Usuario", db.settingsDao().get()?.toSettings()?.username)
    }

    @Test
    fun corruptReceiptIsNotSilentlyCoerced() = runBlocking {
        db.setupCommitReceiptDao().insert(
            com.example.kpkn.data.db.SetupCommitReceiptEntity(
                commitId = "corrupt-receipt",
                draftId = null,
                programId = null,
                nutritionPlanId = null,
                bodyGoalIdsJson = "[1]",
                committedAtEpochMs = 1L,
            ),
        )

        assertThrows<Throwable> {
            runBlocking {
                SetupCommitCoordinator(db).commit(
                    SetupCommitRequest("corrupt-receipt", null, Settings(), null, null, false, false),
                )
            }
        }
    }

    @Test
    fun activatingSetupUsesReplacementProgramAndDeactivatesPreviousNutrition() = runBlocking {
        val json = Json { encodeDefaults = true }
        db.stateDao().upsertActiveProgram(
            com.example.kpkn.data.db.ActiveProgramEntity(
                data = json.encodeToString(ActiveProgramState(programId = "old-program")),
            ),
        )
        db.nutritionDao().upsertPlan(NutritionPlan(id = "old-plan", name = "Old", isActive = true).toEntity())

        val replacement = executableProgram("new-program")
        val plan = NutritionPlan(id = "new-plan", name = "New", isActive = true)
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-activation",
                draftId = null,
                settings = Settings(),
                program = replacement,
                nutritionPlan = plan,
                activateProgram = true,
                activateNutrition = true,
            ),
        )

        assertEquals("new-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals(false, plans.getValue("old-plan").isActive)
        assertEquals(true, plans.getValue("new-plan").isActive)
        assertEquals("new-plan", db.nutritionDao().getActiveState()?.activePlanId)
    }

    @Test
    fun roomTransactionRollsBackAndDoesNotLeavePartialDraft() = runBlocking {
        assertThrows<IllegalStateException> {
            runBlocking {
                db.withTransaction {
                    db.setupDraftDao().upsertDraft(
                        com.example.kpkn.data.db.SetupDraftEntity("draft-rollback", "{}", 1, null, 1),
                    )
                    error("forced")
                }
            }
        }
        assertNull(db.setupDraftDao().getDraft("draft-rollback"))
    }

    @Test
    fun failedSettingsWriteRollsBackProgramAndReceipt() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_setup_settings BEFORE INSERT ON settings BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        val result = runCatching {
            SetupCommitCoordinator(db).commit(SetupCommitRequest("failed", null, Settings(), executableProgram("rollback-program"), NutritionPlan(id = "rollback-plan", name = "Rollback"), true, true))
        }
        org.junit.Assert.assertTrue(result.isFailure)
        assertNull(db.programDao().getById("rollback-program"))
        assertNull(db.stateDao().getActiveProgram())
        assertTrue(db.nutritionDao().getAllPlans().isEmpty())
        assertNull(db.nutritionDao().getActiveState())
        assertNull(db.settingsDao().get())
        assertNull(db.setupCommitReceiptDao().get("failed"))
    }

    private fun executableProgram(id: String): Program {
        val session = com.example.kpkn.data.models.Session("$id-session", "Sesión", exercises = listOf(
            com.example.kpkn.data.models.Exercise("$id-exercise", "Ejercicio", sets = listOf(
                com.example.kpkn.data.models.ExerciseSet("$id-set", targetReps = 10),
            )),
        ))
        return Program(id, "Test", macrocycles = listOf(
            com.example.kpkn.data.models.Macrocycle("$id-macro", "Macro", blocks = listOf(
                com.example.kpkn.data.models.Block("$id-block", "Block", mesocycles = listOf(
                    com.example.kpkn.data.models.Mesocycle("$id-meso", "Meso", weeks = listOf(
                        com.example.kpkn.data.models.ProgramWeek("$id-week", "Semana", sessions = listOf(session)),
                    )),
                )),
            )),
        ))
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }
}
