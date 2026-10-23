package com.example.kpkn.screens.workout.technique

import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ejercita la misma guarda que usa el efecto real con proveedores y casos de uso falsos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecorderCameraBindingOwnerTest {
    private class FakeProvider {
        val bound = linkedSetOf<Any>()
        val releases = mutableListOf<List<Any>>()
        fun bind(vararg cases: Any) { bound.addAll(cases) }
        fun unbind(vararg cases: Any) {
            releases += cases.toList()
            bound.removeAll(cases.toSet())
        }
    }

    @Test fun provider_listener_arriving_after_close_cannot_bind_or_release_another_camera() {
        val provider = FakeProvider().apply { bind("back-preview", "back-video") }
        val owner = RecorderCameraBindingOwner()
        val lateListener = {
            owner.bindIfActive {
                provider.bind("rec-preview", "rec-video")
                owner.own { provider.unbind("rec-preview", "rec-video") }
            }
        }
        owner.close()
        lateListener()
        assertEquals(setOf("back-preview", "back-video"), provider.bound)
        assertTrue(provider.releases.isEmpty())
    }

    @Test fun recorder_cleanup_after_finalize_leaves_the_new_back_camera_bound() {
        val provider = FakeProvider()
        val afterFinalize = mutableListOf<() -> Unit>()
        val owner = RecorderCameraBindingOwner()
        owner.bindIfActive {
            provider.bind("rec-preview", "rec-video")
            owner.own { afterFinalize += { provider.unbind("rec-preview", "rec-video") } }
        }
        owner.close()
        provider.bind("back-preview", "back-photo", "back-video")
        afterFinalize.single().invoke()
        assertEquals(setOf("back-preview", "back-photo", "back-video"), provider.bound)
        assertEquals(listOf(listOf("rec-preview", "rec-video")), provider.releases)
    }

    @Test fun repeated_close_releases_owned_cases_once() {
        val provider = FakeProvider()
        val owner = RecorderCameraBindingOwner()
        owner.bindIfActive {
            provider.bind("preview", "video")
            owner.own { provider.unbind("preview", "video") }
        }
        owner.close()
        owner.close()
        assertTrue(provider.bound.isEmpty())
        assertEquals(1, provider.releases.size)
    }

    @Test fun partially_failed_binding_still_has_owned_cleanup() {
        val provider = FakeProvider().apply { bind("other") }
        val owner = RecorderCameraBindingOwner()
        runCatching {
            owner.bindIfActive {
                owner.own { provider.unbind("preview", "video") }
                provider.bind("preview")
                error("falló el segundo caso de uso")
            }
        }
        owner.close()
        assertEquals(setOf("other"), provider.bound)
        assertEquals(listOf(listOf("preview", "video")), provider.releases)
    }

    @Test
    fun cleanup_clears_downgraded_session_copy_and_unbinds_the_owned_use_cases() {
        val ownedSession = createSession()
        val effectiveSession = ownedSession.copy(
            tier = RecorderFpsTier.FALLBACK_30,
            grantedFps = 30f,
        )
        var activeSession: RecorderSession? = effectiveSession
        val provider = FakeProvider().apply {
            bind("other", ownedSession.preview, ownedSession.videoCapture)
        }
        val owner = RecorderCameraBindingOwner()
        owner.own {
            if (activeSession?.sharesUseCasesWith(ownedSession) == true) activeSession = null
            if (ownedSession.preview in provider.bound || ownedSession.videoCapture in provider.bound) {
                provider.unbind(ownedSession.preview, ownedSession.videoCapture)
            }
        }

        owner.close()

        assertNull(activeSession)
        assertEquals(setOf("other"), provider.bound)
        assertEquals(
            listOf(listOf(ownedSession.preview, ownedSession.videoCapture)),
            provider.releases,
        )
    }

    @OptIn(ExperimentalSessionConfig::class)
    private fun createSession(): RecorderSession {
        val preview = Preview.Builder().build()
        val videoCapture = VideoCapture.withOutput(Recorder.Builder().build())
        val sessionConfig = SessionConfig.Builder(preview, videoCapture).build()
        return RecorderSession(
            preview = preview,
            videoCapture = videoCapture,
            sessionConfig = sessionConfig,
            tier = RecorderFpsTier.FEATURE_GROUP_60,
            grantedFps = 60f,
        )
    }
}
