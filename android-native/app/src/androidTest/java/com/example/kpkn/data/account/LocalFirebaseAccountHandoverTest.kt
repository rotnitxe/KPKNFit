package com.example.kpkn.data.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.BuildConfig
import com.example.kpkn.MainActivity
import com.example.kpkn.data.backup.AppBootstrap
import com.example.kpkn.data.backup.AppDataSessions
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Actual native Auth/transport/profile graph on local Firebase, real MainActivity and Room. */
@RunWith(AndroidJUnit4::class)
class LocalFirebaseAccountHandoverTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val base: Context get() = ProfileStorage.platformContext()
    private fun context() = ProfileStorage.context(base)
    private fun io(block: suspend () -> Unit) = runBlocking(Dispatchers.IO) { withTimeout(120_000) { block() } }
    private fun openAccount(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(base, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("kpkn://account"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun awaitReady() {
        rule.waitUntil(90_000) { AppBootstrap.state.value == AppBootstrap.State.Ready }
    }
    private fun allowAdultGateForSyntheticProfile() = io {
        // This fixture is an adult test identity. No production profile is touched.
        assertTrue(context().getSharedPreferences("adult_admission",0).edit().putBoolean("confirmed_18",true).commit())
        val db=KpknDatabase.getInstance(context())
        val current=db.settingsDao().get()?.toSettings() ?: Settings()
        db.settingsDao().upsert(current.copy(age=30,appLanguage="en",onboardingCompleted=true,hasSeenWelcome=true).toEntity())
        ProgramRepository.getInstance().refreshData()
    }
    private suspend fun seed(title: String,id: String) {
        val repository=ProgramRepository.getInstance()
        assertTrue("Only empty/known QA data allowed",repository.programs.value.all { it.id.startsWith("qa-native-account-") })
        repository.addProgramNow(Program(id=id,name=title)).getOrThrow()
        repository.flushPendingWrites()
    }
    private fun visibleEmail(email: String) {
        rule.waitUntil(30_000) { rule.onAllNodesWithText(email).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(email).assertIsDisplayed()
    }
    @Test fun accountABReturnPreservesRoomAndRejectsTheRetiredRepository() {
        check(BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATORS && base.packageName=="com.example.kpkn.stability")
        val runId=UUID.randomUUID().toString()
        val runtime=requireNotNull(FirebaseRuntime.get(base))
        assertTrue(runtime.local)
        // A previous failed QA run may leave its own synthetic Auth/profile selected.
        val prior=runtime.auth.currentUser
        check(prior==null || prior.email?.startsWith("qa-native-")==true) { "Existing non-QA identity must be preserved" }
        check(ProfileStorage.current().uid==null || prior?.uid==ProfileStorage.current().uid) { "Non-QA/mismatched profile requires separate recovery" }
        var scenario=openAccount()
        try {
            awaitReady()
            if (ProfileStorage.current().uid!=null) io { AccountGraph.account(base).signOut() }
            awaitReady(); allowAdultGateForSyntheticProfile()
            scenario.close(); scenario=openAccount(); awaitReady()
            val password="FixtureOnlyQA123!"
            val emailA="qa-native-a-$runId@example.test"
            val emailB="qa-native-b-$runId@example.test"
            val idA="qa-native-account-a-$runId"
            val idB="qa-native-account-b-$runId"
            var uidA="";var uidB=""
            var oldA: ProgramRepository?=null
            io { AccountGraph.account(base).signUp(emailA,password,true,false) }
            awaitReady();allowAdultGateForSyntheticProfile()
            scenario.close();scenario=openAccount();awaitReady();visibleEmail(emailA)
            io {
                uidA=requireNotNull(ProfileStorage.current().uid)
                assertEquals(uidA,runtime.auth.currentUser!!.uid)
                seed("Original A",idA);oldA=ProgramRepository.getInstance()
                AccountGraph.account(base).signOut()
            }
            awaitReady();allowAdultGateForSyntheticProfile()
            scenario.close();scenario=openAccount();awaitReady()
            io {
                assertNull(ProfileStorage.current().uid)
                assertTrue(ProgramRepository.getInstance().programs.value.isEmpty())
                AccountGraph.account(base).signUp(emailB,password,true,false)
            }
            awaitReady();allowAdultGateForSyntheticProfile()
            scenario.close();scenario=openAccount();awaitReady();visibleEmail(emailB)
            io {
                uidB=requireNotNull(ProfileStorage.current().uid)
                assertNotEquals(uidA,uidB)
                assertTrue(ProgramRepository.getInstance().programs.value.none { it.id==idA })
                seed("Original B",idB)
                assertTrue(runCatching { oldA!!.addProgramNow(Program(id=idA,name="Late old A")).getOrThrow() }.isFailure)
                AccountGraph.account(base).signOut()
            }
            awaitReady();allowAdultGateForSyntheticProfile()
            scenario.close();scenario=openAccount();awaitReady()
            io { AccountGraph.account(base).signIn(emailA,password,false) }
            awaitReady();allowAdultGateForSyntheticProfile()
            scenario.close();scenario=openAccount();awaitReady();visibleEmail(emailA)
            io {
                assertEquals(uidA,ProfileStorage.current().uid)
                assertEquals("Original A",ProgramRepository.getInstance().programs.value.single { it.id==idA }.name)
                assertTrue(ProgramRepository.getInstance().programs.value.none { it.id==idB })
                assertEquals("Original A",KpknDatabase.getInstance(context()).programDao().getById(idA)!!.toProgram().name)
                AccountGraph.account(base).signOut()
            }
            awaitReady();allowAdultGateForSyntheticProfile()
            assertNull(ProfileStorage.current().uid)
            instrumentation.sendStatus(2,Bundle().apply {
                putString("kpkn_account_handover",org.json.JSONObject().put("state","PASS").put("distinctUids",uidA!=uidB)
                    .put("accountARetained",true).put("accountBHiddenFromA",true).put("staleWriterRejected",true)
                    .put("finalGuest",true).put("scope","ONE_DEVICE_LOCAL_FIREBASE_NATIVE_CLIENT_AND_ROOM").toString())
            })
        } finally { scenario.close() }
    }
}
