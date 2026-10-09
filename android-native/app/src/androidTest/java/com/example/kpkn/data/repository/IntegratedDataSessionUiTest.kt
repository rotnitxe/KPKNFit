package com.example.kpkn.data.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.MainActivity
import com.example.kpkn.R
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.settings.SettingsViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Actual MainActivity, navigation, coordinator and disk stores; the harness restarts between phases. */
@RunWith(AndroidJUnit4::class)
class IntegratedDataSessionUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val base: Context get() = ProfileStorage.platformContext().also { check(it.packageName == "com.example.kpkn.stability") }
    private val context: Context get() = ProfileStorage.context(base)
    private val runId get() = requireNotNull(args.getString("kpkn_run_id")).also { require(it.matches(Regex("[a-f0-9-]{36}"))) }
    private val control get() = File((base as ContextWrapper).baseContext.cacheDir, "qa-integrated-$runId").apply { check(isDirectory || mkdirs()) }
    private val archive get() = File(control, "backup.zip")
    private val marker get() = File(control, "phase.txt")
    private var scenario: ActivityScenario<MainActivity>? = null
    private val programId = "qa-integrated-program"
    private val sessionId = "qa-integrated-workout"

    @Before fun requireHarness() {
        Assume.assumeTrue("Use the integrated-session harness with a run ID", args.containsKey("kpkn_run_id"))
    }
    @After fun closeActivity() {
        val active = instrumentation.uiAutomation.rootInActiveWindow
        if (active?.packageName?.toString() == "com.example.kpkn.stability") {
            runCatching {
                File(control, "ending-semantics.txt").writeText(rule.onRoot(useUnmergedTree = true).printToString())
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(control, "ending.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
        scenario?.close()
    }

    private fun io(block: suspend () -> Unit) = runBlocking(Dispatchers.IO) { block() }
    private fun database() = KpknDatabase.getInstance(context)
    private fun settings() = Settings(onboardingCompleted = true, hasSeenWelcome = true,
        hasSeenHomeTour = true, hasChosenVoiceCaptureMode = true, hasSeenWorkoutTagEducation = true,
        age = 30, username = "Integrated QA", appLanguage = "en", profilePicture = ProfilePhotoStore.STORAGE_TOKEN)
    private fun weekly(): Program {
        val session = Session(sessionId, "Integrated QA workout", dayOfWeek = 1,
            exercises = listOf(Exercise("qa-integrated-exercise", "Press banca",
                exerciseDbId = "bench_press__barbell", exerciseId = "bench_press__barbell",
                canonicalExerciseId = "bench_press__barbell", catalogConfigurationId = "bench_press__barbell",
                sets = listOf(ExerciseSet("qa-integrated-set", targetReps = 8, weight = 20.0)))))
        val week = ProgramWeek("qa-integrated-week", "QA week", sessions = listOf(session))
        return Program(programId, "Integrated QA program", volumeSetupPromptSeen = true,
            macrocycles = listOf(Macrocycle("qa-macro", "QA macro", listOf(Block("qa-block", "QA block",
                mesocycles = listOf(Mesocycle("qa-meso", "QA meso", weeks = listOf(week))))))))
    }
    private fun photo() = instrumentation.context.assets.open("media-formats/sample.png").use { it.readBytes() }
    private fun seed() = io {
        check(ProfileStorage.current().uid == null) { "A guest isolated QA profile is required" }
        val db = database()
        check(db.programDao().getAllHeaders().all { it.id.startsWith("qa-integrated-") }) { "Existing non-QA programs must be preserved" }
        check(db.workoutLogDao().getAll().all { requireNotNull(it.toWorkoutLog()).programId.startsWith("qa-integrated-") }) { "Existing non-QA history must be preserved" }
        db.settingsDao().upsert(settings().toEntity())
        db.programDao().upsert(weekly().toEntity())
        check(context.getSharedPreferences("adult_admission", 0).edit().putBoolean("confirmed_18", true).commit())
        File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).also { it.parentFile!!.mkdirs(); it.writeBytes(photo()) }
    }
    private fun openSettings() {
        scenario = ActivityScenario.launch(Intent(base, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("kpkn://settings/data")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        rule.waitUntil(90_000) { AppBootstrap.state.value == AppBootstrap.State.Ready }
        rule.waitUntil(30_000) { rule.onAllNodesWithText(context.getString(R.string.settings_delete_title)).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun requestDelete() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.settings_delete_action)))
        println("QA erase button: " + rule.onAllNodesWithText(context.getString(R.string.settings_delete_action)).onFirst().fetchSemanticsNode().boundsInRoot)
        rule.onAllNodesWithText(context.getString(R.string.settings_delete_action)).onFirst().performClick()
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(context.getString(R.string.settings_delete_confirm_title)).fetchSemanticsNodes().isNotEmpty() &&
                rule.onNodeWithText(context.getString(R.string.settings_delete_confirm_title)).isDisplayed() }
        } catch (error: Throwable) {
            println("QA erase failure semantics:\n" + rule.onRoot(useUnmergedTree = true).printToString())
            val active = instrumentation.uiAutomation.rootInActiveWindow
            if (active?.packageName?.toString() == "com.example.kpkn.stability") {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(control, "erase-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    println("QA screenshot: " + File(control, "erase-failure.png").absolutePath)
                }
            }
            throw error
        }
        rule.onNodeWithText(context.getString(R.string.settings_delete_confirm_title)).assertIsDisplayed()
        rule.onAllNodesWithText(context.getString(R.string.settings_delete_action)).onLast().performClick()
    }
    private fun awaitBlocked() {
        rule.waitUntil(10_000) { AppDataSessions.blockedUse.value != null }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(context.getString(R.string.data_session_active_title)).fetchSemanticsNodes().isNotEmpty() &&
            rule.onNodeWithText(context.getString(R.string.data_session_active_title)).isDisplayed() }
        rule.onNodeWithText(context.getString(R.string.data_session_active_title)).assertIsDisplayed()
        assertEquals(DataSessionCoordinator.State.Ready, AppDataSessions.coordinator.state.value)
    }
    private fun activeWorkout(completed: Boolean) = io {
        val session = weekly().macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single()
        val state = OngoingWorkoutState(programId, session, startTime = System.currentTimeMillis() - 60_000,
            completedSets = if (completed) mapOf("qa-integrated-exercise_0" to CompletedSet("qa-integrated-set", 20.0, 8)) else emptyMap(),
            archivedCompletedExercises = if (completed) listOf(CompletedExercise("qa-integrated-archived", "Press banca",
                exerciseDbId = "bench_press__barbell", canonicalExerciseId = "bench_press__barbell",
                sets = listOf(CompletedSet("qa-integrated-completed", 20.0, 8)))) else emptyList())
        database().stateDao().upsertOngoingWorkout(state.toEntity())
        ProgramRepository.getInstance().refreshData()
    }
    private suspend fun operation(name: String, block: suspend () -> Unit) {
        val result = CompletableDeferred<Unit>()
        AppDataSessions.launch(context, name, { result.complete(Unit) },
            { result.completeExceptionally(IllegalStateException(it)) }, requiresInactiveWorkout = true, action = block)
        withTimeout(180_000) { result.await() }
        assertEquals(DataSessionCoordinator.State.Ready, AppDataSessions.coordinator.state.value)
        assertEquals(AppBootstrap.State.Ready, AppBootstrap.state.value)
    }
    private fun phaseDone(phase: Int) {
        marker.outputStream().use { out -> out.write("$phase:${Process.myPid()}".toByteArray()); (out as java.io.FileOutputStream).fd.sync() }
        instrumentation.sendStatus(0, Bundle().apply { putString("kpkn_integrated_phase", "$phase"); putString("kpkn_integrated_pid", "${Process.myPid()}") })
    }
    private fun previous(phase: Int) {
        val saved = marker.readText().split(':')
        assertEquals("${phase - 1}", saved[0])
        assertNotEquals("A new Android process is required", Process.myPid(), saved[1].toInt())
    }
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    @Test fun phase1FinishDiscardAndRestoreThroughActualCoordinator() {
        seed(); openSettings()
        activeWorkout(completed = true)
        requestDelete(); awaitBlocked()
        io { assertNotNull(database().stateDao().getOngoingWorkout()) }
        rule.onNodeWithText(context.getString(R.string.common_back)).performClick()
        io { assertNotNull(database().stateDao().getOngoingWorkout()) }
        // The Settings delete confirmation remains open underneath the blocked-activity prompt.
        rule.onAllNodesWithText(context.getString(R.string.settings_delete_action)).onLast().performClick()
        awaitBlocked()
        rule.onNodeWithText(context.getString(R.string.data_session_finish_workout)).performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithContentDescription("Guardar y terminar entrenamiento").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Guardar y terminar entrenamiento").performClick()
        rule.waitUntil(60_000) { runBlocking(Dispatchers.IO) { database().stateDao().getOngoingWorkout() == null } }
        io { assertTrue(database().workoutLogDao().getAll().isNotEmpty()); assertNotNull(database().programDao().getById(programId)) }
        rule.waitUntil(30_000) { rule.onAllNodesWithText(context.getString(R.string.settings_title)).fetchSemanticsNodes().isNotEmpty() }
        val savedCount = runBlocking(Dispatchers.IO) { database().workoutLogDao().getAll().size }
        activeWorkout(completed = false)
        requestDelete(); awaitBlocked()
        rule.onNodeWithText(context.getString(R.string.data_session_discard_workout)).performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText(context.getString(R.string.data_session_discard_title)).fetchSemanticsNodes().isNotEmpty() }
        // Opening the discard confirmation does not itself clear the durable workout.
        io { assertNotNull(database().stateDao().getOngoingWorkout()) }
        rule.onNodeWithText(context.getString(R.string.data_session_discard_workout)).performClick()
        rule.waitUntil(30_000) { runBlocking(Dispatchers.IO) { database().stateDao().getOngoingWorkout() == null } }
        io { assertEquals(savedCount, database().workoutLogDao().getAll().size); assertNotNull(database().programDao().getById(programId)) }
        // Export/restore use the actual application participant, which retires and rebuilds every store.
        val old = ProgramRepository.getInstance()
        io {
            operation("export-integrated-fixture") { archive.outputStream().use { PortableBackup.export(context, it) } }
            val archiveHash = digest(archive)
            ProgramRepository.getInstance().updateProgramNow(weekly().copy(name = "Later QA title"))
            File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).writeBytes(byteArrayOf(1, 2, 3))
            operation("restore-integrated-fixture") { archive.inputStream().use { PortableBackup.restore(context, it) } }
            assertEquals(archiveHash, digest(archive))
            assertEquals("Integrated QA program", database().programDao().getById(programId)!!.toProgram().name)
            assertArrayEquals(photo(), File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).readBytes())
            assertTrue(runCatching { old.updateProgramNow(weekly().copy(name = "Stale caller")) }.isFailure)
            assertEquals("Integrated QA program", ProgramRepository.getInstance().programs.value.single { it.id == programId }.name)
        }
        phaseDone(1)
    }

    @Test fun phase2ColdRestoreThenExplicitErase() {
        previous(2)
        io {
            assertEquals("Integrated QA program", database().programDao().getById(programId)!!.toProgram().name)
            assertTrue(database().workoutLogDao().getAll().isNotEmpty())
            assertArrayEquals(photo(), File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).readBytes())
        }
        openSettings()
        val old = ProgramRepository.getInstance()
        requestDelete()
        rule.waitUntil(180_000) { AppDataSessions.coordinator.state.value == DataSessionCoordinator.State.Ready &&
            runBlocking(Dispatchers.IO) { database().programDao().getById(programId) == null } }
        io {
            assertTrue(database().programDao().getAllHeaders().isEmpty())
            assertTrue(database().workoutLogDao().getAll().isEmpty())
            assertNull(database().stateDao().getOngoingWorkout())
            assertFalse(File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).exists())
            assertTrue(runCatching { old.updateProgramNow(weekly().copy(name = "Recreate after erase")) }.isFailure)
            assertTrue(ProgramRepository.getInstance().programs.value.isEmpty())
        }
        phaseDone(2)
    }

    @Test fun phase3ColdEraseAndSyntheticGuestHandover() {
        previous(3)
        io {
            assertTrue(database().programDao().getAllHeaders().isEmpty())
            assertTrue(database().workoutLogDao().getAll().isEmpty())
            assertFalse(File(context.filesDir, ProfilePhotoStore.STORAGE_TOKEN).exists())
            assertFalse(PersonalDataErasure.pending())
            database().settingsDao().upsert(settings().copy(profilePicture = null).toEntity())
            check(context.getSharedPreferences("adult_admission", 0).edit().putBoolean("confirmed_18", true).commit())
        }
        openSettings()
        val original = context
        val old = ProgramRepository.getInstance()
        io {
            // This exercises local handover/recreation; it is not evidence of Firebase sign-in.
            AppDataSessions.changeIdentity(original) { null }
            assertNotEquals((original as com.example.kpkn.data.account.ProfileContext).profile.key, ProfileStorage.current().key)
            assertTrue(ProgramRepository.getInstance().programs.value.isEmpty())
            assertTrue(runCatching { old.updateProgramNow(weekly()) }.isFailure)
            val priorDatabase = KpknDatabase.openIsolated(original, "kpkn.db")
            try { assertTrue(priorDatabase.programDao().getAllHeaders().isEmpty()) }
            finally { priorDatabase.close() }
        }
        phaseDone(3)
    }
}
