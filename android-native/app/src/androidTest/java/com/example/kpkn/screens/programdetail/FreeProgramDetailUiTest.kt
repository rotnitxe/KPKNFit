package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.R
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.ui.entitlements.LocalPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class FreeProgramDetailUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var repo: ProgramRepository
    private val navigationCalls=AtomicInteger(0)
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun weekly(count:Int): Program {
        val week=ProgramWeek("free-ui-week","Week",sessions=(1..count).map { Session("session-$it","Session $it",dayOfWeek=it) })
        val meso=Mesocycle("meso","Meso",weeks=listOf(week))
        return Program("free-ui-program","Free UI program",volumeSetupPromptSeen=true,
            macrocycles=listOf(Macrocycle("macro","Macro",listOf(Block("block","Block",mesocycles=listOf(meso))))))
    }
    @Before fun setup(): Unit =runBlocking {
        NutritionRepository.closeInstance()
        AugeRepository.closeForDataSession()
        NutritionRepository.initForTests(context, object : FoodCatalogImporter {
            override suspend fun importIfNeeded(db: KpknDatabase, context: Context, alreadyImported: Boolean,
                existingMeta: FoodImporter.ImportMetadata?, onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit): Boolean = true
        })
        repo=ProgramRepository.initForTests(context) { PlanLevel.FREE }
        withTimeout(10_000){repo.isReady.first{it}}
    }
    @After fun cleanup(){
        NutritionRepository.closeInstance()
        AugeRepository.closeForDataSession()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }
    private fun mount(count:Int)=runBlocking {
        assertTrue(repo.addProgramNow(weekly(count)).isSuccess)
        rule.setContent {
            MaterialTheme { CompositionLocalProvider(LocalPlan provides PlanLevel.FREE) {
                ProgramDetailScreen(programId="free-ui-program",onBack={},onStartWorkout={_,_->},onEditSession={},
                    onCreateSession={_,_,_,_,_->navigationCalls.incrementAndGet()})
            } }
        }
        rule.waitUntil(15_000){rule.onAllNodesWithContentDescription("Agregar").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun fifthSessionIsPersistedAndOpensItsEditor() {
        mount(4)
        rule.onAllNodesWithContentDescription("Agregar").onFirst().performClick()
        rule.waitUntil(10_000){navigationCalls.get()==1}
        runBlocking {repo.flushPendingWrites()}
        val stored=runBlocking {repo.databaseForTests().programDao().getById("free-ui-program")!!.toProgram()}
        assertEquals(5,stored.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.size)
    }
    @Test fun sixthSessionShowsLimitWithoutOpeningAnUnsavedEditor() {
        mount(5)
        val before=runBlocking {repo.databaseForTests().programDao().getById("free-ui-program")}
        rule.onAllNodesWithContentDescription("Agregar").onFirst().performClick()
        rule.onNodeWithText(context.getString(R.string.capability_free_limit),substring=true).assertIsDisplayed()
        rule.waitForIdle()
        assertEquals(0,navigationCalls.get())
        runBlocking {repo.flushPendingWrites()}
        assertEquals(before,runBlocking {repo.databaseForTests().programDao().getById("free-ui-program")})
    }
}
