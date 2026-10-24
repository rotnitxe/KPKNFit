package com.example.kpkn.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HabitsDeepLinkTest {

    @Test
    fun `el enlace kpkn habits abre la pantalla de habitos`() {
        assertEquals("habits", KpknRoute.Habits.route)
        assertEquals(KpknRoute.Habits.route, DeepLinkRouter.resolve(Uri.parse("kpkn://habits"))?.route)
    }

    @Test
    fun `el alias en espanol habitos tambien abre la pantalla de habitos`() {
        assertEquals(KpknRoute.Habits.route, DeepLinkRouter.resolve(Uri.parse("kpkn://habitos"))?.route)
    }
}
