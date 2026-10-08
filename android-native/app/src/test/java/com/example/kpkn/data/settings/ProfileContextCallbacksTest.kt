package com.example.kpkn.data.settings

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
class ProfileContextCallbacksTest {
    @Test fun callbackRegistrationDelegatesToThePlatform() {
        val application: Context = ApplicationProvider.getApplicationContext()
        var registered: ComponentCallbacks? = null
        var unregistered: ComponentCallbacks? = null
        val platform = object : ContextWrapper(application) {
            override fun registerComponentCallbacks(callback: ComponentCallbacks) { registered = callback }
            override fun unregisterComponentCallbacks(callback: ComponentCallbacks) { unregistered = callback }
        }
        val profile = ProfileContext(platform, LocalProfile(uid = "callback-owner"))
        val callback = object : ComponentCallbacks {
            override fun onConfigurationChanged(configuration: Configuration) = Unit
            override fun onLowMemory() = Unit
        }
        assertSame(profile, profile.applicationContext)
        profile.registerComponentCallbacks(callback)
        profile.unregisterComponentCallbacks(callback)
        assertSame(callback, registered)
        assertSame(callback, unregistered)
    }
}
