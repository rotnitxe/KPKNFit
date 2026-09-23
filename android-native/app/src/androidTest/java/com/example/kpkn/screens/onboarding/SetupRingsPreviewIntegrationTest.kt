package com.example.kpkn.screens.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.onboarding.SetupRingsPreviewCalculator
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupRingsPreviewIntegrationTest {
    @Test
    fun candidateRunsTheRecoveryEngineWithoutWritingWellbeing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val programs = ProgramRepository.init(context)
        programs.isReady.first { it }
        NutritionRepository.init(context)
        val auge = AugeRepository.getInstance(context)
        val before = auge.getTodayWellbeing()
        val now = System.currentTimeMillis()
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(now, 0, 0,
            InitialRecoveryActivityType.MIXED, InitialRecoveryIntensity.MODERATE,
            sensations = InitialRecoverySensations(2, 2, 2))
        val preview = SetupRingsPreviewCalculator(context).calculate(programs.settings.value,
            evidence, "rings-preview-test", emptyMap(), null, null, null, now)
        assertNotNull(preview.batteries.sourceLabel)
        assertTrue(preview.batteries.muscular in 0..100)
        assertTrue(preview.batteries.cnc in 0..100)
        assertTrue(preview.batteries.spinal in 0..100)
        assertEquals(before, auge.getTodayWellbeing())
    }
}
