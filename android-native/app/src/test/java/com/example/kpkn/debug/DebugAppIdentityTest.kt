package com.example.kpkn.debug

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.BuildConfig
import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La variante ordinaria baseDebug conserva identidad y nombre en los dos idiomas de la app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DebugAppIdentityTest {
    private fun verifyIdentity() {
        assertEquals("com.example.kpkn.dbg", BuildConfig.APPLICATION_ID)
        assertEquals("KPKN Dev", ApplicationProvider.getApplicationContext<Application>().getString(R.string.app_name))
    }
    @Test @Config(qualifiers = "en") fun elNombreEnInglesTambienEsKpknDev() = verifyIdentity()
    @Test @Config(qualifiers = "es") fun elNombreEnEspanolEsKpknDev() = verifyIdentity()
}
