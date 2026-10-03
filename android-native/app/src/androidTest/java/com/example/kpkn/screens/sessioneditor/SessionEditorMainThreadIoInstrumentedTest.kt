package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import java.lang.reflect.Field
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * StrictMode guard for the editor UI routes. A unique preference namespace and
 * private file directory use the target process UID; Room is in-memory. Using
 * the test APK context would make commit fail because its directory has another UID.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class SessionEditorMainThreadIoInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private val programId = "androidtest-editor-io-$suffix"
    private val sessionAId = "androidtest-editor-session-a-$suffix"
    private val sessionBId = "androidtest-editor-session-b-$suffix"

    @Test
    fun constructor_switch_update_autosave_save_and_discard_do_not_do_main_thread_disk_io() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedApplication = withContext(Dispatchers.IO) {
            IsolatedEditorApplication("androidtest-editor-io-$suffix-").apply { attachForTest(targetContext) }
        }
        val singletonSnapshots = mutableListOf<StaticSingletonSnapshot>()
        var strictPolicyInstalled = false
        var previousPolicy: StrictMode.ThreadPolicy? = null
        val viewModelStore = ViewModelStore()
        val violations = ConcurrentLinkedQueue<String>()
        val listenerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "session-editor-strictmode-test").apply { isDaemon = true }
        }

        try {
            // Remove any process-cached repositories before installing this test's DB.
            singletonSnapshots += detachSingleton(AugeRepository::class.java)
            singletonSnapshots += detachSingleton(CompetitionRepository::class.java)
            singletonSnapshots += detachSingleton(NutritionRepository::class.java)
            singletonSnapshots += detachSingleton(RuleTemplateStore::class.java)
            singletonSnapshots += detachSingleton(SessionEditorDraftStore::class.java)
            singletonSnapshots += detachSingleton(TrainedSessionVersionStore::class.java)
            SessionTemplateRepository.resetForTests()

            val isolatedPreferenceNames = listOf(
                "session_editor_rule_templates",
                "session_editor_drafts",
                "session_editor_preferences",
                "trained_session_versions",
            )
            withContext(Dispatchers.IO) {
                isolatedPreferenceNames.forEach { name -> isolatedApplication.deleteSharedPreferences(name) }
            }

            val repo = ProgramRepository.initForTests(isolatedApplication)
            val db = repo.databaseForTests()
            setDatabaseSingletonForTest(db)
            assertEquals(db, KpknDatabase.getInstance(isolatedApplication))
            withTimeout(30_000L) { repo.isReady.first { it } }
            assertTrue(repo.addProgramNow(fixtureProgram()).isSuccess)

            previousPolicy = onMain {
                StrictMode.getThreadPolicy().also {
                    StrictMode.setThreadPolicy(
                        StrictMode.ThreadPolicy.Builder()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .penaltyListener(
                                listenerExecutor,
                                StrictMode.OnThreadViolationListener { violation ->
                                    val route = violation.stackTrace.asSequence()
                                        .filter { frame -> frame.className.startsWith("com.example.kpkn.") }
                                        .take(8)
                                        .joinToString(";") { frame ->
                                            "${frame.className}#${frame.methodName}:${frame.lineNumber}"
                                        }
                                    if (route.isNotBlank()) {
                                        violations += "${violation.javaClass.simpleName}: $route"
                                    }
                                },
                            )
                            .build(),
                    )
                }
            }
            strictPolicyInstalled = true

            val viewModel = onMain {
                SessionEditorViewModel(
                    application = isolatedApplication,
                    programId = programId,
                    sessionId = sessionAId,
                    draftWeekId = "week-$suffix",
                    draftMacroIndex = 0,
                    draftMesoIndex = 0,
                    draftDayOfWeek = 1,
                ).also { viewModelStore.put("session-editor", it) }
            }
            withTimeout(30_000L) { viewModel.uiState.first { it.session?.id == sessionAId } }

            // Exercise synchronous editor preference/template actions on Main too.
            onMain {
                viewModel.saveCurrentRulesAsTemplate("StrictMode fixture")
                val templateId = viewModel.uiState.value.ruleTemplates
                    .last { it.name == "StrictMode fixture" }.id
                viewModel.renameRuleTemplate(templateId, "StrictMode rename")
                viewModel.deleteRuleTemplate(templateId)
                viewModel.updateCurrentSession { it.copy(description = "durable edit") }
            }
            delay(2_300L) // allow the debounced autosave to reach the app-lifetime IO writer
            var autoSavedDescription: String? = null
            withTimeout(5_000L) {
                while (autoSavedDescription != "durable edit") {
                    autoSavedDescription = viewModel.persistedDraftFor(
                        weekId = "week-$suffix",
                        macroIndex = 0,
                        mesoIndex = 0,
                        sessionId = sessionAId,
                    )?.session?.description
                    if (autoSavedDescription != "durable edit") delay(100L)
                }
            }
            assertEquals("debounced autosave must become durable before manual save", "durable edit", autoSavedDescription)

            val savedDraft = withContext(Dispatchers.Main.immediate) {
                viewModel.saveDraftForExitAndAwait()
            }
            assertTrue("manual draft save must complete", savedDraft)
            val savedSession = withContext(Dispatchers.Main.immediate) {
                viewModel.saveSession(skipRefresh = true)
            }
            assertTrue("manual session save must complete: ${savedSession.message}", savedSession.success)

            onMain { viewModel.updateCurrentSession { it.copy(description = "discarded edit") } }
            delay(2_300L)
            val discarded = withContext(Dispatchers.Main.immediate) {
                viewModel.discardDraftForCurrentSessionAndAwait()
            }
            assertTrue("manual draft discard must complete", discarded)

            // Exercise the dirty-switch path: it must write on IO before publishing B.
            onMain {
                viewModel.updateCurrentSession { it.copy(description = "switch edit") }
                viewModel.requestSessionSwitch(sessionBId)
            }
            withTimeout(30_000L) { viewModel.uiState.first { it.session?.id == sessionBId } }
            val savedBeforeSwitch = viewModel.persistedDraftFor(
                weekId = "week-$suffix",
                macroIndex = 0,
                mesoIndex = 0,
                sessionId = sessionAId,
            )
            assertEquals(
                "dirty switch must commit the old-session draft before showing the target",
                "switch edit",
                savedBeforeSwitch?.session?.description,
            )

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // Drain the asynchronous StrictMode listener after all editor actions.
            listenerExecutor.submit(Runnable {}).get(5, TimeUnit.SECONDS)
            assertTrue(
                "Editor actions performed disk IO on Main:\n${violations.joinToString("\n")}",
                violations.isEmpty(),
            )
        } finally {
            if (strictPolicyInstalled) {
                onMain {
                    previousPolicy?.let { StrictMode.setThreadPolicy(it) }
                    viewModelStore.clear()
                }
            } else {
                onMain { viewModelStore.clear() }
            }
            SessionTemplateRepository.resetForTests()
            singletonSnapshots.filter { it.owner == RuleTemplateStore::class.java ||
                it.owner == SessionEditorDraftStore::class.java ||
                it.owner == TrainedSessionVersionStore::class.java
            }.asReversed().forEach(::restoreSingleton)
            singletonSnapshots.filterNot { it.owner == RuleTemplateStore::class.java ||
                it.owner == SessionEditorDraftStore::class.java ||
                it.owner == TrainedSessionVersionStore::class.java
            }.forEach { snapshot -> clearSingleton(snapshot.owner) }
            runCatching { ProgramRepository.closeInstance() }
            runCatching { KpknDatabase.closeInstance() }
            runCatching { setDatabaseSingletonForTest(null) }
            withContext(Dispatchers.IO) {
                listOf(
                    "session_editor_rule_templates",
                    "session_editor_drafts",
                    "session_editor_preferences",
                    "trained_session_versions",
                ).forEach { name -> isolatedApplication.deleteSharedPreferences(name) }
                isolatedApplication.deleteTestFiles()
            }
            listenerExecutor.shutdownNow()
        }
    }

    private fun fixtureProgram() = Program(
        id = programId,
        name = "Editor StrictMode fixture",
        macrocycles = listOf(
            Macrocycle(
                id = "macro-$suffix",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block-$suffix",
                        name = "Block",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-$suffix",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week-$suffix",
                                        name = "Week",
                                        sessions = listOf(
                                            Session(id = sessionAId, name = "Session A", dayOfWeek = 1),
                                            Session(id = sessionBId, name = "Session B", dayOfWeek = 2),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, database)
    }

    private fun detachSingleton(owner: Class<*>): StaticSingletonSnapshot {
        val field = singletonField(owner)
        val receiver = singletonReceiver(owner, field)
        field.isAccessible = true
        val previous = field.get(receiver)
        field.set(receiver, null)
        return StaticSingletonSnapshot(owner, field, receiver, previous)
    }

    private fun clearSingleton(owner: Class<*>) {
        val field = singletonField(owner)
        field.isAccessible = true
        field.set(singletonReceiver(owner, field), null)
    }

    private fun restoreSingleton(snapshot: StaticSingletonSnapshot) {
        snapshot.field.isAccessible = true
        snapshot.field.set(snapshot.receiver, snapshot.previousValue)
    }

    private fun singletonField(owner: Class<*>): Field {
        val candidateClasses = sequenceOf(owner) + owner.declaredClasses.asSequence()
        return candidateClasses.flatMap { it.declaredFields.asSequence() }
            .first { field ->
                (field.name.equals("instance", ignoreCase = true) || field.name == "INSTANCE") &&
                    owner.isAssignableFrom(field.type)
            }
    }

    private fun singletonReceiver(owner: Class<*>, field: Field): Any? {
        if (Modifier.isStatic(field.modifiers)) return null
        val companionField = owner.getDeclaredField("Companion").apply { isAccessible = true }
        return companionField.get(null)
    }

    private fun <T : Any> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private data class StaticSingletonSnapshot(
        val owner: Class<*>,
        val field: Field,
        val receiver: Any?,
        val previousValue: Any?,
    )

    private class IsolatedEditorApplication(private val preferencePrefix: String) : Application() {
        private lateinit var isolatedFiles: File
        private lateinit var isolatedCache: File

        fun attachForTest(targetContext: Context) {
            attachBaseContext(ContextWrapper(targetContext))
            isolatedFiles = File(targetContext.filesDir, preferencePrefix).apply { check(mkdirs()) }
            isolatedCache = File(targetContext.cacheDir, preferencePrefix).apply { check(mkdirs()) }
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(preferencePrefix + name, mode)
        override fun deleteSharedPreferences(name: String): Boolean =
            super.deleteSharedPreferences(preferencePrefix + name)
        override fun getFilesDir(): File = isolatedFiles
        override fun getCacheDir(): File = isolatedCache

        fun deleteTestFiles() {
            // Only directories created by this fixture; never the target app root.
            check(isolatedFiles.name == preferencePrefix && isolatedCache.name == preferencePrefix)
            isolatedFiles.deleteRecursively()
            isolatedCache.deleteRecursively()
        }
    }
}
