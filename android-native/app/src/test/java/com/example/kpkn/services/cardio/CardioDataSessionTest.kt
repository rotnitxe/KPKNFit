package com.example.kpkn.services.cardio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.domain.cardio.GpsTrackPoint
import com.example.kpkn.domain.cardio.GpsTrackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class CardioDataSessionTest {
    @Test fun erasedRouteIsNotRecreatedAfterMaintenance() = runBlocking(Dispatchers.IO) {
        val base: Context = ApplicationProvider.getApplicationContext(); ProfileStorage.initialize(base)
        val context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
        val key = "owned-route"
        val route = currentCardioGpsSnapshotFile(context.filesDir, key)
        route.parentFile!!.mkdirs()
        val original = GpsTrackSnapshot(key, listOf(GpsTrackPoint(1000L, -33.4, -70.6)), paused = true)
        route.writeText(Json.encodeToString(GpsTrackSnapshot.serializer(), original))
        try {
            CardioGpsTracker.restoreIfAvailable(context, key)
            assertEquals(key, CardioGpsTracker.state.value.sessionKey)
            CardioGpsTracker.pausePersonalWrites()
            route.delete()
            CardioGpsTracker.releaseProfileMemory()
            CardioGpsTracker.resumePersonalWrites()
            CardioGpsTracker.pausePersonalWrites() // Drains every accepted write before asserting.
            assertFalse(route.exists())
            assertNull(CardioGpsTracker.state.value.sessionKey)
        } finally {
            CardioGpsTracker.releaseProfileMemory(); CardioGpsTracker.resumePersonalWrites()
            context.filesDir.deleteRecursively()
        }
    }
}
