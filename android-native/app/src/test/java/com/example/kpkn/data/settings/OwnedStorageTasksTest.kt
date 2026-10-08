package com.example.kpkn.data.settings

import android.app.Application
import com.example.kpkn.data.sync.OwnedStorageTasks
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class OwnedStorageTasksTest {
    @Test fun coroutineCancellationWaitsForSdkCompletionBeforeMaintenanceCanProceed() = runBlocking {
        OwnedStorageTasks.resume()
        val source = TaskCompletionSource<String>()
        val created = CompletableDeferred<Unit>()
        var cancelRequested = false
        val reader = launch {
            OwnedStorageTasks.run<String> {
                created.complete(Unit)
                source.task to { cancelRequested = true }
            }
        }
        created.await()
        reader.cancelAndJoin()
        assertTrue(cancelRequested)
        val barrier = async { OwnedStorageTasks.pauseAndAwait() }
        yield()
        assertFalse(barrier.isCompleted)
        var createdDuringMaintenance = false
        try {
            OwnedStorageTasks.run { createdDuringMaintenance = true; source.task to {} }
            fail("A new file writer bypassed the maintenance barrier")
        } catch (_: IllegalStateException) { }
        assertFalse(createdDuringMaintenance)
        source.setResult("SDK stopped")
        barrier.await()
        OwnedStorageTasks.resume()
        assertEquals("SDK stopped", OwnedStorageTasks.run { source.task to {} })
    }
}
