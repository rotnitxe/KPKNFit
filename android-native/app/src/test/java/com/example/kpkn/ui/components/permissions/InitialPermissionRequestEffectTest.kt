package com.example.kpkn.ui.components.permissions

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InitialPermissionRequestEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun declinedOrPendingRequestIsNotLaunchedAgainAfterRecreationOrRouteChanges() {
        val onSetupRoute = mutableStateOf(false)
        var requests = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            InitialPermissionRequestEffect(true, onSetupRoute.value) { requests++ }
        }
        compose.runOnIdle { assertEquals(1, requests) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.runOnIdle { onSetupRoute.value = true }
        compose.runOnIdle { onSetupRoute.value = false }
        compose.runOnIdle { assertEquals(1, requests) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun onboardingMustFinishAndItsRouteMustBeLeftBeforeTheFirstRequest() {
        val completed = mutableStateOf(false)
        val onSetupRoute = mutableStateOf(true)
        var requests = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            InitialPermissionRequestEffect(completed.value, onSetupRoute.value) { requests++ }
        }
        compose.runOnIdle { assertEquals(0, requests); completed.value = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(0, requests); onSetupRoute.value = false }
        compose.runOnIdle { assertEquals(1, requests) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, requests) }
    }
}
