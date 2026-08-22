package com.example.kpkn.telemetry

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.KpknApplication
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryIntegrationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun app_should_initialize_telemetry_on_startup() {
        // This test verifies that the telemetry system initializes correctly
        // when the app starts up
        
        // The actual test would require a real Firebase setup
        // This is a placeholder for integration testing
        composeTestRule.onNodeWithText("KPKN").assertExists()
    }
    
    @Test
    fun telemetry_helper_should_be_accessible_from_app_context() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.filesDir, KpknDiagnosticLogger.LOG_ROOT)
        assertTrue(root.isDirectory)
        KpknDiagnosticLogger.officialAreas.forEach { area ->
            assertTrue("missing canonical area $area", File(root, area).isDirectory)
        }
        assertTrue(KpknDiagnosticLogger.awaitIdle())
    }
}
