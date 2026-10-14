package com.example.kpkn.services.workout

import android.net.Uri
import com.example.kpkn.navigation.DeepLinkRouter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las notificaciones abren la sesión en curso, y sin ella, la pestaña Entreno (LS3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutSessionNotificationPathTest {

    @Test
    fun sinSesionEnCursoLaRutaEsEntreno() {
        assertEquals("training", workoutNotificationPath(programId = null, sessionId = null))
        assertEquals("training", workoutNotificationPath(programId = "p1", sessionId = null))
        assertEquals("training", workoutNotificationPath(programId = "", sessionId = "s1"))
    }

    @Test
    fun conSesionEnCursoLaRutaEsLaSesion() {
        assertEquals("workout/p1/s1", workoutNotificationPath(programId = "p1", sessionId = "s1"))
    }

    @Test
    fun recortaLosIdentificadores() {
        assertEquals("workout/p1/s1", workoutNotificationPath(programId = " p1 ", sessionId = "s1 "))
    }

    @Test
    fun laRutaResueltaPorElEnlaceAbreLaSesionYNoEntreno() {
        val path = workoutNotificationPath(programId = "p1", sessionId = "s1")
        val resolved = DeepLinkRouter.resolve(Uri.parse("kpkn://$path"))
        assertEquals("workout/p1/s1", resolved?.route)
    }
}
