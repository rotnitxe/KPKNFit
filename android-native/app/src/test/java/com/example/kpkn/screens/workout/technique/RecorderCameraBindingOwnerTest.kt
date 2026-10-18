package com.example.kpkn.screens.workout.technique

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ejercita la misma guarda que usa el efecto real con proveedores y casos de uso falsos. */
class RecorderCameraBindingOwnerTest {
    private class FakeProvider {
        val bound = linkedSetOf<String>()
        val releases = mutableListOf<List<String>>()
        fun bind(vararg cases: String) { bound.addAll(cases) }
        fun unbind(vararg cases: String) {
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
}
