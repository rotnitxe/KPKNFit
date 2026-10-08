package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.navigation.NavigationApplicationContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
class NavigationApplicationTest {
    class Probe(application: Application) : AndroidViewModel(application)
    @Test fun navEntryCanCreateAndroidViewModelWithoutUncapturingPersonalPaths() {
        val platform: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(platform)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val profile = ProfileStorage.context(activity, LocalProfile(uid = "navigation-owner"))
        val fixedPath = profile.getDatabasePath("kpkn.db")
        assertFalse(profile.applicationContext is Application)
        val navigation = NavigationApplicationContext(profile, activity.application)
        val store = ViewModelStore()
        try {
            val controller = NavHostController(navigation)
            controller.setLifecycleOwner(activity)
            controller.setViewModelStore(store)
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            controller.graph = controller.createGraph(startDestination = "probe") { composable("probe") {} }
            val entry = requireNotNull(controller.currentBackStackEntry)
            val model = ViewModelProvider.create(entry)[Probe::class.java]
            assertSame(activity.application, model.getApplication<Application>())
            ProfileStorage.select("other-owner")
            assertEquals(fixedPath, profile.getDatabasePath("kpkn.db"))
            assertSame(profile, navigation.baseContext)
            assertSame(profile, profile.applicationContext)
        } finally { store.clear(); activity.finish() }
    }
}
